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
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.transport.AbsTransport
import org.connectbot.transport.sftp.SftpChannel
import org.connectbot.transport.sftp.SftpFile
import org.connectbot.transport.sftp.SftpPaths
import org.connectbot.transport.sftp.SftpTransferCancelledException
import org.connectbot.transport.sftp.SftpTransferProgressListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SftpViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )

    private lateinit var context: Context
    private lateinit var fakeChannel: FakeSftpChannel
    private lateinit var fakeTransport: FakeTransport
    private lateinit var bridge: TerminalBridge
    private lateinit var terminalManager: TerminalManager
    private lateinit var bridgesFlow: MutableStateFlow<List<TerminalBridge>>

    private val testHostId = 1L
    private val testHost = Host(
        id = testHostId,
        nickname = "test-host",
        protocol = "ssh",
        username = "user",
        hostname = "example.com",
        port = 22,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                // No-op for tests
            }
        })

        context = ApplicationProvider.getApplicationContext()
        fakeChannel = FakeSftpChannel()
        fakeTransport = FakeTransport(fakeChannel)

        bridge = Mockito.mock(TerminalBridge::class.java)
        Mockito.`when`(bridge.host).thenReturn(testHost)
        Mockito.`when`(bridge.transport).thenReturn(fakeTransport)

        bridgesFlow = MutableStateFlow(listOf(bridge))
        terminalManager = Mockito.mock(TerminalManager::class.java)
        Mockito.`when`(terminalManager.bridgesFlow).thenReturn(bridgesFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        Timber.uprootAll()
    }

    private fun createViewModel(): SftpViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("hostId" to testHostId))
        val viewModel = SftpViewModel(savedStateHandle, context, dispatchers)
        viewModel.setTerminalManager(terminalManager)
        return viewModel
    }

    private fun SftpViewModel.entryNamed(name: String): SftpFile {
        val entry = uiState.value.entries.find { it.name == name }
        assertNotNull("Expected entry '$name' in ${uiState.value.entries.map { it.name }}", entry)
        return entry!!
    }

    @Test
    fun initialLoad_listsHomeDirectory() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should stop loading", state.isLoading)
        assertTrue("Should be connected", state.isConnected)
        assertEquals("test-host", state.hostNickname)
        assertEquals("/home/test", state.currentPath)
        assertEquals("/home", state.parentPath)
        // Directories sort before files
        assertEquals(listOf("docs", "readme.txt"), state.entries.map { it.name })
    }

    @Test
    fun initialLoad_withoutBridge_showsNotConnected() = runTest {
        bridgesFlow.value = emptyList()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isConnected)
    }

    @Test
    fun openEntry_directory_navigatesInto() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEntry(viewModel.entryNamed("docs"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/home/test/docs", state.currentPath)
        assertEquals("/home/test", state.parentPath)
        assertEquals(listOf("guide.md"), state.entries.map { it.name })
    }

    @Test
    fun navigateUp_loadsParentDirectory() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEntry(viewModel.entryNamed("docs"))
        advanceUntilIdle()
        viewModel.navigateUp()
        advanceUntilIdle()

        assertEquals("/home/test", viewModel.uiState.value.currentPath)
    }

    @Test
    fun openEntry_file_requestsDownloadDestination() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEntry(viewModel.entryNamed("readme.txt"))
        advanceUntilIdle()

        assertEquals("readme.txt", viewModel.uiState.value.pendingDownload?.name)
    }

    @Test
    fun onDownloadDestinationChosen_null_abortsWithoutTransfer() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEntry(viewModel.entryNamed("readme.txt"))
        advanceUntilIdle()
        viewModel.onDownloadDestinationChosen(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.pendingDownload)
        assertNull(state.transfer)
    }

    @Test
    fun onDownloadDestinationChosen_writesRemoteContentToLocalFile() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEntry(viewModel.entryNamed("readme.txt"))
        advanceUntilIdle()

        val destination = File.createTempFile("download", ".txt")
        try {
            viewModel.onDownloadDestinationChosen(Uri.fromFile(destination))
            advanceUntilIdle()

            assertEquals("hello remote world", destination.readText())
            val state = viewModel.uiState.value
            assertNull(state.transfer)
            assertEquals(
                context.getString(R.string.sftp_download_complete, "readme.txt"),
                state.message,
            )
        } finally {
            destination.delete()
        }
    }

    @Test
    fun requestUpload_newFile_uploadsAndRefreshesListing() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val source = File.createTempFile("localnotes", ".txt").apply { writeText("local content") }
        try {
            viewModel.requestUpload(Uri.fromFile(source))
            advanceUntilIdle()

            val uploadedName = source.name
            val uploaded = fakeChannel.files["/home/test/$uploadedName"]
            assertNotNull("File should be uploaded", uploaded)
            assertEquals("local content", uploaded!!.decodeToString())

            val state = viewModel.uiState.value
            assertNull(state.transfer)
            assertEquals(context.getString(R.string.sftp_upload_complete, uploadedName), state.message)
            assertTrue(state.entries.any { it.name == uploadedName })
        } finally {
            source.delete()
        }
    }

    @Test
    fun requestUpload_existingFile_asksBeforeOverwriting() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val source = File.createTempFile("existing", ".txt").apply { writeText("new content") }
        try {
            fakeChannel.files["/home/test/${source.name}"] = "old content".toByteArray()

            viewModel.requestUpload(Uri.fromFile(source))
            advanceUntilIdle()

            val dialog = viewModel.uiState.value.dialog
            assertTrue("Expected overwrite dialog, got $dialog", dialog is SftpDialog.ConfirmUploadOverwrite)
            assertEquals(
                "old content",
                fakeChannel.files["/home/test/${source.name}"]!!.decodeToString(),
            )

            viewModel.confirmUploadOverwrite()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.dialog)
            assertEquals(
                "new content",
                fakeChannel.files["/home/test/${source.name}"]!!.decodeToString(),
            )
        } finally {
            source.delete()
        }
    }

    @Test
    fun confirmDelete_file_removesItAndRefreshes() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestDelete(viewModel.entryNamed("readme.txt"))
        assertTrue(viewModel.uiState.value.dialog is SftpDialog.ConfirmDelete)

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertFalse(fakeChannel.files.containsKey("/home/test/readme.txt"))
        assertFalse(viewModel.uiState.value.entries.any { it.name == "readme.txt" })
    }

    @Test
    fun confirmDelete_emptyDirectory_removesIt() = runTest {
        fakeChannel.directories.add("/home/test/empty")

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestDelete(viewModel.entryNamed("empty"))
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertFalse(fakeChannel.directories.contains("/home/test/empty"))
    }

    @Test
    fun confirmDelete_nonEmptyDirectory_reportsError() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestDelete(viewModel.entryNamed("docs"))
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertTrue(fakeChannel.directories.contains("/home/test/docs"))
        assertNotNull(viewModel.uiState.value.message)
    }

    @Test
    fun confirmRename_movesEntry() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestRename(viewModel.entryNamed("readme.txt"))
        viewModel.confirmRename("manual.txt")
        advanceUntilIdle()

        assertFalse(fakeChannel.files.containsKey("/home/test/readme.txt"))
        assertEquals(
            "hello remote world",
            fakeChannel.files["/home/test/manual.txt"]!!.decodeToString(),
        )
        assertTrue(viewModel.uiState.value.entries.any { it.name == "manual.txt" })
    }

    @Test
    fun confirmCreateFolder_makesDirectoryAndRefreshes() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestCreateFolder()
        viewModel.confirmCreateFolder("uploads")
        advanceUntilIdle()

        assertTrue(fakeChannel.directories.contains("/home/test/uploads"))
        assertTrue(viewModel.uiState.value.entries.any { it.name == "uploads" && it.isDirectory })
    }

    @Test
    fun confirmCreateFolder_invalidName_reportsError() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestCreateFolder()
        viewModel.confirmCreateFolder("bad/name")
        advanceUntilIdle()

        assertEquals(context.getString(R.string.sftp_error_invalid_name), viewModel.uiState.value.message)
        assertFalse(fakeChannel.directories.any { it.contains("bad") })
    }

    @Test
    fun openEntry_symlinkToDirectory_navigatesIntoTarget() = runTest {
        fakeChannel.symlinks["/home/test/dolink"] = "/home/test/docs"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEntry(viewModel.entryNamed("dolink"))
        advanceUntilIdle()

        assertEquals("/home/test/docs", viewModel.uiState.value.currentPath)
    }

    @Test
    fun openEntry_symlinkToFile_requestsDownload() = runTest {
        fakeChannel.symlinks["/home/test/filelink"] = "/home/test/readme.txt"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEntry(viewModel.entryNamed("filelink"))
        advanceUntilIdle()

        assertEquals("filelink", viewModel.uiState.value.pendingDownload?.name)
    }

    @Test
    fun openEntry_brokenSymlink_reportsError() = runTest {
        fakeChannel.symlinks["/home/test/broken"] = "/home/test/missing"

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEntry(viewModel.entryNamed("broken"))
        advanceUntilIdle()

        assertEquals(
            context.getString(R.string.sftp_error_broken_link, "broken"),
            viewModel.uiState.value.message,
        )
        assertNull(viewModel.uiState.value.pendingDownload)
    }

    @Test
    fun bridgeRemoved_marksDisconnected() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isConnected)

        bridgesFlow.value = emptyList()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isConnected)
    }
}

