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

package org.connectbot.ui.screens.console

import org.connectbot.data.entity.Host
import org.connectbot.sshlib.ConnectionInfo

/** A snapshot of the selected connection for display in the console. */
data class ConnectionDetails(
    val host: Host,
    val status: ConnectionStatus,
    val localAddress: String?,
    val ssh: ConnectionInfo?,
    val portForwards: List<PortForwardDetails> = emptyList(),
)

enum class ConnectionStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

/** Runtime forwarding details, separate from the saved forwarding configuration. */
data class PortForwardDetails(
    val nickname: String,
    val type: String,
    val sourceAddress: String,
    val configuredPort: Int,
    val destinationAddress: String?,
    val destinationPort: Int,
    val boundPort: Int?,
    val isActive: Boolean,
)
