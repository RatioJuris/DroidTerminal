/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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

/** Top-level destinations. Status text is deliberately explicit about what is not built yet. */
enum class Destination(
    val label: String,
    val plannedPhase: String,
    val status: String,
    val icon: ImageVector,
) {
    TERMINAL(
        "Terminal",
        "Phase 2",
        "Terminal emulation, scrollback and clipboard integration are not implemented in this foundation build.",
        Icons.Filled.Home,
    ),
    CONNECTIONS(
        "Connections",
        "Phase 4",
        "SSH profiles and host-key verification are not implemented in this foundation build.",
        Icons.AutoMirrored.Filled.List,
    ),
    VAULT(
        "Vault",
        "Phase 5",
        "Keystore-backed credential and SSH key storage is not implemented in this foundation build.",
        Icons.Filled.Lock,
    ),
    SFTP(
        "SFTP",
        "Phase 6",
        "Remote file browsing and transfer management are not implemented in this foundation build.",
        Icons.AutoMirrored.Filled.List,
    ),
    SETTINGS(
        "Settings",
        "Phase 7",
        "Terminal, keypad and security preferences are not implemented in this foundation build.",
        Icons.Filled.Settings,
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroidTerminalApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.TERMINAL) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(BuildInfo.APP_NAME) })
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
            ModuleStatusScreen(selected)
        }
    }
}

@Composable
private fun ModuleStatusScreen(destination: Destination) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(destination.label, style = MaterialTheme.typography.headlineMedium)
        Text("Planned milestone: ${destination.plannedPhase}", style = MaterialTheme.typography.titleMedium)
        Text(destination.status, style = MaterialTheme.typography.bodyLarge)
        HorizontalDivider()
        Text("Version ${BuildInfo.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Text(BuildInfo.BUILD_STAGE, style = MaterialTheme.typography.bodyMedium)
        Text("Licence: ${BuildInfo.SPDX_LICENSE_ID}", style = MaterialTheme.typography.bodyMedium)
    }
}
