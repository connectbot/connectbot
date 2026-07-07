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

package org.connectbot.ui.screens.console.tmux

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.connectbot.R

/** One palette entry: the command that ran and what tmux answered. */
data class TmuxPaletteEntry(
    val command: String,
    val output: String,
    val isError: Boolean,
)

/**
 * The tmux command palette: control mode has no prefix key, so this is the
 * power-user escape hatch — any raw tmux command runs against the attached
 * session (bare targets resolve to the viewed window/pane, which the app
 * mirrors server-side). History entries re-fill the field when tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TmuxCommandPaletteSheet(
    history: List<TmuxPaletteEntry>,
    onRunCommand: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var command by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("tmux_command_palette"),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.tmux_palette_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.tmux_palette_hint)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tmux_palette_input"),
                )
                IconButton(
                    onClick = {
                        val trimmed = command.trim()
                        if (trimmed.isNotEmpty()) {
                            onRunCommand(trimmed)
                            command = ""
                        }
                    },
                    enabled = command.isNotBlank(),
                    modifier = Modifier.testTag("tmux_palette_run"),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.tmux_palette_run),
                    )
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(bottom = 16.dp),
            ) {
                items(history.asReversed()) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { command = entry.command },
                    ) {
                        Text(
                            text = "› ${entry.command}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (entry.output.isNotEmpty()) {
                            Text(
                                text = entry.output,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (entry.isError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
