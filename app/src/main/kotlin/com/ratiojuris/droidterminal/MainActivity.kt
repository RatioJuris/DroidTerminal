/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.ratiojuris.droidterminal.auth.BiometricAuth
import com.ratiojuris.droidterminal.ui.DroidTerminalApp
import com.ratiojuris.droidterminal.ui.theme.DroidTerminalTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as DroidTerminalApplication).container
        lifecycle.addObserver(container.vaultAutoLock)
        val biometricAuth = BiometricAuth(this, container.vault)
        setContent {
            DroidTerminalTheme {
                DroidTerminalApp(container, biometricAuth)
            }
        }
    }
}
