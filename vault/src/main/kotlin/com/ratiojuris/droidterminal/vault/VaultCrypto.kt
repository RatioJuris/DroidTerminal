/*
 * Droid Terminal — vault module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Keystore and AES-GCM primitives used by the vault. No Android Context required. */
internal object VaultCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PBKDF2 = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val random = SecureRandom()

    fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun newSalt(): ByteArray = ByteArray(16).also(random::nextBytes)

    fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2)
        val derived = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(derived, "AES")
    }

    fun encrypt(plain: ByteArray, key: javax.crypto.SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Android Keystore generates the GCM IV itself and rejects a caller-supplied one.
        // Software keys behave the same way, so read the generated IV back for both.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        return encode(iv + cipher.doFinal(plain))
    }

    fun decrypt(payload: String, key: javax.crypto.SecretKey): ByteArray {
        val raw = decode(payload)
        require(raw.size > IV_BYTES) { "ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, raw.copyOfRange(0, IV_BYTES)))
        return cipher.doFinal(raw.copyOfRange(IV_BYTES, raw.size))
    }

    fun generateKeystoreKey(alias: String, requireUserAuth: Boolean) {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
        if (requireUserAuth) {
            builder.setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(builder.build())
        generator.generateKey()
    }

    fun keystoreKey(alias: String): javax.crypto.SecretKey? =
        keyStore().getKey(alias, null) as? javax.crypto.SecretKey

    fun hasKeystoreKey(alias: String): Boolean = keyStore().containsAlias(alias)

    fun deleteKeystoreKey(alias: String) {
        val store = keyStore()
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    fun biometricCipher(alias: String): Cipher {
        val key = keystoreKey(alias) ?: error("biometric key is not enrolled")
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
