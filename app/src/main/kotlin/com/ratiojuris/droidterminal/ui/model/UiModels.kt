/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.model

/** UI-level models for the Phase 1 screens. Backing services arrive in Phase 2. */

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
    val secret: String,
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

/** Sample content for the Phase 1 screens. Every consumer is marked with TODO(phase2). */
// TODO(phase2): replace MockData with repositories from connections, vault, sftp and sessions.
object MockData {
    val connections = listOf(
        ConnectionProfile("c1", "Production web", "web.example.net", 22, "deploy", AuthType.SSH_KEY, "Servers", 50),
        ConnectionProfile("c2", "Staging database", "db.staging.example.net", 2222, "ops", AuthType.PASSWORD, "Servers", 40),
        ConnectionProfile("c3", "Raspberry Pi", "192.168.1.42", 22, "pi", AuthType.PASSWORD, "Home", 30),
        ConnectionProfile("c4", "Jump host", "bastion.example.net", 22, "admin", AuthType.AGENT, "Servers", 20),
    )

    val vaultItems = listOf(
        VaultItem("v1", "Production web key", VaultItemKind.SSH_KEY, "2026-11-20", "-----BEGIN OPENSSH PRIVATE KEY-----"),
        VaultItem("v2", "Staging database", VaultItemKind.PASSWORD, "2026-11-18", "k7f2!example"),
        VaultItem("v3", "Router admin", VaultItemKind.PASSWORD, "2026-11-02", "admin-pass"),
        VaultItem("v4", "Code signing", VaultItemKind.CERTIFICATE, "2026-10-27", "PKCS#12 certificate"),
        VaultItem("v5", "Recovery codes", VaultItemKind.NOTE, "2026-10-11", "8 single-use codes"),
    )

    val directory = listOf(
        RemoteFile("bin", "/home/deploy/bin", true, "-", "drwxr-xr-x", "2026-11-20 09:12"),
        RemoteFile("logs", "/home/deploy/logs", true, "-", "drwxr-xr-x", "2026-11-22 14:03"),
        RemoteFile("app.jar", "/home/deploy/app.jar", false, "24.8 MB", "-rw-r--r--", "2026-11-22 11:47"),
        RemoteFile("deploy.sh", "/home/deploy/deploy.sh", false, "2.1 KB", "-rwxr-xr-x", "2026-11-19 16:30"),
        RemoteFile("README.md", "/home/deploy/README.md", false, "812 B", "-rw-r--r--", "2026-11-01 08:15"),
    )

    val transfers = listOf(
        TransferItem("t1", "app.jar", TransferDirection.UPLOAD, 0.72f, TransferStatus.ACTIVE),
        TransferItem("t2", "access-2026-11-23.log", TransferDirection.DOWNLOAD, 1f, TransferStatus.DONE),
        TransferItem("t3", "backup.tar.gz", TransferDirection.DOWNLOAD, 0.35f, TransferStatus.FAILED),
    )

    val sessions = listOf(
        SessionTab("s1", "Production web", "web.example.net", true),
        SessionTab("s2", "Raspberry Pi", "192.168.1.42", true),
    )

    val terminalLines = listOf(
        TerminalLine("Last login: Mon Nov 23 09:14:02 2026 from 10.0.0.8", TerminalLineKind.OUTPUT),
        TerminalLine("deploy@web:~$ uptime", TerminalLineKind.COMMAND),
        TerminalLine(" 09:14:03 up 42 days,  6:12,  2 users,  load average: 0.08, 0.12, 0.09", TerminalLineKind.OUTPUT),
        TerminalLine("deploy@web:~$ systemctl status app", TerminalLineKind.COMMAND),
        TerminalLine("● app.service - Application server", TerminalLineKind.SUCCESS),
        TerminalLine("   Loaded: loaded (/etc/systemd/system/app.service; enabled)", TerminalLineKind.OUTPUT),
        TerminalLine("   Active: active (running) since Mon 2026-11-23 09:00:01 UTC; 14min ago", TerminalLineKind.SUCCESS),
        TerminalLine("deploy@web:~$", TerminalLineKind.PROMPT),
    )
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
