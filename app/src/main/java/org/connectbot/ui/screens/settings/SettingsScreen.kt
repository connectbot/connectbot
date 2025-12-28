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

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.BuildConfig
import org.connectbot.R
import org.connectbot.ui.ObservePermissionOnResume
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.common.getLocalizedFontDisplayName
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.LocalFontProvider
import org.connectbot.util.NotificationPermissionHelper
import org.connectbot.util.TerminalFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Permission launcher for notification permission
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Check the actual permission status instead of relying on the launcher result.
        // If user went to settings and granted permission, the result will be false but
        // the actual permission may be granted.
        val actuallyGranted = NotificationPermissionHelper.isNotificationPermissionGranted(context)
        viewModel.onNotificationPermissionResult(actuallyGranted)
    }

    // Listen for permission request events
    LaunchedEffect(Unit) {
        viewModel.requestNotificationPermission.collect {
            if (NotificationPermissionHelper.isNotificationPermissionGranted(context)) {
                // Permission already granted
                viewModel.onNotificationPermissionResult(true)
            } else {
                // Request permission
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // No permission needed on older versions
                    viewModel.onNotificationPermissionResult(true)
                }
            }
        }
    }

    // State for showing permission denied dialog
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    // Listen for permission denied dialog events
    LaunchedEffect(Unit) {
        viewModel.showPermissionDeniedDialog.collect {
            showPermissionDeniedDialog = true
        }
    }

    // Re-check permission status when screen resumes (e.g., user grants/revokes in Settings)
    ObservePermissionOnResume { isGranted ->
        viewModel.onNotificationPermissionResult(isGranted)
    }

    // Show permission denied dialog if needed
    if (showPermissionDeniedDialog) {
        NotificationPermissionDeniedDialog(
            onDismiss = {
                showPermissionDeniedDialog = false
            },
            onOpenSettings = {
                showPermissionDeniedDialog = false
                // Open app settings so user can grant notification permission
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }

    SettingsScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onMemkeysChange = viewModel::updateMemkeys,
        onConnPersistChange = viewModel::updateConnPersist,
        onWifilockChange = viewModel::updateWifilock,
        onBackupkeysChange = viewModel::updateBackupkeys,
        onScrollbackChange = viewModel::updateScrollback,
        onAddCustomTerminalType = viewModel::addCustomTerminalType,
        onRemoveCustomTerminalType = viewModel::removeCustomTerminalType,
        onFontFamilyChange = viewModel::updateFontFamily,
        onAddCustomFont = viewModel::addCustomFont,
        onRemoveCustomFont = viewModel::removeCustomFont,
        onClearFontError = viewModel::clearFontValidationError,
        onImportLocalFont = viewModel::importLocalFont,
        onDeleteLocalFont = viewModel::deleteLocalFont,
        onClearImportError = viewModel::clearFontImportError,
        onDefaultProfileChange = viewModel::updateDefaultProfile,
        onRotationChange = viewModel::updateRotation,
        onFullscreenChange = viewModel::updateFullscreen,
        onTitleBarHideChange = viewModel::updateTitleBarHide,
        onPgUpDnGestureChange = viewModel::updatePgUpDnGesture,
        onVolumeFontChange = viewModel::updateVolumeFont,
        onKeepAliveChange = viewModel::updateKeepAlive,
        onAlwaysVisibleChange = viewModel::updateAlwaysVisible,
        onShiftFkeysChange = viewModel::updateShiftFkeys,
        onCtrlFkeysChange = viewModel::updateCtrlFkeys,
        onStickyModifiersChange = viewModel::updateStickyModifiers,
        onKeyModeChange = viewModel::updateKeyMode,
        onCameraChange = viewModel::updateCamera,
        onBumpyArrowsChange = viewModel::updateBumpyArrows,
        onBellChange = viewModel::updateBell,
        onBellVolumeChange = viewModel::updateBellVolume,
        onBellVibrateChange = viewModel::updateBellVibrate,
        onBellNotificationChange = viewModel::updateBellNotification,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onMemkeysChange: (Boolean) -> Unit,
    onConnPersistChange: (Boolean) -> Unit,
    onWifilockChange: (Boolean) -> Unit,
    onBackupkeysChange: (Boolean) -> Unit,
    onScrollbackChange: (String) -> Unit,
    onAddCustomTerminalType: (String) -> Unit,
    onRemoveCustomTerminalType: (String) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onAddCustomFont: (String) -> Unit,
    onRemoveCustomFont: (String) -> Unit,
    onClearFontError: () -> Unit,
    onImportLocalFont: (Uri, String) -> Unit,
    onDeleteLocalFont: (String) -> Unit,
    onClearImportError: () -> Unit,
    onDefaultProfileChange: (Long) -> Unit,
    onRotationChange: (String) -> Unit,
    onFullscreenChange: (Boolean) -> Unit,
    onTitleBarHideChange: (Boolean) -> Unit,
    onPgUpDnGestureChange: (Boolean) -> Unit,
    onVolumeFontChange: (Boolean) -> Unit,
    onKeepAliveChange: (Boolean) -> Unit,
    onAlwaysVisibleChange: (Boolean) -> Unit,
    onShiftFkeysChange: (Boolean) -> Unit,
    onCtrlFkeysChange: (Boolean) -> Unit,
    onStickyModifiersChange: (String) -> Unit,
    onKeyModeChange: (String) -> Unit,
    onCameraChange: (String) -> Unit,
    onBumpyArrowsChange: (Boolean) -> Unit,
    onBellChange: (Boolean) -> Unit,
    onBellVolumeChange: (Float) -> Unit,
    onBellVibrateChange: (Boolean) -> Unit,
    onBellNotificationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    onCheckedChange = onMemkeysChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_conn_persist_title),
                    summary = stringResource(R.string.pref_conn_persist_summary),
                    checked = uiState.connPersist,
                    onCheckedChange = onConnPersistChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_wifilock_title),
                    summary = stringResource(R.string.pref_wifilock_summary),
                    checked = uiState.wifilock,
                    onCheckedChange = onWifilockChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_backupkeys_title),
                    summary = stringResource(R.string.pref_backupkeys_summary),
                    checked = uiState.backupkeys,
                    onCheckedChange = onBackupkeysChange
                )
            }

            item {
                PreferenceCategory(title = stringResource(R.string.pref_emulation_category))
            }

            item {
                TextPreference(
                    title = stringResource(R.string.pref_scrollback_title),
                    summary = stringResource(R.string.pref_scrollback_summary),
                    value = uiState.scrollback,
                    onValueChange = onScrollbackChange
                )
            }

            item {
                AddCustomTerminalTypePreference(
                    customTerminalTypes = uiState.customTerminalTypes,
                    onAddTerminalType = onAddCustomTerminalType,
                    onRemoveTerminalType = onRemoveCustomTerminalType
                )
            }

            item {
                // Build combined font list: presets + custom fonts + local fonts
                // Only show downloadable preset fonts if Google Play Services is available
                val presetEntries = if (BuildConfig.HAS_DOWNLOADABLE_FONTS) {
                    TerminalFont.entries.map { it.displayName to it.name }
                } else {
                    // In OSS builds, only show System Default (which doesn't require download)
                    listOf(TerminalFont.SYSTEM_DEFAULT.displayName to TerminalFont.SYSTEM_DEFAULT.name)
                }
                val customEntries = if (BuildConfig.HAS_DOWNLOADABLE_FONTS) {
                    uiState.customFonts.map { it to TerminalFont.createCustomFontValue(it) }
                } else {
                    emptyList()
                }
                val localEntries = uiState.localFonts.map { (displayName, fileName) ->
                    displayName to LocalFontProvider.createLocalFontValue(fileName)
                }
                val allEntries = presetEntries + customEntries + localEntries

                ListPreference(
                    title = stringResource(R.string.pref_fontfamily_title),
                    summary = getLocalizedFontDisplayName(uiState.fontFamily),
                    value = uiState.fontFamily,
                    entries = allEntries,
                    onValueChange = onFontFamilyChange
                )
            }

            // Only show downloadable fonts UI if Google Play Services is available
            if (BuildConfig.HAS_DOWNLOADABLE_FONTS) {
                item {
                    AddCustomFontPreference(
                        customFonts = uiState.customFonts,
                        validationInProgress = uiState.fontValidationInProgress,
                        validationError = uiState.fontValidationError,
                        onAddFont = onAddCustomFont,
                        onRemoveFont = onRemoveCustomFont,
                        onClearError = onClearFontError
                    )
                }
            }

            item {
                LocalFontPreference(
                    localFonts = uiState.localFonts,
                    importInProgress = uiState.fontImportInProgress,
                    importError = uiState.fontImportError,
                    onImportFont = onImportLocalFont,
                    onDeleteFont = onDeleteLocalFont,
                    onClearError = onClearImportError
                )
            }

            item {
                PreferenceCategory(title = stringResource(R.string.pref_profiles_category))
            }

            item {
                val selectedProfile = if (uiState.defaultProfileId == 0L) {
                    null
                } else {
                    uiState.availableProfiles.find { it.id == uiState.defaultProfileId }
                }
                val noneLabel = stringResource(R.string.pref_default_profile_none)
                val profileEntries = listOf(noneLabel to "0") +
                    uiState.availableProfiles.map { it.name to it.id.toString() }
                ListPreference(
                    title = stringResource(R.string.pref_default_profile_title),
                    summary = selectedProfile?.name ?: noneLabel,
                    value = uiState.defaultProfileId.toString(),
                    entries = profileEntries,
                    onValueChange = { onDefaultProfileChange(it.toLong()) }
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
                    onValueChange = onRotationChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_fullscreen_title),
                    summary = stringResource(R.string.pref_fullscreen_summary),
                    checked = uiState.fullscreen,
                    onCheckedChange = onFullscreenChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_titlebarhide_title),
                    summary = stringResource(R.string.pref_titlebarhide_summary),
                    checked = uiState.titlebarhide,
                    onCheckedChange = onTitleBarHideChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_pg_updn_gesture_title),
                    summary = stringResource(R.string.pref_pg_updn_gesture_summary),
                    checked = uiState.pgupdngesture,
                    onCheckedChange = onPgUpDnGestureChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_volumefont_title),
                    summary = stringResource(R.string.pref_volumefont_summary),
                    checked = uiState.volumefont,
                    onCheckedChange = onVolumeFontChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_keepalive_title),
                    summary = stringResource(R.string.pref_keepalive_summary),
                    checked = uiState.keepalive,
                    onCheckedChange = onKeepAliveChange
                )
            }

            item {
                PreferenceCategory(title = stringResource(R.string.pref_keyboard_category))
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_alwaysvisible_title),
                    summary = stringResource(R.string.pref_alwaysvisible_summary),
                    checked = uiState.alwaysvisible,
                    onCheckedChange = onAlwaysVisibleChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_shiftfkeys_title),
                    summary = stringResource(R.string.pref_shiftfkeys_summary),
                    checked = uiState.shiftfkeys,
                    onCheckedChange = onShiftFkeysChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_ctrlfkeys_title),
                    summary = stringResource(R.string.pref_ctrlfkeys_summary),
                    checked = uiState.ctrlfkeys,
                    onCheckedChange = onCtrlFkeysChange
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
                    onValueChange = onStickyModifiersChange
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
                    onValueChange = onKeyModeChange
                )
            }

            item {
                val cameraSummary = when (uiState.camera) {
                    "Ctrl+A then Space" -> stringResource(R.string.list_camera_ctrlaspace_description)
                    "Ctrl+A" -> stringResource(R.string.list_camera_ctrla_description)
                    "Esc" -> stringResource(R.string.list_camera_esc_description)
                    "Esc+A" -> stringResource(R.string.list_camera_esc_a_description)
                    "None" -> stringResource(R.string.list_camera_none_description)
                    "text_input" -> stringResource(R.string.list_camera_text_input_description)
                    else -> uiState.camera
                }
                ListPreference(
                    title = stringResource(R.string.pref_camera_title),
                    summary = cameraSummary,
                    value = uiState.camera,
                    entries = listOf(
                        stringResource(R.string.list_camera_ctrlaspace) to "Ctrl+A then Space",
                        stringResource(R.string.list_camera_ctrla) to "Ctrl+A",
                        stringResource(R.string.list_camera_esc) to "Esc",
                        stringResource(R.string.list_camera_esc_a) to "Esc+A",
                        stringResource(R.string.list_camera_none) to "None",
                        stringResource(R.string.list_camera_text_input) to "text_input"
                    ),
                    onValueChange = onCameraChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_bumpyarrows_title),
                    summary = stringResource(R.string.pref_bumpyarrows_summary),
                    checked = uiState.bumpyarrows,
                    onCheckedChange = onBumpyArrowsChange
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
                    onCheckedChange = onBellChange
                )
            }

            item {
                SliderPreference(
                    title = stringResource(R.string.pref_bell_volume_title),
                    value = uiState.bellVolume,
                    onValueChange = onBellVolumeChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_bell_vibrate_title),
                    summary = stringResource(R.string.pref_bell_vibrate_summary),
                    checked = uiState.bellVibrate,
                    onCheckedChange = onBellVibrateChange
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.pref_bell_notification_title),
                    summary = stringResource(R.string.pref_bell_notification_summary),
                    checked = uiState.bellNotification,
                    onCheckedChange = onBellNotificationChange
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
private fun ListPreferenceWithCustom(
    title: String,
    summary: String,
    value: String,
    entries: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    customLabel: String = "Custom..."
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = modifier.clickable { showDialog = true }
    )
    HorizontalDivider()

    if (showDialog) {
        ListPreferenceWithCustomDialog(
            title = title,
            value = value,
            entries = entries,
            customLabel = customLabel,
            onDismiss = { showDialog = false },
            onConfirm = { newValue ->
                onValueChange(newValue)
                showDialog = false
            }
        )
    }
}

