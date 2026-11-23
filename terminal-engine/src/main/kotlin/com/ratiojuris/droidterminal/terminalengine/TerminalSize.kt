/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/**
 * Character dimensions of a terminal viewport.
 *
 * The terminal engine must remain independent of the SSH transport, so this
 * value object deliberately contains no networking or UI dependencies.
 */
data class TerminalSize(val columns: Int, val rows: Int) {
    init {
        require(columns > 0) { "columns must be positive" }
        require(rows > 0) { "rows must be positive" }
    }

    val cellCount: Int
        get() = columns * rows

    /** A size is usable only when both dimensions fit in a signed 16-bit int. */
    val isWithinProtocolLimits: Boolean
        get() = columns <= MAX_DIMENSION && rows <= MAX_DIMENSION

    companion object {
        const val MAX_DIMENSION: Int = 32_767

        val DEFAULT: TerminalSize = TerminalSize(columns = 80, rows = 24)
    }
}
