/*
 * Droid Terminal — terminal engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.terminalengine

/**
 * Display-width classification for Unicode code points.
 *
 * Terminal cells are discrete, so combining marks occupy no cell of their own
 * and East Asian wide characters occupy two. This is the character-handling
 * foundation used by the screen buffer before any styling is applied.
 */
internal object UnicodeWidth {
    fun of(codePoint: Int): Int {
        require(Character.isValidCodePoint(codePoint)) { "invalid Unicode code point: $codePoint" }
        return when {
            isCombining(codePoint) -> 0
            isWide(codePoint) -> 2
            else -> 1
        }
    }

    fun isCombining(codePoint: Int): Boolean {
        if (!Character.isValidCodePoint(codePoint)) return false
        val type = Character.getType(codePoint)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            codePoint == 0x200C || // zero-width non-joiner
            codePoint == 0x200D || // zero-width joiner
            codePoint in 0xFE00..0xFE0F || // variation selectors
            codePoint in 0xE0100..0xE01EF // supplementary variation selectors
    }

    fun isWide(codePoint: Int): Boolean {
        if (!Character.isValidCodePoint(codePoint)) return false
        return codePoint in 0x1100..0x115F ||
            codePoint in 0x231A..0x231B ||
            codePoint == 0x2329 ||
            codePoint == 0x232A ||
            codePoint in 0x23E9..0x23EC ||
            codePoint == 0x23F0 ||
            codePoint == 0x23F3 ||
            codePoint in 0x25FD..0x25FE ||
            codePoint in 0x2614..0x2615 ||
            codePoint in 0x2648..0x2653 ||
            codePoint == 0x267F ||
            codePoint == 0x2693 ||
            codePoint == 0x26A1 ||
            codePoint in 0x26AA..0x26AB ||
            codePoint in 0x26BD..0x26BE ||
            codePoint in 0x26C4..0x26C5 ||
            codePoint == 0x26CE ||
            codePoint == 0x26D4 ||
            codePoint == 0x26EA ||
            codePoint in 0x26F2..0x26F3 ||
            codePoint == 0x26F5 ||
            codePoint == 0x26FA ||
            codePoint == 0x26FD ||
            codePoint == 0x2705 ||
            codePoint in 0x270A..0x270B ||
            codePoint == 0x2728 ||
            codePoint == 0x274C ||
            codePoint == 0x274E ||
            codePoint in 0x2753..0x2755 ||
            codePoint == 0x2757 ||
            codePoint in 0x2795..0x2797 ||
            codePoint == 0x27B0 ||
            codePoint == 0x27BF ||
            codePoint in 0x2B1B..0x2B1C ||
            codePoint == 0x2B50 ||
            codePoint == 0x2B55 ||
            codePoint in 0x2E80..0x303E ||
            codePoint in 0x3040..0xA4CF ||
            codePoint in 0xAC00..0xD7A3 ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0xFE10..0xFE19 ||
            codePoint in 0xFE30..0xFE6F ||
            codePoint in 0xFF00..0xFF60 ||
            codePoint in 0xFFE0..0xFFE6 ||
            codePoint in 0x1F300..0x1FAFF ||
            codePoint in 0x20000..0x3FFFD
    }
}
