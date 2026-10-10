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

import com.trilead.ssh2.Connection
import com.trilead.ssh2.ConnectionInfo
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException

class SSHJumpHostTest {
    private val manager = mock(TerminalManager::class.java)
    private val bridge = mock(TerminalBridge::class.java)
    private val repository = mock(HostRepository::class.java)

    @Test
    fun selfReferencingJumpHostDisconnectsWithoutOverflow() {
        connectInvalidChain(Host(id = 2, nickname = "jump", jumpHostId = 2))
    }

    @Test
    fun cyclicJumpHostsDisconnectWithoutOverflow() {
        connectInvalidChain(
            Host(id = 2, nickname = "first", jumpHostId = 3),
            Host(id = 3, nickname = "second", jumpHostId = 2),
        )
    }

    @Test
    fun jumpChainReturningToTargetDisconnectsWithoutOverflow() {
        connectInvalidChain(Host(id = 2, nickname = "jump", jumpHostId = 1))
    }

    @Test
    fun validChainConnectsAndAuthenticatesOutermostFirst() {
        val outer = Host(id = 3, nickname = "outer", hostname = "outer.example", username = "alice")
        val inner = Host(id = 2, nickname = "inner", hostname = "inner.example", username = "alice", jumpHostId = 3)
        val target = Host(id = 1, nickname = "target", hostname = "target.example", username = "alice", jumpHostId = 2, wantSession = false)
        stubHosts(inner, outer)
        val hostnames = mutableListOf<String>()
        mockConstruction(Connection::class.java) { connection, context ->
            hostnames.add(context.arguments()[0] as String)
            `when`(connection.connect(any(), any())).thenReturn(ConnectionInfo())
            `when`(connection.authenticateWithNone("alice")).thenReturn(true)
        }.use { connections ->
            val ssh = SSH(target, bridge, manager)
            ssh.connect()
            val opened = connections.constructed()
            assertEquals(listOf("outer.example", "inner.example", "target.example"), hostnames)
            assertEquals(3, opened.size)
            val order = inOrder(*opened.toTypedArray())
            order.verify(opened[0]).connect(any(), any())
            order.verify(opened[0]).authenticateWithNone("alice")
            order.verify(opened[1]).setProxyData(any(JumpHostProxyData::class.java))
            order.verify(opened[1]).connect(any(), any())
            order.verify(opened[1]).authenticateWithNone("alice")
            order.verify(opened[2]).setProxyData(any(JumpHostProxyData::class.java))
            order.verify(opened[2]).connect(any(), any())
            order.verify(opened[2]).authenticateWithNone("alice")
            val proxy = ArgumentCaptor.forClass(JumpHostProxyData::class.java)
            verify(opened[1]).setProxyData(proxy.capture())
            assertSame(opened[0], proxy.value.jumpConnection)
            verify(opened[2]).setProxyData(proxy.capture())
            assertSame(opened[1], proxy.value.jumpConnection)
            verify(bridge).onConnected()
            ssh.close()
            opened.forEach { verify(it).close() }
        }
    }

    @Test
    fun failedInnerHopClosesPreviouslyConnectedOuterHop() {
        stubHosts(
            Host(id = 2, nickname = "inner", username = "alice", jumpHostId = 3),
            Host(id = 3, nickname = "outer", username = "alice"),
        )
        mockConstruction(Connection::class.java) { connection, context ->
            if (context.count == 2) {
                `when`(connection.connect(any(), any())).thenThrow(IOException("Cannot reach inner hop"))
            } else {
                `when`(connection.connect(any(), any())).thenReturn(ConnectionInfo())
                `when`(connection.authenticateWithNone(anyString())).thenReturn(true)
            }
        }.use { connections ->
            SSH(Host(id = 1, jumpHostId = 2), bridge, manager).connect()
            assertEquals(2, connections.constructed().size)
            connections.constructed().forEach { verify(it).close() }
            verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
        }
    }

    private fun stubHosts(vararg hosts: Host) {
        `when`(manager.hostRepository).thenReturn(repository)
        hosts.forEach { `when`(repository.findHostByIdBlocking(it.id)).thenReturn(it) }
    }

    private fun connectInvalidChain(vararg jumps: Host) {
        val target = Host(id = 1, nickname = "target", jumpHostId = 2)
        stubHosts(*(jumps.toList() + target).toTypedArray())
        mockConstruction(Connection::class.java).use { connections ->
            SSH(target, bridge, manager).connect()
            assertTrue(connections.constructed().isEmpty())
        }
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }
}
