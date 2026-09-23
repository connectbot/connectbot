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

package org.connectbot.ui.screens.automation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.entity.AutomationAction
import org.connectbot.data.entity.AutomationActionType
import org.connectbot.data.entity.AutomationFailurePolicy
import org.connectbot.data.entity.PortForward
import org.connectbot.service.automation.AutomationKeySupport
import org.connectbot.terminal.VTermKey
import org.connectbot.ui.components.SpecialCharVisualTransformation
import org.connectbot.util.TerminalKeyModifiers
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@StringRes
internal fun AutomationActionType.title(): Int = when (this) {
    AutomationActionType.WAIT_FOR_TEXT -> R.string.automation_wait_text
    AutomationActionType.SEND_TEXT -> R.string.automation_send_text
    AutomationActionType.SEND_KEY -> R.string.automation_send_key
    AutomationActionType.DELAY -> R.string.automation_delay
    AutomationActionType.DISCONNECT -> R.string.automation_disconnect
    AutomationActionType.ENABLE_FORWARD -> R.string.automation_enable_forward
    AutomationActionType.DISABLE_FORWARD -> R.string.automation_disable_forward
}

internal data class AutomationKey(@StringRes val label: Int?, val key: Int, val argument: String? = null) {
    @Composable
    fun displayName(): String = when {
        label == null -> requireNotNull(argument)
        argument == null -> stringResource(label)
        else -> stringResource(label, argument)
    }
}

internal val automationKeys = listOf(
    AutomationKey(R.string.automation_key_enter, VTermKey.ENTER), AutomationKey(R.string.automation_key_tab, VTermKey.TAB),
    AutomationKey(R.string.automation_key_escape, VTermKey.ESCAPE), AutomationKey(R.string.automation_key_backspace, VTermKey.BACKSPACE),
    AutomationKey(R.string.automation_key_delete, VTermKey.DEL), AutomationKey(R.string.automation_key_up, VTermKey.UP),
    AutomationKey(R.string.automation_key_down, VTermKey.DOWN), AutomationKey(R.string.automation_key_left, VTermKey.LEFT),
    AutomationKey(R.string.automation_key_right, VTermKey.RIGHT), AutomationKey(R.string.automation_key_home, VTermKey.HOME),
    AutomationKey(R.string.automation_key_end, VTermKey.END), AutomationKey(R.string.automation_key_insert, VTermKey.INS),
    AutomationKey(R.string.automation_key_page_up, VTermKey.PAGEUP), AutomationKey(R.string.automation_key_page_down, VTermKey.PAGEDOWN),
) + AutomationKeySupport.functionNumbers.map { AutomationKey(R.string.automation_key_function, VTermKey.FUNCTION_0 + it, argument = it.toString()) } +
    AutomationKeySupport.characterKeys.map {
        AutomationKey(null, it, AutomationKeySupport.characterCodePoint(it).toChar().toString())
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutomationEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Saving disables interaction and accessibility actions without flashing disabled colors.
    val buttonColors = ButtonDefaults.textButtonColors().let {
        it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor)
    }
    // This is computed outside Scaffold/TopAppBar, so do not inherit LocalContentColor.
    val iconButtonColors = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContentColor = MaterialTheme.colorScheme.onSurface,
    )
    val addCardColors = CardDefaults.cardColors().let {
        it.copy(disabledContainerColor = it.containerColor, disabledContentColor = it.contentColor)
    }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showExit by rememberSaveable { mutableStateOf(false) }
    val back = {
        if (!state.saving) {
            if (state.dirty) showExit = true else onNavigateBack()
        }
    }
    BackHandler(enabled = state.dirty, onBack = back)
    val list = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(list) { from, to ->
        if (!state.saving && to.index in state.actions.indices) {
            viewModel.move(from.key as String, to.index)
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hostpref_postlogin_title)) },
                navigationIcon = {
                    IconButton(onClick = back, enabled = !state.saving, colors = iconButtonColors) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.button_navigate_up))
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(state.dirty && !state.loading && state.hostExists) {
                FloatingActionButton(onClick = { if (!state.saving) scope.launch { viewModel.save() } }) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Save, stringResource(R.string.automation_save))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(16.dp))
            } else {
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    itemsIndexed(state.actions, key = { _, action -> action.id }) { index, action ->
                        val up = stringResource(R.string.automation_move_up)
                        val down = stringResource(R.string.automation_move_down)
                        val drag = stringResource(R.string.automation_drag)
                        ReorderableItem(reorderableState, key = action.id) { _ ->
                            val handleInteractionSource = remember { MutableInteractionSource() }
                            Card(
                                modifier = Modifier.fillMaxWidth()
                                    .semantics {
                                        customActions = listOf(
                                            CustomAccessibilityAction(up) {
                                                viewModel.move(action.id, index - 1)
                                                true
                                            },
                                            CustomAccessibilityAction(down) {
                                                viewModel.move(action.id, index + 1)
                                                true
                                            },
                                        )
                                    },
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(action.type.title()), style = MaterialTheme.typography.titleMedium)
                                        ActionSummary(action, state.forwards)
                                        Row {
                                            TextButton(onClick = { viewModel.edit(action) }, enabled = !state.saving, colors = buttonColors) { Text(stringResource(R.string.automation_edit)) }
                                            TextButton(onClick = { viewModel.delete(action.id) }, enabled = !state.saving, colors = buttonColors) { Text(stringResource(R.string.automation_delete)) }
                                        }
                                    }
                                    IconButton(
                                        onClick = {},
                                        enabled = !state.saving,
                                        colors = iconButtonColors,
                                        interactionSource = handleInteractionSource,
                                        modifier = Modifier.draggableHandle(
                                            enabled = !state.saving,
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                            },
                                            interactionSource = handleInteractionSource,
                                        ),
                                    ) {
                                        Icon(Icons.Default.DragHandle, contentDescription = drag)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(
                            onClick = { showAdd = true },
                            enabled = !state.saving && state.hostExists,
                            colors = if (state.hostExists) addCardColors else CardDefaults.cardColors(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(stringResource(R.string.automation_add))
                            }
                        }
                    }
                    item { Spacer(Modifier.size(96.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.automation_add)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AutomationActionType.entries.forEach { type ->
                        TextButton(
                            onClick = {
                                showAdd = false
                                viewModel.add(type)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(type.title()), modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
        )
    }
    state.editing?.let {
        ActionEditorDialog(it, state.forwards, state.editError, viewModel::dismissEdit, viewModel::applyEdit)
    }
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text(stringResource(R.string.automation_unsaved)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { if (viewModel.save()) onNavigateBack() } }, enabled = !state.saving) {
                    Text(stringResource(R.string.automation_save))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onNavigateBack, enabled = !state.saving) { Text(stringResource(R.string.automation_discard)) }
                    TextButton(onClick = { showExit = false }) { Text(stringResource(R.string.automation_cancel)) }
                }
            },
        )
    }
}

