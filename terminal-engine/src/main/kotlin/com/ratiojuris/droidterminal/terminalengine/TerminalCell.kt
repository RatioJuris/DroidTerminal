/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/**
 * One visible terminal cell.
 *
 * A cell holds a base code point, any combining marks attached to it and the
 * SGR attributes active when it was written. Wide characters occupy the cell
 * they start in plus a continuation cell that renderers skip.
 */
data class TerminalCell(
    val codePoints: List<Int>,
    val width: Int,
    val isWideContinuation: Boolean = false,
    val attributes: TerminalAttributes = TerminalAttributes.DEFAULT,
) {
    init {
        require(codePoints.isNotEmpty()) { "a cell needs at least one code point" }
        require(width in 0..2) { "width must be between 0 and 2" }
        require(!isWideContinuation || width == 0) { "continuation cells have zero width" }
    }

    val baseCodePoint: Int
        get() = codePoints.first()

    val text: String
        get() = if (isWideContinuation) {
            ""
        } else {
            buildString(codePoints.size) {
                codePoints.forEach { appendCodePoint(it) }
            }
        }

    val isBlank: Boolean
        get() = !isWideContinuation && width == 1 && codePoints.singleOrNull() == SPACE_CODE

    fun withCombining(codePoint: Int): TerminalCell = copy(codePoints = codePoints + codePoint)

    companion object {
        const val SPACE_CODE: Int = 0x20

        val BLANK: TerminalCell = TerminalCell(listOf(SPACE_CODE), 1)

        fun of(
            codePoint: Int,
            width: Int,
            attributes: TerminalAttributes = TerminalAttributes.DEFAULT,
        ): TerminalCell {
            require(width in 1..2) { "printed cell width must be 1 or 2" }
            return TerminalCell(listOf(codePoint), width, attributes = attributes)
        }

        fun wideContinuation(attributes: TerminalAttributes = TerminalAttributes.DEFAULT): TerminalCell =
            TerminalCell(listOf(SPACE_CODE), 0, true, attributes)
    }
}
