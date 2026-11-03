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

package org.connectbot.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.transport.AbsTransport
import org.connectbot.zmodemlib.Framing
import org.connectbot.zmodemlib.Header
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class ZmodemTransferTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val authority = "org.connectbot.test.documents"
    private val provider = TestDocuments()

    private class TestDocuments : DocumentsProvider() {
        lateinit var directory: File
        val names = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        var failOpen = false
        override fun onCreate() = true
        override fun queryRoots(projection: Array<out String>?) = MatrixCursor(arrayOf("root_id"))
        override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(documentId)) }
        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = MatrixCursor(arrayOf("document_id"))
        override fun isChildDocument(parentDocumentId: String, documentId: String) = true
        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            names.add(displayName)
            File(directory, displayName).createNewFile()
            return displayName
        }
        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
            if (failOpen) throw java.io.FileNotFoundException("Document unavailable")
            return ParcelFileDescriptor.open(File(directory, documentId), ParcelFileDescriptor.parseMode(mode))
        }
        override fun deleteDocument(documentId: String) {
            deleted.add(documentId)
            File(directory, documentId).delete()
        }
    }

    private data class Fixture(val controller: ZmodemController, val bridge: TerminalBridge, val transport: AbsTransport, val writes: MutableList<ByteArray>, val cache: File)

    private suspend fun kotlinx.coroutines.test.TestScope.fixture(): Fixture {
        provider.directory = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdirs() }
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = this@ZmodemTransferTest.authority
                readPermission = "android.permission.MANAGE_DOCUMENTS"
                writePermission = "android.permission.MANAGE_DOCUMENTS"
                exported = true
                grantUriPermissions = true
            },
        )
        ShadowContentResolver.registerProviderInternal(authority, provider)
        val uploads = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/octet-stream"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) = provider.queryDocument(uri.lastPathSegment!!, projection)
            override fun openFile(uri: Uri, mode: String) = provider.openDocument(uri.lastPathSegment!!, mode, null)
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unused")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("Unused")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("Unused")
        }
        uploads.attachInfo(context, ProviderInfo().apply { authority = "org.connectbot.test.uploads" })
        ShadowContentResolver.registerProviderInternal("org.connectbot.test.uploads", uploads)
        val manager = mock(TerminalManager::class.java)
        val cache = File(provider.directory, "cache").apply { mkdirs() }
        `when`(manager.cacheDir).thenReturn(cache)
        `when`(manager.contentResolver).thenReturn(context.contentResolver)
        `when`(manager.prefs).thenReturn(context.getSharedPreferences(UUID.randomUUID().toString(), Context.MODE_PRIVATE))
        val bridge = mock(TerminalBridge::class.java)
        val transport = mock(AbsTransport::class.java)
        `when`(bridge.manager).thenReturn(manager)
        `when`(bridge.transport).thenReturn(transport)
        `when`(transport.supportsZmodem).thenReturn(true)
        val writes = mutableListOf<ByteArray>()
        doAnswer {
            writes.add(it.getArgument(0))
            null
        }.`when`(bridge).writeTransfer(
            any(ByteArray::class.java) ?: byteArrayOf(),
            anyBoolean(),
            any(AbsTransport::class.java) ?: transport,
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        return Fixture(ZmodemController(bridge, backgroundScope, CoroutineDispatchers(dispatcher, dispatcher, dispatcher)), bridge, transport, writes, cache)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.start(f: Fixture, upload: Boolean, uris: List<Uri>) {
        val job = launch { f.controller.accept(f.transport, Framing.header(Header(if (upload) Framing.ZRINIT else Framing.ZRQINIT))) }
        runCurrent()
        assertEquals(upload, f.controller.state.value.upload)
        f.controller.selected(f.controller.state.value.picker!!, uris)
        runCurrent()
        job.join()
    }

    private fun header(type: Int, position: Long = 0) = Framing.header(Header(type, position))
    private suspend fun metadata(f: Fixture, name: String, size: Int) {
        f.controller.accept(f.transport, header(Framing.ZFILE) + Framing.packet((name + "\u0000" + size + " 0 0 0\u0000").toByteArray(), Framing.ZCRCW))
    }
    private val tree get() = DocumentsContract.buildTreeDocumentUri(authority, "root")

    @Test
    fun downloadsBytesSanitizesFilenameAndPreservesShellTail() = runTest {
        val f = fixture()
        start(f, false, listOf(tree))
        val data = ByteArray(5000) { it.toByte() }
        metadata(f, "../remote/path/\\file.bin", data.size)
        assertEquals(listOf("file.bin"), provider.names)
        f.controller.accept(f.transport, header(Framing.ZDATA) + Framing.packet(data, Framing.ZCRCW))
        assertEquals(data.size.toLong(), f.controller.state.value.bytes)
        assertEquals(data.size.toLong(), f.controller.state.value.total)
        f.controller.accept(f.transport, header(Framing.ZEOF, data.size.toLong()))
        f.controller.accept(f.transport, header(Framing.ZFIN))
        assertArrayEquals("shell>".toByteArray(), f.controller.accept(f.transport, "OOshell>".toByteArray()))
        assertFalse(f.controller.active)
        assertNull(f.controller.state.value.error)
        assertArrayEquals(data, File(provider.directory, "file.bin").readBytes())
        assertTrue(provider.deleted.isEmpty())
        verify(f.bridge).beginTransfer()
        verify(f.transport).prepareTransfer()
    }

    @Test
    fun lostFinalConfirmationClosesTransferWithoutRetryingOrDeletingDownload() = runTest {
        val f = fixture()
        start(f, false, listOf(tree))
        val data = "GIF89a completed download".toByteArray()
        metadata(f, "completed.gif", data.size)
        f.controller.accept(f.transport, header(Framing.ZDATA) + Framing.packet(data, Framing.ZCRCW))
        f.controller.accept(f.transport, header(Framing.ZEOF, data.size.toLong()))
        // EOF completes one file, not the batch: leave selection/progress active until ZFIN.
        assertTrue(f.controller.active)
        f.controller.accept(f.transport, header(Framing.ZFIN))
        val writes = f.writes.size
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))
        advanceTimeBy(2001)
        runCurrent()
        assertFalse(f.controller.active)
        assertNull(f.controller.state.value.error)
        assertEquals(writes, f.writes.size)
        assertArrayEquals(data, File(provider.directory, "completed.gif").readBytes())
        assertTrue(provider.deleted.isEmpty())
        assertArrayEquals("shell>".toByteArray(), f.controller.accept(f.transport, "shell>".toByteArray()))
    }

    @Test
    fun completionInInitialReadClearsTransferState() = runTest {
        val f = fixture()
        val prompt = "shell>".toByteArray()
        var trailing = byteArrayOf()
        val job = launch {
            trailing = f.controller.accept(f.transport, header(Framing.ZRQINIT) + header(Framing.ZFIN) + "OO".toByteArray() + prompt)
        }
        runCurrent()
        f.controller.selected(f.controller.state.value.picker!!, listOf(tree))
        runCurrent()
        job.join()
        assertFalse(f.controller.active)
        assertNull(f.controller.state.value.error)
        assertArrayEquals(prompt, trailing)
    }

    @Test
    fun cancelDeletesIncompleteDownload() = runTest {
        val f = fixture()
        start(f, false, listOf(tree))
        metadata(f, "partial.bin", 100)
        f.controller.accept(f.transport, header(Framing.ZDATA) + Framing.packet(byteArrayOf(1, 2), Framing.ZCRCW))
        f.controller.cancel()
        f.controller.cancel()
        runCurrent()
        assertFalse(f.controller.active)
        assertEquals(listOf("partial.bin"), provider.deleted)
        assertFalse(File(provider.directory, "partial.bin").exists())
    }

    @Test
    fun downloadOpenFailureDeletesCreatedDocument() = runTest {
        val f = fixture()
        start(f, false, listOf(tree))
        provider.failOpen = true
        metadata(f, "unavailable.bin", 1)
        assertFalse(f.controller.active)
        assertNotNull(f.controller.state.value.error)
        assertEquals(listOf("unavailable.bin"), provider.deleted)
    }

    @Test
    fun rejectsUnsafeRemoteFilename() = runTest {
        val f = fixture()
        start(f, false, listOf(tree))
        metadata(f, "..", 1)
        assertFalse(f.controller.active)
        assertEquals("Unsafe remote filename", f.controller.state.value.error)
        assertTrue(provider.names.isEmpty())
    }

    @Test
    fun uploadsBatchIncludingEmptyFileAndDeletesTemporaryCopies() = runTest {
        val f = fixture()
        val data = ByteArray(20_000) { (it % 127 + 32).toByte() }
        File(provider.directory, "source.bin").writeBytes(data)
        File(provider.directory, "empty.bin").writeBytes(byteArrayOf())
        start(f, true, listOf("source.bin", "empty.bin").map { Uri.parse("content://org.connectbot.test.uploads/$it") })
        assertTrue(f.controller.state.value.toString(), f.controller.active)
        assertEquals(2, f.cache.listFiles()!!.size)
        var offset = 0
        val received = java.io.ByteArrayOutputStream()
        while (offset < data.size) {
            f.writes.clear()
            f.controller.accept(f.transport, header(if (offset == 0) Framing.ZRPOS else Framing.ZACK, offset.toLong()))
            val wire = f.writes.fold(byteArrayOf()) { a, b -> a + b }
            var remainder = wire.copyOfRange(Framing.parseHeader(wire)!!.consumed, wire.size)
            while (remainder.isNotEmpty()) {
                val packet = Framing.parsePacket(remainder, false)!!
                received.write(packet.bytes)
                offset += packet.bytes.size
                remainder = remainder.copyOfRange(packet.consumed, remainder.size)
            }
        }
        assertArrayEquals(data, received.toByteArray())
        f.controller.accept(f.transport, header(Framing.ZACK, offset.toLong()))
        assertEquals(data.size.toLong(), f.controller.state.value.bytes)
        f.controller.accept(f.transport, header(Framing.ZRINIT))
        f.controller.accept(f.transport, header(Framing.ZRPOS))
        f.controller.accept(f.transport, header(Framing.ZRINIT))
        f.controller.accept(f.transport, header(Framing.ZFIN))
        assertFalse(f.controller.active)
        assertNull(f.controller.state.value.error)
        assertTrue(f.cache.listFiles()!!.isEmpty())
        assertArrayEquals(data, File(provider.directory, "source.bin").readBytes())
    }

    @Test
    fun unreadableUploadRemovesTemporaryCopyAndReportsError() = runTest {
        val f = fixture()
        provider.failOpen = true
        start(f, true, listOf(Uri.parse("content://org.connectbot.test.uploads/missing")))
        assertFalse(f.controller.active)
        assertNotNull(f.controller.state.value.error)
        assertTrue(f.cache.listFiles()!!.isEmpty())
        assertArrayEquals(Framing.cancelSequence(), f.writes.last())
    }
}
