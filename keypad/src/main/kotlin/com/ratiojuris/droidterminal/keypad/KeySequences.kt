/*
 * Droid Terminal — keyboard module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.keypad

/**
 * Translates keyboard actions into terminal byte sequences.
 *
 * Encoding follows xterm's modifyOtherKeys convention: modified navigation and
 * function keys carry `1;modifier` (or `number;modifier`) CSI parameters, where
 * modifier = 1 + Shift + 2*Alt + 4*Ctrl + 8*Meta. DECCKM changes unmodified
 * arrows and Home/End from CSI to SS3; modified forms stay CSI.
 */
object KeySequences {
    private const val ESC = '\u001B'
    private const val PASTE_START = "${ESC}[200~"
    private const val PASTE_END = "${ESC}[201~"

    /** xterm CSI numbers for F5-F12; F6/F7 and F11/F12 are deliberately non-contiguous. */
    private val FUNCTION_TILDE_NUMBERS = mapOf(
        5 to 15,
        6 to 17,
        7 to 18,
        8 to 19,
        9 to 20,
        10 to 21,
        11 to 23,
        12 to 24,
    )

    /** Returns the terminal byte sequence for [key] under [modifiers] and [modes]. */
    fun sequence(
        key: TerminalKey,
        modifiers: ModifierState = ModifierState.NONE,
        modes: TerminalModes = TerminalModes(),
    ): String {
        val parameter = modifierParameter(modifiers)
        val modified = parameter > 1

        val base = when (key) {
            TerminalKey.Control,
            TerminalKey.Alt,
            TerminalKey.Shift,
            TerminalKey.Fn,
            TerminalKey.CapsLock,
            -> ""
            TerminalKey.Escape -> ESC.toString()
            TerminalKey.Tab -> when {
                modifiers.isEnabled(Modifier.SHIFT) -> "${ESC}[Z"
                else -> "\t"
            }
            TerminalKey.Enter -> if (modifiers.isEnabled(Modifier.SHIFT)) "\n" else "\r"
            TerminalKey.Backspace -> if (modifiers.isEnabled(Modifier.SHIFT)) "\b" else 0x7F.toChar().toString()
            TerminalKey.Delete -> tildeKey(3, parameter, modified)
            TerminalKey.Insert -> tildeKey(2, parameter, modified)
            TerminalKey.PageUp -> tildeKey(5, parameter, modified)
            TerminalKey.PageDown -> tildeKey(6, parameter, modified)
            is TerminalKey.Character -> characterSequence(key.char, modifiers)
            is TerminalKey.ControlLetter -> controlByte(key.letter).toString()
            is TerminalKey.Arrow -> letterKey(arrowFinal(key.direction), parameter, modified, modes)
            TerminalKey.Home -> letterKey('H', parameter, modified, modes)
            TerminalKey.End -> letterKey('F', parameter, modified, modes)
            is TerminalKey.FunctionKey -> functionSequence(key.number, parameter, modified)
            is TerminalKey.Custom -> applyControlToCustom(key.sequence, modifiers)
        }

        if (base.isEmpty()) return ""

        // Alt/Meta prefix text and editing keys; navigation keys carry their own
        // modifier parameter so prefixing would double-report the modifier.
        val wantsPrefix = modifiers.isEnabled(Modifier.ALT) || modifiers.isEnabled(Modifier.META)
        val prefixed = if (wantsPrefix && isPrefixableKey(key)) "$ESC$base" else base
        return prefixed
    }

