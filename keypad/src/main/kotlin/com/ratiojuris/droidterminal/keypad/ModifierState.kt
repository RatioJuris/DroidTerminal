/*
 * Droid Terminal — shortcut keypad module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.keypad

/** Modifiers supported by the terminal keypad. */
enum class Modifier {
    CONTROL,
    ALT,
    SHIFT,
    FN,
    /** Physical Meta/Windows key; encoded as xterm's 8 modifier bit. */
    META,
}

/** Terminal modes that change how a key is encoded. */
data class TerminalModes(
    /** DECSET 1 / DECCKM: application cursor keys use SS3 instead of CSI. */
    val applicationCursorKeys: Boolean = false,
)

/**
 * Immutable modifier state.
 *
 * [pressed] represents momentary modifiers. [locked] represents a modifier the
 * user has explicitly locked until they cancel it.
 */
data class ModifierState(
    val pressed: Set<Modifier> = emptySet(),
    val locked: Modifier? = null,
) {
    /** Visual state used by the in-app keyboard to distinguish a tap from a lock. */
    enum class VisualState { OFF, ONE_SHOT, LOCKED }

    val isActive: Boolean
        get() = pressed.isNotEmpty() || locked != null

    fun isEnabled(modifier: Modifier): Boolean = modifier in pressed || locked == modifier

    fun visualState(modifier: Modifier): VisualState = when {
        locked == modifier -> VisualState.LOCKED
        modifier in pressed -> VisualState.ONE_SHOT
        else -> VisualState.OFF
    }

    fun toggle(modifier: Modifier): ModifierState = copy(
        pressed = if (modifier in pressed) pressed - modifier else pressed + modifier,
        locked = if (locked == modifier) null else locked,
    )

    fun lock(modifier: Modifier): ModifierState = copy(
        pressed = pressed + modifier,
        locked = modifier,
    )

    /** Removes one-shot modifiers after they have been applied to a key. Locks persist. */
    fun consume(): ModifierState = if (pressed.isEmpty()) this else copy(pressed = emptySet())

    fun cancel(): ModifierState = NONE

    companion object {
        val NONE: ModifierState = ModifierState()
    }
}
