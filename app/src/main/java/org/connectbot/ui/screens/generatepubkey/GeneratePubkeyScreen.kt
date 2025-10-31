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

package org.connectbot.ui.screens.generatepubkey

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratePubkeyScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember { GeneratePubkeyViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_pubkey_generate)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
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
                onValueChange = { viewModel.updateNickname(it) },
                label = { Text(stringResource(R.string.prompt_nickname)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Key Type Selection
            Text(
                text = stringResource(R.string.pubkey_editor_key_type_label),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            KeyTypeOption(
                label = stringResource(R.string.pubkey_type_rsa),
                selected = uiState.keyType == KeyType.RSA,
                onClick = { viewModel.updateKeyType(KeyType.RSA) }
            )
            KeyTypeOption(
                label = stringResource(R.string.pubkey_type_dsa),
                selected = uiState.keyType == KeyType.DSA,
                onClick = { viewModel.updateKeyType(KeyType.DSA) }
            )
            KeyTypeOption(
                label = stringResource(R.string.pubkey_type_ecdsa),
                selected = uiState.keyType == KeyType.EC,
                onClick = { viewModel.updateKeyType(KeyType.EC) },
                enabled = uiState.ecdsaAvailable
            )
            KeyTypeOption(
                label = stringResource(R.string.pubkey_type_ed25519),
                selected = uiState.keyType == KeyType.ED25519,
                onClick = { viewModel.updateKeyType(KeyType.ED25519) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bit Strength
            if (uiState.allowBitStrengthChange) {
                Text(
                    text = "${stringResource(R.string.prompt_bits)} ${uiState.bits}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = uiState.bits.toFloat(),
                    onValueChange = { viewModel.updateBits(it.toInt()) },
                    valueRange = uiState.minBits.toFloat()..uiState.maxBits.toFloat(),
                    steps = ((uiState.maxBits - uiState.minBits) / 8) - 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Password
            OutlinedTextField(
                value = uiState.password1,
                onValueChange = { viewModel.updatePassword1(it) },
                label = { Text(stringResource(R.string.prompt_password)) },
                supportingText = { Text(stringResource(R.string.prompt_password_can_be_blank)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.password2,
                onValueChange = { viewModel.updatePassword2(it) },
                label = { Text("${stringResource(R.string.prompt_password)} ${stringResource(R.string.prompt_again)}") },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Options
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.updateUnlockAtStartup(!uiState.unlockAtStartup)
                    }
            ) {
                Checkbox(
                    checked = uiState.unlockAtStartup,
                    onCheckedChange = { viewModel.updateUnlockAtStartup(it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.pubkey_load_on_start))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.updateConfirmUse(!uiState.confirmUse)
                    }
            ) {
                Checkbox(
                    checked = uiState.confirmUse,
                    onCheckedChange = { viewModel.updateConfirmUse(it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.pubkey_confirm_use))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Generate Button
            Button(
                onClick = {
                    viewModel.generateKey(onSuccess = onNavigateBack)
                },
                enabled = uiState.canGenerate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pubkey_generate))
            }
        }
    }

    // Show entropy dialog when needed
    if (uiState.showEntropyDialog) {
        EntropyGatherDialog(
            onEntropyGathered = { entropy ->
                viewModel.onEntropyGathered(entropy)
            },
            onDismiss = {
                viewModel.cancelGeneration()
                onNavigateBack()
            }
        )
    }

    // Show progress dialog when generating
    if (uiState.isGenerating) {
        GeneratingKeyDialog()
    }
}

@Composable
private fun KeyTypeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 4.dp)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        )
    }
}