    /**
     * Convenience for transports that need raw bytes. Printable Unicode is UTF-8;
     * [TerminalKey.Custom] keeps its documented single-byte Latin-1 encoding.
     */
    fun bytes(
        key: TerminalKey,
        modifiers: ModifierState = ModifierState.NONE,
        modes: TerminalModes = TerminalModes(),
    ): ByteArray {
        val output = sequence(key, modifiers, modes)
        return if (key is TerminalKey.Custom) {
            output.map { it.code.toByte() }.toByteArray()
        } else {
            output.toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Encodes clipboard text for the remote pty. When [bracketed] is true the
     * text is wrapped in DECSET 2004 markers. ESC and NUL are removed so pasted
     * content cannot inject escape sequences into the remote terminal.
     */
    fun paste(text: String, bracketed: Boolean): String {
        val safe = buildString {
            text.forEach { char ->
                if (char != ESC && char != '\u0000') append(char)
            }
        }
        return if (bracketed) "$PASTE_START$safe$PASTE_END" else safe
    }

    private fun isPrefixableKey(key: TerminalKey): Boolean = key is TerminalKey.Character ||
        key is TerminalKey.ControlLetter ||
        key is TerminalKey.Custom ||
        key is TerminalKey.Tab ||
        key is TerminalKey.Enter ||
        key is TerminalKey.Backspace

    private fun modifierParameter(modifiers: ModifierState): Int {
        var value = 1
        if (modifiers.isEnabled(Modifier.SHIFT)) value += 1
        if (modifiers.isEnabled(Modifier.ALT)) value += 2
        if (modifiers.isEnabled(Modifier.CONTROL)) value += 4
        if (modifiers.isEnabled(Modifier.META)) value += 8
        return value
    }

    private fun letterKey(final: Char, parameter: Int, modified: Boolean, modes: TerminalModes): String = when {
        modified -> "${ESC}[1;$parameter$final"
        modes.applicationCursorKeys -> "${ESC}O$final"
        else -> "$ESC[$final"
    }

    private fun tildeKey(number: Int, parameter: Int, modified: Boolean): String =
        if (modified) "${ESC}[$number;$parameter~" else "${ESC}[$number~"

    private fun functionSequence(number: Int, parameter: Int, modified: Boolean): String {
        val tildeNumber = FUNCTION_TILDE_NUMBERS[number]
        if (tildeNumber != null) {
            return if (modified) "${ESC}[$tildeNumber;$parameter~" else "${ESC}[$tildeNumber~"
        }
        val final = when (number) {
            in 1..4 -> "PQRS"[number - 1]
            else -> throw IllegalArgumentException("unsupported function key F$number")
        }
        return if (modified) "${ESC}[1;$parameter$final" else "${ESC}O$final"
    }

    private fun characterSequence(char: Char, modifiers: ModifierState): String {
        val controlled = if (modifiers.isEnabled(Modifier.CONTROL)) controlChar(char) else char.toString()
        return controlled
    }

    private fun controlChar(char: Char): String {
        val code = char.code
        return when {
            code in 0x61..0x7A -> (code - 0x60).toChar().toString() // a..z
            code in 0x41..0x5A -> (code - 0x40).toChar().toString() // A..Z
            char == ' ' -> "\u0000"
            char == '[' -> ESC.toString()
            code in 0x5C..0x5F -> (code - 0x40).toChar().toString() // \ ] ^ _
            else -> char.toString()
        }
    }

    private fun arrowFinal(direction: ArrowDirection): Char = when (direction) {
        ArrowDirection.UP -> 'A'
        ArrowDirection.DOWN -> 'B'
        ArrowDirection.RIGHT -> 'C'
        ArrowDirection.LEFT -> 'D'
    }

    private fun applyControlToCustom(sequence: String, modifiers: ModifierState): String {
        if (!modifiers.isEnabled(Modifier.CONTROL) || sequence.length != 1) return sequence
        val character = sequence.single()
        return if (character.code in 0x61..0x7A || character.code in 0x41..0x5A) {
            controlByte(character).toString()
        } else {
            sequence
        }
    }

    private fun controlByte(letter: Char): Char {
        val upper = letter.uppercaseChar()
        require(upper in 'A'..'Z') { "control shortcuts require an ASCII letter" }
        return (upper.code - '@'.code).toChar()
    }
}
