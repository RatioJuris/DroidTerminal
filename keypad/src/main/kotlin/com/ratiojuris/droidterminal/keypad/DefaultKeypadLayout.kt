/*
 * Droid Terminal — shortcut keypad module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.keypad

/** The five-row layout shipped with Droid Terminal. */
object DefaultKeypadLayout {
    val rows: List<List<TerminalKey>> = listOf(
        listOf(
            TerminalKey.Control,
            TerminalKey.Alt,
            TerminalKey.Escape,
            TerminalKey.Tab,
            TerminalKey.Shift,
            TerminalKey.Fn,
        ),
        listOf(
            TerminalKey.Arrow(ArrowDirection.UP),
            TerminalKey.Arrow(ArrowDirection.DOWN),
            TerminalKey.Arrow(ArrowDirection.LEFT),
            TerminalKey.Arrow(ArrowDirection.RIGHT),
            TerminalKey.Home,
            TerminalKey.End,
        ),
        listOf(
            TerminalKey.ControlLetter('C'),
            TerminalKey.ControlLetter('D'),
            TerminalKey.ControlLetter('Z'),
            TerminalKey.ControlLetter('L'),
            TerminalKey.ControlLetter('R'),
            TerminalKey.ControlLetter('A'),
        ),
        listOf(
            TerminalKey.ControlLetter('E'),
            TerminalKey.ControlLetter('W'),
            TerminalKey.ControlLetter('U'),
            TerminalKey.ControlLetter('K'),
            TerminalKey.ControlLetter('P'),
            TerminalKey.ControlLetter('N'),
        ),
        (1..12).map { TerminalKey.FunctionKey(it) },
    )

    val keys: List<TerminalKey>
        get() = rows.flatten()
}
