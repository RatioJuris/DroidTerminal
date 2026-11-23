/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UnicodeWidthTest {
    @Test
    fun `narrow latin code points use one cell`() {
        assertEquals(1, UnicodeWidth.of('A'.code))
        assertEquals(1, UnicodeWidth.of(0xE9))
    }

    @Test
    fun `combining marks attach without a cell`() {
        assertEquals(0, UnicodeWidth.of(0x0301))
        assertTrue(UnicodeWidth.isCombining(0x200D))
        assertTrue(UnicodeWidth.isCombining(0xFE0F))
    }

    @Test
    fun `wide characters use two cells`() {
        assertEquals(2, UnicodeWidth.of(0x6F22))
        assertEquals(2, UnicodeWidth.of(0x1F600))
    }

    @Test
    fun `invalid code points are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { UnicodeWidth.of(-1) }
    }
}
