/*
 * Droid Terminal — connection manager module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ratiojuris.droidterminal.connections"
    compileSdk = libs.versions.compileSdk.get().toInt()

    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    lint {
        abortOnError = true
    }
}

dependencies {
    testImplementation(libs.junit)
}
