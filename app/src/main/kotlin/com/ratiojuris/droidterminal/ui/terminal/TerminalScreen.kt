/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratiojuris.droidterminal.connections.ConnectionRepository
import com.ratiojuris.droidterminal.keypad.ArrowDirection
import com.ratiojuris.droidterminal.keypad.KeySequences
import com.ratiojuris.droidterminal.keypad.Modifier as KeypadModifier
import com.ratiojuris.droidterminal.keypad.ModifierState
import com.ratiojuris.droidterminal.keypad.TerminalKey
import com.ratiojuris.droidterminal.sessions.SessionManager
import com.ratiojuris.droidterminal.sessions.SessionState
import com.ratiojuris.droidterminal.settings.AppSettings
import com.ratiojuris.droidterminal.settings.SettingsRepository
import com.ratiojuris.droidterminal.sshengine.HostKeyInfo
import com.ratiojuris.droidterminal.sshengine.SshCredential
import com.ratiojuris.droidterminal.sshengine.SshGateway
import com.ratiojuris.droidterminal.terminalengine.TerminalSize
import com.ratiojuris.droidterminal.ui.components.ErrorState
import com.ratiojuris.droidterminal.ui.components.FormTextField
import com.ratiojuris.droidterminal.ui.components.KeyCap
import com.ratiojuris.droidterminal.ui.components.LoadingState
import com.ratiojuris.droidterminal.ui.components.SectionHeader
import com.ratiojuris.droidterminal.ui.model.AuthType
import com.ratiojuris.droidterminal.ui.model.ConnectionProfile
import com.ratiojuris.droidterminal.ui.model.SessionTab
import com.ratiojuris.droidterminal.ui.model.TerminalLine
import com.ratiojuris.droidterminal.ui.model.TerminalLineKind
import com.ratiojuris.droidterminal.ui.model.toBackend
import com.ratiojuris.droidterminal.ui.model.toUi
import com.ratiojuris.droidterminal.ui.model.toUiLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ConnectStep {
    data class Credentials(val profile: ConnectionProfile) : ConnectStep
    data class HostKey(
        val profile: ConnectionProfile,
        val credential: SshCredential,
        val info: HostKeyInfo,
    ) : ConnectStep
}

