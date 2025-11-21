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

package org.connectbot.ui.screens.pubkeylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.Pubkey
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.theme.ConnectBotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGenerate: () -> Unit,
    onNavigateToEdit: (Pubkey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember { PubkeyListViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()

    PubkeyListScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateToGenerate = onNavigateToGenerate,
        onNavigateToEdit = onNavigateToEdit,
        onDeletePubkey = viewModel::deletePubkey,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyListScreenContent(
    uiState: PubkeyListUiState,
    onNavigateBack: () -> Unit,
    onNavigateToGenerate: () -> Unit,
    onNavigateToEdit: (Pubkey) -> Unit,
    onDeletePubkey: (Pubkey) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_pubkey_list)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToGenerate) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.pubkey_generate)
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

                uiState.error != null -> {
                    Text(
                        text = stringResource(R.string.error_message, uiState.error ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.pubkeys.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.empty_pubkeys_message),
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
                            items = uiState.pubkeys,
                            key = { it.id }
                        ) { pubkey ->
                            PubkeyListItem(
                                pubkey = pubkey,
                                onDelete = { onDeletePubkey(pubkey) },
                                onClick = { onNavigateToEdit(pubkey) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PubkeyListItem(
    pubkey: Pubkey,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = pubkey.nickname,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.pubkey_type_label,
                    pubkey.type
                )
            )
        },
        leadingContent = {
            Icon(
                imageVector = if (pubkey.encrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = if (pubkey.encrypted) stringResource(R.string.pubkey_encrypted_description) else stringResource(
                    R.string.pubkey_not_encrypted_description
                ),
                tint = if (pubkey.encrypted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
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
                        text = { Text(stringResource(R.string.pubkey_delete)) },
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
        modifier = modifier.clickable { onClick() }
    )
    HorizontalDivider()
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenEmptyPreview() {
    ConnectBotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = emptyList(),
                isLoading = false
            ),
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenLoadingPreview() {
    ConnectBotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = emptyList(),
                isLoading = true
            ),
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenErrorPreview() {
    ConnectBotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = emptyList(),
                isLoading = false,
                error = "Failed to load SSH keys from database"
            ),
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenPopulatedPreview() {
    ConnectBotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = listOf(
                    Pubkey(
                        id = 1,
                        nickname = "work-laptop",
                        type = "RSA",
                        encrypted = true,
                        startup = true,
                        confirmation = false,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0),
                    ),
                    Pubkey(
                        id = 2,
                        nickname = "home-server",
                        type = "Ed25519",
                        encrypted = false,
                        startup = false,
                        confirmation = true,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0),
                    ),
                    Pubkey(
                        id = 3,
                        nickname = "github-key",
                        type = "ECDSA",
                        encrypted = true,
                        startup = false,
                        confirmation = false,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0),
                    )
                ),
                isLoading = false
            ),
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {}
        )
    }
}
