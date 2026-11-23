/*
 * Droid Terminal — shortcut keypad module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.keypad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultKeypadLayoutTest {
    @Test
    fun `layout has the specified five rows`() {
        assertEquals(5, DefaultKeypadLayout.rows.size)
        assertEquals(6, DefaultKeypadLayout.rows[0].size)
        assertEquals(6, DefaultKeypadLayout.rows[1].size)
        assertEquals(6, DefaultKeypadLayout.rows[2].size)
        assertEquals(6, DefaultKeypadLayout.rows[3].size)
        assertEquals(12, DefaultKeypadLayout.rows[4].size)
    }

    @Test
    fun `function row contains F1 through F12`() {
        val labels = DefaultKeypadLayout.rows[4].map { it.label }
        assertEquals((1..12).map { "F$it" }, labels)
    }

    @Test
    fun `modifier state can toggle lock and cancel`() {
        val ctrl = ModifierState.NONE.toggle(Modifier.CONTROL)
        assertTrue(ctrl.isActive)
        assertTrue(ctrl.isEnabled(Modifier.CONTROL))
        assert(!ctrl.isEnabled(Modifier.ALT))

        val locked = ctrl.lock(Modifier.CONTROL)
        assertEquals(Modifier.CONTROL, locked.locked)
        assertTrue(locked.isEnabled(Modifier.CONTROL))

        val cancelled = locked.cancel()
        assert(!cancelled.isActive)
    }
}
