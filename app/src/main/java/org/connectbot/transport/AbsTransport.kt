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

import android.content.Context
import android.net.Uri
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import java.io.IOException

/**
 * @author Kenny Root
 */
abstract class AbsTransport {
    @JvmField
    var host: Host? = null

    @JvmField
    var bridge: TerminalBridge? = null

    @JvmField
    var manager: TerminalManager? = null

    private var _emulation: String? = null

    constructor()

    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) {
        this.host = host
        this.bridge = bridge
        this.manager = manager
    }

    /**
     * Causes transport to connect to the target host. After connecting but before a
     * session is started, must call back to [TerminalBridge.onConnected].
     * After that call a session may be opened.
     */
    abstract suspend fun connect()

    /**
     * Reads from the transport. Transport must support reading into a the byte array
     * `buffer` at the start of `offset` and a maximum of
     * `length` bytes. If the remote host disconnects, throw an
     * [IOException].
     * @param buffer byte buffer to store read bytes into
     * @param offset where to start writing in the buffer
     * @param length maximum number of bytes to read
     * @return number of bytes read
     * @throws IOException when remote host disconnects
     */
    @Throws(IOException::class)
    abstract suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int

    /**
     * Writes to the transport. If the host is not yet connected, simply return without
     * doing anything. An [IOException] should be thrown if there is an error after
     * connection.
     * @param buffer bytes to write to transport
     * @throws IOException when there is a problem writing after connection
     */
    @Throws(IOException::class)
    abstract suspend fun write(buffer: ByteArray)

    /**
     * Writes to the transport. See [write] for behavior details.
     * @param c character to write to the transport
     * @throws IOException when there is a problem writing after connection
     */
    @Throws(IOException::class)
    abstract suspend fun write(c: Int)

    /**
     * Flushes the write commands to the transport.
     * @throws IOException when there is a problem writing after connection
     */
    @Throws(IOException::class)
    abstract suspend fun flush()

    /**
     * Closes the connection to the terminal. Note that the resulting failure to read
     * should call [TerminalBridge.dispatchDisconnect].
     */
    abstract suspend fun close()

    /**
     * Tells the transport what dimensions the display is currently
     * @param columns columns of text
     * @param rows rows of text
     * @param width width in pixels
     * @param height height in pixels
     */
    abstract suspend fun setDimensions(columns: Int, rows: Int, width: Int, height: Int)

    open fun setOptions(options: Map<String, String>) {
        // do nothing
    }

    open fun getOptions(): Map<String, String>? = null

    open fun setCompression(compression: Boolean) {
        // do nothing
    }

    open fun setUseAuthAgent(useAuthAgent: String) {
        // do nothing
    }

    open fun setEmulation(emulation: String?) {
        this._emulation = emulation
    }

    open fun getEmulation(): String? = _emulation

    open fun setHost(host: Host?) {
        this.host = host
    }

    open fun setBridge(bridge: TerminalBridge?) {
        this.bridge = bridge
    }

    open fun setManager(manager: TerminalManager?) {
        this.manager = manager
    }

    /**
     * Whether or not this transport type can forward ports.
     * @return true on ability to forward ports
     */
    open fun canForwardPorts(): Boolean = false

    /**
     * Adds the [PortForward] to the list.
     * @param portForward the port forward bean to add
     * @return true on successful addition
     */
    open fun addPortForward(portForward: PortForward): Boolean = false

    /**
     * Enables a port forward member. After calling this method, the port forward should
     * be operational iff it could be enabled by the transport.
     * @param portForward member of our current port forwards list to enable
     * @return true on successful port forward setup
     */
    open suspend fun enablePortForward(portForward: PortForward): Boolean = false

    /**
     * Disables a port forward member. After calling this method, the port forward should
     * be non-functioning iff it could be disabled by the transport.
     * @param portForward member of our current port forwards list to enable
     * @return true on successful port forward tear-down
     */
    open suspend fun disablePortForward(portForward: PortForward): Boolean = false

    /**
     * Removes the [PortForward] from the available port forwards.
     * @param portForward the port forward bean to remove
     * @return true on successful removal
     */
    open suspend fun removePortForward(portForward: PortForward): Boolean = false

    /**
     * Gets a list of the [PortForward] currently used by this transport.
     * @return the list of port forwards
     */
    open fun getPortForwards(): List<PortForward>? = null

    abstract fun isConnected(): Boolean
    abstract fun isSessionOpen(): Boolean

    /**
     * @return int default port for protocol
     */
    abstract fun getDefaultPort(): Int

    /**
     * @param username
     * @param hostname
     * @param port
     * @return
     */
    abstract fun getDefaultNickname(username: String?, hostname: String?, port: Int): String

    /**
     * @param uri
     * @param selection
     */
    abstract fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>)

    /**
     * @param uri
     * @return
     */
    abstract fun createHost(uri: Uri): Host

    /**
     * @return
     */
    abstract fun usesNetwork(): Boolean
}
