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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import timber.log.Timber
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import org.connectbot.di.IoDispatcher
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.crypto.OpenSSHKeyEncoder
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.PEMEncoder
import com.trilead.ssh2.crypto.PublicKeyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalManager
import org.connectbot.util.BiometricKeyManager
import org.connectbot.util.PubkeyUtils
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

enum class ExportFormat {
    OPENSSH,
    PEM
}

data class PendingExport(
    val pubkey: Pubkey,
    val format: ExportFormat,
    val password: String? = null,  // Password to decrypt the key (if encrypted)
    val exportPassphrase: String? = null  // Passphrase to encrypt the exported file
)

data class PendingImport(
    val keyData: ByteArray,
    val nickname: String,
    val keyType: String  // Extracted key type (RSA, Ed25519, etc.)
)

data class PendingPublicKeyExport(
    val pubkey: Pubkey
)

sealed class ImportResult {
    data class Success(val pubkey: Pubkey) : ImportResult()
    data class NeedsPassword(val keyData: ByteArray, val nickname: String, val keyType: String) : ImportResult()
    object Failed : ImportResult()
}

data class PubkeyListUiState(
    val pubkeys: List<Pubkey> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val loadedKeyNicknames: Set<String> = emptySet(),
    // Biometric key that needs to be unlocked (triggers BiometricPrompt in UI)
    val biometricKeyToUnlock: Pubkey? = null,
    // Key pending export to file (triggers file picker in UI)
    val pendingExport: PendingExport? = null,
    // Public key pending export to file (triggers file picker in UI)
    val pendingPublicKeyExport: PendingPublicKeyExport? = null,
    // Key pending import that needs password (triggers password dialog in UI)
    val pendingImport: PendingImport? = null
)

