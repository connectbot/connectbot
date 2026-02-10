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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.transport.TransportFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.IOException

class SSHJumpHostTest {
    private val fallbackHandler = mock(AuthHandler::class.java)
    private val manager = mock(TerminalManager::class.java)
    private val bridge = mock(TerminalBridge::class.java)
    private val repository = mock(HostRepository::class.java)

    @Test
    fun selfReferencingJumpHostDisconnectsWithoutOverflow() = runTest {
        connectInvalidChain(Host(id = 2, nickname = "jump", jumpHostId = 2))
    }

    @Test
    fun cyclicJumpHostsDisconnectWithoutOverflow() = runTest {
        connectInvalidChain(
            Host(id = 2, nickname = "first", jumpHostId = 3),
            Host(id = 3, nickname = "second", jumpHostId = 2),
        )
    }

    @Test
    fun jumpChainReturningToTargetDisconnectsWithoutOverflow() = runTest {
        connectInvalidChain(Host(id = 2, nickname = "jump", jumpHostId = 1))
    }

    @Test
    fun validChainConnectsAndAuthenticatesOutermostFirst() = runTest {
        val outer = Host(id = 3, nickname = "outer", hostname = "outer.example", username = "alice")
        val inner = Host(id = 2, nickname = "inner", hostname = "inner.example", username = "alice", jumpHostId = 3)
        val target = Host(id = 1, nickname = "target", hostname = "target.example", username = "alice", jumpHostId = 2, wantSession = false)
        stubHosts(inner, outer)
        val tunnels = mutableListOf<TransportFactory>()
        mockConstruction(SshClient::class.java) { client, _ ->
            runBlocking {
                `when`(client.connect()).thenReturn(ConnectResult.Success)
                `when`(client.authenticate(anyString() ?: "", any(AuthHandler::class.java) ?: fallbackHandler))
                    .thenReturn(AuthResult.Success)
                val tunnel = mock(TransportFactory::class.java)
                tunnels.add(tunnel)
                `when`(client.openDirectTcpipTransport(anyString() ?: "", org.mockito.ArgumentMatchers.anyInt(), anyString() ?: "", org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(tunnel)
                `when`(client.disconnectedFlow).thenReturn(MutableSharedFlow())
            }
        }.use { clients ->
            val ssh = SSH(target, bridge, manager)
            ssh.connect()
            val opened = clients.constructed()
            assertEquals(3, opened.size)
            val order = inOrder(*opened.toTypedArray())
            order.verify(opened[0]).connect()
            order.verify(opened[0]).authenticate(anyString() ?: "", any(AuthHandler::class.java) ?: fallbackHandler)
            order.verify(opened[0]).openDirectTcpipTransport(inner.hostname, inner.port)
            order.verify(opened[1]).connect()
            order.verify(opened[1]).authenticate(anyString() ?: "", any(AuthHandler::class.java) ?: fallbackHandler)
            order.verify(opened[1]).openDirectTcpipTransport(target.hostname, target.port)
            order.verify(opened[2]).connect()
            order.verify(opened[2]).authenticate(anyString() ?: "", any(AuthHandler::class.java) ?: fallbackHandler)
            verify(bridge).onConnected()
            ssh.close()
            opened.forEach { verify(it).disconnect() }
        }
    }

    @Test
    fun failedInnerHopClosesPreviouslyConnectedOuterHop() = runTest {
        stubHosts(
            Host(id = 2, nickname = "inner", hostname = "inner.example", username = "alice", jumpHostId = 3),
            Host(id = 3, nickname = "outer", hostname = "outer.example", username = "alice"),
        )
        mockConstruction(SshClient::class.java) { client, context ->
            val tunnel = mock(TransportFactory::class.java)
            runBlocking {
                `when`(client.connect()).thenReturn(
                    if (context.count == 2) ConnectResult.TransportError(IOException("Cannot reach inner hop")) else ConnectResult.Success,
                )
                `when`(client.authenticate(anyString() ?: "", any(AuthHandler::class.java) ?: fallbackHandler))
                    .thenReturn(AuthResult.Success)
                `when`(client.openDirectTcpipTransport(anyString() ?: "", org.mockito.ArgumentMatchers.anyInt(), anyString() ?: "", org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(tunnel)
            }
        }.use { clients ->
            SSH(Host(id = 1, jumpHostId = 2), bridge, manager).connect()
            assertEquals(2, clients.constructed().size)
            clients.constructed().forEach { verify(it).disconnect() }
            verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
        }
    }

    private fun stubHosts(vararg hosts: Host) {
        `when`(manager.hostRepository).thenReturn(repository)
        hosts.forEach { `when`(repository.findHostByIdBlocking(it.id)).thenReturn(it) }
    }

    private suspend fun connectInvalidChain(vararg jumps: Host) {
        val target = Host(id = 1, nickname = "target", jumpHostId = 2)
        stubHosts(*(jumps.toList() + target).toTypedArray())
        mockConstruction(SshClient::class.java).use { connections ->
            SSH(target, bridge, manager).connect()
            assertTrue(connections.constructed().isEmpty())
        }
        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }
}
