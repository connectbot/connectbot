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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.ui.theme.terminal

@Composable
internal fun Fido2ConnectPromptContent(
    keyNickname: String,
    onCancel: () -> Unit,
) {
    val terminalColors = MaterialTheme.colorScheme.terminal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(terminalColors.overlayBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Usb,
            contentDescription = null,
            tint = terminalColors.overlayText,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 8.dp),
        )

        Text(
            text = stringResource(R.string.fido2_connect_title),
            style = MaterialTheme.typography.titleMedium,
            color = terminalColors.overlayText,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(
            text = stringResource(R.string.fido2_connect_message, keyNickname),
            style = MaterialTheme.typography.bodyMedium,
            color = terminalColors.overlayTextSecondary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        CircularProgressIndicator(
            color = terminalColors.overlayText,
            modifier = Modifier
                .size(32.dp)
                .padding(bottom = 16.dp),
        )

        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.fido2_cancel), color = terminalColors.overlayText)
        }
    }
}

/**
 * Overlay shown when waiting for NFC tap during SSH signing.
 */
@Composable
fun Fido2NfcTapOverlay(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val terminalColors = MaterialTheme.colorScheme.terminal

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(terminalColors.overlayBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.Nfc,
            contentDescription = null,
            tint = terminalColors.overlayText,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 8.dp),
        )

        Text(
            text = stringResource(R.string.fido2_nfc_tap_title),
            style = MaterialTheme.typography.titleMedium,
            color = terminalColors.overlayText,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(
            text = stringResource(R.string.fido2_nfc_tap_message),
            style = MaterialTheme.typography.bodyMedium,
            color = terminalColors.overlayTextSecondary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        CircularProgressIndicator(
            color = terminalColors.overlayText,
            modifier = Modifier
                .size(32.dp)
                .padding(bottom = 16.dp),
        )

        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.fido2_cancel), color = terminalColors.overlayText)
        }
    }
}

@Composable
internal fun Fido2PinPromptContent(
    keyNickname: String,
    attemptsRemaining: Int?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val terminalColors = MaterialTheme.colorScheme.terminal

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(terminalColors.overlayBackground)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                tint = terminalColors.overlayText,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 8.dp),
            )
            Text(
                text = stringResource(R.string.fido2_pin_title),
                style = MaterialTheme.typography.titleMedium,
                color = terminalColors.overlayText,
            )
        }

        Text(
            text = if (attemptsRemaining != null) {
                stringResource(R.string.fido2_pin_message_retries, attemptsRemaining)
            } else {
                stringResource(R.string.fido2_pin_message, keyNickname)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (attemptsRemaining != null && attemptsRemaining <= 3) {
                MaterialTheme.colorScheme.error
            } else {
                terminalColors.overlayTextSecondary
            },
            modifier = Modifier.padding(bottom = 16.dp),
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text(stringResource(R.string.fido2_pin_hint), color = terminalColors.overlayTextSecondary) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
                keyboardType = KeyboardType.Password,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit(pin) },
            ),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = terminalColors.overlayText,
                unfocusedTextColor = terminalColors.overlayText,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = terminalColors.overlayText,
                focusedIndicatorColor = terminalColors.overlayTextSecondary,
                unfocusedIndicatorColor = terminalColors.overlayTextSecondary.copy(alpha = 0.5f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.fido2_cancel), color = terminalColors.overlayText)
            }
            Button(
                onClick = { onSubmit(pin) },
                enabled = pin.isNotEmpty(),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(R.string.button_ok))
            }
        }
    }
}

@Composable
internal fun Fido2TouchPromptContent(
    keyNickname: String,
    onCancel: () -> Unit,
) {
    val terminalColors = MaterialTheme.colorScheme.terminal

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(terminalColors.overlayBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Filled.TouchApp,
            contentDescription = null,
            tint = terminalColors.overlayText,
            modifier = Modifier
                .size(48.dp)
                .padding(bottom = 8.dp),
        )

        Text(
            text = stringResource(R.string.fido2_touch_title),
            style = MaterialTheme.typography.titleMedium,
            color = terminalColors.overlayText,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Text(
            text = stringResource(R.string.fido2_touch_message, keyNickname),
            style = MaterialTheme.typography.bodyMedium,
            color = terminalColors.overlayTextSecondary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.fido2_cancel), color = terminalColors.overlayText)
        }
    }
}

private const val FIDO2_PREVIEW_NICKNAME = "My YubiKey"

@Preview
@Composable
private fun Fido2ConnectPromptPreview() {
    Fido2ConnectPromptContent(
        keyNickname = FIDO2_PREVIEW_NICKNAME,
        onCancel = { },
    )
}

@Preview
@Composable
private fun Fido2PinPromptPreview() {
    Fido2PinPromptContent(
        keyNickname = FIDO2_PREVIEW_NICKNAME,
        attemptsRemaining = 3,
        onSubmit = { },
        onCancel = { },
    )
}

@Preview
@Composable
private fun Fido2TouchPromptPreview() {
    Fido2TouchPromptContent(
        keyNickname = FIDO2_PREVIEW_NICKNAME,
        onCancel = { },
    )
}
