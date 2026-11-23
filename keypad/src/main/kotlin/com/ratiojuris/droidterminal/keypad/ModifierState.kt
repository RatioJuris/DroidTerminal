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
}

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
    val isActive: Boolean
        get() = pressed.isNotEmpty() || locked != null

    fun isEnabled(modifier: Modifier): Boolean = modifier in pressed || locked == modifier

    fun toggle(modifier: Modifier): ModifierState = copy(
        pressed = if (modifier in pressed) pressed - modifier else pressed + modifier,
        locked = if (locked == modifier) null else locked,
    )

    fun lock(modifier: Modifier): ModifierState = copy(
        pressed = pressed + modifier,
        locked = modifier,
    )

    fun cancel(): ModifierState = NONE

    companion object {
        val NONE: ModifierState = ModifierState()
    }
}
