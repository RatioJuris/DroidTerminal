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
 * Owns the visible grid, bounded scrollback, character placement, SGR state,
 * cursor/erase operations and scroll-region behaviour. Text selection and the
 * Compose renderer belong to later batches.
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

    var attributes: TerminalAttributes = TerminalAttributes.DEFAULT
        private set

    var title: String = ""
        private set

    var cursorVisible: Boolean = true
        private set

    var autoWrap: Boolean = true
        private set

    var topMargin: Int = 0
        private set

    var bottomMargin: Int = initialSize.rows - 1
        private set

    private var rows: MutableList<MutableList<TerminalCell>> = blankRows(initialSize)
    private val history: MutableList<List<TerminalCell>> = mutableListOf()
    private var lastWrittenColumn: Int = -1
    private var savedCursor: SavedCursor? = null

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
            row[cursor.column] = TerminalCell.of(codePoint, 2, attributes)
            if (cursor.column + 1 < size.columns) {
                row[cursor.column + 1] = TerminalCell.wideContinuation(attributes)
            }
            advance(2)
        } else {
            row[cursor.column] = TerminalCell.of(codePoint, 1, attributes)
            advance(1)
        }
    }

    fun backspace() {
        detach()
        if (cursor.column > 0) cursor = cursor.copy(column = cursor.column - 1)
    }

    fun carriageReturn() {
        detach()
        cursor = cursor.copy(column = 0)
    }

    fun lineFeed() {
        detach()
        if (cursor.row >= bottomMargin) {
            scrollUp()
        } else {
            cursor = cursor.copy(row = cursor.row + 1)
        }
    }

    fun newLine() {
        carriageReturn()
        lineFeed()
    }

    /** IND: moves down one row without changing the column. */
    fun index() = lineFeed()

    /** NEL: moves to the first column of the next row. */
    fun nextLine() = newLine()

    /** RI: moves up one row, scrolling the region down at the top margin. */
    fun reverseIndex() {
        detach()
        if (cursor.row <= topMargin) {
            scrollDown()
        } else {
            cursor = cursor.copy(row = cursor.row - 1)
        }
    }

    fun tab() {
        detach()
        val next = ((cursor.column / TAB_WIDTH) + 1) * TAB_WIDTH
        cursor = cursor.copy(column = minOf(next, size.columns - 1))
    }

    fun cursorUp(count: Int) = moveRow(-positive(count))

    fun cursorDown(count: Int) = moveRow(positive(count))

    fun cursorForward(count: Int) = moveColumn(positive(count))

    fun cursorBackward(count: Int) = moveColumn(-positive(count))

    /** CNL: cursor next line. */
    fun cursorNextLine(count: Int) {
        detach()
        cursor = Cursor(
            row = minOf(size.rows - 1, cursor.row + positive(count)),
            column = 0,
        )
    }

    /** CPL: cursor preceding line. */
    fun cursorPreviousLine(count: Int) {
        detach()
        cursor = Cursor(
            row = maxOf(0, cursor.row - positive(count)),
            column = 0,
        )
    }

    /** CHA: one-based absolute column. */
    fun cursorHorizontalAbsolute(column: Int) {
        detach()
        cursor = cursor.copy(column = (column - 1).coerceIn(0, size.columns - 1))
    }

    /** VPA: one-based absolute row. */
    fun cursorVerticalAbsolute(row: Int) {
        detach()
        cursor = cursor.copy(row = (row - 1).coerceIn(0, size.rows - 1))
    }

    /** CUP/HVP: one-based absolute row and column. */
    fun cursorPosition(row: Int, column: Int) {
        detach()
        cursor = Cursor(
            row = (row - 1).coerceIn(0, size.rows - 1),
            column = (column - 1).coerceIn(0, size.columns - 1),
        )
    }

    /** ED: 0 = to end, 1 = from start, 2 = screen, 3 = screen and scrollback. */
    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseCells(cursor.row, cursor.column, size.columns)
                for (row in cursor.row + 1 until size.rows) eraseCells(row, 0, size.columns)
            }
            1 -> {
                for (row in 0 until cursor.row) eraseCells(row, 0, size.columns)
                eraseCells(cursor.row, 0, cursor.column + 1)
            }
            2, 3 -> {
                if (mode == 3) history.clear()
                for (row in 0 until size.rows) eraseCells(row, 0, size.columns)
            }
            else -> return
        }
        detach()
    }

    /** EL: 0 = to end of line, 1 = from start of line, 2 = whole line. */
    fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> eraseCells(cursor.row, cursor.column, size.columns)
            1 -> eraseCells(cursor.row, 0, cursor.column + 1)
            2 -> eraseCells(cursor.row, 0, size.columns)
            else -> return
        }
        detach()
    }

    /** ECH: erases cells from the cursor without moving it. */
    fun eraseCharacter(count: Int) {
        val from = cursor.column
        val until = minOf(size.columns, from + positive(count))
        eraseCells(cursor.row, from, until)
        detach()
    }

    /** DECSTBM: one-based, inclusive scroll region. Invalid regions are ignored. */
    fun setScrollRegion(top: Int, bottom: Int) {
        val lastRow = size.rows - 1
        val normalizedTop = (top - 1).coerceIn(0, lastRow)
        val normalizedBottom = (if (bottom <= 0) size.rows else bottom - 1).coerceIn(0, lastRow)
        if (normalizedTop >= normalizedBottom) return
        topMargin = normalizedTop
        bottomMargin = normalizedBottom
        cursor = Cursor.HOME
        detach()
    }

    fun saveCursor() {
        savedCursor = SavedCursor(cursor, attributes, autoWrap, wrapPending)
    }

    fun restoreCursor() {
        val saved = savedCursor ?: return
        cursor = Cursor(
            row = saved.cursor.row.coerceIn(0, size.rows - 1),
            column = saved.cursor.column.coerceIn(0, size.columns - 1),
        )
        attributes = saved.attributes
        autoWrap = saved.autoWrap
        wrapPending = saved.wrapPending
        lastWrittenColumn = -1
    }

    /** RIS: full terminal reset. */
    fun reset() {
        rows = blankRows(size)
        history.clear()
        cursor = Cursor.HOME
        wrapPending = false
        lastWrittenColumn = -1
        attributes = TerminalAttributes.DEFAULT
        title = ""
        cursorVisible = true
        autoWrap = true
        topMargin = 0
        bottomMargin = size.rows - 1
        savedCursor = null
    }

    fun setTitle(value: String) {
        title = value
    }

    fun setCursorVisible(value: Boolean) {
        cursorVisible = value
    }

    fun setAutoWrap(value: Boolean) {
        autoWrap = value
        if (!value) wrapPending = false
    }

    /** Applies SGR parameters to cells written from now on. */
    fun applySgr(parameters: List<Int>) {
        val values = if (parameters.isEmpty()) listOf(RESET_SGR) else parameters
        var index = 0
        while (index < values.size) {
            val value = values[index]
            when (value) {
                RESET_SGR -> attributes = TerminalAttributes.DEFAULT
                1 -> attributes = attributes.copy(bold = true)
                2 -> attributes = attributes.copy(faint = true)
                3 -> attributes = attributes.copy(italic = true)
                4 -> attributes = attributes.copy(underline = true)
                5, 6 -> attributes = attributes.copy(blink = true)
                7 -> attributes = attributes.copy(inverse = true)
                8 -> attributes = attributes.copy(hidden = true)
                9 -> attributes = attributes.copy(strikethrough = true)
                21, 22 -> attributes = attributes.copy(bold = false, faint = false)
                23 -> attributes = attributes.copy(italic = false)
                24 -> attributes = attributes.copy(underline = false)
                25 -> attributes = attributes.copy(blink = false)
                27 -> attributes = attributes.copy(inverse = false)
                28 -> attributes = attributes.copy(hidden = false)
                29 -> attributes = attributes.copy(strikethrough = false)
                39 -> attributes = attributes.copy(foreground = AnsiColor.Default)
                49 -> attributes = attributes.copy(background = AnsiColor.Default)
                in 30..37 -> attributes = attributes.copy(foreground = AnsiColor.Indexed(value - 30))
                in 40..47 -> attributes = attributes.copy(background = AnsiColor.Indexed(value - 40))
                in 90..97 -> attributes = attributes.copy(foreground = AnsiColor.Indexed(value - 90 + 8))
                in 100..107 -> attributes = attributes.copy(background = AnsiColor.Indexed(value - 100 + 8))
                38 -> {
                    val (color, lastIndex) = extendedColor(values, index)
                    attributes = attributes.copy(foreground = color)
                    index = lastIndex
                }
                48 -> {
                    val (color, lastIndex) = extendedColor(values, index)
                    attributes = attributes.copy(background = color)
                    index = lastIndex
                }
            }
            index++
        }
    }

    /** Resizes the visible grid, preserving overlapping content and clamping state. */
    fun resize(newSize: TerminalSize) {
        if (newSize == size) return

        val resized = blankRows(newSize)
        for (row in 0 until minOf(size.rows, newSize.rows)) {
            resized[row] = copyRow(rows[row], newSize.columns).toMutableList()
        }
        history.replaceAll { row -> copyRow(row, newSize.columns) }

        val newBottom = if (bottomMargin == size.rows - 1) {
            newSize.rows - 1
        } else {
            minOf(bottomMargin, newSize.rows - 1)
        }
        val newTop = minOf(topMargin, newBottom - 1).coerceAtLeast(0)

        rows = resized
        size = newSize
        cursor = Cursor(
            row = minOf(cursor.row, newSize.rows - 1),
            column = minOf(cursor.column, newSize.columns - 1),
        )
        savedCursor = savedCursor?.let { saved ->
            saved.copy(
                cursor = Cursor(
                    row = minOf(saved.cursor.row, newSize.rows - 1),
                    column = minOf(saved.cursor.column, newSize.columns - 1),
                ),
            )
        }
        topMargin = newTop
        bottomMargin = newBottom
        if (lastWrittenColumn >= newSize.columns) lastWrittenColumn = -1
        wrapPending = false
    }

    fun snapshot(): TerminalSnapshot = TerminalSnapshot(
        size = size,
        rows = rows.map { it.toList() },
        scrollback = history.map { it.toList() },
        cursor = cursor,
        wrapPending = wrapPending,
        title = title,
        cursorVisible = cursorVisible,
        autoWrap = autoWrap,
    )

    private fun extendedColor(values: List<Int>, start: Int): Pair<AnsiColor, Int> {
        var cursorIndex = start + 1
        while (cursorIndex < values.size && values[cursorIndex] == EMPTY_PARAMETER) cursorIndex++
        if (cursorIndex >= values.size) return AnsiColor.Default to values.lastIndex

        return when (values[cursorIndex]) {
            INDEXED_COLOR -> {
                val index = values.getOrNull(cursorIndex + 1)
                val color = if (index != null && index in 0..255) {
                    AnsiColor.Indexed(index)
                } else {
                    AnsiColor.Default
                }
                color to minOf(cursorIndex + 1, values.lastIndex)
            }
            RGB_COLOR -> {
                val components = mutableListOf<Int>()
                var scan = cursorIndex + 1
                while (scan < values.size && components.size < 3) {
                    val component = values[scan]
                    if (component >= 0) components.add(component.coerceIn(0, 255))
                    scan++
                }
                val color = if (components.size == 3) {
                    AnsiColor.Rgb(components[0], components[1], components[2])
                } else {
                    AnsiColor.Default
                }
                color to minOf(scan - 1, values.lastIndex)
            }
            else -> AnsiColor.Default to cursorIndex
        }
    }

    private fun moveRow(delta: Int) {
        detach()
        cursor = cursor.copy(row = (cursor.row + delta).coerceIn(0, size.rows - 1))
    }

    private fun moveColumn(delta: Int) {
        detach()
        cursor = cursor.copy(column = (cursor.column + delta).coerceIn(0, size.columns - 1))
    }

    private fun prepareForWrite(width: Int) {
        if (autoWrap && wrapPending) {
            carriageReturn()
            lineFeed()
        }
        if (autoWrap && width == 2 && cursor.column + 1 >= size.columns) {
            carriageReturn()
            lineFeed()
        }
    }

    private fun advance(cells: Int) {
        val next = cursor.column + cells
        if (next >= size.columns) {
            cursor = cursor.copy(column = size.columns - 1)
            wrapPending = autoWrap
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
        val evicted = rows.removeAt(topMargin)
        if (topMargin == 0 && scrollbackLimit > 0) {
            history.add(evicted.toList())
            while (history.size > scrollbackLimit) history.removeAt(0)
        }
        rows.add(bottomMargin, blankRow(size.columns))
        cursor = cursor.copy(row = bottomMargin)
    }

    private fun scrollDown() {
        rows.removeAt(bottomMargin)
        rows.add(topMargin, blankRow(size.columns))
        cursor = cursor.copy(row = topMargin)
    }

    private fun eraseCells(row: Int, from: Int, until: Int) {
        if (from >= until) return
        val line = rows[row]
        val targets = (from until until).toMutableSet()
        for (column in from until until) {
            val cell = line[column]
            if (cell.width == 2 && column + 1 < until) targets.add(column + 1)
            if (cell.isWideContinuation && column - 1 >= from) targets.add(column - 1)
        }
        targets.forEach { line[it] = blankCell() }
    }

    private fun blankCell(): TerminalCell = TerminalCell.of(TerminalCell.SPACE_CODE, 1, attributes)

    private fun detach() {
        wrapPending = false
        lastWrittenColumn = -1
    }

    private fun positive(value: Int): Int = if (value <= 0) 1 else value

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

    private data class SavedCursor(
        val cursor: Cursor,
        val attributes: TerminalAttributes,
        val autoWrap: Boolean,
        val wrapPending: Boolean,
    )

    private companion object {
        const val TAB_WIDTH = 8
        const val REPLACEMENT_CODE_POINT = 0xFFFD
        const val DEFAULT_SCROLLBACK_LIMIT = 1_000
        const val EMPTY_PARAMETER = -1
        const val RESET_SGR = 0
        const val INDEXED_COLOR = 5
        const val RGB_COLOR = 2
    }
}
