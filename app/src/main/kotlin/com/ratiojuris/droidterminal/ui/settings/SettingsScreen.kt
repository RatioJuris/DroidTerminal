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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ratiojuris.droidterminal.core.BuildInfo
import com.ratiojuris.droidterminal.settings.AppSettings
import com.ratiojuris.droidterminal.settings.SettingsRepository
import com.ratiojuris.droidterminal.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repository: SettingsRepository) {
    val persisted by repository.settings.collectAsState(AppSettings())
    val scope = rememberCoroutineScope()

    var fontSize by rememberSaveable { mutableFloatStateOf(persisted.fontSize) }
    var scrollback by rememberSaveable { mutableIntStateOf(persisted.scrollback) }
    var showKeypad by rememberSaveable { mutableStateOf(persisted.showKeypad) }
    var haptics by rememberSaveable { mutableStateOf(persisted.haptics) }
    var useSystemKeyboard by rememberSaveable { mutableStateOf(persisted.useSystemKeyboard) }
    var autoLock by rememberSaveable { mutableStateOf(persisted.autoLockVault) }
    var biometric by rememberSaveable { mutableStateOf(persisted.requireBiometric) }
    var hostKeyPolicy by rememberSaveable { mutableIntStateOf(persisted.hostKeyPolicy) }
    var notice by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(persisted) {
        fontSize = persisted.fontSize
        scrollback = persisted.scrollback
        showKeypad = persisted.showKeypad
        haptics = persisted.haptics
        useSystemKeyboard = persisted.useSystemKeyboard
        autoLock = persisted.autoLockVault
        biometric = persisted.requireBiometric
        hostKeyPolicy = persisted.hostKeyPolicy
    }

    fun draft(): AppSettings = AppSettings(
        fontSize = fontSize,
        scrollback = scrollback,
        showKeypad = showKeypad,
        haptics = haptics,
        useSystemKeyboard = useSystemKeyboard,
        autoLockVault = autoLock,
        requireBiometric = biometric,
        hostKeyPolicy = hostKeyPolicy,
    )

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
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = AppSettings.MIN_FONT_SIZE..AppSettings.MAX_FONT_SIZE,
                )
                Text("Scrollback lines: $scrollback", style = MaterialTheme.typography.bodyMedium)
                Row {
                    AppSettings.SUPPORTED_SCROLLBACK.forEach { value ->
                        FilterChip(
                            selected = scrollback == value,
                            onClick = { scrollback = value },
                            label = { Text(value.toString()) },
                        )
                    }
                }
            }
        }

        SectionHeader("Keyboard")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingToggle("Show keyboard by default", showKeypad) { showKeypad = it }
                SettingToggle("Haptic feedback on key press", haptics) { haptics = it }
                SettingToggle("Use system keyboard instead", useSystemKeyboard) { useSystemKeyboard = it }
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
                scope.launch {
                    runCatching { repository.save(draft()) }
                        .onSuccess { notice = "Preferences saved" }
                        .onFailure { notice = "Save failed: ${it.message ?: it::class.simpleName}" }
                }
            }) { Text("Save") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                scope.launch {
                    runCatching { repository.reset() }
                        .onSuccess {
                            val defaults = AppSettings()
                            fontSize = defaults.fontSize
                            scrollback = defaults.scrollback
                            showKeypad = defaults.showKeypad
                            haptics = defaults.haptics
                            useSystemKeyboard = defaults.useSystemKeyboard
                            autoLock = defaults.autoLockVault
                            biometric = defaults.requireBiometric
                            hostKeyPolicy = defaults.hostKeyPolicy
                            notice = "Defaults restored"
                        }
                        .onFailure { notice = "Reset failed: ${it.message ?: it::class.simpleName}" }
                }
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
