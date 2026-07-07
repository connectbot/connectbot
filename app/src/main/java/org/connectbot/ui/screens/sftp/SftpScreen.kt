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

package org.connectbot.ui.screens.sftp

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.transport.sftp.SftpFile
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.PreviewScreen
import org.connectbot.ui.theme.ConnectBotTheme

@Composable
fun SftpScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SftpViewModel = hiltViewModel(),
) {
    val terminalManager = LocalTerminalManager.current

    LaunchedEffect(terminalManager) {
        terminalManager?.let { viewModel.setTerminalManager(it) }
    }

    val uiState by viewModel.uiState.collectAsState()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        viewModel.onDownloadDestinationChosen(uri)
    }

    LaunchedEffect(uiState.pendingDownload) {
        uiState.pendingDownload?.let { createDocumentLauncher.launch(it.name) }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.requestUpload(it) }
    }

    SftpScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNavigateUp = viewModel::navigateUp,
        onRefresh = viewModel::refresh,
        onEntryClick = viewModel::openEntry,
        onUploadClick = { openDocumentLauncher.launch(arrayOf("*/*")) },
        onCreateFolderClick = viewModel::requestCreateFolder,
        onDownloadRequest = viewModel::requestDownload,
        onRenameRequest = viewModel::requestRename,
        onDeleteRequest = viewModel::requestDelete,
        onConfirmUploadOverwrite = viewModel::confirmUploadOverwrite,
        onConfirmDelete = viewModel::confirmDelete,
        onConfirmRename = viewModel::confirmRename,
        onConfirmCreateFolder = viewModel::confirmCreateFolder,
        onDismissDialog = viewModel::dismissDialog,
        onCancelTransfer = viewModel::cancelTransfer,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SftpScreenContent(
    uiState: SftpUiState,
    onNavigateBack: () -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onEntryClick: (SftpFile) -> Unit,
    onUploadClick: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onDownloadRequest: (SftpFile) -> Unit,
    onRenameRequest: (SftpFile) -> Unit,
    onDeleteRequest: (SftpFile) -> Unit,
    onConfirmUploadOverwrite: () -> Unit,
    onConfirmDelete: () -> Unit,
    onConfirmRename: (String) -> Unit,
    onConfirmCreateFolder: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onCancelTransfer: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnDismissMessage by rememberUpdatedState(onDismissMessage)

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message = message, withDismissAction = true)
            currentOnDismissMessage()
        }
    }

    // Navigate up through remote folders on system back until reaching the root.
    BackHandler(enabled = uiState.isConnected && uiState.parentPath != null && uiState.transfer == null) {
        onNavigateUp()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.hostNickname.ifEmpty { stringResource(R.string.sftp_title) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (uiState.currentPath.isNotEmpty()) {
                            Text(
                                text = uiState.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onCreateFolderClick,
                        enabled = uiState.isConnected && !uiState.isLoading,
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = stringResource(R.string.sftp_new_folder),
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = uiState.isConnected && !uiState.isLoading,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.sftp_refresh),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.isConnected) {
                FloatingActionButton(
                    onClick = onUploadClick,
                    modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = stringResource(R.string.sftp_upload),
                    )
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                !uiState.isConnected && !uiState.isLoading -> {
                    Text(
                        text = stringResource(R.string.sftp_not_connected),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                    )
                }

                uiState.isLoading && uiState.currentPath.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (uiState.isLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (uiState.parentPath != null) {
                                item(key = "..") {
                                    ListItem(
                                        headlineContent = { Text("..") },
                                        supportingContent = {
                                            Text(stringResource(R.string.sftp_parent_folder))
                                        },
                                        leadingContent = {
                                            Icon(Icons.Default.ArrowUpward, contentDescription = null)
                                        },
                                        modifier = Modifier.clickable { onNavigateUp() },
                                    )
                                    HorizontalDivider()
                                }
                            }

                            items(uiState.entries, key = { it.path }) { entry ->
                                SftpFileRow(
                                    file = entry,
                                    onClick = { onEntryClick(entry) },
                                    onDownload = { onDownloadRequest(entry) },
                                    onRename = { onRenameRequest(entry) },
                                    onDelete = { onDeleteRequest(entry) },
                                )
                                HorizontalDivider()
                            }

                            if (uiState.entries.isEmpty() && !uiState.isLoading) {
                                item(key = "empty") {
                                    Text(
                                        text = stringResource(R.string.sftp_empty_folder),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.transfer?.let { transfer ->
        SftpTransferDialog(transfer = transfer, onCancel = onCancelTransfer)
    }

    when (val dialog = uiState.dialog) {
        is SftpDialog.ConfirmUploadOverwrite -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text(stringResource(R.string.sftp_overwrite_title)) },
                text = { Text(stringResource(R.string.sftp_overwrite_message, dialog.fileName)) },
                confirmButton = {
                    TextButton(onClick = onConfirmUploadOverwrite) {
                        Text(stringResource(R.string.sftp_overwrite_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(R.string.button_cancel))
                    }
                },
            )
        }

        is SftpDialog.ConfirmDelete -> {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text(stringResource(R.string.sftp_delete)) },
                text = { Text(stringResource(R.string.sftp_delete_message, dialog.file.name)) },
                confirmButton = {
                    TextButton(onClick = onConfirmDelete) {
                        Text(stringResource(R.string.sftp_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text(stringResource(R.string.button_cancel))
                    }
                },
            )
        }

        is SftpDialog.Rename -> {
            SftpTextFieldDialog(
                title = stringResource(R.string.sftp_rename),
                label = stringResource(R.string.sftp_name_label),
                initialValue = dialog.file.name,
                confirmText = stringResource(R.string.sftp_rename),
                onConfirm = onConfirmRename,
                onDismiss = onDismissDialog,
            )
        }

        is SftpDialog.CreateFolder -> {
            SftpTextFieldDialog(
                title = stringResource(R.string.sftp_new_folder),
                label = stringResource(R.string.sftp_name_label),
                initialValue = "",
                confirmText = stringResource(R.string.sftp_create),
                onConfirm = onConfirmCreateFolder,
                onDismiss = onDismissDialog,
            )
        }

        null -> Unit
    }
}

@Composable
private fun SftpFileRow(
    file: SftpFile,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val supportingText = when {
        file.isDirectory -> stringResource(R.string.sftp_type_folder)

        file.isSymlink -> stringResource(R.string.sftp_type_link)

        else -> {
            val size = file.size?.let { Formatter.formatShortFileSize(context, it) }
            val date = file.modifiedTimeMillis?.let {
                DateUtils.getRelativeTimeSpanString(it).toString()
            }
            listOfNotNull(size, date).joinToString(" • ")
        }
    }

    ListItem(
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (supportingText.isNotEmpty()) {
            { Text(supportingText) }
        } else {
            null
        },
        leadingContent = {
            val icon = when {
                file.isDirectory -> Icons.Default.Folder
                file.isSymlink -> Icons.Default.Link
                else -> Icons.AutoMirrored.Filled.InsertDriveFile
            }
            Icon(icon, contentDescription = null)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.button_more_options),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    if (!file.isDirectory) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sftp_download)) },
                            onClick = {
                                showMenu = false
                                onDownload()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Download, contentDescription = null)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sftp_rename)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sftp_delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        },
                    )
                }
            }
        },
        modifier = modifier.clickable { onClick() },
    )
}

