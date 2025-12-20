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
import timber.log.Timber
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host

/**
 * Factory for creating and managing transport instances.
 *
 * @author Kenny Root
 */
object TransportFactory {
    private const val TAG = "CB.TransportFactory"

    /**
     * Get a transport instance by protocol name.
     * @param protocol The protocol name (e.g., "ssh", "telnet", "local")
     * @return Transport instance or null if protocol is not recognized
     */
    fun getTransport(protocol: String?): AbsTransport? {
        return Transport.fromProtocol(protocol)?.createInstance()
    }

    /**
     * Parse a URI string for a given scheme.
     * @param scheme The URI scheme (protocol)
     * @param input The input string to parse
     * @return Parsed URI or null if parsing fails
     */
    fun getUri(scheme: String, input: String): Uri? {
        Timber.d("Attempting to discover URI for scheme=$scheme on input=$input")
        val transport = Transport.fromProtocol(scheme)
        if (transport is Transport.Local) {
            Timber.d("Got to the local parsing area")
        }
        return transport?.parseUri(input)
    }

    /**
     * Get all available transport protocol names.
     * @return Array of protocol names
     */
    fun getTransportNames(): Array<String> {
        return Transport.allTransports().map { it.protocolName }.toTypedArray()
    }

    /**
     * Check if two transports are of the same type.
     * @param a First transport
     * @param b Second transport
     * @return true if both transports are of the same class
     */
    fun isSameTransportType(a: AbsTransport?, b: AbsTransport?): Boolean {
        if (a == null || b == null) return false
        return a::class == b::class
    }

    /**
     * Check if a protocol supports port forwarding.
     * @param protocol The protocol name
     * @return true if the protocol supports port forwarding
     */
    fun canForwardPorts(protocol: String): Boolean {
        val transport = Transport.fromProtocol(protocol)
        // Only SSH supports port forwarding
        return transport is Transport.Ssh
    }

    /**
     * Get the format hint for a given protocol.
     * @param protocol The protocol name
     * @param context Android context for accessing resources
     * @return Format hint string
     */
    fun getFormatHint(protocol: String, context: Context): String {
        return Transport.fromProtocol(protocol)?.getFormatHint(context) ?: "???"
    }

    /**
     * Find a host in the repository that matches the given URI.
     * @param hostRepository Repository to search
     * @param uri URI to match
     * @return Matching host or null
     */
    suspend fun findHost(hostRepository: HostRepository, uri: Uri): Host? {
        val transport = getTransport(uri.scheme) ?: run {
            Timber.e("Unknown transport scheme: ${uri.scheme}")
            throw IllegalStateException("Unknown transport scheme: ${uri.scheme}")
        }

        val selection = mutableMapOf<String, String>()
        transport.getSelectionArgs(uri, selection)

        if (selection.isEmpty()) {
            Timber.e("Transport ${uri.scheme} failed to do something useful with URI=$uri")
            throw IllegalStateException("Failed to get needed selection arguments")
        }

        return hostRepository.findHost(selection)
    }
}
