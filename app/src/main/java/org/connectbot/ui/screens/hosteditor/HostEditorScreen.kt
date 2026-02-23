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

package org.connectbot.ui.screens.hosteditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.BuildConfig
import org.connectbot.R
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Profile
import org.connectbot.data.entity.Pubkey
import org.connectbot.ui.PreviewScreen
import org.connectbot.ui.common.getIconColors
import org.connectbot.ui.common.getLocalizedColorSchemeDescription
import org.connectbot.ui.common.getLocalizedFontDisplayName
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.HostConstants
import org.connectbot.util.LocalFontProvider
import org.connectbot.util.TerminalFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HostEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    HostEditorScreenContent(
        hostId = uiState.hostId,
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onQuickConnectChange = viewModel::updateQuickConnect,
        onNicknameChange = viewModel::updateNickname,
        onProtocolChange = viewModel::updateProtocol,
        onUsernameChange = viewModel::updateUsername,
        onHostnameChange = viewModel::updateHostname,
        onPortChange = viewModel::updatePort,
        onColorChange = viewModel::updateColor,
        onPubkeyChange = viewModel::updatePubkeyId,
        onProfileChange = viewModel::updateProfileId,
        onUseAuthAgentChange = viewModel::updateUseAuthAgent,
        onCompressionChange = viewModel::updateCompression,
        onWantSessionChange = viewModel::updateWantSession,
        onStayConnectedChange = viewModel::updateStayConnected,
        onQuickDisconnectChange = viewModel::updateQuickDisconnect,
        onPostLoginChange = viewModel::updatePostLogin,
        onJumpHostChange = viewModel::updateJumpHostId,
        onIpVersionChange = viewModel::updateIpVersion,
        onPasswordChange = viewModel::updatePassword,
        onClearPassword = viewModel::clearSavedPassword,
        onSaveHost = { expandedMode -> viewModel.saveHost(expandedMode) },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreenContent(
    hostId: Long,
    uiState: HostEditorUiState,
    onNavigateBack: () -> Unit,
    onQuickConnectChange: (String) -> Unit,
    onNicknameChange: (String) -> Unit,
    onProtocolChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onHostnameChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onPubkeyChange: (Long) -> Unit,
    onProfileChange: (Long?) -> Unit,
    onUseAuthAgentChange: (String) -> Unit,
    onCompressionChange: (Boolean) -> Unit,
    onWantSessionChange: (Boolean) -> Unit,
    onStayConnectedChange: (Boolean) -> Unit,
    onQuickDisconnectChange: (Boolean) -> Unit,
    onPostLoginChange: (String) -> Unit,
    onJumpHostChange: (Long?) -> Unit,
    onIpVersionChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onClearPassword: () -> Unit,
    onSaveHost: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showProtocolMenu by remember { mutableStateOf(false) }
    var expandedMode by remember { mutableStateOf(hostId != -1L) }
    val protocols = listOf("ssh", "telnet", "local")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (hostId == -1L) {
                            stringResource(R.string.hostpref_add_host)
                        } else {
                            stringResource(R.string.hostpref_setting_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSaveHost(expandedMode)
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("add_host_button"),
                        enabled = if (expandedMode) {
                            // For local protocol, hostname can be blank
                            uiState.protocol == "local" || uiState.hostname.isNotBlank()
                        } else {
                            uiState.quickConnect.isNotBlank()
                        }
                    ) {
                        Text(stringResource(if (hostId == -1L) R.string.hostpref_add_host else R.string.hostpref_save_host))
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding()
        ) {
            if (!expandedMode) {
                // Quick connect mode
                OutlinedTextField(
                    value = uiState.quickConnect,
                    onValueChange = onQuickConnectChange,
                    label = { Text(stringResource(R.string.host_editor_quick_connect_label)) },
                    placeholder = { Text(stringResource(R.string.host_editor_quick_connect_placeholder)) },
                    supportingText = { Text(stringResource(R.string.host_editor_quick_connect_example)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedMode = true }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.host_editor_show_advanced),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.expand),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // Expanded mode
                OutlinedTextField(
                    value = uiState.nickname,
                    onValueChange = onNicknameChange,
                    label = { Text(stringResource(R.string.hostpref_nickname_title)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Show collapse button only if this is a new host (not editing existing)
                if (hostId == -1L) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedMode = false }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.host_editor_hide_advanced),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ExpandLess,
                            contentDescription = stringResource(R.string.button_collapse),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Only show individual fields in expanded mode
            if (expandedMode) {
                // Protocol selector
                ExposedDropdownMenuBox(
                    expanded = showProtocolMenu,
                    onExpandedChange = { showProtocolMenu = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.protocol,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.protocol_spinner_label)) },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProtocolMenu)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = showProtocolMenu,
                        onDismissRequest = { showProtocolMenu = false }
                    ) {
                        protocols.forEach { protocol ->
                            DropdownMenuItem(
                                text = { Text(protocol) },
                                onClick = {
                                    onProtocolChange(protocol)
                                    showProtocolMenu = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                // Only show username, hostname, and port for non-local protocols
                if (uiState.protocol != "local") {
                    OutlinedTextField(
                        value = uiState.username,
                        onValueChange = onUsernameChange,
                        label = { Text(stringResource(R.string.hostpref_username_title)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.hostname,
                        onValueChange = onHostnameChange,
                        label = { Text(stringResource(R.string.hostpref_hostname_title)) },
                        isError = uiState.hostname.isBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.port,
                        onValueChange = onPortChange,
                        label = { Text(stringResource(R.string.hostpref_port_title)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true
                    )

                    // IP version selector (disabled for literal IP addresses)
                    IpVersionSelector(
                        ipVersion = uiState.ipVersion,
                        hostname = uiState.hostname,
                        onIpVersionSelect = onIpVersionChange,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    // Save password section (SSH only)
                    if (uiState.protocol == "ssh") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = onPasswordChange,
                            label = {
                                Text(
                                    if (uiState.hasExistingPassword && uiState.password.isEmpty()) {
                                        stringResource(R.string.hostpref_password_unchanged)
                                    } else {
                                        stringResource(R.string.hostpref_password_title)
                                    }
                                )
                            },
                            supportingText = {
                                Text(stringResource(R.string.hostpref_save_password_summary))
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (uiState.hasExistingPassword) {
                            TextButton(
                                onClick = onClearPassword,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(stringResource(R.string.hostpref_clear_password))
                            }
                        }
                    }
                }
            }

            // Color selector
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            ColorSelector(
                selectedColor = uiState.color,
                onColorSelect = onColorChange
            )

            // Pubkey selector
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            PubkeySelector(
                pubkeyId = uiState.pubkeyId,
                availablePubkeys = uiState.availablePubkeys,
                onPubkeySelect = onPubkeyChange
            )

            // Profile selector
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            ProfileSelector(
                profileId = uiState.profileId,
                availableProfiles = uiState.availableProfiles,
                onProfileSelect = onProfileChange
            )

            // Jump host selector (only for SSH protocol)
            if (uiState.protocol == "ssh") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                JumpHostSelector(
                    jumpHostId = uiState.jumpHostId,
                    availableJumpHosts = uiState.availableJumpHosts,
                    onJumpHostSelect = onJumpHostChange
                )
            }

            // SSH Auth agent
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_authagent_title),
                checked = uiState.useAuthAgent != "no",
                onCheckedChange = { checked ->
                    onUseAuthAgentChange(if (checked) "yes" else "no")
                }
            )

            if (uiState.useAuthAgent != "no") {
                SwitchPreference(
                    title = stringResource(R.string.hostpref_authagent_with_confirmation),
                    checked = uiState.useAuthAgent == "confirm",
                    onCheckedChange = { checked ->
                        onUseAuthAgentChange(if (checked) "confirm" else "yes")
                    }
                )
            }

            // Compression
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_compression_title),
                summary = stringResource(R.string.hostpref_compression_summary),
                checked = uiState.compression,
                onCheckedChange = onCompressionChange
            )

            // Want session
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_wantsession_title),
                summary = stringResource(R.string.hostpref_wantsession_summary),
                checked = uiState.wantSession,
                onCheckedChange = onWantSessionChange
            )

            // Stay connected
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_stayconnected_title),
                summary = stringResource(R.string.hostpref_stayconnected_summary),
                checked = uiState.stayConnected,
                onCheckedChange = onStayConnectedChange
            )

            // Quick disconnect
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_quickdisconnect_title),
                summary = stringResource(R.string.hostpref_quickdisconnect_summary),
                checked = uiState.quickDisconnect,
                onCheckedChange = onQuickDisconnectChange
            )

            // Post-login automation
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            OutlinedTextField(
                value = uiState.postLogin,
                onValueChange = onPostLoginChange,
                label = { Text(stringResource(R.string.hostpref_postlogin_title)) },
                supportingText = { Text(stringResource(R.string.hostpref_postlogin_summary)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSelector(
    selectedColor: String,
    onColorSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val iconColors = getIconColors()

    // Find the display name for the selected color
    // Check by hex value first (current format), then by English name (legacy format)
    val selectedDisplayName = iconColors.find { it.hexValue.equals(selectedColor, ignoreCase = true) }?.localizedName
        ?: iconColors.find { it.englishName.equals(selectedColor, ignoreCase = true) }?.localizedName
        ?: selectedColor

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_color_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedDisplayName,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                iconColors.forEach { color ->
                    DropdownMenuItem(
                        text = { Text(color.localizedName) },
                        onClick = {
                            // Always store hex value in database (language-independent)
                            onColorSelect(color.hexValue)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun FontSizeSelector(
    fontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_fontsize_title),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (!enabled) {
            Text(
                text = stringResource(R.string.profile_controlled_setting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 8f..32f,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = fontSize.toString(),
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontFamilySelector(
    fontFamily: String?,
    customFonts: List<String>,
    localFonts: List<Pair<String, String>>,
    onFontFamilySelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    // Build options: "Use Default" + preset fonts + custom fonts (if available) + local fonts
    // Only show downloadable preset fonts if Google Play Services is available
    val presetOptions = if (BuildConfig.HAS_DOWNLOADABLE_FONTS) {
        TerminalFont.entries.map { it.displayName to it.name }
    } else {
        // In OSS builds, only show System Default (which doesn't require download)
        listOf(TerminalFont.SYSTEM_DEFAULT.displayName to TerminalFont.SYSTEM_DEFAULT.name)
    }
    val customOptions = if (BuildConfig.HAS_DOWNLOADABLE_FONTS) {
        customFonts.map { it to TerminalFont.createCustomFontValue(it) }
    } else {
        emptyList()
    }
    val localOptions = localFonts.map { (displayName, fileName) ->
        displayName to LocalFontProvider.createLocalFontValue(fileName)
    }
    val allOptions = listOf(stringResource(R.string.font_use_default) to null) + presetOptions + customOptions + localOptions

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_fontfamily_title),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (!enabled) {
            Text(
                text = stringResource(R.string.profile_controlled_setting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            OutlinedTextField(
                value = if (fontFamily == null) {
                    stringResource(R.string.font_use_default)
                } else {
                    getLocalizedFontDisplayName(fontFamily)
                },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                enabled = enabled,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                allOptions.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onFontFamilySelect(value)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorSchemeSelector(
    colorSchemeId: Long,
    availableSchemes: List<ColorScheme>,
    onColorSchemeSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_colorscheme_title),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (!enabled) {
            Text(
                text = stringResource(R.string.profile_controlled_setting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            OutlinedTextField(
                value = availableSchemes.find { it.id == colorSchemeId }?.name ?: stringResource(R.string.colorscheme_default),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                enabled = enabled,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableSchemes.forEach { scheme ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(scheme.name)
                                val localizedDescription = getLocalizedColorSchemeDescription(scheme)
                                if (localizedDescription.isNotBlank()) {
                                    Text(
                                        text = localizedDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onColorSchemeSelect(scheme.id)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PubkeySelector(
    pubkeyId: Long,
    availablePubkeys: List<Pubkey>,
    onPubkeySelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Build options list: first the default options, then individual keys
    val defaultOptions = listOf(
        stringResource(R.string.list_pubkeyids_any) to -1L,
        stringResource(R.string.list_pubkeyids_none) to -2L
    )

    val pubkeyOptions = availablePubkeys.map { pubkey ->
        pubkey.nickname to pubkey.id
    }

    val allOptions = defaultOptions + pubkeyOptions

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_pubkeyid_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = when (pubkeyId) {
                    -1L -> stringResource(R.string.list_pubkeyids_any)

                    -2L -> stringResource(R.string.list_pubkeyids_none)

                    else -> {
                        val selectedPubkey = availablePubkeys.find { it.id == pubkeyId }
                        selectedPubkey?.nickname ?: stringResource(R.string.pubkey_unknown)
                    }
                },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                allOptions.forEach { (label, id) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onPubkeySelect(id)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSelector(
    profileId: Long?,
    availableProfiles: List<Profile>,
    onProfileSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_profile_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.hostpref_profile_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = when {
                    profileId == null -> stringResource(R.string.hostpref_profile_none)

                    else -> {
                        val selectedProfile = availableProfiles.find { it.id == profileId }
                        selectedProfile?.name ?: stringResource(R.string.hostpref_profile_none)
                    }
                },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // "None" option
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hostpref_profile_none)) },
                    onClick = {
                        onProfileSelect(null)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )

                // Available profiles
                availableProfiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name) },
                        onClick = {
                            onProfileSelect(profile.id)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IpVersionSelector(
    ipVersion: String,
    hostname: String,
    onIpVersionSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val isLiteralIp = HostConstants.isIpAddress(hostname)
    val isIpv4 = HostConstants.isIpv4Address(hostname)
    val isIpv6 = HostConstants.isIpv6Address(hostname)

    val options = listOf(
        HostConstants.IPVERSION_IPV4_AND_IPV6 to stringResource(R.string.ipversion_auto),
        HostConstants.IPVERSION_IPV4_ONLY to stringResource(R.string.ipversion_ipv4),
        HostConstants.IPVERSION_IPV6_ONLY to stringResource(R.string.ipversion_ipv6)
    )

    val displayLabel = when {
        isIpv4 -> stringResource(R.string.ipversion_ipv4)

        isIpv6 -> stringResource(R.string.ipversion_ipv6)

        else -> options.find { it.first == ipVersion }?.second
            ?: stringResource(R.string.ipversion_auto)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && !isLiteralIp,
        onExpandedChange = { if (!isLiteralIp) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            label = { Text(stringResource(R.string.hostpref_ipversion_title)) },
            readOnly = true,
            enabled = !isLiteralIp,
            singleLine = true,
            trailingIcon = {
                if (!isLiteralIp) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded && !isLiteralIp,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onIpVersionSelect(value)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpHostSelector(
    jumpHostId: Long?,
    availableJumpHosts: List<Host>,
    onJumpHostSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_jumphost_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.hostpref_jumphost_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = when {
                    jumpHostId == null || jumpHostId <= 0 -> stringResource(R.string.list_jumphost_none)

                    else -> {
                        val selectedHost = availableJumpHosts.find { it.id == jumpHostId }
                        selectedHost?.nickname ?: stringResource(R.string.list_jumphost_none)
                    }
                },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // "None" option for direct connection
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.list_jumphost_none)) },
                    onClick = {
                        onJumpHostSelect(null)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )

                // Available jump hosts
                availableJumpHosts.forEach { host ->
                    DropdownMenuItem(
                        text = { Text(host.nickname) },
                        onClick = {
                            onJumpHostSelect(host.id)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DelKeySelector(
    delKey: String,
    onDelKeySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("del", "backspace")

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_delkey_title),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (!enabled) {
            Text(
                text = stringResource(R.string.profile_controlled_setting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            OutlinedTextField(
                value = delKey,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                enabled = enabled,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onDelKeySelect(option)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EncodingSelector(
    encoding: String,
    onEncodingSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val encodings = listOf("UTF-8", "ISO-8859-1", "US-ASCII", "Windows-1252")

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_encoding_title),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (!enabled) {
            Text(
                text = stringResource(R.string.profile_controlled_setting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            OutlinedTextField(
                value = encoding,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                enabled = enabled,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                encodings.forEach { enc ->
                    DropdownMenuItem(
                        text = { Text(enc) },
                        onClick = {
                            onEncodingSelect(enc)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@PreviewScreen
@Composable
private fun HostEditorScreenPreview() {
    ConnectBotTheme {
        HostEditorScreenContent(
            hostId = 1L,
            uiState = HostEditorUiState(
                hostId = 1L,
                nickname = "Production Server",
                protocol = "ssh",
                username = "admin",
                hostname = "prod.example.com",
                port = "22",
                color = "blue",
                pubkeyId = -1L,
                availablePubkeys = listOf(
                    Pubkey(
                        id = 1,
                        nickname = "My SSH Key",
                        type = "rsa",
                        privateKey = byteArrayOf(),
                        publicKey = byteArrayOf(),
                        encrypted = false,
                        startup = false,
                        confirmation = false,
                        createdDate = System.currentTimeMillis()
                    )
                ),
                useAuthAgent = "yes",
                compression = true,
                wantSession = true,
                stayConnected = false,
                quickDisconnect = false,
                postLogin = "cd /var/www"
            ),
            onNavigateBack = {},
            onQuickConnectChange = {},
            onNicknameChange = {},
            onProtocolChange = {},
            onUsernameChange = {},
            onHostnameChange = {},
            onPortChange = {},
            onColorChange = {},
            onPubkeyChange = {},
            onProfileChange = {},
            onUseAuthAgentChange = {},
            onCompressionChange = {},
            onWantSessionChange = {},
            onStayConnectedChange = {},
            onQuickDisconnectChange = {},
            onPostLoginChange = {},
            onJumpHostChange = {},
            onIpVersionChange = {},
            onPasswordChange = {},
            onClearPassword = {},
            onSaveHost = {}
        )
    }
}
