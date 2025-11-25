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

import com.trilead.ssh2.Connection
import com.trilead.ssh2.ProxyData
import java.net.Socket

/**
 * ProxyData implementation that tunnels connections through an SSH jump host.
 * This enables SSH ProxyJump functionality by creating direct-tcpip channels
 * through an established SSH connection.
 *
 * Usage:
 * 1. Establish and authenticate an SSH connection to the jump host
 * 2. Create JumpHostProxyData with that connection
 * 3. Set as ProxyData on the target host's Connection before connecting
 *
 * @param jumpConnection An established and authenticated SSH connection to the jump host.
 *                       This connection must remain open for the duration of the tunneled connection.
 */
class JumpHostProxyData(val jumpConnection: Connection) : ProxyData {
    init {
        requireNotNull(jumpConnection) { "Jump host connection cannot be null" }
    }

    /**
     * Opens a tunneled connection to the target host through the jump host.
     *
     * @param hostname The target hostname to connect to
     * @param port The target port to connect to
     * @param connectTimeout Connection timeout in milliseconds (not used for tunneled connections)
     * @return A Socket wrapping the SSH tunnel to the target host
     */
    override fun openConnection(hostname: String, port: Int, connectTimeout: Int): Socket {
        // Create a direct-tcpip channel through the jump host to the target
        val forwarder = jumpConnection.createLocalStreamForwarder(hostname, port)

        // Wrap the forwarder in a Socket for use by the SSH library
        return StreamSocket(forwarder, hostname, port)
    }
}
