/*
 * Droid Terminal — keyboard module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.keypad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModifierStateTest {
    @Test
    fun `tap creates a one-shot modifier`() {
        val state = ModifierState.NONE.toggle(Modifier.CONTROL)
        assertEquals(ModifierState.VisualState.ONE_SHOT, state.visualState(Modifier.CONTROL))
        assertTrue(state.isEnabled(Modifier.CONTROL))
    }

    @Test
    fun `double tap creates a locked modifier`() {
        val state = ModifierState.NONE.lock(Modifier.SHIFT)
        assertEquals(ModifierState.VisualState.LOCKED, state.visualState(Modifier.SHIFT))
        assertTrue(state.isEnabled(Modifier.SHIFT))
    }

    @Test
    fun `tapping a locked modifier turns it off`() {
        val state = ModifierState.NONE.lock(Modifier.ALT).toggle(Modifier.ALT)
        assertEquals(ModifierState.VisualState.OFF, state.visualState(Modifier.ALT))
        assertFalse(state.isEnabled(Modifier.ALT))
    }

    @Test
    fun `consume clears one-shot but preserves locks`() {
        val state = ModifierState(pressed = setOf(Modifier.CONTROL), locked = Modifier.SHIFT)
        val after = state.consume()
        assertEquals(ModifierState.VisualState.OFF, after.visualState(Modifier.CONTROL))
        assertEquals(ModifierState.VisualState.LOCKED, after.visualState(Modifier.SHIFT))
        assertTrue(after.isEnabled(Modifier.SHIFT))
    }

    @Test
    fun `consume on a state without one-shot modifiers is a no-op`() {
        val state = ModifierState(locked = Modifier.CONTROL)
        assertEquals(state, state.consume())
    }
}
