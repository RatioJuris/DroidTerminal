/*
 * Droid Terminal — SSH engine module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ratiojuris.droidterminal.sshengine"
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
    implementation(libs.jsch)
    implementation(project(":security"))
    implementation(project(":terminal-engine"))
    testImplementation(libs.junit)
}
