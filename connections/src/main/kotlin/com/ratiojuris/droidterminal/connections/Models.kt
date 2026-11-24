/*
 * Droid Terminal — connections module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.connections

/** Authentication method recorded on a connection profile. */
enum class AuthType { PASSWORD, SSH_KEY, AGENT }

/** Persisted SSH connection profile. */
data class ConnectionProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: AuthType,
    val group: String,
    val lastUsed: Long,
) {
    init {
        require(name.isNotBlank()) { "profile name must not be blank" }
        require(host.isNotBlank()) { "host must not be blank" }
        require(port in 1..65535) { "port outside valid range" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(group.isNotBlank()) { "group must not be blank" }
    }

    val endpoint: String get() = "$host:$port"
}

/** A host key the user explicitly accepted out of band or on first use. */
data class TrustedHostKey(
    val host: String,
    val fingerprint: String,
    val algorithm: String,
    val acceptedAt: Long,
) {
    init {
        require(host.isNotBlank()) { "host must not be blank" }
        require(fingerprint.isNotBlank()) { "fingerprint must not be blank" }
    }
}
