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

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.transport.AbsTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException
import java.nio.ByteBuffer

class RelayTest {

    @Test
    fun advanceAfterRead_validRead_advancesPosition() {
        val buffer = ByteBuffer.allocate(8)
        buffer.position(3)

        val endOfInput = buffer.advanceAfterRead(bytesRead = 2, requestedLength = 5)

        assertFalse(endOfInput)
        assertEquals(5, buffer.position())
    }

    @Test
    fun advanceAfterRead_remoteEof_reportsEndOfInput() {
        val buffer = ByteBuffer.allocate(8)
        buffer.position(3)

        val endOfInput = buffer.advanceAfterRead(bytesRead = -1, requestedLength = 5)

        assertTrue(endOfInput)
        assertEquals(3, buffer.position())
    }

    @Test
    fun advanceAfterRead_tooManyBytesRead_reportsIoFailure() {
        val buffer = ByteBuffer.allocate(8)
        buffer.position(3)

        assertThrows(IOException::class.java) {
            buffer.advanceAfterRead(bytesRead = 6, requestedLength = 5)
        }
        assertEquals(3, buffer.position())
    }

    @Test
    fun advanceAfterRead_invalidNegativeCount_reportsIoFailure() {
        val buffer = ByteBuffer.allocate(8)
        buffer.position(3)

        assertThrows(IOException::class.java) {
            buffer.advanceAfterRead(bytesRead = -2, requestedLength = 5)
        }
        assertEquals(3, buffer.position())
    }

    @Test
    fun countEnqBytes_noEnq_returnsZero() {
        val data = "plain text".toByteArray()

        assertEquals(0, countEnqBytes(data, data.size))
    }

    @Test
    fun countEnqBytes_multipleEnq_countsEach() {
        val data = byteArrayOf(0x05, 'a'.code.toByte(), 0x05, 0x05)

        assertEquals(3, countEnqBytes(data, data.size))
    }

    @Test
    fun countEnqBytes_respectsLength() {
        val data = byteArrayOf('a'.code.toByte(), 0x05, 0x05)

        assertEquals(1, countEnqBytes(data, 2))
    }

    @Test
    fun start_enqInStream_sendsAnswerbackAndRelaysData() = runTest {
        val bridge = mock<TerminalBridge>()
        val payload = byteArrayOf('A'.code.toByte(), 0x05, 'B'.code.toByte())
        val received = StringBuilder()
        val relay = Relay(bridge, transportReturning(payload), testDispatchers(), "UTF-8") { data, offset, length ->
            received.append(String(data, offset, length, Charsets.UTF_8))
        }

        relay.start()

        verify(bridge, times(1)).sendAnswerback()
        assertEquals("A\u0005B", received.toString())
    }

    @Test
    fun start_noEnqInStream_doesNotSendAnswerback() = runTest {
        val bridge = mock<TerminalBridge>()
        val relay = Relay(bridge, transportReturning("no controls here".toByteArray()), testDispatchers(), "UTF-8") { _, _, _ -> }

        relay.start()

        verify(bridge, never()).sendAnswerback()
    }

    private fun transportReturning(payload: ByteArray): AbsTransport {
        val transport = mock<AbsTransport>()
        var delivered = false
        whenever(transport.read(any(), any(), any())).thenAnswer { invocation ->
            if (delivered) {
                -1
            } else {
                delivered = true
                val buffer = invocation.getArgument<ByteArray>(0)
                val offset = invocation.getArgument<Int>(1)
                payload.copyInto(buffer, offset)
                payload.size
            }
        }
        return transport
    }

    private fun TestScope.testDispatchers(): CoroutineDispatchers {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CoroutineDispatchers(default = dispatcher, io = dispatcher, main = dispatcher)
    }
}
