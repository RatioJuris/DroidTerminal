/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.di

import android.content.Context
import com.ratiojuris.droidterminal.auth.VaultAutoLock
import com.ratiojuris.droidterminal.connections.ConnectionRepository
import com.ratiojuris.droidterminal.sftp.SftpService
import com.ratiojuris.droidterminal.sessions.SessionManager
import com.ratiojuris.droidterminal.settings.SettingsRepository
import com.ratiojuris.droidterminal.sshengine.SshGateway
import com.ratiojuris.droidterminal.vault.VaultRepository
import java.io.File

/** Application-scoped dependency graph. Backends are shared across destinations. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settings: SettingsRepository = SettingsRepository(appContext)
    val connections: ConnectionRepository = ConnectionRepository(appContext)
    val vault: VaultRepository = VaultRepository(appContext)
    val vaultAutoLock: VaultAutoLock = VaultAutoLock(vault)

    val sshGateway: SshGateway = SshGateway()
    val sessions: SessionManager = SessionManager(sshGateway)

    private val downloadDir: File = File(appContext.filesDir, "downloads").apply { mkdirs() }
    val sftp: SftpService = SftpService(sshGateway, downloadDir)
}
