/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.ui.components.ConfirmDialog
import com.ratiojuris.droidterminal.ui.components.EmptyState
import com.ratiojuris.droidterminal.ui.components.ErrorState
import com.ratiojuris.droidterminal.ui.components.FormTextField
import com.ratiojuris.droidterminal.ui.components.SectionHeader
import com.ratiojuris.droidterminal.ui.model.MockData
import com.ratiojuris.droidterminal.ui.model.UiValidation
import com.ratiojuris.droidterminal.ui.model.VaultItem
import com.ratiojuris.droidterminal.ui.model.VaultItemKind

@Composable
fun VaultScreen() {
    var unlocked by remember { mutableStateOf(false) }
    var passphrase by rememberSaveable { mutableStateOf("") }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf(MockData.vaultItems) }
    var showAddForm by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<VaultItem?>(null) }
    var pendingDelete by remember { mutableStateOf<VaultItem?>(null) }
    var requireBiometric by rememberSaveable { mutableStateOf(false) }
    var autoLock by rememberSaveable { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = detail != null || pendingDelete != null || showAddForm) {
        when {
            detail != null -> detail = null
            pendingDelete != null -> pendingDelete = null
            else -> showAddForm = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!unlocked) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vault locked", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Unlock to view credentials and SSH keys.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FormTextField(
                        "Passphrase",
                        passphrase,
                        {
                            passphrase = it
                            passphraseError = null
                        },
                        passphraseError,
                        password = true,
                    )
                    passphraseError?.let { ErrorState(it) }
                    Button(onClick = {
                        passphraseError = UiValidation.validatePassphrase(passphrase)
                        if (passphraseError == null) {
                            // TODO(phase2): unlock the Android Keystore-backed vault.
                            unlocked = true
                            passphrase = ""
                        }
                    }) { Text("Unlock") }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vault unlocked", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    // TODO(phase2): wipe in-memory plaintext on lock.
                    unlocked = false
                }) { Text("Lock") }
            }

            Button(onClick = { showAddForm = true }) { Text("Add credential") }

            notice?.let {
                ErrorState(it, onRetry = { notice = null })
            }

            if (items.isEmpty()) {
                EmptyState(
                    "No credentials",
                    "Store a password, SSH key or certificate in this vault.",
                    "Add credential",
                    { showAddForm = true },
                )
            } else {
                SectionHeader("${items.size} stored item(s)")
                items.forEach { item ->
                    VaultItemCard(
                        item = item,
                        onOpen = { detail = item },
                        onDelete = { pendingDelete = item },
                    )
                }
            }

            SectionHeader("Vault options")
            SettingToggle("Require biometric unlock", requireBiometric) {
                // TODO(phase2): wire androidx.biometric prompt and keystore-gated access.
                requireBiometric = it
            }
            SettingToggle("Auto-lock after 5 minutes", autoLock) {
                // TODO(phase2): bind to lifecycle background timer.
                autoLock = it
            }
        }
    }

    if (showAddForm) {
        VaultItemForm(
            onDismiss = { showAddForm = false },
            onSave = { item ->
                // TODO(phase2): encrypt and persist through the vault module.
                items = items + item.copy(id = "v${items.size + 1}")
                showAddForm = false
                notice = null
            },
        )
    }

    detail?.let { item ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(item.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kind: ${item.kind.label}")
                    Text("Updated: ${item.updated}")
                    // TODO(phase2): reveal only after explicit, authenticated user action.
                    Text(item.secret, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete credential",
            message = "Remove ${target.label} from the vault?",
            confirmLabel = "Delete",
            onConfirm = {
                // TODO(phase2): delete ciphertext and purge the key from the Keystore.
                items = items.filter { it.id != target.id }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun VaultItemCard(item: VaultItem, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.label, style = MaterialTheme.typography.titleSmall)
            Text("${item.kind.label} · updated ${item.updated}", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onOpen) { Text("View") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun VaultItemForm(onDismiss: () -> Unit, onSave: (VaultItem) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(VaultItemKind.PASSWORD) }
    var attempted by remember { mutableStateOf(false) }
    val errors = UiValidation.validateVaultItem(label, secret)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add credential") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormTextField("Label", label, { label = it }, if (attempted) errors["label"] else null)
                FormTextField("Value", secret, { secret = it }, if (attempted) errors["secret"] else null, password = true)
                Text("Type", style = MaterialTheme.typography.labelLarge)
                Row {
                    VaultItemKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
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
                        VaultItem(
                            id = "",
                            label = label.trim(),
                            kind = kind,
                            updated = "2026-11-24",
                            secret = secret,
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
