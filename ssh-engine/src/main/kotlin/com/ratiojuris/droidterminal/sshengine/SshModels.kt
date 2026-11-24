/*
 * Droid Terminal — SSH engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.sshengine

/** Result of evaluating a presented host key against the stored trust decision. */
enum class HostKeyDecision { ACCEPT, REJECT }

/** Supplies the trust decision for a host key during connection setup. */
fun interface HostKeyPolicy {
    fun decide(host: String, fingerprint: String, algorithm: String): HostKeyDecision
}

/** Authentication material for one connection attempt. Never persisted by this module. */
data class SshCredential(
    val password: String? = null,
    val privateKeyPem: String? = null,
    val passphrase: String? = null,
) {
    init {
        require(password != null || privateKeyPem != null) { "a password or private key is required" }
    }
}

/** A host key presented during a handshake, captured without granting it trust. */
data class HostKeyInfo(
    val host: String,
    val fingerprint: String,
    val algorithm: String,
)

/** Connection parameters and the trust decision captured before dialling. */
data class SshTarget(
    val host: String,
    val port: Int,
    val username: String,
    val credential: SshCredential,
    val trustedFingerprint: String? = null,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port outside valid range" }
        require(username.isNotBlank()) { "username must not be blank" }
    }
}

/** Lifecycle state reported by an SSH session. */
enum class SshSessionState { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

/** Transport-level failure with a message suitable for the UI. */
class SshException(message: String, cause: Throwable? = null) : Exception(message, cause)
