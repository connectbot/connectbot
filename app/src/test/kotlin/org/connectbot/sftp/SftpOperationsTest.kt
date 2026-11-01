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

package org.connectbot.sftp

import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.SFTPv3FileAttributes
import com.trilead.ssh2.SFTPv3FileHandle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class SftpOperationsTest {
    @Test
    fun downloadsBytesReportsProgressAndClosesHandle() = runBlocking {
        val client = mock(SFTPv3Client::class.java)
        val handle = mock(SFTPv3FileHandle::class.java)
        `when`(client.stat("/file")).thenReturn(SFTPv3FileAttributes().apply { size = 3L })
        `when`(client.openFileRO("/file")).thenReturn(handle)
        `when`(client.read(eq(handle), anyLong(), any(ByteArray::class.java), eq(0), anyInt())).thenAnswer {
            if (it.getArgument<Long>(1) == 0L) {
                byteArrayOf(1, 2, 3).copyInto(it.getArgument(2))
                3
            } else {
                -1
            }
        }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Pair<Long, Long>>()
        SftpOperations(client).downloadFile("/file", output) { done, total -> progress.add(done to total) }
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
        assertEquals(listOf(3L to 3L), progress)
        verify(client).closeFile(handle)
    }

    @Test
    fun transfersAlwaysCloseHandlesAndIgnoreOnlyCloseErrors() = runBlocking {
        val client = mock(SFTPv3Client::class.java)
        val handle = mock(SFTPv3FileHandle::class.java)
        `when`(client.stat("/empty")).thenReturn(SFTPv3FileAttributes())
        `when`(client.openFileRO("/empty")).thenReturn(handle)
        `when`(client.read(eq(handle), anyLong(), any(ByteArray::class.java), eq(0), anyInt())).thenReturn(0)
        doThrow(IOException("closed")).`when`(client).closeFile(handle)
        val operations = SftpOperations(client)
        operations.downloadFile("/empty", ByteArrayOutputStream())
        `when`(client.createFileTruncate("/upload")).thenReturn(handle)
        val progress = mutableListOf<Long>()
        operations.uploadFile(ByteArrayInputStream(byteArrayOf(7, 8)), "/upload", 2) { done, _ -> progress.add(done) }
        assertEquals(listOf(2L), progress)
        verify(client).write(eq(handle), eq(0L), any(ByteArray::class.java), eq(0), eq(2))
        operations.uploadFile(ByteArrayInputStream(byteArrayOf()), "/upload", 0)
        verify(client, times(3)).closeFile(handle)
    }

    @Test
    fun failedReadStillClosesHandleAndPropagatesFailure() = runBlocking {
        val client = mock(SFTPv3Client::class.java)
        val handle = mock(SFTPv3FileHandle::class.java)
        `when`(client.stat("/file")).thenReturn(SFTPv3FileAttributes())
        `when`(client.openFileRO("/file")).thenReturn(handle)
        `when`(client.read(eq(handle), anyLong(), any(ByteArray::class.java), eq(0), anyInt())).thenThrow(IOException("read failed"))
        try {
            SftpOperations(client).downloadFile("/file", ByteArrayOutputStream())
            fail("Read errors must propagate")
        } catch (e: IOException) {
            assertEquals("read failed", e.message)
        }
        verify(client).closeFile(handle)
    }

    @Test
    fun metadataAndMutationOperationsUseRequestedPaths() = runBlocking {
        val client = mock(SFTPv3Client::class.java)
        val attributes = SFTPv3FileAttributes()
        `when`(client.stat("/a")).thenReturn(attributes)
        `when`(client.lstat("/a")).thenReturn(attributes)
        `when`(client.ls("/a")).thenReturn(java.util.Vector())
        `when`(client.canonicalPath("/a")).thenReturn("/resolved")
        `when`(client.readLink("/a")).thenReturn("/target")
        val operations = SftpOperations(client)
        assertSame(attributes, operations.stat("/a"))
        assertSame(attributes, operations.lstat("/a"))
        assertTrue(operations.listDirectory("/a").isEmpty())
        assertEquals("/resolved", operations.canonicalPath("/a"))
        assertEquals("/target", operations.readLink("/a"))
        operations.mkdir("/directory")
        operations.rmdir("/directory")
        operations.rm("/a")
        operations.mv("/a", "/b")
        operations.close()
        verify(client).mkdir("/directory", 493)
        verify(client).rmdir("/directory")
        verify(client).rm("/a")
        verify(client).mv("/a", "/b")
        verify(client).close()
    }
}
