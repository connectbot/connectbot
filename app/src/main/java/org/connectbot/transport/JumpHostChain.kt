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

package org.connectbot.transport

import androidx.annotation.StringRes
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.util.HostConstants
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

internal const val MAX_JUMP_HOSTS = 32

internal class JumpHostChainException(@StringRes val messageResId: Int) : IOException("Invalid jump host chain")

/** Resolve the whole chain before opening sockets, ordered from nearest to outermost hop. */
internal fun resolveJumpHostChain(target: Host, findHost: (UUID) -> Host?): List<Host> {
    val chain = mutableListOf<Host>()
    val visited = mutableSetOf(target.id)
    var nextId = target.jumpHostId
    while (nextId != null) {
        if (!visited.add(nextId)) throw JumpHostChainException(R.string.terminal_jump_cycle)
        if (chain.size >= MAX_JUMP_HOSTS) throw JumpHostChainException(R.string.terminal_jump_too_deep)
        val nextHost = findHost(nextId) ?: throw JumpHostChainException(R.string.terminal_jump_not_found)
        chain.add(nextHost)
        nextId = nextHost.jumpHostId
    }
    return chain
}

/**
 * Whether the host accepts a direct TCP connection, honoring its IP version preference.
 * Blocks for up to the host's fallback timeout per resolved address, so call it off the main thread.
 */
internal fun isDirectlyReachable(host: Host): Boolean {
    val timeoutMs = host.jumpHostFallbackTimeout
        .coerceIn(HostConstants.MIN_JUMP_HOST_FALLBACK_TIMEOUT, HostConstants.MAX_JUMP_HOST_FALLBACK_TIMEOUT) * 1000
    val hostname = host.hostname.trim().trim('[', ']')
    if (hostname.isEmpty()) return false
    val addresses = try {
        InetAddress.getAllByName(hostname).toList()
    } catch (e: IOException) {
        return false
    }.let { all ->
        if (HostConstants.isIpAddress(hostname)) {
            all
        } else {
            when (host.ipVersion) {
                HostConstants.IPVERSION_IPV4_ONLY -> all.filterIsInstance<Inet4Address>()
                HostConstants.IPVERSION_IPV6_ONLY -> all.filterIsInstance<Inet6Address>()
                else -> all
            }
        }
    }
    return addresses.any { address ->
        try {
            Socket().use { it.connect(InetSocketAddress(address, host.port), timeoutMs) }
            true
        } catch (e: IOException) {
            false
        }
    }
}
