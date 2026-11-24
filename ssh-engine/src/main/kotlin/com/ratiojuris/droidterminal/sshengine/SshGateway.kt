/*
 * Droid Terminal — SSH engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.sshengine

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.ratiojuris.droidterminal.terminalengine.TerminalSize
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

/** A connected SFTP channel and the session that owns it. */
class SshSftpSession internal constructor(
    private val session: Session,
    val channel: ChannelSftp,
) : Closeable {
    /** Disconnects the channel and session. */
    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }
}

/**
 * Creates SSH shell sessions and fetches host keys.
 *
 * Every connection uses `StrictHostKeyChecking=yes` with an in-memory repository
 * built from the trust decision supplied by the caller; an untrusted key aborts
 * the handshake rather than being silently accepted.
 */
class SshGateway {
    /**
     * Captures the host key a server presents without trusting or persisting it.
     * The handshake is expected to fail because the empty repository rejects an
     * unseen key; the presented key is recorded before that rejection.
     */
    fun fetchHostKey(host: String, port: Int, username: String): Result<HostKeyInfo> = runCatching {
        require(host.isNotBlank()) { "host must not be blank" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(port in 1..65535) { "port outside valid range" }

        val captured = AtomicReference<HostKeyInfo?>(null)
        val jsch = JSch()
        jsch.setHostKeyRepository(JschHostKeyRepository(emptyMap()) { presentedHost, fingerprint, algorithm ->
            captured.set(HostKeyInfo(presentedHost, fingerprint, algorithm))
        })
        val session = jsch.getSession(username, host, port)
        session.setConfig("StrictHostKeyChecking", "yes")
        try {
            session.connect(CONNECT_TIMEOUT_MS)
        } catch (_: JSchException) {
            // Expected: the presented key is not in the empty repository.
        } finally {
            session.disconnect()
        }
        captured.get() ?: throw SshException("Host $host did not present a key")
    }

    /** Opens an authenticated interactive shell for [target] at viewport [size]. */
    fun openSession(target: SshTarget, size: TerminalSize): Result<SshSessionHandle> = runCatching {
        val session = connect(target)
        val channel = session.openChannel("shell") as? ChannelShell
            ?: throw SshException("Remote host refused a shell channel")
        channel.setPtyType("xterm-256color")
        channel.setPtySize(size.columns, size.rows, 0, 0)
        channel.connect(CHANNEL_TIMEOUT_MS)
        SshSessionHandle(session, channel, channel.inputStream, channel.outputStream)
    }.recoverCatching(::translate)

    /** Opens an authenticated SFTP channel for [target]. */
    fun openSftpSession(target: SshTarget): Result<SshSftpSession> = runCatching {
        val session = connect(target)
        val channel = session.openChannel("sftp") as? ChannelSftp
            ?: throw SshException("Remote host refused the SFTP subsystem")
        channel.connect(CHANNEL_TIMEOUT_MS)
        SshSftpSession(session, channel)
    }.recoverCatching(::translate)

    /** Applies the host-key policy and credentials, then completes the SSH handshake. */
    private fun connect(target: SshTarget): Session {
        val fingerprint = target.trustedFingerprint
            ?: throw SshException("Host key for ${target.host} is not trusted")
        val jsch = JSch()
        jsch.setHostKeyRepository(JschHostKeyRepository(mapOf(target.host to fingerprint)))

        val session = jsch.getSession(target.username, target.host, target.port)
        session.setConfig("StrictHostKeyChecking", "yes")
        val credential = target.credential
        when {
            credential.privateKeyPem != null -> {
                jsch.addIdentity(
                    target.username,
                    credential.privateKeyPem!!.toByteArray(Charsets.UTF_8),
                    null,
                    credential.passphrase?.toByteArray(Charsets.UTF_8),
                )
            }
            credential.password != null -> {
                session.setUserInfo(PasswordUserInfo(credential.password!!))
            }
        }
        session.connect(CONNECT_TIMEOUT_MS)
        return session
    }

    private fun translate(error: Throwable): Nothing {
        if (error is SshException) throw error
        throw SshException("SSH connection failed: ${error.message ?: error::class.simpleName}", error)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val CHANNEL_TIMEOUT_MS = 15_000
    }
}
