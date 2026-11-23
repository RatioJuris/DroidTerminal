/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/** Zero-based cursor position in a terminal screen. */
data class Cursor(val row: Int, val column: Int) {
    init {
        require(row >= 0) { "row must not be negative" }
        require(column >= 0) { "column must not be negative" }
    }

    companion object {
        val HOME: Cursor = Cursor(0, 0)
    }
}
