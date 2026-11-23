/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `sgr colours and styles apply to subsequent cells`() {
        val emulator = TerminalEmulator(TerminalSize(8, 1))

        emulator.feed("\u001B[1;4;31mA\u001B[0mB")

        val cells = emulator.snapshot().rows[0]
        assertEquals(AnsiColor.Indexed(1), cells[0].attributes.foreground)
        assertTrue(cells[0].attributes.bold)
        assertTrue(cells[0].attributes.underline)
        assertEquals(TerminalAttributes.DEFAULT, cells[1].attributes)
        assertEquals("AB", emulator.snapshot().renderText())
    }

    @Test
    fun `sgr supports 256 colour and truecolor`() {
        val emulator = TerminalEmulator(TerminalSize(8, 1))

        emulator.feed("\u001B[38;5;196mX\u001B[48:2::10:20:30mY")

        val cells = emulator.snapshot().rows[0]
        assertEquals(AnsiColor.Indexed(196), cells[0].attributes.foreground)
        assertEquals(AnsiColor.Rgb(10, 20, 30), cells[1].attributes.background)
    }

    @Test
    fun `sgr reset codes clear individual attributes`() {
        val emulator = TerminalEmulator(TerminalSize(8, 1))

        emulator.feed("\u001B[1;3;4;5;7;8;9mX\u001B[22;23;24;25;27;28;29mY")

        val attributes = emulator.snapshot().rows[0][1].attributes
        assertFalse(attributes.bold)
        assertFalse(attributes.italic)
        assertFalse(attributes.underline)
        assertFalse(attributes.blink)
        assertFalse(attributes.inverse)
        assertFalse(attributes.hidden)
        assertFalse(attributes.strikethrough)
    }

    @Test
    fun `osc title accepts bell and string terminator`() {
        val emulator = TerminalEmulator(TerminalSize(8, 1))

        emulator.feed("\u001B]2;hello\u0007")
        assertEquals("hello", emulator.title)

        emulator.feed("\u001B]0;next\u001B\\")
        assertEquals("next", emulator.snapshot().title)
    }

    @Test
    fun `csi cursor movement commands change position`() {
        val emulator = TerminalEmulator(TerminalSize(10, 4))

        emulator.feed("abcd")
        emulator.feed("\u001B[3;5H")
        assertEquals(Cursor(2, 4), emulator.cursor)
        emulator.feed("\u001B[2A")
        assertEquals(Cursor(0, 4), emulator.cursor)
        emulator.feed("\u001B[3C")
        assertEquals(Cursor(0, 7), emulator.cursor)
        emulator.feed("\u001B[2D")
        assertEquals(Cursor(0, 5), emulator.cursor)
        emulator.feed("\u001B[4G")
        assertEquals(Cursor(0, 3), emulator.cursor)
        emulator.feed("\u001B[2d")
        assertEquals(Cursor(1, 3), emulator.cursor)
        emulator.feed("\u001B[2;3f")
        assertEquals(Cursor(1, 2), emulator.cursor)
    }

    @Test
    fun `cursor next and preceding line reset the column`() {
        val emulator = TerminalEmulator(TerminalSize(8, 3))

        emulator.feed("\u001B[2;3H\u001B[2E")
        assertEquals(Cursor(2, 0), emulator.cursor)
        emulator.feed("\u001B[1F")
        assertEquals(Cursor(1, 0), emulator.cursor)
    }

    @Test
    fun `erase in line removes text from cursor to end`() {
        val emulator = TerminalEmulator(TerminalSize(6, 1))

        emulator.feed("abcdef\u001B[1;4H\u001B[K")

        assertEquals("abc", emulator.snapshot().renderText())
        assertEquals(Cursor(0, 3), emulator.cursor)
    }

    @Test
    fun `erase in display clears from cursor and whole screen`() {
        val partial = TerminalEmulator(TerminalSize(6, 2))
        partial.feed("abcdef\r\nghijkl\u001B[1;2H\u001B[J")
        assertEquals("a", partial.snapshot().renderText())

        val whole = TerminalEmulator(TerminalSize(6, 2))
        whole.feed("abc\r\ndef\u001B[2J")
        assertEquals("", whole.snapshot().renderText())
    }

    @Test
    fun `erase character blanks cells without moving cursor`() {
        val emulator = TerminalEmulator(TerminalSize(6, 1))

        emulator.feed("abcdef\u001B[1;3H\u001B[2X")

        assertEquals("abef", emulator.snapshot().renderText())
        assertEquals(Cursor(0, 2), emulator.cursor)
    }

    @Test
    fun `erase display mode three clears scrollback`() {
        val emulator = TerminalEmulator(TerminalSize(4, 2), scrollbackLimit = 2)
        emulator.feed("a\nb\nc\nd")
        assertEquals("a\nb", emulator.snapshot().renderScrollbackText())

        emulator.feed("\u001B[3J")

        assertEquals("", emulator.snapshot().renderScrollbackText())
        assertEquals("", emulator.snapshot().renderText())
    }

    @Test
    fun `save and restore cursor works through esc and csi forms`() {
        val emulator = TerminalEmulator(TerminalSize(8, 2))
        emulator.feed("AB\r\nCD\u001B[1;1H")

        emulator.feed("\u001B7\u001B[2;2H")
        assertEquals(Cursor(1, 1), emulator.cursor)
        emulator.feed("\u001B8")
        assertEquals(Cursor(0, 0), emulator.cursor)

        emulator.feed("\u001Bs\u001B[2;2H")
        assertEquals(Cursor(1, 1), emulator.cursor)
        emulator.feed("\u001Bu")
        assertEquals(Cursor(0, 0), emulator.cursor)
    }

    @Test
    fun `ris resets screen title attributes and modes`() {
        val emulator = TerminalEmulator(TerminalSize(8, 2))
        emulator.feed("\u001B[31m\u001B]2;title\u0007abc\u001B[?7l\u001B[?25l\u001Bc")

        assertEquals("", emulator.title)
        assertEquals("", emulator.snapshot().renderText())
        assertEquals(Cursor.HOME, emulator.cursor)
        assertTrue(emulator.autoWrap)
        assertTrue(emulator.cursorVisible)
        assertEquals(TerminalAttributes.DEFAULT, emulator.snapshot().rows[0][0].attributes)
    }

    @Test
    fun `scroll region confines scrolling and excludes scrollback`() {
        val emulator = TerminalEmulator(TerminalSize(4, 3))
        emulator.feed("a\r\nb\r\nc")
        emulator.feed("\u001B[2;3r")

        emulator.feed("T\r\nU\r\nV\r\nW")

        assertEquals("Ta\nVc\nW", emulator.snapshot().renderText())
        assertTrue(emulator.snapshot().scrollback.isEmpty())
    }

    @Test
    fun `reverse index scrolls region down at top margin`() {
        val emulator = TerminalEmulator(TerminalSize(4, 3))
        emulator.feed("a\r\nb\r\nc\u001B[2;3r\u001B[2;1H\u001B[1M")

        assertEquals("a\n\nb", emulator.snapshot().renderText())
    }

    @Test
    fun `private modes toggle autowrap and cursor visibility`() {
        val emulator = TerminalEmulator(TerminalSize(4, 2))
        emulator.feed("\u001B[?7l\u001B[?25l")

        assertFalse(emulator.autoWrap)
        assertFalse(emulator.cursorVisible)

        emulator.feed("abcdef")
        assertEquals("abcd", emulator.snapshot().renderText())
        assertEquals(Cursor(0, 3), emulator.cursor)

        emulator.feed("\u001B[?7h\u001B[?25h")
        assertTrue(emulator.autoWrap)
        assertTrue(emulator.cursorVisible)
    }

    @Test
    fun `escape sequence split across feeds is parsed once`() {
        val emulator = TerminalEmulator(TerminalSize(8, 1))
        val bytes = "\u001B[38;5;82mZ".toByteArray(StandardCharsets.UTF_8)

        bytes.forEach { emulator.feed(byteArrayOf(it)) }

        assertEquals("Z", emulator.snapshot().renderText())
        assertEquals(AnsiColor.Indexed(82), emulator.snapshot().rows[0][0].attributes.foreground)
    }

    @Test
    fun `unknown sequences are consumed without rendering`() {
        val emulator = TerminalEmulator(TerminalSize(8, 1))

        emulator.feed("\u001B[999~\u001B(0abc")

        assertEquals("abc", emulator.snapshot().renderText())
    }
}
