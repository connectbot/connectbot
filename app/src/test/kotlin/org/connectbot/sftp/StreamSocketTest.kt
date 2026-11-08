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

import com.trilead.ssh2.Connection
import com.trilead.ssh2.LocalStreamForwarder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.SocketException

class StreamSocketTest {
    @Test
    fun proxySocketForwardsStreamsClosesOnceAndRejectsAccessAfterClose() {
        val connection = mock(Connection::class.java)
        val forwarder = mock(LocalStreamForwarder::class.java)
        val input = ByteArrayInputStream(byteArrayOf(1))
        val output = ByteArrayOutputStream()
        `when`(forwarder.inputStream).thenReturn(input)
        `when`(forwarder.outputStream).thenReturn(output)
        `when`(connection.createLocalStreamForwarder("127.0.0.1", 22)).thenReturn(forwarder)
        val socket = JumpHostProxyData(connection).openConnection("127.0.0.1", 22, 0)
        assertSame(input, socket.getInputStream())
        assertSame(output, socket.getOutputStream())
        assertTrue(socket.isConnected)
        assertFalse(socket.isClosed)
        assertFalse(socket.isInputShutdown)
        assertFalse(socket.isOutputShutdown)
        assertEquals("127.0.0.1", socket.inetAddress.hostAddress)
        assertEquals(22, socket.port)
        assertNull(socket.remoteSocketAddress)
        assertNull(socket.localAddress)
        assertEquals(0, socket.localPort)
        assertNull(socket.localSocketAddress)
        socket.soTimeout = 10
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.sendBufferSize = 1024
        socket.receiveBufferSize = 1024
        assertEquals(0, socket.soTimeout)
        assertFalse(socket.tcpNoDelay)
        assertFalse(socket.keepAlive)
        assertEquals(0, socket.sendBufferSize)
        assertEquals(0, socket.receiveBufferSize)
        socket.close()
        socket.close()
        verify(forwarder, times(1)).close()
        assertTrue(socket.isClosed)
        assertFalse(socket.isConnected)
        assertTrue(socket.isInputShutdown)
        assertTrue(socket.isOutputShutdown)
        assertThrows(SocketException::class.java) { socket.getInputStream() }
        assertThrows(SocketException::class.java) { socket.getOutputStream() }
    }
}
