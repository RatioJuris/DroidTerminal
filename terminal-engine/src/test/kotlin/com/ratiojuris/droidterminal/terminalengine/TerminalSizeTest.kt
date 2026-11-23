/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSizeTest {
    @Test
    fun `default size is eighty by twenty four`() {
        assertEquals(80, TerminalSize.DEFAULT.columns)
        assertEquals(24, TerminalSize.DEFAULT.rows)
        assertEquals(1_920, TerminalSize.DEFAULT.cellCount)
    }

    @Test
    fun `non positive dimensions are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TerminalSize(0, 24) }
        assertThrows(IllegalArgumentException::class.java) { TerminalSize(80, -1) }
    }

    @Test
    fun `protocol limit is enforced for oversized values`() {
        assertTrue(TerminalSize(32_767, 24).isWithinProtocolLimits)
        assertFalse(TerminalSize(32_768, 24).isWithinProtocolLimits)
    }
}
