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

package org.connectbot.ui.screens.sftp

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trilead.ssh2.SFTPException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.connectbot.R
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.transport.sftp.SftpChannel
import org.connectbot.transport.sftp.SftpFile
import org.connectbot.transport.sftp.SftpPaths
import org.connectbot.transport.sftp.SftpTransferCancelledException
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

data class SftpUiState(
    val isLoading: Boolean = true,
    val hostNickname: String = "",
    val isConnected: Boolean = true,
    val currentPath: String = "",
    val parentPath: String? = null,
    val entries: List<SftpFile> = emptyList(),
    val pendingDownload: SftpFile? = null,
    val transfer: SftpTransfer? = null,
    val dialog: SftpDialog? = null,
    val message: String? = null,
)

data class SftpTransfer(
    val fileName: String,
    val isUpload: Boolean,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long? = null,
)

sealed interface SftpDialog {
    data class ConfirmUploadOverwrite(val sourceUri: Uri, val fileName: String, val fileSize: Long?) : SftpDialog
    data class ConfirmDelete(val file: SftpFile) : SftpDialog
    data class Rename(val file: SftpFile) : SftpDialog
    data object CreateFolder : SftpDialog
}

/**
 * Drives the SFTP file browser for a currently connected host. The channel is
 * opened lazily over the host's live [TerminalBridge] transport and closed
 * when the screen goes away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SftpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {
    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L

    private val _terminalManager = MutableStateFlow<TerminalManager?>(null)

    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    /** Serializes all use of [channel], which is not thread-safe. */
    private val channelMutex = Mutex()
    private var channel: SftpChannel? = null
    private var transferJob: Job? = null

    init {
        // Track live bridges so a dropped connection is reflected in the UI.
        viewModelScope.launch {
            _terminalManager.filterNotNull().flatMapLatest { it.bridgesFlow }.collect {
                if (channel != null && findBridgeForHost()?.transport?.isConnected() != true) {
                    _uiState.update { state -> state.copy(isConnected = false) }
                }
            }
        }
    }

    fun setTerminalManager(manager: TerminalManager) {
        if (_terminalManager.value === manager) return
        _terminalManager.value = manager
        openChannel()
    }

    private fun findBridgeForHost(): TerminalBridge? = _terminalManager.value?.bridgesFlow?.value?.find { it.host.id == hostId }

    private fun openChannel() {
        viewModelScope.launch {
            val bridge = findBridgeForHost()
            val transport = bridge?.transport
            if (transport == null || !transport.isConnected() || !transport.canTransferFiles()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConnected = false,
                        hostNickname = bridge?.host?.nickname.orEmpty(),
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, hostNickname = bridge.host.nickname) }
            try {
                channelMutex.withLock {
                    withContext(dispatchers.io) {
                        val newChannel = transport.openSftpChannel()
                        channel = newChannel
                        val home = newChannel.canonicalPath(HOME_PATH)
                        val entries = newChannel.list(home)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isConnected = true,
                                currentPath = home,
                                parentPath = SftpPaths.parent(home),
                                entries = entries,
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Failed to open SFTP channel")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isConnected = false,
                        message = context.getString(R.string.sftp_error_channel, errorDetail(e)),
                    )
                }
            }
        }
    }

    fun refresh() {
        val path = _uiState.value.currentPath
        if (path.isNotEmpty()) {
            loadDirectory(path)
        }
    }

    fun navigateUp() {
        _uiState.value.parentPath?.let { loadDirectory(it) }
    }

    fun openEntry(file: SftpFile) {
        val state = _uiState.value
        if (state.isLoading || state.transfer != null || !state.isConnected) return

        when {
            file.isDirectory -> loadDirectory(file.path)
            !file.isSymlink -> _uiState.update { it.copy(pendingDownload = file) }
            else -> resolveSymlink(file)
        }
    }

    /**
     * Starts the download flow for a file; the screen reacts to
     * [SftpUiState.pendingDownload] by asking the user for a destination.
     */
    fun requestDownload(file: SftpFile) {
        if (file.isDirectory) return
        openEntry(file)
    }

    private fun resolveSymlink(file: SftpFile) {
        viewModelScope.launch {
            val currentChannel = channel ?: return@launch
            try {
                val resolved = channelMutex.withLock {
                    withContext(dispatchers.io) { currentChannel.stat(file.path) }
                }
                when {
                    resolved == null -> _uiState.update {
                        it.copy(message = context.getString(R.string.sftp_error_broken_link, file.name))
                    }

                    resolved.isDirectory -> loadDirectory(file.path)

                    else -> _uiState.update {
                        it.copy(pendingDownload = file.copy(size = resolved.size))
                    }
                }
            } catch (e: IOException) {
                reportError(R.string.sftp_error_listing, e)
            }
        }
    }

    private fun loadDirectory(path: String) {
        viewModelScope.launch {
            val currentChannel = channel ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                channelMutex.withLock {
                    withContext(dispatchers.io) {
                        val canonical = currentChannel.canonicalPath(path)
                        val entries = currentChannel.list(canonical)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentPath = canonical,
                                parentPath = SftpPaths.parent(canonical),
                                entries = entries,
                            )
                        }
                    }
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false) }
                reportError(R.string.sftp_error_listing, e)
            }
        }
    }

    /**
     * Called with the SAF destination picked for [SftpUiState.pendingDownload],
     * or null when the user backed out of the picker.
     */
    fun onDownloadDestinationChosen(destination: Uri?) {
        val file = _uiState.value.pendingDownload ?: return
        _uiState.update { it.copy(pendingDownload = null) }
        if (destination == null) return
        val currentChannel = channel ?: return

        transferJob = viewModelScope.launch {
            _uiState.update {
                it.copy(transfer = SftpTransfer(file.name, isUpload = false, totalBytes = file.size))
            }
            try {
                channelMutex.withLock {
                    withContext(dispatchers.io) {
                        val job = coroutineContext.job
                        val output = context.contentResolver.openOutputStream(destination)
                            ?: throw IOException(context.getString(R.string.sftp_error_open_local_file))
                        output.use { sink ->
                            currentChannel.download(file.path, sink) { bytes ->
                                updateTransferProgress(bytes)
                                job.isActive
                            }
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        transfer = null,
                        message = context.getString(R.string.sftp_download_complete, file.name),
                    )
                }
            } catch (e: CancellationException) {
                finishCancelledDownload(destination)
                throw e
            } catch (e: SftpTransferCancelledException) {
                finishCancelledDownload(destination)
            } catch (e: IOException) {
                deletePartialDownload(destination)
                _uiState.update {
                    it.copy(
                        transfer = null,
                        message = context.getString(R.string.sftp_error_download, errorDetail(e)),
                    )
                }
            } finally {
                transferJob = null
            }
        }
    }

    private suspend fun finishCancelledDownload(destination: Uri) {
        deletePartialDownload(destination)
        _uiState.update {
            it.copy(transfer = null, message = context.getString(R.string.sftp_transfer_cancelled))
        }
    }

    private suspend fun deletePartialDownload(destination: Uri) {
        withContext(NonCancellable + dispatchers.io) {
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, destination)
            }.onFailure { Timber.d(it, "Could not remove partial download") }
        }
    }

    /**
     * Starts the upload flow for a document picked with SAF, prompting before
     * overwriting an existing remote file.
     */
    fun requestUpload(source: Uri) {
        val currentChannel = channel ?: return
        viewModelScope.launch {
            try {
                val info = withContext(dispatchers.io) { queryDocumentInfo(source) }
                val targetPath = SftpPaths.join(_uiState.value.currentPath, info.name)
                val existing = channelMutex.withLock {
                    withContext(dispatchers.io) { currentChannel.stat(targetPath) }
                }
                if (existing != null) {
                    _uiState.update {
                        it.copy(dialog = SftpDialog.ConfirmUploadOverwrite(source, info.name, info.size))
                    }
                } else {
                    startUpload(source, info.name, info.size)
                }
            } catch (e: IOException) {
                reportError(R.string.sftp_error_upload, e)
            }
        }
    }

    fun confirmUploadOverwrite() {
        val dialog = _uiState.value.dialog as? SftpDialog.ConfirmUploadOverwrite ?: return
        _uiState.update { it.copy(dialog = null) }
        startUpload(dialog.sourceUri, dialog.fileName, dialog.fileSize)
    }

    private fun startUpload(source: Uri, name: String, size: Long?) {
        val currentChannel = channel ?: return
        val remotePath = SftpPaths.join(_uiState.value.currentPath, name)

        transferJob = viewModelScope.launch {
            _uiState.update {
                it.copy(transfer = SftpTransfer(name, isUpload = true, totalBytes = size))
            }
            try {
                channelMutex.withLock {
                    withContext(dispatchers.io) {
                        val job = coroutineContext.job
                        val input = context.contentResolver.openInputStream(source)
                            ?: throw IOException(context.getString(R.string.sftp_error_open_local_file))
                        input.use { stream ->
                            currentChannel.upload(stream, remotePath) { bytes ->
                                updateTransferProgress(bytes)
                                job.isActive
                            }
                        }
                    }
                }
                _uiState.update {
                    it.copy(
                        transfer = null,
                        message = context.getString(R.string.sftp_upload_complete, name),
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                finishCancelledUpload(remotePath)
                throw e
            } catch (e: SftpTransferCancelledException) {
                finishCancelledUpload(remotePath)
                refresh()
            } catch (e: IOException) {
                deletePartialUpload(remotePath)
                _uiState.update {
                    it.copy(
                        transfer = null,
                        message = context.getString(R.string.sftp_error_upload, errorDetail(e)),
                    )
                }
                refresh()
            } finally {
                transferJob = null
            }
        }
    }

    private suspend fun finishCancelledUpload(remotePath: String) {
        deletePartialUpload(remotePath)
        _uiState.update {
            it.copy(transfer = null, message = context.getString(R.string.sftp_transfer_cancelled))
        }
    }

    private suspend fun deletePartialUpload(remotePath: String) {
        val currentChannel = channel ?: return
        withContext(NonCancellable + dispatchers.io) {
            channelMutex.withLock {
                runCatching { currentChannel.delete(remotePath) }
                    .onFailure { Timber.d(it, "Could not remove partial upload") }
            }
        }
    }

    private fun updateTransferProgress(bytesTransferred: Long) {
        _uiState.update { state ->
            state.copy(transfer = state.transfer?.copy(bytesTransferred = bytesTransferred))
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
    }

    fun requestDelete(file: SftpFile) {
        _uiState.update { it.copy(dialog = SftpDialog.ConfirmDelete(file)) }
    }

    fun confirmDelete() {
        val dialog = _uiState.value.dialog as? SftpDialog.ConfirmDelete ?: return
        _uiState.update { it.copy(dialog = null) }
        val file = dialog.file
        runChannelOperation(R.string.sftp_error_delete) { currentChannel ->
            if (file.isDirectory) {
                currentChannel.rmdir(file.path)
            } else {
                currentChannel.delete(file.path)
            }
        }
    }

    fun requestRename(file: SftpFile) {
        _uiState.update { it.copy(dialog = SftpDialog.Rename(file)) }
    }

    fun confirmRename(newName: String) {
        val dialog = _uiState.value.dialog as? SftpDialog.Rename ?: return
        if (!isValidFileName(newName)) return
        _uiState.update { it.copy(dialog = null) }
        val target = SftpPaths.join(_uiState.value.currentPath, newName.trim())
        runChannelOperation(R.string.sftp_error_rename) { currentChannel ->
            currentChannel.rename(dialog.file.path, target)
        }
    }

    fun requestCreateFolder() {
        _uiState.update { it.copy(dialog = SftpDialog.CreateFolder) }
    }

    fun confirmCreateFolder(name: String) {
        if (_uiState.value.dialog !is SftpDialog.CreateFolder) return
        if (!isValidFileName(name)) return
        _uiState.update { it.copy(dialog = null) }
        val target = SftpPaths.join(_uiState.value.currentPath, name.trim())
        runChannelOperation(R.string.sftp_error_create_folder) { currentChannel ->
            currentChannel.mkdir(target)
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun isValidFileName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.contains('/') || trimmed == "." || trimmed == "..") {
            _uiState.update {
                it.copy(dialog = null, message = context.getString(R.string.sftp_error_invalid_name))
            }
            return false
        }
        return true
    }

    private fun runChannelOperation(errorResId: Int, operation: (SftpChannel) -> Unit) {
        viewModelScope.launch {
            val currentChannel = channel ?: return@launch
            try {
                channelMutex.withLock {
                    withContext(dispatchers.io) { operation(currentChannel) }
                }
                refresh()
            } catch (e: IOException) {
                reportError(errorResId, e)
            }
        }
    }

    private fun reportError(resId: Int, e: IOException) {
        Timber.e(e, "SFTP operation failed")
        _uiState.update { it.copy(message = context.getString(resId, errorDetail(e))) }
    }

    private fun errorDetail(e: IOException): String {
        val sftpMessage = (e as? SFTPException)?.serverErrorMessage
        return sftpMessage ?: e.message ?: e.javaClass.simpleName
    }

    private data class DocumentInfo(val name: String, val size: Long?)

    private fun queryDocumentInfo(source: Uri): DocumentInfo {
        var name: String? = null
        var size: Long? = null
        runCatching {
            context.contentResolver.query(source, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        name = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                    }
                }
            }
        }.onFailure { Timber.d(it, "Could not query document info") }

        val fallback = source.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val displayName = (name ?: fallback ?: DEFAULT_UPLOAD_NAME).replace('/', '_')
        return DocumentInfo(displayName, size)
    }

    override fun onCleared() {
        super.onCleared()
        val openedChannel = channel
        channel = null
        if (openedChannel != null) {
            // viewModelScope is already cancelled here, so close on a detached scope.
            CoroutineScope(SupervisorJob() + dispatchers.io).launch {
                runCatching { openedChannel.close() }
                    .onFailure { Timber.d(it, "Error closing SFTP channel") }
            }
        }
    }

    companion object {
        private const val HOME_PATH = "."
        private const val DEFAULT_UPLOAD_NAME = "upload"
    }
}
