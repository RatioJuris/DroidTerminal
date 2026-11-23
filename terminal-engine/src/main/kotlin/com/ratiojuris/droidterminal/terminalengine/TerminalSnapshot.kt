/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/** Immutable view of the visible screen at a point in time. */
data class TerminalSnapshot(
    val size: TerminalSize,
    val rows: List<List<TerminalCell>>,
    val scrollback: List<List<TerminalCell>>,
    val cursor: Cursor,
    val wrapPending: Boolean,
) {
    init {
        require(rows.size == size.rows) { "snapshot row count must match terminal size" }
        require(rows.all { it.size == size.columns }) { "snapshot row width must match terminal size" }
        require(scrollback.all { it.size == size.columns }) { "scrollback width must match terminal size" }
    }

    /** Plain visible text for diagnostics and tests; trailing blank cells and rows are removed. */
    fun renderText(): String = renderLines(rows)

    /** Scrollback text, oldest first; trailing blank cells are removed. */
    fun renderScrollbackText(): String = renderLines(scrollback)

    /** Scrollback followed by visible text, for diagnostics and tests. */
    fun renderHistoryText(): String = renderLines(scrollback + rows)

    private fun renderLines(source: List<List<TerminalCell>>): String {
        val lines = source.map { row ->
            row.joinToString("") { cell -> if (cell.isWideContinuation) "" else cell.text }
                .trimEnd(' ')
        }
        return lines.dropLastWhile { it.isEmpty() }.joinToString("\n")
    }
}
