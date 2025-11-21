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
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.crypto.PEMDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.TerminalManager
import org.connectbot.util.PubkeyUtils
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

data class PubkeyListUiState(
    val pubkeys: List<Pubkey> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val loadedKeyNicknames: Set<String> = emptySet()
)

class PubkeyListViewModel(
    private val context: Context,
    private val repository: PubkeyRepository = PubkeyRepository.get(context)
) : ViewModel() {

    private val _uiState = MutableStateFlow(PubkeyListUiState(isLoading = true))
    val uiState: StateFlow<PubkeyListUiState> = _uiState.asStateFlow()

    var terminalManager: TerminalManager? = null
        set(value) {
            field = value
            updateLoadedKeys()
        }

    init {
        loadPubkeys()
    }

    fun loadPubkeys() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val pubkeys = repository.getAll()
                _uiState.update {
                    it.copy(pubkeys = pubkeys, isLoading = false, error = null)
                }
                updateLoadedKeys()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load keys")
                }
            }
        }
    }

    private fun updateLoadedKeys() {
        terminalManager?.let { manager ->
            val loadedKeys = _uiState.value.pubkeys
                .map { it.nickname }
                .filter { manager.isKeyLoaded(it) }
                .toSet()
            _uiState.update { it.copy(loadedKeyNicknames = loadedKeys) }
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
                loadPubkeys()
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
            updateLoadedKeys()
        } else {
            if (pubkey.encrypted) {
                onPasswordRequired(pubkey) { password ->
                    loadKeyWithPassword(pubkey, password)
                }
            } else {
                loadKeyWithPassword(pubkey, null)
            }
        }
    }

    private fun loadKeyWithPassword(pubkey: Pubkey, password: String?) {
        viewModelScope.launch {
            try {
                val keyPair = withContext(Dispatchers.Default) {
                    PubkeyUtils.convertToKeyPair(pubkey, password)
                }
                keyPair?.let { terminalManager?.addKey(pubkey, it, true) }
                updateLoadedKeys()
            } catch (e: PubkeyUtils.BadPasswordException) {
                _uiState.update {
                    it.copy(error = "Failed to unlock key: Bad password")
                }
            } catch (e: Exception) {
                Log.e("PubkeyListViewModel", "Failed to load key", e)
                _uiState.update {
                    it.copy(error = "Failed to load key: ${e.message}")
                }
            }
        }
    }

    fun copyPublicKey(pubkey: Pubkey) {
        viewModelScope.launch {
            try {
                val publicKeyString = withContext(Dispatchers.Default) {
                    // Check if this is an imported key
                    val isImported = pubkey.type == "IMPORTED"
                    if (isImported) {
                        throw IllegalArgumentException("Cannot export public key from imported key")
                    }

                    val pk = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                    PubkeyUtils.convertToOpenSSHFormat(pk, pubkey.nickname)
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Public Key", publicKeyString)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                Log.e("PubkeyListViewModel", "Failed to copy public key", e)
                _uiState.update {
                    it.copy(error = "Failed to copy public key: ${e.message}")
                }
            }
        }
    }

    fun copyPrivateKey(pubkey: Pubkey) {
        viewModelScope.launch {
            try {
                val isImported = pubkey.type == "IMPORTED"

                if (pubkey.encrypted && !isImported) {
                    // Encrypted non-imported keys cannot be exported
                    _uiState.update {
                        it.copy(error = "Cannot copy encrypted private key. Remove password first.")
                    }
                    return@launch
                }

                val privateKeyString = withContext(Dispatchers.Default) {
                    if (isImported) {
                        // For imported keys, just return the raw data
                        String(pubkey.privateKey ?: ByteArray(0))
                    } else {
                        // For non-imported keys, export as PEM
                        val pk = PubkeyUtils.decodePrivate(pubkey.privateKey, pubkey.type)
                        pk?.let { PubkeyUtils.exportPEM(it, null) }
                    }
                }

                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Private Key", privateKeyString)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                Log.e("PubkeyListViewModel", "Failed to copy private key", e)
                _uiState.update {
                    it.copy(error = "Failed to copy private key: ${e.message}")
                }
            }
        }
    }

    fun importKeyFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val pubkey = withContext(Dispatchers.IO) {
                    readKeyFromUri(uri)
                }

                if (pubkey != null) {
                    repository.save(pubkey)
                    loadPubkeys()
                } else {
                    _uiState.update {
                        it.copy(error = "Failed to parse key file")
                    }
                }
            } catch (e: Exception) {
                Log.e("PubkeyListViewModel", "Failed to import key", e)
                _uiState.update {
                    it.copy(error = "Failed to import key: ${e.message}")
                }
            }
        }
    }

    private fun readKeyFromUri(uri: Uri): Pubkey? {
        val nickname = uri.lastPathSegment ?: "imported-key"

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
            Log.e("PubkeyListViewModel", "Failed to read key file", e)
            return null
        }

        if (keyData == null || keyData.isEmpty()) {
            return null
        }

        // Try to parse as PKCS#8 first (using PubkeyUtils)
        val keyPair = parsePKCS8Key(keyData)
        if (keyPair != null) {
            val algorithm = convertAlgorithmName(keyPair.private.algorithm)
            return Pubkey(
                id = 0,
                nickname = nickname,
                type = algorithm,
                encrypted = false,
                startup = false,
                confirmation = false,
                createdDate = System.currentTimeMillis(),
                privateKey = keyPair.private.encoded,
                publicKey = keyPair.public.encoded
            )
        }

        // Try to parse as PEM (using PEMDecoder from trilead)
        try {
            val struct = PEMDecoder.parsePEM(String(keyData).toCharArray())
            val encrypted = PEMDecoder.isPEMEncrypted(struct)

            if (!encrypted) {
                // Unencrypted PEM - decode and convert to internal format
                val kp = PEMDecoder.decode(struct, null)
                val algorithm = convertAlgorithmName(kp.private.algorithm)
                return Pubkey(
                    id = 0,
                    nickname = nickname,
                    type = algorithm,
                    encrypted = false,
                    startup = false,
                    confirmation = false,
                    createdDate = System.currentTimeMillis(),
                    privateKey = kp.private.encoded,
                    publicKey = kp.public.encoded
                )
            } else {
                // Encrypted PEM - store as IMPORTED type (keeps original PEM format)
                return Pubkey(
                    id = 0,
                    nickname = nickname,
                    type = "IMPORTED",
                    encrypted = true,
                    startup = false,
                    confirmation = false,
                    createdDate = System.currentTimeMillis(),
                    privateKey = keyData,
                    publicKey = ByteArray(0) // No public key available for encrypted imports
                )
            }
        } catch (e: Exception) {
            Log.e("PubkeyListViewModel", "Failed to parse PEM key", e)
            return null
        }
    }

    /**
     * Parse a PKCS#8 format key using PubkeyUtils.
     * This handles the PEM armor stripping and Base64 decoding.
     *
     * Note: PubkeyUtils.recoverKeyPair() only supports RSA, DSA, and EC keys.
     * Ed25519 keys are not supported via PKCS#8 format and must use the PEM path instead.
     * This is acceptable since Ed25519 keys are typically distributed in OpenSSH/PEM format.
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
                return PubkeyUtils.recoverKeyPair(decoded)
            }
        } catch (e: Exception) {
            Log.e("PubkeyListViewModel", "Failed to parse PKCS#8 key", e)
        }

        return null
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
