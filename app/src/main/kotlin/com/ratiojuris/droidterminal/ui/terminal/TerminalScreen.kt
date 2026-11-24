/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.terminal

import android.util.Base64 as AndroidBase64
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratiojuris.droidterminal.connections.ConnectionRepository
import com.ratiojuris.droidterminal.keypad.ArrowDirection
import com.ratiojuris.droidterminal.keypad.KeySequences
import com.ratiojuris.droidterminal.keypad.Modifier as KeypadModifier
import com.ratiojuris.droidterminal.keypad.ModifierState
import com.ratiojuris.droidterminal.keypad.TerminalKey
import com.ratiojuris.droidterminal.keypad.TerminalModes
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
import com.ratiojuris.droidterminal.vault.VaultItem as BackendVaultItem
import com.ratiojuris.droidterminal.vault.VaultItemKind
import com.ratiojuris.droidterminal.vault.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ConnectStep {
    data class Credentials(val profile: ConnectionProfile) : ConnectStep
    data class HostKey(
        val profile: ConnectionProfile,
        val credential: SshCredential,
        val info: HostKeyInfo,
        val saveCredential: Boolean,
    ) : ConnectStep
}

private data class QuickTarget(val username: String, val host: String, val port: Int)

/** Parses the one-line terminal form `ssh user@host [-p port]`. */
private fun parseQuickTarget(command: String): QuickTarget? {
    val tokens = command.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty() || !tokens[0].equals("ssh", ignoreCase = true)) return null

    var target: String? = null
    var port = 22
    var index = 1
    while (index < tokens.size) {
        val token = tokens[index]
        when {
            token == "-p" || token == "--port" -> {
                if (index + 1 >= tokens.size) return null
                port = tokens[index + 1].toIntOrNull() ?: return null
                index += 2
            }
            token.startsWith("-p") && token.length > 2 -> {
                port = token.removePrefix("-p").toIntOrNull() ?: return null
                index++
            }
            token.startsWith("--port=") -> {
                port = token.removePrefix("--port=").toIntOrNull() ?: return null
                index++
            }
            target == null && !token.startsWith("-") -> {
                target = token
                index++
            }
            else -> index++
        }
    }

    val userHost = target ?: return null
    val username = userHost.substringBefore('@')
    val host = userHost.substringAfter('@')
    if (username.isBlank() || host.isBlank() || port !in 1..65535) return null
    return QuickTarget(username, host, port)
}

private fun credentialLabel(profile: ConnectionProfile): String =
    "${profile.username}@${profile.host}:${profile.port}"

private fun encodeCredential(credential: SshCredential): String = when {
    credential.privateKeyPem != null -> {
        val pem = AndroidBase64.encodeToString(
            credential.privateKeyPem!!.toByteArray(Charsets.UTF_8),
            AndroidBase64.NO_WRAP,
        )
        val passphrase = AndroidBase64.encodeToString(
            credential.passphrase.orEmpty().toByteArray(Charsets.UTF_8),
            AndroidBase64.NO_WRAP,
        )
        "key:$pem:$passphrase"
    }
    credential.password != null -> {
        val password = AndroidBase64.encodeToString(
            credential.password!!.toByteArray(Charsets.UTF_8),
            AndroidBase64.NO_WRAP,
        )
        "password:$password"
    }
    else -> error("a password or private key is required")
}

private fun decodeCredential(raw: String): SshCredential? = runCatching {
    val parts = raw.split(":", limit = 3)
    when (parts[0]) {
        "password" -> SshCredential(
            password = String(AndroidBase64.decode(parts[1], AndroidBase64.NO_WRAP), Charsets.UTF_8),
        )
        "key" -> {
            val passphrase = String(AndroidBase64.decode(parts[2], AndroidBase64.NO_WRAP), Charsets.UTF_8)
            SshCredential(
                privateKeyPem = String(AndroidBase64.decode(parts[1], AndroidBase64.NO_WRAP), Charsets.UTF_8),
                passphrase = passphrase.ifBlank { null },
            )
        }
        else -> null
    }
}.getOrNull()

