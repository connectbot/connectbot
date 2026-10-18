/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007-2026 Kenny Root
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
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TelnetInputDiscipline
import org.connectbot.service.TelnetKeyboardInput
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.service.terminalCharset
import org.connectbot.telnetlib.TelnetConfig
import org.connectbot.telnetlib.TelnetFailure
import org.connectbot.telnetlib.TelnetFailureCode
import org.connectbot.telnetlib.TelnetInput
import org.connectbot.telnetlib.TelnetOutcome
import org.connectbot.telnetlib.TelnetSession
import org.connectbot.telnetlib.TelnetStatus
import org.connectbot.telnetlib.ktor.connectTelnet
import org.connectbot.util.HostConstants
import timber.log.Timber
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.regex.Pattern

/** Telnet metadata and suspend adapter. Exactly one Relay owns [receive]. */
class Telnet : AbsTransport {
    @Volatile private var session: TelnetSession? = null

    @Volatile private var sessionScope: CoroutineScope? = null

    @Volatile private var closed = false

    @Volatile private var connected = false

    @Volatile private var dimensions = 0 to 0
    private val sessionMutex = Mutex()
    private var inputReader: TelnetInputReader? = null
    private var inputDiscipline: TelnetInputDiscipline? = null
    private var keyboardInput: TelnetKeyboardInput? = null

    internal var connector: suspend (CoroutineScope, String, Int, TelnetConfig, CoroutineDispatchers) -> TelnetOutcome<TelnetSession> =
        { scope, hostname, port, config, dispatchers ->
            connectTelnet(scope, hostname, port, config, dispatchers.io)
        }

