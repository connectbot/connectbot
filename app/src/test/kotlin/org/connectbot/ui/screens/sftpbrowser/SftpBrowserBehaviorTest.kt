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

package org.connectbot.ui.screens.sftpbrowser

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.trilead.ssh2.SFTPv3DirectoryEntry
import com.trilead.ssh2.SFTPv3FileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.sftp.SftpConnectionManager
import org.connectbot.sftp.SftpOperations
import org.connectbot.sftp.SftpPromptHandler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SftpBrowserBehaviorTest {
    private val dispatcher = StandardTestDispatcher()
    private val host = Host(id = UUID(0, 7), nickname = "test", hostname = "host")
    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var repository: HostRepository
    private lateinit var manager: SftpConnectionManager
    private lateinit var operations: SftpOperations
    private lateinit var prompts: SftpPromptHandler
    private var listingFailure: IOException? = null
    private var mutationFailure: IOException? = null
    private var listing = emptyList<SFTPv3DirectoryEntry>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = mock(Context::class.java)
        resolver = mock(ContentResolver::class.java)
        repository = mock(HostRepository::class.java)
        manager = mock(SftpConnectionManager::class.java)
        operations = mock(SftpOperations::class.java)
        `when`(context.contentResolver).thenReturn(resolver)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun browser(): SftpBrowserViewModel {
        `when`(repository.findHostById(host.id)).thenReturn(host)
        val fallback = mock(SftpPromptHandler::class.java)
        doAnswer {
            prompts = it.getArgument(1)
            // A suspend function returning Result exposes its unboxed success value to Java.
            operations
        }.`when`(manager).connect(eq(host) ?: host, any(SftpPromptHandler::class.java) ?: fallback)
        `when`(operations.canonicalPath(anyString())).thenAnswer {
            val path = it.getArgument<String>(0)
            if (path == ".") "/home/test" else path
        }
        `when`(operations.listDirectory(anyString())).thenAnswer {
            listingFailure?.let { throw it }
            listing
        }
        doAnswer {
            mutationFailure?.let { throw it }
            Unit
        }.`when`(operations).mkdir(anyString(), eq(493))
        doAnswer {
            mutationFailure?.let { throw it }
            Unit
        }.`when`(operations).rm(anyString())
        doAnswer {
            mutationFailure?.let { throw it }
            Unit
        }.`when`(operations).rmdir(anyString())
        return SftpBrowserViewModel(context, repository, manager, CoroutineDispatchers(dispatcher, dispatcher, dispatcher), SavedStateHandle(mapOf("hostId" to host.id.toString())))
    }

    private fun entry(name: String, directory: Boolean = false) = SftpEntry(name, "/home/test/$name", directory, false, 3, 1, "rw-------")

    @Test
    fun loadsHostSortsDirectoryEntriesAndNavigatesRelativeAndAbsolutePaths() = runTest(dispatcher) {
        fun remote(name: String, mode: Int) = SFTPv3DirectoryEntry().apply {
            filename = name
            attributes = SFTPv3FileAttributes().apply {
                permissions = mode
                size = 42L
                mtime = 1
            }
        }
        listing = listOf(remote("z.txt", 33188), remote("folder", 16877), remote(".", 16877), remote("link", 41471))
        val vm = browser()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isConnected)
        assertEquals(listOf("folder", "link", "z.txt"), vm.uiState.value.entries.map { it.filename })
        assertTrue(vm.uiState.value.entries[1].isSymlink)
        vm.navigateTo("folder")
        advanceUntilIdle()
        assertEquals("/home/test/folder", vm.uiState.value.currentPath)
        vm.navigateUp()
        advanceUntilIdle()
        assertEquals("/home/test", vm.uiState.value.currentPath)
        vm.goToPath(" / ")
        advanceUntilIdle()
        vm.navigateUp()
        vm.navigateTo("child")
        advanceUntilIdle()
        assertEquals("/child", vm.uiState.value.currentPath)
        vm.goToPath(" ")
        vm.refresh()
        advanceUntilIdle()
        assertEquals("/child", vm.uiState.value.currentPath)
        listingFailure = IOException("offline")
        vm.navigateTo("/missing")
        advanceUntilIdle()
        assertEquals("Failed to list directory: offline", vm.uiState.value.error)
        vm.clearError()
        assertNull(vm.uiState.value.error)
        vm.disconnect()
        advanceUntilIdle()
        verify(manager).disconnect(host.id)
        assertFalse(vm.uiState.value.isConnected)
        assertTrue(vm.uiState.value.entries.isEmpty())
        assertEquals("/", vm.uiState.value.currentPath)
    }

    @Test
    fun createsAndDeletesFilesAndDirectoriesAndReportsMutationFailures() = runTest(dispatcher) {
        val vm = browser()
        advanceUntilIdle()
        vm.showCreateFolderDialog()
        assertTrue(vm.uiState.value.showCreateFolderDialog)
        vm.dismissCreateFolderDialog()
        vm.showGoToPathDialog()
        assertTrue(vm.uiState.value.showGoToPathDialog)
        vm.dismissGoToPathDialog()
        vm.createFolder("new")
        advanceUntilIdle()
        verify(operations).mkdir("/home/test/new", 493)
        vm.navigateTo("/")
        advanceUntilIdle()
        vm.createFolder("root")
        advanceUntilIdle()
        verify(operations).mkdir("/root", 493)
        vm.showDeleteDialog(entry("file"))
        vm.deleteEntry()
        advanceUntilIdle()
        verify(operations).rm("/home/test/file")
        vm.showDeleteDialog(entry("folder", true))
        vm.deleteEntry()
        advanceUntilIdle()
        verify(operations).rmdir("/home/test/folder")
        vm.showDeleteDialog(entry("cancel"))
        vm.dismissDeleteDialog()
        vm.deleteEntry()
        assertFalse(vm.uiState.value.showDeleteDialog)
        mutationFailure = IOException("denied")
        vm.createFolder("bad")
        advanceUntilIdle()
        assertEquals("Failed to create folder: denied", vm.uiState.value.error)
        vm.showDeleteDialog(entry("bad"))
        vm.deleteEntry()
        advanceUntilIdle()
        assertEquals("Failed to delete: denied", vm.uiState.value.error)
    }

    @Test
    fun hostKeyPasswordPassphraseAndBiometricPromptsResumeWithUserDecisions() = runTest(dispatcher) {
        val vm = browser()
        advanceUntilIdle()
        for (accept in listOf(true, false)) {
            val hostKey = async { prompts.confirmHostKey("server", "RSA", "fingerprint", accept) }
            runCurrent()
            assertTrue(vm.uiState.value.showHostKeyDialog)
            if (accept) vm.acceptHostKey() else vm.rejectHostKey()
            assertEquals(accept, hostKey.await())
            assertNull(vm.uiState.value.hostKeyInfo)
            val password = async { prompts.requestPassword("Password") }
            runCurrent()
            assertEquals("Password", vm.uiState.value.passwordPrompt)
            if (accept) vm.submitPassword("value") else vm.cancelPassword()
            assertEquals(if (accept) "value" else null, password.await())
            val passphrase = async { prompts.requestKeyPassphrase("key") }
            runCurrent()
            assertTrue(vm.uiState.value.showKeyPassphraseDialog)
            if (accept) vm.submitKeyPassphrase("phrase") else vm.cancelKeyPassphrase()
            assertEquals(if (accept) "phrase" else null, passphrase.await())
            val biometric = async { prompts.requestBiometricAuth("key", "alias") }
            runCurrent()
            assertEquals("alias", vm.uiState.value.biometricKeyInfo?.keystoreAlias)
            if (accept) vm.onBiometricSuccess() else vm.onBiometricFailure()
            assertEquals(accept, biometric.await())
            val interactive = async { prompts.handleKeyboardInteractive("name", "instruction", arrayOf("Password"), booleanArrayOf(false)) }
            runCurrent()
            if (accept) vm.submitPassword("interactive") else vm.cancelPassword()
            assertEquals(if (accept) listOf("interactive") else null, interactive.await()?.toList())
        }
        assertNull(prompts.handleKeyboardInteractive("", "", arrayOf("one", "two"), booleanArrayOf(false, false)))
        vm.acceptHostKey()
        vm.rejectHostKey()
        vm.submitPassword("")
        vm.cancelPassword()
        vm.submitKeyPassphrase("")
        vm.cancelKeyPassphrase()
        vm.onBiometricSuccess()
        vm.onBiometricFailure()
    }

    @Test
    fun uploadsDownloadsAndReportsMissingDocumentStreams() = runTest(dispatcher) {
        val vm = browser()
        advanceUntilIdle()
        val source = Uri.parse("content://test/source")
        val target = Uri.parse("content://test/target")
        `when`(resolver.openInputStream(source)).thenReturn(ByteArrayInputStream(byteArrayOf(1)))
        `when`(resolver.openOutputStream(target)).thenReturn(ByteArrayOutputStream())
        vm.uploadFile(source, "uploaded")
        advanceUntilIdle()
        vm.downloadFile(entry("downloaded"), target)
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.transferProgress)
        vm.uploadFiles(emptyList())
        vm.downloadFiles(emptyList(), target)
        `when`(resolver.openInputStream(source)).thenReturn(null)
        vm.uploadFile(source, "missing")
        advanceUntilIdle()
        assertEquals("Upload failed: Cannot open input stream", vm.uiState.value.error)
        `when`(resolver.openOutputStream(target)).thenReturn(null)
        vm.downloadFile(entry("missing"), target)
        advanceUntilIdle()
        assertEquals("Download failed: Cannot open output stream", vm.uiState.value.error)
        vm.cancelTransfer()
        assertNull(vm.uiState.value.transferProgress)
    }

    @Test
    fun missingHostAndDisconnectedActionsDoNotStartOperations() = runTest(dispatcher) {
        val vm = SftpBrowserViewModel(context, repository, manager, CoroutineDispatchers(dispatcher, dispatcher, dispatcher), SavedStateHandle())
        advanceUntilIdle()
        assertEquals("Host not found", vm.uiState.value.error)
        vm.connect()
        vm.disconnect()
        vm.navigateTo("/")
        vm.createFolder("new")
        vm.deleteEntry()
        val uri = Uri.parse("content://test/file")
        vm.uploadFile(uri, "file")
        vm.downloadFile(entry("file"), uri)
        vm.downloadFiles(listOf(entry("file")), uri)
        assertFalse(vm.uiState.value.isConnected)
    }
}
