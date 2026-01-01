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

package org.connectbot.ui.screens.pubkeylist

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.data.entity.Pubkey
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.components.rememberBiometricPromptState
import org.connectbot.ui.theme.ConnectBotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PubkeyListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToGenerate: () -> Unit,
    onNavigateToImportFido2: () -> Unit,
    onNavigateToEdit: (Pubkey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val terminalManager = LocalTerminalManager.current
    val viewModel: PubkeyListViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Set TerminalManager in ViewModel
    LaunchedEffect(terminalManager) {
        viewModel.terminalManager = terminalManager
    }

    // Show snackbar for errors
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // File picker for importing keys
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importKeyFromUri(it) }
    }

    // File saver for exporting private keys
    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportKeyToUri(uri)
        } else {
            viewModel.cancelExport()
        }
    }

    // File saver for exporting public keys
    val publicKeyFileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportPublicKeyToUri(uri)
        } else {
            viewModel.cancelPublicKeyExport()
        }
    }

    // Trigger file saver when private key export is requested
    LaunchedEffect(uiState.pendingExport) {
        uiState.pendingExport?.let {
            fileSaverLauncher.launch(viewModel.getExportFilename())
        }
    }

    // Trigger file saver when public key export is requested
    LaunchedEffect(uiState.pendingPublicKeyExport) {
        uiState.pendingPublicKeyExport?.let {
            publicKeyFileSaverLauncher.launch(viewModel.getPublicKeyExportFilename())
        }
    }

    // Biometric prompt for unlocking biometric keys
    // Returns null if FragmentActivity context is not available
    val biometricPromptState = rememberBiometricPromptState(
        onSuccess = { _ ->
            // Load the biometric key after successful authentication
            uiState.biometricKeyToUnlock?.let { pubkey ->
                viewModel.loadBiometricKey(pubkey)
            }
        },
        onError = { errorCode, errString ->
            // Show error in snackbar (unless user cancelled)
            if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                viewModel.onBiometricError(errString.toString())
            } else {
                viewModel.cancelBiometricAuth()
            }
        },
        onFailed = {
            // Don't do anything on failed attempt - user can retry
        }
    )

    // Trigger biometric prompt when needed
    LaunchedEffect(uiState.biometricKeyToUnlock, biometricPromptState) {
        uiState.biometricKeyToUnlock?.let { pubkey ->
            if (biometricPromptState != null) {
                biometricPromptState.authenticate(
                    title = context.getString(R.string.pubkey_biometric_prompt_title),
                    subtitle = context.getString(R.string.pubkey_biometric_prompt_subtitle, pubkey.nickname),
                    negativeButtonText = context.getString(android.R.string.cancel)
                )
            } else {
                // Biometric not available in this context, show error
                viewModel.onBiometricError(context.getString(R.string.pubkey_biometric_not_available))
            }
        }
    }

    // Password dialog for importing encrypted keys
    val pendingImport = uiState.pendingImport
    if (pendingImport != null) {
        ImportPasswordDialog(
            keyType = pendingImport.keyType,
            nickname = pendingImport.nickname,
            onDismiss = { viewModel.cancelImport() },
            onImport = { decryptPassword, encrypt, encryptPassword ->
                viewModel.completeImportWithPassword(decryptPassword, encrypt, encryptPassword)
            }
        )
    }

    PubkeyListScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToGenerate = onNavigateToGenerate,
        onNavigateToImportFido2 = onNavigateToImportFido2,
        onNavigateToEdit = onNavigateToEdit,
        onDeletePubkey = viewModel::deletePubkey,
        onToggleKeyLoaded = viewModel::toggleKeyLoaded,
        onCopyPublicKey = viewModel::copyPublicKey,
        onCopyPrivateKeyOpenSSH = { pubkey, onPasswordRequired ->
            viewModel.copyPrivateKeyOpenSSH(pubkey, onPasswordRequired)
        },
        onCopyPrivateKeyPem = { pubkey, onPasswordRequired ->
            viewModel.copyPrivateKeyPem(pubkey, onPasswordRequired)
        },
        onCopyPrivateKeyEncrypted = { pubkey, onPasswordRequired, onExportPassphraseRequired ->
            viewModel.copyPrivateKeyEncrypted(pubkey, onPasswordRequired, onExportPassphraseRequired)
        },
        onExportPublicKey = viewModel::requestExportPublicKey,
        onExportPrivateKeyOpenSSH = { pubkey, onPasswordRequired ->
            viewModel.requestExportPrivateKeyOpenSSH(pubkey, onPasswordRequired)
        },
        onExportPrivateKeyPem = { pubkey, onPasswordRequired ->
            viewModel.requestExportPrivateKeyPem(pubkey, onPasswordRequired)
        },
        onExportPrivateKeyEncrypted = { pubkey, onPasswordRequired, onExportPassphraseRequired ->
            viewModel.requestExportPrivateKeyEncrypted(pubkey, onPasswordRequired, onExportPassphraseRequired)
        },
        onImportKey = {
            filePickerLauncher.launch(arrayOf("*/*"))
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PubkeyListScreenContent(
    uiState: PubkeyListUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateToGenerate: () -> Unit,
    onNavigateToImportFido2: () -> Unit,
    onNavigateToEdit: (Pubkey) -> Unit,
    onDeletePubkey: (Pubkey) -> Unit,
    onToggleKeyLoaded: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPublicKey: (Pubkey) -> Unit,
    onCopyPrivateKeyOpenSSH: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPrivateKeyPem: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPrivateKeyEncrypted: (Pubkey, (Pubkey, (String) -> Unit) -> Unit, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPublicKey: (Pubkey) -> Unit,
    onExportPrivateKeyOpenSSH: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPrivateKeyPem: (Pubkey, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPrivateKeyEncrypted: (Pubkey, (Pubkey, (String) -> Unit) -> Unit, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onImportKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

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
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabMenuExpanded,
                button = {
                    ToggleFloatingActionButton(
                        checked = fabMenuExpanded,
                        onCheckedChange = { fabMenuExpanded = !fabMenuExpanded }
                    ) {
                        Icon(
                            painter = rememberVectorPainter(
                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                            ),
                            contentDescription = if (fabMenuExpanded) {
                                stringResource(android.R.string.cancel)
                            } else {
                                stringResource(R.string.pubkey_generate)
                            },
                            modifier = Modifier.animateIcon({ checkedProgress }),
                        )
                    }
                }
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        onNavigateToGenerate()
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.pubkey_generate)) }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        onImportKey()
                    },
                    icon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                    text = { Text(stringResource(R.string.pubkey_import_existing)) }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabMenuExpanded = false
                        onNavigateToImportFido2()
                    },
                    icon = { Icon(Icons.Default.Key, contentDescription = null) },
                    text = { Text(stringResource(R.string.pubkey_import_fido2)) }
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.pubkeys.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.empty_pubkeys_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 104.dp, // Extra padding to avoid FAB menu overlap (88dp + 16dp for menu padding)
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.pubkeys,
                            key = { it.id }
                        ) { pubkey ->
                            PubkeyListItem(
                                pubkey = pubkey,
                                isLoaded = uiState.loadedKeyNicknames.contains(pubkey.nickname),
                                onDelete = { onDeletePubkey(pubkey) },
                                onToggleLoaded = { onToggleKeyLoaded(pubkey, it) },
                                onCopyPublicKey = { onCopyPublicKey(pubkey) },
                                onCopyPrivateKeyOpenSSH = { onPasswordRequired ->
                                    onCopyPrivateKeyOpenSSH(pubkey, onPasswordRequired)
                                },
                                onCopyPrivateKeyPem = { onPasswordRequired ->
                                    onCopyPrivateKeyPem(pubkey, onPasswordRequired)
                                },
                                onCopyPrivateKeyEncrypted = { onPasswordRequired, onExportPassphraseRequired ->
                                    onCopyPrivateKeyEncrypted(pubkey, onPasswordRequired, onExportPassphraseRequired)
                                },
                                onExportPublicKey = { onExportPublicKey(pubkey) },
                                onExportPrivateKeyOpenSSH = { onPasswordRequired ->
                                    onExportPrivateKeyOpenSSH(pubkey, onPasswordRequired)
                                },
                                onExportPrivateKeyPem = { onPasswordRequired ->
                                    onExportPrivateKeyPem(pubkey, onPasswordRequired)
                                },
                                onExportPrivateKeyEncrypted = { onPasswordRequired, onExportPassphraseRequired ->
                                    onExportPrivateKeyEncrypted(pubkey, onPasswordRequired, onExportPassphraseRequired)
                                },
                                onEdit = { onNavigateToEdit(pubkey) },
                                onClick = { onToggleKeyLoaded(pubkey, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PubkeyListItem(
    pubkey: Pubkey,
    isLoaded: Boolean,
    onDelete: () -> Unit,
    onToggleLoaded: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPublicKey: () -> Unit,
    onCopyPrivateKeyOpenSSH: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPrivateKeyPem: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onCopyPrivateKeyEncrypted: ((Pubkey, (String) -> Unit) -> Unit, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPublicKey: () -> Unit,
    onExportPrivateKeyOpenSSH: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPrivateKeyPem: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onExportPrivateKeyEncrypted: ((Pubkey, (String) -> Unit) -> Unit, (Pubkey, (String) -> Unit) -> Unit) -> Unit,
    onEdit: () -> Unit,
    onClick: ((Pubkey, (String) -> Unit) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var passwordCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var exportPassphraseCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    ListItem(
        headlineContent = {
            Text(
                text = pubkey.nickname,
                fontWeight = FontWeight.Bold
            )
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.pubkey_type_label,
                    pubkey.type
                )
            )
        },
        leadingContent = {
            // FIDO2 keys always show locked icon (private key is on hardware)
            val icon = when {
                pubkey.isFido2 -> Icons.Outlined.Lock
                pubkey.isBiometric -> Icons.Outlined.Fingerprint
                pubkey.encrypted -> Icons.Outlined.Lock
                else -> Icons.Outlined.LockOpen
            }

            // FIDO2 keys never show loaded state (they're always "locked" on hardware)
            val showLoaded = isLoaded && !pubkey.isFido2
            val modifier = when {
                showLoaded -> Modifier
                    .padding(2.dp)
                    .border(
                        width = 2.dp, // Border thickness
                        color = Color.Green, // Border color
                        shape = CircleShape // Makes the border a circle
                    )
                    .clip(CircleShape)
                    .padding(4.dp)
                else -> Modifier.padding(2.dp).clip(CircleShape).padding(4.dp)
            }

            Box(
                modifier = modifier
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = when {
                        pubkey.isFido2 -> stringResource(R.string.pubkey_fido2_description)
                        pubkey.isBiometric -> stringResource(R.string.pubkey_biometric_description_icon)
                        pubkey.encrypted -> stringResource(R.string.pubkey_encrypted_description)
                        else -> stringResource(R.string.pubkey_not_encrypted_description)
                    },
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.button_more_options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // Edit key
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.list_pubkey_edit))
                        },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null)
                        }
                    )

                    // Copy public key
                    val isImported = pubkey.type == "IMPORTED"
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_copy_public)) },
                        onClick = {
                            showMenu = false
                            onCopyPublicKey()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null)
                        },
                        enabled = !isImported
                    )

                    // Export public key to file
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_export_public)) },
                        onClick = {
                            showMenu = false
                            onExportPublicKey()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, null)
                        },
                        enabled = !isImported
                    )

                    // Copy private key in OpenSSH format (not available for Keystore or FIDO2 keys)
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(
                                if (isImported)
                                    R.string.pubkey_copy_private
                                else
                                    R.string.pubkey_copy_private_openssh
                            ))
                        },
                        onClick = {
                            showMenu = false
                            onCopyPrivateKeyOpenSSH { _, callback ->
                                passwordCallback = callback
                                showPasswordDialog = true
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, null)
                        },
                        enabled = !pubkey.isBiometric && !pubkey.isFido2
                    )

                    // Copy private key in PEM format (for non-imported keys)
                    if (!isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_copy_private_pem)) },
                            onClick = {
                                showMenu = false
                                onCopyPrivateKeyPem { _, callback ->
                                    passwordCallback = callback
                                    showPasswordDialog = true
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, null)
                            },
                            enabled = !pubkey.isBiometric && !pubkey.isFido2
                        )
                    }

                    // Copy private key encrypted (for non-imported keys)
                    if (!isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_copy_private_encrypted)) },
                            onClick = {
                                showMenu = false
                                onCopyPrivateKeyEncrypted(
                                    { _, callback ->
                                        passwordCallback = callback
                                        showPasswordDialog = true
                                    },
                                    { _, callback ->
                                        exportPassphraseCallback = callback
                                        showExportPassphraseDialog = true
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, null)
                            },
                            enabled = !pubkey.isBiometric && !pubkey.isFido2
                        )
                    }

                    // Export private key to file in OpenSSH format
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(
                                if (isImported)
                                    R.string.pubkey_export_private
                                else
                                    R.string.pubkey_export_private_openssh
                            ))
                        },
                        onClick = {
                            showMenu = false
                            onExportPrivateKeyOpenSSH { _, callback ->
                                passwordCallback = callback
                                showPasswordDialog = true
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Default.FileDownload, null)
                        },
                        enabled = !pubkey.isBiometric && !pubkey.isFido2
                    )

                    // Export private key to file in PEM format (for non-imported keys)
                    if (!isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_export_private_pem)) },
                            onClick = {
                                showMenu = false
                                onExportPrivateKeyPem { _, callback ->
                                    passwordCallback = callback
                                    showPasswordDialog = true
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.FileDownload, null)
                            },
                            enabled = !pubkey.isBiometric && !pubkey.isFido2
                        )
                    }

                    // Export private key to file with encryption (for non-imported keys)
                    if (!isImported) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pubkey_export_private_encrypted)) },
                            onClick = {
                                showMenu = false
                                onExportPrivateKeyEncrypted(
                                    { _, callback ->
                                        passwordCallback = callback
                                        showPasswordDialog = true
                                    },
                                    { _, callback ->
                                        exportPassphraseCallback = callback
                                        showExportPassphraseDialog = true
                                    }
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, null)
                            },
                            enabled = !pubkey.isBiometric && !pubkey.isFido2
                        )
                    }

                    // Delete
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pubkey_delete)) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null)
                        }
                    )
                }
            }
        },
        // FIDO2 keys can't be "loaded" - they're always on hardware
        modifier = if (pubkey.isFido2) {
            modifier
        } else {
            modifier.clickable {
                onClick { targetPubkey, callback ->
                    // Show password dialog if needed
                    passwordCallback = callback
                    showPasswordDialog = true
                }
            }
        }
    )
    HorizontalDivider()

    // Password dialog for unlocking key
    if (showPasswordDialog && passwordCallback != null) {
        PubkeyPasswordDialog(
            pubkey = pubkey,
            onDismiss = {
                showPasswordDialog = false
                passwordCallback = null
            },
            onPasswordProvided = { password ->
                passwordCallback?.invoke(password)
                showPasswordDialog = false
                passwordCallback = null
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        PubkeyDeleteDialog(
            pubkey = pubkey,
            onDismiss = {
                showDeleteDialog = false
            },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            }
        )
    }

    // Export passphrase dialog
    if (showExportPassphraseDialog && exportPassphraseCallback != null) {
        ExportPassphraseDialog(
            onDismiss = {
                showExportPassphraseDialog = false
                exportPassphraseCallback = null
            },
            onPassphraseProvided = { passphrase ->
                exportPassphraseCallback?.invoke(passphrase)
                showExportPassphraseDialog = false
                exportPassphraseCallback = null
            }
        )
    }
}

