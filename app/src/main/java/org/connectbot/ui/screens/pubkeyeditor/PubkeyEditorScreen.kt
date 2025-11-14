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

package org.connectbot.ui.screens.pubkeyeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.theme.ConnectBotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyEditorScreen(
    pubkeyId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember { PubkeyEditorViewModel(context, pubkeyId) }
    val uiState by viewModel.uiState.collectAsState()

    // Navigate back on successful save
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onNavigateBack()
        }
    }

    PubkeyEditorScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onNicknameChange = viewModel::updateNickname,
        onOldPasswordChange = viewModel::updateOldPassword,
        onNewPassword1Change = viewModel::updateNewPassword1,
        onNewPassword2Change = viewModel::updateNewPassword2,
        onUnlockAtStartupChange = viewModel::updateUnlockAtStartup,
        onConfirmUseChange = viewModel::updateConfirmUse,
        onSave = viewModel::save,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyEditorScreenContent(
    uiState: PubkeyEditorUiState,
    onNavigateBack: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onOldPasswordChange: (String) -> Unit,
    onNewPassword1Change: (String) -> Unit,
    onNewPassword2Change: (String) -> Unit,
    onUnlockAtStartupChange: (Boolean) -> Unit,
    onConfirmUseChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_pubkey_list)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(padding)
                )
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.error_message, uiState.error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Nickname
                    OutlinedTextField(
                        value = uiState.nickname,
                        onValueChange = onNicknameChange,
                        label = { Text(stringResource(R.string.prompt_nickname)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Key Type (read-only)
                    OutlinedTextField(
                        value = uiState.keyType,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.pubkey_editor_key_type_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Change Password Section
                    Text(
                        text = stringResource(R.string.pubkey_change_password),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.isEncrypted) {
                        OutlinedTextField(
                            value = uiState.oldPassword,
                            onValueChange = onOldPasswordChange,
                            label = { Text(stringResource(R.string.prompt_old_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = uiState.newPassword1,
                        onValueChange = onNewPassword1Change,
                        label = { Text(stringResource(R.string.prompt_password)) },
                        supportingText = { Text(stringResource(R.string.prompt_password_can_be_blank)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uiState.newPassword2,
                        onValueChange = onNewPassword2Change,
                        label = {
                            Text(
                                "${stringResource(R.string.prompt_password)} ${
                                    stringResource(
                                        R.string.prompt_again
                                    )
                                }"
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = uiState.passwordMismatch
                    )

                    if (uiState.passwordMismatch) {
                        Text(
                            text = stringResource(R.string.alert_passwords_do_not_match_msg),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }

                    if (uiState.wrongPassword) {
                        Text(
                            text = stringResource(R.string.alert_wrong_password_msg),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Options
                    Text(
                        text = stringResource(R.string.pubkey_editor_options_label),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.willBeEncrypted) {
                                onUnlockAtStartupChange(!uiState.unlockAtStartup)
                            }
                    ) {
                        Checkbox(
                            checked = uiState.unlockAtStartup,
                            onCheckedChange = onUnlockAtStartupChange,
                            enabled = !uiState.willBeEncrypted
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.pubkey_load_on_start),
                            color = if (!uiState.willBeEncrypted) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }

                    if (uiState.willBeEncrypted && uiState.unlockAtStartup) {
                        Text(
                            text = stringResource(R.string.pubkey_editor_encrypted_startup_warning),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 48.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onConfirmUseChange(!uiState.confirmUse)
                            }
                    ) {
                        Checkbox(
                            checked = uiState.confirmUse,
                            onCheckedChange = onConfirmUseChange
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.pubkey_confirm_use))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Save Button
                    Button(
                        onClick = onSave,
                        enabled = uiState.canSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.portforward_save))
                    }
                }
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun PubkeyEditorScreenLoadingPreview() {
    ConnectBotTheme {
        PubkeyEditorScreenContent(
            uiState = PubkeyEditorUiState(
                isLoading = true
            ),
            onNavigateBack = {},
            onNicknameChange = {},
            onOldPasswordChange = {},
            onNewPassword1Change = {},
            onNewPassword2Change = {},
            onUnlockAtStartupChange = {},
            onConfirmUseChange = {},
            onSave = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyEditorScreenErrorPreview() {
    ConnectBotTheme {
        PubkeyEditorScreenContent(
            uiState = PubkeyEditorUiState(
                isLoading = false,
                error = "Failed to load public key"
            ),
            onNavigateBack = {},
            onNicknameChange = {},
            onOldPasswordChange = {},
            onNewPassword1Change = {},
            onNewPassword2Change = {},
            onUnlockAtStartupChange = {},
            onConfirmUseChange = {},
            onSave = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyEditorScreenUnencryptedPreview() {
    ConnectBotTheme {
        PubkeyEditorScreenContent(
            uiState = PubkeyEditorUiState(
                nickname = "work-laptop",
                keyType = "RSA",
                isEncrypted = false,
                unlockAtStartup = true,
                confirmUse = false,
                isLoading = false
            ),
            onNavigateBack = {},
            onNicknameChange = {},
            onOldPasswordChange = {},
            onNewPassword1Change = {},
            onNewPassword2Change = {},
            onUnlockAtStartupChange = {},
            onConfirmUseChange = {},
            onSave = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyEditorScreenEncryptedPreview() {
    ConnectBotTheme {
        PubkeyEditorScreenContent(
            uiState = PubkeyEditorUiState(
                nickname = "home-server",
                keyType = "Ed25519",
                isEncrypted = true,
                oldPassword = "",
                unlockAtStartup = false,
                confirmUse = true,
                isLoading = false
            ),
            onNavigateBack = {},
            onNicknameChange = {},
            onOldPasswordChange = {},
            onNewPassword1Change = {},
            onNewPassword2Change = {},
            onUnlockAtStartupChange = {},
            onConfirmUseChange = {},
            onSave = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyEditorScreenPasswordMismatchPreview() {
    ConnectBotTheme {
        PubkeyEditorScreenContent(
            uiState = PubkeyEditorUiState(
                nickname = "github-key",
                keyType = "ECDSA",
                isEncrypted = true,
                oldPassword = "oldpass123",
                newPassword1 = "newpass123",
                newPassword2 = "newpass456",
                unlockAtStartup = false,
                confirmUse = false,
                isLoading = false
            ),
            onNavigateBack = {},
            onNicknameChange = {},
            onOldPasswordChange = {},
            onNewPassword1Change = {},
            onNewPassword2Change = {},
            onUnlockAtStartupChange = {},
            onConfirmUseChange = {},
            onSave = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyEditorScreenWrongPasswordPreview() {
    ConnectBotTheme {
        PubkeyEditorScreenContent(
            uiState = PubkeyEditorUiState(
                nickname = "production",
                keyType = "RSA",
                isEncrypted = true,
                oldPassword = "wrongpass",
                newPassword1 = "newpass123",
                newPassword2 = "newpass123",
                unlockAtStartup = false,
                confirmUse = true,
                isLoading = false,
                wrongPassword = true
            ),
            onNavigateBack = {},
            onNicknameChange = {},
            onOldPasswordChange = {},
            onNewPassword1Change = {},
            onNewPassword2Change = {},
            onUnlockAtStartupChange = {},
            onConfirmUseChange = {},
            onSave = {}
        )
    }
}
