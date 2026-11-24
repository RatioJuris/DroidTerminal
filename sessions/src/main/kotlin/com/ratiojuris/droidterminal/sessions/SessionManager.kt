/*
 * Droid Terminal — sessions module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.sessions

import com.ratiojuris.droidterminal.connections.ConnectionProfile
import com.ratiojuris.droidterminal.sshengine.SshCredential
import com.ratiojuris.droidterminal.sshengine.SshGateway
import com.ratiojuris.droidterminal.sshengine.SshSessionHandle
import com.ratiojuris.droidterminal.sshengine.SshTarget
import com.ratiojuris.droidterminal.terminalengine.TerminalEmulator
import com.ratiojuris.droidterminal.terminalengine.TerminalSize
import com.ratiojuris.droidterminal.terminalengine.TerminalSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-scoped owner of live SSH sessions and their terminal emulators.
 *
 * Sessions deliberately outlive screen composition: navigating away from the
 * terminal destination must not drop a connection. All mutable session state is
 * guarded by [synchronized] and published through immutable StateFlow values.
 */
class SessionManager(
    private val gateway: SshGateway,
    initialScrollback: Int = 1_000,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequence = AtomicInteger()
    private val sessions = LinkedHashMap<String, LiveSession>()

    @Volatile
    private var scrollbackLimit = initialScrollback

    private val _tabs = MutableStateFlow<List<SessionTabInfo>>(emptyList())
    val tabs: StateFlow<List<SessionTabInfo>> = _tabs.asStateFlow()

    private val _snapshots = MutableStateFlow<Map<String, TerminalSnapshot>>(emptyMap())
    val snapshots: StateFlow<Map<String, TerminalSnapshot>> = _snapshots.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _activeTarget = MutableStateFlow<SshTarget?>(null)
    val activeTarget: StateFlow<SshTarget?> = _activeTarget.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    /** Updates the scrollback bound applied to newly created emulators. */
    @Synchronized
    fun configure(scrollback: Int) {
        scrollbackLimit = scrollback.coerceAtLeast(1)
    }

    fun clearStatus() {
        _status.value = null
    }

    /**
     * Opens a new session for [profile] and makes it active.
     * The caller supplies the credential and the already-verified host-key
     * fingerprint; no secret is persisted here.
     */
    suspend fun connect(
        profile: ConnectionProfile,
        credential: SshCredential,
        fingerprint: String?,
        size: TerminalSize,
    ): Result<String> = withContext(Dispatchers.IO) {
        val id = "s-${sequence.incrementAndGet()}"
        val emulator = TerminalEmulator(size, scrollbackLimit)
        val target = SshTarget(profile.host, profile.port, profile.username, credential, fingerprint)
        val session = LiveSession(id, profile.name, target, emulator)
        synchronized(this@SessionManager) {
            sessions[id] = session
            publishTabs()
            setActive(id)
        }

        gateway.openSession(target, size).onSuccess { handle ->
            synchronized(this@SessionManager) {
                if (!session.removed) {
                    session.handle = handle
                    session.state = SessionState.CONNECTED
                    publishTabs()
                    publishActiveTarget()
                    publishSnapshot(session)
                } else {
                    handle.close()
                }
            }
            startReading(session)
        }.onFailure { failure ->
            synchronized(this@SessionManager) {
                if (!session.removed) {
                    session.state = SessionState.FAILED
                    publishTabs()
                    publishActiveTarget()
                }
            }
            _status.value = "Connection failed: ${failure.message ?: failure::class.simpleName}"
        }.map { id }
    }

    @Synchronized
    fun select(id: String) {
        if (sessions.containsKey(id)) setActive(id)
    }

    @Synchronized
    fun disconnect(id: String) {
        val session = sessions[id] ?: return
        stopReading(session)
        session.handle?.close()
        session.handle = null
        session.state = SessionState.DISCONNECTED
        publishTabs()
        publishActiveTarget()
        publishSnapshot(session)
    }

    /** Disconnects and removes a session and its buffered terminal state. */
    @Synchronized
    fun close(id: String) {
        val session = sessions.remove(id) ?: return
        session.removed = true
        stopReading(session)
        session.handle?.close()
        session.handle = null
        _snapshots.value = _snapshots.value - id
        publishTabs()
        if (_activeId.value == id) {
            setActive(sessions.keys.lastOrNull())
        }
    }

    /** Sends already-encoded bytes to the active session. */
    fun write(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val handle = synchronized(this) { activeSession()?.handle }
        if (handle == null) {
            _status.value = "No active SSH connection"
            return
        }
        runCatching { handle.write(bytes) }.onFailure { failure ->
            _status.value = "Write failed: ${failure.message ?: failure::class.simpleName}"
        }
    }

    /** Applies a viewport change to the active emulator and remote pty. */
    @Synchronized
    fun resize(size: TerminalSize) {
        val session = activeSession() ?: return
        session.emulator.resize(size)
        session.handle?.resize(size)
        publishSnapshot(session)
    }

    private fun startReading(session: LiveSession) {
        session.readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(READ_BUFFER_BYTES)
            try {
                while (isActive && !session.removed) {
                    val handle = synchronized(this@SessionManager) { session.handle }
                        ?: break
                    val count = handle.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    session.emulator.feed(buffer.copyOf(count))
                    publishSnapshot(session)
                }
            } catch (failure: Exception) {
                if (!session.removed) {
                    _status.value = "Read failed: ${failure.message ?: failure::class.simpleName}"
                }
            } finally {
                synchronized(this@SessionManager) {
                    if (!session.removed && session.state == SessionState.CONNECTED) {
                        session.state = SessionState.DISCONNECTED
                        session.handle = null
                        publishTabs()
                        publishActiveTarget()
                    }
                }
            }
        }
    }

    private fun stopReading(session: LiveSession) {
        session.readJob?.cancel()
        session.readJob = null
    }

    @Synchronized
    private fun setActive(id: String?) {
        _activeId.value = id
        publishActiveTarget()
    }

    private fun activeSession(): LiveSession? = _activeId.value?.let(sessions::get)

    private fun publishActiveTarget() {
        val active = activeSession()
        _activeTarget.value = if (active?.state == SessionState.CONNECTED) active.target else null
    }

    private fun publishTabs() {
        _tabs.value = sessions.values.map { session ->
            SessionTabInfo(session.id, session.title, session.target.host, session.state)
        }
    }

    private fun publishSnapshot(session: LiveSession) {
        if (session.removed) return
        _snapshots.value = _snapshots.value + (session.id to session.emulator.snapshot())
    }

    private class LiveSession(
        val id: String,
        val title: String,
        val target: SshTarget,
        val emulator: TerminalEmulator,
    ) {
        @Volatile
        var handle: SshSessionHandle? = null

        @Volatile
        var readJob: Job? = null

        @Volatile
        var state: SessionState = SessionState.CONNECTING

        @Volatile
        var removed: Boolean = false
    }

    private companion object {
        const val READ_BUFFER_BYTES = 8192
    }
}
