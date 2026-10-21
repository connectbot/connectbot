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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardConfiguration
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardDraft
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayers
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.terminal.TerminalKeyCatalog
import org.connectbot.ui.components.EditorScaffold
import org.connectbot.ui.components.KeyboardGridButton
import org.connectbot.ui.components.TerminalKeyButtonDialog
import org.connectbot.ui.components.keyboardLabel
import org.connectbot.util.TerminalKeyModifiers
import java.util.UUID

/** A key transaction: saving merges changes into the enclosing layout draft. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyboardKeyEditor(
    initial: KeyboardDraft,
    itemId: UUID,
    config: KeyboardConfiguration,
    library: KeyboardLibraryState,
    isNew: Boolean,
    onDone: (KeyboardDraft) -> Unit,
    onBack: () -> Unit,
) {
    var draft by rememberSaveable(itemId) { mutableStateOf(initial) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var choosingAction by rememberSaveable { mutableStateOf(false) }
    var choosingKey by rememberSaveable { mutableStateOf(isNew && initial.items.first { it.id == itemId }.kind == "key") }
    var choosingMacro by rememberSaveable { mutableStateOf(isNew && initial.items.first { it.id == itemId }.kind == "macro") }
    var choosingTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val item = draft.items.first { it.id == itemId }
    val macros = (config.macros + draft.macros).associateBy { it.id }.values.toList()
    val assignMacro = stringResource(R.string.macro_assign)
    val valid = runCatching {
        KeyboardDefaults.validate(draft.layout, draft.items)
        require(item.kind != "macro" || macros.any { it.id == item.macroId }) { assignMacro }
    }
    val update: (KeyboardItem) -> Unit = { replacement -> draft = draft.copy(items = draft.items.map { if (it.id == itemId) replacement else it }) }
    BackHandler(onBack = onBack)
    EditorScaffold(
        title = stringResource(R.string.keyboard_edit_key_title),
        saveContentDescription = stringResource(R.string.keyboard_save),
        saveVisible = valid.isSuccess,
        confirmDiscard = isNew || draft != initial,
        isSaving = false,
        onNavigateBack = onBack,
        onSave = { onDone(draft) },
        snackbarHostState = remember { SnackbarHostState() },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    KeyboardGridButton(
                        layout = draft.layout, item = item, macros = macros, layer = item.layer,
                        onPress = {}, items = draft.items, editing = true, interactive = false,
                        modifier = Modifier.size(width = 96.dp, height = 56.dp),
                    )
                    Column {
                        Text(keyboardLabel(item, macros), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.keyboard_preview_label), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(stringResource(R.string.keyboard_key_save_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            KeyboardSettingsGroup(stringResource(R.string.keyboard_action_type), keyboardLabel(item, macros), initiallyExpanded = true) {
                KeyboardNavigationRow(
                    stringResource(
                        when (item.kind) {
                            "macro" -> R.string.keyboard_action_macro
                            "modifier" -> R.string.keyboard_action_modifier
                            "app" -> R.string.keyboard_action_app
                            "layer" -> R.string.keyboard_action_layer
                            else -> R.string.keyboard_key_type
                        },
                    ),
                    stringResource(R.string.keyboard_action_help),
                    { choosingAction = true },
                )
                when (item.kind) {
                    "key" -> OutlinedButton(onClick = { choosingKey = true }) { Text(stringResource(R.string.keyboard_change_shortcut)) }
                    "macro" -> KeyboardNavigationRow(stringResource(R.string.macro_assign), macros.find { it.id == item.macroId }?.name.orEmpty(), { choosingMacro = true })
                    "layer" -> KeyboardNavigationRow(stringResource(R.string.keyboard_layer_destination), if (item.target == "next") stringResource(R.string.keyboard_layer_next) else stringResource(R.string.keyboard_layer, item.target.toIntOrNull() ?: 0), { choosingTarget = "layer" })
                    else -> KeyboardNavigationRow(stringResource(R.string.keyboard_edit), keyboardLabel(item, macros), { choosingTarget = item.kind })
                }
            }
            KeyboardSettingsGroup(
                stringResource(R.string.keyboard_placement),
                stringResource(
                    if (draft.layout.fitToWidth) {
                        R.string.keyboard_mode_fit
                    } else if (item.pinned) {
                        R.string.keyboard_pin_choice
                    } else {
                        R.string.keyboard_mode_scrollable
                    },
                ),
                initiallyExpanded = true,
            ) {
                if (!draft.layout.fitToWidth) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(false, true).forEachIndexed { index, pinned ->
                            SegmentedButton(
                                selected = item.pinned == pinned,
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                onClick = {
                                    runCatching { pinKeyboardItem(draft.layout, draft.items, itemId, pinned) }
                                        .onSuccess {
                                            draft = draft.copy(items = it)
                                            error = null
                                        }
                                        .onFailure { error = it.message }
                                },
                            ) { Text(stringResource(if (pinned) R.string.keyboard_pin_choice else R.string.keyboard_mode_scrollable)) }
                        }
                    }
                    Text(stringResource(R.string.keyboard_pinned_hint), style = MaterialTheme.typography.bodySmall)
                }
                KeyboardSwitchRow(stringResource(R.string.keyboard_show_key), item.visible, { update(item.copy(visible = it)) })
            }
            KeyboardButtonBehavior(item, update)
            KeyboardButtonAppearance(item, update)
            KeyboardSettingsGroup(stringResource(R.string.keyboard_advanced), stringResource(R.string.keyboard_advanced_summary)) {
                Text(stringResource(R.string.keyboard_grid_advanced_help), style = MaterialTheme.typography.bodySmall)
                IntegerField(stringResource(R.string.keyboard_row), item.row, { update(item.copy(row = it)) })
                IntegerField(stringResource(R.string.keyboard_column), item.column, { update(item.copy(column = it)) })
                IntegerField(stringResource(R.string.keyboard_column_span), item.columnSpan, { update(item.copy(columnSpan = it)) })
            }
            (error ?: valid.exceptionOrNull()?.message)?.let { Text(stringResource(R.string.keyboard_error, it), color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { confirmRemove = true }) { Text(stringResource(R.string.keyboard_remove_button), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (choosingAction) {
        AlertDialog(onDismissRequest = { choosingAction = false }, title = { Text(stringResource(R.string.keyboard_action_type)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("key" to R.string.keyboard_key_type, "macro" to R.string.keyboard_action_macro, "modifier" to R.string.keyboard_action_modifier, "app" to R.string.keyboard_action_app, "layer" to R.string.keyboard_action_layer).forEach { (kind, label) ->
                    KeyboardNavigationRow(stringResource(label), "", {
                        choosingAction = false
                        when (kind) {
                            "key" -> choosingKey = true
                            "macro" -> choosingMacro = true
                            else -> choosingTarget = kind
                        }
                    })
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { choosingAction = false }) { Text(stringResource(R.string.keyboard_cancel)) } })
    }
    choosingTarget?.let { kind ->
        val targets = when (kind) {
            "layer" -> listOf("next") + (0 until KeyboardLayers.count).map(Int::toString)
            "modifier" -> KeyboardDefaults.modifiers
            else -> KeyboardDefaults.controls
        }
        AlertDialog(onDismissRequest = { choosingTarget = null }, title = { Text(stringResource(if (kind == "layer") R.string.keyboard_layer_destination else R.string.keyboard_action_type)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                targets.forEach { target ->
                    val label = if (kind == "layer") {
                        if (target == "next") stringResource(R.string.keyboard_layer_next) else stringResource(R.string.keyboard_layer, target.toInt())
                    } else {
                        keyboardLabel(item.copy(kind = kind, target = target), macros)
                    }
                    KeyboardNavigationRow(label, "", {
                        update(item.copy(kind = kind, target = target, macroId = null))
                        choosingTarget = null
                    })
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { choosingTarget = null }) { Text(stringResource(R.string.keyboard_cancel)) } })
    }
    if (choosingKey) {
        val shortcut = macros.find { it.id == item.macroId }?.let { runCatching { MacroFormat.decode(it.document).singleOrNull() as? MacroAction.Key }.getOrNull() }
        val modifiers = shortcut?.modifiers.orEmpty().fold(0) { value, name ->
            value or when (name) {
                "ctrl" -> TerminalKeyModifiers.CTRL
                "alt" -> TerminalKeyModifiers.ALT
                "shift" -> TerminalKeyModifiers.SHIFT
                else -> 0
            }
        }
        TerminalKeyButtonDialog(
            initialTarget = shortcut?.key ?: item.target.takeIf { TerminalKeyCatalog.byTarget(it) != null } ?: "enter",
            initialModifiers = modifiers,
            initialLabel = keyboardLabel(item, macros),
            onDismiss = { choosingKey = false },
            onConfirm = { key, mask, label ->
                val names = buildList {
                    if (mask and TerminalKeyModifiers.CTRL != 0) add("ctrl")
                    if (mask and TerminalKeyModifiers.ALT != 0) add("alt")
                    if (mask and TerminalKeyModifiers.SHIFT != 0) add("shift")
                }
                val macro = KeyboardMacro(name = label, document = MacroFormat.encode(listOf(MacroAction.Key(key = key.target, modifiers = names))))
                draft = draft.copy(macros = draft.macros + macro)
                update(item.copy(kind = "key", target = key.target, macroId = macro.id))
                choosingKey = false
            },
        )
    }
    if (choosingMacro) {
        KeyboardWorkflowDialog(onDismiss = { choosingMacro = false }) {
            KeyboardLibrary(onNavigateBack = { choosingMacro = false }, library = library, macrosOnly = true, pendingMacros = macros, currentReferenceId = itemId, onSelectMacro = { macro ->
                draft = draft.copy(macros = draft.macros.filterNot { it.id == macro.id } + macro)
                update(item.copy(kind = "macro", target = "", macroId = macro.id))
                choosingMacro = false
            })
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.keyboard_remove_button)) },
            text = { Text(stringResource(R.string.keyboard_remove_key_message)) },
            confirmButton = { TextButton(onClick = { onDone(draft.copy(items = draft.items.filterNot { it.id == itemId })) }) { Text(stringResource(R.string.keyboard_remove_button), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.keyboard_cancel)) } },
        )
    }
}
