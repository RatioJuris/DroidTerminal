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
import com.ratiojuris.droidterminal.auth.BiometricAuth
import com.ratiojuris.droidterminal.auth.VaultAutoLock
import com.ratiojuris.droidterminal.settings.AppSettings
import com.ratiojuris.droidterminal.settings.SettingsRepository
import com.ratiojuris.droidterminal.ui.components.ConfirmDialog
import com.ratiojuris.droidterminal.ui.components.EmptyState
import com.ratiojuris.droidterminal.ui.components.ErrorState
import com.ratiojuris.droidterminal.ui.components.FormTextField
import com.ratiojuris.droidterminal.ui.components.SectionHeader
import com.ratiojuris.droidterminal.ui.model.UiValidation
import com.ratiojuris.droidterminal.ui.model.VaultItem
import com.ratiojuris.droidterminal.ui.model.VaultItemKind
import com.ratiojuris.droidterminal.ui.model.toBackend
import com.ratiojuris.droidterminal.ui.model.toUi
import com.ratiojuris.droidterminal.vault.VaultRepository
import kotlinx.coroutines.launch

@Composable
fun VaultScreen(
    vault: VaultRepository,
    autoLock: VaultAutoLock,
    biometric: BiometricAuth,
    settings: SettingsRepository,
) {
    val unlocked by vault.isUnlocked.collectAsState()
    val storedItems by vault.items.collectAsState()
    val settingsState by settings.settings.collectAsState(AppSettings())
    val items = storedItems.map { it.toUi() }
    val scope = rememberCoroutineScope()

    var passphrase by rememberSaveable { mutableStateOf("") }
    var passphraseError by remember { mutableStateOf<String?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<VaultItem?>(null) }
    var revealedSecret by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<VaultItem?>(null) }
    var requireBiometric by remember { mutableStateOf(settingsState.requireBiometric) }
    var autoLockEnabled by remember { mutableStateOf(settingsState.autoLockVault) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settingsState) {
        requireBiometric = settingsState.requireBiometric
        autoLockEnabled = settingsState.autoLockVault
        autoLock.enabled = settingsState.autoLockVault
    }

    BackHandler(enabled = detail != null || pendingDelete != null || showAddForm) {
        when {
            detail != null -> {
                detail = null
                revealedSecret = null
            }
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
                            scope.launch {
                                vault.unlock(passphrase)
                                    .onSuccess {
                                        passphrase = ""
                                        notice = null
                                    }
                                    .onFailure { failure ->
                                        passphraseError = failure.message ?: "Vault unlock failed"
                                    }
                            }
                        }
                    }) { Text("Unlock") }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vault unlocked", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    vault.lock()
                    detail = null
                    revealedSecret = null
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
                        onOpen = {
                            detail = item
                            revealedSecret = null
                        },
                        onDelete = { pendingDelete = item },
                    )
                }
            }

            SectionHeader("Vault options")
            SettingToggle("Require biometric unlock", requireBiometric) { enabled ->
                requireBiometric = enabled
                scope.launch {
                    runCatching { settings.save(settingsState.copy(requireBiometric = enabled)) }
                        .onFailure { notice = "Setting save failed: ${it.message ?: it::class.simpleName}" }
                }
            }
            SettingToggle("Auto-lock after 5 minutes", autoLockEnabled) { enabled ->
                autoLockEnabled = enabled
                autoLock.enabled = enabled
                scope.launch {
                    runCatching { settings.save(settingsState.copy(autoLockVault = enabled)) }
                        .onFailure { notice = "Setting save failed: ${it.message ?: it::class.simpleName}" }
                }
            }
        }
    }

    if (showAddForm) {
        VaultItemForm(
            onDismiss = { showAddForm = false },
            onSave = { label, kind, secret ->
                scope.launch {
                    vault.add(label, kind.toBackend(), secret)
                        .onSuccess {
                            showAddForm = false
                            notice = null
                        }
                        .onFailure { failure ->
                            notice = "Save failed: ${failure.message ?: failure::class.simpleName}"
                        }
                }
            },
        )
    }

    detail?.let { item ->
        AlertDialog(
            onDismissRequest = {
                detail = null
                revealedSecret = null
            },
            title = { Text(item.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kind: ${item.kind.label}")
                    Text("Updated: ${item.updated}")
                    revealedSecret?.let { secret ->
                        Text(secret, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (revealedSecret == null) {
                    Button(onClick = {
                        fun reveal() {
                            vault.reveal(item.id)
                                .onSuccess { revealedSecret = it }
                                .onFailure { failure ->
                                    notice = "Reveal failed: ${failure.message ?: failure::class.simpleName}"
                                }
                        }
                        if (requireBiometric) {
                            biometric.authenticate(
                                title = "Reveal credential",
                                onSuccess = { reveal() },
                                onError = { error -> notice = error },
                            )
                        } else {
                            reveal()
                        }
                    }) { Text("Reveal") }
                } else {
                    TextButton(onClick = {
                        detail = null
                        revealedSecret = null
                    }) { Text("Close") }
                }
            },
            dismissButton = {
                if (revealedSecret == null) {
                    TextButton(onClick = { detail = null }) { Text("Cancel") }
                }
            },
        )
    }

    pendingDelete?.let { target ->
        ConfirmDialog(
            title = "Delete credential",
            message = "Remove ${target.label} from the vault?",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    vault.delete(target.id)
                        .onSuccess {
                            pendingDelete = null
                            detail = null
                            revealedSecret = null
                        }
                        .onFailure { failure ->
                            notice = "Delete failed: ${failure.message ?: failure::class.simpleName}"
                            pendingDelete = null
                        }
                }
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
private fun VaultItemForm(
    onDismiss: () -> Unit,
    onSave: (String, VaultItemKind, String) -> Unit,
) {
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
                    onSave(label.trim(), kind, secret)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