@Composable
fun TerminalScreen(
    connections: ConnectionRepository,
    sessions: SessionManager,
    gateway: SshGateway,
    settings: SettingsRepository,
) {
    val tabs by sessions.tabs.collectAsState()
    val snapshots by sessions.snapshots.collectAsState()
    val activeId by sessions.activeId.collectAsState()
    val status by sessions.status.collectAsState()
    val appSettings by settings.settings.collectAsState(AppSettings())
    val storedProfiles by connections.profiles.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var showKeypad by remember { mutableStateOf(appSettings.showKeypad) }
    var connectStep by remember { mutableStateOf<ConnectStep?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }
    var modifiers by remember { mutableStateOf(ModifierState()) }
    val scrollState = rememberScrollState()

    val profiles = storedProfiles.map { it.toUi() }
    val selected = tabs.firstOrNull { it.id == selectedId } ?: tabs.firstOrNull()
    val snapshot = selected?.let { snapshots[it.id] }
    val lines = snapshot?.toUiLines().orEmpty()
    val cursorRow = (snapshot?.scrollback?.size ?: 0) + (snapshot?.cursor?.row ?: 0)
    val cursorColumn = snapshot?.cursor?.column ?: 0

    LaunchedEffect(appSettings.scrollback) { sessions.configure(appSettings.scrollback) }
    LaunchedEffect(appSettings.showKeypad) { showKeypad = appSettings.showKeypad }
    LaunchedEffect(activeId) { activeId?.let { selectedId = it } }
    LaunchedEffect(cursorRow, cursorColumn) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    BackHandler(enabled = showKeypad || connectStep != null || showProfilePicker) {
        when {
            connectStep != null -> connectStep = null
            showProfilePicker -> showProfilePicker = false
            else -> showKeypad = false
        }
    }

    fun requestConnect(profile: ConnectionProfile) {
        if (profile.authType == AuthType.AGENT) {
            connectError = "Agent authentication is not implemented"
            return
        }
        sessions.clearStatus()
        connectError = null
        connectStep = ConnectStep.Credentials(profile)
    }

    suspend fun completeConnect(profile: ConnectionProfile, credential: SshCredential, fingerprint: String) {
        val result = sessions.connect(profile.toBackend(), credential, fingerprint, TerminalSize.DEFAULT)
        if (result.isSuccess) {
            selectedId = result.getOrThrow()
            runCatching { connections.touch(profile.id) }
            connectStep = null
            connectError = null
            isConnecting = false
        } else {
            connectStep = null
            connectError = "Connection failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            isConnecting = false
        }
    }

    suspend fun resolveHostKey(profile: ConnectionProfile, credential: SshCredential) {
        val trusted = connections.trustedFingerprint(profile.host)
        if (trusted != null) {
            completeConnect(profile, credential, trusted)
            return
        }
        when (appSettings.hostKeyPolicy) {
            AppSettings.HOST_KEY_STRICT -> {
                isConnecting = false
                connectError = "Host key for ${profile.host} is not trusted"
            }
            else -> {
                val fetched = withContext(Dispatchers.IO) {
                    gateway.fetchHostKey(profile.host, profile.port, profile.username)
                }
                if (fetched.isFailure) {
                    isConnecting = false
                    connectError = "Host key read failed: ${fetched.exceptionOrNull()?.message ?: "unknown error"}"
                    return
                }
                val info = fetched.getOrThrow()
                if (appSettings.hostKeyPolicy == AppSettings.HOST_KEY_TOFU) {
                    val accepted = runCatching {
                        connections.trustHost(info.host, info.fingerprint, info.algorithm)
                    }
                    if (accepted.isFailure) {
                        isConnecting = false
                        connectError = "Host key acceptance failed: ${accepted.exceptionOrNull()?.message}"
                    } else {
                        completeConnect(profile, credential, info.fingerprint)
                    }
                } else {
                    isConnecting = false
                    connectStep = ConnectStep.HostKey(profile, credential, info)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SessionTabs(tabs.map { it.toUi() }, selected?.id) { id ->
            selectedId = id
            sessions.select(id)
            inputError = null
        }

        selected?.let { session ->
            ConnectionBar(session.toUi(), onToggle = {
                if (session.connected) {
                    sessions.disconnect(session.id)
                } else {
                    val profile = profiles.firstOrNull {
                        it.name == session.title && it.host == session.host
                    }
                    if (profile != null) requestConnect(profile) else {
                        connectError = "Connection profile is no longer available"
                    }
                }
            })
        } ?: EmptySessionBanner(onConnect = { showProfilePicker = true })

        val errorMessage = connectError ?: status
        errorMessage?.let {
            ErrorState(it, onRetry = {
                sessions.clearStatus()
                connectError = null
            })
        }

        if (isConnecting || connectStep != null || selected?.state == SessionState.CONNECTING) {
            LoadingState("Connecting…")
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
        ) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                ) {
                    lines.forEach { line ->
                        TerminalOutputLine(line, appSettings.fontSize)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    inputError = null
                },
                label = { Text("Command") },
                singleLine = true,
                isError = inputError != null,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (input.isBlank()) {
                    inputError = "Enter a command first"
                    return@Button
                }
                val enter = KeySequences.bytes(
                    TerminalKey.Custom("Enter", "\r"),
                    ModifierState(),
                )
                sessions.write(input.toByteArray(Charsets.UTF_8) + enter)
                input = ""
            }) { Text("Send") }
        }

        inputError?.let {
            ErrorState(it)
            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shortcut keypad", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Switch(checked = showKeypad, onCheckedChange = { showKeypad = it })
        }

        if (showKeypad) {
            ShortcutKeypad(
                modifiers = modifiers,
                onModifier = { key, lock ->
                    val modifier = keypadModifier(key)
                    if (modifier != null) {
                        modifiers = if (lock) modifiers.lock(modifier) else modifiers.toggle(modifier)
                    }
                },
                onKey = { key ->
                    if (appSettings.haptics) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (key is TerminalKey.Custom && key.label == "Clr") {
                        modifiers = ModifierState()
                    } else {
                        sessions.write(KeySequences.bytes(key, modifiers))
                    }
                },
            )
        }
    }

    if (showProfilePicker) {
        ProfilePickerDialog(
            profiles = profiles,
            onSelect = { profile ->
                showProfilePicker = false
                requestConnect(profile)
            },
            onDismiss = { showProfilePicker = false },
        )
    }

    val step = connectStep
    if (step is ConnectStep.Credentials) {
        CredentialDialog(
            profile = step.profile,
            onDismiss = { connectStep = null },
            onSubmit = { credential ->
                connectStep = null
                isConnecting = true
                scope.launch { resolveHostKey(step.profile, credential) }
            },
        )
    }

    if (step is ConnectStep.HostKey) {
        HostKeyDialog(
            info = step.info,
            onAccept = {
                connectStep = null
                isConnecting = true
                scope.launch {
                    val accepted = runCatching {
                        connections.trustHost(step.info.host, step.info.fingerprint, step.info.algorithm)
                    }
                    if (accepted.isFailure) {
                        isConnecting = false
                        connectError = "Host key acceptance failed: ${accepted.exceptionOrNull()?.message}"
                    } else {
                        completeConnect(step.profile, step.credential, step.info.fingerprint)
                    }
                }
            },
            onReject = { connectStep = null },
        )
    }
}

@Composable
private fun SessionTabs(sessions: List<SessionTab>, selectedId: String?, onSelect: (String) -> Unit) {
    if (sessions.isEmpty()) {
        Text(
            "No terminal sessions",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
        return
    }
    val selectedIndex = sessions.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    TabRow(selectedTabIndex = selectedIndex) {
        sessions.forEach { session ->
            Tab(
                selected = session.id == selectedId,
                onClick = { onSelect(session.id) },
                text = { Text(session.title) },
            )
        }
    }
}

@Composable
private fun ConnectionBar(session: SessionTab, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.title, style = MaterialTheme.typography.titleSmall)
            Text(session.host, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            if (session.connected) "Connected" else "Disconnected",
            style = MaterialTheme.typography.labelLarge,
            color = if (session.connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onToggle) {
            Text(if (session.connected) "Disconnect" else "Connect")
        }
    }
}

