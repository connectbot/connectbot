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

import android.content.Context
import android.net.Uri

/**
 * Sealed class representing the available transport types in ConnectBot.
 * This provides type-safe transport selection and eliminates string-based protocol matching.
 *
 * Each transport type is a singleton object that provides metadata about the transport
 * (protocol name, default port, format hints) and factory methods for creating instances.
 */
sealed class Transport {
    /**
     * The protocol identifier (e.g., "ssh")
     */
    abstract val protocolName: String

    /**
     * The default port for this transport protocol
     */
    abstract val defaultPort: Int

    /**
     * Whether this transport requires network connectivity
     */
    abstract val usesNetwork: Boolean

    /**
     * Get a human-readable format hint for connection strings
     */
    abstract fun getFormatHint(context: Context): String

    /**
     * Create a transport instance
     */
    abstract fun createInstance(): AbsTransport

    /**
     * Parse a URI string into a URI object for this transport
     */
    abstract fun parseUri(input: String): Uri?

    /**
     * SSH transport - Secure Shell protocol
     */
    object Ssh : Transport() {
        override val protocolName = "ssh"
        override val defaultPort = 22
        override val usesNetwork = true

        override fun getFormatHint(context: Context): String = SSH.getFormatHint(context)

        override fun createInstance(): AbsTransport = SSH()

        override fun parseUri(input: String): Uri? = SSH.getUri(input)
    }

    companion object {
        /**
         * Get a transport by its protocol name.
         * Returns null if the protocol is not recognized.
         */
        @JvmStatic
        fun fromProtocol(protocol: String?): Transport? = when (protocol) {
            "ssh" -> Ssh
            else -> null
        }

        /**
         * Get all available transports
         */
        @JvmStatic
        fun allTransports(): List<Transport> = listOf(Ssh)

        /**
         * Get transport from a URI
         */
        @JvmStatic
        fun fromUri(uri: Uri): Transport? = fromProtocol(uri.scheme)
    }
}
