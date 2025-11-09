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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import org.connectbot.R
import org.connectbot.data.entity.PortForward

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortForwardListScreen(
    hostId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember(hostId) { PortForwardListViewModel(context, hostId) }
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingPortForward by remember { mutableStateOf<PortForward?>(null) }

    Scaffold(
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.portforward_pos))
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
                uiState.error != null -> {
                    Text(
                        text = stringResource(R.string.error_message, uiState.error ?: ""),
                        color = MaterialTheme.colorScheme.error,
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
                    LazyColumn {
                        items(
                            items = uiState.portForwards,
                            key = { it.id }
                        ) { portForward ->
                            PortForwardListItem(
                                portForward = portForward,
                                onEdit = { editingPortForward = portForward },
                                onDelete = { viewModel.deletePortForward(portForward) }
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
                viewModel.addPortForward(nickname, type, sourcePort, destination)
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
                viewModel.updatePortForward(portForward, nickname, type, sourcePort, destination)
            },
            initialNickname = portForward.nickname ?: "",
            initialType = portForward.type ?: "",
            initialSourcePort = portForward.sourcePort.toString(),
            initialDestination = initialDest,
            isEditing = true
        )
    }
}

@Composable
private fun PortForwardListItem(
    portForward: PortForward,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = portForward.nickname ?: stringResource(R.string.portforward_unnamed),
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Column {
                Text(stringResource(R.string.portforward_type_label, portForward.type ?: stringResource(R.string.pubkey_type_unknown_text)))
                Text("${portForward.sourcePort} → ${portForward.destAddr}:${portForward.destPort}")
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
