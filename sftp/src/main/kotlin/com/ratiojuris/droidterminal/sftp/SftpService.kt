/*
 * Droid Terminal — SFTP module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.sftp

import com.jcraft.jsch.SftpProgressMonitor
import com.ratiojuris.droidterminal.sshengine.SshGateway
import com.ratiojuris.droidterminal.sshengine.SshTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-scoped SFTP operations.
 *
 * Each operation opens and closes its own authenticated channel, so no live
 * network resource is tied to a screen's composition lifecycle. Downloads run
 * on a supervised I/O scope and publish progress through [transfers].
 */
class SftpService(
    private val gateway: SshGateway,
    private val downloadDir: File,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sequence = AtomicInteger()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private val _target = MutableStateFlow<SshTarget?>(null)
    val target: StateFlow<SshTarget?> = _target.asStateFlow()

    private val _path = MutableStateFlow("~")
    val path: StateFlow<String> = _path.asStateFlow()

    private val _home = MutableStateFlow<String?>(null)
    val home: StateFlow<String?> = _home.asStateFlow()

    private val _entries = MutableStateFlow<List<RemoteEntry>>(emptyList())
    val entries: StateFlow<List<RemoteEntry>> = _entries.asStateFlow()

    private val _transfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    val transfers: StateFlow<List<TransferProgress>> = _transfers.asStateFlow()

    private val _audit = MutableStateFlow<List<AuditEntry>>(emptyList())
    val audit: StateFlow<List<AuditEntry>> = _audit.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Binds SFTP to the credential of the active terminal session. */
    fun setTarget(value: SshTarget?) {
        if (_target.value?.host != value?.host) {
            _entries.value = emptyList()
            _path.value = "~"
            _home.value = null
        }
        _target.value = value
    }

    fun setPath(value: String) {
        _path.value = value
    }

    fun clearError() {
        _error.value = null
    }

    /** Reads [directory] (or the current path) from the remote filesystem. */
    suspend fun refresh(directory: String = _path.value) = withContext(Dispatchers.IO) {
        val target = _target.value
        if (target == null) {
            _error.value = "Connect an SSH session in Terminal first"
            return@withContext
        }
        _isLoading.value = true
        _error.value = null
        gateway.openSftpSession(target).mapCatching { handle ->
            handle.use { sftp ->
                val start = if (directory == "~" || directory.isBlank()) {
                    sftp.channel.pwd()
                } else {
                    directory
                }
                sftp.channel.cd(start)
                val resolved = sftp.channel.pwd()
                val listed = sftp.channel.ls(resolved).mapNotNull { item ->
                    val filename = item.filename
                    if (filename == "." || filename == "..") return@mapNotNull null
                    val attrs = item.attrs
                    RemoteEntry(
                        name = filename,
                        path = joinPath(resolved, filename),
                        isDirectory = attrs.isDir,
                        sizeBytes = attrs.size,
                        permissions = formatPermissions(attrs.permissions),
                        modified = formatTime(attrs.mTime),
                    )
                }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                resolved to listed
            }
        }.onSuccess { (resolved, listed) ->
            _path.value = resolved
            if (_home.value == null) _home.value = resolved
            _entries.value = listed
        }.onFailure { failure ->
            _error.value = "SFTP refresh failed: ${failure.message ?: failure::class.simpleName}"
        }
        _isLoading.value = false
    }

    /** Queues a real download and returns its transfer id immediately. */
    fun enqueueDownload(entry: RemoteEntry): String {
        val id = "t${sequence.incrementAndGet()}"
        _transfers.value = _transfers.value + TransferProgress(
            id = id,
            name = entry.name,
            transferred = 0L,
            total = entry.sizeBytes,
            state = TransferState.QUEUED,
        )
        scope.launch {
            updateTransfer(id) { it.copy(state = TransferState.ACTIVE) }
            val target = _target.value
            if (target == null) {
                updateTransfer(id) {
                    it.copy(state = TransferState.FAILED, error = "No active SSH session")
                }
                return@launch
            }
            val destination = File(downloadDir, entry.name)
            val monitor = TransferMonitor(
                onProgress = { transferred, total ->
                    updateTransfer(id) { it.copy(transferred = transferred, total = total) }
                },
                isCancelled = { cancelled.contains(id) },
            )
            runCatching {
                gateway.openSftpSession(target).getOrThrow().use { handle ->
                    FileOutputStream(destination).use { output ->
                        handle.channel.get(entry.path, output, monitor)
                    }
                }
            }.onSuccess {
                updateTransfer(id) { it.copy(state = TransferState.DONE, transferred = it.total, error = null) }
                record("DOWNLOAD", entry.path, "OK")
            }.onFailure { failure ->
                val finalState = if (cancelled.contains(id)) TransferState.CANCELLED else TransferState.FAILED
                updateTransfer(id) { it.copy(state = finalState, error = failure.message) }
                record("DOWNLOAD", entry.path, finalState.name)
            }
        }
        return id
    }

    fun cancelTransfer(id: String) {
        cancelled.add(id)
        updateTransfer(id) { current ->
            if (current.state == TransferState.DONE || current.state == TransferState.FAILED) {
                current
            } else {
                current.copy(state = TransferState.CANCELLED)
            }
        }
    }

    /** Renames a remote entry, then the caller refreshes the directory. */
    suspend fun rename(entry: RemoteEntry, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val target = _target.value
        if (target == null) {
            return@withContext Result.failure(IllegalStateException("No active SSH session"))
        }
        val newPath = joinPath(entry.path.substringBeforeLast('/'), newName)
        gateway.openSftpSession(target).mapCatching { handle ->
            handle.use { it.channel.rename(entry.path, newPath) }
            record("RENAME", "${entry.path} -> $newPath", "OK")
        }.onFailure { failure ->
            _error.value = "Rename failed: ${failure.message ?: failure::class.simpleName}"
        }
    }

    /** Deletes a confirmed remote entry, then the caller refreshes the directory. */
    suspend fun delete(entry: RemoteEntry): Result<Unit> = withContext(Dispatchers.IO) {
        val target = _target.value
        if (target == null) {
            return@withContext Result.failure(IllegalStateException("No active SSH session"))
        }
        gateway.openSftpSession(target).mapCatching { handle ->
            handle.use { sftp ->
                if (entry.isDirectory) sftp.channel.rmdir(entry.path) else sftp.channel.rm(entry.path)
            }
            record("DELETE", entry.path, "OK")
        }.onFailure { failure ->
            _error.value = "Delete failed: ${failure.message ?: failure::class.simpleName}"
        }
    }

    private fun updateTransfer(id: String, transform: (TransferProgress) -> TransferProgress) {
        _transfers.value = _transfers.value.map { if (it.id == id) transform(it) else it }
    }

    private fun record(action: String, path: String, outcome: String) {
        _audit.value = (listOf(AuditEntry(action, path, outcome)) + _audit.value).take(MAX_AUDIT)
    }

    private class TransferMonitor(
        private val onProgress: (Long, Long) -> Unit,
        private val isCancelled: () -> Boolean,
    ) : SftpProgressMonitor {
        @Volatile
        private var transferred = 0L

        @Volatile
        private var total = SftpProgressMonitor.UNKNOWN_SIZE

        override fun init(op: Int, src: String?, dest: String?, max: Long) {
            total = max
            transferred = 0L
            onProgress(0L, total)
        }

        override fun count(count: Long): Boolean {
            transferred += count
            onProgress(transferred, total)
            return !isCancelled()
        }

        override fun end() = onProgress(transferred, total)
    }

    private companion object {
        const val MAX_AUDIT = 50

        fun joinPath(directory: String, name: String): String =
            if (directory.endsWith("/")) "$directory$name" else "$directory/$name"

        fun formatPermissions(mode: Int): String {
            fun triad(shift: Int): String {
                val bits = (mode shr shift) and 0b111
                return buildString(3) {
                    append(if (bits and 0b100 != 0) 'r' else '-')
                    append(if (bits and 0b010 != 0) 'w' else '-')
                    append(if (bits and 0b001 != 0) 'x' else '-')
                }
            }
            return triad(6) + triad(3) + triad(0)
        }

        fun formatTime(epochSeconds: Int): String {
            val instant = Instant.ofEpochSecond(epochSeconds.toLong())
            val local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            return local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))
        }
    }
}
