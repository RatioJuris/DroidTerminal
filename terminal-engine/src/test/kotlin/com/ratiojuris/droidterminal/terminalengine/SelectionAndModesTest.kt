/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionAndModesTest {
    @Test
    fun `bracketed paste and DECCKM are tracked from private modes`() {
        val emulator = TerminalEmulator(TerminalSize(10, 2))

        emulator.feed("\u001B[?2004h\u001B[?1h")
        assertTrue(emulator.snapshot().bracketedPaste)
        assertTrue(emulator.snapshot().applicationCursorKeys)

        emulator.feed("\u001B[?2004l\u001B[?1l")
        assertFalse(emulator.snapshot().bracketedPaste)
        assertFalse(emulator.snapshot().applicationCursorKeys)
    }

    @Test
    fun `RIS clears terminal modes and selection`() {
        val emulator = TerminalEmulator(TerminalSize(10, 2))
        emulator.feed("hi")
        emulator.feed("\u001B[?2004h\u001B[?1h")
        emulator.beginSelection(0, 0)

        emulator.feed("\u001Bc")

        assertFalse(emulator.snapshot().bracketedPaste)
        assertFalse(emulator.snapshot().applicationCursorKeys)
        assertNull(emulator.snapshot().selection)
    }

    @Test
    fun `pad driven selection returns the selected range as text`() {
        val emulator = TerminalEmulator(TerminalSize(10, 3))
        emulator.feed("hello\r\nworld")

        emulator.beginSelection(0, 0)
        emulator.moveSelectionHead(1, 4)

        assertEquals("hello\nworld", emulator.snapshot().selectedText)
    }

    @Test
    fun `word and line selection are available to the select pad`() {
        val emulator = TerminalEmulator(TerminalSize(10, 3))
        emulator.feed("hello\r\nworld")

        emulator.beginSelection(1, 1)
        emulator.selectWord()
        assertEquals("world", emulator.snapshot().selectedText)

        emulator.clearSelection()
        emulator.beginSelection(1, 2)
        emulator.selectLine()
        assertEquals("world", emulator.snapshot().selectedText)
    }

    @Test
    fun `selection survives a scroll and clears when its line is evicted`() {
        val emulator = TerminalEmulator(TerminalSize(10, 1), scrollbackLimit = 1)
        emulator.feed("first")
        emulator.beginSelection(0, 0)
        emulator.feed("\r\nsecond")

        assertEquals("first", emulator.snapshot().selectedText)

        emulator.feed("\r\nthird")
        assertNull(emulator.snapshot().selection)
    }

    @Test
    fun `selection follows a visible line into scrollback`() {
        val emulator = TerminalEmulator(TerminalSize(10, 1), scrollbackLimit = 1)
        emulator.feed("first")
        emulator.feed("\r\nsecond")
        emulator.beginSelection(1, 0)

        emulator.feed("\r\nthird")

        assertEquals("second", emulator.snapshot().selectedText)
    }
}
