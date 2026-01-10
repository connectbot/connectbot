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

package org.connectbot.ui.screens.fido2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.fido2.Fido2Algorithm
import org.connectbot.fido2.Fido2ConnectionState
import org.connectbot.fido2.Fido2Credential

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportFido2Screen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ImportFido2ViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? android.app.Activity

    // Handle USB discovery lifecycle only when USB transport is selected
    DisposableEffect(uiState.transportSelected, uiState.selectedTransport) {
        if (uiState.transportSelected && uiState.selectedTransport == Fido2Transport.USB) {
            viewModel.startUsbDiscovery()
        }
        onDispose {
            viewModel.stopUsbDiscovery()
        }
    }

    // Handle NFC discovery lifecycle only when NFC transport is selected
    DisposableEffect(lifecycleOwner, activity, uiState.transportSelected, uiState.selectedTransport) {
        val shouldUseNfc = uiState.transportSelected && uiState.selectedTransport == Fido2Transport.NFC
        val observer = LifecycleEventObserver { _, event ->
            if (activity != null && shouldUseNfc) {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> viewModel.startNfcDiscovery(activity)
                    Lifecycle.Event.ON_PAUSE -> viewModel.stopNfcDiscovery(activity)
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Also start immediately if we're already resumed
        if (shouldUseNfc) {
            activity?.let { viewModel.startNfcDiscovery(it) }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.let { viewModel.stopNfcDiscovery(it) }
        }
    }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Navigate back on successful import
    LaunchedEffect(uiState.importSuccess) {
        if (uiState.importSuccess) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fido2_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when {
                !uiState.transportSelected -> {
                    // First step: Select transport type (USB or NFC)
                    TransportSelectionContent(
                        selectedTransport = uiState.selectedTransport,
                        onTransportChange = viewModel::updateTransport,
                        onConfirm = viewModel::confirmTransportSelection,
                        onCancel = onNavigateBack
                    )
                }
                uiState.selectedCredential != null -> {
                    // Show import confirmation with nickname input
                    ImportConfirmationContent(
                        credential = uiState.selectedCredential!!,
                        nickname = uiState.nickname,
                        selectedTransport = uiState.selectedTransport,
                        onNicknameChange = viewModel::updateNickname,
                        onTransportChange = viewModel::updateTransport,
                        onConfirm = viewModel::importSelectedCredential,
                        onCancel = viewModel::clearSelection
                    )
                }
                uiState.needsPin -> {
                    // Show PIN entry (for NFC, this is shown before tapping)
                    PinEntryContent(
                        pinError = uiState.pinError,
                        onSubmitPin = viewModel::submitPin,
                        onCancel = onNavigateBack
                    )
                }
                uiState.waitingForNfcTap -> {
                    // PIN entered, waiting for NFC tap
                    WaitingForNfcTapContent()
                }
                uiState.credentials.isNotEmpty() -> {
                    // Show credential list
                    CredentialListContent(
                        credentials = uiState.credentials,
                        onSelectCredential = viewModel::selectCredential
                    )
                }
                else -> {
                    // Show connection status (USB waiting or scanning)
                    ConnectionStatusContent(
                        connectionState = uiState.connectionState,
                        isScanning = uiState.isScanning,
                        transport = uiState.selectedTransport
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportSelectionContent(
    selectedTransport: Fido2Transport,
    onTransportChange: (Fido2Transport) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.fido2_select_transport_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.fido2_select_transport_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = selectedTransport == Fido2Transport.USB,
                onClick = { onTransportChange(Fido2Transport.USB) },
                label = { Text(stringResource(R.string.fido2_transport_usb)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedTransport == Fido2Transport.NFC,
                onClick = { onTransportChange(Fido2Transport.NFC) },
                label = { Text(stringResource(R.string.fido2_transport_nfc)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.fido2_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.fido2_continue))
            }
        }
    }
}

@Composable
private fun ConnectionStatusContent(
    connectionState: Fido2ConnectionState,
    isScanning: Boolean,
    transport: Fido2Transport
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (transport == Fido2Transport.USB) Icons.Filled.Usb else Icons.Filled.Nfc,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when (connectionState) {
                is Fido2ConnectionState.Disconnected -> {
                    if (transport == Fido2Transport.USB) stringResource(R.string.fido2_connect_usb)
                    else stringResource(R.string.fido2_waiting_connection)
                }
                is Fido2ConnectionState.Connecting -> stringResource(R.string.fido2_waiting_connection)
                is Fido2ConnectionState.Connected -> {
                    if (isScanning) stringResource(R.string.fido2_scanning)
                    else stringResource(R.string.fido2_connected)
                }
                is Fido2ConnectionState.Error -> connectionState.message
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        if (connectionState is Fido2ConnectionState.Connecting || isScanning) {
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun WaitingForNfcTapContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.fido2_tap_and_hold),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.fido2_tap_and_hold_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PinEntryContent(
    pinError: String?,
    onSubmitPin: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.fido2_pin_title),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text(stringResource(R.string.fido2_pin_hint)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmitPin(pin) }
            ),
            isError = pinError != null,
            supportingText = pinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.fido2_cancel))
            }
            Button(
                onClick = { onSubmitPin(pin) },
                enabled = pin.isNotEmpty(),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.button_ok))
            }
        }
    }
}

@Composable
private fun CredentialListContent(
    credentials: List<Fido2Credential>,
    onSelectCredential: (Fido2Credential) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(credentials) { credential ->
            CredentialCard(
                credential = credential,
                onClick = { onSelectCredential(credential) }
            )
        }
    }
}

@Composable
private fun CredentialCard(
    credential: Fido2Credential,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Key,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = credential.userName ?: "SSH Key",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = when (credential.algorithm) {
                        Fido2Algorithm.ES256 -> "SK-ECDSA (P-256)"
                        Fido2Algorithm.EDDSA -> "SK-Ed25519"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = credential.rpId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImportConfirmationContent(
    credential: Fido2Credential,
    nickname: String,
    selectedTransport: Fido2Transport,
    onNicknameChange: (String) -> Unit,
    onTransportChange: (Fido2Transport) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.fido2_import_credential),
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (credential.algorithm) {
                Fido2Algorithm.ES256 -> "SK-ECDSA (P-256)"
                Fido2Algorithm.EDDSA -> "SK-Ed25519"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChange,
            label = { Text(stringResource(R.string.prompt_nickname)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Transport selection
        Text(
            text = stringResource(R.string.fido2_transport_label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTransport == Fido2Transport.USB,
                onClick = { onTransportChange(Fido2Transport.USB) },
                label = { Text(stringResource(R.string.fido2_transport_usb)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Usb,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedTransport == Fido2Transport.NFC,
                onClick = { onTransportChange(Fido2Transport.NFC) },
                label = { Text(stringResource(R.string.fido2_transport_nfc)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.fido2_cancel))
            }
            Button(
                onClick = onConfirm,
                enabled = nickname.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.fido2_import_credential))
            }
        }
    }
}
