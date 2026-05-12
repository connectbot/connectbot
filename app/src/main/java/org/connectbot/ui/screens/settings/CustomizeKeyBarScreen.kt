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

package org.connectbot.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.util.keybar.BuiltinKeyId
import org.connectbot.util.keybar.KeyEntry
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Editor screen for the on-screen key bar.
 *
 * Owns dialog state locally (add-macro, edit-macro at index, reset
 * confirmation). The configuration itself lives in
 * [SettingsViewModel.uiState.keyBarConfig], so mutations propagate
 * via the shared [org.connectbot.util.keybar.KeyBarConfigRepository]
 * StateFlow and update any open terminal session immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeKeyBarScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsState()

    var showAddMacro by remember { mutableStateOf(false) }
    var editingMacroIndex by remember { mutableStateOf<Int?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keybar_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            KeyBarEntryList(
                entries = ui.keyBarConfig,
                onMove = viewModel::moveKeyBarEntry,
                onToggleVisible = viewModel::setBuiltinVisible,
                onEditMacro = { editingMacroIndex = it },
                onDeleteMacro = viewModel::deleteKeyBarEntry,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { showAddMacro = true },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.keybar_add_custom)) }
                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.keybar_reset)) }
            }
        }
    }

    if (showAddMacro) {
        MacroDialog(
            isEdit = false,
            onDismiss = { showAddMacro = false },
            onSave = { label, text ->
                viewModel.addMacro(label, text)
                showAddMacro = false
            },
        )
    }
    editingMacroIndex?.let { idx ->
        val entry = ui.keyBarConfig.getOrNull(idx) as? KeyEntry.Macro
        if (entry == null) {
            editingMacroIndex = null
        } else {
            MacroDialog(
                isEdit = true,
                initialLabel = entry.label,
                initialText = entry.text,
                onDismiss = { editingMacroIndex = null },
                onSave = { label, text ->
                    viewModel.updateMacro(idx, label, text)
                    editingMacroIndex = null
                },
            )
        }
    }
    if (showResetConfirm) {
        ResetConfirmDialog(
            onDismiss = { showResetConfirm = false },
            onConfirm = {
                viewModel.resetKeyBar()
                showResetConfirm = false
            },
        )
    }
}

@Composable
private fun KeyBarEntryList(
    entries: List<KeyEntry>,
    onMove: (Int, Int) -> Unit,
    onToggleVisible: (index: Int, visible: Boolean) -> Unit,
    onEditMacro: (Int) -> Unit,
    onDeleteMacro: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMove(from.index, to.index)
    }
    LazyColumn(state = lazyListState, modifier = modifier) {
        itemsIndexed(
            items = entries,
            key = { _, entry -> entryKey(entry) },
        ) { index, entry ->
            ReorderableItem(reorderState, key = entryKey(entry)) { _ ->
                KeyBarEntryRow(
                    entry = entry,
                    onToggleVisible = { v -> onToggleVisible(index, v) },
                    onEditMacro = { onEditMacro(index) },
                    onDeleteMacro = { onDeleteMacro(index) },
                    onMoveUp = if (index > 0) ({ onMove(index, index - 1) }) else null,
                    onMoveDown = if (index < entries.size - 1) ({ onMove(index, index + 1) }) else null,
                )
            }
        }
    }
}

private fun entryKey(entry: KeyEntry): String = when (entry) {
    is KeyEntry.Builtin -> "b:${entry.id}"
    is KeyEntry.Macro -> "m:${entry.id}"
}

@Composable
private fun ReorderableCollectionItemScope.KeyBarEntryRow(
    entry: KeyEntry,
    onToggleVisible: (Boolean) -> Unit,
    onEditMacro: () -> Unit,
    onDeleteMacro: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val moveUpLabel = stringResource(R.string.keybar_a11y_move_up)
    val moveDownLabel = stringResource(R.string.keybar_a11y_move_down)
    val dragHandleLabel = stringResource(R.string.keybar_a11y_drag_handle)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics {
                customActions = buildList {
                    onMoveUp?.let {
                        add(CustomAccessibilityAction(moveUpLabel) { it(); true })
                    }
                    onMoveDown?.let {
                        add(CustomAccessibilityAction(moveDownLabel) { it(); true })
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = dragHandleLabel,
            modifier = Modifier
                .size(24.dp)
                .draggableHandle(),
        )
        when (entry) {
            is KeyEntry.Builtin -> {
                Checkbox(checked = entry.visible, onCheckedChange = onToggleVisible)
                Text(text = stringResource(builtinLabelRes(entry.id)), modifier = Modifier.weight(1f))
            }
            is KeyEntry.Macro -> {
                Box(modifier = Modifier.size(48.dp))  // align with checkbox column
                Text(text = entry.label, modifier = Modifier.weight(1f))
                IconButton(onClick = onEditMacro) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.keybar_a11y_edit_macro),
                    )
                }
                IconButton(onClick = onDeleteMacro) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.keybar_a11y_delete_macro),
                    )
                }
            }
        }
    }
}

/**
 * Single source of truth for built-in key labels. Reused by the
 * runtime [org.connectbot.ui.components.TerminalKeyboard] in a
 * later bundle.
 */
internal fun builtinLabelRes(id: BuiltinKeyId): Int = when (id) {
    BuiltinKeyId.CTRL -> R.string.button_key_ctrl
    BuiltinKeyId.ALT -> R.string.button_key_alt
    BuiltinKeyId.SHIFT -> R.string.button_key_shift
    BuiltinKeyId.ESC -> R.string.button_key_esc
    BuiltinKeyId.TAB -> R.string.button_key_tab
    BuiltinKeyId.ENTER -> R.string.button_key_enter
    BuiltinKeyId.BACKSPACE -> R.string.button_key_backspace
    BuiltinKeyId.DELETE -> R.string.button_key_delete
    BuiltinKeyId.INSERT -> R.string.button_key_insert
    BuiltinKeyId.UP -> R.string.button_key_up
    BuiltinKeyId.DOWN -> R.string.button_key_down
    BuiltinKeyId.LEFT -> R.string.button_key_left
    BuiltinKeyId.RIGHT -> R.string.button_key_right
    BuiltinKeyId.HOME -> R.string.button_key_home
    BuiltinKeyId.END -> R.string.button_key_end
    BuiltinKeyId.PG_UP -> R.string.button_key_pgup
    BuiltinKeyId.PG_DN -> R.string.button_key_pgdn
    BuiltinKeyId.F1 -> R.string.button_key_f1
    BuiltinKeyId.F2 -> R.string.button_key_f2
    BuiltinKeyId.F3 -> R.string.button_key_f3
    BuiltinKeyId.F4 -> R.string.button_key_f4
    BuiltinKeyId.F5 -> R.string.button_key_f5
    BuiltinKeyId.F6 -> R.string.button_key_f6
    BuiltinKeyId.F7 -> R.string.button_key_f7
    BuiltinKeyId.F8 -> R.string.button_key_f8
    BuiltinKeyId.F9 -> R.string.button_key_f9
    BuiltinKeyId.F10 -> R.string.button_key_f10
    BuiltinKeyId.F11 -> R.string.button_key_f11
    BuiltinKeyId.F12 -> R.string.button_key_f12
}

@Composable
private fun ResetConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keybar_reset_confirm_title)) },
        text = { Text(stringResource(R.string.keybar_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
