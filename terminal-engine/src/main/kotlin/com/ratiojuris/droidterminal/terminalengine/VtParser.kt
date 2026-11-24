/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/**
 * Incremental VT parser.
 *
 * One input code point yields at most one action. Sequences may be split across
 * feeds; unterminated or malformed sequences are discarded on reset or when a
 * new escape starts. Parameter and string lengths are bounded so a hostile
 * host cannot grow memory without limit.
 */
internal class VtParser {
    private enum class State {
        GROUND,
        ESCAPE,
        ESCAPE_INTERMEDIATE,
        SS3,
        CSI,
        OSC,
        DCS,
        CHARSET,
    }

    private var state = State.GROUND
    private val parameters = ArrayList<Int>(INITIAL_PARAMETER_CAPACITY)
    private var currentParameter: Int? = null
    private var parameterStarted = false
    private var parameterOverflow = false
    private var privatePrefix: Char? = null
    private var intermediate: Char? = null
    private var escapeIntermediate: Char? = null
    private val stringData = StringBuilder()
    private var stringIsOsc = false
    private var awaitingStringTerminator = false

    fun consume(codePoint: Int): VtAction? {
        if (state == State.OSC || state == State.DCS) return consumeString(codePoint)

        return when (state) {
            State.GROUND -> consumeGround(codePoint)
            State.ESCAPE -> consumeEscape(codePoint)
            State.ESCAPE_INTERMEDIATE -> consumeEscapeIntermediate(codePoint)
            State.SS3 -> consumeSs3(codePoint)
            State.CSI -> consumeCsi(codePoint)
            State.CHARSET -> {
                state = if (codePoint == ESC) State.ESCAPE else State.GROUND
                null
            }
            State.OSC, State.DCS -> error("string states are handled above")
        }
    }

    /** Abandons any partial sequence without emitting an action. */
    fun reset() {
        state = State.GROUND
        clearCsi()
        clearString()
        escapeIntermediate = null
    }

    private fun consumeGround(codePoint: Int): VtAction? = when {
        codePoint == ESC -> {
            state = State.ESCAPE
            null
        }
        codePoint < 0x20 -> VtAction.Execute(codePoint)
        codePoint == 0x7F -> null
        else -> VtAction.Print(codePoint)
    }

    private fun consumeEscape(codePoint: Int): VtAction? = when {
        codePoint == ESC -> null // Cancels and restarts the escape sequence.
        codePoint < 0x20 -> VtAction.Execute(codePoint) // State remains ESCAPE.
        codePoint == '['.code -> {
            clearCsi()
            state = State.CSI
            null
        }
        codePoint == ']'.code -> {
            startString(isOsc = true)
            null
        }
        codePoint == 'P'.code || codePoint == 'X'.code ||
            codePoint == '^'.code || codePoint == '_'.code -> {
            startString(isOsc = false)
            null
        }
        codePoint in listOf('('.code, ')'.code, '*'.code, '+'.code, '#'.code) -> {
            state = State.CHARSET
            null
        }
        codePoint == 'O'.code -> {
            state = State.SS3
            null
        }
        // ESC 7/8 (DECSC/DECRC) use final bytes below 0x40.
        codePoint == '7'.code || codePoint == '8'.code -> {
            state = State.GROUND
            VtAction.Escape(codePoint.toChar())
        }
        codePoint in 0x20..0x2F -> {
            escapeIntermediate = codePoint.toChar()
            state = State.ESCAPE_INTERMEDIATE
            null
        }
        codePoint in 0x40..0x7E -> {
            state = State.GROUND
            VtAction.Escape(codePoint.toChar())
        }
        else -> {
            state = State.GROUND
            null
        }
    }

    private fun consumeEscapeIntermediate(codePoint: Int): VtAction? = when {
        codePoint == ESC -> {
            state = State.ESCAPE
            null
        }
        codePoint in 0x30..0x7E -> {
            state = State.GROUND
            VtAction.Escape(codePoint.toChar())
        }
        else -> null
    }

