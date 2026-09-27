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
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
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
import org.connectbot.data.keyboard.KeyboardLayers
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.KeyboardPreset
import org.connectbot.data.keyboard.KeyboardPresets
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.terminal.TerminalKeyCatalog
import org.connectbot.ui.components.KeyboardGrid
import org.connectbot.ui.components.TerminalKeyButtonDialog
import org.connectbot.ui.components.keyboardLabel
import org.connectbot.util.TerminalKeyModifiers
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
    var draftMacros by remember { mutableStateOf(emptyList<KeyboardMacro>()) }
    var macro by remember { mutableStateOf<KeyboardMacro?>(null) }
    var confirmation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val resources = LocalResources.current
    var choosingPreset by remember { mutableStateOf(false) }
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
                    layout = layout!!, items = items, config = config.copy(macros = config.macros + draftMacros),
                    onMacroChange = { value -> draftMacros = draftMacros + value },
                    onLayoutChange = { layout = it }, onItemsChange = { items = it },
                    onError = { viewModel.error.value = it },
                    onSave = {
                        viewModel.perform {
                            viewModel.repository.save(layout!!, items, draftMacros.filter { macro -> items.any { it.macroId == macro.id } })
                            layout = null
                        }
                    },
                    onCancel = { layout = null },
                    onReset = {
                        confirmation = {
                            val current = layout!!
                            val preset = KeyboardPresets.find(current.presetKey)
                            if (preset == null) {
                                items = KeyboardDefaults.items(current.id)
                                draftMacros = emptyList()
                                layout = current.copy(buttonWidth = 45f, buttonHeight = 30f, fitToWidth = false)
                            } else {
                                val reset = KeyboardPresets.create(preset, current.name, resources::getString, current.id)
                                items = reset.items
                                draftMacros = reset.macros
                                layout = reset.layout
                            }
                        }
                    },
                )

                else -> {
                    Text(stringResource(R.string.keyboard_layouts), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { choosingPreset = true }) { Text(stringResource(R.string.keyboard_add_layout)) }
                    config.layouts.forEach { value ->
                        Card {
                            Column(Modifier.padding(8.dp)) {
                                Text(value.name, style = MaterialTheme.typography.titleMedium)
                                KeyboardPresets.find(value.presetKey)?.let { Text(stringResource(it.subtitleRes), style = MaterialTheme.typography.bodySmall) }
                                Row {
                                    TextButton(onClick = {
                                        draftMacros = emptyList()
                                        layout = value
                                        items = config.items.filter { it.layoutId == value.id }
                                    }) { Text(stringResource(R.string.keyboard_edit)) }
                                    TextButton(onClick = {
                                        draftMacros = emptyList()
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
    if (choosingPreset) {
        AlertDialog(
            onDismissRequest = { choosingPreset = false },
            title = { Text(stringResource(R.string.keyboard_choose_preset)) },
            text = {
                Column {
                    TextButton(onClick = {
                        val value = KeyboardLayout(name = newLayoutName)
                        layout = value
                        items = KeyboardDefaults.items(value.id)
                        draftMacros = emptyList()
                        choosingPreset = false
                    }) { Text(stringResource(R.string.keyboard_original_layout)) }
                    KeyboardPreset.entries.forEach { preset ->
                        val presetName = stringResource(preset.nameRes)
                        TextButton(onClick = {
                            val draft = KeyboardPresets.create(preset, presetName, resources::getString)
                            layout = draft.layout
                            items = draft.items
                            draftMacros = draft.macros
                            choosingPreset = false
                        }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(presetName, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(preset.subtitleRes), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingPreset = false }) { Text(stringResource(R.string.keyboard_cancel)) } },
        )
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
    onMacroChange: (KeyboardMacro) -> Unit,
    onLayoutChange: (KeyboardLayout) -> Unit,
    onItemsChange: (List<KeyboardItem>) -> Unit,
    onError: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeLayer by remember(layout.id) { mutableIntStateOf(0) }
    val layerItems = items.filter { it.layer == activeLayer }
    var choosingDestination by remember(layout.id) { mutableStateOf(false) }
    var selected by remember(layout.id) { mutableStateOf<UUID?>(null) }
    var showKeyDialog by remember(layout.id) { mutableStateOf(false) }
    var editingKey by remember(layout.id) { mutableStateOf<KeyboardItem?>(null) }
    val validation = runCatching { KeyboardDefaults.validate(layout, items) }
    val updateItem: (KeyboardItem) -> Unit = { replacement ->
        onItemsChange(items.map { if (it.id == replacement.id) replacement else it })
    }
    val ordered = layerItems.sortedWith(compareBy<KeyboardItem> { it.row }.thenBy { it.column }.thenBy { it.id })
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
        Row {
            Checkbox(checked = layout.fitToWidth, onCheckedChange = { onLayoutChange(layout.copy(fitToWidth = it)) })
            Text(stringResource(R.string.keyboard_fit_to_width))
        }
        if (!layout.fitToWidth) DimensionField(stringResource(R.string.keyboard_width), layout.buttonWidth, { onLayoutChange(layout.copy(buttonWidth = it)) })
        DimensionField(stringResource(R.string.keyboard_height), layout.buttonHeight, { onLayoutChange(layout.copy(buttonHeight = it)) })
        PrimaryScrollableTabRow(selectedTabIndex = activeLayer, edgePadding = 0.dp) {
            repeat(KeyboardLayers.count) { layer ->
                Tab(selected = activeLayer == layer, onClick = {
                    activeLayer = layer
                    selected = null
                }, text = { Text(stringResource(R.string.keyboard_layer, layer)) })
            }
        }
        Text(stringResource(R.string.keyboard_grid_help))
        KeyboardGrid(
            layout,
            items.filter { it.row >= 0 && it.column >= 0 && it.rowSpan > 0 && it.columnSpan > 0 },
            config.macros,
            onPress = { selected = it.id },
            editing = true,
            activeLayer = activeLayer,
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
        val catalog = KeyboardDefaults.modifiers.map { KeyboardItem(layoutId = layout.id, kind = "modifier", target = it) } +
            KeyboardDefaults.controls.map { KeyboardItem(layoutId = layout.id, kind = "app", target = it) } +
            config.macros.map { KeyboardItem(layoutId = layout.id, kind = "macro", target = "", macroId = it.id) }
        var adding by remember { mutableStateOf(false) }
        Box {
            Button(onClick = { adding = true }) { Text(stringResource(R.string.keyboard_add_button)) }
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.keyboard_add_key)) }, onClick = {
                    editingKey = null
                    showKeyDialog = true
                    adding = false
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.keyboard_add_layer_switch)) }, onClick = {
                    val added = KeyboardItem(layoutId = layout.id, layer = activeLayer, row = layerItems.maxOfOrNull { it.row + it.rowSpan } ?: 0, kind = "layer", target = "next")
                    onItemsChange(items + added)
                    selected = added.id
                    adding = false
                    choosingDestination = true
                })
                catalog.forEach { item ->
                    DropdownMenuItem(text = { Text(keyboardLabel(item, config.macros)) }, onClick = {
                        val added = item.copy(layer = activeLayer, row = layerItems.maxOfOrNull { it.row + it.rowSpan } ?: 0)
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
                if (item.kind == "layer") {
                    TextButton(onClick = { choosingDestination = true }) {
                        Text(stringResource(R.string.keyboard_layer_destination))
                    }
                    if (choosingDestination) {
                        AlertDialog(
                            onDismissRequest = { choosingDestination = false },
                            title = { Text(stringResource(R.string.keyboard_layer_destination)) },
                            text = {
                                Column {
                                    (listOf("next") + (0 until KeyboardLayers.count).map(Int::toString)).forEach { target ->
                                        Row {
                                            RadioButton(selected = item.target == target, onClick = {
                                                updateItem(item.copy(target = target))
                                                choosingDestination = false
                                            })
                                            TextButton(onClick = {
                                                updateItem(item.copy(target = target))
                                                choosingDestination = false
                                            }) {
                                                Text(if (target == "next") stringResource(R.string.keyboard_layer_next) else stringResource(R.string.keyboard_layer, target.toInt()))
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = { TextButton(onClick = { choosingDestination = false }) { Text(stringResource(R.string.keyboard_cancel)) } },
                        )
                    }
                }
                val macro = config.macros.find { it.id == item.macroId }
                val macroKey = macro?.let { runCatching { MacroFormat.decode(it.document).singleOrNull() as? MacroAction.Key }.getOrNull() }
                val target = if (item.kind == "key") item.target else macroKey?.key
                if (target != null && TerminalKeyCatalog.byTarget(target) != null) {
                    TextButton(onClick = {
                        editingKey = item
                        showKeyDialog = true
                    }) { Text(stringResource(R.string.keyboard_edit)) }
                }
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
        if (showKeyDialog) {
            val existing = editingKey
            val macro = config.macros.find { it.id == existing?.macroId }
            val macroKey = macro?.let { runCatching { MacroFormat.decode(it.document).singleOrNull() as? MacroAction.Key }.getOrNull() }
            val mask = macroKey?.modifiers.orEmpty().fold(0) { value, name ->
                value or when (name) {
                    "ctrl" -> TerminalKeyModifiers.CTRL
                    "alt" -> TerminalKeyModifiers.ALT
                    "shift" -> TerminalKeyModifiers.SHIFT
                    else -> 0
                }
            }
            TerminalKeyButtonDialog(
                initialTarget = macroKey?.key ?: existing?.target ?: "enter",
                initialModifiers = mask,
                initialLabel = existing?.let { keyboardLabel(it, config.macros) },
                onDismiss = { showKeyDialog = false },
                onConfirm = { key, modifiers, label ->
                    val names = buildList {
                        if (modifiers and TerminalKeyModifiers.CTRL != 0) add("ctrl")
                        if (modifiers and TerminalKeyModifiers.ALT != 0) add("alt")
                        if (modifiers and TerminalKeyModifiers.SHIFT != 0) add("shift")
                    }
                    val value = KeyboardMacro(name = label, document = MacroFormat.encode(listOf(MacroAction.Key(key = key.target, modifiers = names))))
                    onMacroChange(value)
                    val button = (existing ?: KeyboardItem(layoutId = layout.id, layer = activeLayer, row = layerItems.maxOfOrNull { it.row + it.rowSpan } ?: 0))
                        .copy(kind = "key", target = key.target, macroId = value.id)
                    if (existing == null) onItemsChange(items + button) else updateItem(button)
                    selected = button.id
                    showKeyDialog = false
                },
            )
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
