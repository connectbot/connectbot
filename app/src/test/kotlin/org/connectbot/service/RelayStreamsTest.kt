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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.transport.AbsTransport
import org.junit.Assert.assertEquals
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RelayStreamsTest {
    private val bridge = mock(TerminalBridge::class.java)
    private val transport = mock(AbsTransport::class.java)
    private val terminal = spy(TerminalEmulatorFactory.create())
    private val output = ByteArrayOutputStream()
    private val stdout = Channel<ByteArray>(Channel.UNLIMITED)
    private val stderr = Channel<ByteArray>(Channel.UNLIMITED)

    private fun relay(dispatcher: kotlinx.coroutines.CoroutineDispatcher): Relay {
        `when`(bridge.terminalEmulator).thenReturn(terminal)
        `when`(transport.getOutputStreams()).thenReturn(listOf(stdout, stderr))
        doAnswer { call ->
            output.write(call.getArgument<ByteArray>(0), call.getArgument<Int>(1), call.getArgument<Int>(2))
            null
        }.`when`(terminal).writeInput(any(ByteArray::class.java) ?: byteArrayOf(), anyInt(), anyInt())
        return Relay(bridge, transport, CoroutineDispatchers(dispatcher, dispatcher, dispatcher), "UTF-8")
    }

    @Test
    fun stderrOnly_isDisplayedBeforeCompletion() = runTest {
        stdout.close()
        stderr.send("remote error\n".toByteArray())
        stderr.close()
        relay(StandardTestDispatcher(testScheduler)).start()
        assertEquals("remote error\n", output.toString("UTF-8"))
        verify(transport).onOutputComplete()
        verify(bridge).cancelAutomation()
    }

    @Test
    fun stdoutEndsFirst_stderrKeepsFlowingWithoutDisconnect() = runTest {
        val relay = relay(StandardTestDispatcher(testScheduler))
        stdout.send("output\n".toByteArray())
        stdout.close()
        val job = launch { relay.start() }
        runCurrent()
        verify(transport, never()).onOutputComplete()
        verify(bridge, never()).cancelAutomation()
        stderr.send("final diagnostic\n".toByteArray())
        stderr.close()
        job.join()
        assertEquals("output\nfinal diagnostic\n", output.toString("UTF-8"))
        verify(transport).onOutputComplete()
    }

    @Test
    fun splitMultibyteOutput_hasIndependentDecoders() = runTest {
        val relay = relay(StandardTestDispatcher(testScheduler))
        val job = launch { relay.start() }
        val euro = "€".toByteArray()
        stdout.send(euro.copyOfRange(0, 1))
        runCurrent()
        stderr.send("stderr".toByteArray())
        runCurrent()
        stdout.send(euro.copyOfRange(1, euro.size))
        runCurrent()
        stdout.close()
        stderr.close()
        job.join()
        assertEquals("stderr€", output.toString("UTF-8"))
    }

    @Test
    fun largePackets_areFullyDrained() = runTest {
        val text = "€".repeat(5000)
        stdout.send(text.toByteArray())
        stdout.close()
        stderr.close()
        relay(StandardTestDispatcher(testScheduler)).start()
        assertEquals(text, output.toString("UTF-8"))
        verify(transport).onOutputComplete()
    }

    @Test
    fun charsetChange_updatesBothStreamDecoders() = runTest {
        val relay = relay(StandardTestDispatcher(testScheduler))
        val job = launch { relay.start() }
        runCurrent()
        relay.setCharset("ISO-8859-1")
        stdout.send(byteArrayOf(0xe9.toByte()))
        runCurrent()
        stderr.send(byteArrayOf(0xe9.toByte()))
        stdout.close()
        stderr.close()
        job.join()
        assertEquals("éé", output.toString("UTF-8"))
    }

    @Test
    fun streamFailure_reportsFailureAndStopsAutomation() = runTest {
        stdout.close(IOException("Connection reset"))
        relay(StandardTestDispatcher(testScheduler)).start()
        verify(transport).onOutputFailure(any(Exception::class.java) ?: Exception())
        verify(transport, never()).onOutputComplete()
        verify(bridge).cancelAutomation()
    }

    @Test
    fun relayCancellation_doesNotReportConnectionFailure() = runTest {
        val relay = relay(StandardTestDispatcher(testScheduler))
        val job = launch { relay.start() }
        runCurrent()
        job.cancel()
        job.join()
        verify(transport, never()).onOutputFailure(any(Exception::class.java) ?: Exception())
        verify(transport, never()).onOutputComplete()
        verify(bridge).cancelAutomation()
    }
}
