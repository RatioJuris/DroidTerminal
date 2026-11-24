/*
 * Droid Terminal — SFTP module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.sftp

/** One directory entry read from a remote filesystem. */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val permissions: String,
    val modified: String,
)

/** Lifecycle of a queued transfer. */
enum class TransferState { QUEUED, ACTIVE, DONE, FAILED, CANCELLED }

/** Progress snapshot for one transfer. */
data class TransferProgress(
    val id: String,
    val name: String,
    val transferred: Long,
    val total: Long,
    val state: TransferState,
    val error: String? = null,
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (transferred.toFloat() / total).coerceIn(0f, 1f)
}

/** Append-only record of a mutating remote operation. */
data class AuditEntry(
    val action: String,
    val path: String,
    val outcome: String,
    val at: Long = System.currentTimeMillis(),
)
