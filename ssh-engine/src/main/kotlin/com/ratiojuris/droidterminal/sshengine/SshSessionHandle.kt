/*
 * Droid Terminal — SSH engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.sshengine

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.Session
import com.ratiojuris.droidterminal.terminalengine.TerminalSize
import java.io.InputStream
import java.io.OutputStream

/**
 * Owns the transport streams for one interactive shell channel.
 *
 * [read] blocks until the remote side produces bytes or the channel closes;
 * callers must run it on an I/O dispatcher. [close] disconnects the channel and
 * session, which unblocks a pending read.
 */
class SshSessionHandle internal constructor(
    private val session: Session,
    private val channel: ChannelShell,
    private val input: InputStream,
    private val output: OutputStream,
) {
    @Volatile
    private var closed = false

    val isConnected: Boolean
        get() = !closed && channel.isConnected && session.isConnected

    /** Blocking read into [buffer]. Returns the byte count, or -1 at end of stream. */
    fun read(buffer: ByteArray): Int = input.read(buffer)

    /** Writes raw terminal bytes and flushes them to the channel. */
    @Synchronized
    fun write(bytes: ByteArray) {
        check(!closed) { "session is closed" }
        output.write(bytes)
        output.flush()
    }

    /** Reports a viewport change to the remote pseudo-terminal. */
    fun resize(size: TerminalSize) {
        if (!closed) channel.setPtySize(size.columns, size.rows, 0, 0)
    }

    /** Releases channel, streams and session. Idempotent. */
    fun close() {
        if (closed) return
        closed = true
        runCatching { output.close() }
        runCatching { input.close() }
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }
}
