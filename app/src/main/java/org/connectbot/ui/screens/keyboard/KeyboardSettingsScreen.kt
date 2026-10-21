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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardConfiguration
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardDraft
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayers
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.KeyboardPreset
import org.connectbot.data.keyboard.KeyboardPresets
import org.connectbot.data.keyboard.KeyboardUsage
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.data.keyboard.fittedKeyboardItems
import org.connectbot.terminal.TerminalKeyCatalog
import org.connectbot.ui.components.EditorUnsavedChangesDialog
import org.connectbot.ui.components.KeyboardGrid
import org.connectbot.ui.components.ReorderableKeyboardGrid
import org.connectbot.ui.components.SaveEditorFab
import org.connectbot.ui.components.TerminalKeyButtonDialog
import org.connectbot.ui.components.keyboardLabel
import org.connectbot.util.TerminalKeyModifiers
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    macrosOnly: Boolean = false,
    viewModel: KeyboardViewModel = hiltViewModel(),
    selectedLayoutId: UUID? = null,
    inheritedLabel: String? = null,
    initialDraft: KeyboardDraft? = null,
    onSelectLayout: (suspend (UUID?, KeyboardDraft?) -> Unit)? = null,
    onSelectMacro: ((KeyboardMacro) -> Unit)? = null,
    pendingMacros: List<KeyboardMacro> = emptyList(),
    currentReferenceId: UUID? = null,
    onNavigateToUsage: ((KeyboardUsage) -> Unit)? = null,
) {
    val library = remember(viewModel) { viewModel.libraryState() }
    KeyboardLibrary(
        onNavigateBack = onNavigateBack, modifier = modifier, macrosOnly = macrosOnly, library = library,
        selectedLayoutId = selectedLayoutId, inheritedLabel = inheritedLabel, initialDraft = initialDraft,
        onSelectLayout = onSelectLayout, onSelectMacro = onSelectMacro, pendingMacros = pendingMacros,
        currentReferenceId = currentReferenceId, onNavigateToUsage = onNavigateToUsage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyboardLibrary(
    onNavigateBack: () -> Unit,
    library: KeyboardLibraryState,
    modifier: Modifier = Modifier,
    macrosOnly: Boolean = false,
    selectedLayoutId: UUID? = null,
    inheritedLabel: String? = null,
    initialDraft: KeyboardDraft? = null,
    onSelectLayout: (suspend (UUID?, KeyboardDraft?) -> Unit)? = null,
    onSelectMacro: ((KeyboardMacro) -> Unit)? = null,
    pendingMacros: List<KeyboardMacro> = emptyList(),
    currentReferenceId: UUID? = null,
    onNavigateToUsage: ((KeyboardUsage) -> Unit)? = null,
    applyImmediately: Boolean = false,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val config by library.configuration.collectAsState()
    val error by library.error.collectAsState()
    var draft by rememberSaveable { mutableStateOf(initialDraft) }
    var original by rememberSaveable { mutableStateOf(initialDraft) }
    var macro by rememberSaveable { mutableStateOf<KeyboardMacro?>(null) }
    var macroGlobal by rememberSaveable { mutableStateOf(false) }
    var macroDirty by rememberSaveable { mutableStateOf(false) }
    var macroCanSave by remember { mutableStateOf(false) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var choosingPreset by rememberSaveable { mutableStateOf(false) }
    var libraryTab by rememberSaveable { mutableIntStateOf(if (macrosOnly) 1 else 0) }
    var discard by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var destructiveId by rememberSaveable { mutableStateOf<UUID?>(null) }
    var promptMacro by rememberSaveable { mutableStateOf<KeyboardMacro?>(null) }
    var confirmSharedLayout by rememberSaveable { mutableStateOf<KeyboardDraft?>(null) }
    var sharedLayout by rememberSaveable { mutableStateOf<KeyboardDraft?>(null) }
    var exportingMacro by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveConfig = config.copy(macros = (config.macros + pendingMacros + draft?.macros.orEmpty()).associateBy { it.id }.values.toList())
    fun editMacro(value: KeyboardMacro, global: Boolean) {
        macro = value
        macroGlobal = global
        macroDirty = false
    }
    val exportMacro = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = exportingMacro?.let(UUID::fromString)
        if (uri != null && id != null) library.perform { library.repository.exportMacro(id) { context.contentResolver.openOutputStream(uri) } }
        exportingMacro = null
    }
    val importMacro = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            library.perform {
                val imported = library.repository.readMacro { context.contentResolver.openInputStream(uri) }
                if (onSelectMacro != null) onSelectMacro(imported) else editMacro(imported, false)
            }
        }
    }
    val leave = {
        when {
            macro != null -> {
                macro = null
                macroDirty = false
            }

            draft != null && initialDraft == null -> {
                draft = null
                original = null
            }

            else -> onNavigateBack()
        }
    }
    val dirty = if (macro != null) macroDirty || config.macros.none { it.id == macro?.id } else draft != original || (draft != null && config.layouts.none { it.id == draft?.layout?.id })
    val requestBack = {
        if (!busy) {
            if (dirty) discard = true else leave()
        }
    }
    val saveMacro: (KeyboardMacro) -> Unit = { value ->
        if (!busy) {
            busy = true
            library.perform {
                try {
                    if (onSelectMacro != null && !macroGlobal) {
                        onSelectMacro(value)
                    } else {
                        library.repository.saveMacro(value)
                        if (onSelectMacro != null) onSelectMacro(value)
                    }
                    macro = null
                    macroDirty = false
                } finally {
                    busy = false
                }
            }
        }
    }
    val saveLayout: () -> Unit = {
        if (!busy) {
            busy = true
            library.perform {
                try {
                    val value = draft!!
                    if (onSelectLayout != null) {
                        onSelectLayout(value.layout.id, value)
                    } else {
                        library.repository.save(value.layout, value.items, value.macros.filter { m -> value.items.any { it.macroId == m.id } })
                        if (initialDraft != null) onNavigateBack()
                    }
                    draft = null
                    original = null
                } finally {
                    busy = false
                }
            }
        }
    }
    val selectExisting: (UUID?) -> Unit = { id ->
        if (!busy) {
            busy = true
            library.perform {
                try {
                    onSelectLayout?.invoke(id, null)
                } finally {
                    busy = false
                }
            }
        }
    }
    BackHandler { requestBack() }
    if (managing) {
        KeyboardWorkflowDialog(onDismiss = { managing = false }) {
            KeyboardLibrary(onNavigateBack = { managing = false }, library = library)
        }
    }
    Scaffold(modifier = modifier, contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime), floatingActionButton = {
        if (draft != null && macro == null) {
            SaveEditorFab(
                visible = runCatching { KeyboardDefaults.validate(draft!!.layout, draft!!.items) }.isSuccess,
                isSaving = busy,
                contentDescription = stringResource(R.string.keyboard_save),
                onClick = saveLayout,
            )
        }
    }, topBar = {
        TopAppBar(title = { Text(if (draft != null && macro == null) stringResource(R.string.keyboard_edit_layout_title) else macro?.name ?: stringResource(if (libraryTab == 1) R.string.keyboard_macros else R.string.keyboard_settings_title)) }, navigationIcon = {
            IconButton(onClick = requestBack, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.button_navigate_up)) }
        }, actions = {
            if (draft != null && macro == null) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.keyboard_more_layout_actions)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.keyboard_reset)) }, onClick = {
                            menu = false
                            confirmation = "reset"
                        })
                    }
                }
            }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = if (draft != null && macro == null) 88.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(stringResource(R.string.keyboard_error, it), color = MaterialTheme.colorScheme.error) }
            when {
                macro != null -> {
                    if (macroGlobal) Text(stringResource(R.string.macro_global_save_notice))
                    MacroEditor(macro!!, onSave = saveMacro, onCancel = requestBack, onDirtyChange = { macroDirty = it }, onDraftChange = { macro = it }, onCanSaveChange = { macroCanSave = it }, done = onSelectMacro != null && !macroGlobal)
                }

                draft != null -> {
                    if (initialDraft != null && onSelectLayout == null) {
                        val users by remember(draft!!.layout.id) { library.repository.layoutUsage(draft!!.layout.id) }.collectAsState(emptyList())
                        KeyboardStatus(stringResource(R.string.keyboard_usage_shared_layout))
                        if (users.isNotEmpty()) KeyboardSettingsGroup(stringResource(R.string.keyboard_usage_count, users.size), "") { UsageLinks(users, onNavigateToUsage) }
                    }
                    LayoutEditor(
                        layout = draft!!.layout, storedItems = draft!!.items, config = effectiveConfig, library = library, done = onSelectLayout != null, applyImmediately = applyImmediately,
                        onMacroChange = { value -> draft = draft!!.copy(macros = draft!!.macros.filterNot { it.id == value.id } + value) },
                        onLayoutChange = { draft = draft!!.copy(layout = it) }, onItemsChange = { draft = draft!!.copy(items = it) },
                        onError = { library.error.value = it },
                    )
                }

                else -> {
                    if (!macrosOnly && onSelectMacro == null) {
                        Row {
                            PrimaryScrollableTabRow(selectedTabIndex = libraryTab, edgePadding = 0.dp) {
                                Tab(selected = libraryTab == 0, onClick = { libraryTab = 0 }, text = { Text(stringResource(R.string.keyboard_layouts)) })
                                Tab(selected = libraryTab == 1, onClick = { libraryTab = 1 }, text = { Text(stringResource(R.string.keyboard_macros)) })
                            }
                        }
                    }
                    if (libraryTab == 0 && !macrosOnly && onSelectMacro == null) {
                        inheritedLabel?.let { label ->
                            Surface(onClick = { selectExisting(null) }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = selectedLayoutId == null, onClick = null)
                                    Column {
                                        Text(stringResource(R.string.keyboard_inherited_selection), style = MaterialTheme.typography.titleSmall)
                                        Text(label, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        if (onSelectLayout != null) TextButton(onClick = { managing = true }) { Text(stringResource(R.string.keyboard_manage_layouts)) }
                        Button(onClick = { choosingPreset = true }) { Text(stringResource(R.string.keyboard_add_layout)) }
                        config.layouts.forEach { value ->
                            val users by remember(value.id) { library.repository.layoutUsage(value.id) }.collectAsState(emptyList())
                            var actions by remember(value.id) { mutableStateOf(false) }
                            val edit = {
                                val valueDraft = KeyboardDraft(value, config.items.filter { it.layoutId == value.id }.map { it.copy(rowSpan = 1) })
                                if (onSelectLayout != null || users.isNotEmpty() || config.layout(null)?.id == value.id) {
                                    confirmSharedLayout = valueDraft
                                } else {
                                    draft = valueDraft
                                    original = valueDraft
                                }
                            }
                            Card {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(onClick = { if (onSelectLayout != null) selectExisting(value.id) else edit() }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                if (onSelectLayout != null) RadioButton(selected = value.id == selectedLayoutId, onClick = null)
                                                Column(Modifier.weight(1f)) {
                                                    Text(value.name, style = MaterialTheme.typography.titleMedium)
                                                    KeyboardPresets.find(value.presetKey)?.let { Text(stringResource(it.subtitleRes), style = MaterialTheme.typography.bodySmall) }
                                                }
                                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                            }
                                            KeyboardGrid(value, config.items.filter { it.layoutId == value.id }, config.macros, onPress = {}, interactive = false, modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).clearAndSetSemantics {})
                                            if (config.layout(null)?.id == value.id) KeyboardStatus(stringResource(R.string.keyboard_app_default))
                                            if (value.id == selectedLayoutId) KeyboardStatus(stringResource(R.string.keyboard_selected))
                                        }
                                    }
                                    if (onSelectLayout == null) {
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(onClick = edit, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.keyboard_library_edit)) }
                                            Box {
                                                IconButton(onClick = { actions = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.keyboard_more_layout_actions)) }
                                                DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                                                    DropdownMenuItem(text = { Text(stringResource(R.string.keyboard_edit_copy)) }, onClick = {
                                                        draft = KeyboardDraft(value, config.items.filter { it.layoutId == value.id }).duplicate(value.name + resources.getString(R.string.keyboard_copy_suffix))
                                                        original = null
                                                        actions = false
                                                    })
                                                    DropdownMenuItem(text = { Text(stringResource(R.string.keyboard_make_default)) }, onClick = {
                                                        actions = false
                                                        library.perform { library.repository.setDefault(value.id) }
                                                    })
                                                    DropdownMenuItem(text = { Text(stringResource(R.string.keyboard_delete), color = MaterialTheme.colorScheme.error) }, onClick = {
                                                        actions = false
                                                        destructiveId = value.id
                                                        confirmation = "layout"
                                                    })
                                                }
                                            }
                                        }
                                    }
                                    if (users.isNotEmpty()) KeyboardSettingsGroup(stringResource(R.string.keyboard_usage_count, users.size), "") { UsageLinks(users, onNavigateToUsage) }
                                }
                            }
                        }
                        Text(stringResource(R.string.keyboard_builtin_presets), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.keyboard_builtin_presets_help), style = MaterialTheme.typography.bodySmall)
                        KeyboardPreset.entries.forEach { preset ->
                            val name = stringResource(preset.nameRes)
                            val preview = remember(preset, name) { KeyboardPresets.create(preset, name, resources::getString) }
                            val editCopy = {
                                val copy = KeyboardPresets.create(preset, name + resources.getString(R.string.keyboard_copy_suffix), resources::getString)
                                draft = KeyboardDraft(copy.layout, copy.items, copy.macros)
                                original = null
                            }
                            val selectPreset = {
                                if (!busy) {
                                    busy = true
                                    library.perform {
                                        try {
                                            val copy = KeyboardPresets.create(preset, name, resources::getString)
                                            onSelectLayout?.invoke(copy.layout.id, KeyboardDraft(copy.layout, copy.items, copy.macros))
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            }
                            Card {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(onClick = { if (onSelectLayout != null) selectPreset() else editCopy() }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(name, style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(preset.subtitleRes), style = MaterialTheme.typography.bodySmall)
                                            KeyboardGrid(preview.layout, preview.items, preview.macros, onPress = {}, interactive = false, modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).clearAndSetSemantics {})
                                            KeyboardStatus(stringResource(R.string.keyboard_builtin_preset))
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (onSelectLayout != null) Button(onClick = { selectPreset() }, enabled = !busy) { Text(stringResource(R.string.keyboard_select)) }
                                        OutlinedButton(onClick = { editCopy() }, enabled = !busy) { Text(stringResource(R.string.keyboard_edit_copy)) }
                                    }
                                }
                            }
                        }
                    } else {
                        Button(onClick = { editMacro(KeyboardMacro(name = resources.getString(R.string.keyboard_new_macro), document = MacroFormat.encode(listOf(MacroAction.Key(key = "enter")))), false) }) { Text(stringResource(R.string.keyboard_add_macro)) }
                        TextButton(onClick = { importMacro.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.macro_import)) }
                        effectiveConfig.macros.forEach { value ->
                            val usage by remember(value.id) { library.repository.macroUsage(value.id) }.collectAsState(initial = null)
                            val users = usage.orEmpty()
                            val supported = value.id !in effectiveConfig.unsupportedMacroIds
                            Card {
                                Column(Modifier.padding(8.dp)) {
                                    if (onSelectMacro != null) TextButton(enabled = supported, onClick = { onSelectMacro(value) }) { Text(value.name) } else Text(value.name)
                                    UsageLinks(users, onNavigateToUsage)
                                    Row {
                                        if (onSelectMacro != null) TextButton(enabled = supported, onClick = { onSelectMacro(value) }) { Text(stringResource(R.string.keyboard_select)) }
                                        TextButton(enabled = usage != null, onClick = {
                                            if (users.any { it.referenceId != currentReferenceId }) promptMacro = value else editMacro(value, false)
                                        }) { Text(stringResource(R.string.keyboard_edit)) }
                                        if (onSelectMacro == null) {
                                            TextButton(onClick = {
                                                exportingMacro = value.id.toString()
                                                exportMacro.launch("macro.json")
                                            }) { Text(stringResource(R.string.macro_export)) }
                                        }
                                    }
                                    if (onSelectMacro == null) {
                                        TextButton(enabled = usage != null && users.isEmpty(), onClick = {
                                            destructiveId = value.id
                                            confirmation = "macro"
                                        }) { Text(stringResource(R.string.keyboard_delete)) }
                                        if (users.isNotEmpty()) Text(stringResource(R.string.macro_remove_uses_first))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (busy) Text(stringResource(R.string.keyboard_saving))
        }
    }
    if (choosingPreset) {
        KeyboardWorkflowDialog(onDismiss = { choosingPreset = false }) {
            BackHandler { choosingPreset = false }
            Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.keyboard_choose_preset)) }, navigationIcon = { IconButton(onClick = { choosingPreset = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.button_navigate_up)) } }) }) { padding ->
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val originalName = stringResource(R.string.keyboard_original_layout)
                    val originalLayout = remember(originalName) { KeyboardLayout(name = originalName) }
                    val presets = listOf(KeyboardDraft(originalLayout, KeyboardDefaults.items(originalLayout.id))) + KeyboardPreset.entries.map { preset ->
                        KeyboardPresets.create(preset, resources.getString(preset.nameRes), resources::getString).let { KeyboardDraft(it.layout, it.items, it.macros) }
                    }
                    presets.forEach { preset ->
                        Card(onClick = {
                            draft = preset
                            original = null
                            choosingPreset = false
                        }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(preset.layout.name, style = MaterialTheme.typography.titleMedium)
                                KeyboardPresets.find(preset.layout.presetKey)?.let { Text(stringResource(it.subtitleRes), style = MaterialTheme.typography.bodySmall) }
                                KeyboardGrid(preset.layout, preset.items, preset.macros, onPress = {}, interactive = false, modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).clearAndSetSemantics {})
                            }
                        }
                    }
                }
            }
        }
    }
    if (discard) {
        EditorUnsavedChangesDialog(
            canSave = !busy && (if (macro != null) macroCanSave else draft?.let { runCatching { KeyboardDefaults.validate(it.layout, it.items) }.isSuccess } == true),
            onCancel = { discard = false },
            onDiscard = {
                discard = false
                leave()
            },
            onSave = {
                discard = false
                if (macro != null) saveMacro(macro!!) else saveLayout()
            },
        )
    }
    promptMacro?.let { value ->
        val users by remember(value.id) { library.repository.macroUsage(value.id) }.collectAsState(emptyList())
        AlertDialog(
            onDismissRequest = { promptMacro = null },
            title = { Text(stringResource(R.string.macro_shared_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.macro_shared_message))
                    UsageLinks(users, onNavigateToUsage)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    promptMacro = null
                    editMacro(value.copy(id = UUID.randomUUID(), name = value.name + resources.getString(R.string.keyboard_copy_suffix)), false)
                }) { Text(stringResource(R.string.macro_make_copy)) }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = {
                        promptMacro = null
                        editMacro(value, true)
                    }) { Text(stringResource(R.string.macro_edit_shared)) }
                    TextButton(onClick = { promptMacro = null }) { Text(stringResource(R.string.button_cancel)) }
                }
            },
        )
    }
    confirmSharedLayout?.let { value ->
        val users by remember(value.layout.id) { library.repository.layoutUsage(value.layout.id) }.collectAsState(emptyList())
        AlertDialog(onDismissRequest = { confirmSharedLayout = null }, title = { Text(stringResource(R.string.keyboard_shared_title)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.keyboard_shared_confirm))
                UsageLinks(users, null)
            }
        }, confirmButton = {
            Button(onClick = {
                sharedLayout = value
                confirmSharedLayout = null
            }) { Text(stringResource(R.string.keyboard_edit_shared)) }
        }, dismissButton = {
            TextButton(onClick = {
                draft = value.duplicate(value.layout.name + resources.getString(R.string.keyboard_copy_suffix))
                original = null
                confirmSharedLayout = null
            }) { Text(stringResource(R.string.keyboard_edit_copy)) }
        })
    }
    // Shared editing is a separate full-screen destination so contextual Save never assigns a shared edit as a private draft.
    sharedLayout?.let { value ->
        KeyboardWorkflowDialog(onDismiss = { sharedLayout = null }) {
            SharedLayoutEditor(value, onNavigateBack = { sharedLayout = null }, library = library)
        }
    }
    confirmation?.let { operation ->
        val id = destructiveId
        val value = config.layouts.find { it.id == id }
        val users by remember(id) { if (id != null && operation == "layout") library.repository.layoutUsage(id) else kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(emptyList())
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = {
                Text(
                    stringResource(
                        when (operation) {
                            "reset" -> R.string.keyboard_reset
                            "macro" -> R.string.keyboard_delete_macro_title
                            else -> R.string.keyboard_delete_layout_title
                        },
                        value?.name ?: config.macros.find { it.id == id }?.name.orEmpty(),
                    ),
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(
                            if (operation == "reset") {
                                R.string.keyboard_reset_message
                            } else if (operation == "layout") {
                                R.string.keyboard_delete_layout_message
                            } else {
                                R.string.keyboard_delete_macro_message
                            },
                        ),
                    )
                    UsageLinks(users, onNavigateToUsage)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (operation) {
                        "reset" -> {
                            val current = draft!!.layout
                            val preset = KeyboardPresets.find(current.presetKey)
                            draft = if (preset == null) KeyboardDraft(current.copy(buttonWidth = 45f, buttonHeight = 30f, fitToWidth = false), KeyboardDefaults.items(current.id)) else KeyboardPresets.create(preset, current.name, resources::getString, current.id).let { KeyboardDraft(it.layout, it.items, it.macros) }
                        }

                        "macro" -> library.perform { library.repository.deleteMacro(id!!) }

                        else -> library.perform { library.repository.deleteLayout(id!!) }
                    }
                }) { Text(stringResource(R.string.keyboard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.button_cancel)) } },
        )
    }
}

