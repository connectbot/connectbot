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

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.telnetlib.TelnetFailure
import org.connectbot.telnetlib.TelnetFailureCode
import org.connectbot.telnetlib.TelnetInput
import org.connectbot.telnetlib.TelnetOutcome
import org.connectbot.telnetlib.TelnetSession
import org.connectbot.telnetlib.TelnetSnapshot
import org.connectbot.telnetlib.TelnetStatus
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.transport.Telnet
import org.connectbot.transport.TelnetOperationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.net.InetAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RelayTelnetTest {
    private val bridge = mock(TerminalBridge::class.java)
    private val transport = Telnet()
    private val terminal = spy(TerminalEmulatorFactory.create())
    private val output = ByteArrayOutputStream()
    private val input = Channel<TelnetOutcome<TelnetInput?>>(Channel.UNLIMITED)
    private var receives = 0
    private var operationResult: TelnetOutcome<Unit> = TelnetOutcome.Success(Unit)
    private val sessionState = MutableStateFlow(TelnetSnapshot())
    private val session = object : TelnetSession by mock(TelnetSession::class.java) {
        override val state = sessionState
        override suspend fun receive(): TelnetOutcome<TelnetInput?> {
            receives++
            return input.receive()
        }
        override suspend fun write(data: ByteArray, offset: Int, length: Int): TelnetOutcome<Unit> = operationResult
        override suspend fun close(): TelnetOutcome<Unit> = TelnetOutcome.Success(Unit)
    }

    private suspend fun TestScope.relay(): Relay {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        transport.host = Host(hostname = "127.0.0.1")
        transport.bridge = bridge
        transport.resolveAddresses = { listOf(InetAddress.getLoopbackAddress()) }
        transport.connector = { _, _, _, _, _ -> TelnetOutcome.Success(session) }
        `when`(bridge.transport).thenReturn(transport)
        `when`(bridge.terminalEmulator).thenReturn(terminal)
        doAnswer { call ->
            output.write(call.getArgument<ByteArray>(0), call.getArgument<Int>(1), call.getArgument<Int>(2))
            null
        }.`when`(terminal).writeInput(any(ByteArray::class.java) ?: byteArrayOf(), anyInt(), anyInt())
        transport.connect(backgroundScope, dispatchers, "UTF-8")
        return Relay(bridge, transport, dispatchers, "UTF-8")
    }

    private suspend fun send(event: TelnetInput?) = input.send(TelnetOutcome.Success(event))

    @Test
    fun orderedEventsPreservePartialCharactersAndEofFlushesTail() = runTest {
        val relay = relay()
        val job = launch { relay.start() }
        send(TelnetInput.Data(byteArrayOf('A'.code.toByte(), 0xe2.toByte())))
        send(TelnetInput.EchoChanged(false))
        runCurrent()
        assertEquals("A", output.toString("UTF-8"))
        transport.write("hidden".toByteArray())
        verify(bridge, never()).displayLocalEcho(any(Telnet::class.java) ?: transport, any(ByteArray::class.java) ?: byteArrayOf())
        send(TelnetInput.GoAhead)
        send(TelnetInput.EndOfRecord)
        send(TelnetInput.CharsetAgreed("UTF-8"))
        send(TelnetInput.Diagnostic("boundary"))
        send(TelnetInput.Data(byteArrayOf(0x82.toByte(), 0xac.toByte(), 0xc3.toByte())))
        send(TelnetInput.EchoChanged(true))
        runCurrent()
        assertEquals("A€", output.toString("UTF-8"))
        transport.write("visible".toByteArray())
        verify(bridge).displayLocalEcho(transport, "visible".toByteArray())
        send(null)
        job.join()
        assertEquals("A€�", output.toString("UTF-8"))
        verify(bridge).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
        verify(bridge).cancelAutomation()
    }

    @Test
    fun completeDataPrecedesFailureAndFailureDoesNotFlushPartialCharacter() = runTest {
        val relay = relay()
        send(TelnetInput.Data(byteArrayOf('A'.code.toByte(), 0xe2.toByte())))
        input.send(TelnetOutcome.Failure(TelnetFailure(TelnetFailureCode.TRANSPORT_ERROR, "Reset")))
        relay.start()
        assertEquals("A", output.toString("UTF-8"))
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
        verify(bridge, never()).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
        verify(bridge).cancelAutomation()
    }

    private suspend fun assertWriteRejected(failure: TelnetFailure) {
        operationResult = TelnetOutcome.Failure(failure)
        try {
            transport.write("rejected".toByteArray())
            fail("Expected operation failure")
        } catch (error: TelnetOperationException) {
            assertSame(failure, error.failure)
        }
        verify(bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.IO_ERROR)
        verify(bridge, never()).displayLocalEcho(any(Telnet::class.java) ?: transport, any(ByteArray::class.java) ?: byteArrayOf())
    }

    @Test
    fun failedWriteWaitsForQueuedInputBeforeDisconnect() = runTest {
        val relay = relay()
        val failure = TelnetFailure(TelnetFailureCode.TRANSPORT_ERROR, "Write failed")
        sessionState.value = TelnetSnapshot(status = TelnetStatus.FAILED, failure = failure)
        send(TelnetInput.Data("queued before failure".toByteArray()))
        input.send(TelnetOutcome.Failure(failure))
        assertWriteRejected(failure)
        doAnswer {
            assertEquals("queued before failure", output.toString("UTF-8"))
            null
        }.`when`(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
        relay.start()
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
        verify(bridge).cancelAutomation()
    }

    @Test
    fun closedWriteAfterPeerEofPreservesQueuedInputAndEofReason() = runTest {
        val relay = relay()
        sessionState.value = TelnetSnapshot(status = TelnetStatus.PEER_EOF)
        send(TelnetInput.Data("final output".toByteArray()))
        send(null)
        assertWriteRejected(TelnetFailure(TelnetFailureCode.CLOSED, "Peer finished"))
        relay.start()
        assertEquals("final output", output.toString("UTF-8"))
        verify(bridge).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
        verify(bridge, never()).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun rejectedOperationsDoNotInferSessionFailureFromErrorCode() = runTest {
        val relay = relay()
        for ((code, message) in listOf(
            TelnetFailureCode.NON_ASCII_DATA to R.string.telnet_ascii_input_rejected,
            TelnetFailureCode.UNSUPPORTED_OPERATION to R.string.telnet_input_rejected,
            TelnetFailureCode.INTERNAL_ERROR to null,
        )) {
            val failure = TelnetFailure(code, "Operation rejected")
            assertWriteRejected(failure)
            assertEquals(message, transport.userMessageForError(TelnetOperationException(failure)))
        }
        operationResult = TelnetOutcome.Success(Unit)
        transport.write("accepted".toByteArray())
        verify(bridge).displayLocalEcho(transport, "accepted".toByteArray())
        send(TelnetInput.Data("still receiving".toByteArray()))
        send(null)
        relay.start()
        assertEquals("still receiving", output.toString("UTF-8"))
        verify(bridge).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
        verify(bridge, never()).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun unexpectedCharsetAgreementStopsBeforeSubsequentData() = runTest {
        val relay = relay()
        send(TelnetInput.Data("before".toByteArray()))
        send(TelnetInput.CharsetAgreed("ISO-8859-1"))
        send(TelnetInput.Data("after".toByteArray()))
        relay.start()
        assertEquals("before", output.toString("UTF-8"))
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun largeDataEventsAreFullyDecoded() = runTest {
        val relay = relay()
        val text = "€".repeat(5000)
        send(TelnetInput.Data(text.toByteArray()))
        send(null)
        relay.start()
        assertEquals(text, output.toString("UTF-8"))
        verify(bridge).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
    }

    @Test
    fun duplicateStartsKeepOneOrderedConsumer() = runTest {
        val relay = relay()
        val job = launch { relay.start() }
        runCurrent()
        relay.start()
        send(TelnetInput.Data("once".toByteArray()))
        send(null)
        job.join()
        assertEquals("once", output.toString("UTF-8"))
        assertEquals(2, receives)
    }

    @Test
    fun cancellationWhileReceivingDoesNotReportDisconnect() = runTest {
        val relay = relay()
        val job = launch { relay.start() }
        runCurrent()
        job.cancelAndJoin()
        verify(bridge, never()).dispatchDisconnect(any(DisconnectReason::class.java) ?: DisconnectReason.IO_ERROR)
        verify(bridge).cancelAutomation()
    }
}