@Composable
private fun SftpTransferDialog(
    transfer: SftpTransfer,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        // Require the cancel button so a stray outside tap doesn't abort the transfer.
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    if (transfer.isUpload) R.string.sftp_uploading else R.string.sftp_downloading,
                    transfer.fileName,
                ),
            )
        },
        text = {
            Column {
                val totalBytes = transfer.totalBytes
                if (totalBytes != null && totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = {
                            (transfer.bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(
                            R.string.sftp_transfer_progress,
                            Formatter.formatShortFileSize(context, transfer.bytesTransferred),
                            Formatter.formatShortFileSize(context, totalBytes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = Formatter.formatShortFileSize(context, transfer.bytesTransferred),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}

@Composable
private fun SftpTextFieldDialog(
    title: String,
    label: String,
    initialValue: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}

@PreviewScreen
@Composable
private fun SftpScreenPreview() {
    ConnectBotTheme {
        SftpScreenContent(
            uiState = SftpUiState(
                isLoading = false,
                isConnected = true,
                hostNickname = "user@example.com",
                currentPath = "/home/user",
                parentPath = "/home",
                entries = listOf(
                    SftpFile(
                        name = "projects",
                        path = "/home/user/projects",
                        isDirectory = true,
                        isSymlink = false,
                        size = null,
                        modifiedTimeMillis = 1700000000000L,
                    ),
                    SftpFile(
                        name = "notes.txt",
                        path = "/home/user/notes.txt",
                        isDirectory = false,
                        isSymlink = false,
                        size = 4096L,
                        modifiedTimeMillis = 1700000000000L,
                    ),
                ),
            ),
            onNavigateBack = {},
            onNavigateUp = {},
            onRefresh = {},
            onEntryClick = {},
            onUploadClick = {},
            onCreateFolderClick = {},
            onDownloadRequest = {},
            onRenameRequest = {},
            onDeleteRequest = {},
            onConfirmUploadOverwrite = {},
            onConfirmDelete = {},
            onConfirmRename = {},
            onConfirmCreateFolder = {},
            onDismissDialog = {},
            onCancelTransfer = {},
            onDismissMessage = {},
        )
    }
}
