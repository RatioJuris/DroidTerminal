/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.core.BuildInfo
import com.ratiojuris.droidterminal.ui.connections.ConnectionsScreen
import com.ratiojuris.droidterminal.ui.sftp.SftpScreen
import com.ratiojuris.droidterminal.ui.settings.SettingsScreen
import com.ratiojuris.droidterminal.ui.terminal.TerminalScreen
import com.ratiojuris.droidterminal.ui.vault.VaultScreen

/** Top-level destinations. Screen content is skeletal by design until Phase 2. */
enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    TERMINAL("Terminal", Icons.Filled.Home),
    CONNECTIONS("Connections", Icons.AutoMirrored.Filled.List),
    VAULT("Vault", Icons.Filled.Lock),
    SFTP("SFTP", Icons.AutoMirrored.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroidTerminalApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.TERMINAL) }

    BackHandler(enabled = selected != Destination.TERMINAL) {
        selected = Destination.TERMINAL
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(BuildInfo.APP_NAME) },
                actions = {
                    Text(
                        selected.label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (selected) {
                Destination.TERMINAL -> TerminalScreen()
                Destination.CONNECTIONS -> ConnectionsScreen()
                Destination.VAULT -> VaultScreen()
                Destination.SFTP -> SftpScreen()
                Destination.SETTINGS -> SettingsScreen()
            }
        }
    }
}
