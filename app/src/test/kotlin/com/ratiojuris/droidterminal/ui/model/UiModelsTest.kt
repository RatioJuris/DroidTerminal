/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.model

import com.ratiojuris.droidterminal.terminalengine.Cursor
import com.ratiojuris.droidterminal.terminalengine.TerminalCell
import com.ratiojuris.droidterminal.terminalengine.TerminalSize
import com.ratiojuris.droidterminal.terminalengine.TerminalSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiModelsTest {
    private val sample = listOf(
        ConnectionProfile("c1", "Production web", "web.example.net", 22, "deploy", AuthType.SSH_KEY, "Servers", 50),
        ConnectionProfile("c2", "Staging database", "db.staging.example.net", 2222, "ops", AuthType.PASSWORD, "Servers", 40),
        ConnectionProfile("c3", "Raspberry Pi", "192.168.1.42", 22, "pi", AuthType.PASSWORD, "Home", 30),
        ConnectionProfile("c4", "Jump host", "bastion.example.net", 22, "admin", AuthType.AGENT, "Servers", 20),
    )

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
        assertEquals(1, filterAndSortConnections(sample, "staging", ConnectionSort.NAME).size)
        assertEquals(1, filterAndSortConnections(sample, "PI", ConnectionSort.NAME).size)
        assertEquals(1, filterAndSortConnections(sample, "home", ConnectionSort.NAME).size)
        assertEquals(1, filterAndSortConnections(sample, "OPS", ConnectionSort.NAME).size)
    }

    @Test
    fun `profile sorting orders by name host and recency`() {
        assertEquals(
            listOf("Jump host", "Production web", "Raspberry Pi", "Staging database"),
            filterAndSortConnections(sample, "", ConnectionSort.NAME).map { it.name },
        )
        assertEquals(
            listOf("192.168.1.42", "bastion.example.net", "db.staging.example.net", "web.example.net"),
            filterAndSortConnections(sample, "", ConnectionSort.HOST).map { it.host },
        )
        assertEquals(
            listOf("Production web", "Staging database", "Raspberry Pi", "Jump host"),
            filterAndSortConnections(sample, "", ConnectionSort.RECENT).map { it.name },
        )
    }

    @Test
    fun `connection profile round trips through the backend model`() {
        val original = sample.first()

        assertEquals(original, original.toBackend().toUi())
    }

    @Test
    fun `terminal snapshot renders history as selectable output lines`() {
        val size = TerminalSize(2, 1)
        val snapshot = TerminalSnapshot(
            size = size,
            rows = listOf(listOf(TerminalCell.of('A'.code, 1), TerminalCell.BLANK)),
            scrollback = emptyList(),
            cursor = Cursor(0, 1),
        )

        assertEquals(
            listOf(TerminalLine("A", TerminalLineKind.OUTPUT)),
            snapshot.toUiLines(),
        )
    }
}
