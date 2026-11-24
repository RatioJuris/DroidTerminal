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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.ui.components.ConfirmDialog
import com.ratiojuris.droidterminal.ui.components.EmptyState
import com.ratiojuris.droidterminal.ui.components.ErrorState
import com.ratiojuris.droidterminal.ui.components.LoadingState
import com.ratiojuris.droidterminal.ui.components.SectionHeader
import com.ratiojuris.droidterminal.ui.model.MockData
import com.ratiojuris.droidterminal.ui.model.RemoteFile
import com.ratiojuris.droidterminal.ui.model.TransferDirection
import com.ratiojuris.droidterminal.ui.model.TransferItem
import com.ratiojuris.droidterminal.ui.model.TransferStatus
import kotlinx.coroutines.delay

private val ROOT_PATH = "/home/deploy"
private val DIRECTORIES: Map<String, List<RemoteFile>> = mapOf(
    ROOT_PATH to MockData.directory,
    "$ROOT_PATH/bin" to listOf(
        RemoteFile("app", "$ROOT_PATH/bin/app", false, "4.2 KB", "-rwxr-xr-x", "2026-11-20 09:12"),
        RemoteFile("deploy", "$ROOT_PATH/bin/deploy", false, "1.1 KB", "-rwxr-xr-x", "2026-11-20 09:12"),
    ),
    "$ROOT_PATH/logs" to listOf(
        RemoteFile("app.log", "$ROOT_PATH/logs/app.log", false, "1.8 MB", "-rw-r-----", "2026-11-23 14:03"),
        RemoteFile("error.log", "$ROOT_PATH/logs/error.log", false, "64 KB", "-rw-r-----", "2026-11-22 23:40"),
    ),
)

@Composable
fun SftpScreen() {
    var tab by rememberSaveable { mutableStateOf(0) }
    var path by rememberSaveable { mutableStateOf(ROOT_PATH) }
    var files by remember { mutableStateOf(DIRECTORIES[ROOT_PATH].orEmpty()) }
    var actionFile by remember { mutableStateOf<RemoteFile?>(null) }
    var deleteFile by remember { mutableStateOf<RemoteFile?>(null) }
    var renameFile by remember { mutableStateOf<RemoteFile?>(null) }
    var transfers by remember { mutableStateOf(MockData.transfers) }
    var isLoading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = actionFile != null || deleteFile != null || renameFile != null) {
        when {
            actionFile != null -> actionFile = null
            deleteFile != null -> deleteFile = null
            else -> renameFile = null
        }
    }

    LaunchedEffect(isLoading) {
        // TODO(phase2): refresh through a real SFTP session.
        if (isLoading) {
            delay(500)
            isLoading = false
        }
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
                    TextButton(onClick = { isLoading = true }) { Text("Refresh") }
                }
                if (path != ROOT_PATH) {
                    TextButton(onClick = {
                        path = ROOT_PATH
                        files = DIRECTORIES[ROOT_PATH].orEmpty()
                    }) { Text("← Back to $ROOT_PATH") }
                }
                if (isLoading) LoadingState("Reading directory…")
                notice?.let { ErrorState(it, onRetry = { notice = null }) }

                if (files.isEmpty()) {
                    EmptyState("Empty directory", "This folder has no entries.")
                } else {
                    SectionHeader("${files.size} item(s)")
                    files.forEach { file ->
                        FileCard(file) {
                            if (file.isDirectory) {
                                // TODO(phase2): list the directory from the remote filesystem.
                                path = file.path
                                files = DIRECTORIES[file.path].orEmpty()
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
                if (transfers.isEmpty()) {
                    EmptyState("No transfers", "Uploads and downloads appear here.")
                } else {
                    transfers.forEach { transfer ->
                        TransferCard(transfer) {
                            transfers = transfers.map { item ->
                                if (item.id == transfer.id && item.status != TransferStatus.DONE) {
                                    item.copy(status = TransferStatus.CANCELLED)
                                } else {
                                    item
                                }
                            }
                        }
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
                    // TODO(phase2): enqueue a real SFTP download with progress reporting.
                    transfers = transfers + TransferItem(
                        id = "t${transfers.size + 1}",
                        name = file.name,
                        direction = TransferDirection.DOWNLOAD,
                        progress = 0f,
                        status = TransferStatus.QUEUED,
                    )
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
                        // TODO(phase2): issue an SFTP rename and refresh the directory.
                        files = files.map { entry ->
                            if (entry.path == file.path) {
                                entry.copy(name = newName, path = entry.path.replaceAfterLast('/', newName))
                            } else {
                                entry
                            }
                        }
                        renameFile = null
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
                // TODO(phase2): perform a confirmed SFTP delete with audit logging.
                files = files.filter { it.path != file.path }
                deleteFile = null
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
