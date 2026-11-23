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
 * Applies host output bytes to a [TerminalScreen].
 *
 * UTF-8 decoding is incremental, so multi-byte characters split across reads
 * are preserved. Bytes are parsed by [VtParser] into typed actions; printable
 * text, C0 controls, CSI, OSC and ESC sequences mutate real screen state.
 * `feed(ByteArray)` is the local I/O adapter; no transport is involved.
 */
class TerminalEmulator(
    initialSize: TerminalSize = TerminalSize.DEFAULT,
    scrollbackLimit: Int = 1_000,
) {
    private val screen = TerminalScreen(initialSize, scrollbackLimit)
    private val parser = VtParser()
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    private var pendingBytes = ByteArray(0)

    val size: TerminalSize
        @Synchronized get() = screen.size

    val cursor: Cursor
        @Synchronized get() = screen.cursor

    val title: String
        @Synchronized get() = screen.title

    val cursorVisible: Boolean
        @Synchronized get() = screen.cursorVisible

    val autoWrap: Boolean
        @Synchronized get() = screen.autoWrap

    @Synchronized
    fun feed(bytes: ByteArray) {
        if (bytes.isEmpty() && pendingBytes.isEmpty()) return
        decode(bytes).codePoints().forEach { codePoint ->
            parser.consume(codePoint)?.let(::applyAction)
        }
    }

    @Synchronized
    fun feed(text: String) {
        feed(text.toByteArray(StandardCharsets.UTF_8))
    }

    @Synchronized
    fun resize(newSize: TerminalSize) {
        screen.resize(newSize)
    }

    @Synchronized
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

    private fun applyAction(action: VtAction) {
        when (action) {
            is VtAction.Print -> screen.writeCodePoint(action.codePoint)
            is VtAction.Execute -> execute(action.control)
            is VtAction.Csi -> applyCsi(action)
            is VtAction.Osc -> if (action.command == 0 || action.command == 2) {
                screen.setTitle(action.data)
            }
            is VtAction.Escape -> applyEscape(action.final)
            // SS3 finals are input-side key reports (F1-F4); consuming them is
            // the required behaviour, never printing the final byte.
            is VtAction.Ss3 -> Unit
        }
    }

    private fun execute(control: Int) {
        when (control) {
            0x00, 0x07, 0x0E, 0x0F -> Unit
            0x08 -> screen.backspace()
            0x09 -> screen.tab()
            0x0A, 0x0B, 0x0C -> screen.lineFeed()
            0x0D -> screen.carriageReturn()
            0x18, 0x1A -> parser.reset()
            else -> Unit
        }
    }

    private fun applyCsi(action: VtAction.Csi) {
        val parameters = action.parameters
        if (action.privatePrefix == '?') {
            if (action.final == 'h' || action.final == 'l') {
                applyPrivateMode(parameters, action.final == 'h')
            }
            return
        }
        if (action.intermediate != null) return

        when (action.final) {
            'A' -> screen.cursorUp(count(parameters, 0))
            'B' -> screen.cursorDown(count(parameters, 0))
            'C' -> screen.cursorForward(count(parameters, 0))
            'D' -> screen.cursorBackward(count(parameters, 0))
            'E' -> screen.cursorNextLine(count(parameters, 0))
            'F' -> screen.cursorPreviousLine(count(parameters, 0))
            'G' -> screen.cursorHorizontalAbsolute(count(parameters, 0))
            'H', 'f' -> screen.cursorPosition(count(parameters, 0), count(parameters, 1))
            'J' -> screen.eraseInDisplay(value(parameters, 0, 0))
            'K' -> screen.eraseInLine(value(parameters, 0, 0))
            'X' -> screen.eraseCharacter(count(parameters, 0))
            'Z' -> screen.cursorBackward(TAB_WIDTH)
            'd' -> screen.cursorVerticalAbsolute(count(parameters, 0))
            'm' -> screen.applySgr(parameters)
            'r' -> screen.setScrollRegion(count(parameters, 0), count(parameters, 1))
            's' -> screen.saveCursor()
            'u' -> screen.restoreCursor()
            else -> Unit
        }
    }

    private fun applyPrivateMode(parameters: List<Int>, enabled: Boolean) {
        parameters.forEach { mode ->
            when (mode) {
                7 -> screen.setAutoWrap(enabled)
                25 -> screen.setCursorVisible(enabled)
                else -> Unit
            }
        }
    }

    private fun applyEscape(final: Char) {
        when (final) {
            'c' -> screen.reset()
            '7' -> screen.saveCursor()
            '8' -> screen.restoreCursor()
            'D' -> screen.index()
            'E' -> screen.nextLine()
            'M' -> screen.reverseIndex()
            else -> Unit
        }
    }

    private fun count(parameters: List<Int>, index: Int): Int = value(parameters, index, 1)

    private fun value(parameters: List<Int>, index: Int, default: Int): Int {
        val parameter = parameters.getOrNull(index) ?: return default
        return if (parameter < 0) default else parameter
    }

    private companion object {
        const val TAB_WIDTH = 8
    }
}
