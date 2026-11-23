/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/** SGR state applied to cells written after the attribute change. */
data class TerminalAttributes(
    val foreground: AnsiColor = AnsiColor.Default,
    val background: AnsiColor = AnsiColor.Default,
    val bold: Boolean = false,
    val faint: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val blink: Boolean = false,
    val inverse: Boolean = false,
    val hidden: Boolean = false,
    val strikethrough: Boolean = false,
) {
    companion object {
        val DEFAULT: TerminalAttributes = TerminalAttributes()
    }
}
