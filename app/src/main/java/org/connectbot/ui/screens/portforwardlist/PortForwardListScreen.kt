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

package org.connectbot.ui.screens.portforwardlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.PortForward
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.theme.ConnectBotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardListScreen(
    hostId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val terminalManager = org.connectbot.ui.LocalTerminalManager.current
    val viewModel = remember(hostId) { PortForwardListViewModel(context, hostId, terminalManager) }
    val uiState by viewModel.uiState.collectAsState()

    PortForwardListScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onDeletePortForward = viewModel::deletePortForward,
        onAddPortForward = viewModel::addPortForward,
        onUpdatePortForward = viewModel::updatePortForward,
        onEnablePortForward = viewModel::enablePortForward,
        onDisablePortForward = viewModel::disablePortForward,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardListScreenContent(
    uiState: PortForwardListUiState,
    onNavigateBack: () -> Unit,
    onDeletePortForward: (PortForward) -> Unit,
    onAddPortForward: (String, String, String, String) -> Unit,
    onUpdatePortForward: (PortForward, String, String, String, String) -> Unit,
    onEnablePortForward: (PortForward) -> Unit,
    onDisablePortForward: (PortForward) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPortForward by remember { mutableStateOf<PortForward?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when there's an error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_port_forwards_list)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.portforward_pos)
                )
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.portForwards.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.empty_port_forwards_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 88.dp // Extra padding to avoid FAB overlap
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.portForwards,
                            key = { it.id }
                        ) { portForward ->
                            PortForwardListItem(
                                portForward = portForward,
                                onEdit = { editingPortForward = portForward },
                                onDelete = { onDeletePortForward(portForward) },
                                onEnable = { onEnablePortForward(portForward) },
                                onDisable = { onDisablePortForward(portForward) },
                                hasLiveConnection = uiState.hasLiveConnection
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PortForwardEditorDialog(
            onDismiss = { showAddDialog = false },
            onSave = { nickname, type, sourcePort, destination ->
                showAddDialog = false
                onAddPortForward(nickname, type, sourcePort, destination)
            }
        )
    }

    editingPortForward?.let { portForward ->
        val initialDest = if (portForward.destAddr != null && portForward.destPort > 0) {
            "${portForward.destAddr}:${portForward.destPort}"
        } else {
            portForward.destAddr ?: ""
        }

        PortForwardEditorDialog(
            onDismiss = { editingPortForward = null },
            onSave = { nickname, type, sourcePort, destination ->
                editingPortForward = null
                onUpdatePortForward(portForward, nickname, type, sourcePort, destination)
            },
            initialNickname = portForward.nickname,
            initialType = portForward.type,
            initialSourcePort = portForward.sourcePort.toString(),
            initialDestination = initialDest,
            isEditing = true
        )
    }
}

@ScreenPreviews
@Composable
private fun PortForwardListScreenEmptyPreview() {
    ConnectBotTheme {
        PortForwardListScreenContent(
            uiState = PortForwardListUiState(
                portForwards = emptyList(),
                isLoading = false
            ),
            onNavigateBack = {},
            onDeletePortForward = {},
            onAddPortForward = { _, _, _, _ -> },
            onUpdatePortForward = { _, _, _, _, _ -> },
            onEnablePortForward = {},
            onDisablePortForward = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PortForwardListScreenLoadingPreview() {
    ConnectBotTheme {
        PortForwardListScreenContent(
            uiState = PortForwardListUiState(
                portForwards = emptyList(),
                isLoading = true
            ),
            onNavigateBack = {},
            onDeletePortForward = {},
            onAddPortForward = { _, _, _, _ -> },
            onUpdatePortForward = { _, _, _, _, _ -> },
            onEnablePortForward = {},
            onDisablePortForward = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PortForwardListScreenErrorPreview() {
    ConnectBotTheme {
        PortForwardListScreenContent(
            uiState = PortForwardListUiState(
                portForwards = emptyList(),
                isLoading = false,
                error = "Failed to load port forwards"
            ),
            onNavigateBack = {},
            onDeletePortForward = {},
            onAddPortForward = { _, _, _, _ -> },
            onUpdatePortForward = { _, _, _, _, _ -> },
            onEnablePortForward = {},
            onDisablePortForward = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PortForwardListScreenPopulatedPreview() {
    ConnectBotTheme {
        PortForwardListScreenContent(
            uiState = PortForwardListUiState(
                portForwards = listOf(
                    PortForward(
                        id = 1,
                        nickname = "MySQL Tunnel",
                        type = "Local",
                        sourcePort = 3306,
                        destAddr = "db.internal",
                        destPort = 3306,
                        hostId = 1
                    ).apply { setEnabled(true) },
                    PortForward(
                        id = 2,
                        nickname = "Web Server",
                        type = "Remote",
                        sourcePort = 8080,
                        destAddr = "localhost",
                        destPort = 80,
                        hostId = 1
                    ).apply { setEnabled(false) },
                    PortForward(
                        id = 3,
                        nickname = "SOCKS Proxy",
                        type = "Dynamic",
                        sourcePort = 1080,
                        destAddr = "",
                        destPort = 0,
                        hostId = 1
                    ).apply { setEnabled(true) }
                ),
                isLoading = false,
                hasLiveConnection = true
            ),
            onNavigateBack = {},
            onDeletePortForward = {},
            onAddPortForward = { _, _, _, _ -> },
            onUpdatePortForward = { _, _, _, _, _ -> },
            onEnablePortForward = {},
            onDisablePortForward = {}
        )
    }
}

@Composable
private fun PortForwardListItem(
    portForward: PortForward,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    hasLiveConnection: Boolean,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isEnabled = portForward.isEnabled()

    ListItem(
        headlineContent = {
            Text(
                text = portForward.nickname,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.portforward_type_label,
                        portForward.type
                    )
                )
                Text("${portForward.sourcePort} → ${portForward.destAddr}:${portForward.destPort}")
            }
        },
        leadingContent = {
            if (hasLiveConnection) {
                Icon(
                    imageVector = if (isEnabled) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isEnabled) stringResource(R.string.portforward_enabled) else stringResource(R.string.portforward_disabled),
                    tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.button_more_options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (hasLiveConnection) {
                        if (isEnabled) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.portforward_disable)) },
                                onClick = {
                                    showMenu = false
                                    onDisable()
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Circle, null)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.portforward_enable)) },
                                onClick = {
                                    showMenu = false
                                    onEnable()
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.CheckCircle, null)
                                }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.portforward_edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.portforward_delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null)
                        }
                    )
                }
            }
        },
        modifier = modifier.clickable { onEdit() }
    )
    HorizontalDivider()
}
