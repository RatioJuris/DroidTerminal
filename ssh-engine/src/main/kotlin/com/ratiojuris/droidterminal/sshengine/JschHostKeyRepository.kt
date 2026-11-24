/*
 * Droid Terminal — SSH engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.sshengine

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import com.ratiojuris.droidterminal.security.HostKeyFingerprint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Read-only host-key repository backed by a snapshot of user-accepted fingerprints.
 *
 * Trust is keyed by host and is enforced with `StrictHostKeyChecking=yes`: an
 * unseen key is [HostKeyRepository.NOT_INCLUDED] and a differing key is
 * [HostKeyRepository.CHANGED], both of which make JSch abort the handshake.
 * The repository never mutates persisted trust; acceptance is written by the
 * caller only after an explicit UI decision.
 */
class JschHostKeyRepository(
    trustedFingerprints: Map<String, String>,
    private val onPresented: ((host: String, fingerprint: String, algorithm: String) -> Unit)? = null,
) : HostKeyRepository {
    private val trusted: Map<String, String> =
        trustedFingerprints.mapKeys { (host, _) -> host.lowercase(Locale.ROOT) }

    override fun check(host: String, key: ByteArray): Int {
        val fingerprint = HostKeyFingerprint.sha256(key)
        onPresented?.invoke(host, fingerprint, algorithmOf(key))
        val expected = trusted[host.lowercase(Locale.ROOT)] ?: return HostKeyRepository.NOT_INCLUDED
        return if (HostKeyFingerprint.matches(expected, fingerprint)) {
            HostKeyRepository.OK
        } else {
            HostKeyRepository.CHANGED
        }
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit

    override fun remove(host: String?, type: String?) = Unit

    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit

    override fun getKnownHostsRepositoryID(): String = "DroidTerminalTrustedHosts"

    override fun getHostKey(): Array<HostKey> = emptyArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

    /** Reads the leading length-prefixed algorithm name from an SSH public-key blob. */
    private fun algorithmOf(blob: ByteArray): String = runCatching {
        if (blob.size < 4) return@runCatching "unknown"
        val length = ByteBuffer.wrap(blob, 0, 4).order(ByteOrder.BIG_ENDIAN).int
        if (length <= 0 || length > blob.size - 4 || length > MAX_ALGORITHM_LENGTH) {
            return@runCatching "unknown"
        }
        String(blob, 4, length, Charsets.US_ASCII)
    }.getOrDefault("unknown")

    private companion object {
        const val MAX_ALGORITHM_LENGTH = 64
    }
}

/** A password-only JSch user info. All interactive prompts are answered non-interactively. */
internal class PasswordUserInfo(private val password: String) : UserInfo {
    override fun getPassphrase(): String? = null
    override fun getPassword(): String = password
    override fun promptPassword(message: String?): Boolean = true
    override fun promptPassphrase(message: String?): Boolean = false
    override fun promptYesNo(message: String?): Boolean = false
    override fun showMessage(message: String?) = Unit
}