    internal var resolveAddresses: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }

    constructor()

    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) : super(host, bridge, manager)

    override suspend fun connect(scope: CoroutineScope, dispatchers: CoroutineDispatchers, encoding: String) {
        val currentHost = requireNotNull(host)
        val charset = terminalCharset(encoding)
        val echo = TelnetInputDiscipline(currentHost.telnetEchoMode, encoding) { bytes -> bridge?.displayLocalEcho(this, bytes) }
        inputDiscipline = echo
        keyboardInput = TelnetKeyboardInput(encoding)
        inputReader = TelnetInputReader(charset, this::receive, echo::negotiatedEcho) { Timber.d("Telnet: %s", it) }
        val owner = CoroutineScope(SupervisorJob(scope.coroutineContext[Job]) + dispatchers.io)
        sessionScope = owner
        if (closed) owner.cancel()
        try {
            owner.ensureActive()
            val addresses = withContext(dispatchers.io) {
                resolveAddresses(currentHost.hostname).filter { address ->
                    HostConstants.isIpAddress(currentHost.hostname) || when (currentHost.ipVersion) {
                        HostConstants.IPVERSION_IPV4_ONLY -> address is Inet4Address
                        HostConstants.IPVERSION_IPV6_ONLY -> address is Inet6Address
                        else -> true
                    }
                }
            }
            val initialDimensions = dimensions
            val config = telnetConfig(currentHost, getEmulation(), initialDimensions.first, initialDimensions.second)
            var failure = TelnetFailure(TelnetFailureCode.TRANSPORT_ERROR, "No eligible address for ${currentHost.hostname}")
            for (address in addresses) {
                owner.ensureActive()
                when (val result = connector(owner, address.hostAddress!!, currentHost.port, config, dispatchers)) {
                    is TelnetOutcome.Success -> {
                        owner.ensureActive()
                        sessionMutex.withLock {
                            val latestDimensions = dimensions
                            if (latestDimensions != initialDimensions) {
                                result.value.resize(latestDimensions.first, latestDimensions.second).checked()
                            }
                            owner.ensureActive()
                            session = result.value
                            connected = true
                        }
                        owner.ensureActive()
                        bridge?.onTransportConnected(this)
                        return
                    }

                    is TelnetOutcome.Failure -> failure = result.error
                }
            }
            throw TelnetOperationException(failure)
        } catch (e: Exception) {
            connected = false
            owner.cancel()
            throw e
        }
    }

    suspend fun receive(): TelnetOutcome<TelnetInput?> = session?.receive()
        ?: if (closed) {
            TelnetOutcome.Failure(TelnetFailure(TelnetFailureCode.CLOSED, "Telnet session closed"))
        } else {
            error("Telnet session has not connected")
        }

    override suspend fun write(buffer: ByteArray) {
        val current = operationSession() ?: return
        current.write(buffer).checked()
        inputDiscipline?.echo(buffer)
    }

    override suspend fun writeKeyboardInput(buffer: ByteArray) {
        if (operationSession() == null) return
        requireNotNull(keyboardInput).write(buffer, this::write, this::carriageReturn)
    }

    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int = requireNotNull(inputReader).read(buffer, offset, length)

    override fun userMessageForError(error: IOException): Int? = when ((error as? TelnetOperationException)?.failure?.code) {
        TelnetFailureCode.NON_ASCII_DATA -> R.string.telnet_ascii_input_rejected
        TelnetFailureCode.UNSUPPORTED_OPERATION -> R.string.telnet_input_rejected
        else -> null
    }

    override fun onConnectionFailure(error: Exception) = disconnect(DisconnectReason.IO_ERROR)

    override fun onReadFailure(error: IOException) = disconnect(DisconnectReason.IO_ERROR)

    override fun onReadEof() = disconnect(DisconnectReason.REMOTE_EOF)

    private fun disconnect(reason: DisconnectReason) {
        if (bridge?.transport === this) bridge?.dispatchDisconnect(reason)
    }

    override suspend fun carriageReturn() {
        val current = operationSession() ?: return
        current.carriageReturn().checked()
        inputDiscipline?.carriageReturn()
    }

    override suspend fun flush() {
        operationSession()?.flush()?.checked()
    }

    override fun setInitialDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        dimensions = columns to rows
    }

    override suspend fun resize(columns: Int, rows: Int, width: Int, height: Int) {
        setInitialDimensions(columns, rows, width, height)
        sessionMutex.withLock {
            val latestDimensions = dimensions
            operationSession()?.resize(latestDimensions.first, latestDimensions.second)?.checked()
        }
    }

    override suspend fun close() {
        closed = true
        connected = false
        try {
            when (val result = session?.close()) {
                is TelnetOutcome.Failure -> Timber.d("Telnet close: %s: %s", result.error.code, result.error.message)
                else -> Unit
            }
        } finally {
            session = null
            sessionScope?.cancel()
        }
    }

    private fun operationSession(): TelnetSession? {
        if (closed) throw TelnetOperationException(TelnetFailure(TelnetFailureCode.CLOSED, "Telnet session closed"))
        return session
    }

    private fun TelnetOutcome<Unit>.checked() {
        if (this is TelnetOutcome.Failure) {
            if (error.isTerminal() && !closed && session?.state?.value?.status != TelnetStatus.PEER_EOF) {
                if (bridge?.transport === this@Telnet) bridge?.dispatchDisconnect(DisconnectReason.IO_ERROR)
            }
            throw TelnetOperationException(error)
        }
    }

    override fun getDefaultPort(): Int = DEFAULT_PORT
    override fun isConnected(): Boolean = connected

    // Retain the session until Relay has drained preceding input, even after the socket reaches EOF.
    override fun isSessionOpen(): Boolean = connected

    @SuppressLint("DefaultLocale")
    override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String = if (port == DEFAULT_PORT) {
        String.format("%s", hostname)
    } else {
        String.format("%s:%d", hostname, port)
    }

    override fun createHost(uri: Uri): Host {
        var port = uri.port
        if (port < 0 || port > 65535) {
            port = DEFAULT_PORT
        }

        val hostname = uri.host ?: ""

        var nickname = uri.fragment
        if (nickname.isNullOrEmpty()) {
            nickname = getDefaultNickname("", hostname, port)
        }

        return Host.createTelnetHost(nickname, hostname, port)
    }

    override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) {
        selection["protocol"] = PROTOCOL
        selection["nickname"] = uri.fragment ?: ""
        selection["hostname"] = uri.host ?: ""

        var port = uri.port
        if (port < 0 || port > 65535) {
            port = DEFAULT_PORT
        }
        selection["port"] = port.toString()
    }

    override fun usesNetwork(): Boolean = true

    override fun getLocalIpAddress(): String? = session?.getLocalSocketAddress()?.address?.hostAddress

    companion object {
        private const val PROTOCOL = "telnet"
        private const val DEFAULT_PORT = 23

        private val hostmask: Pattern = Pattern.compile(
            "^((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?\$",
            Pattern.CASE_INSENSITIVE,
        )

        @JvmStatic
        fun getProtocolName(): String = PROTOCOL

        @JvmStatic
        fun getUri(input: String): Uri? {
            val matcher = hostmask.matcher(input)

            if (!matcher.matches()) {
                return null
            }

            val sb = StringBuilder()

            sb.append(PROTOCOL)
                .append("://")
                .append(matcher.group(1))

            val portString = matcher.group(2)
            var port = DEFAULT_PORT
            if (portString != null) {
                try {
                    port = portString.toInt()
                    if (port < 1 || port > 65535) {
                        port = DEFAULT_PORT
                    }
                } catch (nfe: NumberFormatException) {
                    // Keep the default port
                }
            }

            if (port != DEFAULT_PORT) {
                sb.append(':')
                sb.append(port)
            }

            sb.append("/#")
                .append(Uri.encode(input))

            return sb.toString().toUri()
        }

        @JvmStatic
        fun getFormatHint(context: Context): String = String.format(
            "%s:%s",
            context.getString(R.string.format_hostname),
            context.getString(R.string.format_port),
        )
    }
}