@Composable
private fun SharedLayoutEditor(draft: KeyboardDraft, onNavigateBack: () -> Unit, library: KeyboardLibraryState) {
    KeyboardLibrary(onNavigateBack = onNavigateBack, initialDraft = draft, library = library)
}

@Composable
internal fun UsageLinks(users: List<KeyboardUsage>, onNavigate: ((KeyboardUsage) -> Unit)?) {
    val navigate = onNavigate ?: LocalKeyboardUsageNavigator.current
    users.distinctBy { it.kind to it.ownerId }.forEach { usage ->
        val label = stringResource(
            when (usage.kind) {
                "key" -> R.string.keyboard_usage_layout
                "automation" -> R.string.keyboard_usage_automation
                "profile" -> R.string.keyboard_usage_profile
                else -> R.string.keyboard_usage_host
            },
            usage.name,
        )
        if (navigate == null) {
            Text(label, style = MaterialTheme.typography.bodySmall)
        } else {
            TextButton(onClick = { navigate(usage) }) { Text(label) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutEditor(
    layout: KeyboardLayout,
    storedItems: List<KeyboardItem>,
    config: KeyboardConfiguration,
    library: KeyboardLibraryState,
    done: Boolean,
    applyImmediately: Boolean,
    onMacroChange: (KeyboardMacro) -> Unit,
    onLayoutChange: (KeyboardLayout) -> Unit,
    onItemsChange: (List<KeyboardItem>) -> Unit,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = if (layout.fitToWidth) fittedKeyboardItems(storedItems) else storedItems
    var activeLayer by rememberSaveable(layout.id) { mutableIntStateOf(0) }
    var selected by rememberSaveable(layout.id) { mutableStateOf<UUID?>(null) }
    var keyDraft by rememberSaveable(layout.id) { mutableStateOf<KeyboardDraft?>(null) }
    var newKey by rememberSaveable(layout.id) { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(value = layout.name, onValueChange = { onLayoutChange(layout.copy(name = it)) }, label = { Text(stringResource(R.string.keyboard_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (done) Text(stringResource(if (applyImmediately) R.string.keyboard_immediate_hint else R.string.keyboard_parent_save_hint), style = MaterialTheme.typography.bodySmall)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.keyboard_canvas_title), style = MaterialTheme.typography.titleMedium)
                    Box {
                        OutlinedButton(onClick = { adding = true }) {
                            Icon(Icons.Default.Add, null)
                            Text(stringResource(R.string.keyboard_add_button))
                        }
                        DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                            listOf("key" to R.string.keyboard_key_type, "macro" to R.string.keyboard_action_macro, "modifier" to R.string.keyboard_action_modifier, "app" to R.string.keyboard_action_app, "layer" to R.string.keyboard_action_layer).forEach { (kind, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                    val item = KeyboardItem(
                                        layoutId = layout.id,
                                        layer = activeLayer,
                                        row = items.filter { it.layer == activeLayer && !it.pinned }.maxOfOrNull { it.row + 1 } ?: 0,
                                        kind = kind,
                                        target = when (kind) {
                                            "key" -> "enter"
                                            "modifier" -> "ctrl"
                                            "app" -> "paste"
                                            "layer" -> "0"
                                            else -> ""
                                        },
                                    )
                                    keyDraft = KeyboardDraft(layout, items + item)
                                    selected = item.id
                                    newKey = true
                                    adding = false
                                })
                            }
                        }
                    }
                }
                PrimaryScrollableTabRow(selectedTabIndex = activeLayer, edgePadding = 0.dp) {
                    repeat(KeyboardLayers.count) { layer ->
                        Tab(selected = activeLayer == layer, onClick = { activeLayer = layer }, text = { Text(stringResource(R.string.keyboard_layer, layer)) })
                    }
                }
                Text(stringResource(R.string.keyboard_canvas_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (items.none { it.layer == activeLayer }) Text(stringResource(R.string.keyboard_empty_layer), style = MaterialTheme.typography.bodySmall)
                ReorderableKeyboardGrid(
                    layout, items.filter { it.row >= 0 && it.column >= 0 && it.rowSpan > 0 && it.columnSpan > 0 }, config.macros,
                    onPress = {
                        selected = it.id
                        keyDraft = KeyboardDraft(layout, items)
                        newKey = false
                    }, activeLayer = activeLayer,
                    onReorder = { from, to ->
                        runCatching { moveKeyboardItem(layout, items, from, to) }.onSuccess {
                            onItemsChange(it)
                            onError(null)
                        }.onFailure { onError(it.message) }
                    },
                    onPin = { id, pinned ->
                        runCatching { pinKeyboardItem(layout, items, id, pinned, targetRow = 0) }.onSuccess {
                            onItemsChange(it)
                            onError(null)
                        }.onFailure { onError(it.message) }
                    },
                    onMove = { item, row, column ->
                        val proposed = items.map { if (it.id == item.id) item.copy(row = row, column = column) else it }
                        runCatching { KeyboardDefaults.validate(layout, proposed) }.onSuccess {
                            onItemsChange(proposed)
                            onError(null)
                        }.onFailure { onError(it.message) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                )
                if (!layout.fitToWidth) Text(stringResource(R.string.keyboard_scrolling_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        KeyboardSettingsGroup(
            stringResource(R.string.keyboard_layout_settings),
            stringResource(R.string.keyboard_dimensions_summary, stringResource(if (layout.fitToWidth) R.string.keyboard_mode_fit else R.string.keyboard_mode_scrollable), layout.buttonWidth.toString(), layout.buttonHeight.toString()),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { index, fit ->
                    SegmentedButton(selected = layout.fitToWidth == fit, onClick = {
                        onItemsChange(if (fit) fittedKeyboardItems(items) else items)
                        onLayoutChange(layout.copy(fitToWidth = fit))
                    }, shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                        Text(stringResource(if (fit) R.string.keyboard_mode_fit else R.string.keyboard_mode_scrollable))
                    }
                }
            }
            if (!layout.fitToWidth) DimensionField(stringResource(R.string.keyboard_width), layout.buttonWidth, { onLayoutChange(layout.copy(buttonWidth = it)) }, Modifier.fillMaxWidth())
            DimensionField(stringResource(R.string.keyboard_height), layout.buttonHeight, { onLayoutChange(layout.copy(buttonHeight = it)) }, Modifier.fillMaxWidth())
        }
        runCatching { KeyboardDefaults.validate(layout, items) }.exceptionOrNull()?.message?.let { Text(stringResource(R.string.keyboard_error, it), color = MaterialTheme.colorScheme.error) }
    }
    val editing = keyDraft
    val id = selected
    if (editing != null && id != null) {
        KeyboardWorkflowDialog(onDismiss = {}) {
            KeyboardKeyEditor(
                initial = editing,
                itemId = id,
                config = config,
                library = library,
                isNew = newKey,
                onDone = { value ->
                    onItemsChange(value.items)
                    value.macros.filter { macro -> value.items.any { it.macroId == macro.id } }.forEach(onMacroChange)
                    keyDraft = null
                    selected = null
                },
                onBack = {
                    keyDraft = null
                    selected = null
                },
            )
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
internal fun IntegerField(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
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
fun KeyboardLayoutSelector(
    selected: UUID?,
    onSelect: (UUID?) -> Unit,
    modifier: Modifier = Modifier,
    macrosOnly: Boolean = false,
    viewModel: KeyboardViewModel = hiltViewModel(),
    inheritedId: UUID? = null,
    source: String? = null,
    ownerName: String = "",
    pendingDraft: KeyboardDraft? = null,
    onDraft: (KeyboardDraft?) -> Unit = {},
    onApply: (suspend (UUID?, KeyboardDraft?) -> Unit)? = null,
) {
    val library = remember(viewModel) { viewModel.libraryState() }
    KeyboardLayoutSelection(
        selected = selected, onSelect = onSelect, library = library, modifier = modifier,
        inheritedId = inheritedId, source = source, ownerName = ownerName,
        pendingDraft = pendingDraft, onDraft = onDraft, onApply = onApply,
    )
}

@Composable
internal fun KeyboardLayoutSelection(
    selected: UUID?,
    onSelect: (UUID?) -> Unit,
    library: KeyboardLibraryState,
    modifier: Modifier = Modifier,
    inheritedId: UUID? = null,
    source: String? = null,
    ownerName: String = "",
    pendingDraft: KeyboardDraft? = null,
    onDraft: (KeyboardDraft?) -> Unit = {},
    onApply: (suspend (UUID?, KeyboardDraft?) -> Unit)? = null,
) {
    val config by library.configuration.collectAsState()
    val layout = pendingDraft?.takeIf { it.layout.id == selected }?.layout ?: config.layout(selected ?: inheritedId)
    val items = pendingDraft?.takeIf { it.layout.id == layout?.id }?.items ?: config.items.filter { it.layoutId == layout?.id }
    val macros = config.macros + pendingDraft?.macros.orEmpty()
    var open by rememberSaveable { mutableStateOf(false) }
    var customizing by rememberSaveable { mutableStateOf<KeyboardDraft?>(null) }
    val resources = LocalResources.current
    val inheritance = source ?: stringResource(R.string.keyboard_app_default)
    val applySelection: suspend (UUID?, KeyboardDraft?) -> Unit = { id, draft ->
        if (onApply != null) {
            onApply(id, draft)
        } else {
            onDraft(draft)
            onSelect(id)
        }
    }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.keyboard_settings_title), style = MaterialTheme.typography.titleMedium)
            KeyboardNavigationRow(layout?.name.orEmpty(), if (selected == null) inheritance else stringResource(R.string.keyboard_own_layout), { open = true })
            layout?.let { value ->
                KeyboardPresets.find(value.presetKey)?.let { Text(stringResource(it.subtitleRes), style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(R.string.keyboard_preview_label), style = MaterialTheme.typography.labelSmall)
                KeyboardGrid(value, items, macros, onPress = {}, interactive = false, modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).clearAndSetSemantics {})
            }
            Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.keyboard_change)) }
            OutlinedButton(enabled = layout != null, modifier = Modifier.fillMaxWidth(), onClick = {
                val value = layout!!
                customizing = if (pendingDraft != null && pendingDraft.layout.id == selected) pendingDraft else KeyboardDraft(value, items).duplicate(resources.getString(R.string.keyboard_context_name, value.name, ownerName.ifBlank { resources.getString(R.string.keyboard_copy_name) }))
                open = true
            }) { Text(stringResource(if (pendingDraft != null && pendingDraft.layout.id == selected) R.string.keyboard_continue_editing else R.string.keyboard_edit_copy)) }
            Text(stringResource(R.string.keyboard_copy_help, ownerName.ifBlank { resources.getString(R.string.keyboard_this_configuration) }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onApply == null) Text(stringResource(R.string.keyboard_parent_save_hint), style = MaterialTheme.typography.bodySmall)
            if (selected != null) TextButton(onClick = { library.perform { applySelection(null, null) } }) { Text(stringResource(R.string.keyboard_reset_inherited)) }
        }
    }
    if (open) {
        KeyboardWorkflowDialog(onDismiss = {
            open = false
            customizing = null
        }) {
            KeyboardLibrary(
                onNavigateBack = {
                    open = false
                    customizing = null
                },
                library = library,
                selectedLayoutId = selected,
                inheritedLabel = resources.getString(R.string.keyboard_inherit_named, inheritance, config.layout(inheritedId)?.name.orEmpty()),
                initialDraft = customizing,
                applyImmediately = onApply != null,
                onSelectLayout = { id, draft ->
                    applySelection(id, draft)
                    open = false
                    customizing = null
                },
            )
        }
    }
}

/** One full-screen surface, with back handling owned by the editor inside it. */
@Composable
internal fun KeyboardWorkflowDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // Own the window's state in the parent registry so drafts survive recreation with it.
    val stateHolder = rememberSaveableStateHolder()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        stateHolder.SaveableStateProvider("keyboard_workflow") {
            androidx.compose.material3.Surface(Modifier.fillMaxSize()) { content() }
        }
    }
}

internal val LocalKeyboardUsageNavigator = androidx.compose.runtime.staticCompositionLocalOf<((KeyboardUsage) -> Unit)?> { null }

@Composable
internal fun KeyboardLayoutRoute(id: UUID, onNavigateBack: () -> Unit, viewModel: KeyboardViewModel = hiltViewModel()) {
    val library = remember(viewModel) { viewModel.libraryState() }
    val config by library.configuration.collectAsState()
    val layout = config.layouts.find { it.id == id }
    if (layout != null) {
        KeyboardLibrary(onNavigateBack = onNavigateBack, initialDraft = KeyboardDraft(layout, config.items.filter { it.layoutId == id }.map { it.copy(rowSpan = 1) }), library = library)
    } else {
        androidx.compose.material3.Scaffold { padding ->
            Column(Modifier.padding(padding)) {
                Text(stringResource(R.string.keyboard_layout_unavailable))
                TextButton(onClick = onNavigateBack) { Text(stringResource(R.string.button_navigate_up)) }
            }
        }
    }
}

internal class KeyboardLibraryState(
    val repository: org.connectbot.data.keyboard.KeyboardRepository,
    val configuration: kotlinx.coroutines.flow.StateFlow<KeyboardConfiguration>,
    val error: kotlinx.coroutines.flow.MutableStateFlow<String?>,
    val perform: (suspend () -> Unit) -> Unit,
)

internal fun KeyboardViewModel.libraryState() = KeyboardLibraryState(repository, configuration, error, ::perform)
