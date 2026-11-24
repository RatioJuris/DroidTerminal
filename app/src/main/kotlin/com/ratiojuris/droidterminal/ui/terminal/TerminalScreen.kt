/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.terminal

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.ratiojuris.droidterminal.ui.components.LoadingState
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
    val terminalFocus = remember { FocusRequester() }

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var showKeypad by remember { mutableStateOf(appSettings.showKeypad) }
    var connectStep by remember { mutableStateOf<ConnectStep?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var showProfilePicker by remember { mutableStateOf(false) }
    var modifiers by remember { mutableStateOf(ModifierState()) }
    var capsLock by remember { mutableStateOf(false) }
    var pad by remember { mutableStateOf(KeyboardPad.KEYS) }
    var showFunctionRow by remember { mutableStateOf(true) }
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
                .padding(horizontal = 12.dp)
                .focusRequester(terminalFocus)
                .clickable { terminalFocus.requestFocus() }
                .onKeyEvent { event ->
                    val key = hardwareKey(event) ?: return@onKeyEvent false
                    sessions.write(KeySequences.bytes(key, hardwareModifiers(event)))
                    true
                },
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

        if (appSettings.useSystemKeyboard) {
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
                    sessions.write(input.toByteArray(Charsets.UTF_8) + KeySequences.bytes(TerminalKey.Enter))
                    input = ""
                }) { Text("Send") }
            }
            inputError?.let {
                ErrorState(it)
                Spacer(Modifier.height(4.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (showKeypad) "Keyboard" else "Keyboard hidden",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Switch(checked = showKeypad, onCheckedChange = { showKeypad = it })
        }

        if (showKeypad && !appSettings.useSystemKeyboard) {
            InAppKeyboard(
                modifiers = modifiers,
                capsLock = capsLock,
                pad = pad,
                showFunctionRow = showFunctionRow,
                haptics = appSettings.haptics,
                onKey = { key ->
                    if (key == TerminalKey.CapsLock) {
                        capsLock = !capsLock
                    } else {
                        sessions.write(KeySequences.bytes(key, modifiers))
                        modifiers = modifiers.consume()
                    }
                },
                onModifierTap = { modifier, tap ->
                    modifiers = when (tap) {
                        ModifierTap.SINGLE -> modifiers.toggle(modifier)
                        ModifierTap.DOUBLE -> modifiers.lock(modifier)
                    }
                },
                onToggleCaps = { capsLock = !capsLock },
                onTogglePad = {
                    pad = if (pad == KeyboardPad.KEYS) KeyboardPad.SELECT else KeyboardPad.KEYS
                },
                onToggleFunctionRow = { showFunctionRow = !showFunctionRow },
                onClearModifiers = {
                    modifiers = ModifierState()
                    capsLock = false
                },
                onSelectionAction = { action ->
                    when (action) {
                        SelectionAction.PASTE -> {
                            // TODO(phase2): read the Android clipboard and emit ESC[200~ / ESC[201~ when DECSET 2004 is active
                        }
                        SelectionAction.COPY -> {
                            // TODO(phase2): copy the terminal selection to the clipboard and exit selection mode
                        }
                        else -> {
                            // TODO(phase2): implement ${action.name} against the terminal selection model
                        }
                    }
                },
                onHide = { showKeypad = false },
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

/** Maps a physical-keyboard event to the same key model used by the in-app keyboard. */
private fun hardwareKey(event: KeyEvent): TerminalKey? {
    if (event.type != KeyEventType.KeyDown) return null
    val special = when (event.key) {
        Key.Enter -> TerminalKey.Enter
        Key.Backspace -> TerminalKey.Backspace
        Key.Tab -> TerminalKey.Tab
        Key.Escape -> TerminalKey.Escape
        Key.DirectionLeft -> TerminalKey.Arrow(ArrowDirection.LEFT)
        Key.DirectionRight -> TerminalKey.Arrow(ArrowDirection.RIGHT)
        Key.DirectionUp -> TerminalKey.Arrow(ArrowDirection.UP)
        Key.DirectionDown -> TerminalKey.Arrow(ArrowDirection.DOWN)
        Key.MoveHome -> TerminalKey.Home
        Key.MoveEnd -> TerminalKey.End
        Key.PageUp -> TerminalKey.PageUp
        Key.PageDown -> TerminalKey.PageDown
        Key.Delete -> TerminalKey.Delete
        Key.Insert -> TerminalKey.Insert
        Key.F1 -> TerminalKey.FunctionKey(1)
        Key.F2 -> TerminalKey.FunctionKey(2)
        Key.F3 -> TerminalKey.FunctionKey(3)
        Key.F4 -> TerminalKey.FunctionKey(4)
        Key.F5 -> TerminalKey.FunctionKey(5)
        Key.F6 -> TerminalKey.FunctionKey(6)
        Key.F7 -> TerminalKey.FunctionKey(7)
        Key.F8 -> TerminalKey.FunctionKey(8)
        Key.F9 -> TerminalKey.FunctionKey(9)
        Key.F10 -> TerminalKey.FunctionKey(10)
        Key.F11 -> TerminalKey.FunctionKey(11)
        Key.F12 -> TerminalKey.FunctionKey(12)
        else -> null
    }
    if (special != null) return special

    val unicode = event.nativeKeyEvent.unicodeChar
    if (unicode > 0) return TerminalKey.Character(unicode.toChar())

    val keyCode = event.nativeKeyEvent.keyCode
    if (event.isCtrlPressed && keyCode in AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z) {
        return TerminalKey.Character(('a' + (keyCode - AndroidKeyEvent.KEYCODE_A)).toChar())
    }
    return null
}

private fun hardwareModifiers(event: KeyEvent): ModifierState {
    var pressed = emptySet<KeypadModifier>()
    if (event.isCtrlPressed) pressed = pressed + KeypadModifier.CONTROL
    if (event.isAltPressed) pressed = pressed + KeypadModifier.ALT
    if (event.isShiftPressed) pressed = pressed + KeypadModifier.SHIFT
    return ModifierState(pressed = pressed)
}
