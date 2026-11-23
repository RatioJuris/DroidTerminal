/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalScreenTest {
    @Test
    fun `blank screen starts at home`() {
        val snapshot = TerminalScreen(TerminalSize(4, 2)).snapshot()

        assertEquals(2, snapshot.rows.size)
        assertTrue(snapshot.rows.all { row -> row.all { cell -> cell.isBlank } })
        assertEquals(Cursor.HOME, snapshot.cursor)
        assertFalse(snapshot.wrapPending)
    }

    @Test
    fun `printing advances cursor and stores text`() {
        val screen = TerminalScreen(TerminalSize(4, 2))

        screen.writeCodePoint('A'.code)
        screen.writeCodePoint('B'.code)

        val snapshot = screen.snapshot()
        assertEquals("AB", snapshot.renderText())
        assertEquals(Cursor(0, 2), snapshot.cursor)
    }

    @Test
    fun `right margin defers wrapping`() {
        val screen = TerminalScreen(TerminalSize(4, 2))
        "abcd".forEach { screen.writeCodePoint(it.code) }

        val beforeWrap = screen.snapshot()
        assertEquals("abcd", beforeWrap.renderText())
        assertEquals(Cursor(0, 3), beforeWrap.cursor)
        assertTrue(beforeWrap.wrapPending)

        screen.writeCodePoint('e'.code)

        val afterWrap = screen.snapshot()
        assertEquals("abcd\ne", afterWrap.renderText())
        assertEquals(Cursor(1, 1), afterWrap.cursor)
        assertFalse(afterWrap.wrapPending)
    }

    @Test
    fun `control keys move cursor`() {
        val screen = TerminalScreen()

        screen.writeCodePoint('a'.code)
        screen.backspace()
        screen.writeCodePoint('b'.code)
        screen.tab()
        screen.carriageReturn()
        screen.lineFeed()
        screen.writeCodePoint('c'.code)

        val snapshot = screen.snapshot()
        assertEquals("b\nc", snapshot.renderText())
        assertEquals(Cursor(1, 1), snapshot.cursor)
    }

    @Test
    fun `line feed at bottom scrolls visible screen`() {
        val screen = TerminalScreen(TerminalSize(4, 2))

        screen.writeCodePoint('a'.code)
        screen.newLine()
        screen.writeCodePoint('b'.code)
        screen.newLine()
        screen.writeCodePoint('c'.code)

        assertEquals("b\nc", screen.snapshot().renderText())
    }

    @Test
    fun `wide characters reserve a continuation cell`() {
        val screen = TerminalScreen(TerminalSize(4, 1))

        screen.writeCodePoint(0x6F22)
        screen.writeCodePoint('A'.code)

        val snapshot = screen.snapshot()
        assertEquals(2, snapshot.rows[0][0].width)
        assertTrue(snapshot.rows[0][1].isWideContinuation)
        assertEquals('A'.code, snapshot.rows[0][2].baseCodePoint)
        assertEquals(Cursor(0, 3), snapshot.cursor)
    }

    @Test
    fun `combining marks attach to previous base`() {
        val screen = TerminalScreen(TerminalSize(4, 1))

        screen.writeCodePoint('e'.code)
        screen.writeCodePoint(0x0301)

        val snapshot = screen.snapshot()
        assertEquals(listOf('e'.code, 0x0301), snapshot.rows[0][0].codePoints)
        assertEquals(1, snapshot.rows[0][0].width)
        assertEquals(Cursor(0, 1), snapshot.cursor)
    }

    @Test
    fun `combining mark after final column attaches to written cell`() {
        val screen = TerminalScreen(TerminalSize(4, 2))

        "abcd".forEach { screen.writeCodePoint(it.code) }
        screen.writeCodePoint(0x0301)

        val snapshot = screen.snapshot()
        assertEquals(listOf('d'.code, 0x0301), snapshot.rows[0][3].codePoints)
        assertEquals('c'.code, snapshot.rows[0][2].baseCodePoint)
    }

    @Test
    fun `scrollback retains evicted rows`() {
        val screen = TerminalScreen(TerminalSize(4, 2), scrollbackLimit = 2)

        screen.writeCodePoint('a'.code)
        screen.newLine()
        screen.writeCodePoint('b'.code)
        screen.newLine()
        screen.writeCodePoint('c'.code)
        screen.newLine()
        screen.writeCodePoint('d'.code)

        val snapshot = screen.snapshot()
        assertEquals("a\nb", snapshot.renderScrollbackText())
        assertEquals("c\nd", snapshot.renderText())
        assertEquals("a\nb\nc\nd", snapshot.renderHistoryText())
    }

    @Test
    fun `scrollback limit drops oldest rows`() {
        val screen = TerminalScreen(TerminalSize(4, 2), scrollbackLimit = 1)

        screen.writeCodePoint('a'.code)
        screen.newLine()
        screen.writeCodePoint('b'.code)
        screen.newLine()
        screen.writeCodePoint('c'.code)
        screen.newLine()
        screen.writeCodePoint('d'.code)

        assertEquals("b", screen.snapshot().renderScrollbackText())
    }

    @Test
    fun `negative scrollback limit is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalScreen(TerminalSize(4, 2), scrollbackLimit = -1)
        }
    }

    @Test
    fun `resize normalizes scrollback width`() {
        val screen = TerminalScreen(TerminalSize(4, 2), scrollbackLimit = 2)
        listOf("abcd", "efgh", "ijkl", "mnop").forEachIndexed { index, line ->
            line.forEach { screen.writeCodePoint(it.code) }
            if (index < 3) screen.newLine()
        }

        screen.resize(TerminalSize(2, 2))

        val snapshot = screen.snapshot()
        assertTrue(snapshot.scrollback.all { it.size == 2 })
        assertEquals("ab\nef", snapshot.renderScrollbackText())
        assertEquals("ij\nmn", snapshot.renderText())
    }

    @Test
    fun `resize preserves overlap and clamps cursor`() {
        val screen = TerminalScreen(TerminalSize(4, 2))
        "ab".forEach { screen.writeCodePoint(it.code) }
        screen.newLine()
        "cd".forEach { screen.writeCodePoint(it.code) }

        screen.resize(TerminalSize(2, 1))

        val snapshot = screen.snapshot()
        assertEquals(TerminalSize(2, 1), snapshot.size)
        assertEquals("ab", snapshot.renderText())
        assertEquals(Cursor(0, 1), snapshot.cursor)
    }
}
