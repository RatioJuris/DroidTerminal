/*
 * Droid Terminal — settings module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "dt_settings")

/** DataStore-backed settings repository. All writes replace the full snapshot atomically. */
class SettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            fontSize = prefs[Keys.FONT_SIZE] ?: AppSettings.DEFAULT_FONT_SIZE,
            scrollback = prefs[Keys.SCROLLBACK] ?: AppSettings.DEFAULT_SCROLLBACK,
            showKeypad = prefs[Keys.SHOW_KEYPAD] ?: true,
            haptics = prefs[Keys.HAPTICS] ?: true,
            longPressModifier = prefs[Keys.LONG_PRESS_MODIFIER] ?: false,
            autoLockVault = prefs[Keys.AUTO_LOCK_VAULT] ?: true,
            requireBiometric = prefs[Keys.REQUIRE_BIOMETRIC] ?: false,
            hostKeyPolicy = prefs[Keys.HOST_KEY_POLICY] ?: AppSettings.HOST_KEY_WARN,
        )
    }

    suspend fun save(value: AppSettings) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.FONT_SIZE] = value.fontSize
            prefs[Keys.SCROLLBACK] = value.scrollback
            prefs[Keys.SHOW_KEYPAD] = value.showKeypad
            prefs[Keys.HAPTICS] = value.haptics
            prefs[Keys.LONG_PRESS_MODIFIER] = value.longPressModifier
            prefs[Keys.AUTO_LOCK_VAULT] = value.autoLockVault
            prefs[Keys.REQUIRE_BIOMETRIC] = value.requireBiometric
            prefs[Keys.HOST_KEY_POLICY] = value.hostKeyPolicy
        }
    }

    suspend fun reset() = save(AppSettings())

    private object Keys {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val SCROLLBACK = intPreferencesKey("scrollback")
        val SHOW_KEYPAD = booleanPreferencesKey("show_keypad")
        val HAPTICS = booleanPreferencesKey("haptics")
        val LONG_PRESS_MODIFIER = booleanPreferencesKey("long_press_modifier")
        val AUTO_LOCK_VAULT = booleanPreferencesKey("auto_lock_vault")
        val REQUIRE_BIOMETRIC = booleanPreferencesKey("require_biometric")
        val HOST_KEY_POLICY = intPreferencesKey("host_key_policy")
    }
}
