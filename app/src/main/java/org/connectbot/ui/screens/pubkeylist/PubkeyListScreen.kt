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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.Pubkey
import org.connectbot.ui.LocalTerminalManager
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
    val terminalManager = LocalTerminalManager.current
    val viewModel = remember { PubkeyListViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Set TerminalManager in ViewModel
    LaunchedEffect(terminalManager) {
        viewModel.terminalManager = terminalManager
    }

    // Show snackbar for errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // File picker for importing keys
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importKeyFromUri(it) }
    }

    PubkeyListScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToGenerate = onNavigateToGenerate,
        onNavigateToEdit = onNavigateToEdit,
        onDeletePubkey = viewModel::deletePubkey,
        onToggleKeyLoaded = viewModel::toggleKeyLoaded,
        onCopyPublicKey = viewModel::copyPublicKey,
        onCopyPrivateKey = viewModel::copyPrivateKey,
        onImportKey = {
            filePickerLauncher.launch(arrayOf("*/*"))
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyListScreenContent(
    uiState: PubkeyListUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateToGenerate: () -> Unit,
    onNavigateToEdit: (Pubkey) -> Unit,
    onDeletePubkey: (Pubkey) -> Unit,
    onToggleKeyLoaded: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPublicKey: (Pubkey) -> Unit,
    onCopyPrivateKey: (Pubkey) -> Unit,
    onImportKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_pubkey_list)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.button_more_options))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_import_existing)) },
                            onClick = {
                                showMenu = false
                                onImportKey()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FileOpen, contentDescription = null)
                            }
                        )
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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
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
                                isLoaded = uiState.loadedKeyNicknames.contains(pubkey.nickname),
                                onDelete = { onDeletePubkey(pubkey) },
                                onToggleLoaded = { onToggleKeyLoaded(pubkey, it) },
                                onCopyPublicKey = { onCopyPublicKey(pubkey) },
                                onCopyPrivateKey = { onCopyPrivateKey(pubkey) },
                                onEdit = { onNavigateToEdit(pubkey) },
                                onClick = { onToggleKeyLoaded(pubkey, it) }
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
    isLoaded: Boolean,
    onDelete: () -> Unit,
    onToggleLoaded: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPublicKey: () -> Unit,
    onCopyPrivateKey: () -> Unit,
    onEdit: () -> Unit,
    onClick: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var passwordCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

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
            val icon = when {
                pubkey.encrypted -> Icons.Outlined.Lock
                else -> Icons.Outlined.LockOpen
            }

            val modifier = when {
                isLoaded -> Modifier
                    .padding(2.dp)
                    .border(
                        width = 2.dp, // Border thickness
                        color = Color.Green, // Border color
                        shape = CircleShape // Makes the border a circle
                    )
                    .clip(CircleShape)
                    .padding(4.dp)
                else -> Modifier.padding(2.dp).clip(CircleShape).padding(4.dp)
            }

            Box(
                modifier = modifier
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (pubkey.encrypted) {
                        stringResource(R.string.pubkey_encrypted_description)
                    } else {
                        stringResource(R.string.pubkey_not_encrypted_description)
                    },
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
                    // Edit key
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.list_pubkey_edit))
                        },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null)
                        }
                    )

                    // Copy public key
                    val isImported = pubkey.type == "IMPORTED"
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_copy_public)) },
                        onClick = {
                            showMenu = false
                            onCopyPublicKey()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null)
                        },
                        enabled = !isImported
                    )

                    // Copy private key
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_copy_private)) },
                        onClick = {
                            showMenu = false
                            onCopyPrivateKey()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null)
                        },
                        enabled = !pubkey.encrypted || isImported
                    )

                    // Delete
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_delete)) },
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
        modifier = modifier.clickable {
            onClick { targetPubkey, callback ->
                // Show password dialog if needed
                passwordCallback = callback
                showPasswordDialog = true
            }
        }
    )
    HorizontalDivider()

    // Password dialog for unlocking key
    if (showPasswordDialog && passwordCallback != null) {
        PubkeyPasswordDialog(
            pubkey = pubkey,
            onDismiss = {
                showPasswordDialog = false
                passwordCallback = null
            },
            onPasswordProvided = { password ->
                passwordCallback?.invoke(password)
                showPasswordDialog = false
                passwordCallback = null
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        PubkeyDeleteDialog(
            pubkey = pubkey,
            onDismiss = {
                showDeleteDialog = false
            },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            }
        )
    }
}

@Composable
private fun PubkeyPasswordDialog(
    pubkey: Pubkey,
    onDismiss: () -> Unit,
    onPasswordProvided: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.pubkey_unlock)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pubkey_unlock_message, pubkey.nickname),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.prompt_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPasswordProvided(password) }
            ) {
                Text(stringResource(R.string.pubkey_unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun PubkeyDeleteDialog(
    pubkey: Pubkey,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.pubkey_delete)) },
        text = {
            Text(stringResource(R.string.delete_message, pubkey.nickname))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.delete_pos))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_neg))
            }
        }
    )
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
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoaded = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKey = {},
            onImportKey = {}
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
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoaded = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKey = {},
            onImportKey = {}
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
                isLoading = false,
                loadedKeyNicknames = setOf("home-server")
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoaded = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKey = {},
            onImportKey = {}
        )
    }
}
