/*
 * Droid Terminal — shortcut keypad module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.keypad

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KeySequencesTest {
    @Test
    fun `ctrl C sends ETX rather than a clipboard command`() {
        val bytes = KeySequences.bytes(TerminalKey.ControlLetter('C'))
        assertArrayEquals(byteArrayOf(0x03), bytes)
    }

    @Test
    fun `all default control shortcuts map to their ASCII control codes`() {
        val expected = mapOf(
            'C' to 0x03,
            'D' to 0x04,
            'Z' to 0x1A,
            'L' to 0x0C,
            'R' to 0x12,
            'A' to 0x01,
            'E' to 0x05,
            'W' to 0x17,
            'U' to 0x15,
            'K' to 0x0B,
            'P' to 0x10,
            'N' to 0x0E,
        )
        expected.forEach { (letter, code) ->
            val actual = KeySequences.bytes(TerminalKey.ControlLetter(letter))
            assertArrayEquals("Ctrl+$letter", byteArrayOf(code.toByte()), actual)
        }
    }

    @Test
    fun `arrow keys use xterm sequences and shift modifies them`() {
        assertEquals("\u001B[A", KeySequences.sequence(TerminalKey.Arrow(ArrowDirection.UP)))
        assertEquals(
            "\u001B[1;2D",
            KeySequences.sequence(
                TerminalKey.Arrow(ArrowDirection.LEFT),
                ModifierState(pressed = setOf(Modifier.SHIFT)),
            ),
        )
    }

    @Test
    fun `alt modifier prefixes escape once`() {
        val alt = ModifierState(pressed = setOf(Modifier.ALT))
        assertEquals("\u001B\u001B[A", KeySequences.sequence(TerminalKey.Arrow(ArrowDirection.UP), alt))
        assertEquals("\u001B\t", KeySequences.sequence(TerminalKey.Tab, alt))
    }

    @Test
    fun `esc and function keys are terminal control sequences`() {
        assertEquals("\u001B", KeySequences.sequence(TerminalKey.Escape))
        assertEquals("\u001BOP", KeySequences.sequence(TerminalKey.FunctionKey(1)))
        assertEquals("\u001B[24~", KeySequences.sequence(TerminalKey.FunctionKey(12)))
    }

    @Test
    fun `modifier keys themselves emit nothing`() {
        assertEquals("", KeySequences.sequence(TerminalKey.Control))
        assertEquals("", KeySequences.sequence(TerminalKey.Fn))
    }

    @Test
    fun `custom key can be controlled by the active modifier`() {
        val custom = TerminalKey.Custom("x", "a")
        val controlled = ModifierState(pressed = setOf(Modifier.CONTROL))
        assertArrayEquals(byteArrayOf(0x01), KeySequences.bytes(custom, controlled))
    }

    @Test
    fun `printable keys send their character with shift resolved by the UI`() {
        assertEquals("a", KeySequences.sequence(TerminalKey.Character('a')))
        assertEquals("A", KeySequences.sequence(TerminalKey.Character('A')))
        assertEquals("!", KeySequences.sequence(TerminalKey.Character('!')))
        assertEquals(" ", KeySequences.sequence(TerminalKey.Character(' ')))
    }

    @Test
    fun `control applies to printable characters and special codes`() {
        val ctrl = ModifierState(pressed = setOf(Modifier.CONTROL))
        assertArrayEquals(byteArrayOf(0x01), KeySequences.bytes(TerminalKey.Character('a'), ctrl))
        assertArrayEquals(byteArrayOf(0x1B), KeySequences.bytes(TerminalKey.Character('['), ctrl))
        assertArrayEquals(byteArrayOf(0x1C), KeySequences.bytes(TerminalKey.Character('\\'), ctrl))
        assertArrayEquals(byteArrayOf(0x1D), KeySequences.bytes(TerminalKey.Character(']'), ctrl))
        assertArrayEquals(byteArrayOf(0x00), KeySequences.bytes(TerminalKey.Character(' '), ctrl))
    }

    @Test
    fun `editing and navigation keys use standard sequences`() {
        assertEquals("\r", KeySequences.sequence(TerminalKey.Enter))
        assertEquals("\u007F", KeySequences.sequence(TerminalKey.Backspace))
        assertEquals("\u001B[3~", KeySequences.sequence(TerminalKey.Delete))
        assertEquals("\u001B[2~", KeySequences.sequence(TerminalKey.Insert))
        assertEquals("\u001B[5~", KeySequences.sequence(TerminalKey.PageUp))
        assertEquals("\u001B[6~", KeySequences.sequence(TerminalKey.PageDown))
        assertEquals("", KeySequences.sequence(TerminalKey.CapsLock))
    }
}
