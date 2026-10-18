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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.TelnetNonBinaryPolicy
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.telnetlib.TelnetFailureCode
import org.connectbot.telnetlib.TelnetInput
import org.connectbot.telnetlib.TelnetOutcome
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// The scripted peer uses host Java 17 stream APIs.
@SuppressLint("NewApi")
@RunWith(Parameterized::class)
class TelnetBinaryPolicyTest(
    private val acceptBinary: Boolean,
    private val policy: TelnetNonBinaryPolicy,
    private val expectedWire: String,
    private val rejectNonAscii: Boolean,
) {
    private val dispatchers = CoroutineDispatchers(Dispatchers.Default, Dispatchers.IO, Dispatchers.Default)

    @Test
    fun binaryAcceptanceAndRefusalApplyTheSelectedFallbackPolicy() = runBlocking {
        withTimeout(10000) {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                val flushCompleted = CountDownLatch(1)
                val wire = CompletableFuture.supplyAsync {
                    server.accept().use { peer ->
                        peer.soTimeout = 5000
                        assertEquals(18, peer.getInputStream().readNBytes(18).size)
                        peer.getOutputStream().write(
                            byteArrayOf(
                                -1,
                                if (acceptBinary) -3 else -2,
                                0,
                                -1,
                                if (acceptBinary) -5 else -4,
                                0,
                                'R'.code.toByte(),
                            ),
                        )
                        peer.getOutputStream().flush()
                        val received = peer.getInputStream().readNBytes(expectedWire.toByteArray().size)
                        assertTrue(flushCompleted.await(5, TimeUnit.SECONDS))
                        received
                    }
                }
                val transport = Telnet()
                transport.host = Host(hostname = "127.0.0.1", port = server.localPort, telnetNonBinaryPolicy = policy)
                transport.connect(this@withTimeout, dispatchers, "UTF-8")
                try {
                    while ((transport.receive() as TelnetOutcome.Success<TelnetInput?>).value !is TelnetInput.Data) { }
                    if (rejectNonAscii) {
                        try {
                            transport.write("é\n".toByteArray())
                            fail("Non-ASCII input should be rejected")
                        } catch (e: TelnetOperationException) {
                            assertEquals(TelnetFailureCode.NON_ASCII_DATA, e.failure.code)
                            assertTrue(transport.isSessionOpen())
                        }
                        transport.write(byteArrayOf('A'.code.toByte()))
                    } else {
                        transport.write("é\n".toByteArray())
                    }
                    transport.flush()
                    flushCompleted.countDown()
                    val received = withContext(Dispatchers.IO) { wire.get(5, TimeUnit.SECONDS) }
                    assertArrayEquals(expectedWire.toByteArray(), received)
                } finally {
                    transport.close()
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "binary={0}, fallback={1}")
        fun data(): List<Array<Any>> = listOf(
            arrayOf(true, TelnetNonBinaryPolicy.STRICT_ASCII, "é\n", false),
            arrayOf(true, TelnetNonBinaryPolicy.EIGHT_BIT_COMPATIBILITY, "é\n", false),
            arrayOf(false, TelnetNonBinaryPolicy.STRICT_ASCII, "A", true),
            arrayOf(false, TelnetNonBinaryPolicy.EIGHT_BIT_COMPATIBILITY, "é\r\n", false),
        )
    }
}
