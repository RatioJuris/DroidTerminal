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
    fun `alt modifier uses xterm parameter on navigation and prefix on text`() {
        val alt = ModifierState(pressed = setOf(Modifier.ALT))
        assertEquals("\u001B[1;3A", KeySequences.sequence(TerminalKey.Arrow(ArrowDirection.UP), alt))
        assertEquals("\u001B\t", KeySequences.sequence(TerminalKey.Tab, alt))
        assertEquals("\u001Ba", KeySequences.sequence(TerminalKey.Character('a'), alt))
    }

    @Test
    fun `esc and function keys are terminal control sequences`() {
        assertEquals("\u001B", KeySequences.sequence(TerminalKey.Escape))
        assertEquals("\u001BOP", KeySequences.sequence(TerminalKey.FunctionKey(1)))
        assertEquals("\u001B[15~", KeySequences.sequence(TerminalKey.FunctionKey(5)))
        assertEquals("\u001B[17~", KeySequences.sequence(TerminalKey.FunctionKey(6)))
        assertEquals("\u001B[18~", KeySequences.sequence(TerminalKey.FunctionKey(7)))
        assertEquals("\u001B[19~", KeySequences.sequence(TerminalKey.FunctionKey(8)))
        assertEquals("\u001B[20~", KeySequences.sequence(TerminalKey.FunctionKey(9)))
        assertEquals("\u001B[21~", KeySequences.sequence(TerminalKey.FunctionKey(10)))
        assertEquals("\u001B[23~", KeySequences.sequence(TerminalKey.FunctionKey(11)))
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

    @Test
    fun `DECCKM switches unmodified cursor keys and home end to SS3`() {
        val modes = TerminalModes(applicationCursorKeys = true)
        assertEquals("\u001BOA", KeySequences.sequence(TerminalKey.Arrow(ArrowDirection.UP), modes = modes))
        assertEquals("\u001BOH", KeySequences.sequence(TerminalKey.Home, modes = modes))
        assertEquals("\u001BOF", KeySequences.sequence(TerminalKey.End, modes = modes))
        val shift = ModifierState(pressed = setOf(Modifier.SHIFT))
        assertEquals("\u001B[1;2A", KeySequences.sequence(TerminalKey.Arrow(ArrowDirection.UP), shift, modes))
    }

    @Test
    fun `modified navigation and function keys carry xterm parameters`() {
        val altCtrl = ModifierState(pressed = setOf(Modifier.ALT, Modifier.CONTROL))
        assertEquals("\u001B[5;7~", KeySequences.sequence(TerminalKey.PageUp, altCtrl))
        assertEquals("\u001B[1;7P", KeySequences.sequence(TerminalKey.FunctionKey(1), altCtrl))
        assertEquals("\u001B[15;7~", KeySequences.sequence(TerminalKey.FunctionKey(5), altCtrl))
        val meta = ModifierState(pressed = setOf(Modifier.META))
        assertEquals("\u001B[1;9D", KeySequences.sequence(TerminalKey.Arrow(ArrowDirection.LEFT), meta))
    }

    @Test
    fun `paste wraps only when bracketed and cannot inject escapes`() {
        val text = "echo hi\nmkdir \u001B[2Jx"
        assertEquals("echo hi\nmkdir [2Jx", KeySequences.paste(text, bracketed = false))
        assertEquals(
            "\u001B[200~echo hi\nmkdir [2Jx\u001B[201~",
            KeySequences.paste(text, bracketed = true),
        )
    }

    @Test
    fun `bytes encode printable unicode as UTF-8`() {
        assertArrayEquals(
            "€".toByteArray(Charsets.UTF_8),
            KeySequences.bytes(TerminalKey.Character('€')),
        )
    }

    @Test
    fun `custom keys keep their documented single byte encoding`() {
        val custom = TerminalKey.Custom("é", "é")
        assertArrayEquals(byteArrayOf(0xE9.toByte()), KeySequences.bytes(custom))
        val ctrl = ModifierState(pressed = setOf(Modifier.CONTROL))
        assertArrayEquals(byteArrayOf(0xE9.toByte()), KeySequences.bytes(custom, ctrl))
    }
}
