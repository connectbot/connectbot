/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trilead.ssh2.SFTPv3DirectoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.sftp.SftpConnectionManager
import org.connectbot.sftp.SftpOperations
import org.connectbot.sftp.SftpPromptHandler
import org.connectbot.ui.navigation.NavArgs
import java.io.IOException
import javax.inject.Inject

/**
 * Represents a file or directory entry in the SFTP browser.
 */
data class SftpEntry(
    val filename: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long?,
    val modifiedTime: Long?,
    val permissions: String?,
)

/**
 * A local document selected for upload.
 */
data class UploadFile(
    val uri: Uri,
    val filename: String,
)

/**
 * UI state for the SFTP browser screen.
 */
data class SftpBrowserUiState(
    val host: Host? = null,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val currentPath: String = "/",
    val entries: List<SftpEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val transferProgress: TransferProgress? = null,
    val showCreateFolderDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val entryToDelete: SftpEntry? = null,
    val showHostKeyDialog: Boolean = false,
    val hostKeyInfo: HostKeyInfo? = null,
    val showPasswordDialog: Boolean = false,
    val passwordPrompt: String? = null,
    val showKeyPassphraseDialog: Boolean = false,
    val keyPassphrasePrompt: String? = null,
    val showBiometricDialog: Boolean = false,
    val biometricKeyInfo: BiometricKeyInfo? = null,
    val showGoToPathDialog: Boolean = false,
)

/**
 * Progress information for file transfers.
 */
data class TransferProgress(
    val filename: String,
    val isUpload: Boolean,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val fileIndex: Int = 1,
    val fileCount: Int = 1,
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f

    val percentComplete: Int
        get() = (progress * 100).toInt()
}

/**
 * Host key information for verification dialog.
 */
data class HostKeyInfo(
    val hostname: String,
    val keyType: String,
    val fingerprint: String,
    val isNewKey: Boolean,
)

/**
 * Biometric key information for authentication dialog.
 */
data class BiometricKeyInfo(
    val keyName: String,
    val keystoreAlias: String,
)

