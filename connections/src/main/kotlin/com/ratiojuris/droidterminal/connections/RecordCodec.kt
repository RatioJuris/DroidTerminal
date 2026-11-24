/*
 * Droid Terminal — connections module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.connections

import java.util.Base64

/**
 * Dependency-free encoding for DataStore string values.
 *
 * Every field is Base64 encoded independently so separators and line breaks in
 * user input cannot corrupt a record. A malformed record is discarded rather
 * than allowed to break loading of all other records.
 */
internal object RecordCodec {
    private const val FIELD = '\u001F'
    private const val RECORD = '\n'

    fun encodeProfiles(profiles: List<ConnectionProfile>): String = profiles.joinToString(RECORD.toString()) { profile ->
        fields(
            profile.id,
            profile.name,
            profile.host,
            profile.port.toString(),
            profile.username,
            profile.authType.name,
            profile.group,
            profile.lastUsed.toString(),
        )
    }

    fun decodeProfiles(raw: String): List<ConnectionProfile> = records(raw).mapNotNull { parts ->
        runCatching {
            ConnectionProfile(
                id = parts[0],
                name = parts[1],
                host = parts[2],
                port = parts[3].toInt(),
                username = parts[4],
                authType = AuthType.valueOf(parts[5]),
                group = parts[6],
                lastUsed = parts[7].toLong(),
            )
        }.getOrNull()
    }

    fun encodeHostKeys(keys: List<TrustedHostKey>): String = keys.joinToString(RECORD.toString()) { key ->
        fields(key.host, key.fingerprint, key.algorithm, key.acceptedAt.toString())
    }

    fun decodeHostKeys(raw: String): List<TrustedHostKey> = records(raw).mapNotNull { parts ->
        runCatching {
            TrustedHostKey(
                host = parts[0],
                fingerprint = parts[1],
                algorithm = parts[2],
                acceptedAt = parts[3].toLong(),
            )
        }.getOrNull()
    }

    private fun fields(vararg values: String): String =
        values.joinToString(FIELD.toString()) { encode(it) }

    private fun records(raw: String): List<List<String>> = raw
        .split(RECORD)
        .filter { it.isNotBlank() }
        .map { line -> line.split(FIELD).map(::decode) }

    private fun encode(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
