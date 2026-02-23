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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.components.DisconnectAllDialog
import org.connectbot.ui.components.ShortcutCustomizationDialog
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.IconStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreen(
    onNavigateToConsole: (Host) -> Unit,
    onNavigateToEditHost: (Host?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPubkeys: () -> Unit,
    onNavigateToPortForwards: (Host) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToHelp: () -> Unit,
    modifier: Modifier = Modifier,
    makingShortcut: Boolean = false,
    onSelectShortcut: (Host, String?, IconStyle) -> Unit = { _, _, _ -> },
    viewModel: HostListViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val terminalManager = LocalTerminalManager.current

    LaunchedEffect(terminalManager) {
        terminalManager?.let { viewModel.setTerminalManager(it) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // File picker for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && uiState.exportedJson != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(uiState.exportedJson!!.toByteArray())
                    }
                    val exportResult = uiState.exportResult
                    if (exportResult != null) {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.export_hosts_success,
                                exportResult.hostCount,
                                exportResult.profileCount
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.export_hosts_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
                viewModel.clearExportedJson()
            }
        } else {
            viewModel.clearExportedJson()
        }
    }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    }
                    if (jsonString != null) {
                        viewModel.importHosts(jsonString)
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.import_hosts_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Show errors as Toast notifications
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMessage ->
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Handle export result - launch file picker when JSON is ready
    LaunchedEffect(uiState.exportedJson) {
        if (uiState.exportedJson != null) {
            exportLauncher.launch(context.getString(R.string.export_hosts_filename))
        }
    }

    // Handle import result
    LaunchedEffect(uiState.importResult) {
        uiState.importResult?.let { result ->
            Toast.makeText(
                context,
                context.getString(
                    R.string.import_hosts_success,
                    result.hostsImported,
                    result.hostsSkipped,
                    result.profilesImported,
                    result.profilesSkipped
                ),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.clearImportResult()
        }
    }

    var shortcutHost by remember { mutableStateOf<Host?>(null) }

    if (shortcutHost != null) {
        ShortcutCustomizationDialog(
            host = shortcutHost!!,
            onDismiss = { shortcutHost = null },
            onConfirm = { color, iconStyle ->
                onSelectShortcut(shortcutHost!!, color, iconStyle)
                shortcutHost = null
            }
        )
    }

    HostListScreenContent(
        uiState = uiState,
        makingShortcut = makingShortcut,
        onNavigateToConsole = onNavigateToConsole,
        onSelectShortcut = { host -> shortcutHost = host },
        onNavigateToEditHost = onNavigateToEditHost,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToPubkeys = onNavigateToPubkeys,
        onNavigateToPortForwards = onNavigateToPortForwards,
        onNavigateToProfiles = onNavigateToProfiles,
        onNavigateToHelp = onNavigateToHelp,
        onToggleSortOrder = viewModel::toggleSortOrder,
        onDeleteHost = viewModel::deleteHost,
        onDuplicateHost = viewModel::duplicateHost,
        onForgetHostKeys = viewModel::forgetHostKeys,
        onDisconnectHost = viewModel::disconnectHost,
        onDisconnectAll = viewModel::disconnectAll,
        onExportHosts = viewModel::exportHosts,
        onImportHosts = { importLauncher.launch(arrayOf("application/json")) },
        onOpenNewSession = { host ->
            viewModel.connectToHost(host)
            onNavigateToConsole(host)
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostListScreenContent(
    uiState: HostListUiState,
    makingShortcut: Boolean = false,
    onNavigateToConsole: (Host) -> Unit,
    onSelectShortcut: (Host) -> Unit = {},
    onNavigateToEditHost: (Host?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPubkeys: () -> Unit,
    onNavigateToPortForwards: (Host) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onToggleSortOrder: () -> Unit,
    onDeleteHost: (Host) -> Unit,
    onDuplicateHost: (Host) -> Unit,
    onForgetHostKeys: (Host) -> Unit,
    onDisconnectHost: (Host) -> Unit,
    onDisconnectAll: () -> Unit,
    onExportHosts: () -> Unit = {},
    onImportHosts: () -> Unit = {},
    onOpenNewSession: (Host) -> Unit = {},
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
                    if (!makingShortcut) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.button_more_options))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (uiState.sortedByColor) {
                                                R.string.list_menu_sortname
                                            } else {
                                                R.string.list_menu_sortcolor
                                            }
                                        )
                                    )
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
                                text = { Text(stringResource(R.string.profile_list_title)) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToProfiles()
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
                                text = { Text(stringResource(R.string.list_menu_export_hosts)) },
                                onClick = {
                                    showMenu = false
                                    onExportHosts()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_menu_import_hosts)) },
                                onClick = {
                                    showMenu = false
                                    onImportHosts()
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
                }
            )
        },
        floatingActionButton = {
            if (!makingShortcut) {
                FloatingActionButton(
                    onClick = { onNavigateToEditHost(null) },
                    // This matches the FloatingActionButtonMenu padding
                    modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hostpref_add_host))
                }
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
                            bottom = 104.dp // Extra padding to avoid FAB menu overlap (88dp + 16dp for menu padding)
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
                                sessionCount = uiState.sessionCounts[host.id] ?: 0,
                                makingShortcut = makingShortcut,
                                onClick = {
                                    if (makingShortcut) {
                                        onSelectShortcut(host)
                                    } else {
                                        onNavigateToConsole(host)
                                    }
                                },
                                onEdit = { onNavigateToEditHost(host) },
                                onPortForwards = { onNavigateToPortForwards(host) },
                                onDuplicate = { onDuplicateHost(host) },
                                onForgetHostKeys = { onForgetHostKeys(host) },
                                onDisconnect = { onDisconnectHost(host) },
                                onDelete = { onDeleteHost(host) },
                                onOpenNewSession = { onOpenNewSession(host) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDisconnectAllDialog) {
        DisconnectAllDialog(
            onDismiss = { showDisconnectAllDialog = false },
            onConfirm = {
                showDisconnectAllDialog = false
                onDisconnectAll()
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostListItem(
    host: Host,
    connectionState: ConnectionState,
    sessionCount: Int = 0,
    makingShortcut: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPortForwards: () -> Unit,
    onDuplicate: () -> Unit,
    onForgetHostKeys: () -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit,
    onOpenNewSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showForgetHostKeysDialog by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    val isConnected = connectionState == ConnectionState.CONNECTED

    // Determine border color based on connection state
    val borderColor = when (connectionState) {
        ConnectionState.CONNECTED -> colorResource(R.color.host_green)

        // Green
        ConnectionState.DISCONNECTED -> colorResource(R.color.host_red)

        // Red
        ConnectionState.UNKNOWN -> Color.Transparent
    }

    Box(modifier = modifier) {
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

                    // Session count badge (top right corner) when multiple sessions
                    if (sessionCount > 1) {
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                        ) {
                            Text(sessionCount.toString())
                        }
                    }
                }
            },
            trailingContent = {
                if (!makingShortcut) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.button_host_options))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // Show "Open new session" option for connected hosts
                            if (isConnected) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.list_host_new_session)) },
                                    onClick = {
                                        showMenu = false
                                        onOpenNewSession()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.OpenInNew, null)
                                    }
                                )
                            }
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
                                text = { Text(stringResource(R.string.list_host_duplicate)) },
                                onClick = {
                                    showMenu = false
                                    onDuplicate()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, null)
                                }
                            )
                            if (host.protocol == "ssh") {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.list_host_forget_keys)) },
                                    onClick = {
                                        showMenu = false
                                        showForgetHostKeysDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Key, null)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_host_disconnect)) },
                                onClick = {
                                    showMenu = false
                                    showDisconnectDialog = true
                                },
                                enabled = connectionState == ConnectionState.CONNECTED,
                                leadingIcon = {
                                    Icon(Icons.Default.LinkOff, null)
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
                }
            },
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = {
                    // Show context menu on long press for connected hosts (when not making shortcut)
                    if (isConnected && !makingShortcut) {
                        showLongPressMenu = true
                    }
                }
            )
        )

        // Long-press context menu
        DropdownMenu(
            expanded = showLongPressMenu,
            onDismissRequest = { showLongPressMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.list_host_new_session)) },
                onClick = {
                    showLongPressMenu = false
                    onOpenNewSession()
                },
                leadingIcon = {
                    Icon(Icons.Default.OpenInNew, null)
                }
            )
        }
    }
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

    if (showDisconnectDialog) {
        HostDisconnectDialog(
            host = host,
            onDismiss = { showDisconnectDialog = false },
            onConfirm = {
                showDisconnectDialog = false
                onDisconnect()
            }
        )
    }

    if (showForgetHostKeysDialog) {
        ForgetHostKeysDialog(
            host = host,
            onDismiss = { showForgetHostKeysDialog = false },
            onConfirm = {
                showForgetHostKeysDialog = false
                onForgetHostKeys()
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
private fun HostDisconnectDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_host_disconnect)) },
        text = {
            Text(stringResource(R.string.disconnect_all_sessions_alert, host.nickname))
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
private fun ForgetHostKeysDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_host_forget_keys)) },
        text = {
            Text(stringResource(R.string.forget_host_keys_confirm, host.nickname))
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
            onNavigateToProfiles = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDuplicateHost = {},
            onForgetHostKeys = {},
            onDisconnectHost = {},
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
            onNavigateToProfiles = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDuplicateHost = {},
            onForgetHostKeys = {},
            onDisconnectHost = {},
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
            onNavigateToProfiles = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDuplicateHost = {},
            onForgetHostKeys = {},
            onDisconnectHost = {},
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
            onNavigateToProfiles = {},
            onNavigateToHelp = {},
            onToggleSortOrder = {},
            onDeleteHost = {},
            onDuplicateHost = {},
            onForgetHostKeys = {},
            onDisconnectHost = {},
            onDisconnectAll = {}
        )
    }
}
