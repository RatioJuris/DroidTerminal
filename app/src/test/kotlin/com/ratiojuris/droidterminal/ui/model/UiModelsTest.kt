/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiModelsTest {
    @Test
    fun `connection validation reports every invalid field`() {
        val errors = UiValidation.validateConnection("", "host", "70000", "")

        assertEquals(setOf("name", "port", "username"), errors.keys)
    }

    @Test
    fun `connection validation accepts a complete profile`() {
        assertTrue(UiValidation.validateConnection("Prod", "host.example", "22", "deploy").isEmpty())
    }

    @Test
    fun `passphrase validation requires eight characters`() {
        assertEquals("Passphrase is required", UiValidation.validatePassphrase(""))
        assertEquals("Passphrase must be at least 8 characters", UiValidation.validatePassphrase("short"))
        assertEquals(null, UiValidation.validatePassphrase("long-enough"))
    }

    @Test
    fun `vault item validation requires label and value`() {
        assertEquals(setOf("label", "secret"), UiValidation.validateVaultItem("", "").keys)
    }

    @Test
    fun `profile search matches name host user and group case insensitively`() {
        val all = MockData.connections

        assertEquals(1, filterAndSortConnections(all, "staging", ConnectionSort.NAME).size)
        assertEquals(1, filterAndSortConnections(all, "PI", ConnectionSort.NAME).size)
        assertEquals(1, filterAndSortConnections(all, "home", ConnectionSort.NAME).size)
        assertEquals(1, filterAndSortConnections(all, "OPS", ConnectionSort.NAME).size)
    }

    @Test
    fun `profile sorting orders by name host and recency`() {
        val all = MockData.connections

        assertEquals(
            listOf("Jump host", "Production web", "Raspberry Pi", "Staging database"),
            filterAndSortConnections(all, "", ConnectionSort.NAME).map { it.name },
        )
        assertEquals(
            listOf("192.168.1.42", "bastion.example.net", "db.staging.example.net", "web.example.net"),
            filterAndSortConnections(all, "", ConnectionSort.HOST).map { it.host },
        )
        assertEquals(
            listOf("Production web", "Staging database", "Raspberry Pi", "Jump host"),
            filterAndSortConnections(all, "", ConnectionSort.RECENT).map { it.name },
        )
    }
}
