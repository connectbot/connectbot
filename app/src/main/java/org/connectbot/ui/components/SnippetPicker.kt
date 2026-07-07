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

package org.connectbot.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.connectbot.R
import org.connectbot.data.SnippetRepository
import org.connectbot.data.entity.Snippet
import org.connectbot.util.SnippetVariables
import javax.inject.Inject

@HiltViewModel
class SnippetPickerViewModel @Inject constructor(
    snippetRepository: SnippetRepository,
) : ViewModel() {
    val snippets: StateFlow<List<Snippet>> = snippetRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Bottom sheet listing the snippets available for the current host, with
 * text search across names, tags, and commands.
 *
 * Tapping a snippet runs it (sends the command followed by Enter); the
 * trailing keyboard icon types the command without running it. Snippets with
 * `${variable}` placeholders first prompt for values.
 *
 * @param hostId The current host's ID; host-scoped snippets of other hosts
 *   are hidden. Null shows only global snippets.
 * @param onSend Called with the final text to inject into the terminal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetPickerSheet(
    hostId: Long?,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SnippetPickerViewModel = hiltViewModel(),
) {
    val allSnippets by viewModel.snippets.collectAsState()
    var query by remember { mutableStateOf("") }
    var pendingVariables by remember { mutableStateOf<PendingSnippet?>(null) }

    val snippets = allSnippets.filter { snippet ->
        (snippet.hostId == null || snippet.hostId == hostId) && snippet.matches(query)
    }

    fun select(snippet: Snippet, run: Boolean) {
        val variables = SnippetVariables.extract(snippet.command)
        if (variables.isEmpty()) {
            onSend(if (run) snippet.command + "\n" else snippet.command)
            onDismiss()
        } else {
            pendingVariables = PendingSnippet(snippet, variables, run)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.snippet_picker_title),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.snippet_picker_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            if (snippets.isEmpty()) {
                Text(
                    text = stringResource(R.string.snippet_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(snippets, key = { it.id }) { snippet ->
                        SnippetPickerRow(
                            snippet = snippet,
                            onRun = { select(snippet, run = true) },
                            onInsert = { select(snippet, run = false) },
                        )
                    }
                }
            }
        }
    }

    pendingVariables?.let { pending ->
        SnippetVariablesDialog(
            pending = pending,
            onDismiss = { pendingVariables = null },
            onConfirm = { values ->
                val text = SnippetVariables.substitute(pending.snippet.command, values)
                onSend(if (pending.run) text + "\n" else text)
                pendingVariables = null
                onDismiss()
            },
        )
    }
}

private data class PendingSnippet(
    val snippet: Snippet,
    val variables: List<String>,
    val run: Boolean,
)

private fun Snippet.matches(query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    return name.contains(trimmed, ignoreCase = true) ||
        command.contains(trimmed, ignoreCase = true) ||
        tagList.any { it.contains(trimmed, ignoreCase = true) }
}

@Composable
private fun SnippetPickerRow(
    snippet: Snippet,
    onRun: () -> Unit,
    onInsert: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRun)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = snippet.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = snippet.command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (snippet.tagList.isNotEmpty()) {
                Text(
                    text = snippet.tagList.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(onClick = onInsert) {
            Icon(
                Icons.Outlined.Keyboard,
                contentDescription = stringResource(R.string.snippet_picker_insert),
            )
        }
    }
}

/**
 * Prompts for values for each `${variable}` placeholder before sending.
 */
@Composable
private fun SnippetVariablesDialog(
    pending: PendingSnippet,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, String>) -> Unit,
) {
    var values by remember { mutableStateOf(pending.variables.associateWith { "" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snippet_variables_dialog_title, pending.snippet.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pending.variables.forEach { variable ->
                    OutlinedTextField(
                        value = values[variable].orEmpty(),
                        onValueChange = { values = values + (variable to it) },
                        label = { Text(variable) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(values) }) {
                Text(
                    stringResource(
                        if (pending.run) R.string.snippet_run_button else R.string.snippet_insert_button,
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}
