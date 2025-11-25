/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

import com.trilead.ssh2.LocalStreamForwarder
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException

/**
 * A Socket implementation that wraps a LocalStreamForwarder.
 * This allows SSH tunneled connections to be used with APIs that expect a Socket,
 * such as the ProxyData interface for SSH ProxyJump support.
 *
 * @param forwarder The LocalStreamForwarder providing the tunneled connection
 * @param remoteHost The remote host this socket is connected to (for informational purposes)
 * @param remotePort The remote port this socket is connected to (for informational purposes)
 */
class StreamSocket(
    private val forwarder: LocalStreamForwarder,
    private val remoteHost: String,
    private val remotePort: Int
) : Socket() {

    private val inputStreamInternal: InputStream = forwarder.inputStream
    private val outputStreamInternal: OutputStream = forwarder.outputStream

    @Volatile
    private var closed = false

    override fun getInputStream(): InputStream {
        if (closed) {
            throw SocketException("Socket is closed")
        }
        return inputStreamInternal
    }

    override fun getOutputStream(): OutputStream {
        if (closed) {
            throw SocketException("Socket is closed")
        }
        return outputStreamInternal
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            forwarder.close()
        }
    }

    override fun isClosed(): Boolean = closed

    override fun isConnected(): Boolean = !closed

    override fun isInputShutdown(): Boolean = closed

    override fun isOutputShutdown(): Boolean = closed

    override fun getInetAddress(): InetAddress? {
        return try {
            InetAddress.getByName(remoteHost)
        } catch (e: Exception) {
            null
        }
    }

    override fun getPort(): Int = remotePort

    override fun getRemoteSocketAddress(): SocketAddress? = null // Not applicable for tunneled connections

    override fun getLocalAddress(): InetAddress? = null // Not applicable for tunneled connections

    override fun getLocalPort(): Int = 0 // Not applicable for tunneled connections

    override fun getLocalSocketAddress(): SocketAddress? = null // Not applicable for tunneled connections

    // The following methods are not supported for stream-based sockets
    // but we provide reasonable defaults

    override fun setSoTimeout(timeout: Int) {
        // Timeout is not directly supported on the underlying stream
    }

    override fun getSoTimeout(): Int = 0

    override fun setTcpNoDelay(on: Boolean) {
        // Not applicable for tunneled connections
    }

    override fun getTcpNoDelay(): Boolean = false

    override fun setKeepAlive(on: Boolean) {
        // Not applicable for tunneled connections
    }

    override fun getKeepAlive(): Boolean = false

    override fun setSendBufferSize(size: Int) {
        // Not applicable for tunneled connections
    }

    override fun getSendBufferSize(): Int = 0

    override fun setReceiveBufferSize(size: Int) {
        // Not applicable for tunneled connections
    }

    override fun getReceiveBufferSize(): Int = 0
}
