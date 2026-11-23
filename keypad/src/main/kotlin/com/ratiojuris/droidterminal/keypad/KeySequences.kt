/*
 * Droid Terminal — shortcut keypad module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.keypad

/** Translates keypad actions into terminal byte sequences. */
object KeySequences {
    private const val ESC = '\u001B'

    /** Returns the terminal byte sequence for [key] under [modifiers]. */
    fun sequence(key: TerminalKey, modifiers: ModifierState = ModifierState.NONE): String {
        val base = when (key) {
            TerminalKey.Control, TerminalKey.Alt, TerminalKey.Shift, TerminalKey.Fn -> ""
            TerminalKey.Escape -> ESC.toString()
            TerminalKey.Tab -> if (modifiers.isEnabled(Modifier.SHIFT)) "${ESC}[Z" else "\t"
            is TerminalKey.ControlLetter -> controlByte(key.letter).toString()
            is TerminalKey.Arrow -> arrowSequence(key.direction, modifiers)
            TerminalKey.Home -> "${ESC}[H"
            TerminalKey.End -> "${ESC}[F"
            is TerminalKey.FunctionKey -> functionSequence(key.number)
            is TerminalKey.Custom -> applyControlToCustom(key.sequence, modifiers)
        }

        if (base.isEmpty()) return ""

        val withAlt = if (modifiers.isEnabled(Modifier.ALT)) "$ESC$base" else base
        return withAlt
    }

    /** Convenience for transports that need raw bytes. */
    fun bytes(key: TerminalKey, modifiers: ModifierState = ModifierState.NONE): ByteArray =
        sequence(key, modifiers).map { it.code.toByte() }.toByteArray()

    private fun arrowSequence(direction: ArrowDirection, modifiers: ModifierState): String {
        val final = when (direction) {
            ArrowDirection.UP -> "A"
            ArrowDirection.DOWN -> "B"
            ArrowDirection.RIGHT -> "C"
            ArrowDirection.LEFT -> "D"
        }
        return if (modifiers.isEnabled(Modifier.SHIFT)) "${ESC}[1;2$final" else "$ESC[$final"
    }

    private fun functionSequence(number: Int): String = when (number) {
        1 -> "${ESC}OP"
        2 -> "${ESC}OQ"
        3 -> "${ESC}OR"
        4 -> "${ESC}OS"
        5 -> "${ESC}[15~"
        6 -> "${ESC}[17~"
        7 -> "${ESC}[18~"
        8 -> "${ESC}[19~"
        9 -> "${ESC}[20~"
        10 -> "${ESC}[21~"
        11 -> "${ESC}[23~"
        12 -> "${ESC}[24~"
        else -> throw IllegalArgumentException("unsupported function key F$number")
    }

    private fun applyControlToCustom(sequence: String, modifiers: ModifierState): String {
        if (!modifiers.isEnabled(Modifier.CONTROL) || sequence.length != 1) return sequence
        val character = sequence.single()
        return if (character.isLetter()) controlByte(character).toString() else sequence
    }

    private fun controlByte(letter: Char): Char {
        val upper = letter.uppercaseChar()
        require(upper in 'A'..'Z') { "control shortcuts require an ASCII letter" }
        return (upper.code - '@'.code).toChar()
    }
}
