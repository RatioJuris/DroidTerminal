/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/** Colour value carried by a terminal cell. */
sealed interface AnsiColor {
    data object Default : AnsiColor

    data class Indexed(val index: Int) : AnsiColor {
        init {
            require(index in 0..255) { "colour index must be between 0 and 255" }
        }
    }

    data class Rgb(val red: Int, val green: Int, val blue: Int) : AnsiColor {
        init {
            require(red in 0..255 && green in 0..255 && blue in 0..255) {
                "RGB components must be between 0 and 255"
            }
        }
    }
}