@HiltViewModel
class SftpBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hostRepository: HostRepository,
    private val sftpConnectionManager: SftpConnectionManager,
    private val dispatchers: CoroutineDispatchers,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val hostId: Long = savedStateHandle.get<Long>(NavArgs.HOST_ID) ?: -1L

    private val _uiState = MutableStateFlow(SftpBrowserUiState())
    val uiState: StateFlow<SftpBrowserUiState> = _uiState.asStateFlow()

    private var sftpOperations: SftpOperations? = null
    private var pendingHostKeyCallback: ((Boolean) -> Unit)? = null
    private var pendingPasswordCallback: ((String?) -> Unit)? = null
    private var pendingKeyPassphraseCallback: ((String?) -> Unit)? = null
    private var pendingBiometricCallback: ((Boolean) -> Unit)? = null
    private var transferJob: Job? = null

    init {
        loadHost()
    }

    private fun loadHost() {
        viewModelScope.launch {
            val host = hostRepository.findHostById(hostId)
            if (host == null) {
                _uiState.update { it.copy(error = "Host not found") }
                return@launch
            }

            _uiState.update { it.copy(host = host) }
            connect()
        }
    }

    fun connect() {
        val host = _uiState.value.host ?: return

        _uiState.update { it.copy(isConnecting = true, error = null) }

        viewModelScope.launch {
            val result = sftpConnectionManager.connect(host, createPromptHandler())

            result.fold(
                onSuccess = { operations ->
                    sftpOperations = operations
                    _uiState.update { it.copy(isConnecting = false, isConnected = true) }
                    // Navigate to home directory
                    navigateTo("~")
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            isConnected = false,
                            error = "Connection failed: ${error.message}",
                        )
                    }
                },
            )
        }
    }

    fun disconnect() {
        val host = _uiState.value.host ?: return

        viewModelScope.launch {
            sftpConnectionManager.disconnect(host.id)
            sftpOperations = null
            _uiState.update {
                it.copy(
                    isConnected = false,
                    entries = emptyList(),
                    currentPath = "/",
                )
            }
        }
    }

    fun navigateTo(path: String) {
        val operations = sftpOperations ?: return

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Resolve the path
                val resolvedPath = when {
                    path == "~" -> operations.canonicalPath(".")

                    path.startsWith("/") -> path

                    else -> {
                        val current = _uiState.value.currentPath
                        if (current.endsWith("/")) "$current$path" else "$current/$path"
                    }
                }

                val canonicalPath = operations.canonicalPath(resolvedPath)
                val entries = operations.listDirectory(canonicalPath)
                    .filter { it.filename != "." }
                    .map { entry -> toSftpEntry(entry, canonicalPath) }
                    .sortedWith(compareBy({ !it.isDirectory }, { it.filename.lowercase() }))

                _uiState.update {
                    it.copy(
                        currentPath = canonicalPath,
                        entries = entries,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to list directory: ${e.message}",
                    )
                }
            }
        }
    }

    fun navigateUp() {
        val currentPath = _uiState.value.currentPath
        if (currentPath == "/") return

        val parentPath = currentPath.substringBeforeLast("/").ifEmpty { "/" }
        navigateTo(parentPath)
    }

    fun refresh() {
        navigateTo(_uiState.value.currentPath)
    }

    fun showCreateFolderDialog() {
        _uiState.update { it.copy(showCreateFolderDialog = true) }
    }

    fun dismissCreateFolderDialog() {
        _uiState.update { it.copy(showCreateFolderDialog = false) }
    }

    fun showGoToPathDialog() {
        _uiState.update { it.copy(showGoToPathDialog = true) }
    }

    fun dismissGoToPathDialog() {
        _uiState.update { it.copy(showGoToPathDialog = false) }
    }

    fun goToPath(path: String) {
        _uiState.update { it.copy(showGoToPathDialog = false) }
        if (path.isNotBlank()) {
            navigateTo(path.trim())
        }
    }

    fun createFolder(name: String) {
        val operations = sftpOperations ?: return

        _uiState.update { it.copy(showCreateFolderDialog = false, isLoading = true) }

        viewModelScope.launch {
            try {
                val currentPath = _uiState.value.currentPath
                val newPath = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
                operations.mkdir(newPath)
                refresh()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to create folder: ${e.message}",
                    )
                }
            }
        }
    }

    fun showDeleteDialog(entry: SftpEntry) {
        _uiState.update { it.copy(showDeleteDialog = true, entryToDelete = entry) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, entryToDelete = null) }
    }

    fun deleteEntry() {
        val operations = sftpOperations ?: return
        val entry = _uiState.value.entryToDelete ?: return

        _uiState.update { it.copy(showDeleteDialog = false, entryToDelete = null, isLoading = true) }

        viewModelScope.launch {
            try {
                if (entry.isDirectory) {
                    operations.rmdir(entry.fullPath)
                } else {
                    operations.rm(entry.fullPath)
                }
                refresh()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to delete: ${e.message}",
                    )
                }
            }
        }
    }

    fun downloadFile(entry: SftpEntry, destinationUri: Uri) {
        val operations = sftpOperations ?: return

        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            try {
                withContext(dispatchers.io) {
                    downloadFile(operations, entry, destinationUri, 1, 1)
                }
            } catch (_: CancellationException) {
                // The user cancelled the transfer.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Download failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(transferProgress = null) }
            }
        }
    }

    fun downloadFiles(entries: List<SftpEntry>, destinationDirectoryUri: Uri) {
        val operations = sftpOperations ?: return
        if (entries.isEmpty()) return

        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            try {
                withContext(dispatchers.io) {
                    entries.forEachIndexed { index, entry ->
                        currentCoroutineContext().ensureActive()
                        val destinationUri = createDocument(destinationDirectoryUri, entry.filename)
                        downloadFile(operations, entry, destinationUri, index + 1, entries.size)
                    }
                }
            } catch (_: CancellationException) {
                // The user cancelled the transfer.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Download failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(transferProgress = null) }
            }
        }
    }

    fun uploadFile(sourceUri: Uri, filename: String) {
        uploadFiles(listOf(UploadFile(sourceUri, filename)))
    }

    fun uploadFiles(files: List<UploadFile>) {
        val operations = sftpOperations ?: return
        if (files.isEmpty()) return

        transferJob?.cancel()
        transferJob = viewModelScope.launch {
            try {
                withContext(dispatchers.io) {
                    val currentPath = _uiState.value.currentPath
                    files.forEachIndexed { index, file ->
                        currentCoroutineContext().ensureActive()
                        uploadFile(operations, file, currentPath, index + 1, files.size)
                    }
                }
                refresh()
            } catch (_: CancellationException) {
                // The user cancelled the transfer.
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Upload failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(transferProgress = null) }
            }
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        _uiState.update { it.copy(transferProgress = null) }
    }

    private suspend fun downloadFile(
        operations: SftpOperations,
        entry: SftpEntry,
        destinationUri: Uri,
        fileIndex: Int,
        fileCount: Int,
    ) {
        val outputStream = context.contentResolver.openOutputStream(destinationUri)
            ?: throw IOException("Cannot open output stream")

        updateTransferProgress(entry.filename, false, 0, entry.size ?: 0, fileIndex, fileCount)
        outputStream.use { stream ->
            operations.downloadFile(
                remotePath = entry.fullPath,
                outputStream = stream,
            ) { bytesTransferred, totalBytes ->
                updateTransferProgress(
                    entry.filename,
                    false,
                    bytesTransferred,
                    totalBytes,
                    fileIndex,
                    fileCount,
                )
            }
        }
    }

    private suspend fun uploadFile(
        operations: SftpOperations,
        file: UploadFile,
        currentPath: String,
        fileIndex: Int,
        fileCount: Int,
    ) {
        val inputStream = context.contentResolver.openInputStream(file.uri)
            ?: throw IOException("Cannot open input stream")
        val fileSize = context.contentResolver.openAssetFileDescriptor(file.uri, "r")?.use {
            it.length
        } ?: 0L
        val remotePath = if (currentPath.endsWith("/")) {
            "$currentPath${file.filename}"
        } else {
            "$currentPath/${file.filename}"
        }

        updateTransferProgress(file.filename, true, 0, fileSize, fileIndex, fileCount)
        inputStream.use { stream ->
            operations.uploadFile(
                inputStream = stream,
                remotePath = remotePath,
                totalSize = fileSize,
            ) { bytesTransferred, totalBytes ->
                updateTransferProgress(
                    file.filename,
                    true,
                    bytesTransferred,
                    totalBytes,
                    fileIndex,
                    fileCount,
                )
            }
        }
    }

    private fun createDocument(directoryUri: Uri, filename: String): Uri {
        val directoryDocumentId = DocumentsContract.getTreeDocumentId(directoryUri)
        val directoryDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            directoryUri,
            directoryDocumentId,
        )
        return DocumentsContract.createDocument(
            context.contentResolver,
            directoryDocumentUri,
            "application/octet-stream",
            filename,
        ) ?: throw IOException("Cannot create $filename")
    }

    private fun updateTransferProgress(
        filename: String,
        isUpload: Boolean,
        bytesTransferred: Long,
        totalBytes: Long,
        fileIndex: Int,
        fileCount: Int,
    ) {
        _uiState.update {
            it.copy(
                transferProgress = TransferProgress(
                    filename = filename,
                    isUpload = isUpload,
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalBytes,
                    fileIndex = fileIndex,
                    fileCount = fileCount,
                ),
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // Host key dialog handling
    fun acceptHostKey() {
        pendingHostKeyCallback?.invoke(true)
        pendingHostKeyCallback = null
        _uiState.update { it.copy(showHostKeyDialog = false, hostKeyInfo = null) }
    }

    fun rejectHostKey() {
        pendingHostKeyCallback?.invoke(false)
        pendingHostKeyCallback = null
        _uiState.update { it.copy(showHostKeyDialog = false, hostKeyInfo = null) }
    }

    // Password dialog handling
    fun submitPassword(password: String) {
        pendingPasswordCallback?.invoke(password)
        pendingPasswordCallback = null
        _uiState.update { it.copy(showPasswordDialog = false, passwordPrompt = null) }
    }

    fun cancelPassword() {
        pendingPasswordCallback?.invoke(null)
        pendingPasswordCallback = null
        _uiState.update { it.copy(showPasswordDialog = false, passwordPrompt = null) }
    }

    // Key passphrase dialog handling
    fun submitKeyPassphrase(passphrase: String) {
        pendingKeyPassphraseCallback?.invoke(passphrase)
        pendingKeyPassphraseCallback = null
        _uiState.update { it.copy(showKeyPassphraseDialog = false, keyPassphrasePrompt = null) }
    }

    fun cancelKeyPassphrase() {
        pendingKeyPassphraseCallback?.invoke(null)
        pendingKeyPassphraseCallback = null
        _uiState.update { it.copy(showKeyPassphraseDialog = false, keyPassphrasePrompt = null) }
    }

    // Biometric dialog handling
    fun onBiometricSuccess() {
        pendingBiometricCallback?.invoke(true)
        pendingBiometricCallback = null
        _uiState.update { it.copy(showBiometricDialog = false, biometricKeyInfo = null) }
    }

    fun onBiometricFailure() {
        pendingBiometricCallback?.invoke(false)
        pendingBiometricCallback = null
        _uiState.update { it.copy(showBiometricDialog = false, biometricKeyInfo = null) }
    }

    private fun createPromptHandler(): SftpPromptHandler {
        return object : SftpPromptHandler {
            override suspend fun requestPassword(message: String): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                pendingPasswordCallback = { password ->
                    cont.resumeWith(Result.success(password))
                }
                _uiState.update {
                    it.copy(showPasswordDialog = true, passwordPrompt = message)
                }
            }

            override suspend fun requestKeyPassphrase(keyName: String): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                pendingKeyPassphraseCallback = { passphrase ->
                    cont.resumeWith(Result.success(passphrase))
                }
                _uiState.update {
                    it.copy(
                        showKeyPassphraseDialog = true,
                        keyPassphrasePrompt = "Enter passphrase for key '$keyName'",
                    )
                }
            }

            override suspend fun confirmHostKey(
                hostname: String,
                keyType: String,
                fingerprint: String,
                isNewKey: Boolean,
            ): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                pendingHostKeyCallback = { accepted ->
                    cont.resumeWith(Result.success(accepted))
                }
                _uiState.update {
                    it.copy(
                        showHostKeyDialog = true,
                        hostKeyInfo = HostKeyInfo(hostname, keyType, fingerprint, isNewKey),
                    )
                }
            }

            override suspend fun requestBiometricAuth(keyName: String, keystoreAlias: String): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                pendingBiometricCallback = { success ->
                    cont.resumeWith(Result.success(success))
                }
                _uiState.update {
                    it.copy(
                        showBiometricDialog = true,
                        biometricKeyInfo = BiometricKeyInfo(keyName, keystoreAlias),
                    )
                }
            }

            override suspend fun handleKeyboardInteractive(
                name: String,
                instruction: String,
                prompts: Array<String>,
                echoResponses: BooleanArray,
            ): Array<String>? {
                // For simplicity, treat single prompt as password
                if (prompts.size == 1) {
                    val response = requestPassword(prompts[0])
                    return if (response != null) arrayOf(response) else null
                }
                return null
            }
        }
    }

    private fun toSftpEntry(entry: SFTPv3DirectoryEntry, parentPath: String): SftpEntry {
        val fullPath = if (parentPath.endsWith("/")) {
            "$parentPath${entry.filename}"
        } else {
            "$parentPath/${entry.filename}"
        }

        return SftpEntry(
            filename = entry.filename,
            fullPath = fullPath,
            isDirectory = entry.attributes?.isDirectory == true,
            isSymlink = entry.attributes?.isSymlink == true,
            size = entry.attributes?.size,
            modifiedTime = entry.attributes?.mtime,
            permissions = entry.attributes?.octalPermissions,
        )
    }

    override fun onCleared() {
        super.onCleared()
        val host = _uiState.value.host
        if (host != null) {
            viewModelScope.launch {
                sftpConnectionManager.disconnect(host.id)
            }
        }
    }
}
