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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.data.ColorSchemePresets

/**
 * Dialog for selecting a color from the 256-color terminal palette.
 *
 * @param title The title of the dialog
 * @param selectedColorIndex The currently selected color index (0-15)
 * @param palette The current color palette (16 ARGB values)
 * @param onSelectColor Callback when a color is selected
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun ColorPickerDialog(
    title: String,
    selectedColorIndex: Int,
    onSelectColor: (Int) -> Unit,
    onDismiss: () -> Unit,
    palette: IntArray = ColorSchemePresets.default.colors
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.title_color_picker),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Grid of 16 colors (8 columns)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(palette.size) { index ->
                        val color = palette[index]
                        val isSelected = index == selectedColorIndex

                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .background(
                                    color = Color(color),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Gray.copy(alpha = 0.5f)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    onSelectColor(index)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Show index for first 16 ANSI colors for easier identification
                            if (index < 16 && isSelected) {
                                Text(
                                    text = index.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (index == 0 || (index in 1..6)) {
                                        Color.White
                                    } else {
                                        Color.Black
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_close))
            }
        }
    )
}
