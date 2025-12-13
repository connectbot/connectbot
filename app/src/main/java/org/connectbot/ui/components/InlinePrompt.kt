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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
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
    modifier: Modifier = Modifier,
    onDismissed: () -> Unit = {},
) {
    var wasVisible by remember { mutableStateOf(false) }

    // Track when prompt becomes invisible to call onDismissed
    LaunchedEffect(promptRequest) {
        if (wasVisible && promptRequest == null) {
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

            is PromptRequest.BiometricPrompt -> {
                // Biometric prompts are handled by BiometricPromptHandler in PromptDialogs.kt
                // which uses the system BiometricPrompt dialog
                BiometricPromptHandler(
                    prompt = promptRequest,
                    onResponse = onResponse
                )
            }

            is PromptRequest.HostKeyFingerprintPrompt -> {
                HostKeyFingerprintPromptContent(
                    prompt = promptRequest,
                    onAccept = { onResponse(PromptResponse.BooleanResponse(true)) },
                    onReject = { onResponse(PromptResponse.BooleanResponse(false)) }
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
                imeAction = ImeAction.Done,
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Unspecified,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onSubmit(text)
                }
            ),
            singleLine = true,
            colors = TextFieldDefaults.colors(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostKeyFingerprintPromptContent(
    prompt: PromptRequest.HostKeyFingerprintPrompt,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val terminalColors = MaterialTheme.colorScheme.terminal
    val clipboardManager = LocalClipboardManager.current

    // Fingerprint format options
    val formats = listOf(
        stringResource(R.string.fingerprint_format_sha256) to prompt.sha256,
        stringResource(R.string.fingerprint_format_randomart) to prompt.randomArt,
        stringResource(R.string.fingerprint_format_bubblebabble) to prompt.bubblebabble,
        stringResource(R.string.fingerprint_format_md5) to prompt.md5
    )

    var selectedFormatIndex by remember { mutableIntStateOf(0) } // SHA-256 is first (default)
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(terminalColors.overlayBackground)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.host_key_verification_title),
            style = MaterialTheme.typography.titleMedium,
            color = terminalColors.overlayText,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(
            text = prompt.hostname,
            style = MaterialTheme.typography.bodyLarge,
            color = terminalColors.overlayTextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.host_key_type_and_size, prompt.keyType, prompt.keySize),
            style = MaterialTheme.typography.bodyMedium,
            color = terminalColors.overlayText
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Display selected fingerprint with copy button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formats[selectedFormatIndex].second,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = terminalColors.overlayText,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            )

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(formats[selectedFormatIndex].second))
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.fingerprint_copy_description),
                    tint = terminalColors.overlayTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fingerprint format selector
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it },
        ) {
            TextField(
                value = formats[selectedFormatIndex].first,
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.bodySmall,
                label = {
                    Text(stringResource(R.string.fingerprint_format_header))
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = terminalColors.overlayText,
                    unfocusedTextColor = terminalColors.overlayText,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = terminalColors.overlayTextSecondary,
                    unfocusedIndicatorColor = terminalColors.overlayTextSecondary.copy(alpha = 0.5f),
                    focusedLabelColor = terminalColors.overlayTextSecondary,
                    unfocusedLabelColor = terminalColors.overlayTextSecondary
                ),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                formats.forEachIndexed { index, (label, _) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedFormatIndex = index
                            dropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.prompt_continue_connecting),
            style = MaterialTheme.typography.bodyLarge,
            color = terminalColors.overlayText
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onReject) {
                Text(stringResource(R.string.button_no), color = terminalColors.overlayText)
            }
            Button(
                onClick = onAccept,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.button_yes))
            }
        }
    }
}

@Preview
@Composable
private fun HostKeyFingerprintPromptPreview() {
    HostKeyFingerprintPromptContent(
        prompt = PromptRequest.HostKeyFingerprintPrompt(
            hostname = "example.com",
            keyType = "Ed25519",
            keySize = 256,
            serverHostKey = byteArrayOf(),
            randomArt = "wobble",
            bubblebabble = "babble",
            sha256 = "sha256",
            md5 = "md5"
        ),
        onAccept = { },
        onReject = { },
    )
}
