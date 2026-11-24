/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.sftp

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.sessions.SessionManager
import com.ratiojuris.droidterminal.sftp.SftpService
import com.ratiojuris.droidterminal.ui.components.ConfirmDialog
import com.ratiojuris.droidterminal.ui.components.EmptyState
import com.ratiojuris.droidterminal.ui.components.ErrorState
import com.ratiojuris.droidterminal.ui.components.LoadingState
import com.ratiojuris.droidterminal.ui.components.SectionHeader
import com.ratiojuris.droidterminal.ui.model.RemoteFile
import com.ratiojuris.droidterminal.ui.model.TransferItem
import com.ratiojuris.droidterminal.ui.model.TransferStatus
import com.ratiojuris.droidterminal.ui.model.toBackend
import com.ratiojuris.droidterminal.ui.model.toUi
import kotlinx.coroutines.launch

@Composable
fun SftpScreen(sftp: SftpService, sessions: SessionManager) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var actionFile by remember { mutableStateOf<RemoteFile?>(null) }
    var deleteFile by remember { mutableStateOf<RemoteFile?>(null) }
    var renameFile by remember { mutableStateOf<RemoteFile?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val entries by sftp.entries.collectAsState()
    val transfers by sftp.transfers.collectAsState()
    val isLoading by sftp.isLoading.collectAsState()
    val error by sftp.error.collectAsState()
    val path by sftp.path.collectAsState()
    val home by sftp.home.collectAsState()
    val activeTarget by sessions.activeTarget.collectAsState()

    val files = entries.map { it.toUi() }
    val uiTransfers = transfers.map { it.toUi() }

    BackHandler(enabled = actionFile != null || deleteFile != null || renameFile != null) {
        when {
            actionFile != null -> actionFile = null
            deleteFile != null -> deleteFile = null
            else -> renameFile = null
        }
    }

    LaunchedEffect(activeTarget?.host) {
        if (activeTarget != null) sftp.refresh()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Files") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Transfers") })
        }

        if (tab == 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(path, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { sftp.refresh() } }) { Text("Refresh") }
                }
                val homePath = home
                if (homePath != null && path != homePath) {
                    TextButton(onClick = {
                        scope.launch {
                            sftp.setPath(homePath)
                            sftp.refresh(homePath)
                        }
                    }) { Text("← Back to $homePath") }
                }
                if (isLoading) LoadingState("Reading directory…")
                error?.let {
                    ErrorState(it, onRetry = {
                        sftp.clearError()
                        scope.launch { sftp.refresh() }
                    })
                }
                notice?.let {
                    ErrorState(it, onRetry = { notice = null })
                }

                if (activeTarget == null) {
                    EmptyState("No SFTP session", "Connect an SSH session in Terminal first.")
                } else if (files.isEmpty() && !isLoading) {
                    EmptyState("Empty directory", "This folder has no entries.")
                } else {
                    SectionHeader("${files.size} item(s)")
                    files.forEach { file ->
                        FileCard(file) {
                            if (file.isDirectory) {
                                scope.launch {
                                    sftp.setPath(file.path)
                                    sftp.refresh(file.path)
                                }
                            } else {
                                actionFile = file
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiTransfers.isEmpty()) {
                    EmptyState("No transfers", "Uploads and downloads appear here.")
                } else {
                    uiTransfers.forEach { transfer ->
                        TransferCard(transfer) { sftp.cancelTransfer(transfer.id) }
                    }
                }
            }
        }
    }

    actionFile?.let { file ->
        AlertDialog(
            onDismissRequest = { actionFile = null },
            title = { Text(file.name) },
            text = { Text("${file.permissions} · ${file.size} · ${file.modified}") },
            confirmButton = {
                Button(onClick = {
                    sftp.enqueueDownload(file.toBackend())
                    actionFile = null
                    tab = 1
                }) { Text("Download") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { renameFile = file; actionFile = null }) { Text("Rename") }
                    TextButton(onClick = { deleteFile = file; actionFile = null }) { Text("Delete") }
                }
            },
        )
    }

    renameFile?.let { file ->
        var newName by remember(file) { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { renameFile = null },
            title = { Text("Rename ${file.name}") },
            text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("New name") }) },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        scope.launch {
                            sftp.rename(file.toBackend(), newName.trim())
                                .onSuccess {
                                    renameFile = null
                                    sftp.refresh()
                                }
                                .onFailure { failure ->
                                    notice = "Rename failed: ${failure.message ?: failure::class.simpleName}"
                                    renameFile = null
                                }
                        }
                    } else {
                        notice = "New name cannot be empty"
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameFile = null }) { Text("Cancel") } },
        )
    }

    deleteFile?.let { file ->
        ConfirmDialog(
            title = "Delete file",
            message = "Permanently delete ${file.name}? This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    sftp.delete(file.toBackend())
                        .onSuccess {
                            deleteFile = null
                            sftp.refresh()
                        }
                        .onFailure { failure ->
                            notice = "Delete failed: ${failure.message ?: failure::class.simpleName}"
                            deleteFile = null
                        }
                }
            },
            onDismiss = { deleteFile = null },
        )
    }
}

@Composable
private fun FileCard(file: RemoteFile, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (file.isDirectory) "DIR" else "FILE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                Text("${file.size} · ${file.permissions}", style = MaterialTheme.typography.bodySmall)
            }
            Text(file.modified, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TransferCard(transfer: TransferItem, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(transfer.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(transfer.status.label, style = MaterialTheme.typography.labelSmall)
            }
            Text("${transfer.direction.label} · ${(transfer.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { transfer.progress }, modifier = Modifier.fillMaxWidth())
            if (transfer.status != TransferStatus.DONE && transfer.status != TransferStatus.CANCELLED) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            if (transfer.status == TransferStatus.FAILED) {
                Spacer(Modifier.height(2.dp))
                ErrorState("Transfer failed. Retry from the source host.")
            }
        }
    }
}
