/*
 * Droid Terminal — a secure Android SSH client and terminal emulator.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DroidTerminal"

include(
    ":app",
    ":core",
    ":terminal-engine",
    ":keypad",
    ":ssh-engine",
    ":vault",
    ":connections",
    ":sftp",
    ":sessions",
    ":settings",
    ":security",
    ":test-support",
)
