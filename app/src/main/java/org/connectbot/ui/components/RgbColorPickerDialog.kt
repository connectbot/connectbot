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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.connectbot.R

/**
 * Dialog for selecting an RGB color with sliders.
 *
 * @param title The title of the dialog
 * @param initialColor The initial ARGB color value
 * @param onSelectColor Callback when a color is selected, receives ARGB Int
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun RgbColorPickerDialog(
    title: String,
    initialColor: Int,
    onSelectColor: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Extract RGB components from ARGB Int
    val initialRed = (initialColor shr 16) and 0xFF
    val initialGreen = (initialColor shr 8) and 0xFF
    val initialBlue = initialColor and 0xFF

    var red by remember { mutableFloatStateOf(initialRed.toFloat()) }
    var green by remember { mutableFloatStateOf(initialGreen.toFloat()) }
    var blue by remember { mutableFloatStateOf(initialBlue.toFloat()) }

    // Hex input state
    var hexInput by remember { mutableStateOf(String.format("%06X", initialColor and 0xFFFFFF)) }
    var isUpdatingFromSliders by remember { mutableStateOf(false) }

    // Construct current color from RGB sliders
    val currentColor = (0xFF shl 24) or
        (red.toInt() shl 16) or
        (green.toInt() shl 8) or
        blue.toInt()

    // Update hex input when sliders change
    LaunchedEffect(red, green, blue) {
        if (!isUpdatingFromSliders) {
            hexInput = String.format("%06X", currentColor and 0xFFFFFF)
        }
        isUpdatingFromSliders = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Color preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            color = Color(currentColor),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                )

                // Hex value input
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { newHex ->
                        // Strip # if present
                        val cleanedHex = newHex.removePrefix("#").uppercase()

                        // Only allow valid hex characters
                        if (cleanedHex.all { it in "0123456789ABCDEF" }) {
                            hexInput = cleanedHex

                            // Parse and update RGB values
                            val parsedColor = parseHexColor(cleanedHex)
                            if (parsedColor != null) {
                                isUpdatingFromSliders = true
                                red = ((parsedColor shr 16) and 0xFF).toFloat()
                                green = ((parsedColor shr 8) and 0xFF).toFloat()
                                blue = (parsedColor and 0xFF).toFloat()
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.label_hex_color)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Red slider
                ColorSliderRow(
                    label = stringResource(R.string.label_red),
                    value = red,
                    color = Color.Red,
                    onValueChange = { red = it }
                )

                // Green slider
                ColorSliderRow(
                    label = stringResource(R.string.label_green),
                    value = green,
                    color = Color.Green,
                    onValueChange = { green = it }
                )

                // Blue slider
                ColorSliderRow(
                    label = stringResource(R.string.label_blue),
                    value = blue,
                    color = Color.Blue,
                    onValueChange = { blue = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelectColor(currentColor)
                onDismiss()
            }) {
                Text(stringResource(R.string.button_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

/**
 * Row with label, slider, and value display for a color component.
 */
@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value.toInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Parse hex color string to RGB integer.
 * Supports:
 * - 3 digits: "abc" -> "aabbcc"
 * - 6 digits: "aabbcc"
 * Returns null if invalid.
 */
private fun parseHexColor(hex: String): Int? = when (hex.length) {
    3 -> {
        // Expand abc to aabbcc
        val r = hex[0].toString().repeat(2)
        val g = hex[1].toString().repeat(2)
        val b = hex[2].toString().repeat(2)
        try {
            (r + g + b).toInt(16)
        } catch (e: NumberFormatException) {
            null
        }
    }

    6 -> {
        try {
            hex.toInt(16)
        } catch (e: NumberFormatException) {
            null
        }
    }

    else -> null
}
