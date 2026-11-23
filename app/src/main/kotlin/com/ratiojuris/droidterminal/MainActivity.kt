/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ratiojuris.droidterminal.ui.DroidTerminalApp
import com.ratiojuris.droidterminal.ui.theme.DroidTerminalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DroidTerminalTheme {
                DroidTerminalApp()
            }
        }
    }
}
