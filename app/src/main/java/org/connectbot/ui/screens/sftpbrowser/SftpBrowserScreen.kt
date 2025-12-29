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

package org.connectbot.ui.screens.sftpbrowser

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.connectbot.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SftpBrowserScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: SftpBrowserViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }

    // Handle back navigation - go up directory or exit
    BackHandler(enabled = uiState.currentPath != "/" && uiState.isConnected) {
        viewModel.navigateUp()
    }

    // Disconnect when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
        }
    }

    // Show errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Pending download entry
    var pendingDownloadEntry by remember { mutableStateOf<SftpEntry?>(null) }

    // File picker for uploading
    val uploadFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Get filename from URI
            val filename = context.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: "uploaded_file"

            viewModel.uploadFile(selectedUri, filename)
        }
    }

    // File saver for downloading
    val downloadFileSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        if (uri != null && pendingDownloadEntry != null) {
            viewModel.downloadFile(pendingDownloadEntry!!, uri)
        }
        pendingDownloadEntry = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.sftp_title, uiState.host?.nickname ?: ""),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.isConnected) {
                            Text(
                                text = uiState.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_back)
                        )
                    }
                },
                actions = {
                    if (uiState.isConnected) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.sftp_refresh)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.isConnected) {
                FloatingActionButtonMenu(
                    expanded = fabExpanded,
                    button = {
                        ToggleFloatingActionButton(
                            checked = fabExpanded,
                            onCheckedChange = { fabExpanded = !fabExpanded }
                        ) {
                            Icon(
                                painter = rememberVectorPainter(
                                    if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                                ),
                                contentDescription = stringResource(R.string.sftp_actions),
                                modifier = Modifier.animateIcon({ checkedProgress })
                            )
                        }
                    }
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabExpanded = false
                            uploadFilePicker.launch(arrayOf("*/*"))
                        },
                        icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                        text = { Text(stringResource(R.string.sftp_upload_file)) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            fabExpanded = false
                            viewModel.showCreateFolderDialog()
                        },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        text = { Text(stringResource(R.string.sftp_create_folder)) }
                    )
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
                uiState.isConnecting -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.sftp_connecting, uiState.host?.hostname ?: ""),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                !uiState.isConnected && uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sftp_connection_failed, uiState.error ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { viewModel.connect() }) {
                            Text(stringResource(R.string.sftp_retry))
                        }
                    }
                }

                uiState.isConnected -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.entries.isEmpty() && !uiState.isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.sftp_empty_directory),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 88.dp)
                            ) {
                                items(
                                    items = uiState.entries,
                                    key = { it.fullPath }
                                ) { entry ->
                                    SftpEntryItem(
                                        entry = entry,
                                        onClick = {
                                            if (entry.isDirectory || entry.filename == "..") {
                                                if (entry.filename == "..") {
                                                    viewModel.navigateUp()
                                                } else {
                                                    viewModel.navigateTo(entry.fullPath)
                                                }
                                            } else {
                                                // Download on click for files
                                                pendingDownloadEntry = entry
                                                downloadFileSaver.launch(entry.filename)
                                            }
                                        },
                                        onDownload = {
                                            pendingDownloadEntry = entry
                                            downloadFileSaver.launch(entry.filename)
                                        },
                                        onDelete = {
                                            viewModel.showDeleteDialog(entry)
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Initial state - waiting to connect
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    // Dialogs
    if (uiState.showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { viewModel.dismissCreateFolderDialog() },
            onConfirm = { folderName -> viewModel.createFolder(folderName) }
        )
    }

    if (uiState.showDeleteDialog && uiState.entryToDelete != null) {
        DeleteConfirmDialog(
            entry = uiState.entryToDelete!!,
            onDismiss = { viewModel.dismissDeleteDialog() },
            onConfirm = { viewModel.deleteEntry() }
        )
    }

    if (uiState.transferProgress != null) {
        TransferProgressDialog(
            progress = uiState.transferProgress!!,
            onCancel = { viewModel.cancelTransfer() }
        )
    }

    if (uiState.showHostKeyDialog && uiState.hostKeyInfo != null) {
        HostKeyDialog(
            info = uiState.hostKeyInfo!!,
            onAccept = { viewModel.acceptHostKey() },
            onReject = { viewModel.rejectHostKey() }
        )
    }

    if (uiState.showPasswordDialog && uiState.passwordPrompt != null) {
        PasswordDialog(
            prompt = uiState.passwordPrompt!!,
            onSubmit = { viewModel.submitPassword(it) },
            onCancel = { viewModel.cancelPassword() }
        )
    }

    if (uiState.showKeyPassphraseDialog && uiState.keyPassphrasePrompt != null) {
        KeyPassphraseDialog(
            prompt = uiState.keyPassphrasePrompt!!,
            onSubmit = { viewModel.submitKeyPassphrase(it) },
            onCancel = { viewModel.cancelKeyPassphrase() }
        )
    }
}