@Composable
private fun ActionSummary(action: AutomationAction, forwards: List<PortForward>) {
    val summary = when (action.type) {
        AutomationActionType.SEND_TEXT, AutomationActionType.WAIT_FOR_TEXT ->
            SpecialCharVisualTransformation.filter(AnnotatedString(action.text)).text.text

        AutomationActionType.DELAY -> stringResource(R.string.automation_seconds, action.durationMs / 1000.0)

        AutomationActionType.SEND_KEY -> {
            val keyName = automationKeys.find { it.key == action.key }?.displayName().orEmpty()
            val modifiers = buildList {
                if (action.modifiers and TerminalKeyModifiers.SHIFT != 0) add(stringResource(R.string.automation_key_shift))
                if (action.modifiers and TerminalKeyModifiers.CTRL != 0) add(stringResource(R.string.button_key_ctrl))
                if (action.modifiers and TerminalKeyModifiers.ALT != 0) add(stringResource(R.string.button_key_alt))
            }
            (modifiers + keyName).filter(String::isNotEmpty).joinToString("+")
        }

        AutomationActionType.ENABLE_FORWARD, AutomationActionType.DISABLE_FORWARD ->
            forwards.find { it.id == action.forwardId }?.nickname ?: stringResource(R.string.automation_forward_missing)

        AutomationActionType.DISCONNECT -> ""
    }
    if (summary.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = summary,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (action.type == AutomationActionType.SEND_TEXT || action.type == AutomationActionType.WAIT_FOR_TEXT) FontFamily.Monospace else null,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionEditorDialog(
    action: AutomationAction,
    forwards: List<PortForward>,
    error: Int?,
    onDismiss: () -> Unit,
    onSave: (AutomationAction) -> Unit,
) {
    val saver = remember {
        Saver<AutomationAction, String>(
            save = { AutomationDraftCodec.encode(listOf(it)) },
            restore = { AutomationDraftCodec.decode(it).single() },
        )
    }
    var draft by rememberSaveable(action.id, stateSaver = saver) { mutableStateOf(action) }
    var seconds by rememberSaveable(action.id) { mutableStateOf((action.durationMs / 1000.0).toString()) }
    var keyMenu by remember { mutableStateOf(false) }
    var forwardMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(action.type.title())) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (action.type == AutomationActionType.SEND_TEXT || action.type == AutomationActionType.WAIT_FOR_TEXT) {
                    OutlinedTextField(
                        value = draft.text,
                        onValueChange = { draft = draft.copy(text = it) },
                        label = { Text(stringResource(R.string.automation_text)) },
                        minLines = 3,
                        maxLines = 8,
                        visualTransformation = SpecialCharVisualTransformation,
                    )
                }
                if (action.type == AutomationActionType.WAIT_FOR_TEXT) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(draft.regex, onCheckedChange = { draft = draft.copy(regex = it) })
                        Text(stringResource(R.string.automation_regex))
                    }
                    Text(stringResource(R.string.automation_regex_help), style = MaterialTheme.typography.bodySmall)
                }
                if (action.type == AutomationActionType.WAIT_FOR_TEXT || action.type == AutomationActionType.DELAY) {
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { seconds = it },
                        singleLine = true,
                        label = { Text(stringResource(if (action.type == AutomationActionType.DELAY) R.string.automation_duration else R.string.automation_timeout)) },
                    )
                }
                if (action.type == AutomationActionType.SEND_KEY) {
                    Column {
                        TextButton(onClick = { keyMenu = true }) {
                            Text(stringResource(R.string.automation_key) + ": " + automationKeys.find { it.key == draft.key }?.displayName().orEmpty())
                        }
                        DropdownMenu(keyMenu, onDismissRequest = { keyMenu = false }) {
                            automationKeys.forEach { key ->
                                DropdownMenuItem(text = { Text(key.displayName()) }, onClick = {
                                    draft = draft.copy(key = key.key)
                                    keyMenu = false
                                })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                TerminalKeyModifiers.SHIFT to R.string.automation_key_shift,
                                TerminalKeyModifiers.CTRL to R.string.button_key_ctrl,
                                TerminalKeyModifiers.ALT to R.string.button_key_alt,
                            ).forEach { (mask, label) ->
                                FilterChip(
                                    selected = draft.modifiers and mask != 0,
                                    onClick = { draft = draft.copy(modifiers = draft.modifiers xor mask) },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                        }
                    }
                }
                if (action.type == AutomationActionType.ENABLE_FORWARD || action.type == AutomationActionType.DISABLE_FORWARD) {
                    Column {
                        TextButton(onClick = { forwardMenu = true }) {
                            Text(forwards.find { it.id == draft.forwardId }?.nickname ?: stringResource(R.string.automation_forward))
                        }
                        DropdownMenu(forwardMenu, onDismissRequest = { forwardMenu = false }) {
                            forwards.forEach { forward ->
                                DropdownMenuItem(text = { Text(forward.getDescription()) }, onClick = {
                                    draft = draft.copy(forwardId = forward.id)
                                    forwardMenu = false
                                })
                            }
                        }
                    }
                }
                if (action.type != AutomationActionType.DISCONNECT) {
                    Text(stringResource(R.string.automation_failure))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(draft.failurePolicy == AutomationFailurePolicy.CONTINUE, onCheckedChange = {
                            draft = draft.copy(failurePolicy = if (it) AutomationFailurePolicy.CONTINUE else AutomationFailurePolicy.STOP)
                        })
                        Text(stringResource(if (draft.failurePolicy == AutomationFailurePolicy.CONTINUE) R.string.automation_continue else R.string.automation_stop))
                    }
                }
                error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val duration = seconds.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= Long.MAX_VALUE / 1000.0 }?.let { (it * 1000).toLong() } ?: 0
                onSave(draft.copy(durationMs = duration))
            }) { Text(stringResource(R.string.automation_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.automation_cancel)) } },
    )
}
