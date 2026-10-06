/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.telnetlib.TelnetInput
import org.connectbot.telnetlib.TelnetOutcome
import org.connectbot.transport.AbsTransport
import org.connectbot.transport.Telnet
import org.connectbot.transport.TelnetOperationException
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CoderMalfunctionError
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coroutine-based relay that handles incoming data from the transport to the terminal buffer.
 * Handles charset decoding and East Asian character width calculations.
 *
 * @author Kenny Root
 */
class Relay private constructor(
    private val bridge: TerminalBridge,
    private val transport: AbsTransport,
    private val dispatchers: CoroutineDispatchers,
    encoding: String,
    private val streamRead: (suspend (ByteArray, Int, Int) -> Int)?,
    private val manageAutomation: Boolean,
    private val outputLock: Mutex,
) {

    private val running = AtomicBoolean()

    constructor(
        bridge: TerminalBridge,
        transport: AbsTransport,
        dispatchers: CoroutineDispatchers,
        encoding: String,
    ) : this(bridge, transport, dispatchers, encoding, null, true, Mutex())

    private val streamRelays = CopyOnWriteArrayList<Relay>()

    @Volatile
    private var currentEncoding = encoding

    private val decoder = RelayDecoder(
        encoding,
        onText = { if (bridge.transport === transport) bridge.onAutomationOutput(it) },
        onBytes = { bytes ->
            if (bridge.transport === transport) {
                bridge.onOutputReceived()
                bridge.terminalEmulator.writeInput(bytes, 0, bytes.size)
            }
        },
    )

    fun setCharset(encoding: String) {
        currentEncoding = encoding
        decoder.setCharset(encoding)
        streamRelays.forEach { it.setCharset(encoding) }
    }

    fun getCharset(): Charset = decoder.charset

    /**
     * Start relaying data from transport to terminal buffer.
     * This is a suspend function that runs on IO dispatcher.
     */
    suspend fun start(): Unit = withContext(dispatchers.io) {
        if (!running.compareAndSet(false, true)) return@withContext
        val streams = if (streamRead == null) transport.getOutputStreams() else null
        if (streams != null) {
            try {
                coroutineScope {
                    streams.forEach { stream ->
                        var packet: ByteArray? = null
                        var packetOffset = 0
                        val relay = Relay(
                            bridge,
                            transport,
                            dispatchers,
                            currentEncoding,
                            streamRead = { buffer, offset, length ->
                                while (packet == null || packetOffset == packet!!.size) {
                                    val received = stream.receiveCatching()
                                    received.exceptionOrNull()?.let { throw it }
                                    packet = received.getOrNull() ?: return@Relay -1
                                    packetOffset = 0
                                }
                                val currentPacket = checkNotNull(packet)
                                val count = minOf(length, currentPacket.size - packetOffset)
                                currentPacket.copyInto(buffer, offset, packetOffset, packetOffset + count)
                                packetOffset += count
                                count
                            },
                            manageAutomation = false,
                            outputLock = outputLock,
                        )
                        streamRelays.add(relay)
                        launch { relay.start() }
                    }
                }
                if (bridge.transport === transport) transport.onOutputComplete()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (bridge.transport === transport) transport.onOutputFailure(e)
                Timber.e(e, "Problem while handling SSH output streams")
            } finally {
                streamRelays.clear()
                if (bridge.transport === transport) bridge.cancelAutomation()
                running.set(false)
            }
            return@withContext
        }

        try {
            if (streamRead == null && transport is Telnet) {
                relayTelnet(transport)
                return@withContext
            }
            val bytes = ByteArray(BUFFER_SIZE)
            while (isActive) {
                val count = streamRead?.invoke(bytes, 0, bytes.size) ?: transport.read(bytes, 0, bytes.size)
                if (count == -1) {
                    outputLock.withLock { decoder.finish() }
                    if (manageAutomation && bridge.transport === transport) transport.onReadEof()
                    return@withContext
                }
                if (count < 0 || count > bytes.size) throw IOException("Invalid transport read: $count")
                outputLock.withLock { decoder.accept(bytes, length = count) }
            }
        } catch (e: IOException) {
            if (!manageAutomation) throw e
            Timber.e(e, "Problem while handling incoming data in relay")
            if (bridge.transport === transport) transport.onReadFailure(e)
        } catch (e: CoderMalfunctionError) {
            Timber.e(e, "Charset decoder failed while handling incoming data")
            if (bridge.transport === transport) bridge.dispatchDisconnect(DisconnectReason.IO_ERROR)
        } finally {
            if (manageAutomation && bridge.transport === transport) bridge.cancelAutomation()
            running.set(false)
        }
    }

    /** Consume each Telnet event at its wire position, without flattening it into byte reads. */
    private suspend fun relayTelnet(telnet: Telnet) {
        while (currentCoroutineContext().isActive) {
            when (val result = telnet.receive()) {
                is TelnetOutcome.Failure -> throw TelnetOperationException(result.error)

                is TelnetOutcome.Success -> when (val input = result.value) {
                    null -> {
                        outputLock.withLock { decoder.finish() }
                        if (bridge.transport === transport) transport.onReadEof()
                        return
                    }

                    is TelnetInput.Data -> outputLock.withLock { decoder.accept(input.bytes) }

                    is TelnetInput.EchoChanged -> telnet.onEchoChanged(input.localEcho)

                    is TelnetInput.CharsetAgreed -> if (!input.name.equals(decoder.charset.name(), ignoreCase = true)) {
                        throw IOException("Unexpected Telnet charset agreement: ${input.name}")
                    }

                    is TelnetInput.Diagnostic -> Timber.d("Telnet: %s", input.message)

                    TelnetInput.GoAhead, TelnetInput.EndOfRecord -> Unit
                }
            }
        }
    }

    companion object {
        private const val TAG = "CB.Relay"
        private const val BUFFER_SIZE = 4096
    }
}

internal fun ByteBuffer.advanceAfterRead(bytesRead: Int, requestedLength: Int): Boolean {
    if (bytesRead == -1) {
        return true
    }

    if (bytesRead < 0 || bytesRead > requestedLength) {
        throw IOException(
            "Transport read returned $bytesRead bytes for a $requestedLength byte request",
        )
    }

    // Advance position to reflect new data.
    position(position() + bytesRead)
    return false
}
