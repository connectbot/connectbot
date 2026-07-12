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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.connectbot.R
import org.connectbot.keyboard.KeySpec
import org.connectbot.keyboard.KeyboardLayoutSpec
import org.connectbot.keyboard.stringResId
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.ui.components.TerminalKeyboardContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardLayoutEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KeyboardLayoutEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Sheet/dialog state.
    var addKeyRow by remember { mutableStateOf<Int?>(null) }
    var editTarget by remember { mutableStateOf<Triple<Int, Int, KeySpec>?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.name.ifBlank { stringResource(R.string.keyboard_layout_editor_title) }) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!uiState.isLoading && uiState.rows.isNotEmpty()) {
                // Live preview.
                TerminalKeyboardContent(
                    layout = KeyboardLayoutSpec(uiState.rows),
                    modifierState = ModifierState(ModifierLevel.OFF, ModifierLevel.OFF, ModifierLevel.OFF),
                    onKeyAction = {},
                    onInteraction = {},
                    onHideIme = {},
                    onShowIme = {},
                    onOpenTextInput = {},
                    onOpenSnippets = {},
                    onLongPress = {},
                    onScrollInProgressChange = {},
                    imeVisible = false,
                    playAnimation = false,
                    bumpyArrows = false,
                    modifier = Modifier.testTag("editor_preview"),
                )
                HorizontalDivider()
            }

            uiState.rows.forEachIndexed { rowIndex, keys ->
                Text(
                    text = stringResource(R.string.keyboard_layout_editor_row, rowIndex + 1),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
                )

                keys.forEachIndexed { keyIndex, key ->
                    KeyRow(
                        description = keyDescription(key),
                        canMoveUp = keyIndex > 0,
                        canMoveDown = keyIndex < keys.lastIndex,
                        showMoveToOtherRow = uiState.rows.size > 1,
                        onMoveUp = { viewModel.moveKey(rowIndex, keyIndex, -1) },
                        onMoveDown = { viewModel.moveKey(rowIndex, keyIndex, 1) },
                        onMoveToOtherRow = { viewModel.moveKeyToRow(rowIndex, keyIndex, if (rowIndex == 0) 1 else 0) },
                        onEdit = { editTarget = Triple(rowIndex, keyIndex, key) },
                        onRemove = { viewModel.removeKey(rowIndex, keyIndex) },
                    )
                }

                OutlinedButton(
                    onClick = { addKeyRow = rowIndex },
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("add_key_row_$rowIndex"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.keyboard_layout_editor_add_key))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (uiState.rows.size < 2) {
                TextButton(
                    onClick = { viewModel.addSecondRow() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.keyboard_layout_editor_add_row))
                }
            } else {
                TextButton(
                    onClick = { viewModel.removeSecondRow() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(stringResource(R.string.keyboard_layout_editor_remove_row))
                }
            }
        }
    }

    addKeyRow?.let { row ->
        KeyCatalogSheet(
            onDismiss = { addKeyRow = null },
            onPick = { key ->
                viewModel.addKey(row, key)
                addKeyRow = null
            },
        )
    }

    editTarget?.let { (rowIndex, keyIndex, key) ->
        KeyConfigDialog(
            initial = key,
            onConfirm = { updated ->
                viewModel.replaceKey(rowIndex, keyIndex, updated)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }
}

@Composable
private fun KeyRow(
    description: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showMoveToOtherRow: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToOtherRow: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.keyboard_layout_editor_move_up))
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.keyboard_layout_editor_move_down))
        }
        if (showMoveToOtherRow) {
            IconButton(onClick = onMoveToOtherRow) {
                Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.keyboard_layout_editor_move_row))
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.keyboard_layouts_edit))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.keyboard_layout_editor_remove_key))
        }
    }
}

/** Short human description of a key for the editor list. */
@Composable
private fun keyDescription(spec: KeySpec): String = when (spec) {
    is KeySpec.Special -> spec.label ?: spec.key.name

    is KeySpec.Modifier -> spec.label ?: spec.mod.name

    is KeySpec.Text -> {
        val enter = if (spec.sendEnter) " ⏎" else ""
        stringResource(R.string.keyboard_layout_editor_text_desc, spec.label ?: spec.text) + enter
    }

    is KeySpec.Combo -> spec.label ?: run {
        val ctrl = stringResource(R.string.button_key_ctrl)
        val alt = stringResource(R.string.button_key_alt)
        val shift = stringResource(R.string.button_key_shift)
        buildString {
            if (spec.ctrl) append(ctrl).append("+")
            if (spec.alt) append(alt).append("+")
            if (spec.shift) append(shift).append("+")
            append(spec.ch?.uppercaseChar()?.toString() ?: spec.special?.name.orEmpty())
        }
    }

    is KeySpec.FnGrid -> spec.label ?: stringResource(R.string.button_key_fn)

    is KeySpec.Tmux -> spec.label ?: stringResource(spec.action.stringResId)
}
