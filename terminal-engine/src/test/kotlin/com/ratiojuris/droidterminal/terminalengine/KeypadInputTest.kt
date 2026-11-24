/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import com.ratiojuris.droidterminal.keypad.ArrowDirection
import com.ratiojuris.droidterminal.keypad.KeySequences
import com.ratiojuris.droidterminal.keypad.Modifier
import com.ratiojuris.droidterminal.keypad.ModifierState
import com.ratiojuris.droidterminal.keypad.TerminalKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadInputTest {
    @Test
    fun `keypad produces the expected terminal bytes`() {
        assertArrayEquals(
            byteArrayOf(0x1B, 0x5B, 0x41),
            KeySequences.bytes(TerminalKey.Arrow(ArrowDirection.UP)),
        )
        assertArrayEquals(
            byteArrayOf(0x1B, 0x5B, 0x31, 0x3B, 0x32, 0x41),
            KeySequences.bytes(
                TerminalKey.Arrow(ArrowDirection.UP),
                ModifierState(pressed = setOf(Modifier.SHIFT)),
            ),
        )
        assertArrayEquals(byteArrayOf(0x03), KeySequences.bytes(TerminalKey.ControlLetter('C')))
        assertArrayEquals(
            byteArrayOf(0x1B, 0x4F, 0x50),
            KeySequences.bytes(TerminalKey.FunctionKey(1)),
        )
    }

    @Test
    fun `keypad bytes move the cursor without leaking escape text`() {
        val emulator = TerminalEmulator(TerminalSize(10, 3))
        emulator.feed("abc")

        emulator.feed(KeySequences.bytes(TerminalKey.Arrow(ArrowDirection.LEFT)))
        assertEquals(Cursor(0, 2), emulator.cursor)

        emulator.feed(KeySequences.bytes(TerminalKey.Home))
        assertEquals(Cursor(0, 0), emulator.cursor)

        emulator.feed(KeySequences.bytes(TerminalKey.Arrow(ArrowDirection.DOWN)))
        assertEquals(Cursor(1, 0), emulator.cursor)

        emulator.feed(KeySequences.bytes(TerminalKey.FunctionKey(1)))
        emulator.feed(
            KeySequences.bytes(
                TerminalKey.Arrow(ArrowDirection.UP),
                ModifierState(pressed = setOf(Modifier.SHIFT, Modifier.ALT)),
            ),
        )
        emulator.feed(KeySequences.bytes(TerminalKey.ControlLetter('C')))

        assertEquals("abc", emulator.snapshot().renderText())
    }

    @Test
    fun `locked modifiers do not require toggle to be cleared`() {
        val locked = ModifierState(locked = Modifier.CONTROL)

        assertArrayEquals(
            byteArrayOf(0x03),
            KeySequences.bytes(TerminalKey.ControlLetter('C'), locked),
        )
        assertEquals(ModifierState.NONE, locked.cancel())
    }
}
