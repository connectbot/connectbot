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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.repeatsOnHold

@Composable
internal fun KeyboardButtonBehavior(item: KeyboardItem, onChange: (KeyboardItem) -> Unit) {
    var choosingHaptics by rememberSaveable(item.id) { mutableStateOf(false) }
    val labels = listOf(
        null to stringResource(R.string.keyboard_haptics_app_setting),
        true to stringResource(R.string.keyboard_haptics_on),
        false to stringResource(R.string.keyboard_haptics_off),
    )
    KeyboardSettingsGroup(
        stringResource(R.string.keyboard_behavior),
        stringResource(R.string.keyboard_behavior_summary, stringResource(if (item.repeatsOnHold()) R.string.keyboard_repeat_on else R.string.keyboard_repeat_off), labels.first { it.first == item.hapticFeedback }.second),
    ) {
        if (item.kind == "key" || item.kind == "macro") {
            KeyboardSwitchRow(stringResource(R.string.keyboard_repeat_on_hold), item.repeatsOnHold(), { onChange(item.copy(repeatOnHold = it)) })
            if (item.repeatOnHold != null) TextButton(onClick = { onChange(item.copy(repeatOnHold = null)) }) { Text(stringResource(R.string.keyboard_repeat_default)) }
        }
        Box {
            KeyboardNavigationRow(stringResource(R.string.keyboard_haptics_value, labels.first { it.first == item.hapticFeedback }.second), "", { choosingHaptics = true })
            DropdownMenu(expanded = choosingHaptics, onDismissRequest = { choosingHaptics = false }) {
                labels.forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        onChange(item.copy(hapticFeedback = value))
                        choosingHaptics = false
                    })
                }
            }
        }
    }
}
