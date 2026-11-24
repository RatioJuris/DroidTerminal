/*
 * Droid Terminal — sessions module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.sessions

/** Lifecycle of one interactive terminal session. */
enum class SessionState { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

/** Metadata shown in the terminal tab strip. */
data class SessionTabInfo(
    val id: String,
    val title: String,
    val host: String,
    val state: SessionState,
) {
    val connected: Boolean get() = state == SessionState.CONNECTED
}
