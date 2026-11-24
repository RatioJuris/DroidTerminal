/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/**
 * A selection over terminal cells.
 *
 * Rows are absolute line indices: scrollback first, then the visible screen.
 * Columns are zero-based cell indices within a line. An anchor/head pair lets
 * both the selection pad and touch input extend the same selection.
 */
data class TerminalSelection(
    val anchorRow: Int,
    val anchorColumn: Int,
    val headRow: Int,
    val headColumn: Int,
) {
    val startRow: Int get() = minOf(anchorRow, headRow)
    val endRow: Int get() = maxOf(anchorRow, headRow)

    val startColumn: Int
        get() = when {
            anchorRow == headRow -> minOf(anchorColumn, headColumn)
            anchorRow < headRow -> anchorColumn
            else -> headColumn
        }

    val endColumn: Int
        get() = when {
            anchorRow == headRow -> maxOf(anchorColumn, headColumn)
            anchorRow < headRow -> headColumn
            else -> anchorColumn
        }

    /** Cell columns selected on [row], or null when the row is outside the range. */
    fun columnRangeForRow(row: Int): IntRange? = when {
        row !in startRow..endRow -> null
        else -> (if (row == startRow) startColumn else 0)..
            (if (row == endRow) endColumn else Int.MAX_VALUE)
    }
}