@HiltViewModel
class PubkeyListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PubkeyRepository,
    private val dispatchers: CoroutineDispatchers
) : ViewModel() {

    private val _uiState = MutableStateFlow(PubkeyListUiState(isLoading = true))
    val uiState: StateFlow<PubkeyListUiState> = _uiState.asStateFlow()

    var terminalManager: TerminalManager? = null
        set(value) {
            field = value
            observeLoadedKeys()
        }

    init {
        observePubkeys()
    }

    private fun observePubkeys() {
        viewModelScope.launch {
            repository.observeAll().collect { pubkeys ->
                _uiState.update {
                    it.copy(pubkeys = pubkeys, isLoading = false, error = null)
                }
            }
        }
    }

    private fun observeLoadedKeys() {
        val manager = terminalManager ?: return

        viewModelScope.launch {
            manager.loadedKeysChangedFlow.collect { loadedKeyNicknames ->
                _uiState.update {
                    it.copy(loadedKeyNicknames = loadedKeyNicknames)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun deletePubkey(pubkey: Pubkey) {
        viewModelScope.launch {
            try {
                terminalManager?.removeKey(pubkey.nickname)
                repository.delete(pubkey)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Failed to delete key")
                }
            }
        }
    }

    fun toggleKeyLoaded(pubkey: Pubkey, onPasswordRequired: (Pubkey, (String) -> Unit) -> Unit) {
        val isLoaded = terminalManager?.isKeyLoaded(pubkey.nickname) == true

        if (isLoaded) {
            terminalManager?.removeKey(pubkey.nickname)
        } else {
            // Check if this is a biometric key
            if (pubkey.isBiometric) {
                // Set the key to unlock - UI will show BiometricPrompt
                _uiState.update { it.copy(biometricKeyToUnlock = pubkey) }
            } else if (pubkey.encrypted) {
                onPasswordRequired(pubkey) { password ->
                    loadKeyWithPassword(pubkey, password)
                }
            } else {
                loadKeyWithPassword(pubkey, null)
            }
        }
    }

    /**
     * Called when biometric authentication is cancelled or fails.
     */
    fun cancelBiometricAuth() {
        _uiState.update { it.copy(biometricKeyToUnlock = null) }
    }

    /**
     * Called when biometric authentication fails with an error.
     */
    fun onBiometricError(errorMessage: String) {
        _uiState.update {
            it.copy(
                biometricKeyToUnlock = null,
                error = errorMessage
            )
        }
    }

    /**
     * Load a biometric key after successful biometric authentication.
     * This creates a KeyHolder that references the Keystore alias.
     */
    fun loadBiometricKey(pubkey: Pubkey) {
        viewModelScope.launch {
            try {
                val alias = pubkey.keystoreAlias
                    ?: throw IllegalStateException("Biometric key missing keystore alias")

                val biometricKeyManager = BiometricKeyManager(context)
                val publicKey = biometricKeyManager.getPublicKey(alias)
                    ?: throw IllegalStateException("Could not retrieve public key from keystore")

                // Add the biometric key to TerminalManager
                terminalManager?.addBiometricKey(pubkey, alias, publicKey)

                // Clear the biometric key to unlock
                _uiState.update { it.copy(biometricKeyToUnlock = null) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load biometric key")
                _uiState.update {
                    it.copy(
                        error = "Failed to load biometric key: ${e.message}",
                        biometricKeyToUnlock = null
                    )
                }
            }
        }
    }

    private fun loadKeyWithPassword(pubkey: Pubkey, password: String?) {
        viewModelScope.launch {
            try {
                val keyPair = withContext(dispatchers.default) {
                    PubkeyUtils.convertToKeyPair(pubkey, password)
                }
                keyPair?.let { terminalManager?.addKey(pubkey, it, true) }
            } catch (_: PubkeyUtils.BadPasswordException) {
                _uiState.update {
                    it.copy(error = "Failed to unlock key: Bad password")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load key")
                _uiState.update {
                    it.copy(error = "Failed to load key: ${e.message}")
                }
            }
        }
    }

    fun copyPublicKey(pubkey: Pubkey) {
        viewModelScope.launch {
            try {
                val publicKeyString = withContext(dispatchers.default) {
                    // Check if this is an imported key
                    val isImported = pubkey.type == "IMPORTED"
                    if (isImported) {
                        throw IllegalArgumentException("Cannot export public key from imported key")
                    }

                    val pk = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                    PublicKeyUtils.toAuthorizedKeysFormat(pk, pubkey.nickname)
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Public Key", publicKeyString)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy public key")
                _uiState.update {
                    it.copy(error = "Failed to copy public key: ${e.message}")
                }
            }
        }
    }

    fun copyPrivateKeyOpenSSH(pubkey: Pubkey, onPasswordRequired: (Pubkey, (String) -> Unit) -> Unit) {
        val isImported = pubkey.type == "IMPORTED"

        if (pubkey.encrypted && !isImported) {
            // Encrypted key - request password
            onPasswordRequired(pubkey) { password ->
                copyPrivateKeyOpenSSHWithPassword(pubkey, password)
            }
            return
        }

        copyPrivateKeyOpenSSHWithPassword(pubkey, null)
    }

    private fun copyPrivateKeyOpenSSHWithPassword(pubkey: Pubkey, password: String?) {
        viewModelScope.launch {
            try {
                val isImported = pubkey.type == "IMPORTED"

                val privateKeyString = withContext(dispatchers.default) {
                    if (isImported) {
                        // For imported keys, just return the raw data
                        String(pubkey.privateKey ?: ByteArray(0))
                    } else {
                        // For all non-imported keys, export in OpenSSH format for compatibility
                        val privateKeyBytes = pubkey.privateKey ?: throw Exception("No private key data")
                        val pk = PubkeyUtils.decodePrivate(privateKeyBytes, pubkey.type, password)
                        val pub = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                        pk?.let { OpenSSHKeyEncoder.exportOpenSSH(it, pub, pubkey.nickname) }
                    }
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Private Key", privateKeyString)
                clipboard.setPrimaryClip(clip)
            } catch (_: PubkeyUtils.BadPasswordException) {
                _uiState.update {
                    it.copy(error = "Failed to decrypt key: Bad password")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy private key")
                _uiState.update {
                    it.copy(error = "Failed to copy private key: ${e.message}")
                }
            }
        }
    }

    /**
     * Copy private key in PKCS#8 PEM format.
     */
    fun copyPrivateKeyPem(pubkey: Pubkey, onPasswordRequired: (Pubkey, (String) -> Unit) -> Unit) {
        val isImported = pubkey.type == "IMPORTED"

        if (pubkey.encrypted && !isImported) {
            // Encrypted key - request password
            onPasswordRequired(pubkey) { password ->
                copyPrivateKeyPemWithPassword(pubkey, password)
            }
            return
        }

        copyPrivateKeyPemWithPassword(pubkey, null)
    }

    private fun copyPrivateKeyPemWithPassword(pubkey: Pubkey, password: String?) {
        viewModelScope.launch {
            try {
                val isImported = pubkey.type == "IMPORTED"

                val privateKeyString = withContext(dispatchers.default) {
                    if (isImported) {
                        // For imported keys, just return the raw data
                        String(pubkey.privateKey ?: ByteArray(0))
                    } else {
                        // For all non-imported keys, export as PKCS#8 PEM
                        val privateKeyBytes = pubkey.privateKey ?: throw Exception("No private key data")
                        val pk = PubkeyUtils.decodePrivate(privateKeyBytes, pubkey.type, password)
                        pk?.let { PEMEncoder.encodePrivateKey(it, null) }
                    }
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Private Key", privateKeyString)
                clipboard.setPrimaryClip(clip)
            } catch (_: PubkeyUtils.BadPasswordException) {
                _uiState.update {
                    it.copy(error = "Failed to decrypt key: Bad password")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy private key (PEM)")
                _uiState.update {
                    it.copy(error = "Failed to copy private key: ${e.message}")
                }
            }
        }
    }

    /**
     * Copy private key in encrypted OpenSSH format.
     * This first prompts for decryption password if needed, then for the export passphrase.
     */
    fun copyPrivateKeyEncrypted(
        pubkey: Pubkey,
        onPasswordRequired: (Pubkey, (String) -> Unit) -> Unit,
        onExportPassphraseRequired: (Pubkey, (String) -> Unit) -> Unit
    ) {
        val isImported = pubkey.type == "IMPORTED"

        if (pubkey.encrypted && !isImported) {
            // Encrypted key - request password first, then export passphrase
            onPasswordRequired(pubkey) { password ->
                onExportPassphraseRequired(pubkey) { exportPassphrase ->
                    copyPrivateKeyEncryptedWithPasswords(pubkey, password, exportPassphrase)
                }
            }
            return
        }

        // Not encrypted - just need export passphrase
        onExportPassphraseRequired(pubkey) { exportPassphrase ->
            copyPrivateKeyEncryptedWithPasswords(pubkey, null, exportPassphrase)
        }
    }

    private fun copyPrivateKeyEncryptedWithPasswords(pubkey: Pubkey, password: String?, exportPassphrase: String) {
        viewModelScope.launch {
            try {
                val privateKeyString = withContext(dispatchers.default) {
                    val privateKeyBytes = pubkey.privateKey ?: throw Exception("No private key data")
                    val pk = PubkeyUtils.decodePrivate(privateKeyBytes, pubkey.type, password)
                    val pub = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                    pk?.let { OpenSSHKeyEncoder.exportOpenSSH(it, pub, pubkey.nickname, exportPassphrase) }
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Private Key", privateKeyString)
                clipboard.setPrimaryClip(clip)
            } catch (_: PubkeyUtils.BadPasswordException) {
                _uiState.update {
                    it.copy(error = "Failed to decrypt key: Bad password")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to copy encrypted private key")
                _uiState.update {
                    it.copy(error = "Failed to copy private key: ${e.message}")
                }
            }
        }
    }

    /**
     * Request export of private key in OpenSSH format.
     * This sets the pending export state, which triggers the file picker in the UI.
     */
    fun requestExportPrivateKeyOpenSSH(pubkey: Pubkey, onPasswordRequired: (Pubkey, (String) -> Unit) -> Unit) {
        val isImported = pubkey.type == "IMPORTED"

        if (pubkey.encrypted && !isImported) {
            // Encrypted key - request password
            onPasswordRequired(pubkey) { password ->
                _uiState.update {
                    it.copy(pendingExport = PendingExport(pubkey, ExportFormat.OPENSSH, password))
                }
            }
            return
        }

        _uiState.update {
            it.copy(pendingExport = PendingExport(pubkey, ExportFormat.OPENSSH))
        }
    }

    /**
     * Request export of private key in PEM format.
     * This sets the pending export state, which triggers the file picker in the UI.
     */
    fun requestExportPrivateKeyPem(pubkey: Pubkey, onPasswordRequired: (Pubkey, (String) -> Unit) -> Unit) {
        val isImported = pubkey.type == "IMPORTED"

        if (pubkey.encrypted && !isImported) {
            // Encrypted key - request password
            onPasswordRequired(pubkey) { password ->
                _uiState.update {
                    it.copy(pendingExport = PendingExport(pubkey, ExportFormat.PEM, password))
                }
            }
            return
        }

        _uiState.update {
            it.copy(pendingExport = PendingExport(pubkey, ExportFormat.PEM))
        }
    }

    /**
     * Request export of private key in encrypted OpenSSH format.
     * This first prompts for decryption password if needed, then for the export passphrase.
     *
     * @param pubkey The key to export
     * @param onPasswordRequired Callback to request the key's decryption password
     * @param onExportPassphraseRequired Callback to request the export passphrase
     */
    fun requestExportPrivateKeyEncrypted(
        pubkey: Pubkey,
        onPasswordRequired: (Pubkey, (String) -> Unit) -> Unit,
        onExportPassphraseRequired: (Pubkey, (String) -> Unit) -> Unit
    ) {
        val isImported = pubkey.type == "IMPORTED"

        if (pubkey.encrypted && !isImported) {
            // Encrypted key - request password first, then export passphrase
            onPasswordRequired(pubkey) { password ->
                onExportPassphraseRequired(pubkey) { exportPassphrase ->
                    _uiState.update {
                        it.copy(pendingExport = PendingExport(
                            pubkey = pubkey,
                            format = ExportFormat.OPENSSH,
                            password = password,
                            exportPassphrase = exportPassphrase
                        ))
                    }
                }
            }
            return
        }

        // Not encrypted - just need export passphrase
        onExportPassphraseRequired(pubkey) { exportPassphrase ->
            _uiState.update {
                it.copy(pendingExport = PendingExport(
                    pubkey = pubkey,
                    format = ExportFormat.OPENSSH,
                    password = null,
                    exportPassphrase = exportPassphrase
                ))
            }
        }
    }

    /**
     * Clear the pending export state (called when file picker is cancelled).
     */
    fun cancelExport() {
        _uiState.update { it.copy(pendingExport = null) }
    }

    /**
     * Request export of public key.
     * This sets the pending public key export state, which triggers the file picker in the UI.
     */
    fun requestExportPublicKey(pubkey: Pubkey) {
        _uiState.update {
            it.copy(pendingPublicKeyExport = PendingPublicKeyExport(pubkey))
        }
    }

    /**
     * Clear the pending public key export state (called when file picker is cancelled).
     */
    fun cancelPublicKeyExport() {
        _uiState.update { it.copy(pendingPublicKeyExport = null) }
    }

    /**
     * Get the suggested filename for the current pending public key export.
     */
    fun getPublicKeyExportFilename(): String {
        val pending = _uiState.value.pendingPublicKeyExport ?: return "id_key.pub"
        val nickname = pending.pubkey.nickname.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return "${nickname}.pub"
    }

    /**
     * Export the pending public key to the given URI.
     */
    fun exportPublicKeyToUri(uri: Uri) {
        val pending = _uiState.value.pendingPublicKeyExport
        if (pending == null) {
            _uiState.update { it.copy(error = "No public key pending export") }
            return
        }

        viewModelScope.launch {
            try {
                val pubkey = pending.pubkey
                val isImported = pubkey.type == "IMPORTED"

                if (isImported) {
                    _uiState.update {
                        it.copy(
                            error = "Cannot export public key from imported key",
                            pendingPublicKeyExport = null
                        )
                    }
                    return@launch
                }

                val publicKeyString = withContext(dispatchers.default) {
                    val pk = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                    PublicKeyUtils.toAuthorizedKeysFormat(pk, pubkey.nickname)
                }

                withContext(dispatchers.io) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(publicKeyString.toByteArray(Charsets.UTF_8))
                    } ?: throw Exception("Could not open file for writing")
                }

                _uiState.update { it.copy(pendingPublicKeyExport = null) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export public key")
                _uiState.update {
                    it.copy(
                        error = "Failed to export public key: ${e.message}",
                        pendingPublicKeyExport = null
                    )
                }
            }
        }
    }

    /**
     * Get the suggested filename for the current pending export.
     */
    fun getExportFilename(): String {
        val pending = _uiState.value.pendingExport ?: return "id_key"
        val nickname = pending.pubkey.nickname.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return nickname
    }

    /**
     * Export the pending key to the given URI.
     */
    fun exportKeyToUri(uri: Uri) {
        val pending = _uiState.value.pendingExport
        if (pending == null) {
            _uiState.update { it.copy(error = "No key pending export") }
            return
        }

        viewModelScope.launch {
            try {
                val pubkey = pending.pubkey
                val isImported = pubkey.type == "IMPORTED"
                val password = pending.password
                val exportPassphrase = pending.exportPassphrase

                val privateKeyString = withContext(dispatchers.default) {
                    if (isImported) {
                        String(pubkey.privateKey ?: ByteArray(0))
                    } else {
                        val privateKeyBytes = pubkey.privateKey ?: throw Exception("No private key data")
                        when (pending.format) {
                            ExportFormat.OPENSSH -> {
                                val pk = PubkeyUtils.decodePrivate(privateKeyBytes, pubkey.type, password)
                                val pub = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                                pk?.let { OpenSSHKeyEncoder.exportOpenSSH(it, pub, pubkey.nickname, exportPassphrase) }
                            }
                            ExportFormat.PEM -> {
                                val pk = PubkeyUtils.decodePrivate(privateKeyBytes, pubkey.type, password)
                                pk?.let { PEMEncoder.encodePrivateKey(it, null) }
                            }
                        }
                    }
                }

                if (privateKeyString == null) {
                    _uiState.update {
                        it.copy(
                            error = "Failed to export private key",
                            pendingExport = null
                        )
                    }
                    return@launch
                }

                withContext(dispatchers.io) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(privateKeyString.toByteArray(Charsets.UTF_8))
                    } ?: throw Exception("Could not open file for writing")
                }

                _uiState.update { it.copy(pendingExport = null) }
            } catch (_: PubkeyUtils.BadPasswordException) {
                _uiState.update {
                    it.copy(
                        error = "Failed to decrypt key: Bad password",
                        pendingExport = null
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to export private key")
                _uiState.update {
                    it.copy(
                        error = "Failed to export private key: ${e.message}",
                        pendingExport = null
                    )
                }
            }
        }
    }

    fun importKeyFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = withContext(dispatchers.io) {
                    readKeyFromUri(uri)
                }

                when (result) {
                    is ImportResult.Success -> {
                        repository.save(result.pubkey)
                    }
                    is ImportResult.NeedsPassword -> {
                        // Set pending import - UI will show password dialog
                        _uiState.update {
                            it.copy(pendingImport = PendingImport(
                                keyData = result.keyData,
                                nickname = result.nickname,
                                keyType = result.keyType
                            ))
                        }
                    }
                    is ImportResult.Failed -> {
                        _uiState.update {
                            it.copy(error = "Failed to parse key file")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import key")
                _uiState.update {
                    it.copy(error = "Failed to import key: ${e.message}")
                }
            }
        }
    }

    /**
     * Complete the import of an encrypted key with the provided password.
     * @param decryptPassword Password to decrypt the imported key
     * @param encrypt Whether to encrypt the key for storage
     * @param encryptPassword Password to use for encryption (null if not encrypting)
     */
    fun completeImportWithPassword(decryptPassword: String, encrypt: Boolean, encryptPassword: String?) {
        val pending = _uiState.value.pendingImport ?: return

        viewModelScope.launch {
            try {
                val pubkey = withContext(dispatchers.io) {
                    decryptAndImportKey(pending.keyData, pending.nickname, decryptPassword, encrypt, encryptPassword)
                }

                if (pubkey != null) {
                    repository.save(pubkey)
                    _uiState.update {
                        it.copy(pendingImport = null)
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            error = "Failed to decrypt key: Bad password",
                            pendingImport = null
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to import encrypted key")
                _uiState.update {
                    it.copy(
                        error = "Failed to decrypt key: ${e.message}",
                        pendingImport = null
                    )
                }
            }
        }
    }

    /**
     * Cancel the pending import.
     */
    fun cancelImport() {
        _uiState.update { it.copy(pendingImport = null) }
    }

    private fun decryptAndImportKey(
        keyData: ByteArray,
        nickname: String,
        decryptPassword: String,
        encrypt: Boolean,
        encryptPassword: String?
    ): Pubkey? {
        val keyString = String(keyData)

        try {
            // Use PEMDecoder to decrypt the key
            val kp = PEMDecoder.decode(keyString.toCharArray(), decryptPassword)
            val algorithm = convertAlgorithmName(kp.private.algorithm)

            // Optionally re-encrypt the private key with the specified password
            val privateKeyBytes = if (encrypt && encryptPassword != null) {
                PubkeyUtils.getEncodedPrivate(kp.private, encryptPassword)
            } else {
                kp.private.encoded
            }

            return Pubkey(
                id = 0,
                nickname = nickname,
                type = algorithm,
                encrypted = encrypt,
                startup = false,
                confirmation = false,
                createdDate = System.currentTimeMillis(),
                privateKey = privateKeyBytes,
                publicKey = kp.public.encoded
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt key")
            return null
        }
    }

    private fun readKeyFromUri(uri: Uri): ImportResult {
        val nickname = getFilenameFromUri(uri) ?: "imported-key"

        // Read key data from URI
        val keyData = try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val outputStream = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0
                val maxSize = 32768 // MAX_KEYFILE_SIZE from PubkeyListActivity

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytes += bytesRead
                    if (totalBytes > maxSize) {
                        throw Exception("File too large (max 32KB)")
                    }
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.toByteArray()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to read key file")
            return ImportResult.Failed
        }

        if (keyData == null || keyData.isEmpty()) {
            return ImportResult.Failed
        }

        val keyString = String(keyData)

        // Try to parse using PEMDecoder first (handles OpenSSH, traditional PEM formats)
        try {
            val struct = PEMDecoder.parsePEM(keyString.toCharArray())
            val encrypted = PEMDecoder.isPEMEncrypted(struct)

            if (!encrypted) {
                // Unencrypted PEM - decode and convert to internal format
                val kp = PEMDecoder.decode(struct, null)
                val algorithm = convertAlgorithmName(kp.private.algorithm)
                return ImportResult.Success(Pubkey(
                    id = 0,
                    nickname = nickname,
                    type = algorithm,
                    encrypted = false,
                    startup = false,
                    confirmation = false,
                    createdDate = System.currentTimeMillis(),
                    privateKey = kp.private.encoded,
                    publicKey = kp.public.encoded
                ))
            } else {
                // Encrypted key - need password to decrypt
                val keyType = PublicKeyUtils.detectKeyType(keyString) ?: "IMPORTED"
                return ImportResult.NeedsPassword(keyData, nickname, keyType)
            }
        } catch (e: Exception) {
            Timber.d(e, "PEMDecoder failed, trying PKCS#8")
        }

        // Fallback: Try to parse as PKCS#8 format (-----BEGIN PRIVATE KEY-----)
        // Note: This only supports RSA, DSA, and EC keys, not Ed25519
        val keyPair = parsePKCS8Key(keyData)
        if (keyPair != null) {
            val algorithm = convertAlgorithmName(keyPair.private.algorithm)
            return ImportResult.Success(Pubkey(
                id = 0,
                nickname = nickname,
                type = algorithm,
                encrypted = false,
                startup = false,
                confirmation = false,
                createdDate = System.currentTimeMillis(),
                privateKey = keyPair.private.encoded,
                publicKey = keyPair.public.encoded
            ))
        }

        Timber.e("Failed to parse key in any supported format")
        return ImportResult.Failed
    }

    /**
     * Parse a PKCS#8 format key (-----BEGIN PRIVATE KEY-----) using PubkeyUtils.
     * This handles the PEM armor stripping and Base64 decoding.
     *
     * Note: PubkeyUtils.recoverKeyPair() only supports RSA, DSA, and EC keys.
     * Ed25519 keys are not supported via PKCS#8 format.
     * This is used as a fallback after PEMDecoder fails.
     */
    private fun parsePKCS8Key(keyData: ByteArray): java.security.KeyPair? {
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(keyData)))

        try {
            val keyBytes = ByteArrayOutputStream()
            var line: String?
            var inKey = false

            // Strip PEM armor and collect Base64 data
            while (reader.readLine().also { line = it } != null) {
                when {
                    line == PubkeyUtils.PKCS8_START -> inKey = true
                    line == PubkeyUtils.PKCS8_END -> break
                    inKey -> keyBytes.write(line!!.toByteArray(Charsets.US_ASCII))
                }
            }

            if (keyBytes.size() > 0) {
                // Decode Base64 and use PubkeyUtils to recover the KeyPair
                val decoded = Base64.decode(keyBytes.toString().toCharArray())
                return OpenSSHKeyEncoder.recoverKeyPair(decoded)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse PKCS#8 key")
        }

        return null
    }

    /**
     * Get the display filename from a content URI.
     */
    private fun getFilenameFromUri(uri: Uri): String? {
        var filename: String? = null

        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    filename = cursor.getString(nameIndex)
                }
            }
        }

        return filename
    }

    /**
     * Convert algorithm name from Java format to ConnectBot internal format.
     * EdDSA -> Ed25519
     */
    private fun convertAlgorithmName(algorithm: String): String {
        return if (algorithm == "EdDSA") {
            "Ed25519"
        } else {
            algorithm
        }
    }
}
