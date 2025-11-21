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

package org.connectbot.ui.screens.hostlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.theme.ConnectBotTheme
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onNavigateToConsole: (Host) -> Unit,
    onNavigateToEditHost: (Host?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPubkeys: () -> Unit,
    onNavigateToPortForwards: (Host) -> Unit,
    onNavigateToColors: () -> Unit,
    onNavigateToHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val terminalManager = LocalTerminalManager.current
    val viewModel = remember { HostListViewModel(context, terminalManager) }
    val uiState by viewModel.uiState.collectAsState()

    HostListScreenContent(
        uiState = uiState,
        onNavigateToConsole = onNavigateToConsole,
        onNavigateToEditHost = onNavigateToEditHost,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToPubkeys = onNavigateToPubkeys,
        onNavigateToPortForwards = onNavigateToPortForwards,
        onNavigateToColors = onNavigateToColors,
        onNavigateToHelp = onNavigateToHelp,
        onToggleSortOrder = viewModel::toggleSortOrder,
        onDeleteHost = viewModel::deleteHost,
        onDisconnectAll = viewModel::disconnectAll,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreenContent(
    uiState: HostListUiState,
    onNavigateToConsole: (Host) -> Unit,
    onNavigateToEditHost: (Host?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPubkeys: () -> Unit,
    onNavigateToPortForwards: (Host) -> Unit,
    onNavigateToColors: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onToggleSortOrder: () -> Unit,
    onDeleteHost: (Host) -> Unit,
    onDisconnectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDisconnectAllDialog by remember { mutableStateOf(false) }
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
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.button_more_options))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(
                                    if (uiState.sortedByColor) R.string.list_menu_sortname
                                    else R.string.list_menu_sortcolor
                                ))
                            },
                            onClick = {
                                showMenu = false
                                onToggleSortOrder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_menu_settings)) },
                            onClick = {
                                showMenu = false
                                onNavigateToSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.title_colors)) },
                            onClick = {
                                showMenu = false
                                onNavigateToColors()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_menu_pubkeys)) },
                            onClick = {
                                showMenu = false
                                onNavigateToPubkeys()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_menu_disconnect)) },
                            onClick = {
                                showMenu = false
                                showDisconnectAllDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.title_help)) },
                            onClick = {
                                showMenu = false
                                onNavigateToHelp()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToEditHost(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hostpref_add_host))
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
                uiState.hosts.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.empty_hosts_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        TextButton(onClick = { onNavigateToEditHost(null) }) {
                            Text(stringResource(R.string.hostpref_add_host))
                        }
                    }
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
                            items = uiState.hosts,
                            key = { it.id }
                        ) { host ->
                            HostListItem(
                                host = host,
                                connectionState = uiState.connectionStates[host.id] ?: ConnectionState.UNKNOWN,
                                onClick = {
                                    onNavigateToConsole(host)
                                },
                                onEdit = { onNavigateToEditHost(host) },
                                onPortForwards = { onNavigateToPortForwards(host) },
                                onDelete = { onDeleteHost(host) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Disconnect All confirmation dialog
    if (showDisconnectAllDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectAllDialog = false },
            title = { Text(stringResource(R.string.list_menu_disconnect)) },
            text = { Text(stringResource(R.string.disconnect_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectAllDialog = false
                        onDisconnectAll()
                    }
                ) {
                    Text(stringResource(R.string.disconnect_all_pos))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectAllDialog = false }
                ) {
                    Text(stringResource(R.string.disconnect_all_neg))
                }
            }
        )
    }
}

