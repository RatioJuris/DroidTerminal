/*
 * Droid Terminal — settings module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.settings

/** Persisted user preferences shared by the terminal, vault and connection screens. */
data class AppSettings(
    val fontSize: Float = DEFAULT_FONT_SIZE,
    val scrollback: Int = DEFAULT_SCROLLBACK,
    val showKeypad: Boolean = true,
    val haptics: Boolean = true,
    val useSystemKeyboard: Boolean = false,
    val autoLockVault: Boolean = true,
    val requireBiometric: Boolean = false,
    val hostKeyPolicy: Int = HOST_KEY_WARN,
) {
    init {
        require(fontSize in MIN_FONT_SIZE..MAX_FONT_SIZE) { "font size outside supported range" }
        require(scrollback in SUPPORTED_SCROLLBACK) { "unsupported scrollback size" }
        require(hostKeyPolicy in HOST_KEY_WARN..HOST_KEY_TOFU) { "unknown host key policy" }
    }

    companion object {
        const val MIN_FONT_SIZE = 10f
        const val MAX_FONT_SIZE = 24f
        const val DEFAULT_FONT_SIZE = 14f
        const val DEFAULT_SCROLLBACK = 1000
        val SUPPORTED_SCROLLBACK = listOf(200, 1000, 5000)

        const val HOST_KEY_WARN = 0
        const val HOST_KEY_STRICT = 1
        const val HOST_KEY_TOFU = 2
    }
}