@Composable
private fun PubkeyPasswordDialog(
    pubkey: Pubkey,
    onDismiss: () -> Unit,
    onPasswordProvided: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.pubkey_unlock)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pubkey_unlock_message, pubkey.nickname),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.prompt_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPasswordProvided(password) }
            ) {
                Text(stringResource(R.string.pubkey_unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun PubkeyDeleteDialog(
    pubkey: Pubkey,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
        title = { Text(stringResource(R.string.pubkey_delete)) },
        text = {
            Text(stringResource(R.string.delete_message, pubkey.nickname))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.delete_pos))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.delete_neg))
            }
        }
    )
}

@Composable
private fun ImportPasswordDialog(
    keyType: String,
    nickname: String,
    onDismiss: () -> Unit,
    onImport: (decryptPassword: String, encrypt: Boolean, encryptPassword: String?) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var encryptKey by remember { mutableStateOf(true) }
    var reusePassword by remember { mutableStateOf(true) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val canImport = password.isNotEmpty() && (
        !encryptKey ||
        reusePassword ||
        (newPassword.isNotEmpty() && newPassword == confirmPassword)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.pubkey_import_encrypted_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pubkey_import_encrypted_message, nickname, keyType),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.prompt_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clickable { encryptKey = !encryptKey }
                ) {
                    Checkbox(
                        checked = encryptKey,
                        onCheckedChange = { encryptKey = it }
                    )
                    Text(stringResource(R.string.pubkey_import_encrypt_key))
                }

                if (encryptKey) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reusePassword = !reusePassword }
                    ) {
                        Checkbox(
                            checked = reusePassword,
                            onCheckedChange = { reusePassword = it }
                        )
                        Text(stringResource(R.string.pubkey_import_reuse_password))
                    }

                    if (!reusePassword) {
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text(stringResource(R.string.pubkey_import_new_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text(stringResource(R.string.pubkey_import_confirm_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            singleLine = true,
                            isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val encryptPassword = when {
                        !encryptKey -> null
                        reusePassword -> password
                        else -> newPassword
                    }
                    onImport(password, encryptKey, encryptPassword)
                },
                enabled = canImport
            ) {
                Text(stringResource(R.string.pubkey_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun ExportPassphraseDialog(
    onDismiss: () -> Unit,
    onPassphraseProvided: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text(stringResource(R.string.pubkey_export_set_passphrase)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.pubkey_export_passphrase_message),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.prompt_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = confirmPassphrase,
                    onValueChange = {
                        confirmPassphrase = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.pubkey_export_confirm_passphrase)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                    isError = showError
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.pubkey_export_passphrase_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (passphrase == confirmPassphrase && passphrase.isNotEmpty()) {
                        onPassphraseProvided(passphrase)
                    } else {
                        showError = true
                    }
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenEmptyPreview() {
    ConnectBotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = emptyList(),
                isLoading = false
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToImportFido2 = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoaded = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKeyOpenSSH = { _, _ -> },
            onCopyPrivateKeyPem = { _, _ -> },
            onCopyPrivateKeyEncrypted = { _, _, _ -> },
            onExportPublicKey = {},
            onExportPrivateKeyOpenSSH = { _, _ -> },
            onExportPrivateKeyPem = { _, _ -> },
            onExportPrivateKeyEncrypted = { _, _, _ -> },
            onImportKey = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenLoadingPreview() {
    ConnectBotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = emptyList(),
                isLoading = true
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToImportFido2 = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoaded = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKeyOpenSSH = { _, _ -> },
            onCopyPrivateKeyPem = { _, _ -> },
            onCopyPrivateKeyEncrypted = { _, _, _ -> },
            onExportPublicKey = {},
            onExportPrivateKeyOpenSSH = { _, _ -> },
            onExportPrivateKeyPem = { _, _ -> },
            onExportPrivateKeyEncrypted = { _, _, _ -> },
            onImportKey = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun PubkeyListScreenPopulatedPreview() {
    ConnectBotTheme {
        PubkeyListScreenContent(
            uiState = PubkeyListUiState(
                pubkeys = listOf(
                    Pubkey(
                        id = 1,
                        nickname = "work-laptop",
                        type = "RSA",
                        encrypted = true,
                        startup = true,
                        confirmation = false,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0),
                    ),
                    Pubkey(
                        id = 2,
                        nickname = "home-server",
                        type = "Ed25519",
                        encrypted = false,
                        startup = false,
                        confirmation = true,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0),
                    ),
                    Pubkey(
                        id = 3,
                        nickname = "github-key",
                        type = "ECDSA",
                        encrypted = true,
                        startup = false,
                        confirmation = false,
                        createdDate = System.currentTimeMillis(),
                        privateKey = ByteArray(0),
                        publicKey = ByteArray(0),
                    )
                ),
                isLoading = false,
                loadedKeyNicknames = setOf("home-server")
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onNavigateToGenerate = {},
            onNavigateToImportFido2 = {},
            onNavigateToEdit = {},
            onDeletePubkey = {},
            onToggleKeyLoaded = { _, _ -> },
            onCopyPublicKey = {},
            onCopyPrivateKeyOpenSSH = { _, _ -> },
            onCopyPrivateKeyPem = { _, _ -> },
            onCopyPrivateKeyEncrypted = { _, _, _ -> },
            onExportPublicKey = {},
            onExportPrivateKeyOpenSSH = { _, _ -> },
            onExportPrivateKeyPem = { _, _ -> },
            onExportPrivateKeyEncrypted = { _, _, _ -> },
            onImportKey = {}
        )
    }
}
