/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VtParserTest {
    private fun parse(text: String): List<VtAction> {
        val parser = VtParser()
        return text.codePoints().toArray().asIterable().mapNotNull { parser.consume(it) }
    }

    @Test
    fun `ground prints code points and executes c0 controls`() {
        assertEquals(
            listOf(VtAction.Print('A'.code), VtAction.Execute(0x0A), VtAction.Print('B'.code)),
            parse("A\nB"),
        )
    }

    @Test
    fun `csi collects parameters private prefix and intermediates`() {
        assertEquals(
            VtAction.Csi('H', listOf(12, 34)),
            parse("\u001B[12;34H").single(),
        )
        assertEquals(
            VtAction.Csi('h', listOf(25), privatePrefix = '?'),
            parse("\u001B[?25h").single(),
        )
        assertEquals(
            VtAction.Csi('p', listOf(3), intermediate = ' '),
            parse("\u001B[3 p").single(),
        )
    }

    @Test
    fun `csi preserves empty and colon separated parameters`() {
        assertEquals(VtAction.Csi('m', emptyList()), parse("\u001B[m").single())
        assertEquals(VtAction.Csi('H', listOf(-1, 5)), parse("\u001B[;5H").single())
        assertEquals(
            VtAction.Csi('m', listOf(38, 2, -1, 1, 2, 3)),
            parse("\u001B[38:2::1:2:3m").single(),
        )
    }

    @Test
    fun `osc terminates on bell`() {
        assertEquals(VtAction.Osc(0, "title"), parse("\u001B]0;title\u0007").single())
        assertEquals(VtAction.Osc(2, "hello"), parse("\u001B]2;hello\u0007").single())
    }

    @Test
    fun `osc terminates on split string terminator`() {
        val parser = VtParser()
        val actions = "\u001B]2;hi\u001B\\".codePoints().toArray().asIterable()
            .mapNotNull { parser.consume(it) }

        assertEquals(listOf(VtAction.Osc(2, "hi")), actions)
    }

    @Test
    fun `charset and dcs payloads are consumed without actions`() {
        assertEquals(listOf(VtAction.Print('X'.code)), parse("\u001B(BX"))
        assertEquals(listOf(VtAction.Print('Z'.code)), parse("\u001BP1;2q\u001B\\Z"))
    }

    @Test
    fun `ss3 final is emitted for function keys`() {
        assertEquals(listOf(VtAction.Ss3('P')), parse("\u001BOP"))
    }

    @Test
    fun `unterminated sequences emit nothing`() {
        assertTrue(parse("\u001B[12;34").isEmpty())
        assertTrue(parse("\u001B]0;title").isEmpty())
        assertTrue(parse("\u001B").isEmpty())
    }

    @Test
    fun `escape cancels a partial csi sequence`() {
        assertEquals(listOf(VtAction.Escape('c')), parse("\u001B[12\u001Bc"))
    }

    @Test
    fun `oversized parameters and strings are bounded`() {
        val manyParameters = (0..40).joinToString(";")
        assertTrue(parse("\u001B[$manyParameters" + "m").isEmpty())

        val longTitle = "x".repeat(5_000)
        val action = parse("\u001B]2;$longTitle\u0007").single() as VtAction.Osc
        assertEquals(4_096, action.data.length)
    }

    @Test
    fun `reset abandons a partial sequence`() {
        val parser = VtParser()
        parser.consume(0x1B)
        parser.consume('['.code)
        parser.consume('3'.code)
        parser.reset()

        assertEquals(VtAction.Print('A'.code), parser.consume('A'.code))
    }
}
