/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.auth

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ratiojuris.droidterminal.vault.VaultRepository

/** Locks the vault a fixed interval after the app leaves the foreground. */
class VaultAutoLock(
    private val vault: VaultRepository,
    private val delayMs: Long = DEFAULT_DELAY_MS,
) : DefaultLifecycleObserver {
    @Volatile
    var enabled: Boolean = true

    private val handler = Handler(Looper.getMainLooper())
    private val lockNow = Runnable {
        if (enabled) vault.lock()
    }

    override fun onPause(owner: LifecycleOwner) {
        handler.removeCallbacks(lockNow)
        if (enabled) handler.postDelayed(lockNow, delayMs)
    }

    override fun onResume(owner: LifecycleOwner) {
        handler.removeCallbacks(lockNow)
    }

    private companion object {
        const val DEFAULT_DELAY_MS = 5L * 60 * 1000
    }
}