@Composable
private fun ListPreferenceWithCustomDialog(
    title: String,
    value: String,
    entries: List<Pair<String, String>>,
    customLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customValue by remember { mutableStateOf(value) }

    if (showCustomInput) {
        AlertDialog(
            onDismissRequest = {
                showCustomInput = false
                onDismiss()
            },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = customValue,
                    onValueChange = { customValue = it },
                    label = { Text(stringResource(R.string.dialog_custom_value_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customValue.isNotBlank()) {
                            onConfirm(customValue)
                        }
                    },
                    enabled = customValue.isNotBlank()
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCustomInput = false
                    onDismiss()
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    } else {
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ListItem(
                        headlineContent = {
                            Text(
                                text = customLabel,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable {
                            customValue = value
                            showCustomInput = true
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.delete_neg))
                }
            }
        )
    }
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

@Composable
private fun AddCustomTerminalTypePreference(
    customTerminalTypes: List<String>,
    onAddTerminalType: (String) -> Unit,
    onRemoveTerminalType: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newTerminalType by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_customterminal_title)) },
            supportingContent = { Text(stringResource(R.string.pref_customterminal_summary)) },
            modifier = Modifier.clickable { showAddDialog = true }
        )

        // Show existing custom terminal types with remove option
        customTerminalTypes.forEach { terminalType ->
            ListItem(
                headlineContent = { Text(terminalType) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onRemoveTerminalType(terminalType) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.button_remove)
                        )
                    }
                },
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        HorizontalDivider()
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                newTerminalType = ""
            },
            title = { Text(stringResource(R.string.dialog_customterminal_title)) },
            text = {
                OutlinedTextField(
                    value = newTerminalType,
                    onValueChange = { newTerminalType = it },
                    label = { Text(stringResource(R.string.dialog_customterminal_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTerminalType.isNotBlank()) {
                            onAddTerminalType(newTerminalType.trim())
                            showAddDialog = false
                            newTerminalType = ""
                        }
                    },
                    enabled = newTerminalType.isNotBlank()
                ) {
                    Text(stringResource(R.string.button_add))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    newTerminalType = ""
                }) {
                    Text(stringResource(R.string.delete_neg))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCustomFontPreference(
    customFonts: List<String>,
    validationInProgress: Boolean,
    validationError: String?,
    onAddFont: (String) -> Unit,
    onRemoveFont: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newFontName by remember { mutableStateOf("") }

    // Show error snackbar if there's an error
    LaunchedEffect(validationError) {
        if (validationError != null) {
            // Error is shown in dialog, will be cleared when dialog closes
        }
    }

    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_customfont_title)) },
            supportingContent = { Text(stringResource(R.string.pref_customfont_summary)) },
            modifier = Modifier.clickable { showAddDialog = true }
        )

        // Show existing custom fonts with remove option
        customFonts.forEach { fontName ->
            ListItem(
                headlineContent = { Text(fontName) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.FontDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onRemoveFont(fontName) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.button_remove)
                        )
                    }
                },
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        HorizontalDivider()
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!validationInProgress) {
                    showAddDialog = false
                    newFontName = ""
                    onClearError()
                }
            },
            title = { Text(stringResource(R.string.dialog_customfont_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFontName,
                        onValueChange = {
                            newFontName = it
                            onClearError()
                        },
                        label = { Text(stringResource(R.string.dialog_customfont_hint)) },
                        singleLine = true,
                        enabled = !validationInProgress,
                        isError = validationError != null,
                        supportingText = if (validationError != null) {
                            { Text(validationError, color = MaterialTheme.colorScheme.error) }
                        } else if (validationInProgress) {
                            { Text(stringResource(R.string.font_validating)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFontName.isNotBlank()) {
                            onAddFont(newFontName.trim())
                        }
                    },
                    enabled = newFontName.isNotBlank() && !validationInProgress
                ) {
                    Text(stringResource(R.string.button_add))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        newFontName = ""
                        onClearError()
                    },
                    enabled = !validationInProgress
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Close dialog when font is successfully added
    LaunchedEffect(customFonts.size) {
        if (showAddDialog && !validationInProgress && validationError == null && newFontName.isNotBlank()) {
            // Font was added successfully
            showAddDialog = false
            newFontName = ""
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalFontPreference(
    localFonts: List<Pair<String, String>>,
    importInProgress: Boolean,
    importError: String?,
    onImportFont: (Uri, String) -> Unit,
    onDeleteFont: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var fontDisplayName by remember { mutableStateOf("") }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingUri = uri
            fontDisplayName = ""
            showNameDialog = true
        }
    }

    Column(modifier = modifier) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.pref_localfont_title)) },
            supportingContent = {
                Text(
                    if (importInProgress) stringResource(R.string.font_importing)
                    else stringResource(R.string.pref_localfont_summary)
                )
            },
            modifier = Modifier.clickable(enabled = !importInProgress) {
                fontPickerLauncher.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf"))
            }
        )

        // Show existing local fonts with delete option
        localFonts.forEach { (displayName, fileName) ->
            ListItem(
                headlineContent = { Text(displayName) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onDeleteFont(fileName) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.button_remove)
                        )
                    }
                },
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        HorizontalDivider()
    }

    // Dialog to get display name for imported font
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!importInProgress) {
                    showNameDialog = false
                    pendingUri = null
                    fontDisplayName = ""
                    onClearError()
                }
            },
            title = { Text(stringResource(R.string.dialog_localfont_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = fontDisplayName,
                        onValueChange = {
                            fontDisplayName = it
                            onClearError()
                        },
                        label = { Text(stringResource(R.string.dialog_localfont_hint)) },
                        singleLine = true,
                        enabled = !importInProgress,
                        isError = importError != null,
                        supportingText = if (importError != null) {
                            { Text(importError, color = MaterialTheme.colorScheme.error) }
                        } else if (importInProgress) {
                            { Text(stringResource(R.string.font_importing)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingUri?.let { uri ->
                            if (fontDisplayName.isNotBlank()) {
                                onImportFont(uri, fontDisplayName.trim())
                            }
                        }
                    },
                    enabled = fontDisplayName.isNotBlank() && !importInProgress
                ) {
                    Text(stringResource(R.string.button_import))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNameDialog = false
                        pendingUri = null
                        fontDisplayName = ""
                        onClearError()
                    },
                    enabled = !importInProgress
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    // Close dialog when font is successfully imported
    LaunchedEffect(localFonts.size) {
        if (showNameDialog && !importInProgress && importError == null && fontDisplayName.isNotBlank()) {
            showNameDialog = false
            pendingUri = null
            fontDisplayName = ""
        }
    }
}

@ScreenPreviews
@Composable
private fun SettingsScreenPreview() {
    ConnectBotTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(
                memkeys = true,
                connPersist = true,
                wifilock = false,
                backupkeys = true,
                scrollback = "500",
                rotation = "Default",
                titlebarhide = false,
                fullscreen = true,
                pgupdngesture = true,
                volumefont = true,
                keepalive = true,
                alwaysvisible = true,
                shiftfkeys = false,
                ctrlfkeys = false,
                stickymodifiers = "yes",
                keymode = "Use right-side keys",
                camera = "Ctrl+A then Space",
                bumpyarrows = true,
                bell = true,
                bellVolume = 0.75f,
                bellVibrate = true,
                bellNotification = false,
                fontFamily = "JETBRAINS_MONO",
                customFonts = listOf("Cascadia Code", "Hack"),
                customTerminalTypes = listOf("rxvt-unicode", "tmux-256color"),
                localFonts = listOf("My Custom Font" to "my_custom_font.ttf"),
                fontValidationInProgress = false,
                fontValidationError = null,
                fontImportInProgress = false,
                fontImportError = null
            ),
            onNavigateBack = {},
            onMemkeysChange = {},
            onConnPersistChange = {},
            onWifilockChange = {},
            onBackupkeysChange = {},
            onScrollbackChange = {},
            onAddCustomTerminalType = {},
            onRemoveCustomTerminalType = {},
            onFontFamilyChange = {},
            onAddCustomFont = {},
            onRemoveCustomFont = {},
            onClearFontError = {},
            onImportLocalFont = { _, _ -> },
            onDeleteLocalFont = {},
            onClearImportError = {},
            onDefaultProfileChange = {},
            onRotationChange = {},
            onFullscreenChange = {},
            onTitleBarHideChange = {},
            onPgUpDnGestureChange = {},
            onVolumeFontChange = {},
            onKeepAliveChange = {},
            onAlwaysVisibleChange = {},
            onShiftFkeysChange = {},
            onCtrlFkeysChange = {},
            onStickyModifiersChange = {},
            onKeyModeChange = {},
            onCameraChange = {},
            onBumpyArrowsChange = {},
            onBellChange = {},
            onBellVolumeChange = {},
            onBellVibrateChange = {},
            onBellNotificationChange = {}
        )
    }
}

@Composable
private fun NotificationPermissionDeniedDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_permission_denied_title)) },
        text = { Text(stringResource(R.string.notification_permission_denied_message)) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
