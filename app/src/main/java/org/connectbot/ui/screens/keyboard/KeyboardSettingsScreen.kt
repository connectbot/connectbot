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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardConfiguration
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.ui.components.KeyboardGrid
import org.connectbot.ui.components.keyboardLabel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSettingsScreen(onNavigateBack: () -> Unit, modifier: Modifier = Modifier, viewModel: KeyboardViewModel = hiltViewModel()) {
    val config by viewModel.configuration.collectAsState()
    val error by viewModel.error.collectAsState()
    var layout by remember { mutableStateOf<KeyboardLayout?>(null) }
    var items by remember { mutableStateOf(emptyList<KeyboardItem>()) }
    var macro by remember { mutableStateOf<KeyboardMacro?>(null) }
    var confirmation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val newLayoutName = stringResource(R.string.keyboard_new_layout)
    val newMacroName = stringResource(R.string.keyboard_new_macro)
    val copySuffix = stringResource(R.string.keyboard_copy_suffix)
    val back = {
        if (layout != null || macro != null) {
            layout = null
            macro = null
        } else {
            onNavigateBack()
        }
    }
    BackHandler(layout != null || macro != null) { back() }
    Scaffold(modifier = modifier, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.keyboard_settings_title)) }, navigationIcon = {
            TextButton(onClick = back) { Text(stringResource(R.string.keyboard_back)) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).padding(12.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(stringResource(R.string.keyboard_error, it), color = MaterialTheme.colorScheme.error) }
            when {
                macro != null -> MacroEditor(macro!!, onSave = { value ->
                    viewModel.perform {
                        viewModel.repository.saveMacro(value)
                        macro = null
                    }
                }, onCancel = { macro = null })

                layout != null -> LayoutEditor(
                    layout = layout!!, items = items, config = config,
                    onLayoutChange = { layout = it }, onItemsChange = { items = it },
                    onError = { viewModel.error.value = it },
                    onSave = {
                        viewModel.perform {
                            viewModel.repository.save(layout!!, items)
                            layout = null
                        }
                    },
                    onCancel = { layout = null },
                    onReset = {
                        confirmation = {
                            items = KeyboardDefaults.items(layout!!.id)
                            layout = layout!!.copy(buttonWidth = 45f, buttonHeight = 30f)
                        }
                    },
                )

                else -> {
                    Text(stringResource(R.string.keyboard_layouts), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = {
                        val value = KeyboardLayout(name = newLayoutName)
                        layout = value
                        items = KeyboardDefaults.items(value.id)
                    }) { Text(stringResource(R.string.keyboard_add_layout)) }
                    config.layouts.forEach { value ->
                        Card {
                            Column(Modifier.padding(8.dp)) {
                                Text(value.name, style = MaterialTheme.typography.titleMedium)
                                Row {
                                    TextButton(onClick = {
                                        layout = value
                                        items = config.items.filter { it.layoutId == value.id }
                                    }) { Text(stringResource(R.string.keyboard_edit)) }
                                    TextButton(onClick = {
                                        layout = value.copy(id = UUID.randomUUID(), name = value.name + copySuffix)
                                        items = config.items.filter { it.layoutId == value.id }.map { it.copy(id = UUID.randomUUID(), layoutId = layout!!.id) }
                                    }) { Text(stringResource(R.string.keyboard_duplicate)) }
                                    TextButton(onClick = { confirmation = { viewModel.perform { viewModel.repository.deleteLayout(value.id) } } }) { Text(stringResource(R.string.keyboard_delete)) }
                                }
                                Row {
                                    RadioButton(selected = config.layout(null)?.id == value.id, onClick = { viewModel.perform { viewModel.repository.setDefault(value.id) } })
                                    TextButton(onClick = { viewModel.perform { viewModel.repository.setDefault(value.id) } }) { Text(stringResource(R.string.keyboard_default)) }
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.keyboard_macros), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { macro = KeyboardMacro(name = newMacroName, document = MacroFormat.encode(listOf(MacroAction.Key(key = "enter")))) }) { Text(stringResource(R.string.keyboard_add_macro)) }
                    config.macros.forEach { value ->
                        Card {
                            Column(Modifier.padding(8.dp)) {
                                Text(value.name)
                                val users = config.items.filter { it.macroId == value.id }.mapNotNull { item -> config.layouts.find { it.id == item.layoutId }?.name }.distinct()
                                if (users.isNotEmpty()) Text(stringResource(R.string.keyboard_used_by, users.joinToString(", ")))
                                Row {
                                    TextButton(onClick = { macro = value }) { Text(stringResource(R.string.keyboard_edit)) }
                                    TextButton(enabled = users.isEmpty(), onClick = { confirmation = { viewModel.perform { viewModel.repository.deleteMacro(value.id) } } }) { Text(stringResource(R.string.keyboard_delete)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmation != null) {
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(R.string.keyboard_confirm)) },
            text = { Text(stringResource(R.string.keyboard_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation?.invoke()
                    confirmation = null
                }) { Text(stringResource(R.string.keyboard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.keyboard_cancel)) } },
        )
    }
}

@Composable
private fun LayoutEditor(
    layout: KeyboardLayout,
    items: List<KeyboardItem>,
    config: KeyboardConfiguration,
    onLayoutChange: (KeyboardLayout) -> Unit,
    onItemsChange: (List<KeyboardItem>) -> Unit,
    onError: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember(layout.id) { mutableStateOf<UUID?>(null) }
    val validation = runCatching { KeyboardDefaults.validate(layout, items) }
    val updateItem: (KeyboardItem) -> Unit = { replacement ->
        onItemsChange(items.map { if (it.id == replacement.id) replacement else it })
    }
    val ordered = items.sortedWith(compareBy<KeyboardItem> { it.row }.thenBy { it.column }.thenBy { it.id })
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val moveItem: (UUID, UUID) -> Unit = { from, to ->
        runCatching { reorderKeyboardItems(layout, items, from, to) }
            .onSuccess {
                onItemsChange(it)
                onError(null)
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
            .onFailure { onError(it.message) }
    }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        moveItem(from.key as UUID, to.key as UUID)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = layout.name, onValueChange = { onLayoutChange(layout.copy(name = it)) }, label = { Text(stringResource(R.string.keyboard_name)) })
        DimensionField(stringResource(R.string.keyboard_width), layout.buttonWidth, { onLayoutChange(layout.copy(buttonWidth = it)) })
        DimensionField(stringResource(R.string.keyboard_height), layout.buttonHeight, { onLayoutChange(layout.copy(buttonHeight = it)) })
        Text(stringResource(R.string.keyboard_grid_help))
        KeyboardGrid(
            layout,
            items.filter { it.row >= 0 && it.column >= 0 && it.rowSpan > 0 && it.columnSpan > 0 },
            config.macros,
            onPress = { selected = it.id },
            editing = true,
            onMove = { item, row, column ->
                val moved = item.copy(row = row, column = column)
                val proposed = items.map { if (it.id == item.id) moved else it }
                val result = runCatching { KeyboardDefaults.validate(layout, proposed) }
                if (result.isSuccess) {
                    onItemsChange(proposed)
                    onError(null)
                } else {
                    onError(result.exceptionOrNull()?.message)
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
        )
        val moveUp = stringResource(R.string.keyboard_move_up)
        val moveDown = stringResource(R.string.keyboard_move_down)
        val dragLabel = stringResource(R.string.keyboard_drag_button)
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
            items(ordered, key = { it.id }) { button ->
                ReorderableItem(reorderState, key = button.id) { _ ->
                    val index = ordered.indexOfFirst { it.id == button.id }
                    Card(
                        modifier = Modifier.fillMaxWidth().semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(moveUp) {
                                    ordered.getOrNull(index - 1)?.let { moveItem(button.id, it.id) }
                                    true
                                },
                                CustomAccessibilityAction(moveDown) {
                                    ordered.getOrNull(index + 1)?.let { moveItem(button.id, it.id) }
                                    true
                                },
                            )
                        },
                    ) {
                        Row {
                            TextButton(onClick = { selected = button.id }, modifier = Modifier.weight(1f)) {
                                Text(keyboardLabel(button, config.macros))
                            }
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                Icon(Icons.Default.DragHandle, contentDescription = dragLabel)
                            }
                        }
                    }
                }
            }
        }
        val catalog = KeyboardDefaults.keys.map { KeyboardItem(layoutId = layout.id, target = it) } +
            KeyboardDefaults.modifiers.map { KeyboardItem(layoutId = layout.id, kind = "modifier", target = it) } +
            KeyboardDefaults.controls.map { KeyboardItem(layoutId = layout.id, kind = "app", target = it) } +
            config.macros.map { KeyboardItem(layoutId = layout.id, kind = "macro", target = "", macroId = it.id) }
        var adding by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { adding = true }) { Text(stringResource(R.string.keyboard_add_button)) }
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                catalog.forEach { item ->
                    DropdownMenuItem(text = { Text(keyboardLabel(item, config.macros)) }, onClick = {
                        val added = item.copy(row = items.maxOfOrNull { it.row + it.rowSpan } ?: 0)
                        onItemsChange(items + added)
                        selected = added.id
                        adding = false
                    })
                }
            }
        }
        val item = items.find { it.id == selected }
        if (item != null) {
            key(item.id) {
                Text(keyboardLabel(item, config.macros), style = MaterialTheme.typography.titleMedium)
                Row {
                    Checkbox(checked = item.visible, onCheckedChange = { updateItem(item.copy(visible = it)) })
                    Text(stringResource(R.string.keyboard_visible))
                }
                IntegerField(stringResource(R.string.keyboard_row), item.row, onChange = { updateItem(item.copy(row = it)) })
                IntegerField(stringResource(R.string.keyboard_column), item.column, onChange = { updateItem(item.copy(column = it)) })
                IntegerField(stringResource(R.string.keyboard_row_span), item.rowSpan, onChange = { updateItem(item.copy(rowSpan = it)) })
                IntegerField(stringResource(R.string.keyboard_column_span), item.columnSpan, onChange = { updateItem(item.copy(columnSpan = it)) })
                TextButton(onClick = {
                    onItemsChange(items.filterNot { it.id == item.id })
                    selected = null
                }) { Text(stringResource(R.string.keyboard_remove_button)) }
            }
        }
        validation.exceptionOrNull()?.message?.let { Text(stringResource(R.string.keyboard_error, it), color = MaterialTheme.colorScheme.error) }
        Row {
            Button(enabled = validation.isSuccess, onClick = onSave) { Text(stringResource(R.string.keyboard_save)) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.keyboard_cancel)) }
            TextButton(onClick = onReset) { Text(stringResource(R.string.keyboard_reset)) }
        }
    }
}

