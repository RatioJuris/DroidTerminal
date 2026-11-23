/*
 * Droid Terminal — shortcut keypad module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.keypad

/** Direction of a terminal cursor key. */
enum class ArrowDirection(val label: String) {
    UP("↑"),
    DOWN("↓"),
    LEFT("←"),
    RIGHT("→"),
}

/**
 * A key that can appear on the configurable terminal shortcut keypad.
 *
 * The model is UI-independent: the keypad module only describes what should be
 * transmitted, never how the terminal or SSH transport is implemented.
 */
sealed interface TerminalKey {
    val label: String

    data object Control : TerminalKey {
        override val label: String = "Ctrl"
    }

    data object Alt : TerminalKey {
        override val label: String = "Alt"
    }

    data object Escape : TerminalKey {
        override val label: String = "Esc"
    }

    data object Tab : TerminalKey {
        override val label: String = "Tab"
    }

    data object Shift : TerminalKey {
        override val label: String = "Shift"
    }

    data object Fn : TerminalKey {
        override val label: String = "Fn"
    }

    data class ControlLetter(val letter: Char) : TerminalKey {
        override val label: String
            get() = "Ctrl+${letter.uppercaseChar()}"

        init {
            require(letter.isLetter()) { "control shortcuts require a letter" }
        }
    }

    data class Arrow(val direction: ArrowDirection) : TerminalKey {
        override val label: String
            get() = direction.label
    }

    data object Home : TerminalKey {
        override val label: String = "Home"
    }

    data object End : TerminalKey {
        override val label: String = "End"
    }

    data class FunctionKey(val number: Int) : TerminalKey {
        override val label: String
            get() = "F$number"

        init {
            require(number in 1..12) { "function key must be between F1 and F12" }
        }
    }

    /**
     * User-defined key. [sequence] contains one byte per character and must
     * therefore use ASCII or ISO-8859-1 compatible characters only.
     */
    data class Custom(override val label: String, val sequence: String) : TerminalKey {
        init {
            require(label.isNotBlank()) { "custom key label must not be blank" }
            require(sequence.all { it.code in 0..0xFF }) {
                "custom sequence must contain only single-byte characters"
            }
        }
    }
}
