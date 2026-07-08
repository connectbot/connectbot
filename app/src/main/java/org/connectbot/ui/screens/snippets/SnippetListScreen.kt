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

package org.connectbot.ui.screens.snippets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Snippet

/**
 * Management screen for the snippet library. Lists all snippets with tag
 * filtering, and supports creating, editing, and deleting snippets via
 * dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetListScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SnippetListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.snippet_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openEditor(Snippet()) }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.snippet_list_create),
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (uiState.allTags.isNotEmpty()) {
                TagFilterRow(
                    tags = uiState.allTags,
                    selectedTag = uiState.selectedTag,
                    onSelectTag = { viewModel.selectTag(it) },
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (uiState.snippets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.snippet_list_empty_message),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(uiState.filteredSnippets, key = { it.id }) { snippet ->
                            SnippetListItem(
                                snippet = snippet,
                                hostNickname = uiState.hosts.firstOrNull { it.id == snippet.hostId }?.nickname,
                                onClick = { viewModel.openEditor(snippet) },
                                onDelete = { viewModel.showDeleteDialog(snippet) },
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.editorSnippet?.let { snippet ->
        SnippetEditorDialog(
            snippet = snippet,
            hosts = uiState.hosts,
            onDismiss = { viewModel.closeEditor() },
            onSave = { viewModel.saveSnippet(it) },
        )
    }

    uiState.showDeleteDialog?.let { snippet ->
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text(stringResource(R.string.snippet_delete_dialog_title)) },
            text = { Text(stringResource(R.string.snippet_delete_dialog_message, snippet.name)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSnippet(snippet) },
                ) {
                    Text(stringResource(R.string.snippet_delete_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }) {
                    Text(stringResource(R.string.button_cancel))
                }
            },
        )
    }
}

@Composable
private fun TagFilterRow(
    tags: List<String>,
    selectedTag: String?,
    onSelectTag: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedTag == null,
            onClick = { onSelectTag(null) },
            label = { Text(stringResource(R.string.snippet_tag_filter_all)) },
        )
        tags.forEach { tag ->
            FilterChip(
                selected = tag == selectedTag,
                onClick = { onSelectTag(if (tag == selectedTag) null else tag) },
                label = { Text(tag) },
            )
        }
    }
}

@Composable
private fun SnippetListItem(
    snippet: Snippet,
    hostNickname: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snippet.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = snippet.command,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                SnippetSummaryText(snippet = snippet, hostNickname = hostNickname)
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.button_more_options),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.snippet_delete_button)) },
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
        }
    }
}

@Composable
private fun SnippetSummaryText(snippet: Snippet, hostNickname: String?) {
    val parts = mutableListOf<String>()

    parts.add(hostNickname ?: stringResource(R.string.snippet_scope_global))

    if (snippet.tagList.isNotEmpty()) {
        parts.add(snippet.tagList.joinToString(", "))
    }

    Text(
        text = parts.joinToString(" | "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Editor dialog for creating or editing a snippet.
 */
@Composable
internal fun SnippetEditorDialog(
    snippet: Snippet,
    hosts: List<Host>,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Unit,
) {
    var name by remember { mutableStateOf(snippet.name) }
    var command by remember { mutableStateOf(snippet.command) }
    var tags by remember { mutableStateOf(snippet.tags) }
    var hostId by remember { mutableStateOf(snippet.hostId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (snippet.id == 0L) {
                        R.string.snippet_editor_create_title
                    } else {
                        R.string.snippet_editor_edit_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.snippet_editor_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.snippet_editor_command_label)) },
                    supportingText = { Text(stringResource(R.string.snippet_editor_command_hint)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.snippet_editor_tags_label)) },
                    supportingText = { Text(stringResource(R.string.snippet_editor_tags_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ScopeSelector(
                    hosts = hosts,
                    selectedHostId = hostId,
                    onSelectHostId = { hostId = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        snippet.copy(
                            name = name.trim(),
                            command = command,
                            tags = tags,
                            hostId = hostId,
                        ),
                    )
                },
                enabled = name.isNotBlank() && command.isNotBlank(),
            ) {
                Text(stringResource(R.string.snippet_save_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}

/**
 * Dropdown to pick the snippet scope: global or a specific host.
 */
@Composable
private fun ScopeSelector(
    hosts: List<Host>,
    selectedHostId: Long?,
    onSelectHostId: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val globalLabel = stringResource(R.string.snippet_scope_global)
    val selectedLabel = hosts.firstOrNull { it.id == selectedHostId }?.nickname ?: globalLabel

    Box {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(R.string.snippet_editor_scope_label)) },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(globalLabel) },
                onClick = {
                    expanded = false
                    onSelectHostId(null)
                },
            )
            hosts.forEach { host ->
                DropdownMenuItem(
                    text = { Text(host.nickname) },
                    onClick = {
                        expanded = false
                        onSelectHostId(host.id)
                    },
                )
            }
        }
    }
}