@Composable
fun TerminalScreen(
    connections: ConnectionRepository,
    sessions: SessionManager,
    gateway: SshGateway,
    settings: SettingsRepository,
    vault: VaultRepository,
) {
    val tabs by sessions.tabs.collectAsState()
    val snapshots by sessions.snapshots.collectAsState()
    val activeId by sessions.activeId.collectAsState()
    val status by sessions.status.collectAsState()
    val appSettings by settings.settings.collectAsState(AppSettings())
    val storedProfiles by connections.profiles.collectAsState(emptyList())
    val vaultUnlocked by vault.isUnlocked.collectAsState()
    val vaultItems by vault.items.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    val terminalFocus = remember { FocusRequester() }
    val commandFocus = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current

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
    val terminalModes = TerminalModes(applicationCursorKeys = snapshot?.applicationCursorKeys ?: false)
    val bracketedPaste = snapshot?.bracketedPaste ?: false
    val selectionActive = snapshot?.selection != null
    val firstRenderedRow = (snapshot?.let { it.scrollback.size + it.rows.size } ?: 0) - lines.size

    LaunchedEffect(appSettings.scrollback) { sessions.configure(appSettings.scrollback) }
    LaunchedEffect(appSettings.showKeypad) { showKeypad = appSettings.showKeypad }
    LaunchedEffect(appSettings.useSystemKeyboard) {
        if (appSettings.useSystemKeyboard) commandFocus.requestFocus()
    }
    LaunchedEffect(activeId) { activeId?.let { selectedId = it } }
    LaunchedEffect(selected?.id) {
        if (selected == null) commandFocus.requestFocus()
    }
    LaunchedEffect(cursorRow, cursorColumn) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    fun savedCredential(profile: ConnectionProfile): SshCredential? {
        if (!vaultUnlocked) return null
        val item = vaultItems.firstOrNull { it.label == credentialLabel(profile) } ?: return null
        return vault.reveal(item.id).getOrNull()?.let(::decodeCredential)
    }

    suspend fun persistCredential(profile: ConnectionProfile, credential: SshCredential) {
        if (!vaultUnlocked) {
            connectError = "Connected, but the vault is locked; credential was not saved"
            return
        }
        val label = credentialLabel(profile)
        val kind = if (credential.privateKeyPem != null) VaultItemKind.SSH_KEY else VaultItemKind.PASSWORD
        vault.items.value.firstOrNull { it.label == label }?.let { existing ->
            vault.delete(existing.id).onFailure { failure ->
                connectError = "Credential replace failed: ${failure.message ?: failure::class.simpleName}"
            }
        }
        vault.add(label, kind, encodeCredential(credential))
            .onFailure { failure ->
                connectError = "Credential save failed: ${failure.message ?: failure::class.simpleName}"
            }
    }

    suspend fun completeConnect(
        profile: ConnectionProfile,
        credential: SshCredential,
        fingerprint: String,
        saveCredential: Boolean,
    ) {
        val result = sessions.connect(profile.toBackend(), credential, fingerprint, TerminalSize.DEFAULT)
        if (result.isSuccess) {
            selectedId = result.getOrThrow()
            runCatching { connections.touch(profile.id) }
            connectStep = null
            connectError = null
            isConnecting = false
            if (saveCredential) persistCredential(profile, credential)
        } else {
            connectStep = null
            connectError = "Connection failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            isConnecting = false
        }
    }

    suspend fun resolveHostKey(
        profile: ConnectionProfile,
        credential: SshCredential,
        saveCredential: Boolean,
    ) {
        val trusted = connections.trustedFingerprint(profile.host)
        if (trusted != null) {
            completeConnect(profile, credential, trusted, saveCredential)
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
                        completeConnect(profile, credential, info.fingerprint, saveCredential)
                    }
                } else {
                    isConnecting = false
                    connectStep = ConnectStep.HostKey(profile, credential, info, saveCredential)
                }
            }
        }
    }

    fun requestConnect(profile: ConnectionProfile) {
        if (profile.authType == AuthType.AGENT) {
            connectError = "Agent authentication is not implemented"
            return
        }
        sessions.clearStatus()
        connectError = null
        val saved = savedCredential(profile)
        if (saved != null) {
            isConnecting = true
            scope.launch { resolveHostKey(profile, saved, saveCredential = false) }
            return
        }
        connectStep = ConnectStep.Credentials(profile)
    }

    fun submitCommand() {
        if (input.isBlank()) {
            inputError = "Enter a command first"
            return
        }
        parseQuickTarget(input)?.let { quick ->
            val label = "${quick.username}@${quick.host}:${quick.port}"
            val existing = profiles.firstOrNull {
                it.username == quick.username && it.host == quick.host && it.port == quick.port
            }
            val draft = ConnectionProfile(
                id = "quick-$label",
                name = label,
                host = quick.host,
                port = quick.port,
                username = quick.username,
                authType = AuthType.SSH_KEY,
                group = "Quick",
                lastUsed = System.currentTimeMillis(),
            )
            val saved = savedCredential(existing ?: draft)
            val profile = existing ?: draft.copy(
                authType = if (saved?.password != null && saved.privateKeyPem == null) {
                    AuthType.PASSWORD
                } else {
                    AuthType.SSH_KEY
                },
            )
            input = ""
            inputError = null
            requestConnect(profile)
            return
        }
        if (selected == null) {
            inputError = "Start a connection first, e.g. ssh user@host -p 22"
            return
        }
        sessions.write(input.toByteArray(Charsets.UTF_8) + KeySequences.bytes(TerminalKey.Enter, modes = terminalModes))
        input = ""
        inputError = null
    }

    BackHandler(enabled = showKeypad || connectStep != null || showProfilePicker) {
        when {
            connectStep != null -> connectStep = null
            showProfilePicker -> showProfilePicker = false
            else -> showKeypad = false
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
                    if (appSettings.useSystemKeyboard) return@onKeyEvent false
                    val key = hardwareKey(event) ?: return@onKeyEvent false
                    sessions.write(KeySequences.bytes(key, hardwareModifiers(event), terminalModes))
                    true
                },
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp),
            ) {
                val selection = snapshot?.selection
                lines.forEachIndexed { index, line ->
                    val absoluteRow = firstRenderedRow + index
                    val range = selection?.columnRangeForRow(absoluteRow)
                    val lastCharacter = (line.text.length - 1).coerceAtLeast(0)
                    TerminalOutputLine(
                        line = line,
                        fontSize = appSettings.fontSize,
                        selectionStart = range?.start,
                        selectionEnd = range?.last?.let { end ->
                            if (end == Int.MAX_VALUE) lastCharacter else end.coerceAtMost(lastCharacter)
                        },
                        onLongPress = {
                            sessions.beginSelection(absoluteRow, 0)
                            sessions.selectLine()
                        },
                        onTap = {
                            if (selection != null) {
                                sessions.moveSelectionHead(absoluteRow, selection.headColumn)
                            }
                        },
                    )
                }
            }
        }

        if (appSettings.useSystemKeyboard || selected == null) {
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
                    label = { Text("Command / ssh user@host") },
                    singleLine = true,
                    isError = inputError != null,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(commandFocus),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitCommand() }),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { submitCommand() }) { Text("Send") }
            }
        }
        inputError?.let {
            ErrorState(it)
            Spacer(Modifier.height(4.dp))
        }

        if (showKeypad && !appSettings.useSystemKeyboard) {
            InAppKeyboard(
                modifiers = modifiers,
                capsLock = capsLock,
                pad = pad,
                selectionActive = selectionActive,
                showFunctionRow = showFunctionRow,
                haptics = appSettings.haptics,
                onKey = { key ->
                    if (key == TerminalKey.CapsLock) {
                        capsLock = !capsLock
                    } else {
                        sessions.write(KeySequences.bytes(key, modifiers, terminalModes))
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
                        SelectionAction.SELECT -> sessions.beginSelection(cursorRow, cursorColumn)
                        SelectionAction.SELECT_ALL -> sessions.selectAll()
                        SelectionAction.SELECT_WORD -> sessions.selectWord()
                        SelectionAction.SELECT_LINE -> sessions.selectLine()
                        SelectionAction.CLEAR -> sessions.clearSelection()
                        SelectionAction.MOVE_LEFT -> sessions.extendSelection(0, -1)
                        SelectionAction.MOVE_UP -> sessions.extendSelection(-1, 0)
                        SelectionAction.MOVE_DOWN -> sessions.extendSelection(1, 0)
                        SelectionAction.MOVE_RIGHT -> sessions.extendSelection(0, 1)
                        SelectionAction.COPY -> {
                            val copied = sessions.selectionText()
                            if (copied.isEmpty()) {
                                inputError = "Nothing selected to copy"
                            } else {
                                clipboard.setText(AnnotatedString(copied))
                                sessions.clearSelection()
                                inputError = null
                            }
                        }
                        SelectionAction.PASTE -> {
                            val pasted = clipboard.getText()?.text
                            if (pasted.isNullOrEmpty()) {
                                inputError = "Clipboard is empty"
                            } else {
                                sessions.write(KeySequences.paste(pasted, bracketedPaste).toByteArray(Charsets.UTF_8))
                                inputError = null
                            }
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
            canSave = vaultUnlocked,
            vaultItems = vaultItems.filter { it.kind != VaultItemKind.NOTE },
            onRevealVaultItem = { id -> vault.reveal(id).getOrNull() },
            onDismiss = { connectStep = null },
            onSubmit = { credential, saveCredential ->
                connectStep = null
                isConnecting = true
                scope.launch { resolveHostKey(step.profile, credential, saveCredential) }
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
                        completeConnect(step.profile, step.credential, step.info.fingerprint, step.saveCredential)
                    }
                }
            },
            onReject = { connectStep = null },
        )
    }
}

