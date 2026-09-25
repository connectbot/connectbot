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

package org.connectbot.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.DialogProperties

/**
 * An [AlertDialog] containing a single [OutlinedTextField] that automatically gains focus,
 * brings up the software keyboard, and connects keyboard actions and action buttons to [onConfirm].
 */
@Composable
fun TextInputAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    confirmButtonText: String = stringResource(android.R.string.ok),
    dismissButtonText: String = stringResource(android.R.string.cancel),
    confirmButtonTestTag: String? = null,
    dismissButtonTestTag: String? = null,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    message: (@Composable () -> Unit)? = null,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    isPassword: Boolean = false,
    visualTransformation: VisualTransformation = if (isPassword) {
        PasswordVisualTransformation()
    } else {
        VisualTransformation.None
    },
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
        imeAction = ImeAction.Done,
    ),
    keyboardActions: KeyboardActions = KeyboardActions(
        onDone = { if (confirmEnabled) onConfirm() },
        onGo = { if (confirmEnabled) onConfirm() },
    ),
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    properties: DialogProperties = DialogProperties(),
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    val actualConfirmButton: @Composable () -> Unit = confirmButton ?: {
        TextButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = if (confirmButtonTestTag != null) Modifier.testTag(confirmButtonTestTag) else Modifier,
        ) {
            Text(confirmButtonText)
        }
    }

    val actualDismissButton: (@Composable () -> Unit)? = dismissButton ?: {
        TextButton(
            onClick = onDismissRequest,
            enabled = dismissEnabled,
            modifier = if (dismissButtonTestTag != null) Modifier.testTag(dismissButtonTestTag) else Modifier,
        ) {
            Text(dismissButtonText)
        }
    }

    FocusableAlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = actualConfirmButton,
        modifier = modifier,
        dismissButton = actualDismissButton,
        icon = icon,
        title = title,
        properties = properties,
    ) { focusRequester ->
        Column {
            message?.invoke()
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = textFieldModifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                enabled = enabled,
                label = label,
                placeholder = placeholder,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                supportingText = supportingText,
                isError = isError,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
                maxLines = maxLines,
                minLines = minLines,
            )
        }
    }
}
