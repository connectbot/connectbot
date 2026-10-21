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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.ui.components.RgbColorPickerDialog

@Composable
internal fun KeyboardButtonAppearance(item: KeyboardItem, onChange: (KeyboardItem) -> Unit) {
    var picking by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    KeyboardSettingsGroup(stringResource(R.string.keyboard_appearance), stringResource(R.string.keyboard_appearance_summary)) {
        val theme = MaterialTheme.colorScheme
        val backgroundLabel = stringResource(R.string.keyboard_background_color)
        val textLabel = stringResource(R.string.keyboard_text_color)
        val outlineLabel = stringResource(R.string.keyboard_outline_color)
        listOf(
            Triple("background", backgroundLabel, item.backgroundColor),
            Triple("text", textLabel, item.textColor),
            Triple("outline", outlineLabel, item.outlineColor),
        ).forEach { (target, label, color) ->
            Surface(onClick = { picking = target }, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        color = color?.let { Color(it) } ?: when (target) {
                            "background" -> theme.surface
                            "text" -> theme.onSurface
                            else -> theme.outline
                        },
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, theme.outline),
                    ) {}
                    Text(stringResource(R.string.keyboard_color_value, label, color?.let { "#%06X".format(it and 0xFFFFFF) } ?: stringResource(R.string.keyboard_theme_color)), modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
        val opacityLabel = stringResource(R.string.keyboard_background_opacity, ((item.backgroundOpacity ?: 0.85f) * 100).toInt())
        Text(opacityLabel)
        Slider(modifier = Modifier.semantics { contentDescription = opacityLabel }, value = item.backgroundOpacity ?: 0.85f, onValueChange = { onChange(item.copy(backgroundOpacity = it)) })
        val outlinedLabel = stringResource(R.string.keyboard_outlined)
        KeyboardSwitchRow(outlinedLabel, (item.outlineWidth ?: 1f) > 0f, { onChange(item.copy(outlineWidth = if (it) 1f else 0f)) })
        if ((item.outlineWidth ?: 1f) > 0f) {
            val thicknessLabel = stringResource(R.string.keyboard_outline_thickness, item.outlineWidth ?: 1f)
            Text(thicknessLabel)
            Slider(modifier = Modifier.semantics { contentDescription = thicknessLabel }, value = item.outlineWidth ?: 1f, onValueChange = { onChange(item.copy(outlineWidth = it)) }, valueRange = 0.5f..10f, steps = 18)
        }
        Text(stringResource(R.string.keyboard_appearance_state_help), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onChange(item.copy(backgroundColor = null, backgroundOpacity = null, textColor = null, outlineColor = null, outlineWidth = null)) }) {
            Text(stringResource(R.string.keyboard_reset_appearance))
        }
        val target = picking
        if (target != null) {
            RgbColorPickerDialog(
                title = when (target) {
                    "background" -> backgroundLabel
                    "text" -> textLabel
                    else -> outlineLabel
                },
                initialColor = when (target) {
                    "background" -> item.backgroundColor ?: theme.surface.toArgb()
                    "text" -> item.textColor ?: theme.onSurface.toArgb()
                    else -> item.outlineColor ?: theme.outline.toArgb()
                },
                onSelectColor = { color ->
                    onChange(
                        when (target) {
                            "background" -> item.copy(backgroundColor = color)
                            "text" -> item.copy(textColor = color)
                            else -> item.copy(outlineColor = color)
                        },
                    )
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
    }
}
