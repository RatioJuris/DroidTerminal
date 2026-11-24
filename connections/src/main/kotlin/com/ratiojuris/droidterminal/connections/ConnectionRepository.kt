/*
 * Droid Terminal — connections module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.connections

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.connectionStore: DataStore<Preferences> by preferencesDataStore(name = "dt_connections")
private val Context.hostKeyStoreData: DataStore<Preferences> by preferencesDataStore(name = "dt_hostkeys")

/** Persists connection profiles and enforces the trusted-host-key policy. */
class ConnectionRepository(context: Context) {
    private val appContext = context.applicationContext

    val profiles: Flow<List<ConnectionProfile>> = appContext.connectionStore.data.map { prefs ->
        RecordCodec.decodeProfiles(prefs[Keys.PROFILES].orEmpty())
    }

    val trustedHostKeys: Flow<List<TrustedHostKey>> = appContext.hostKeyStoreData.data.map { prefs ->
        RecordCodec.decodeHostKeys(prefs[HostKeys.TRUSTED].orEmpty())
    }

    suspend fun refresh(): List<ConnectionProfile> = profiles.first()

    suspend fun upsert(profile: ConnectionProfile): ConnectionProfile {
        val saved = if (profile.id.isBlank()) {
            profile.copy(id = "c-${UUID.randomUUID()}")
        } else {
            profile
        }
        appContext.connectionStore.edit { prefs ->
            val current = RecordCodec.decodeProfiles(prefs[Keys.PROFILES].orEmpty()).toMutableList()
            val index = current.indexOfFirst { it.id == saved.id }
            if (index >= 0) current[index] = saved else current += saved
            prefs[Keys.PROFILES] = RecordCodec.encodeProfiles(current)
        }
        return saved
    }

    suspend fun delete(id: String) {
        appContext.connectionStore.edit { prefs ->
            val remaining = RecordCodec.decodeProfiles(prefs[Keys.PROFILES].orEmpty())
                .filterNot { it.id == id }
            prefs[Keys.PROFILES] = RecordCodec.encodeProfiles(remaining)
        }
    }

    suspend fun touch(id: String) {
        appContext.connectionStore.edit { prefs ->
            val updated = RecordCodec.decodeProfiles(prefs[Keys.PROFILES].orEmpty()).map { profile ->
                if (profile.id == id) profile.copy(lastUsed = System.currentTimeMillis()) else profile
            }
            prefs[Keys.PROFILES] = RecordCodec.encodeProfiles(updated)
        }
    }

    suspend fun trustedHostKeysNow(): List<TrustedHostKey> = trustedHostKeys.first()

    suspend fun trustedFingerprint(host: String): String? =
        trustedHostKeysNow().firstOrNull { it.host.equals(host, ignoreCase = true) }?.fingerprint

    /** Records an explicit user acceptance. Trust is keyed by host only. */
    suspend fun trustHost(host: String, fingerprint: String, algorithm: String) {
        require(fingerprint.isNotBlank())
        appContext.hostKeyStoreData.edit { prefs ->
            val current = RecordCodec.decodeHostKeys(prefs[HostKeys.TRUSTED].orEmpty())
                .filterNot { it.host.equals(host, ignoreCase = true) }
                .toMutableList()
            current += TrustedHostKey(host, fingerprint, algorithm, System.currentTimeMillis())
            prefs[HostKeys.TRUSTED] = RecordCodec.encodeHostKeys(current)
        }
    }

    suspend fun revokeHost(host: String) {
        appContext.hostKeyStoreData.edit { prefs ->
            val remaining = RecordCodec.decodeHostKeys(prefs[HostKeys.TRUSTED].orEmpty())
                .filterNot { it.host.equals(host, ignoreCase = true) }
            prefs[HostKeys.TRUSTED] = RecordCodec.encodeHostKeys(remaining)
        }
    }

    private object Keys {
        val PROFILES = stringPreferencesKey("profiles")
    }

    private object HostKeys {
        val TRUSTED = stringPreferencesKey("trusted")
    }
}
