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

import android.annotation.SuppressLint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.TelnetNegotiationMode
import org.connectbot.data.entity.TelnetNewlinePolicy
import org.connectbot.data.entity.TelnetNonBinaryPolicy
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.telnetlib.NewlinePolicy
import org.connectbot.telnetlib.NonBinaryPolicy
import org.connectbot.telnetlib.TelnetCommand
import org.connectbot.telnetlib.TelnetConfig
import org.connectbot.telnetlib.TelnetDirection
import org.connectbot.telnetlib.TelnetFailure
import org.connectbot.telnetlib.TelnetFailureCode
import org.connectbot.telnetlib.TelnetInput
import org.connectbot.telnetlib.TelnetOption
import org.connectbot.telnetlib.TelnetOutcome
import org.connectbot.telnetlib.TelnetProfile
import org.connectbot.telnetlib.TelnetSession
import org.connectbot.telnetlib.TelnetSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The scripted peers run on the host JVM (Java 17), independently of Android's InputStream API.
@SuppressLint("NewApi")
@RunWith(RobolectricTestRunner::class)
class TelnetTest {
    private val dispatchers = CoroutineDispatchers(Dispatchers.Default, Dispatchers.IO, Dispatchers.Default)

    @Test
    fun closingUnstartedLocalTransportDoesNotKillAProcess() = runTest {
        val killer = Mockito.mock(Local.Killer::class.java)
        val transport = Local(killer)
        transport.close()
        transport.close()
        Mockito.verifyNoInteractions(killer)
    }

    @Test
    fun closingLocalProcessIsIdempotent() = runTest {
        val killer = Mockito.mock(Local.Killer::class.java)
        val transport = Local(killer)
        Local::class.java.getDeclaredField("shellPid").apply {
            isAccessible = true
            setInt(transport, 42)
        }
        transport.close()
        transport.close()
        Mockito.verify(killer).killProcess(42)
        Mockito.verifyNoMoreInteractions(killer)
    }

    @Test
    fun defaultKeyboardAndEnterDelegateToByteWrites() = runTest {
        val transport = Mockito.mock(AbsTransport::class.java, Mockito.CALLS_REAL_METHODS)
        val bytes = byteArrayOf(1, 2)
        transport.writeKeyboardInput(bytes)
        Mockito.verify(transport).write(bytes)
        transport.carriageReturn()
        Mockito.verify(transport).write(byteArrayOf(13))
        assertNull(transport.userMessageForError(java.io.IOException("failure")))
    }

