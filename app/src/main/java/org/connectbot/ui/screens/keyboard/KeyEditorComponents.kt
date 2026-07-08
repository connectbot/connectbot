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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.keyboard.KeyIconCatalog
import org.connectbot.keyboard.KeySpec
import org.connectbot.keyboard.ModifierKey
import org.connectbot.keyboard.SpecialKey

/** Symbol characters offered as ready-made text keys. */
private val SYMBOL_KEYS = listOf("/", "|", "-", ":", ";", "~", ".", "_", "'", "\"", "*", "&")

private val SPECIAL_CATALOG = listOf(
    SpecialKey.ESC, SpecialKey.TAB, SpecialKey.ENTER,
    SpecialKey.UP, SpecialKey.DOWN, SpecialKey.LEFT, SpecialKey.RIGHT,
    SpecialKey.HOME, SpecialKey.END, SpecialKey.PGUP, SpecialKey.PGDN,
    SpecialKey.INS, SpecialKey.DEL,
)

private val FUNCTION_CATALOG = listOf(
    SpecialKey.F1, SpecialKey.F2, SpecialKey.F3, SpecialKey.F4,
    SpecialKey.F5, SpecialKey.F6, SpecialKey.F7, SpecialKey.F8,
    SpecialKey.F9, SpecialKey.F10, SpecialKey.F11, SpecialKey.F12,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyCatalogSheet(
    onDismiss: () -> Unit,
    onPick: (KeySpec) -> Unit,
) {
    var configuring by remember { mutableStateOf<KeySpec?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            CatalogSection(stringResource(R.string.keyboard_catalog_special)) {
                SPECIAL_CATALOG.forEach { key ->
                    AssistChip(onClick = { onPick(KeySpec.Special(key)) }, label = { Text(key.name) })
                }
            }
            CatalogSection(stringResource(R.string.keyboard_catalog_function)) {
                FUNCTION_CATALOG.forEach { key ->
                    AssistChip(onClick = { onPick(KeySpec.Special(key)) }, label = { Text(key.name) })
                }
            }
            CatalogSection(stringResource(R.string.keyboard_catalog_modifiers)) {
                ModifierKey.entries.forEach { mod ->
                    AssistChip(onClick = { onPick(KeySpec.Modifier(mod)) }, label = { Text(mod.name) })
                }
            }
            CatalogSection(stringResource(R.string.keyboard_catalog_symbols)) {
                SYMBOL_KEYS.forEach { sym ->
                    AssistChip(onClick = { onPick(KeySpec.Text(sym)) }, label = { Text(sym) })
                }
            }
            CatalogSection(stringResource(R.string.keyboard_catalog_other)) {
                AssistChip(onClick = { onPick(KeySpec.FnGrid()) }, label = { Text(stringResource(R.string.button_key_fn)) })
                AssistChip(
                    onClick = { configuring = KeySpec.Text("") },
                    label = { Text(stringResource(R.string.keyboard_catalog_custom_text)) },
                )
                AssistChip(
                    onClick = { configuring = KeySpec.Combo(ctrl = true, ch = 'c') },
                    label = { Text(stringResource(R.string.keyboard_catalog_combo)) },
                )
            }
        }
    }

    configuring?.let { initial ->
        KeyConfigDialog(
            initial = initial,
            onConfirm = {
                configuring = null
                onPick(it)
            },
            onDismiss = { configuring = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogSection(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/**
 * Configure a key's label/icon plus type-specific options (custom text or a
 * modifier combo). Used both for creating custom keys and editing any key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyConfigDialog(
    initial: KeySpec,
    onConfirm: (KeySpec) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var label by remember { mutableStateOf(initial.label ?: "") }
    var icon by remember { mutableStateOf(initial.icon) }

    // Text-key state.
    var text by remember { mutableStateOf((initial as? KeySpec.Text)?.text ?: "") }
    var sendEnter by remember { mutableStateOf((initial as? KeySpec.Text)?.sendEnter ?: false) }

    // Combo-key state.
    val combo = initial as? KeySpec.Combo
    var ctrl by remember { mutableStateOf(combo?.ctrl ?: false) }
    var alt by remember { mutableStateOf(combo?.alt ?: false) }
    var shift by remember { mutableStateOf(combo?.shift ?: false) }
    var comboChar by remember { mutableStateOf(combo?.ch?.toString() ?: "") }

    fun build(): KeySpec {
        val cleanLabel = label.ifBlank { null }
        return when (initial) {
            is KeySpec.Text -> KeySpec.Text(text = text, sendEnter = sendEnter, label = cleanLabel, icon = icon)

            is KeySpec.Combo -> KeySpec.Combo(
                ctrl = ctrl,
                alt = alt,
                shift = shift,
                ch = comboChar.firstOrNull(),
                special = initial.special,
                label = cleanLabel,
                icon = icon,
            )

            is KeySpec.Special -> initial.copy(label = cleanLabel, icon = icon)

            is KeySpec.Modifier -> initial.copy(label = cleanLabel, icon = icon)

            is KeySpec.FnGrid -> initial.copy(label = cleanLabel, icon = icon)
        }
    }

    val confirmEnabled = when (initial) {
        is KeySpec.Text -> text.isNotEmpty() || label.isNotBlank()
        is KeySpec.Combo -> comboChar.isNotEmpty() && (ctrl || alt || shift)
        else -> true
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_key_config_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (initial is KeySpec.Text) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.keyboard_key_config_text)) },
                        supportingText = { Text(stringResource(R.string.keyboard_key_config_text_hint)) },
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = sendEnter, onCheckedChange = { sendEnter = it })
                        Text(
                            stringResource(R.string.keyboard_key_config_send_enter),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                if (initial is KeySpec.Combo) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModifierCheckbox("Ctrl", ctrl) { ctrl = it }
                        ModifierCheckbox("Alt", alt) { alt = it }
                        ModifierCheckbox("Shift", shift) { shift = it }
                    }
                    OutlinedTextField(
                        value = comboChar,
                        onValueChange = { comboChar = it.takeLast(1) },
                        label = { Text(stringResource(R.string.keyboard_key_config_combo_char)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = KeyboardType.Ascii,
                        ),
                    )
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.keyboard_key_config_label)) },
                    singleLine = true,
                )

                Text(
                    stringResource(R.string.keyboard_key_config_icon),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
                IconPicker(selected = icon, onSelect = { icon = if (icon == it) null else it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(build()) }, enabled = confirmEnabled) {
                Text(stringResource(R.string.button_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        },
    )
}

@Composable
private fun ModifierCheckbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.toggleable(value = checked, onValueChange = onChange),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyIconCatalog.entries.forEach { (id, vector) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = {
                    Icon(vector, contentDescription = id, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}