    private fun consumeSs3(codePoint: Int): VtAction? = when {
        codePoint == ESC -> {
            state = State.ESCAPE
            null
        }
        codePoint in 0x40..0x7E -> {
            state = State.GROUND
            VtAction.Ss3(codePoint.toChar())
        }
        codePoint < 0x20 -> VtAction.Execute(codePoint)
        else -> {
            state = State.GROUND
            null
        }
    }

    private fun consumeCsi(codePoint: Int): VtAction? {
        when {
            codePoint == ESC -> {
                state = State.ESCAPE
                return null
            }
            codePoint == 0x18 || codePoint == 0x1A -> {
                state = State.GROUND
                return null
            }
            codePoint < 0x20 -> return null
            codePoint in 0x30..0x39 -> {
                parameterStarted = true
                if (!parameterOverflow) {
                    val digit = codePoint - '0'.code
                    currentParameter = ((currentParameter ?: 0) * 10 + digit)
                        .coerceAtMost(Int.MAX_VALUE)
                }
                return null
            }
            codePoint == ';'.code || codePoint == ':'.code -> {
                parameterStarted = true
                pushParameter()
                return null
            }
            codePoint == '?'.code || codePoint == '<'.code ||
                codePoint == '='.code || codePoint == '>'.code -> {
                if (privatePrefix == null && parameters.isEmpty() && currentParameter == null) {
                    privatePrefix = codePoint.toChar()
                }
                return null
            }
            codePoint in 0x20..0x2F -> {
                if (intermediate == null) intermediate = codePoint.toChar()
                return null
            }
            codePoint in 0x40..0x7E -> {
                // A bare CSI final has no parameters; a separator still means an
                // explicit empty parameter (CSI ;5H -> [-1, 5]).
                if (parameterStarted) pushParameter()
                val action = if (parameterOverflow) {
                    null
                } else {
                    VtAction.Csi(
                        final = codePoint.toChar(),
                        parameters = parameters.toList(),
                        privatePrefix = privatePrefix,
                        intermediate = intermediate,
                    )
                }
                state = State.GROUND
                return action
            }
            else -> return null
        }
    }

    private fun consumeString(codePoint: Int): VtAction? {
        if (awaitingStringTerminator) {
            awaitingStringTerminator = false
            if (codePoint == '\\'.code) return finishString()
            state = State.GROUND
            return consume(codePoint)
        }

        when {
            codePoint == ESC -> awaitingStringTerminator = true
            codePoint == 0x07 -> return finishString()
            codePoint == 0x18 || codePoint == 0x1A -> {
                state = State.GROUND
                clearString()
            }
            codePoint < 0x20 -> Unit
            stringData.length < MAX_STRING_LENGTH -> stringData.appendCodePoint(codePoint)
        }
        return null
    }

    private fun startString(isOsc: Boolean) {
        clearString()
        stringIsOsc = isOsc
        state = if (isOsc) State.OSC else State.DCS
    }

    private fun finishString(): VtAction? {
        val wasOsc = stringIsOsc
        val data = stringData.toString()
        state = State.GROUND
        clearString()
        if (!wasOsc) return null

        val separator = data.indexOf(';')
        val commandText = if (separator < 0) data else data.substring(0, separator)
        val payload = if (separator < 0) "" else data.substring(separator + 1)
        return VtAction.Osc(commandText.toIntOrNull() ?: 0, payload)
    }

    private fun pushParameter() {
        if (parameters.size >= MAX_PARAMETERS) {
            parameterOverflow = true
            currentParameter = null
            return
        }
        parameters.add(currentParameter ?: EMPTY_PARAMETER)
        currentParameter = null
    }

    private fun clearCsi() {
        parameters.clear()
        currentParameter = null
        parameterStarted = false
        parameterOverflow = false
        privatePrefix = null
        intermediate = null
    }

    private fun clearString() {
        stringData.setLength(0)
        stringIsOsc = false
        awaitingStringTerminator = false
    }

    private companion object {
        const val ESC = 0x1B
        const val EMPTY_PARAMETER = -1
        const val INITIAL_PARAMETER_CAPACITY = 8
        const val MAX_PARAMETERS = 32
        const val MAX_STRING_LENGTH = 4_096
    }
}
