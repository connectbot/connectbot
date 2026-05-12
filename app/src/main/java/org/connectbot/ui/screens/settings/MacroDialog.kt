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

package org.connectbot.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.util.keybar.MacroEscape

/**
 * Add / edit dialog for a custom macro key bar button.
 *
 * Stateless from the caller's perspective: the caller owns the
 * visibility (whether to show the dialog at all) and supplies any
 * pre-fill values via [initialLabel] and [initialText]. The dialog
 * only calls [onSave] when both fields pass validation.
 *
 * Validation rules:
 *   - `label` is non-blank and at most [LABEL_MAX] characters
 *   - `text` is non-empty
 *   - `text` parses according to [MacroEscape] grammar
 *
 * @param isEdit `true` to render "Edit custom button", `false` for
 *   "Add custom button" (controls the dialog title only).
 */
@Composable
fun MacroDialog(
    isEdit: Boolean,
    initialLabel: String = "",
    initialText: String = "",
    onDismiss: () -> Unit,
    onSave: (label: String, text: String) -> Unit,
) {
    var label by remember { mutableStateOf(initialLabel) }
    var text by remember { mutableStateOf(initialText) }

    val labelError: String? = when {
        label.isEmpty() -> null  // allowed while typing; just disables Save
        label.length > LABEL_MAX -> stringResource(R.string.keybar_macro_label_too_long, LABEL_MAX)
        else -> null
    }
    val textError: String? = when (MacroEscape.validate(text)) {
        is MacroEscape.ValidationResult.Ok -> null
        is MacroEscape.ValidationResult.Invalid ->
            if (text.isEmpty()) null
            else stringResource(R.string.keybar_macro_invalid_escape)
    }
    val canSave by remember(label, text) {
        derivedStateOf {
            label.isNotBlank() &&
                label.length <= LABEL_MAX &&
                text.isNotEmpty() &&
                MacroEscape.validate(text) is MacroEscape.ValidationResult.Ok
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEdit) R.string.keybar_macro_dialog_title_edit
                    else R.string.keybar_macro_dialog_title_add,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.keybar_macro_label_field)) },
                    isError = labelError != null,
                    supportingText = labelError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.keybar_macro_sends_field)) },
                    isError = textError != null,
                    supportingText = textError?.let { msg -> { Text(msg) } },
                    minLines = 3,
                    maxLines = 6,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.keybar_macro_tip))
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = { onSave(label, text) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

private const val LABEL_MAX = 16