/**
 * In-memory [SftpChannel] with a small default tree:
 * /home/test/docs/guide.md and /home/test/readme.txt.
 */
private class FakeSftpChannel : SftpChannel {
    val directories = mutableSetOf("/", "/home", "/home/test", "/home/test/docs")
    val files = mutableMapOf(
        "/home/test/readme.txt" to "hello remote world".toByteArray(),
        "/home/test/docs/guide.md" to "# guide".toByteArray(),
    )
    val symlinks = mutableMapOf<String, String>()
    var closed = false
        private set

    private fun resolveLinks(path: String): String {
        var current = path
        var hops = 0
        while (symlinks.containsKey(current) && hops++ < 8) {
            current = symlinks.getValue(current)
        }
        return current
    }

    override fun canonicalPath(path: String): String = if (path == ".") "/home/test" else resolveLinks(path)

    override fun list(path: String): List<SftpFile> {
        val resolved = resolveLinks(path)
        if (!directories.contains(resolved)) throw IOException("No such directory: $path")

        val children = mutableListOf<SftpFile>()
        directories.filter { SftpPaths.parent(it) == resolved }.forEach {
            children.add(entry(it, isDirectory = true, isSymlink = false, size = null))
        }
        files.filterKeys { SftpPaths.parent(it) == resolved }.forEach { (path, data) ->
            children.add(entry(path, isDirectory = false, isSymlink = false, size = data.size.toLong()))
        }
        symlinks.filterKeys { SftpPaths.parent(it) == resolved }.forEach { (path, _) ->
            children.add(entry(path, isDirectory = false, isSymlink = true, size = null))
        }
        return children.sortedWith(SftpFile.BROWSER_ORDER)
    }

