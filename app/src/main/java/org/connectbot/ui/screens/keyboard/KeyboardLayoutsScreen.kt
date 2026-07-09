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

package org.connectbot.ui.screens.keyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.keyboard.DefaultKeyboardLayouts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardLayoutsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KeyboardLayoutsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var renameTarget by remember { mutableStateOf<KeyboardLayoutListItem?>(null) }
    var renameFailed by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<KeyboardLayoutListItem?>(null) }
    var creatingLayout by remember { mutableStateOf(false) }
    val newLayoutName = stringResource(R.string.keyboard_layouts_new)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keyboard_layouts_title)) },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Guard against a double-tap creating two layouts.
                    if (!creatingLayout) {
                        creatingLayout = true
                        scope.launch {
                            try {
                                onNavigateToEditor(viewModel.createLayout(newLayoutName))
                            } finally {
                                creatingLayout = false
                            }
                        }
                    }
                },
                modifier = Modifier.testTag("new_layout_fab"),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.keyboard_layouts_new))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(uiState.items, key = { it.id }) { item ->
                val itemName = item.displayName()
                val duplicateName = stringResource(R.string.keyboard_layouts_duplicate_name, itemName)
                KeyboardLayoutRow(
                    item = item,
                    name = itemName,
                    isDefault = item.id == uiState.defaultLayoutId,
                    onSetDefault = { viewModel.setDefault(item.id) },
                    onEdit = { onNavigateToEditor(item.id) },
                    onDuplicate = { scope.launch { onNavigateToEditor(viewModel.duplicate(item.id, duplicateName)) } },
                    onRename = {
                        renameFailed = false
                        renameTarget = item
                    },
                    onDelete = { deleteTarget = item },
                )
            }
        }
    }

    renameTarget?.let { target ->
        val targetName = target.displayName()
        LayoutNameDialog(
            title = stringResource(R.string.keyboard_layouts_rename),
            initialName = targetName,
            errorText = if (renameFailed) stringResource(R.string.keyboard_layouts_name_taken) else null,
            onConfirm = { name ->
                scope.launch {
                    if (viewModel.rename(target.id, name)) {
                        renameTarget = null
                    } else {
                        // Keep the dialog open and flag the conflicting name.
                        renameFailed = true
                    }
                }
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        val targetName = target.displayName()
        var hostsUsing by remember(target.id) { mutableIntStateOf(0) }
        LaunchedEffect(target.id) {
            hostsUsing = viewModel.hostsUsing(target.id)
        }
        DeleteLayoutDialog(
            name = targetName,
            hostsUsing = hostsUsing,
            onConfirm = {
                viewModel.delete(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun KeyboardLayoutRow(
    item: KeyboardLayoutListItem,
    name: String,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(name) },
        supportingContent = if (item.isBuiltIn) {
            { Text(stringResource(R.string.keyboard_layouts_builtin_label)) }
        } else {
            null
        },
        leadingContent = {
            RadioButton(
                selected = isDefault,
                onClick = onSetDefault,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.keyboard_layouts_actions),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (!item.isBuiltIn) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.keyboard_layouts_edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.keyboard_layouts_rename)) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.keyboard_layouts_duplicate)) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        },
                    )
                    if (!item.isBuiltIn) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.keyboard_layouts_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (item.isBuiltIn) onSetDefault() else onEdit() }
            .testTag("layout_row_${item.id}"),
    )
}

@Composable
private fun KeyboardLayoutListItem.displayName(): String = when (id) {
    DefaultKeyboardLayouts.DEFAULT_ID -> stringResource(R.string.keyboard_layout_name_default)
    DefaultKeyboardLayouts.CLASSIC_ID -> stringResource(R.string.keyboard_layout_name_classic)
    else -> name.orEmpty()
}
