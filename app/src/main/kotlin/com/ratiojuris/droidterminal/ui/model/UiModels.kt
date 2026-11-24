/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.model

import com.ratiojuris.droidterminal.connections.AuthType as BackendAuthType
import com.ratiojuris.droidterminal.connections.ConnectionProfile as BackendConnectionProfile
import com.ratiojuris.droidterminal.sftp.RemoteEntry as BackendRemoteEntry
import com.ratiojuris.droidterminal.sftp.TransferProgress as BackendTransferProgress
import com.ratiojuris.droidterminal.sessions.SessionTabInfo as BackendSessionTabInfo
import com.ratiojuris.droidterminal.terminalengine.TerminalSnapshot
import com.ratiojuris.droidterminal.vault.VaultItem as BackendVaultItem
import com.ratiojuris.droidterminal.vault.VaultItemKind as BackendVaultItemKind

/** UI-level models and adapters over the Phase 2 backend modules. */

enum class AuthType(val label: String) {
    PASSWORD("Password"),
    SSH_KEY("SSH key"),
    AGENT("Agent"),
}

data class ConnectionProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authType: AuthType,
    val group: String,
    val lastUsed: Long,
)

enum class ConnectionSort(val label: String) { NAME("Name"), HOST("Host"), RECENT("Recent") }

enum class VaultItemKind(val label: String) {
    PASSWORD("Password"),
    SSH_KEY("SSH key"),
    CERTIFICATE("Certificate"),
    NOTE("Secure note"),
}

data class VaultItem(
    val id: String,
    val label: String,
    val kind: VaultItemKind,
    val updated: String,
    val secret: String = "",
)

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: String,
    val permissions: String,
    val modified: String,
)

enum class TransferDirection(val label: String) { UPLOAD("Upload"), DOWNLOAD("Download") }

enum class TransferStatus(val label: String) {
    QUEUED("Queued"),
    ACTIVE("In progress"),
    DONE("Complete"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
}

data class TransferItem(
    val id: String,
    val name: String,
    val direction: TransferDirection,
    val progress: Float,
    val status: TransferStatus,
)

data class SessionTab(
    val id: String,
    val title: String,
    val host: String,
    val connected: Boolean,
)

enum class TerminalLineKind { OUTPUT, COMMAND, ERROR, SUCCESS, PROMPT }

data class TerminalLine(val text: String, val kind: TerminalLineKind)

fun BackendConnectionProfile.toUi(): ConnectionProfile = ConnectionProfile(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    authType = AuthType.valueOf(authType.name),
    group = group,
    lastUsed = lastUsed,
)

fun ConnectionProfile.toBackend(): BackendConnectionProfile = BackendConnectionProfile(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    authType = BackendAuthType.valueOf(authType.name),
    group = group,
    lastUsed = lastUsed,
)

fun BackendVaultItem.toUi(): VaultItem = VaultItem(
    id = id,
    label = label,
    kind = VaultItemKind.valueOf(kind.name),
    updated = updated,
)

fun VaultItemKind.toBackend(): BackendVaultItemKind = BackendVaultItemKind.valueOf(name)

fun BackendRemoteEntry.toUi(): RemoteFile = RemoteFile(
    name = name,
    path = path,
    isDirectory = isDirectory,
    size = if (isDirectory) "-" else formatSize(sizeBytes),
    permissions = permissions,
    modified = modified,
)

fun RemoteFile.toBackend(): BackendRemoteEntry = BackendRemoteEntry(
    name = name,
    path = path,
    isDirectory = isDirectory,
    sizeBytes = size.toBytesOrZero(),
    permissions = permissions,
    modified = modified,
)

fun BackendTransferProgress.toUi(): TransferItem = TransferItem(
    id = id,
    name = name,
    direction = TransferDirection.DOWNLOAD,
    progress = fraction,
    status = TransferStatus.valueOf(state.name),
)

fun BackendSessionTabInfo.toUi(): SessionTab = SessionTab(
    id = id,
    title = title,
    host = host,
    connected = connected,
)

fun TerminalSnapshot.toUiLines(): List<TerminalLine> =
    renderHistoryText().lines().map { TerminalLine(it, TerminalLineKind.OUTPUT) }

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

private fun String.toBytesOrZero(): Long = when {
    endsWith(" MB") -> removeSuffix(" MB").toLongOrNull()?.times(1024L * 1024L) ?: 0L
    endsWith(" KB") -> removeSuffix(" KB").toLongOrNull()?.times(1024L) ?: 0L
    endsWith(" B") -> removeSuffix(" B").toLongOrNull() ?: 0L
    else -> toLongOrNull() ?: 0L
}

/** Basic validation used by the Phase 1 forms. */
object UiValidation {
    fun validateConnection(name: String, host: String, port: String, username: String): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (name.isBlank()) errors["name"] = "Profile name is required"
        if (host.isBlank()) errors["host"] = "Host is required"
        val portNumber = port.toIntOrNull()
        if (portNumber == null || portNumber !in 1..65535) errors["port"] = "Enter a port between 1 and 65535"
        if (username.isBlank()) errors["username"] = "Username is required"
        return errors
    }

    fun validatePassphrase(passphrase: String): String? = when {
        passphrase.isBlank() -> "Passphrase is required"
        passphrase.length < 8 -> "Passphrase must be at least 8 characters"
        else -> null
    }

    fun validateVaultItem(label: String, secret: String): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (label.isBlank()) errors["label"] = "Label is required"
        if (secret.isBlank()) errors["secret"] = "Value is required"
        return errors
    }
}

fun filterAndSortConnections(
    items: List<ConnectionProfile>,
    query: String,
    sort: ConnectionSort,
): List<ConnectionProfile> {
    val needle = query.trim()
    val filtered = if (needle.isEmpty()) {
        items
    } else {
        items.filter { profile ->
            listOf(profile.name, profile.host, profile.username, profile.group)
                .any { it.contains(needle, ignoreCase = true) }
        }
    }
    return when (sort) {
        ConnectionSort.NAME -> filtered.sortedBy { it.name.lowercase() }
        ConnectionSort.HOST -> filtered.sortedBy { it.host.lowercase() }
        ConnectionSort.RECENT -> filtered.sortedByDescending { it.lastUsed }
    }
}
