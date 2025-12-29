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

package org.connectbot.ui.screens.sftpbrowser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.connectbot.R

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_create_folder)) },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text(stringResource(R.string.sftp_folder_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text(stringResource(R.string.button_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

@Composable
fun GoToPathDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var path by remember { mutableStateOf(currentPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_go_to_path)) },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = { path = it },
                label = { Text(stringResource(R.string.sftp_path)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(path) },
                enabled = path.isNotBlank()
            ) {
                Text(stringResource(R.string.sftp_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    entry: SftpEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_delete)) },
        text = {
            Column {
                Text(stringResource(R.string.sftp_delete_confirm, entry.filename))
                if (entry.isDirectory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sftp_delete_folder_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.sftp_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

@Composable
fun TransferProgressDialog(
    progress: TransferProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Non-dismissable while transferring */ },
        title = {
            Text(
                if (progress.isUpload) {
                    stringResource(R.string.sftp_transfer_uploading, progress.filename)
                } else {
                    stringResource(R.string.sftp_transfer_downloading, progress.filename)
                }
            )
        },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { progress.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${formatBytes(progress.bytesTransferred)} / ${formatBytes(progress.totalBytes)} (${progress.percentComplete}%)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

@Composable
fun HostKeyDialog(
    info: HostKeyInfo,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = {
            Text(
                if (info.isNewKey) {
                    stringResource(R.string.sftp_host_key_new)
                } else {
                    stringResource(R.string.sftp_host_key_changed)
                }
            )
        },
        text = {
            Column {
                Text(stringResource(R.string.sftp_host_key_message, info.hostname))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${info.keyType} fingerprint:",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = info.fingerprint,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                if (!info.isNewKey) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sftp_host_key_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.sftp_host_key_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text(stringResource(R.string.sftp_host_key_reject))
            }
        }
    )
}

@Composable
fun PasswordDialog(
    prompt: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sftp_authentication)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(prompt) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(password) },
                enabled = password.isNotEmpty()
            ) {
                Text(stringResource(R.string.button_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

@Composable
fun KeyPassphraseDialog(
    prompt: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sftp_key_passphrase)) },
        text = {
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(prompt) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(passphrase) },
                enabled = passphrase.isNotEmpty()
            ) {
                Text(stringResource(R.string.button_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