@Composable
private fun DimensionField(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (value.isFinite() && text.toFloatOrNull() != value) text = value.toString()
    }
    val parsed = text.toFloatOrNull()
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.toFloatOrNull() ?: Float.NaN)
        },
        label = { Text(label) },
        isError = parsed == null || !parsed.isFinite() || parsed <= 0 || parsed > 1000,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun IntegerField(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (value != Int.MIN_VALUE && text.toIntOrNull() != value) text = value.toString()
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.toIntOrNull() ?: Int.MIN_VALUE)
        },
        label = { Text(label) },
        isError = text.toIntOrNull() == null || value < 0,
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
fun KeyboardLayoutSelector(selected: UUID?, onSelect: (UUID?) -> Unit, modifier: Modifier = Modifier, viewModel: KeyboardViewModel = hiltViewModel()) {
    val config by viewModel.configuration.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(stringResource(R.string.keyboard_layouts), style = MaterialTheme.typography.titleMedium)
        Box {
            TextButton(onClick = { expanded = true }) { Text(config.layouts.find { it.id == selected }?.name ?: stringResource(R.string.keyboard_use_default)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.keyboard_use_default)) }, onClick = {
                    onSelect(null)
                    expanded = false
                })
                config.layouts.forEach { value ->
                    DropdownMenuItem(text = { Text(value.name) }, onClick = {
                        onSelect(value.id)
                        expanded = false
                    })
                }
            }
        }
    }
}
