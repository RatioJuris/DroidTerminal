/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/** Typed actions emitted by [VtParser] for the emulator to apply. */
internal sealed interface VtAction {
    data class Print(val codePoint: Int) : VtAction

    data class Execute(val control: Int) : VtAction

    /** Empty parameters are represented by -1, as in `CSI 1;;3H`. */
    data class Csi(
        val final: Char,
        val parameters: List<Int>,
        val privatePrefix: Char? = null,
        val intermediate: Char? = null,
    ) : VtAction

    data class Osc(val command: Int, val data: String) : VtAction

    data class Escape(val final: Char) : VtAction

    /** SS3 (`ESC O`) final byte, used by F1-F4 on the shortcut keypad. */
    data class Ss3(val final: Char) : VtAction
}
