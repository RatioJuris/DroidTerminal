/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/**
 * Mutable visible screen buffer for a VT-compatible terminal.
 *
 * It owns the visible grid, bounded scrollback history, character placement
 * and basic C0 control behaviour. Text selection and ANSI styling arrive in
 * later batches.
 */
class TerminalScreen(
    initialSize: TerminalSize = TerminalSize.DEFAULT,
    scrollbackLimit: Int = DEFAULT_SCROLLBACK_LIMIT,
) {
    val scrollbackLimit: Int = scrollbackLimit

    var size: TerminalSize = initialSize
        private set

    var cursor: Cursor = Cursor.HOME
        private set

    var wrapPending: Boolean = false
        private set

    private var rows: MutableList<MutableList<TerminalCell>> = blankRows(initialSize)
    private val history: MutableList<List<TerminalCell>> = mutableListOf()
    private var lastWrittenColumn: Int = -1

    init {
        require(scrollbackLimit >= 0) { "scrollbackLimit must not be negative" }
    }

    /** Places one Unicode code point and advances the cursor according to its width. */
    fun writeCodePoint(codePoint: Int) {
        require(Character.isValidCodePoint(codePoint)) { "invalid Unicode code point: $codePoint" }

        val width = UnicodeWidth.of(codePoint)
        if (width == 0) {
            appendCombiningMark(codePoint)
            return
        }

        prepareForWrite(width)
        val row = rows[cursor.row]
        lastWrittenColumn = cursor.column
        if (width == 2) {
            row[cursor.column] = TerminalCell.of(codePoint, 2)
            if (cursor.column + 1 < size.columns) {
                row[cursor.column + 1] = TerminalCell.wideContinuation()
            }
            advance(2)
        } else {
            row[cursor.column] = TerminalCell.of(codePoint, 1)
            advance(1)
        }
    }

    fun backspace() {
        wrapPending = false
        lastWrittenColumn = -1
        if (cursor.column > 0) cursor = cursor.copy(column = cursor.column - 1)
    }

    fun carriageReturn() {
        wrapPending = false
        lastWrittenColumn = -1
        cursor = cursor.copy(column = 0)
    }

    fun lineFeed() {
        wrapPending = false
        lastWrittenColumn = -1
        if (cursor.row + 1 < size.rows) {
            cursor = cursor.copy(row = cursor.row + 1)
        } else {
            scrollUp()
        }
    }

    fun newLine() {
        carriageReturn()
        lineFeed()
    }

    fun tab() {
        wrapPending = false
        lastWrittenColumn = -1
        val next = ((cursor.column / TAB_WIDTH) + 1) * TAB_WIDTH
        cursor = cursor.copy(column = minOf(next, size.columns - 1))
    }

    /** Resizes the visible grid, preserving overlapping content and clamping the cursor. */
    fun resize(newSize: TerminalSize) {
        if (newSize == size) return

        val resized = blankRows(newSize)
        for (row in 0 until minOf(size.rows, newSize.rows)) {
            resized[row] = copyRow(rows[row], newSize.columns).toMutableList()
        }
        history.replaceAll { row -> copyRow(row, newSize.columns) }

        rows = resized
        size = newSize
        cursor = Cursor(
            row = minOf(cursor.row, newSize.rows - 1),
            column = minOf(cursor.column, newSize.columns - 1),
        )
        if (lastWrittenColumn >= newSize.columns) lastWrittenColumn = -1
        wrapPending = false
    }

    fun snapshot(): TerminalSnapshot = TerminalSnapshot(
        size = size,
        rows = rows.map { it.toList() },
        scrollback = history.map { it.toList() },
        cursor = cursor,
        wrapPending = wrapPending,
    )

    private fun prepareForWrite(width: Int) {
        if (wrapPending) {
            carriageReturn()
            lineFeed()
        }
        if (width == 2 && cursor.column + 1 >= size.columns) {
            carriageReturn()
            lineFeed()
        }
    }

    private fun advance(cells: Int) {
        val next = cursor.column + cells
        if (next >= size.columns) {
            cursor = cursor.copy(column = size.columns - 1)
            wrapPending = true
        } else {
            cursor = cursor.copy(column = next)
        }
    }

    private fun appendCombiningMark(codePoint: Int) {
        val target = lastWrittenColumn
        if (target < 0 || target >= size.columns || rows[cursor.row][target].isWideContinuation) {
            writeCodePoint(REPLACEMENT_CODE_POINT)
        } else {
            rows[cursor.row][target] = rows[cursor.row][target].withCombining(codePoint)
        }
    }

    private fun scrollUp() {
        val evicted = rows.removeAt(0).toList()
        if (scrollbackLimit > 0) {
            history.add(evicted)
            while (history.size > scrollbackLimit) history.removeAt(0)
        }
        rows.add(blankRow(size.columns))
        cursor = cursor.copy(row = size.rows - 1)
    }

    private fun copyRow(source: List<TerminalCell>, columns: Int): List<TerminalCell> =
        List(columns) { column ->
            val cell = source.getOrNull(column) ?: TerminalCell.BLANK
            when {
                cell.isWideContinuation -> {
                    val baseIsVisible = column > 0 && source[column - 1].width == 2
                    if (baseIsVisible) cell else TerminalCell.BLANK
                }
                cell.width == 2 && column + 1 >= columns -> TerminalCell.BLANK
                else -> cell
            }
        }

    private fun blankRow(columns: Int): MutableList<TerminalCell> =
        MutableList(columns) { TerminalCell.BLANK }

    private fun blankRows(size: TerminalSize): MutableList<MutableList<TerminalCell>> =
        MutableList(size.rows) { blankRow(size.columns) }

    private companion object {
        const val TAB_WIDTH = 8
        const val REPLACEMENT_CODE_POINT = 0xFFFD
        const val DEFAULT_SCROLLBACK_LIMIT = 1_000
    }
}
