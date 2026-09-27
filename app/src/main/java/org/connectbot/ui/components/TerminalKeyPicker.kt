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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.terminal.TerminalKey
import org.connectbot.terminal.TerminalKeyCatalog
import org.connectbot.util.TerminalKeyModifiers

@Composable
fun TerminalKey.displayName(): String = when {
    character == ' ' -> stringResource(R.string.terminal_key_space)

    character != null -> character.toString()

    target.startsWith("keypad_") -> stringResource(
        R.string.terminal_key_keypad,
        when (target.removePrefix("keypad_")) {
            "multiply" -> "*"
            "plus" -> "+"
            "comma" -> ","
            "minus" -> "-"
            "period" -> "."
            "divide" -> "/"
            "equal" -> "="
            "enter" -> stringResource(R.string.automation_key_enter)
            else -> target.removePrefix("keypad_")
        },
    )

    else -> when (target) {
        "enter" -> stringResource(R.string.automation_key_enter)
        "tab" -> stringResource(R.string.automation_key_tab)
        "escape" -> stringResource(R.string.automation_key_escape)
        "backspace" -> stringResource(R.string.automation_key_backspace)
        "delete" -> stringResource(R.string.automation_key_delete)
        "arrow_up" -> stringResource(R.string.automation_key_up)
        "arrow_down" -> stringResource(R.string.automation_key_down)
        "arrow_left" -> stringResource(R.string.automation_key_left)
        "arrow_right" -> stringResource(R.string.automation_key_right)
        "home" -> stringResource(R.string.automation_key_home)
        "end" -> stringResource(R.string.automation_key_end)
        "insert" -> stringResource(R.string.automation_key_insert)
        "page_up" -> stringResource(R.string.automation_key_page_up)
        "page_down" -> stringResource(R.string.automation_key_page_down)
        else -> stringResource(R.string.automation_key_function, target.removePrefix("f"))
    }
}

/** The same key and modifier selection is used for automation and keyboard buttons. */
@Composable
fun TerminalKeyPicker(
    target: String,
    modifiers: Int,
    onKeyChange: (TerminalKey) -> Unit,
    onModifiersChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.automation_key) + ": " + TerminalKeyCatalog.byTarget(target)?.displayName().orEmpty())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                TerminalKeyCatalog.entries.forEach { key ->
                    DropdownMenuItem(text = { Text(key.displayName()) }, onClick = {
                        onKeyChange(key)
                        expanded = false
                    })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                TerminalKeyModifiers.SHIFT to R.string.automation_key_shift,
                TerminalKeyModifiers.CTRL to R.string.button_key_ctrl,
                TerminalKeyModifiers.ALT to R.string.button_key_alt,
            ).forEach { (mask, label) ->
                FilterChip(
                    selected = modifiers and mask != 0,
                    onClick = { onModifiersChange(modifiers xor mask) },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
private fun defaultKeyLabel(key: TerminalKey, modifiers: Int): String {
    val character = key.character?.uppercaseChar()
    if (modifiers == TerminalKeyModifiers.CTRL && character != null && (character in '@'..'_' || character == '?')) {
        return "^$character"
    }
    val prefixes = buildList {
        if (modifiers and TerminalKeyModifiers.CTRL != 0) add(stringResource(R.string.button_key_ctrl))
        if (modifiers and TerminalKeyModifiers.ALT != 0) add(stringResource(R.string.button_key_alt))
        if (modifiers and TerminalKeyModifiers.SHIFT != 0) add(stringResource(R.string.automation_key_shift))
    }
    return (prefixes + key.displayName()).joinToString("+")
}

@Composable
fun TerminalKeyButtonDialog(
    onDismiss: () -> Unit,
    onConfirm: (TerminalKey, Int, String) -> Unit,
    initialTarget: String = "enter",
    initialModifiers: Int = TerminalKeyModifiers.NONE,
    initialLabel: String? = null,
) {
    var target by rememberSaveable { mutableStateOf(initialTarget) }
    var modifiers by rememberSaveable { mutableIntStateOf(initialModifiers) }
    var customLabel by rememberSaveable { mutableStateOf(initialLabel) }
    val key = TerminalKeyCatalog.byTarget(target) ?: return
    val label = customLabel ?: defaultKeyLabel(key, modifiers)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_add_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalKeyPicker(target, modifiers, onKeyChange = { target = it.target }, onModifiersChange = { modifiers = it })
                OutlinedTextField(
                    value = label,
                    onValueChange = { customLabel = it },
                    label = { Text(stringResource(R.string.keyboard_button_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = label.isNotBlank(), onClick = { onConfirm(key, modifiers, label) }) {
                Text(stringResource(R.string.keyboard_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.keyboard_cancel)) } },
    )
}
