/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class TerminalEmulatorTest {
    @Test
    fun `ascii text is rendered`() {
        val emulator = TerminalEmulator(TerminalSize(8, 2))

        emulator.feed("hi")

        assertEquals("hi", emulator.snapshot().renderText())
        assertEquals(Cursor(0, 2), emulator.cursor)
    }

    @Test
    fun `control bytes update cursor`() {
        val emulator = TerminalEmulator(TerminalSize(8, 2))

        emulator.feed("a\bb\t\r\nd")

        assertEquals("b\nd", emulator.snapshot().renderText())
        assertEquals(Cursor(1, 1), emulator.cursor)
    }

    @Test
    fun `split utf8 sequence survives separate feeds`() {
        val emulator = TerminalEmulator(TerminalSize(4, 1))
        val bytes = "€".toByteArray(StandardCharsets.UTF_8)

        emulator.feed(bytes.copyOfRange(0, 1))
        emulator.feed(bytes.copyOfRange(1, bytes.size))

        val cell = emulator.snapshot().rows[0][0]
        assertEquals(0x20AC, cell.baseCodePoint)
        assertEquals(Cursor(0, 1), emulator.cursor)
    }

    @Test
    fun `invalid utf8 byte becomes replacement`() {
        val emulator = TerminalEmulator(TerminalSize(4, 1))

        emulator.feed(byteArrayOf(0xFF.toByte()))

        assertEquals(0xFFFD, emulator.snapshot().rows[0][0].baseCodePoint)
    }

    @Test
    fun `unsupported csi sequence is not rendered`() {
        val emulator = TerminalEmulator(TerminalSize(8, 1))

        emulator.feed(byteArrayOf(0x1B) + "[31mR".toByteArray(StandardCharsets.UTF_8))

        val snapshot = emulator.snapshot()
        assertEquals("R", snapshot.renderText())
        assertEquals(Cursor(0, 1), snapshot.cursor)
    }

    @Test
    fun `osc string terminated by bell is ignored`() {
        val emulator = TerminalEmulator(TerminalSize(8, 1))

        emulator.feed("\u001B]0;title\u0007X")

        assertEquals("X", emulator.snapshot().renderText())
    }
}
