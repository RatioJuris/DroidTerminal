/*
 * Droid Terminal — application shell.
 * Copyright (C) 2026 PrajnaBytes International Private Limited
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.ratiojuris.droidterminal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.core.BuildInfo
import com.ratiojuris.droidterminal.ui.components.SectionHeader

@Composable
fun SettingsScreen() {
    var fontSize by rememberSaveable { mutableFloatStateOf(14f) }
    var scrollback by rememberSaveable { mutableIntStateOf(1000) }
    var showKeypad by rememberSaveable { mutableStateOf(true) }
    var haptics by rememberSaveable { mutableStateOf(true) }
    var longPressModifier by rememberSaveable { mutableStateOf(false) }
    var autoLock by rememberSaveable { mutableStateOf(true) }
    var biometric by rememberSaveable { mutableStateOf(false) }
    var hostKeyPolicy by rememberSaveable { mutableIntStateOf(0) }
    var notice by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionHeader("Terminal")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Font size: ${fontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
                // TODO(phase2): persist terminal appearance preferences.
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 10f..24f)
                Text("Scrollback lines: $scrollback", style = MaterialTheme.typography.bodyMedium)
                Row {
                    listOf(200, 1000, 5000).forEach { value ->
                        FilterChip(
                            selected = scrollback == value,
                            onClick = { scrollback = value },
                            label = { Text(value.toString()) },
                        )
                    }
                }
            }
        }

        SectionHeader("Shortcut keypad")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingToggle("Show keypad by default", showKeypad) { showKeypad = it }
                SettingToggle("Haptic feedback on key press", haptics) { haptics = it }
                SettingToggle("Long-press sets locked modifier", longPressModifier) { longPressModifier = it }
            }
        }

        SectionHeader("Security")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingToggle("Auto-lock vault when backgrounded", autoLock) { autoLock = it }
                SettingToggle("Require biometric unlock", biometric) { biometric = it }
                Text("Host key policy", style = MaterialTheme.typography.bodyMedium)
                Row {
                    listOf("Warn", "Strict", "Trust on first use").forEachIndexed { index, label ->
                        FilterChip(
                            selected = hostKeyPolicy == index,
                            onClick = { hostKeyPolicy = index },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        SectionHeader("Actions")
        Row {
            Button(onClick = {
                // TODO(phase2): write preferences to DataStore.
                notice = "Preferences saved (in memory for Phase 1)"
            }) { Text("Save") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                fontSize = 14f
                scrollback = 1000
                showKeypad = true
                haptics = true
                longPressModifier = false
                autoLock = true
                biometric = false
                hostKeyPolicy = 0
                notice = "Defaults restored"
            }) { Text("Reset") }
        }
        if (notice.isNotEmpty()) Text(notice, style = MaterialTheme.typography.bodySmall)

        SectionHeader("About")
        Text("${BuildInfo.APP_NAME} ${BuildInfo.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
        Text("Licence: ${BuildInfo.SPDX_LICENSE_ID}", style = MaterialTheme.typography.bodyMedium)
        Text(BuildInfo.BUILD_STAGE, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
