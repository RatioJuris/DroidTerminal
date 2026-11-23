/*
 * Droid Terminal — application core module.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildInfoTest {
    @Test
    fun `product identity is stable`() {
        assertEquals("Droid Terminal", BuildInfo.APP_NAME)
        assertEquals("PrajnaBytes International Private Limited", BuildInfo.COPYRIGHT_HOLDER)
        assertEquals("Isrg Rajan Gonardiya", BuildInfo.FOUNDER)
    }

    @Test
    fun `project is licensed under GPL v3 or later`() {
        assertEquals("GPL-3.0-or-later", BuildInfo.SPDX_LICENSE_ID)
    }

    @Test
    fun `build stage is disclosed honestly`() {
        assertTrue(BuildInfo.BUILD_STAGE.startsWith("Phase 1"))
    }
}
