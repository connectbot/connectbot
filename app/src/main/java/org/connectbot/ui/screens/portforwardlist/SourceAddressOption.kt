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

package org.connectbot.ui.screens.portforwardlist

import androidx.annotation.StringRes
import org.connectbot.R

enum class SourceAddressOption(val sshValue: String, @StringRes val labelRes: Int) {
    LOCALHOST("localhost", R.string.portforward_source_addr_localhost),
    LOCALHOST_IPV4("127.0.0.1", R.string.portforward_source_addr_localhost_ipv4),
    LOCALHOST_IPV6("::1", R.string.portforward_source_addr_localhost_ipv6),
    ALL("", R.string.portforward_source_addr_all),
    ALL_IPV4("0.0.0.0", R.string.portforward_source_addr_all_ipv4),
    ALL_IPV6("::", R.string.portforward_source_addr_all_ipv6),
    SPECIFIC("", R.string.portforward_source_addr_specific),
    ;

    companion object {
        fun fromSshValue(value: String?): SourceAddressOption = when (value) {
            "localhost" -> LOCALHOST
            "127.0.0.1" -> LOCALHOST_IPV4
            "::1" -> LOCALHOST_IPV6
            "" -> ALL
            "0.0.0.0" -> ALL_IPV4
            "::" -> ALL_IPV6
            null -> LOCALHOST
            else -> SPECIFIC
        }
    }
}

internal object PortForwardEditorTestTags {
    const val NICKNAME_FIELD = "port_forward_nickname_field"
    const val SOURCE_PORT_FIELD = "port_forward_source_port_field"
    const val DESTINATION_FIELD = "port_forward_destination_field"
    const val SPECIFIC_SOURCE_ADDRESS_FIELD = "port_forward_specific_source_address_field"
}
