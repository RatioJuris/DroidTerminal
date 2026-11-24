/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.auth.BiometricAuth
import com.ratiojuris.droidterminal.core.BuildInfo
import com.ratiojuris.droidterminal.di.AppContainer
import com.ratiojuris.droidterminal.settings.AppSettings
import com.ratiojuris.droidterminal.ui.sftp.SftpScreen
import com.ratiojuris.droidterminal.ui.settings.SettingsScreen
import com.ratiojuris.droidterminal.ui.terminal.TerminalScreen
import com.ratiojuris.droidterminal.ui.vault.VaultScreen

/** Top-level destinations. Screen content is backed by the application graph. */
enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    TERMINAL("Terminal", Icons.Filled.Terminal),
    VAULT("Vault", Icons.Filled.Key),
    SFTP("SFTP", Icons.Filled.NetworkWifi),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroidTerminalApp(container: AppContainer, biometricAuth: BiometricAuth) {
    var selected by rememberSaveable { mutableStateOf(Destination.TERMINAL) }
    val settings by container.settings.settings.collectAsState(AppSettings())

    LaunchedEffect(settings.autoLockVault) {
        container.vaultAutoLock.enabled = settings.autoLockVault
    }

    LaunchedEffect(container.sessions, container.sftp) {
        container.sessions.activeTarget.collect { target ->
            container.sftp.setTarget(target)
        }
    }

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
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Destination.entries.forEach { destination ->
                        IconButton(
                            onClick = { selected = destination },
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = destination.label
                            },
                        ) {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label,
                                tint = if (selected == destination) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
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
                Destination.TERMINAL -> TerminalScreen(
                    connections = container.connections,
                    sessions = container.sessions,
                    gateway = container.sshGateway,
                    settings = container.settings,
                    vault = container.vault,
                )
                Destination.VAULT -> VaultScreen(
                    vault = container.vault,
                    autoLock = container.vaultAutoLock,
                    biometric = biometricAuth,
                    settings = container.settings,
                    connections = container.connections,
                    gateway = container.sshGateway,
                )
                Destination.SFTP -> SftpScreen(
                    sftp = container.sftp,
                    sessions = container.sessions,
                )
                Destination.SETTINGS -> SettingsScreen(repository = container.settings)
            }
        }
    }
}
