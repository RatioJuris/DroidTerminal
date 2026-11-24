/*
 * Droid Terminal — vault module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.vault

/** Categories of secret stored in the vault. */
enum class VaultItemKind { PASSWORD, SSH_KEY, CERTIFICATE, NOTE }

/** Encrypted item as persisted in DataStore. Contains no plaintext. */
data class StoredVaultItem(
    val id: String,
    val label: String,
    val kind: VaultItemKind,
    val updated: String,
    val keyAlias: String,
    val ciphertext: String,
)

/** Item metadata safe to display without revealing the secret. */
data class VaultItem(
    val id: String,
    val label: String,
    val kind: VaultItemKind,
    val updated: String,
)

/** Thrown when a passphrase does not match the enrolled verifier. */
class InvalidPassphraseException : SecurityException("vault passphrase is incorrect")

/** Thrown when an operation requires the vault to be unlocked first. */
class VaultLockedException : SecurityException("vault is locked")
