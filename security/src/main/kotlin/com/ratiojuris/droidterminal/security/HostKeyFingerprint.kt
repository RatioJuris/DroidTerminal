/*
 * Droid Terminal — security and cryptography module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.security

import java.security.MessageDigest
import java.util.Base64

/**
 * Formats SSH host-key material for fingerprint comparison.
 *
 * This utility does not decide trust. Callers must present the fingerprint to
 * the user and persist an acceptance only after explicit confirmation.
 */
object HostKeyFingerprint {
    private const val ALGORITHM = "SHA-256"

    /** Returns an OpenSSH-style fingerprint such as `SHA256:AbCd...`. */
    fun sha256(publicKeyBlob: ByteArray): String {
        require(publicKeyBlob.isNotEmpty()) { "public key blob must not be empty" }
        val digest = MessageDigest.getInstance(ALGORITHM).digest(publicKeyBlob)
        val encoded = Base64.getEncoder().encodeToString(digest).trimEnd('=')
        return "SHA256:$encoded"
    }

    /** Compares two fingerprints without short-circuiting on content. */
    fun matches(left: String, right: String): Boolean {
        if (left.length != right.length) return false
        var difference = 0
        for (index in left.indices) {
            difference = difference or (left[index].code xor right[index].code)
        }
        return difference == 0
    }
}
