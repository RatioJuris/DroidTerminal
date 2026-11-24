/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ratiojuris.droidterminal.vault.VaultRepository

/**
 * Gate for revealing a vault secret.
 *
 * The prompt is bound to a user-authentication-gated Keystore cipher. The vault
 * performs a real crypto operation with that cipher before reporting success,
 * so a callback alone cannot unlock the secret.
 */
class BiometricAuth(
    private val activity: FragmentActivity,
    private val vault: VaultRepository,
) {
    fun authenticate(title: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val availability = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Biometric authentication is not available on this device")
            return
        }

        val cipherResult = vault.biometricCipher()
        if (cipherResult.isFailure) {
            onError("Vault biometric key is unavailable: ${cipherResult.exceptionOrNull()?.message}")
            return
        }
        val cipher = cipherResult.getOrThrow()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authorised = runCatching {
                    val authorisedCipher = result.cryptoObject?.cipher
                        ?: error("biometric result carries no cipher")
                    vault.confirmBiometric(authorisedCipher)
                }
                if (authorised.isSuccess) onSuccess() else onError(authorised.exceptionOrNull()?.message ?: "Authorisation failed")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Confirm your identity to reveal this secret")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
