/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package org.connectbot.ui.screens.portforwardlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.components.SaveEditorFab
import org.connectbot.util.HostConstants

@Composable
fun PortForwardEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortForwardEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val terminalManager = LocalTerminalManager.current
    LaunchedEffect(terminalManager) { viewModel.setTerminalManager(terminalManager) }

    PortForwardEditorScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNicknameChange = viewModel::updateNickname,
        onTypeChange = viewModel::updateType,
        onSourcePortChange = viewModel::updateSourcePort,
        onSourceAddressOptionChange = viewModel::updateSourceAddressOption,
        onSpecificAddressChange = viewModel::updateSpecificAddress,
        onDestinationChange = viewModel::updateDestination,
        onSave = { viewModel.save(onNavigateBack) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardEditorScreenContent(
    uiState: PortForwardEditorUiState,
    onNavigateBack: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onSourcePortChange: (String) -> Unit,
    onSourceAddressOptionChange: (SourceAddressOption) -> Unit,
    onSpecificAddressChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var sourceAddressMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { snackbarHostState.showSnackbar(it, withDismissAction = true) }
    }

    val types = listOf(
        HostConstants.PORTFORWARD_LOCAL,
        HostConstants.PORTFORWARD_REMOTE,
        HostConstants.PORTFORWARD_DYNAMIC5,
    )
    val typeLabels = stringArrayResource(R.array.list_portforward_types)
    val typeIndex = types.indexOf(uiState.type).coerceAtLeast(0)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (uiState.forwardId == 0L) R.string.portforward_pos else R.string.portforward_edit)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.button_navigate_up))
                    }
                },
            )
        },
        floatingActionButton = {
            SaveEditorFab(
                visible = !uiState.isLoading && uiState.hasUnsavedChanges && uiState.canSave,
                isSaving = uiState.isSaving,
                contentDescription = stringResource(if (uiState.forwardId == 0L) R.string.portforward_pos else R.string.portforward_save),
                onClick = onSave,
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .imePadding(),
            ) {
                OutlinedTextField(
                    value = uiState.nickname,
                    onValueChange = onNicknameChange,
                    label = { Text(stringResource(R.string.prompt_nickname)) },
                    placeholder = { Text(stringResource(R.string.portforward_nickname_placeholder)) },
                    modifier = Modifier.fillMaxWidth().testTag(PortForwardEditorTestTags.NICKNAME_FIELD),
                    singleLine = true,
                )

                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = typeLabels[typeIndex],
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.prompt_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenuExpanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        types.forEachIndexed { index, type ->
                            DropdownMenuItem(
                                text = { Text(typeLabels[index]) },
                                onClick = {
                                    onTypeChange(type)
                                    typeMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                if (uiState.isRemote) {
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = sourceAddressMenuExpanded,
                        onExpandedChange = { sourceAddressMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = stringResource(uiState.sourceAddressOption.labelRes),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.portforward_host_address)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sourceAddressMenuExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = sourceAddressMenuExpanded, onDismissRequest = { sourceAddressMenuExpanded = false }) {
                            SourceAddressOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    onClick = {
                                        onSourceAddressOptionChange(option)
                                        sourceAddressMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    if (uiState.sourceAddressOption == SourceAddressOption.SPECIFIC) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = uiState.specificAddress,
                            onValueChange = onSpecificAddressChange,
                            label = { Text(stringResource(R.string.portforward_host_address)) },
                            placeholder = { Text(stringResource(R.string.portforward_source_addr_specific_placeholder)) },
                            modifier = Modifier.fillMaxWidth().testTag(PortForwardEditorTestTags.SPECIFIC_SOURCE_ADDRESS_FIELD),
                            singleLine = true,
                            isError = uiState.specificAddress.isBlank(),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.sourcePort,
                    onValueChange = onSourcePortChange,
                    label = { Text(stringResource(R.string.prompt_source_port)) },
                    placeholder = { Text(stringResource(R.string.portforward_source_port_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag(PortForwardEditorTestTags.SOURCE_PORT_FIELD),
                    singleLine = true,
                    isError = uiState.sourcePort.isNotEmpty() && !uiState.isSourcePortValid,
                    supportingText = if (uiState.sourcePort.isNotEmpty() && !uiState.isSourcePortValid) {
                        { Text(stringResource(R.string.portforward_port_range_error)) }
                    } else {
                        null
                    },
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.destination,
                    onValueChange = onDestinationChange,
                    label = { Text(stringResource(R.string.prompt_destination)) },
                    placeholder = { Text(stringResource(R.string.portforward_destination_placeholder)) },
                    enabled = uiState.needsDestination,
                    modifier = Modifier.fillMaxWidth().testTag(PortForwardEditorTestTags.DESTINATION_FIELD),
                    singleLine = true,
                    isError = uiState.needsDestination && uiState.destination.isNotEmpty() && !uiState.isDestinationValid,
                    supportingText = if (uiState.needsDestination && uiState.destination.isNotEmpty() && !uiState.isDestinationValid) {
                        { Text(stringResource(R.string.portforward_destination_format_error)) }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}
