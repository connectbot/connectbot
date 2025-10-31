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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse

@Composable
fun BooleanPromptDialog(
    prompt: PromptRequest.BooleanPrompt,
    onResponse: (PromptResponse.BooleanResponse) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = prompt.instructions?.let { { Text(it) } },
        text = { Text(prompt.message) },
        confirmButton = {
            TextButton(onClick = { onResponse(PromptResponse.BooleanResponse(true)) }) {
                Text(stringResource(R.string.button_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = { onResponse(PromptResponse.BooleanResponse(false)) }) {
                Text(stringResource(R.string.button_no))
            }
        }
    )
}

@Composable
fun StringPromptDialog(
    prompt: PromptRequest.StringPrompt,
    onResponse: (PromptResponse.StringResponse) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = prompt.instructions?.let { { Text(it) } },
        text = {
            Column {
                if (prompt.hint != null) {
                    Text(
                        text = prompt.hint,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = prompt.hint?.let { { Text(it) } },
                    visualTransformation = if (prompt.isPassword) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onResponse(PromptResponse.StringResponse(textValue)) }) {
                Text(stringResource(R.string.button_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { onResponse(PromptResponse.StringResponse(null)) }) {
                Text(stringResource(R.string.delete_neg))
            }
        }
    )
}

/**
 * Main composable that handles all prompt types
 */
@Composable
fun PromptDialog(
    promptRequest: PromptRequest?,
    onResponse: (PromptResponse) -> Unit,
    onDismiss: () -> Unit
) {
    when (promptRequest) {
        is PromptRequest.BooleanPrompt -> {
            BooleanPromptDialog(
                prompt = promptRequest,
                onResponse = onResponse,
                onDismiss = onDismiss
            )
        }
        is PromptRequest.StringPrompt -> {
            StringPromptDialog(
                prompt = promptRequest,
                onResponse = onResponse,
                onDismiss = onDismiss
            )
        }
        null -> {
            // No prompt to show
        }
    }
}
