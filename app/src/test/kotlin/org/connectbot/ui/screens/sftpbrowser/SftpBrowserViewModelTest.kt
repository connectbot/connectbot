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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.sftp.SftpConnectionManager
import org.connectbot.sftp.SftpOperations
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SftpBrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uploadFiles_uploadsEveryFileInSelectionOrder() = runTest(testDispatcher) {
        val context = mock(Context::class.java)
        val contentResolver = mock(ContentResolver::class.java)
        val hostRepository = mock(HostRepository::class.java)
        val operations = mock(SftpOperations::class.java)
        val connectionManager = mock(SftpConnectionManager::class.java)
        val firstUri = mock(Uri::class.java)
        val secondUri = mock(Uri::class.java)

        `when`(context.contentResolver).thenReturn(contentResolver)
        `when`(contentResolver.openInputStream(firstUri)).thenReturn(ByteArrayInputStream(byteArrayOf(1)))
        `when`(contentResolver.openInputStream(secondUri)).thenReturn(ByteArrayInputStream(byteArrayOf(2)))
        val viewModel = SftpBrowserViewModel(
            context = context,
            hostRepository = hostRepository,
            sftpConnectionManager = connectionManager,
            dispatchers = dispatchers,
            savedStateHandle = SavedStateHandle(mapOf("hostId" to 7L)),
        )
        advanceUntilIdle()
        setConnectedState(viewModel, operations, "/home/test")

        viewModel.uploadFiles(
            listOf(
                UploadFile(firstUri, "first.txt"),
                UploadFile(secondUri, "second.txt"),
            ),
        )
        advanceUntilIdle()

        val remotePaths = mockingDetails(operations).invocations
            .filter { it.method.name == "uploadFile" }
            .map { it.arguments[1] }
        assertEquals(listOf("/home/test/first.txt", "/home/test/second.txt"), remotePaths)
    }

    @Test
    fun transferProgress_reportsCurrentFileInBatch() {
        val progress = TransferProgress(
            filename = "second.txt",
            isUpload = true,
            bytesTransferred = 5,
            totalBytes = 10,
            fileIndex = 2,
            fileCount = 3,
        )

        assertEquals(2, progress.fileIndex)
        assertEquals(3, progress.fileCount)
        assertEquals(50, progress.percentComplete)
    }

    private fun setConnectedState(
        viewModel: SftpBrowserViewModel,
        operations: SftpOperations,
        currentPath: String,
    ) {
        SftpBrowserViewModel::class.java.getDeclaredField("sftpOperations").run {
            isAccessible = true
            set(viewModel, operations)
        }
        @Suppress("UNCHECKED_CAST")
        val state = SftpBrowserViewModel::class.java.getDeclaredField("_uiState").run {
            isAccessible = true
            get(viewModel) as MutableStateFlow<SftpBrowserUiState>
        }
        state.value = state.value.copy(
            isConnected = true,
            currentPath = currentPath,
            error = null,
        )
    }
}
