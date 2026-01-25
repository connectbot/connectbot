/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.ui.common.ColorOption
import org.connectbot.ui.common.getIconColors
import org.connectbot.util.IconStyle
import org.connectbot.util.ShortcutIconGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutCustomizationDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: (color: String?, iconStyle: IconStyle) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val iconColors = getIconColors()

    var useHostColor by remember { mutableStateOf(host.color != null) }
    var selectedColor by remember { mutableStateOf(host.color ?: iconColors.first().hexValue) }
    var selectedStyle by remember { mutableStateOf(IconStyle.TERMINAL) }

    var colorDropdownExpanded by remember { mutableStateOf(false) }
    var styleDropdownExpanded by remember { mutableStateOf(false) }

    val effectiveColor = if (useHostColor && host.color != null) host.color else selectedColor

    val previewSizePx = with(density) { 72.dp.toPx().toInt() }
    val previewBitmap: Bitmap = remember(effectiveColor, selectedStyle) {
        ShortcutIconGenerator.generatePreviewBitmap(context, effectiveColor, selectedStyle, previewSizePx)
    }

    val selectedColorDisplay = findColorDisplayName(effectiveColor, iconColors)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shortcut_customize_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = host.color != null) {
                            useHostColor = !useHostColor
                        }
                ) {
                    Checkbox(
                        checked = useHostColor,
                        onCheckedChange = null,
                        enabled = host.color != null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.shortcut_use_host_color),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (host.color != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = colorDropdownExpanded,
                    onExpandedChange = { if (!useHostColor) colorDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedColorDisplay,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !useHostColor,
                        singleLine = true,
                        label = { Text(stringResource(R.string.shortcut_icon_color)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = colorDropdownExpanded,
                        onDismissRequest = { colorDropdownExpanded = false }
                    ) {
                        iconColors.forEach { color ->
                            DropdownMenuItem(
                                text = { Text(color.localizedName) },
                                onClick = {
                                    selectedColor = color.hexValue
                                    colorDropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = styleDropdownExpanded,
                    onExpandedChange = { styleDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = getIconStyleDisplayName(selectedStyle),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text(stringResource(R.string.shortcut_icon_style)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = styleDropdownExpanded,
                        onDismissRequest = { styleDropdownExpanded = false }
                    ) {
                        IconStyle.entries.forEach { style ->
                            DropdownMenuItem(
                                text = { Text(getIconStyleDisplayName(style)) },
                                onClick = {
                                    selectedStyle = style
                                    styleDropdownExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(effectiveColor, selectedStyle) }) {
                Text(stringResource(R.string.shortcut_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun findColorDisplayName(colorValue: String?, iconColors: List<ColorOption>): String {
    if (colorValue == null) {
        return iconColors.first().localizedName
    }
    return iconColors.find { it.hexValue.equals(colorValue, ignoreCase = true) }?.localizedName
        ?: iconColors.find { it.englishName.equals(colorValue, ignoreCase = true) }?.localizedName
        ?: colorValue
}

@Composable
private fun getIconStyleDisplayName(style: IconStyle): String = when (style) {
    IconStyle.TERMINAL -> stringResource(R.string.icon_style_terminal)
    IconStyle.SERVER -> stringResource(R.string.icon_style_server)
    IconStyle.CLOUD -> stringResource(R.string.icon_style_cloud)
    IconStyle.KEY -> stringResource(R.string.icon_style_key)
}
