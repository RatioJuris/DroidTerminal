/*
 * Droid Terminal — security and cryptography module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyFingerprintTest {
    @Test
    fun `known key produces expected sha256 fingerprint`() {
        val key = "host-key".toByteArray(Charsets.UTF_8)
        assertEquals("SHA256:CfEOS9w3pHE4KlqjcQFwWyWMmyRvvPoehydyMhTxpzg", HostKeyFingerprint.sha256(key))
    }

    @Test
    fun `empty key material is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HostKeyFingerprint.sha256(ByteArray(0)) }
    }

    @Test
    fun `fingerprint comparison is exact`() {
        val fingerprint = HostKeyFingerprint.sha256("host-key".toByteArray(Charsets.UTF_8))
        assertTrue(HostKeyFingerprint.matches(fingerprint, fingerprint))
        assertFalse(HostKeyFingerprint.matches(fingerprint, fingerprint.replace('C', 'D')))
        assertFalse(HostKeyFingerprint.matches(fingerprint, "SHA256:short"))
    }
}
