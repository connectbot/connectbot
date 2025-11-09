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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreen(
    hostId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember(hostId) { HostEditorViewModel(context, hostId) }
    val uiState by viewModel.uiState.collectAsState()

    var showProtocolMenu by remember { mutableStateOf(false) }
    var expandedMode by remember { mutableStateOf(hostId != -1L) } // Expand if editing existing host
    val protocols = listOf("ssh", "telnet", "local")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (hostId == -1L) stringResource(R.string.hostpref_add_host)
                        else stringResource(R.string.hostpref_edit_host)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.button_navigate_up))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveHost(expandedMode)
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
                        Text(stringResource(if (hostId == -1L) R.string.hostpref_add_host else R.string.hostpref_edit_host))
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
        ) {
            if (!expandedMode) {
                // Quick connect mode
                OutlinedTextField(
                    value = uiState.quickConnect,
                    onValueChange = viewModel::updateQuickConnect,
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
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.expand),
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                // Expanded mode
                OutlinedTextField(
                    value = uiState.nickname,
                    onValueChange = viewModel::updateNickname,
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
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.ExpandLess,
                            contentDescription = stringResource(R.string.button_collapse),
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
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
                                viewModel.updateProtocol(protocol)
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
                    onValueChange = viewModel::updateUsername,
                    label = { Text(stringResource(R.string.hostpref_username_title)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.hostname,
                    onValueChange = viewModel::updateHostname,
                    label = { Text(stringResource(R.string.hostpref_hostname_title)) },
                    isError = uiState.hostname.isBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = viewModel::updatePort,
                    label = { Text(stringResource(R.string.hostpref_port_title)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true
                )
            }
            }

            // Color selector
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            ColorSelector(
                selectedColor = uiState.color,
                onColorSelected = viewModel::updateColor
            )

            // Font size slider
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            FontSizeSelector(
                fontSize = uiState.fontSize,
                onFontSizeChange = viewModel::updateFontSize
            )

            // Pubkey selector
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            PubkeySelector(
                pubkeyId = uiState.pubkeyId,
                availablePubkeys = uiState.availablePubkeys,
                onPubkeySelected = viewModel::updatePubkeyId
            )

            // DEL key selector
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            DelKeySelector(
                delKey = uiState.delKey,
                onDelKeySelected = viewModel::updateDelKey
            )

            // Encoding selector
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            EncodingSelector(
                encoding = uiState.encoding,
                onEncodingSelected = viewModel::updateEncoding
            )

            // SSH Auth agent
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_authagent_title),
                checked = uiState.useAuthAgent != "no",
                onCheckedChange = { checked ->
                    viewModel.updateUseAuthAgent(if (checked) "yes" else "no")
                }
            )

            if (uiState.useAuthAgent != "no") {
                SwitchPreference(
                    title = stringResource(R.string.hostpref_authagent_with_confirmation),
                    checked = uiState.useAuthAgent == "confirm",
                    onCheckedChange = { checked ->
                        viewModel.updateUseAuthAgent(if (checked) "confirm" else "yes")
                    }
                )
            }

            // Compression
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_compression_title),
                summary = stringResource(R.string.hostpref_compression_summary),
                checked = uiState.compression,
                onCheckedChange = viewModel::updateCompression
            )

            // Want session
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_wantsession_title),
                summary = stringResource(R.string.hostpref_wantsession_summary),
                checked = uiState.wantSession,
                onCheckedChange = viewModel::updateWantSession
            )

            // Stay connected
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_stayconnected_title),
                summary = stringResource(R.string.hostpref_stayconnected_summary),
                checked = uiState.stayConnected,
                onCheckedChange = viewModel::updateStayConnected
            )

            // Quick disconnect
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SwitchPreference(
                title = stringResource(R.string.hostpref_quickdisconnect_title),
                summary = stringResource(R.string.hostpref_quickdisconnect_summary),
                checked = uiState.quickDisconnect,
                onCheckedChange = viewModel::updateQuickDisconnect
            )

            // Post-login automation
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            OutlinedTextField(
                value = uiState.postLogin,
                onValueChange = viewModel::updatePostLogin,
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
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = listOf("red", "green", "blue", "gray")

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_color_title),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedColor,
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
                colors.forEach { color ->
                    DropdownMenuItem(
                        text = { Text(color) },
                        onClick = {
                            onColorSelected(color)
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_fontsize_title),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 8f..32f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = fontSize.toString(),
                modifier = Modifier.padding(start = 16.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PubkeySelector(
    pubkeyId: Long,
    availablePubkeys: List<org.connectbot.data.entity.Pubkey>,
    onPubkeySelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Build options list: first the default options, then individual keys
    val defaultOptions = listOf(
        stringResource(R.string.list_pubkeyids_any) to -1L,
        stringResource(R.string.list_pubkeyids_none) to -2L
    )

    val pubkeyOptions = availablePubkeys.map { pubkey ->
        (pubkey.nickname ?: "Unnamed Key") to pubkey.id
    }

    val allOptions = defaultOptions + pubkeyOptions

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_pubkeyid_title),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
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
                        selectedPubkey?.nickname ?: "Unknown key"
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
                            onPubkeySelected(id)
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
    onDelKeySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("del", "backspace")

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_delkey_title),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = delKey,
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
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onDelKeySelected(option)
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
    onEncodingSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val encodings = listOf("UTF-8", "ISO-8859-1", "US-ASCII", "Windows-1252")

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.hostpref_encoding_title),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = encoding,
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
                encodings.forEach { enc ->
                    DropdownMenuItem(
                        text = { Text(enc) },
                        onClick = {
                            onEncodingSelected(enc)
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
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}