    @Test
    fun adapterOwnsKeyboardEncodingRejectionsAndLifecycle() = runTest {
        val transport = Telnet()
        val bridge = Mockito.mock(TerminalBridge::class.java)
        transport.bridge = bridge
        Mockito.`when`(bridge.transport).thenReturn(transport)
        transport.host = Host(hostname = "127.0.0.1")
        val session = FakeSession(java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), 54321))
        transport.connector = { _, _, _, _, _ -> TelnetOutcome.Success(session) }
        transport.writeKeyboardInput("ignored before connect".toByteArray())
        transport.connect(backgroundScope, dispatchers, "ISO-8859-1")
        assertEquals("127.0.0.1", transport.getLocalIpAddress())
        Mockito.verify(bridge).onTransportConnected(transport)
        transport.writeKeyboardInput("é\rB".toByteArray())
        assertArrayEquals(byteArrayOf(0xe9.toByte()), session.writes[0])
        assertArrayEquals(byteArrayOf('B'.code.toByte()), session.writes[1])
        assertEquals(listOf("write:-23", "enter", "write:66"), session.operations)
        assertEquals(-1, transport.read(ByteArray(16), 0, 16))
        transport.onReadEof()
        Mockito.verify(bridge).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
        transport.onConnectionFailure(java.io.IOException("refused"))
        transport.onReadFailure(java.io.IOException("read failure"))
        Mockito.verify(bridge, Mockito.times(2)).dispatchDisconnect(DisconnectReason.IO_ERROR)
        assertEquals(
            R.string.telnet_ascii_input_rejected,
            transport.userMessageForError(TelnetOperationException(TelnetFailure(TelnetFailureCode.NON_ASCII_DATA, "nonascii"))),
        )
        assertEquals(
            R.string.telnet_input_rejected,
            transport.userMessageForError(TelnetOperationException(TelnetFailure(TelnetFailureCode.UNSUPPORTED_OPERATION, "unsupported"))),
        )
        assertNull(transport.userMessageForError(java.io.IOException("other failure")))
        transport.close()
        assertNull(transport.getLocalIpAddress())
    }

    @Test
    fun configurationUsesHostChoicesAndActualTerminalDimensions() {
        val host = Host(
            telnetNegotiationMode = TelnetNegotiationMode.PASSIVE,
            telnetNonBinaryPolicy = TelnetNonBinaryPolicy.STRICT_ASCII,
            telnetNewlinePolicy = TelnetNewlinePolicy.PRESERVE_LF,
        )
        val config = telnetConfig(host, "screen", 132, 43)
        assertEquals(listOf("screen"), config.terminalTypes)
        assertEquals(132, config.columns)
        assertEquals(43, config.rows)
        assertEquals(TelnetProfile.PASSIVE, config.profile)
        assertEquals(NonBinaryPolicy.STRICT_ASCII, config.nonBinaryPolicy)
        assertEquals(NewlinePolicy.PRESERVE_LF, config.newlinePolicy)
        assertNull(config.charset)
        assertFalse(config.consumeRecords)
        val defaults = telnetConfig(Host(), null, 80, 24)
        assertEquals(TelnetProfile.INTERACTIVE, defaults.profile)
        assertEquals(NonBinaryPolicy.EIGHT_BIT_COMPATIBILITY, defaults.nonBinaryPolicy)
        assertEquals(NewlinePolicy.LF_TO_CRLF, defaults.newlinePolicy)
    }

    @Test
    fun filtersAddressesAndTriesNextAddressAfterFailure() = runTest {
        val transport = Telnet()
        transport.host = Host(hostname = "example.com", port = 23, ipVersion = "IPV4_ONLY")
        transport.resolveAddresses = { listOf(InetAddress.getByName("::1"), InetAddress.getByName("127.0.0.2"), InetAddress.getByName("127.0.0.1")) }
        val tried = mutableListOf<String>()
        val session = FakeSession()
        transport.connector = { _, address, _, _, _ ->
            tried.add(address)
            if (address == "127.0.0.1") {
                TelnetOutcome.Success(session)
            } else {
                TelnetOutcome.Failure(TelnetFailure(TelnetFailureCode.TRANSPORT_ERROR, "refused"))
            }
        }
        transport.setInitialDimensions(99, 31, 0, 0)
        transport.connect(backgroundScope, dispatchers, "CP437")
        assertNull(transport.getLocalIpAddress())
        assertEquals(listOf("127.0.0.2", "127.0.0.1"), tried)
        transport.write(byteArrayOf(1))
        transport.carriageReturn()
        transport.resize(120, 40, 0, 0)
        transport.flush()
        transport.close()
        transport.close()
        assertFalse(transport.isConnected())
        assertEquals(TelnetFailureCode.CLOSED, (transport.receive() as TelnetOutcome.Failure).error.code)
        assertEquals(listOf("write:1", "enter", "resize:120:40", "flush", "close"), session.operations)
    }

    @Test
    fun closingDuringConnectCancelsTheOwner() = runBlocking {
        withTimeout(5000) {
            val transport = Telnet()
            transport.host = Host(hostname = "127.0.0.1")
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            transport.connector = { scope, _, _, _, _ ->
                entered.complete(Unit)
                withContext(scope.coroutineContext) { awaitCancellation() }
            }
            val connection = async { transport.connect(this@withTimeout, dispatchers, "UTF-8") }
            entered.await()
            transport.close()
            connection.join()
            assertTrue(connection.isCancelled)
            assertFalse(transport.isSessionOpen())
        }
    }

    @Test
    fun loopbackPeerSeesQuotedIacNewlinesAndTypedEnterAndInputDrainsAtEof() = runBlocking {
        withTimeout(10000) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val flushCompleted = CountDownLatch(1)
                val transcript = CompletableFuture.supplyAsync {
                    server.accept().use { peer ->
                        peer.soTimeout = 5000
                        // WILL ECHO, text, GA, and a final partial UTF-8 byte before EOF.
                        peer.getOutputStream().write(byteArrayOf(-1, -5, 1, 'P'.code.toByte(), -1, -7, 0xc3.toByte()))
                        peer.getOutputStream().flush()
                        val expectedCount = 10 // DO ECHO + quoted 0xff + CRLF + typed CR NUL + 'Z'.
                        val received = peer.getInputStream().readNBytes(expectedCount)
                        assertTrue(flushCompleted.await(5, TimeUnit.SECONDS))
                        peer.shutdownOutput()
                        received
                    }
                }
                val transport = Telnet()
                transport.host = Host(hostname = "127.0.0.1", port = server.localPort, telnetNegotiationMode = TelnetNegotiationMode.PASSIVE)
                transport.connect(this@withTimeout, dispatchers, "UTF-8")
                assertEquals("127.0.0.1", transport.getLocalIpAddress())
                try {
                    val input = mutableListOf<TelnetInput>()
                    val echoSeen = kotlinx.coroutines.CompletableDeferred<Unit>()
                    val receiver = async {
                        while (true) {
                            when (val result = transport.receive()) {
                                is TelnetOutcome.Failure -> error(result.error.toString())

                                is TelnetOutcome.Success -> {
                                    val value = result.value ?: break
                                    input.add(value)
                                    if (value is TelnetInput.EchoChanged) echoSeen.complete(Unit)
                                }
                            }
                        }
                    }
                    echoSeen.await()
                    transport.write(byteArrayOf(-1, 10))
                    transport.carriageReturn()
                    transport.write(byteArrayOf('Z'.code.toByte()))
                    transport.flush()
                    flushCompleted.countDown()
                    receiver.await()
                    val wire = withContext(Dispatchers.IO) { transcript.get(5, TimeUnit.SECONDS) }
                    assertArrayEquals(byteArrayOf(-1, -3, 1, -1, -1, 13, 10, 13, 0, 'Z'.code.toByte()), wire)
                    assertTrue(input.first() is TelnetInput.EchoChanged)
                    assertTrue(input.any { it is TelnetInput.GoAhead })
                    val data = input.filterIsInstance<TelnetInput.Data>().flatMap { it.bytes.toList() }.toByteArray()
                    assertArrayEquals(byteArrayOf('P'.code.toByte(), 0xc3.toByte()), data)
                } finally {
                    transport.close()
                }
            }
        }
    }

    @Test
    fun resizeDuringConnectIsReplayedBeforeSessionStarts() = runBlocking {
        withTimeout(5000) {
            val transport = Telnet()
            transport.host = Host(hostname = "127.0.0.1")
            transport.setInitialDimensions(80, 24, 0, 0)
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val proceed = kotlinx.coroutines.CompletableDeferred<Unit>()
            val session = FakeSession()
            transport.connector = { _, _, _, config, _ ->
                assertEquals(80, config.columns)
                entered.complete(Unit)
                proceed.await()
                TelnetOutcome.Success(session)
            }
            val connection = async { transport.connect(this@withTimeout, dispatchers, "UTF-8") }
            entered.await()
            transport.resize(120, 43, 0, 0)
            proceed.complete(Unit)
            connection.await()
            assertEquals(listOf("resize:120:43"), session.operations)
            transport.close()
        }
    }

    private class FakeSession(private val localEndpoint: java.net.InetSocketAddress? = null) : TelnetSession {
        override fun getLocalSocketAddress(): java.net.InetSocketAddress? = localEndpoint
        override val state = MutableStateFlow(TelnetSnapshot())
        val operations = mutableListOf<String>()
        val writes = mutableListOf<ByteArray>()
        override suspend fun receive(): TelnetOutcome<TelnetInput?> = TelnetOutcome.Success(null)
        override suspend fun write(data: ByteArray, offset: Int, length: Int): TelnetOutcome<Unit> {
            writes.add(data.copyOfRange(offset, offset + length))
            operations.add("write:${data[offset]}")
            return TelnetOutcome.Success(Unit)
        }
        override suspend fun carriageReturn(): TelnetOutcome<Unit> {
            operations.add("enter")
            return TelnetOutcome.Success(Unit)
        }
        override suspend fun resize(columns: Int, rows: Int): TelnetOutcome<Unit> {
            operations.add("resize:$columns:$rows")
            return TelnetOutcome.Success(Unit)
        }
        override suspend fun flush(): TelnetOutcome<Unit> {
            operations.add("flush")
            return TelnetOutcome.Success(Unit)
        }
        override suspend fun close(): TelnetOutcome<Unit> {
            operations.add("close")
            return TelnetOutcome.Success(Unit)
        }
        override suspend fun sendCommand(command: TelnetCommand): TelnetOutcome<Unit> = TelnetOutcome.Success(Unit)
        override suspend fun requestOption(option: TelnetOption, direction: TelnetDirection, enable: Boolean): TelnetOutcome<Unit> = TelnetOutcome.Success(Unit)
    }
}