@Composable
private fun SessionTabs(sessions: List<SessionTab>, selectedId: String?, onSelect: (String) -> Unit) {
    if (sessions.isEmpty()) return
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
                "Type ssh user@host -p 22 below, or choose a saved profile.",
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
                    Text("No profiles saved. Type ssh user@host -p 22 instead.")
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
    canSave: Boolean,
    vaultItems: List<BackendVaultItem>,
    onRevealVaultItem: (String) -> String?,
    onDismiss: () -> Unit,
    onSubmit: (SshCredential, Boolean) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var privateKey by rememberSaveable { mutableStateOf("") }
    var keyPassphrase by rememberSaveable { mutableStateOf("") }
    var usesKey by rememberSaveable(profile.id, profile.authType) {
        mutableStateOf(profile.authType == AuthType.SSH_KEY)
    }
    var saveCredential by rememberSaveable { mutableStateOf(canSave) }
    var attempted by remember { mutableStateOf(false) }
    val missing = if (usesKey) privateKey.isBlank() else password.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Authenticate ${profile.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Authentication", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = usesKey,
                        onClick = { usesKey = true },
                        label = { Text("SSH key") },
                    )
                    FilterChip(
                        selected = !usesKey,
                        onClick = { usesKey = false },
                        label = { Text("Password") },
                    )
                }
                if (vaultItems.isNotEmpty()) {
                    Text("Use saved credential", style = MaterialTheme.typography.labelLarge)
                    vaultItems.take(4).forEach { item ->
                        TextButton(onClick = {
                            onRevealVaultItem(item.id)?.let { secret ->
                                if (item.kind == VaultItemKind.SSH_KEY || item.kind == VaultItemKind.CERTIFICATE) {
                                    usesKey = true
                                    privateKey = secret
                                } else {
                                    usesKey = false
                                    password = secret
                                }
                                attempted = false
                            }
                        }) { Text(item.label) }
                    }
                }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = saveCredential && canSave,
                        enabled = canSave,
                        onCheckedChange = { saveCredential = it },
                    )
                    Text(
                        if (canSave) "Save in Vault" else "Unlock Vault to save this credential",
                        style = MaterialTheme.typography.bodySmall,
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
                        canSave && saveCredential,
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
private fun TerminalOutputLine(
    line: TerminalLine,
    fontSize: Float,
    selectionStart: Int? = null,
    selectionEnd: Int? = null,
    onLongPress: () -> Unit = {},
    onTap: () -> Unit = {},
) {
    val colour = when (line.kind) {
        TerminalLineKind.COMMAND -> MaterialTheme.colorScheme.primary
        TerminalLineKind.ERROR -> MaterialTheme.colorScheme.error
        TerminalLineKind.SUCCESS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
    )
    val hasSelection = selectionStart != null && selectionEnd != null &&
        selectionStart <= selectionEnd && selectionStart in line.text.indices
    val rendered = if (hasSelection && selectionStart != null && selectionEnd != null) {
        val from = selectionStart.coerceIn(0, line.text.length)
        val to = (selectionEnd + 1).coerceIn(from, line.text.length)
        buildAnnotatedString {
            append(line.text)
            addStyle(SpanStyle(background = MaterialTheme.colorScheme.secondaryContainer), from, to)
        }
    } else {
        AnnotatedString(line.text)
    }

    Text(
        text = rendered,
        style = style,
        color = colour,
        modifier = Modifier.pointerInput(onLongPress, onTap) {
            detectTapGestures(onLongPress = { onLongPress() }, onTap = { onTap() })
        },
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
    if (event.nativeKeyEvent.isMetaPressed) pressed = pressed + KeypadModifier.META
    return ModifierState(pressed = pressed)
}
