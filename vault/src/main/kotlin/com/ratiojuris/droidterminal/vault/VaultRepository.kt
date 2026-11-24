/*
 * Droid Terminal — vault module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.vault

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher

private val Context.vaultStore: DataStore<Preferences> by preferencesDataStore(name = "dt_vault")

/**
 * Keystore-backed secret store.
 *
 * The passphrase protects a verifier only; item secrets are encrypted with
 * individual Keystore keys. Plaintext is held exclusively in [plaintext] while
 * unlocked and is wiped by [lock].
 */
class VaultRepository(context: Context) {
    private val appContext = context.applicationContext
    private val plaintext = mutableMapOf<String, String>()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    val isEnrolled: Flow<Boolean> = appContext.vaultStore.data.map { prefs ->
        prefs[Keys.VERIFIER] != null
    }

    suspend fun unlock(passphrase: String): Result<Unit> = runCatching {
        require(passphrase.length >= MIN_PASSPHRASE_LENGTH) { "passphrase too short" }
        val prefs = appContext.vaultStore.data.first()
        when (val salt = prefs[Keys.SALT]) {
            null -> {
                val newSalt = VaultCrypto.newSalt()
                val derived = VaultCrypto.deriveKey(passphrase.toCharArray(), newSalt)
                val verifier = VaultCrypto.encrypt(VERIFIER_TEXT.toByteArray(Charsets.UTF_8), derived)
                appContext.vaultStore.edit { store ->
                    store[Keys.SALT] = VaultCrypto.encode(newSalt)
                    store[Keys.VERIFIER] = verifier
                }
                derived
            }
            else -> {
                val verifier = prefs[Keys.VERIFIER] ?: error("vault verifier is missing")
                VaultCrypto.deriveKey(
                    passphrase.toCharArray(),
                    VaultCrypto.decode(salt),
                ).also { derived ->
                    val plain = VaultCrypto.decrypt(verifier, derived)
                    if (!plain.contentEquals(VERIFIER_TEXT.toByteArray(Charsets.UTF_8))) {
                        throw InvalidPassphraseException()
                    }
                }
            }
        }

        val stored = decodeItems(prefs[Keys.ITEMS].orEmpty())
        plaintext.clear()
        stored.forEach { item ->
            val secretKey = VaultCrypto.keystoreKey(item.keyAlias) ?: return@forEach
            runCatching {
                plaintext[item.id] = String(VaultCrypto.decrypt(item.ciphertext, secretKey), Charsets.UTF_8)
            }
        }
        _items.value = stored.map { it.metadata() }
        _isUnlocked.value = true
    }

    /** Clears all in-memory plaintext and metadata. */
    fun lock() {
        plaintext.keys.toList().forEach { plaintext.remove(it) }
        _items.value = emptyList()
        _isUnlocked.value = false
    }

    suspend fun add(label: String, kind: VaultItemKind, secret: String): Result<VaultItem> = runCatching {
        ensureUnlocked()
        require(label.isNotBlank()) { "label must not be blank" }
        require(secret.isNotEmpty()) { "secret must not be empty" }
        val id = "v-${UUID.randomUUID()}"
        val alias = "$ITEM_KEY_PREFIX$id"
        VaultCrypto.generateKeystoreKey(alias, requireUserAuth = false)
        val key = VaultCrypto.keystoreKey(alias) ?: error("new Keystore key is not available")
        val stored = StoredVaultItem(
            id = id,
            label = label.trim(),
            kind = kind,
            updated = LocalDate.now().toString(),
            keyAlias = alias,
            ciphertext = VaultCrypto.encrypt(secret.toByteArray(Charsets.UTF_8), key),
        )
        appContext.vaultStore.edit { prefs ->
            prefs[Keys.ITEMS] = encodeItems(decodeItems(prefs[Keys.ITEMS].orEmpty()) + stored)
        }
        plaintext[id] = secret
        _items.value = decodeItems(appContext.vaultStore.data.first()[Keys.ITEMS].orEmpty()).map { it.metadata() }
        stored.metadata()
    }

    /** Returns a secret only while unlocked. No secret is stored in Compose state. */
    fun reveal(id: String): Result<String> = runCatching {
        ensureUnlocked()
        plaintext[id] ?: error("item is not available in the unlocked vault")
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        ensureUnlocked()
        val stored = decodeItems(appContext.vaultStore.data.first()[Keys.ITEMS].orEmpty())
        val target = stored.firstOrNull { it.id == id } ?: error("item does not exist")
        appContext.vaultStore.edit { prefs ->
            prefs[Keys.ITEMS] = encodeItems(stored.filterNot { it.id == id })
        }
        VaultCrypto.deleteKeystoreKey(target.keyAlias)
        plaintext.remove(id)
        _items.value = stored.filterNot { it.id == id }.map { it.metadata() }
    }

    /** Creates (if needed) and initialises the user-authentication-gated cipher. */
    fun biometricCipher(): Result<Cipher> = runCatching {
        if (!VaultCrypto.hasKeystoreKey(BIOMETRIC_ALIAS)) {
            VaultCrypto.generateKeystoreKey(BIOMETRIC_ALIAS, requireUserAuth = true)
        }
        VaultCrypto.biometricCipher(BIOMETRIC_ALIAS)
    }

    /** Executes an authenticated crypto operation; throws unless biometric auth authorised the key. */
    fun confirmBiometric(cipher: Cipher): Boolean {
        cipher.doFinal(byteArrayOf(BIOMETRIC_CHALLENGE))
        return true
    }

    private fun ensureUnlocked() {
        if (!_isUnlocked.value) throw VaultLockedException()
    }

    private fun StoredVaultItem.metadata() = VaultItem(id, label, kind, updated)

    private fun encodeItems(items: List<StoredVaultItem>): String = items.joinToString("\n") { item ->
        listOf(item.id, item.label, item.kind.name, item.updated, item.keyAlias, item.ciphertext)
            .joinToString("\u001F") { encodeField(it) }
    }

    private fun decodeItems(raw: String): List<StoredVaultItem> = raw.split('\n').filter { it.isNotBlank() }
        .mapNotNull { line ->
            runCatching {
                val fields = line.split('\u001F').map(::decodeField)
                StoredVaultItem(
                    id = fields[0],
                    label = fields[1],
                    kind = VaultItemKind.valueOf(fields[2]),
                    updated = fields[3],
                    keyAlias = fields[4],
                    ciphertext = fields[5],
                )
            }.getOrNull()
        }

    private fun encodeField(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeField(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

    private object Keys {
        val SALT = stringPreferencesKey("salt")
        val VERIFIER = stringPreferencesKey("verifier")
        val ITEMS = stringPreferencesKey("items")
    }

    private companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
        const val VERIFIER_TEXT = "DROID_TERMINAL_VAULT_V1"
        const val ITEM_KEY_PREFIX = "dt_vault_item_"
        const val BIOMETRIC_ALIAS = "dt_vault_biometric"
        const val BIOMETRIC_CHALLENGE: Byte = 0x01
    }
}
