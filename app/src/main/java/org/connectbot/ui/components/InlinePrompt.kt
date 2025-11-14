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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
import org.connectbot.ui.theme.terminal

/**
 * Non-modal inline prompt that appears at the bottom of the screen,
 * similar to the old ConsoleActivity's prompt UI.
 */
@Composable
fun InlinePrompt(
    promptRequest: PromptRequest?,
    onResponse: (PromptResponse) -> Unit,
    onCancel: () -> Unit,
    onDismissed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var wasVisible by remember { mutableStateOf(false) }

    // Track when prompt becomes invisible to call onDismissed
    LaunchedEffect(promptRequest) {
        if (wasVisible && promptRequest == null) {
            // Prompt was just dismissed - wait for animation to complete
            kotlinx.coroutines.delay(300) // Match animation duration
            onDismissed()
        }
        wasVisible = promptRequest != null
    }

    AnimatedVisibility(
        visible = promptRequest != null,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        when (promptRequest) {
            is PromptRequest.BooleanPrompt -> {
                BooleanPromptContent(
                    instructions = promptRequest.instructions,
                    message = promptRequest.message,
                    onYes = { onResponse(PromptResponse.BooleanResponse(true)) },
                    onNo = { onResponse(PromptResponse.BooleanResponse(false)) }
                )
            }

            is PromptRequest.StringPrompt -> {
                StringPromptContent(
                    instructions = promptRequest.instructions,
                    hint = promptRequest.hint,
                    isPassword = promptRequest.isPassword,
                    onSubmit = { value -> onResponse(PromptResponse.StringResponse(value)) },
                    onCancel = onCancel
                )
            }

            null -> { /* No prompt */
            }
        }
    }
}

@Composable
private fun BooleanPromptContent(
    instructions: String?,
    message: String,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    val terminalColors = MaterialTheme.colorScheme.terminal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(terminalColors.overlayBackground)
            .padding(16.dp)
    ) {
        if (!instructions.isNullOrEmpty()) {
            Text(
                text = instructions,
                style = MaterialTheme.typography.bodyMedium,
                color = terminalColors.overlayText,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = terminalColors.overlayText,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onNo) {
                Text(stringResource(R.string.button_no), color = terminalColors.overlayText)
            }
            Button(
                onClick = onYes,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.button_yes))
            }
        }
    }
}

@Composable
private fun StringPromptContent(
    instructions: String?,
    hint: String?,
    isPassword: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val terminalColors = MaterialTheme.colorScheme.terminal

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(terminalColors.overlayBackground)
            .padding(16.dp)
    ) {
        if (!instructions.isNullOrEmpty()) {
            Text(
                text = instructions,
                style = MaterialTheme.typography.bodyMedium,
                color = terminalColors.overlayText,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = hint?.let { { Text(it, color = terminalColors.overlayTextSecondary) } },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onSubmit(text)
                }
            ),
            singleLine = true,
            colors = androidx.compose.material3.TextFieldDefaults.colors(
                focusedTextColor = terminalColors.overlayText,
                unfocusedTextColor = terminalColors.overlayText,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = terminalColors.overlayText,
                focusedIndicatorColor = terminalColors.overlayTextSecondary,
                unfocusedIndicatorColor = terminalColors.overlayTextSecondary.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.delete_neg), color = terminalColors.overlayText)
            }
            Button(
                onClick = { onSubmit(text) },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.button_ok))
            }
        }
    }
}