@Composable
private fun EmptySessionBanner(onConnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No session selected", style = MaterialTheme.typography.titleSmall)
            Text(
                "Choose a connection profile to start a terminal session.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onConnect) { Text("Connect to profile…") }
        }
    }
}

@Composable
private fun ProfilePickerDialog(
    profiles: List<ConnectionProfile>,
    onSelect: (ConnectionProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (profiles.isEmpty()) {
                    Text("No profiles saved. Add one in Connections first.")
                } else {
                    profiles.forEach { profile ->
                        TextButton(onClick = { onSelect(profile) }) {
                            Text("${profile.username}@${profile.host}:${profile.port}")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CredentialDialog(
    profile: ConnectionProfile,
    onDismiss: () -> Unit,
    onSubmit: (SshCredential) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var privateKey by rememberSaveable { mutableStateOf("") }
    var keyPassphrase by rememberSaveable { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    val usesKey = profile.authType == AuthType.SSH_KEY
    val missing = if (usesKey) privateKey.isBlank() else password.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Authenticate ${profile.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (usesKey) {
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text("Private key (PEM)") },
                        minLines = 4,
                        maxLines = 6,
                        isError = attempted && privateKey.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FormTextField(
                        "Key passphrase",
                        keyPassphrase,
                        { keyPassphrase = it },
                        password = true,
                    )
                } else {
                    FormTextField(
                        "Password",
                        password,
                        { password = it },
                        if (attempted && password.isBlank()) "Password is required" else null,
                        password = true,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                attempted = true
                if (!missing) {
                    onSubmit(
                        if (usesKey) {
                            SshCredential(
                                privateKeyPem = privateKey,
                                passphrase = keyPassphrase.ifBlank { null },
                            )
                        } else {
                            SshCredential(password = password)
                        },
                    )
                }
            }) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun HostKeyDialog(info: HostKeyInfo, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Verify host key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.host, style = MaterialTheme.typography.bodyMedium)
                Text(info.algorithm, style = MaterialTheme.typography.bodySmall)
                Text(info.fingerprint, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Verify this fingerprint out of band before accepting.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("Accept") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Reject") } },
    )
}

@Composable
private fun TerminalOutputLine(line: TerminalLine, fontSize: Float) {
    val colour = when (line.kind) {
        TerminalLineKind.COMMAND -> MaterialTheme.colorScheme.primary
        TerminalLineKind.ERROR -> MaterialTheme.colorScheme.error
        TerminalLineKind.SUCCESS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = line.text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize.sp,
        ),
        color = colour,
    )
}

@Composable
private fun ShortcutKeypad(
    modifiers: ModifierState,
    onModifier: (TerminalKey, Boolean) -> Unit,
    onKey: (TerminalKey) -> Unit,
) {
    SectionHeader("Shortcut keys")
    val rows = listOf(
        listOf(
            TerminalKey.Control,
            TerminalKey.Alt,
            TerminalKey.Escape,
            TerminalKey.Tab,
            TerminalKey.Shift,
            TerminalKey.Fn,
        ),
        listOf(
            TerminalKey.Arrow(ArrowDirection.UP),
            TerminalKey.Arrow(ArrowDirection.DOWN),
            TerminalKey.Arrow(ArrowDirection.LEFT),
            TerminalKey.Arrow(ArrowDirection.RIGHT),
            TerminalKey.Home,
            TerminalKey.End,
        ),
        listOf(
            TerminalKey.ControlLetter('C'),
            TerminalKey.ControlLetter('D'),
            TerminalKey.ControlLetter('Z'),
            TerminalKey.Custom("PgUp", "\u001B[5~"),
            TerminalKey.Custom("PgDn", "\u001B[6~"),
            TerminalKey.Custom("Clr", ""),
        ),
        listOf(
            TerminalKey.ControlLetter('E'),
            TerminalKey.ControlLetter('W'),
            TerminalKey.ControlLetter('U'),
            TerminalKey.Custom("Enter", "\r"),
            TerminalKey.Custom("Backspace", "\u007F"),
            TerminalKey.ControlLetter('A'),
        ),
        (1..6).map { TerminalKey.FunctionKey(it) },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 180.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    val modifier = keypadModifier(key)
                    KeyCap(
                        text = if (modifier != null && modifiers.isActive) "•${key.label}" else key.label,
                        onClick = {
                            if (modifier != null) onModifier(key, false) else onKey(key)
                        },
                        onLongClick = if (modifier != null) ({ onModifier(key, true) }) else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun keypadModifier(key: TerminalKey): KeypadModifier? = when (key) {
    TerminalKey.Control -> KeypadModifier.CONTROL
    TerminalKey.Alt -> KeypadModifier.ALT
    TerminalKey.Shift -> KeypadModifier.SHIFT
    TerminalKey.Fn -> KeypadModifier.FN
    else -> null
}
