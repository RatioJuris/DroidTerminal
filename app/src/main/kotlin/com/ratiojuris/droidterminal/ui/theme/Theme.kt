/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF00332E),
    secondary = Color(0xFFB0BEC5),
    background = Color(0xFF0B0E12),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF12171D),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1B232C),
    onSurfaceVariant = Color(0xFFAAB7C4),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF546E7A),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF10151B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10151B),
)

/** Material 3 theme with a terminal-oriented dark colour scheme. */
@Composable
fun DroidTerminalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content,
    )
}
