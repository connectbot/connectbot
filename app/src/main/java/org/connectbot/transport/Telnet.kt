/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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
import timber.log.Timber
import de.mud.telnet.TelnetProtocolHandler
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.regex.Pattern

/**
 * Telnet transport implementation.
 * Original idea from the JTA telnet package (de.mud.telnet)
 *
 * @author Kenny Root
 */
class Telnet : AbsTransport {

    private val handler: TelnetProtocolHandler
    private var socket: Socket? = null

    private var `is`: InputStream? = null
    private var os: OutputStream? = null
    private var width: Int = 0
    private var height: Int = 0

    private var connected = false

    constructor() {
        handler = object : TelnetProtocolHandler() {
            /** get the current terminal type */
            override fun getTerminalType(): String? = getEmulation()

            /** get the current window size */
            override fun getWindowSize(): IntArray = intArrayOf(width, height)

            /** notify about local echo */
            override fun setLocalEcho(echo: Boolean) {
                /* EMPTY */
            }

            /** write data to our back end */
            @Throws(IOException::class)
            override fun write(b: ByteArray) {
                os?.write(b)
            }

            /** sent on IAC EOR (prompt terminator for remote access systems). */
            override fun notifyEndOfRecord() {
            }

            override fun getCharsetName(): String {
                val charset = bridge?.charset
                return charset?.name() ?: ""
            }
        }
    }

    /**
     * @param host
     * @param bridge
     * @param manager
     */
    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) : super(host, bridge, manager) {
        handler = object : TelnetProtocolHandler() {
            override fun getTerminalType(): String? = getEmulation()
            override fun getWindowSize(): IntArray = intArrayOf(width, height)
            override fun setLocalEcho(echo: Boolean) {}
            @Throws(IOException::class)
            override fun write(b: ByteArray) {
                os?.write(b)
            }
            override fun notifyEndOfRecord() {}
            override fun getCharsetName(): String {
                val charset = this@Telnet.bridge?.charset
                return charset?.name() ?: ""
            }
        }
    }

    override fun connect() {
        try {
            socket = Socket()

            val currentHost = host ?: return
            tryAllAddresses(socket!!, currentHost.hostname, currentHost.port)

            connected = true

            `is` = socket?.getInputStream()
            os = socket?.getOutputStream()

            bridge?.onConnected()
        } catch (e: UnknownHostException) {
            Timber.d(e, "IO Exception connecting to host")
            throw e
        } catch (e: IOException) {
            Timber.d(e, "IO Exception connecting to host")
            throw e
        }
    }

    override fun close() {
        connected = false
        socket?.let {
            try {
                it.close()
                socket = null
            } catch (e: IOException) {
                Timber.d(e, "Error closing telnet socket.")
            }
        }
    }

    @Throws(IOException::class)
    override fun flush() {
        os?.flush()
    }

    override fun getDefaultPort(): Int = DEFAULT_PORT

    override fun isConnected(): Boolean = connected

    override fun isSessionOpen(): Boolean = connected

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        /* process all already read bytes */
        var n: Int

        do {
            n = handler.negotiate(buffer, offset)
            if (n > 0)
                return n
        } while (n == 0)

        while (n <= 0) {
            do {
                n = handler.negotiate(buffer, offset)
                if (n > 0)
                    return n
            } while (n == 0)
            n = `is`!!.read(buffer, offset, length)
            if (n < 0) {
                bridge?.dispatchDisconnect(false)
                throw IOException("Remote end closed connection.")
            }

            handler.inputfeed(buffer, offset, n)
            n = handler.negotiate(buffer, offset)
        }
        return n
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray) {
        try {
            os?.write(buffer)
        } catch (e: SocketException) {
            bridge?.dispatchDisconnect(false)
        }
    }

    @Throws(IOException::class)
    override fun write(c: Int) {
        try {
            os?.write(c)
        } catch (e: SocketException) {
            bridge?.dispatchDisconnect(false)
        }
    }

    override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        try {
            handler.setWindowSize(columns, rows)
        } catch (e: IOException) {
            Timber.e(e, "Couldn't resize remote terminal")
        }
    }

    @SuppressLint("DefaultLocale")
    override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String {
        return if (port == DEFAULT_PORT) {
            String.format("%s", hostname)
        } else {
            String.format("%s:%d", hostname, port)
        }
    }

    override fun createHost(uri: Uri): Host {
        var port = uri.port
        if (port < 0 || port > 65535)
            port = DEFAULT_PORT

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
        if (port < 0 || port > 65535)
            port = DEFAULT_PORT
        selection["port"] = port.toString()
    }

    override fun usesNetwork(): Boolean = true

    companion object {
        private const val TAG = "CB.Telnet"
        private const val PROTOCOL = "telnet"
        private const val DEFAULT_PORT = 23

        private val hostmask: Pattern = Pattern.compile(
            "^((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?\$",
            Pattern.CASE_INSENSITIVE
        )

        @JvmStatic
        fun getProtocolName(): String = PROTOCOL

        @JvmStatic
        private fun tryAllAddresses(sock: Socket, host: String, port: Int) {
            val addresses = InetAddress.getAllByName(host)
            for (addr in addresses) {
                try {
                    sock.connect(InetSocketAddress(addr, port))
                    return
                } catch (ignored: SocketTimeoutException) {
                }
            }
            throw SocketTimeoutException("Could not connect; socket timed out")
        }

        @JvmStatic
        fun getUri(input: String): Uri? {
            val matcher = hostmask.matcher(input)

            if (!matcher.matches())
                return null

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

            return Uri.parse(sb.toString())
        }

        @JvmStatic
        fun getFormatHint(context: Context): String {
            return String.format(
                "%s:%s",
                context.getString(R.string.format_hostname),
                context.getString(R.string.format_port)
            )
        }
    }
}
