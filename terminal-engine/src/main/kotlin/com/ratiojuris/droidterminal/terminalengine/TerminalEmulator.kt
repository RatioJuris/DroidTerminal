/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Streams terminal bytes into a [TerminalScreen].
 *
 * UTF-8 decoding is incremental, so multi-byte characters split across reads
 * are preserved. C0 controls are applied directly. Escape sequences are
 * consumed safely but deliberately ignored; their actions arrive with the
 * ANSI support milestone.
 */
class TerminalEmulator(
    initialSize: TerminalSize = TerminalSize.DEFAULT,
    scrollbackLimit: Int = 1_000,
) {
    private enum class ParseState {
        GROUND,
        ESCAPE,
        CSI,
        OSC,
        DCS,
        CHARSET,
    }

    private val screen = TerminalScreen(initialSize, scrollbackLimit)
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private var pendingBytes = ByteArray(0)
    private var parseState = ParseState.GROUND
    private var awaitingStringTerminator = false

    val size: TerminalSize
        get() = screen.size

    val cursor: Cursor
        get() = screen.cursor

    fun feed(bytes: ByteArray) {
        if (bytes.isEmpty() && pendingBytes.isEmpty()) return
        decode(bytes).codePoints().forEach { consume(it) }
    }

    fun feed(text: String) = feed(text.toByteArray(StandardCharsets.UTF_8))

    fun resize(newSize: TerminalSize) = screen.resize(newSize)

    fun snapshot(): TerminalSnapshot = screen.snapshot()

    private fun decode(bytes: ByteArray): String {
        val data = if (pendingBytes.isEmpty()) bytes else pendingBytes + bytes
        val input = ByteBuffer.wrap(data)
        val decoded = StringBuilder()
        var output = CharBuffer.allocate((data.size * 2).coerceAtLeast(16))

        while (true) {
            val result = decoder.decode(input, output, false)
            output.flip()
            decoded.append(output)
            output.clear()

            when {
                result.isOverflow -> output = CharBuffer.allocate(output.capacity() * 2)
                result.isUnderflow -> {
                    val remainder = ByteArray(input.remaining())
                    input.get(remainder)
                    pendingBytes = remainder
                    return decoded.toString()
                }
                result.isError -> if (input.hasRemaining()) input.position(input.position() + 1)
            }
        }
    }

    private fun consume(codePoint: Int) {
        when (parseState) {
            ParseState.GROUND -> consumeGround(codePoint)
            ParseState.ESCAPE -> consumeEscape(codePoint)
            ParseState.CSI -> if (codePoint in 0x40..0x7E) parseState = ParseState.GROUND
            ParseState.CHARSET -> parseState = ParseState.GROUND
            ParseState.OSC, ParseState.DCS -> consumeString(codePoint)
        }
    }

    private fun consumeGround(codePoint: Int) {
        when (codePoint) {
            0x00, 0x07 -> Unit // NUL and BEL have no visible effect in this milestone.
            0x08 -> screen.backspace()
            0x09 -> screen.tab()
            0x0A, 0x0B, 0x0C -> screen.lineFeed()
            0x0D -> screen.carriageReturn()
            0x1B -> parseState = ParseState.ESCAPE
            else -> {
                if (!Character.isISOControl(codePoint)) screen.writeCodePoint(codePoint)
            }
        }
    }

    private fun consumeEscape(codePoint: Int) {
        parseState = when (codePoint) {
            '['.code -> ParseState.CSI
            ']'.code -> ParseState.OSC
            'P'.code -> ParseState.DCS
            '('.code, ')'.code, '*'.code, '+'.code -> ParseState.CHARSET
            else -> ParseState.GROUND
        }
    }

    private fun consumeString(codePoint: Int) {
        if (awaitingStringTerminator) {
            awaitingStringTerminator = false
            if (codePoint == '\\'.code) parseState = ParseState.GROUND
            return
        }

        when (codePoint) {
            0x1B -> awaitingStringTerminator = true
            0x07 -> if (parseState == ParseState.OSC) parseState = ParseState.GROUND
        }
    }
}
