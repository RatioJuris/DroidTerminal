/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.connections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.connections.ConnectionRepository
import com.ratiojuris.droidterminal.sshengine.HostKeyInfo
import com.ratiojuris.droidterminal.sshengine.SshGateway
import com.ratiojuris.droidterminal.ui.components.ConfirmDialog
import com.ratiojuris.droidterminal.ui.components.EmptyState
import com.ratiojuris.droidterminal.ui.components.ErrorState
import com.ratiojuris.droidterminal.ui.components.FormTextField
import com.ratiojuris.droidterminal.ui.components.LoadingState
import com.ratiojuris.droidterminal.ui.components.SectionHeader
import com.ratiojuris.droidterminal.ui.model.AuthType
import com.ratiojuris.droidterminal.ui.model.ConnectionProfile
import com.ratiojuris.droidterminal.ui.model.ConnectionSort
import com.ratiojuris.droidterminal.ui.model.UiValidation
import com.ratiojuris.droidterminal.ui.model.filterAndSortConnections
import com.ratiojuris.droidterminal.ui.model.toBackend
import com.ratiojuris.droidterminal.ui.model.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectionsScreen(repository: ConnectionRepository, gateway: SshGateway) {
    var profiles by remember { mutableStateOf(emptyList<ConnectionProfile>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ConnectionSort.NAME) }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ConnectionProfile?>(null) }
    var pendingDelete by remember { mutableStateOf<ConnectionProfile?>(null) }
    var hostKeyProfile by remember { mutableStateOf<ConnectionProfile?>(null) }
    var hostKeyInfo by remember { mutableStateOf<HostKeyInfo?>(null) }
    var hostKeyError by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = hostKeyProfile != null || pendingDelete != null || showForm) {
        when {
            hostKeyProfile != null -> hostKeyProfile = null
            pendingDelete != null -> pendingDelete = null
            else -> showForm = false
        }
    }

    fun loadProfiles() {
        scope.launch {
            isRefreshing = true
            runCatching { repository.refresh() }
                .onSuccess { stored ->
                    profiles = stored.map { it.toUi() }
                    notice = null
                }
                .onFailure { failure ->
                    notice = "Profile load failed: ${failure.message ?: failure::class.simpleName}"
                }
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { loadProfiles() }

    LaunchedEffect(hostKeyProfile) {
        val target = hostKeyProfile
        hostKeyInfo = null
        hostKeyError = null
        if (target == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            gateway.fetchHostKey(target.host, target.port, target.username)
        }.onSuccess { info ->
            hostKeyInfo = info
        }.onFailure { failure ->
            hostKeyError = "Host key read failed: ${failure.message ?: failure::class.simpleName}"
        }
    }

    val visible = filterAndSortConnections(profiles, query, sort)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search profiles") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                editing = null
                showForm = true
            }) { Text("Add") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sort:", style = MaterialTheme.typography.labelLarge)
            ConnectionSort.entries.forEach { option ->
                FilterChip(
                    selected = sort == option,
                    onClick = { sort = option },
                    label = { Text(option.label) },
                )
            }
        }

        TextButton(onClick = { loadProfiles() }) { Text("Refresh profiles") }
        if (isRefreshing) LoadingState("Loading profiles…")

        notice?.let {
            ErrorState(it, onRetry = { notice = null })
            Spacer(Modifier.height(4.dp))
        }

        if (visible.isEmpty()) {
            EmptyState(
                title = if (profiles.isEmpty()) "No connections yet" else "No matches",
                message = if (profiles.isEmpty()) {
                    "Add an SSH profile to get started."
                } else {
                    "Try a different name, host, username or group."
                },
                actionLabel = if (profiles.isEmpty()) "Add profile" else null,
                onAction = { showForm = true },
            )
        } else {
            SectionHeader("${visible.size} profile(s)")
            visible.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    onEdit = {
                        editing = profile
                        showForm = true
                    },
                    onDelete = { pendingDelete = profile },
                    onVerify = { hostKeyProfile = profile },
                )
            }
        }
    }

    if (showForm) {
        ProfileForm(
            initial = editing,
            onDismiss = { showForm = false },
            onSave = { draft ->
                scope.launch {
                    runCatching { repository.upsert(draft.toBackend()) }
                        .onSuccess { saved ->
                            profiles = if (draft.id.isBlank()) {
                                profiles + saved.toUi()
                            } else {
                                profiles.map { if (it.id == saved.id) saved.toUi() else it }
                            }
                            showForm = false
                            notice = null
                        }
                        .onFailure { failure ->
                            notice = "Profile save failed: ${failure.message ?: failure::class.simpleName}"
                        }
                }
            },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete profile",
            message = "Remove ${target.name}? This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    runCatching {
                        repository.delete(target.id)
                        repository.revokeHost(target.host)
                    }.onSuccess {
                        profiles = profiles.filterNot { it.id == target.id }
                        pendingDelete = null
                    }.onFailure { failure ->
                        notice = "Delete failed: ${failure.message ?: failure::class.simpleName}"
                        pendingDelete = null
                    }
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    hostKeyProfile?.let { target ->
        val info = hostKeyInfo
        AlertDialog(
            onDismissRequest = { hostKeyProfile = null },
            title = { Text("Verify host key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${target.host}:${target.port}")
                    when {
                        info != null -> {
                            Text(info.algorithm, style = MaterialTheme.typography.bodySmall)
                            Text(info.fingerprint, style = MaterialTheme.typography.bodySmall)
                        }
                        hostKeyError != null -> ErrorState(hostKeyError!!)
                        else -> LoadingState("Reading host key…")
                    }
                    Text(
                        "Accepting an unknown key is blocked until it is verified out of band.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(enabled = info != null, onClick = {
                    if (info != null) {
                        scope.launch {
                            runCatching {
                                repository.trustHost(target.host, info.fingerprint, info.algorithm)
                            }.onSuccess {
                                notice = "Host key accepted for ${target.host}"
                                hostKeyProfile = null
                            }.onFailure { failure ->
                                hostKeyError = "Accept failed: ${failure.message ?: failure::class.simpleName}"
                            }
                        }
                    }
                }) { Text("Accept") }
            },
            dismissButton = { TextButton(onClick = { hostKeyProfile = null }) { Text("Reject") } },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ConnectionProfile,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onVerify: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleSmall)
            Text("${profile.username}@${profile.host}:${profile.port}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.authType.label, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Text(profile.group, style = MaterialTheme.typography.labelMedium)
            }
            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onVerify) { Text("Host key") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun ProfileForm(
    initial: ConnectionProfile?,
    onDismiss: () -> Unit,
    onSave: (ConnectionProfile) -> Unit,
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial?.name ?: "") }
    var host by rememberSaveable(initial) { mutableStateOf(initial?.host ?: "") }
    var port by rememberSaveable(initial) { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by rememberSaveable(initial) { mutableStateOf(initial?.username ?: "") }
    var group by rememberSaveable(initial) { mutableStateOf(initial?.group ?: "") }
    var authType by rememberSaveable(initial) { mutableStateOf(initial?.authType ?: AuthType.PASSWORD) }
    var attempted by remember { mutableStateOf(false) }

    val errors = UiValidation.validateConnection(name, host, port, username)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New connection" else "Edit connection") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FormTextField("Profile name", name, { name = it }, if (attempted) errors["name"] else null)
                FormTextField("Host", host, { host = it }, if (attempted) errors["host"] else null)
                FormTextField("Port", port, { port = it }, if (attempted) errors["port"] else null, numeric = true)
                FormTextField("Username", username, { username = it }, if (attempted) errors["username"] else null)
                FormTextField("Group", group, { group = it })
                Text("Authentication", style = MaterialTheme.typography.labelLarge)
                Row {
                    AuthType.entries.forEach { option ->
                        FilterChip(
                            selected = authType == option,
                            onClick = { authType = option },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                attempted = true
                if (errors.isEmpty()) {
                    onSave(
                        ConnectionProfile(
                            id = initial?.id ?: "",
                            name = name.trim(),
                            host = host.trim(),
                            port = port.toInt(),
                            username = username.trim(),
                            authType = authType,
                            group = group.trim().ifEmpty { "General" },
                            lastUsed = initial?.lastUsed ?: 0L,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
