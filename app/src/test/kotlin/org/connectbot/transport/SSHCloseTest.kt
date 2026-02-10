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
import org.connectbot.sshlib.SshClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
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

    @Suppress("UNCHECKED_CAST")
    private fun jumps(ssh: SSH): MutableList<SshClient> = SSH::class.java.getDeclaredField("jumpClients").apply {
        isAccessible = true
    }.get(ssh) as MutableList<SshClient>
}
