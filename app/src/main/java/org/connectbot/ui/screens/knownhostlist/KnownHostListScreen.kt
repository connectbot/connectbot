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

package org.connectbot.ui.screens.knownhostlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.entity.KnownHost
import org.connectbot.ui.PreviewScreen
import org.connectbot.ui.theme.ConnectBotTheme

internal object KnownHostListTestTags {
    fun itemRow(knownHostId: Long): String = "known_host_item_${knownHostId}_row"
    fun deleteButton(knownHostId: Long): String = "known_host_item_${knownHostId}_delete"
}

@Composable
fun KnownHostListScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnownHostListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.known_hosts_error)

    LaunchedEffect(uiState.hasError) {
        if (uiState.hasError) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.clearError()
        }
    }

    KnownHostListScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onDeleteKnownHost = viewModel::deleteKnownHost,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownHostListScreenContent(
    uiState: KnownHostListUiState,
    onNavigateBack: () -> Unit,
    onDeleteKnownHost: (KnownHost) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var pendingDelete by remember { mutableStateOf<KnownHostListItem?>(null) }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.known_host_delete_title)) },
            text = { Text(stringResource(R.string.known_host_delete_message, item.endpoint)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteKnownHost(item.knownHost)
                    },
                ) {
                    Text(stringResource(R.string.known_host_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.known_hosts_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                uiState.knownHosts.isEmpty() -> Text(
                    text = stringResource(R.string.known_hosts_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.knownHosts, key = { it.knownHost.id }) { item ->
                        KnownHostListItem(
                            item = item,
                            onDelete = { pendingDelete = item },
                            modifier = Modifier.testTag(KnownHostListTestTags.itemRow(item.knownHost.id)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KnownHostListItem(
    item: KnownHostListItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(item.endpoint) },
        supportingContent = {
            SelectionContainer {
                Text(
                    text = buildString {
                        append(stringResource(R.string.known_host_algorithm, item.knownHost.hostKeyAlgo))
                        append('\n')
                        append(item.fingerprint)
                    },
                )
            }
        },
        trailingContent = {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(KnownHostListTestTags.deleteButton(item.knownHost.id)),
            ) {
                Icon(Icons.Default.Delete, stringResource(R.string.known_host_delete))
            }
        },
        modifier = modifier,
    )
}

@PreviewScreen
@Composable
private fun KnownHostListScreenPreview() {
    ConnectBotTheme {
        KnownHostListScreenContent(
            uiState = KnownHostListUiState(
                knownHosts = listOf(
                    KnownHostListItem(
                        knownHost = KnownHost(
                            id = 1,
                            hostId = 1,
                            hostname = "example.com",
                            port = 22,
                            hostKeyAlgo = "ssh-ed25519",
                            hostKey = byteArrayOf(),
                        ),
                        fingerprint = "SHA256:exampleFingerprint",
                    ),
                ),
                isLoading = false,
            ),
            onNavigateBack = {},
            onDeleteKnownHost = {},
        )
    }
}
