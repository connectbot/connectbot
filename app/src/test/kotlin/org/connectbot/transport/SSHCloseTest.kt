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

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.PortForward
import org.connectbot.sshlib.PortForwarder
import org.connectbot.sshlib.SshClient
import org.connectbot.util.HostConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class SSHCloseTest {
    @Test
    fun reentrantCloseDrainsJumpHostsOnlyOnce() = runTest {
        val ssh = SSH()
        val first = mock(SshClient::class.java)
        val second = mock(SshClient::class.java)
        val reenter = AtomicBoolean(true)
        doAnswer {
            if (reenter.getAndSet(false)) runBlocking { ssh.close() }
            null
        }.`when`(second).disconnect()
        jumps(ssh).addAll(listOf(first, second))

        ssh.close()

        verify(first, times(1)).disconnect()
        verify(second, times(1)).disconnect()
        assertEquals(0, jumps(ssh).size)
    }

    @Test
    fun reentrantTargetCloseDoesNotCloseTargetTwice() = runTest {
        val ssh = SSH()
        val target = mock(SshClient::class.java)
        val reenter = AtomicBoolean(true)
        doAnswer {
            if (reenter.getAndSet(false)) runBlocking { ssh.close() }
            null
        }.`when`(target).disconnect()
        SSH::class.java.getDeclaredField("client").apply { isAccessible = true }.set(ssh, target)

        ssh.close()

        verify(target, times(1)).disconnect()
    }

    @Test
    fun closingStopsSocksListenerBeforeDisconnectingAndClearsState() = runTest {
        val ssh = object : SSH() {
            fun markAuthenticated() {
                authenticated = true
            }
        }
        val client = mock(SshClient::class.java)
        val forwarder = mock(PortForwarder::class.java)
        val forward = PortForward(
            hostId = UUID.randomUUID(),
            nickname = "socks",
            type = HostConstants.PORTFORWARD_DYNAMIC5,
            sourcePort = 1080,
            destAddr = null,
            destPort = 0,
        )
        SSH::class.java.getDeclaredField("client").apply { isAccessible = true }.set(ssh, client)
        `when`(client.dynamicPortForward(1080)).thenReturn(forwarder)
        ssh.addPortForward(forward)
        ssh.markAuthenticated()
        assertTrue(ssh.enablePortForward(forward))

        ssh.close()

        val order = inOrder(forwarder, client)
        order.verify(forwarder).stop()
        order.verify(client).disconnect()
        assertFalse(forward.isEnabled())
        assertNull(forward.getIdentifier())
        assertFalse(ssh.enablePortForward(forward))
    }

    @Suppress("UNCHECKED_CAST")
    private fun jumps(ssh: SSH): MutableList<SshClient> = SSH::class.java.getDeclaredField("jumpClients").apply {
        isAccessible = true
    }.get(ssh) as MutableList<SshClient>
}
