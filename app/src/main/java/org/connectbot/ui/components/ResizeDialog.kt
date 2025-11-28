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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.service.TerminalBridge

@Composable
fun ResizeDialog(
    currentBridge: TerminalBridge,
    onDismiss: () -> Unit,
    onResize: (Int, Int) -> Unit
) {
    val dimensions = currentBridge.terminalEmulator.dimensions

    var widthText by remember { mutableStateOf(dimensions.columns.toString()) }
    var heightText by remember { mutableStateOf(dimensions.rows.toString()) }
    var widthError by remember { mutableStateOf(false) }
    var heightError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_menu_resize)) },
        text = {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = {
                        widthText = it
                        widthError = it.toIntOrNull() == null || it.toInt() <= 0
                    },
                    label = { Text(stringResource(R.string.resize_width_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = widthError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = heightText,
                    onValueChange = {
                        heightText = it
                        heightError = it.toIntOrNull() == null || it.toInt() <= 0
                    },
                    label = { Text(stringResource(R.string.resize_height_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = heightError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val width = widthText.toIntOrNull()
                    val height = heightText.toIntOrNull()

                    if (width != null && width > 0 && height != null && height > 0) {
                        onResize(width, height)
                        onDismiss()
                    }
                },
                enabled = !widthError && !heightError &&
                        widthText.isNotEmpty() && heightText.isNotEmpty()
            ) {
                Text(stringResource(R.string.button_resize))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_neg))
            }
        }
    )
}
