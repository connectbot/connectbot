/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import org.connectbot.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val viewModel = remember { SettingsViewModel(prefs) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_memkeys_title),
                    summary = stringResource(R.string.pref_memkeys_summary),
                    checked = uiState.memkeys,
                    onCheckedChange = viewModel::updateMemkeys
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_conn_persist_title),
                    summary = stringResource(R.string.pref_conn_persist_summary),
                    checked = uiState.connPersist,
                    onCheckedChange = viewModel::updateConnPersist
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_wifilock_title),
                    summary = stringResource(R.string.pref_wifilock_summary),
                    checked = uiState.wifilock,
                    onCheckedChange = viewModel::updateWifilock
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_backupkeys_title),
                    summary = stringResource(R.string.pref_backupkeys_summary),
                    checked = uiState.backupkeys,
                    onCheckedChange = viewModel::updateBackupkeys
                )
            }

            item {
                PreferenceCategory(title = stringResource(R.string.pref_emulation_category))
            }

            item {
                TextPreference(
                    title = stringResource(R.string.pref_emulation_title),
                    summary = uiState.emulation,
                    value = uiState.emulation,
                    onValueChange = viewModel::updateEmulation
                )
            }

            item {
                TextPreference(
                    title = stringResource(R.string.pref_scrollback_title),
                    summary = stringResource(R.string.pref_scrollback_summary),
                    value = uiState.scrollback,
                    onValueChange = viewModel::updateScrollback
                )
            }

            item {
                PreferenceCategory(title = stringResource(R.string.pref_ui_category))
            }

            item {
                ListPreference(
                    title = stringResource(R.string.pref_rotation_title),
                    summary = when (uiState.rotation) {
                        "Default" -> stringResource(R.string.list_rotation_default)
                        "Force landscape" -> stringResource(R.string.list_rotation_land)
                        "Force portrait" -> stringResource(R.string.list_rotation_port)
                        "Automatic" -> stringResource(R.string.list_rotation_auto)
                        else -> uiState.rotation
                    },
                    value = uiState.rotation,
                    entries = listOf(
                        stringResource(R.string.list_rotation_default) to "Default",
                        stringResource(R.string.list_rotation_land) to "Force landscape",
                        stringResource(R.string.list_rotation_port) to "Force portrait",
                        stringResource(R.string.list_rotation_auto) to "Automatic"
                    ),
                    onValueChange = viewModel::updateRotation
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_fullscreen_title),
                    summary = stringResource(R.string.pref_fullscreen_summary),
                    checked = uiState.fullscreen,
                    onCheckedChange = viewModel::updateFullscreen
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_titlebarhide_title),
                    summary = stringResource(R.string.pref_titlebarhide_summary),
                    checked = uiState.titlebarhide,
                    onCheckedChange = viewModel::updateTitleBarHide
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_pg_updn_gesture_title),
                    summary = stringResource(R.string.pref_pg_updn_gesture_summary),
                    checked = uiState.pgupdngesture,
                    onCheckedChange = viewModel::updatePgUpDnGesture
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_volumefont_title),
                    summary = stringResource(R.string.pref_volumefont_summary),
                    checked = uiState.volumefont,
                    onCheckedChange = viewModel::updateVolumeFont
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_keepalive_title),
                    summary = stringResource(R.string.pref_keepalive_summary),
                    checked = uiState.keepalive,
                    onCheckedChange = viewModel::updateKeepAlive
                )
            }

            item {
                PreferenceCategory(title = stringResource(R.string.pref_keyboard_category))
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_alwaysvisible_title),
                    summary = "Keep special keys always visible",
                    checked = uiState.alwaysvisible,
                    onCheckedChange = viewModel::updateAlwaysVisible
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_shiftfkeys_title),
                    summary = stringResource(R.string.pref_shiftfkeys_summary),
                    checked = uiState.shiftfkeys,
                    onCheckedChange = viewModel::updateShiftFkeys
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_ctrlfkeys_title),
                    summary = stringResource(R.string.pref_ctrlfkeys_summary),
                    checked = uiState.ctrlfkeys,
                    onCheckedChange = viewModel::updateCtrlFkeys
                )
            }

            item {
                ListPreference(
                    title = stringResource(R.string.pref_stickymodifiers_title),
                    summary = when (uiState.stickymodifiers) {
                        "no" -> stringResource(R.string.no)
                        "alt" -> stringResource(R.string.only_alt)
                        "yes" -> stringResource(R.string.yes)
                        else -> uiState.stickymodifiers
                    },
                    value = uiState.stickymodifiers,
                    entries = listOf(
                        stringResource(R.string.no) to "no",
                        stringResource(R.string.only_alt) to "alt",
                        stringResource(R.string.yes) to "yes"
                    ),
                    onValueChange = viewModel::updateStickyModifiers
                )
            }

            item {
                ListPreference(
                    title = stringResource(R.string.pref_keymode_title),
                    summary = when (uiState.keymode) {
                        "Use right-side keys" -> stringResource(R.string.list_keymode_right)
                        "Use left-side keys" -> stringResource(R.string.list_keymode_left)
                        "none" -> stringResource(R.string.list_keymode_none)
                        else -> uiState.keymode
                    },
                    value = uiState.keymode,
                    entries = listOf(
                        stringResource(R.string.list_keymode_right) to "Use right-side keys",
                        stringResource(R.string.list_keymode_left) to "Use left-side keys",
                        stringResource(R.string.list_keymode_none) to "none"
                    ),
                    onValueChange = viewModel::updateKeyMode
                )
            }

            item {
                ListPreference(
                    title = stringResource(R.string.pref_camera_title),
                    summary = uiState.camera,
                    value = uiState.camera,
                    entries = listOf(
                        stringResource(R.string.list_camera_ctrlaspace) to "Ctrl+A then Space",
                        stringResource(R.string.list_camera_ctrla) to "Ctrl+A",
                        stringResource(R.string.list_camera_esc) to "Esc",
                        stringResource(R.string.list_camera_esc_a) to "Esc+A",
                        stringResource(R.string.list_camera_none) to "None"
                    ),
                    onValueChange = viewModel::updateCamera
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_bumpyarrows_title),
                    summary = stringResource(R.string.pref_bumpyarrows_summary),
                    checked = uiState.bumpyarrows,
                    onCheckedChange = viewModel::updateBumpyArrows
                )
            }

            item {
                PreferenceCategory(title = stringResource(R.string.pref_bell_category))
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_bell_title),
                    summary = stringResource(R.string.pref_bell_summary),
                    checked = uiState.bell,
                    onCheckedChange = viewModel::updateBell
                )
            }

            item {
                SliderPreference(
                    title = stringResource(R.string.pref_bell_volume_title),
                    value = uiState.bellVolume,
                    onValueChange = viewModel::updateBellVolume
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_bell_vibrate_title),
                    summary = stringResource(R.string.pref_bell_vibrate_summary),
                    checked = uiState.bellVibrate,
                    onCheckedChange = viewModel::updateBellVibrate
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_bell_notification_title),
                    summary = stringResource(R.string.pref_bell_notification_summary),
                    checked = uiState.bellNotification,
                    onCheckedChange = viewModel::updateBellNotification
                )
            }
        }
    }
}

@Composable
private fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = modifier.clickable { onCheckedChange(!checked) }
    )
    HorizontalDivider()
}

@Composable
private fun TextPreference(
    title: String,
    summary: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = modifier.clickable { showDialog = true }
    )
    HorizontalDivider()

    if (showDialog) {
        TextPreferenceDialog(
            title = title,
            value = value,
            onDismiss = { showDialog = false },
            onConfirm = { newValue ->
                onValueChange(newValue)
                showDialog = false
            }
        )
    }
}

@Composable
private fun TextPreferenceDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(textValue) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_neg))
            }
        }
    )
}

@Composable
private fun ListPreference(
    title: String,
    summary: String,
    value: String,
    entries: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = modifier.clickable { showDialog = true }
    )
    HorizontalDivider()

    if (showDialog) {
        ListPreferenceDialog(
            title = title,
            value = value,
            entries = entries,
            onDismiss = { showDialog = false },
            onConfirm = { newValue ->
                onValueChange(newValue)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ListPreferenceDialog(
    title: String,
    value: String,
    entries: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                entries.forEach { (label, entryValue) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        modifier = Modifier.clickable { onConfirm(entryValue) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_neg))
            }
        }
    )
}

@Composable
private fun SliderPreference(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}
