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

package org.connectbot.transport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.telnetlib.TelnetDirection
import org.connectbot.telnetlib.TelnetOption
import org.connectbot.telnetlib.TelnetOutcome
import org.connectbot.telnetlib.TelnetSession
import org.connectbot.telnetlib.TelnetSnapshot
import org.connectbot.zmodemlib.Framing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.io.IOException
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
class ZmodemTelnetTest {
    @Test
    fun transferRequiresBothBinaryDirectionsAndBypassesLocalEcho() = runTest {
        val state = MutableStateFlow(TelnetSnapshot())
        var written: ByteArray? = null
        val session = object : TelnetSession by mock(TelnetSession::class.java) {
            override val state = state
            override suspend fun requestOption(
                option: TelnetOption,
                direction: TelnetDirection,
                enable: Boolean,
            ): TelnetOutcome<Unit> = TelnetOutcome.Success(Unit)
            override suspend fun write(data: ByteArray, offset: Int, length: Int): TelnetOutcome<Unit> {
                written = data.copyOfRange(offset, offset + length)
                return TelnetOutcome.Success(Unit)
            }
            override suspend fun close(): TelnetOutcome<Unit> = TelnetOutcome.Success(Unit)
        }
        val bridge = mock(TerminalBridge::class.java)
        val transport = Telnet().apply {
            host = Host(hostname = "127.0.0.1")
            this.bridge = bridge
            resolveAddresses = { listOf(InetAddress.getLoopbackAddress()) }
            connector = { _, _, _, _, _ -> TelnetOutcome.Success(session) }
        }
        val dispatcher = StandardTestDispatcher(testScheduler)
        transport.connect(backgroundScope, CoroutineDispatchers(dispatcher, dispatcher, dispatcher), "UTF-8")
        try {
            try {
                transport.writeTransfer(byteArrayOf(-1))
                fail("Transfer must reject non-BINARY sessions")
            } catch (_: IOException) { }
            assertTrue(written == null)
            var ready = false
            val job = launch {
                transport.prepareTransfer()
                ready = true
            }
            runCurrent()
            state.value = TelnetSnapshot(incomingBinary = true)
            runCurrent()
            assertFalse(ready)
            state.value = TelnetSnapshot(incomingBinary = true, outgoingBinary = true)
            runCurrent()
            assertTrue(ready)
            job.join()
            val bytes = byteArrayOf(0, 13, 10, -1, 24, -128)
            transport.writeTransfer(bytes)
            assertArrayEquals(bytes, written)
            verify(bridge, never()).displayLocalEcho(eq(transport) ?: transport, any(ByteArray::class.java) ?: byteArrayOf())
            state.value = TelnetSnapshot(incomingBinary = false, outgoingBinary = true)
            try {
                transport.verifyTransfer()
                fail("Transfer must detect BINARY revocation")
            } catch (_: IOException) { }
            state.value = TelnetSnapshot(incomingBinary = true, outgoingBinary = false)
            expectIOException { transport.verifyTransfer() }
            expectIOException { transport.writeTransfer(bytes) }
            // CAN and backspace remain valid ASCII even when BINARY has been revoked.
            transport.writeTransferAbort(Framing.cancelSequence())
            assertArrayEquals(Framing.cancelSequence(), written)
            verify(bridge, never()).displayLocalEcho(eq(transport) ?: transport, any(ByteArray::class.java) ?: byteArrayOf())
        } finally {
            transport.close()
        }
        expectIOException { transport.prepareTransfer() }
        expectIOException { transport.writeTransfer(byteArrayOf(1)) }
        expectIOException { transport.writeTransferAbort(Framing.cancelSequence()) }
        expectIOException { transport.verifyTransfer() }
    }

    private suspend fun expectIOException(operation: suspend () -> Unit) {
        try {
            operation()
            fail("Closed or non-BINARY transports must reject transfer operations")
        } catch (_: IOException) { }
    }

    @Test
    fun defaultTransportHooksPreserveNormalWriteBehavior() = runTest {
        val transport = mock(AbsTransport::class.java, CALLS_REAL_METHODS)
        val writes = mutableListOf<ByteArray>()
        doAnswer {
            writes.add(it.getArgument(0))
            null
        }.`when`(transport).write(any(ByteArray::class.java) ?: byteArrayOf())
        assertFalse(transport.supportsZmodem)
        transport.prepareTransfer()
        transport.verifyTransfer()
        val data = byteArrayOf(0, 13, -1)
        transport.writeTransfer(data)
        transport.writeTransferAbort(Framing.cancelSequence())
        assertArrayEquals(data, writes[0])
        assertArrayEquals(Framing.cancelSequence(), writes[1])
    }

    @Test
    fun transferRequiresAConnectedSession() = runTest {
        val transport = Telnet()
        assertTrue(transport.supportsZmodem)
        expectIOException { transport.prepareTransfer() }
        expectIOException { transport.writeTransfer(byteArrayOf(1)) }
        expectIOException { transport.writeTransferAbort(Framing.cancelSequence()) }
        expectIOException { transport.verifyTransfer() }
    }
}