@Composable
private fun HostListItem(
    host: Host,
    connectionState: ConnectionState,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPortForwards: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Determine border color based on connection state
    val borderColor = when (connectionState) {
        ConnectionState.CONNECTED -> colorResource(R.color.host_green) // Green
        ConnectionState.DISCONNECTED -> colorResource(R.color.host_red) // Red
        ConnectionState.UNKNOWN -> Color.Transparent
    }

    ListItem(
        headlineContent = {
            Text(
                text = host.nickname,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Text("${host.protocol}://${host.hostname}:${host.port}")
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(40.dp)
            ) {
                // Main host icon with colored background and border
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = parseColor(host.color),
                            shape = CircleShape
                        )
                        .border(
                            width = 3.dp,
                            color = borderColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (host.protocol) {
                            "ssh" -> Icons.Default.Computer
                            "telnet" -> Icons.Default.Computer
                            else -> Icons.Default.Link
                        },
                        contentDescription = when (connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.image_description_connected)
                            ConnectionState.DISCONNECTED -> stringResource(R.string.image_description_disconnected)
                            ConnectionState.UNKNOWN -> null
                        },
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Status badge icon in lower right corner
                if (connectionState != ConnectionState.UNKNOWN) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = when (connectionState) {
                                ConnectionState.CONNECTED -> Icons.Default.CheckCircle
                                ConnectionState.DISCONNECTED -> Icons.Default.Error
                                ConnectionState.UNKNOWN -> Icons.Default.Computer // Unreachable
                            },
                            contentDescription = null,
                            tint = when (connectionState) {
                                ConnectionState.CONNECTED -> colorResource(R.color.host_green)
                                ConnectionState.DISCONNECTED -> colorResource(R.color.host_red)
                                ConnectionState.UNKNOWN -> Color.Gray // Unreachable
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.button_host_options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_host_edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_host_portforwards)) },
                        onClick = {
                            showMenu = false
                            onPortForwards()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Link, null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_host_delete)) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null)
                        }
                    )
                }
            }
        },
        modifier = modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()

    if (showDeleteDialog) {
        HostDeleteDialog(
            host = host,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            }
        )
    }
}

@Composable
private fun HostDeleteDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_host_delete)) },
        text = {
            Text(stringResource(R.string.delete_host_confirm, host.nickname))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.button_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_no))
            }
        }
    )
}

@Composable
private fun parseColor(colorString: String?): Color {
    if (colorString.isNullOrBlank()) {
        return colorResource(R.color.host_blue)
    } else {
        val colorInt = colorString.toColorInt()
        return Color(colorInt)
    }
}

@ScreenPreviews
@Composable
private fun HostListScreenEmptyPreview() {
    ConnectBotTheme {
        HostListScreenContent(
            uiState = HostListUiState(
                hosts = emptyList(),
                isLoading = false
            ),
            onNavigateToConsole = {},
            onNavigateToEditHost = {},
            onNavigateToSettings = {},
            onNavigateToPubkeys = {},
            onNavigateToPortForwards = {},
            onNavigateToColors = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDisconnectAll = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun HostListScreenLoadingPreview() {
    ConnectBotTheme {
        HostListScreenContent(
            uiState = HostListUiState(
                hosts = emptyList(),
                isLoading = true
            ),
            onNavigateToConsole = {},
            onNavigateToEditHost = {},
            onNavigateToSettings = {},
            onNavigateToPubkeys = {},
            onNavigateToPortForwards = {},
            onNavigateToColors = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDisconnectAll = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun HostListScreenErrorPreview() {
    ConnectBotTheme {
        HostListScreenContent(
            uiState = HostListUiState(
                hosts = emptyList(),
                isLoading = false,
                error = "Failed to load hosts from database"
            ),
            onNavigateToConsole = {},
            onNavigateToEditHost = {},
            onNavigateToSettings = {},
            onNavigateToPubkeys = {},
            onNavigateToPortForwards = {},
            onNavigateToColors = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDisconnectAll = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun HostListScreenPopulatedPreview() {
    ConnectBotTheme {
        HostListScreenContent(
            uiState = HostListUiState(
                hosts = listOf(
                    Host(
                        id = 1,
                        nickname = "Production Server",
                        protocol = "ssh",
                        username = "root",
                        hostname = "prod.example.com",
                        port = 22,
                        color = "#4CAF50"
                    ),
                    Host(
                        id = 2,
                        nickname = "Development",
                        protocol = "ssh",
                        username = "developer",
                        hostname = "dev.example.com",
                        port = 2222,
                        color = "#2196F3"
                    ),
                    Host(
                        id = 3,
                        nickname = "Local VM",
                        protocol = "ssh",
                        username = "admin",
                        hostname = "192.168.1.100",
                        port = 22,
                        color = "#FF9800"
                    )
                ),
                connectionStates = mapOf(
                    1L to ConnectionState.CONNECTED,
                    2L to ConnectionState.DISCONNECTED,
                    3L to ConnectionState.UNKNOWN
                ),
                isLoading = false
            ),
            onNavigateToConsole = {},
            onNavigateToEditHost = {},
            onNavigateToSettings = {},
            onNavigateToPubkeys = {},
            onNavigateToPortForwards = {},
            onNavigateToColors = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDisconnectAll = {}
        )
    }
}
