/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.terminal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.ui.components.ErrorState
import com.ratiojuris.droidterminal.ui.components.KeyCap
import com.ratiojuris.droidterminal.ui.components.LoadingState
import com.ratiojuris.droidterminal.ui.components.SectionHeader
import com.ratiojuris.droidterminal.ui.model.MockData
import com.ratiojuris.droidterminal.ui.model.SessionTab
import com.ratiojuris.droidterminal.ui.model.TerminalLine
import com.ratiojuris.droidterminal.ui.model.TerminalLineKind
import kotlinx.coroutines.delay

@Composable
fun TerminalScreen() {
    var sessions by remember { mutableStateOf(MockData.sessions) }
    var selectedId by rememberSaveable { mutableStateOf(MockData.sessions.first().id) }
    var lines by remember { mutableStateOf(MockData.terminalLines) }
    var input by rememberSaveable { mutableStateOf("") }
    var showKeypad by rememberSaveable { mutableStateOf(true) }
    var isConnecting by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }

    val selected = sessions.firstOrNull { it.id == selectedId } ?: sessions.firstOrNull()
    val scrollState = rememberScrollState()

    BackHandler(enabled = showKeypad) { showKeypad = false }

    LaunchedEffect(isConnecting) {
        // TODO(phase2): replace the timed transition with real SSH connection state.
        if (isConnecting) {
            delay(700)
            isConnecting = false
        }
    }

    LaunchedEffect(lines.size) {
        // TODO(phase2): follow the live terminal engine cursor instead of line count.
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SessionTabs(sessions, selectedId) { id ->
            selectedId = id
            inputError = null
        }

        selected?.let { session ->
            ConnectionBar(
                session = session,
                onToggle = {
                    // TODO(phase2): open/close a real SSH session for this profile.
                    sessions = sessions.map { tab ->
                        if (tab.id == session.id) tab.copy(connected = !tab.connected) else tab
                    }
                    if (!session.connected) isConnecting = true
                },
            )
        } ?: EmptySessionBanner()

        if (isConnecting) LoadingState("Connecting…")

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp),
            ) {
                lines.forEach { line -> TerminalOutputLine(line) }
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
                // TODO(phase2): encode input with KeySequences and write to the session channel.
                val prompt = selected?.host?.let { "$it$ " } ?: "$ "
                lines = lines + listOf(
                    TerminalLine("$prompt$input", TerminalLineKind.COMMAND),
                    TerminalLine("Command queued (mock output)", TerminalLineKind.OUTPUT),
                    TerminalLine("$prompt", TerminalLineKind.PROMPT),
                )
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

        if (showKeypad) ShortcutKeypad { token -> input += token }
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
private fun EmptySessionBanner() {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No session selected", style = MaterialTheme.typography.titleSmall)
            Text(
                "Create a connection to start a terminal session.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TerminalOutputLine(line: TerminalLine) {
    val colour = when (line.kind) {
        TerminalLineKind.COMMAND -> MaterialTheme.colorScheme.primary
        TerminalLineKind.ERROR -> MaterialTheme.colorScheme.error
        TerminalLineKind.SUCCESS -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = line.text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = colour,
    )
}

@Composable
private fun ShortcutKeypad(onKey: (String) -> Unit) {
    SectionHeader("Shortcut keys")
    val rows = listOf(
        listOf("Esc", "Tab", "Ctrl", "Alt", "Shift", "Fn"),
        listOf("F1", "F2", "F3", "F4", "F5", "F6"),
        listOf("↑", "↓", "←", "→", "Home", "End"),
        listOf("Enter", "Backspace", "PgUp", "PgDn", "Ctrl+C", "Ctrl+D"),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { key ->
                    KeyCap(
                        text = key,
                        onClick = { onKey("<$key>") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
