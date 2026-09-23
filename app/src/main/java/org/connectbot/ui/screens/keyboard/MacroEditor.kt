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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.ui.components.keyboardLabel
import java.util.Collections

@Composable
internal fun MacroEditor(macro: KeyboardMacro, onSave: (KeyboardMacro) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val decoded = remember(macro.id) { runCatching { MacroFormat.decode(macro.document) } }
    var name by remember(macro.id) { mutableStateOf(macro.name) }
    var actions by remember(macro.id) { mutableStateOf(decoded.getOrDefault(emptyList())) }
    var source by remember(macro.id) { mutableStateOf(MacroFormat.format(actions)) }
    var textMode by remember(macro.id) { mutableStateOf(false) }
    var error by remember(macro.id) { mutableStateOf(decoded.exceptionOrNull()?.message) }
    val validation = remember(actions, source, textMode) {
        runCatching { if (textMode) MacroFormat.encode(MacroFormat.parse(source)) else MacroFormat.encode(actions) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.keyboard_name)) })
        if (decoded.isFailure) {
            Text(stringResource(R.string.keyboard_unsupported))
            Text(macro.document)
        } else {
            TextButton(onClick = {
                try {
                    if (textMode) {
                        actions = MacroFormat.parse(source)
                    } else {
                        MacroFormat.encode(actions)
                        source = MacroFormat.format(actions)
                    }
                    textMode = !textMode
                    error = null
                } catch (e: Exception) {
                    error = e.message
                }
            }) { Text(stringResource(if (textMode) R.string.keyboard_visual_editor else R.string.keyboard_text_editor)) }
            if (textMode) {
                Text(stringResource(R.string.keyboard_macro_help))
                OutlinedTextField(value = source, onValueChange = {
                    source = it
                    error = runCatching { MacroFormat.parse(it) }.exceptionOrNull()?.message
                }, modifier = Modifier.fillMaxWidth(), minLines = 5, label = { Text(stringResource(R.string.keyboard_steps)) }, supportingText = { Text(error.orEmpty()) }, isError = error != null)
            } else {
                actions.forEachIndexed { index, action ->
                    Card {
                        Column(Modifier.padding(8.dp)) {
                            ActionEditor(action, onChange = { replacement -> actions = actions.toMutableList().also { it[index] = replacement } })
                            Row {
                                TextButton(enabled = index > 0, onClick = { actions = actions.toMutableList().also { Collections.swap(it, index, index - 1) } }) { Text(stringResource(R.string.keyboard_move_up)) }
                                TextButton(enabled = index < actions.lastIndex, onClick = { actions = actions.toMutableList().also { Collections.swap(it, index, index + 1) } }) { Text(stringResource(R.string.keyboard_move_down)) }
                                TextButton(onClick = { actions = actions.filterIndexed { i, _ -> i != index } }) { Text(stringResource(R.string.keyboard_delete)) }
                            }
                        }
                    }
                }
                Row {
                    TextButton(onClick = { actions = actions + MacroAction.Text("") }) { Text(stringResource(R.string.keyboard_add_text)) }
                    TextButton(onClick = { actions = actions + MacroAction.Key(key = "enter") }) { Text(stringResource(R.string.keyboard_add_key)) }
                    TextButton(onClick = { actions = actions + MacroAction.Bytes("") }) { Text(stringResource(R.string.keyboard_add_bytes)) }
                }
            }
            if (!textMode) (error ?: validation.exceptionOrNull()?.message)?.let { Text(stringResource(R.string.keyboard_error, it), color = MaterialTheme.colorScheme.error) }
            Button(enabled = name.isNotBlank() && validation.isSuccess, onClick = {
                try {
                    val values = if (textMode) MacroFormat.parse(source) else actions
                    onSave(macro.copy(name = name, document = MacroFormat.encode(values)))
                } catch (e: Exception) {
                    error = e.message
                }
            }) { Text(stringResource(R.string.keyboard_save)) }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.keyboard_cancel)) }
    }
}

@Composable
private fun ActionEditor(action: MacroAction, onChange: (MacroAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        when (action) {
            is MacroAction.Text -> OutlinedTextField(value = action.text, onValueChange = { onChange(action.copy(text = it)) }, label = { Text(stringResource(R.string.keyboard_text)) })

            is MacroAction.Bytes -> OutlinedTextField(value = action.hex, onValueChange = { onChange(action.copy(hex = it.filterNot(Char::isWhitespace))) }, label = { Text(stringResource(R.string.keyboard_bytes)) })

            is MacroAction.Key -> {
                var expanded by remember { mutableStateOf(false) }
                val characterLabel = stringResource(R.string.keyboard_character)
                Box {
                    TextButton(onClick = { expanded = true }) { Text(action.key?.let { keyboardLabel(KeyboardItem(layoutId = KeyboardDefaults.layoutId, target = it), emptyList()) } ?: characterLabel) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(characterLabel) }, onClick = {
                            onChange(action.copy(key = null, character = "a"))
                            expanded = false
                        })
                        KeyboardDefaults.keys.forEach { key ->
                            DropdownMenuItem(text = { Text(keyboardLabel(KeyboardItem(layoutId = KeyboardDefaults.layoutId, target = key), emptyList())) }, onClick = {
                                onChange(action.copy(key = key, character = null))
                                expanded = false
                            })
                        }
                    }
                }
                if (action.character != null) OutlinedTextField(value = action.character, onValueChange = { onChange(action.copy(character = it)) }, label = { Text(characterLabel) })
                Row {
                    KeyboardDefaults.modifiers.forEach { value ->
                        Checkbox(checked = value in action.modifiers, onCheckedChange = { checked -> onChange(action.copy(modifiers = if (checked) action.modifiers + value else action.modifiers - value)) })
                        Text(keyboardLabel(KeyboardItem(layoutId = KeyboardDefaults.layoutId, kind = "modifier", target = value), emptyList()))
                    }
                }
            }
        }
    }
}