    private fun entry(path: String, isDirectory: Boolean, isSymlink: Boolean, size: Long?): SftpFile = SftpFile(
        name = SftpPaths.name(path),
        path = path,
        isDirectory = isDirectory,
        isSymlink = isSymlink,
        size = size,
        modifiedTimeMillis = null,
    )

    override fun stat(path: String): SftpFile? {
        val resolved = resolveLinks(path)
        return when {
            directories.contains(resolved) -> entry(resolved, isDirectory = true, isSymlink = false, size = null)
            files.containsKey(resolved) -> entry(resolved, isDirectory = false, isSymlink = false, size = files.getValue(resolved).size.toLong())
            else -> null
        }
    }

    override fun download(remotePath: String, sink: OutputStream, progressListener: SftpTransferProgressListener) {
        val data = files[resolveLinks(remotePath)] ?: throw IOException("No such file: $remotePath")
        var offset = 0
        while (offset < data.size) {
            val chunk = minOf(4, data.size - offset)
            sink.write(data, offset, chunk)
            offset += chunk
            if (!progressListener.onBytesTransferred(offset.toLong())) {
                throw SftpTransferCancelledException()
            }
        }
        sink.flush()
    }

    override fun upload(source: InputStream, remotePath: String, progressListener: SftpTransferProgressListener) {
        val data = source.readBytes()
        files[remotePath] = data
        if (!progressListener.onBytesTransferred(data.size.toLong())) {
            throw SftpTransferCancelledException()
        }
    }

    override fun mkdir(path: String) {
        if (directories.contains(path)) throw IOException("Already exists: $path")
        directories.add(path)
    }

    override fun delete(path: String) {
        files.remove(path) ?: throw IOException("No such file: $path")
    }

    override fun rmdir(path: String) {
        if (!directories.contains(path)) throw IOException("No such directory: $path")
        val hasChildren = directories.any { SftpPaths.parent(it) == path } ||
            files.keys.any { SftpPaths.parent(it) == path } ||
            symlinks.keys.any { SftpPaths.parent(it) == path }
        if (hasChildren) throw IOException("Directory not empty: $path")
        directories.remove(path)
    }

    override fun rename(fromPath: String, toPath: String) {
        when {
            files.containsKey(fromPath) -> files[toPath] = files.remove(fromPath)!!

            directories.contains(fromPath) -> {
                directories.remove(fromPath)
                directories.add(toPath)
            }

            symlinks.containsKey(fromPath) -> symlinks[toPath] = symlinks.remove(fromPath)!!

            else -> throw IOException("No such path: $fromPath")
        }
    }

    override fun close() {
        closed = true
    }
}

/**
 * Minimal transport that reports itself connected and hands out the fake channel.
 */
private class FakeTransport(private val channel: SftpChannel) : AbsTransport() {
    var connected = true

    override fun connect() = Unit
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
    override fun write(buffer: ByteArray) = Unit
    override fun write(c: Int) = Unit
    override fun flush() = Unit
    override fun close() {
        connected = false
    }

    override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) = Unit
    override fun isConnected(): Boolean = connected
    override fun isSessionOpen(): Boolean = connected
    override fun getDefaultPort(): Int = 22
    override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String = "fake"
    override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) = Unit
    override fun createHost(uri: Uri): Host = Host(nickname = "fake", protocol = "ssh", username = "user", hostname = "fake", port = 22)
    override fun usesNetwork(): Boolean = false
    override fun getLocalIpAddress(): String? = null
    override fun canTransferFiles(): Boolean = true
    override fun openSftpChannel(): SftpChannel = channel
}
