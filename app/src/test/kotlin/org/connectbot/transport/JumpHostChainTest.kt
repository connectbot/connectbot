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

import org.connectbot.R
import org.connectbot.data.entity.Host
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID

class JumpHostChainTest {
    private val target = Host(id = UUID(0, 1), jumpHostId = UUID(0, 2))

    @Test
    fun resolvesNearestToOutermost() {
        val outer = Host(id = UUID(0, 3))
        val inner = Host(id = UUID(0, 2), jumpHostId = UUID(0, 3))
        val hosts = listOf(inner, outer).associateBy { it.id }
        assertEquals(listOf(inner, outer), resolveJumpHostChain(target, hosts::get))
    }

    @Test
    fun missingNestedHostReportsMissingHost() {
        val error = assertThrows(JumpHostChainException::class.java) {
            resolveJumpHostChain(target) { if (it == UUID(0, 2)) Host(id = UUID(0, 2), jumpHostId = UUID(0, 3)) else null }
        }
        assertEquals(R.string.terminal_jump_not_found, error.messageResId)
    }

    @Test
    fun acceptsChainAtLimit() {
        val hosts = (2..MAX_JUMP_HOSTS + 1).map { id ->
            Host(id = UUID(0, id.toLong()), jumpHostId = if (id <= MAX_JUMP_HOSTS) UUID(0, id.toLong() + 1) else null)
        }.associateBy { it.id }
        assertEquals(MAX_JUMP_HOSTS, resolveJumpHostChain(target, hosts::get).size)
    }

    @Test
    fun rejectsChainOverLimit() {
        val error = assertThrows(JumpHostChainException::class.java) {
            resolveJumpHostChain(target) { Host(id = it, jumpHostId = UUID(0, it.leastSignificantBits + 1)) }
        }
        assertEquals(R.string.terminal_jump_too_deep, error.messageResId)
    }

    @Test
    fun directConnectionDoesNotLookUpHosts() {
        assertEquals(emptyList<Host>(), resolveJumpHostChain(Host()) { error("Unexpected lookup") })
    }

    @Test
    fun listeningPortIsDirectlyReachable() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            assertTrue(isDirectlyReachable(Host(hostname = "127.0.0.1", port = server.localPort, jumpHostFallbackTimeout = 1)))
        }
    }

    @Test
    fun closedPortIsNotDirectlyReachable() {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        assertFalse(isDirectlyReachable(Host(hostname = "127.0.0.1", port = port, jumpHostFallbackTimeout = 1)))
    }

    @Test
    fun blankHostnameIsNotDirectlyReachable() {
        assertFalse(isDirectlyReachable(Host(hostname = " ")))
    }
}
