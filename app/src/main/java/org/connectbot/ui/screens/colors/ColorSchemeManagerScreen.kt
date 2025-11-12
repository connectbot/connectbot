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

package org.connectbot.ui.screens.colors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.ColorScheme

/**
 * Screen for managing color schemes (create, duplicate, delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSchemeManagerScreen(
    viewModel: ColorSchemeManagerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPaletteEditor: (Int) -> Unit = {},
    onExportScheme: (Int) -> Unit = {},
    onImportScheme: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_scheme_manager)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onImportScheme) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = stringResource(R.string.button_import_scheme)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showNewSchemeDialog() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.button_new_scheme))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.button_close))
                        }
                    }
                }
                uiState.schemes.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.empty_custom_schemes),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 88.dp // Extra padding to avoid FAB overlap
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.schemes) { scheme ->
                            SchemeItem(
                                scheme = scheme,
                                onEditPalette = { onNavigateToPaletteEditor(scheme.id) },
                                onExport = { onExportScheme(scheme.id) },
                                onRename = {
                                    if (!scheme.isBuiltIn) {
                                        viewModel.selectScheme(scheme.id)
                                        viewModel.showRenameDialog()
                                    }
                                },
                                onDelete = {
                                    if (!scheme.isBuiltIn) {
                                        viewModel.selectScheme(scheme.id)
                                        viewModel.showDeleteDialog()
                                    }
                                },
                                onDuplicate = {
                                    viewModel.selectScheme(scheme.id)
                                    viewModel.showNewSchemeDialog()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // New Scheme Dialog
    if (uiState.showNewSchemeDialog) {
        val selectedScheme = uiState.selectedSchemeId?.let { id ->
            uiState.schemes.find { it.id == id }
        }
        NewSchemeDialog(
            availableSchemes = uiState.schemes,
            preselectedSchemeId = uiState.selectedSchemeId,
            suggestedName = selectedScheme?.let { "Copy of ${it.name}" },
            error = uiState.dialogError,
            onConfirm = { name, description, baseSchemeId ->
                viewModel.createNewScheme(name, description, baseSchemeId)
            },
            onDismiss = { viewModel.hideNewSchemeDialog() }
        )
    }

    // Rename Scheme Dialog
    if (uiState.showRenameDialog && uiState.selectedSchemeId != null) {
        val scheme = uiState.schemes.find { it.id == uiState.selectedSchemeId }
        if (scheme != null) {
            RenameSchemeDialog(
                currentName = scheme.name,
                currentDescription = scheme.description,
                error = uiState.dialogError,
                onConfirm = { newName, newDescription ->
                    viewModel.renameScheme(scheme.id, newName, newDescription)
                },
                onDismiss = { viewModel.hideRenameDialog() }
            )
        }
    }

    // Delete Confirmation Dialog
    if (uiState.showDeleteDialog && uiState.selectedSchemeId != null) {
        val scheme = uiState.schemes.find { it.id == uiState.selectedSchemeId }
        if (scheme != null) {
            DeleteSchemeDialog(
                schemeName = scheme.name,
                onConfirm = { viewModel.deleteScheme(scheme.id) },
                onDismiss = { viewModel.hideDeleteDialog() }
            )
        }
    }
}

/**
 * Individual scheme item in the list.
 */
@Composable
private fun SchemeItem(
    scheme: ColorScheme,
    onEditPalette: () -> Unit,
    onExport: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = scheme.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (scheme.description.isNotEmpty()) {
                    Text(
                        text = scheme.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (scheme.isBuiltIn) {
                        stringResource(R.string.label_built_in_scheme)
                    } else {
                        stringResource(R.string.label_custom_scheme)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (scheme.isBuiltIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }

            Row {
                // For custom schemes: show Edit button
                if (!scheme.isBuiltIn) {
                    IconButton(onClick = onRename) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.button_rename_scheme),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Overflow menu (all other actions)
                Box {
                    var showMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.button_more_options)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Edit Palette
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.button_edit_palette)) },
                            onClick = {
                                showMenu = false
                                onEditPalette()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = null
                                )
                            }
                        )

                        // Export
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.button_export_scheme)) },
                            onClick = {
                                showMenu = false
                                onExport()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.FileDownload,
                                    contentDescription = null
                                )
                            }
                        )

                        // Duplicate
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.button_duplicate_scheme)) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null
                                )
                            }
                        )

                        // Delete (only for custom schemes)
                        if (!scheme.isBuiltIn) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.button_delete_scheme)) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for creating a new color scheme.
 */
@Composable
private fun NewSchemeDialog(
    availableSchemes: List<ColorScheme>,
    preselectedSchemeId: Int? = null,
    suggestedName: String? = null,
    error: String?,
    onConfirm: (name: String, description: String, baseSchemeId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(suggestedName ?: "") }
    var description by remember { mutableStateOf("") }
    var selectedBaseSchemeId by remember {
        mutableStateOf(preselectedSchemeId ?: ColorScheme.DEFAULT_SCHEME_ID)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_new_scheme)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_scheme_name)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.label_scheme_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Base scheme:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Simple base scheme selector (could be enhanced with dropdown)
                Column {
                    availableSchemes.take(5).forEach { scheme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedBaseSchemeId = scheme.id }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedBaseSchemeId == scheme.id,
                                onClick = { selectedBaseSchemeId = scheme.id }
                            )
                            Text(
                                text = scheme.name,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description, selectedBaseSchemeId) }
            ) {
                Text(stringResource(R.string.button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

/**
 * Dialog for renaming a color scheme.
 */
@Composable
private fun RenameSchemeDialog(
    currentName: String,
    currentDescription: String,
    error: String?,
    onConfirm: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var description by remember { mutableStateOf(currentDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_rename_scheme)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_scheme_name)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.label_scheme_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description) }
            ) {
                Text(stringResource(R.string.button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

/**
 * Dialog for confirming scheme deletion.
 */
@Composable
private fun DeleteSchemeDialog(
    schemeName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_delete_scheme)) },
        text = {
            Text(stringResource(R.string.dialog_message_delete_scheme, schemeName))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.button_delete_scheme),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
