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

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.Host
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.FileInputStream
import java.io.IOException

class MoshBootstrapTest {
    private val host = Host(hostname = "203.0.113.1")
    private val session = mock(SshSession::class.java)
    private val client = mock(SshClient::class.java)
    private val bridge = mock(TerminalBridge::class.java)
    private val mosh = Mosh().apply mosh@{
        setBridge(this@MoshBootstrapTest.bridge)
        SSH::class.java.getDeclaredField("client").apply {
            isAccessible = true
            set(this@mosh, client)
        }
    }

    @Test
    fun launcher_waitsForCompleteCredentialsAcrossPackets() = runTest {
        prepareSession(
            "MOSH IP 203.0.113.1\nMOSH CONNECT",
            " 60001 abcdef",
            "ghijklmnopqrstuv\n",
        )

        val credentials = mosh.launchMoshServer(host)

        assertEquals(Mosh.MoshCredentials("203.0.113.1", "60001", "abcdefghijklmnopqrstuv"), credentials)
        verify(session).requestExec(mosh.buildMoshServerCommand(host))
        verify(session, never()).requestShell()
        verify(session).close()
    }

    @Test
    fun launcher_withCrLf_parsesCredentials() = runTest {
        prepareSession("MOSH IP 203.0.113.1\r\nMOSH CONNECT 60001 abcdefghijklmnopqrstuv\r\n")

        assertEquals(
            Mosh.MoshCredentials("203.0.113.1", "60001", "abcdefghijklmnopqrstuv"),
            mosh.launchMoshServer(host),
        )
    }

    @Test
    fun launcher_withIncompleteCredentials_doesNotStartClient() = runTest {
        prepareSession("MOSH IP 203.0.113.1\nMOSH CONNECT 60001 abcdef")

        assertNull(mosh.launchMoshServer(host))
        verify(session).close()
    }

    @Test
    fun launcher_withRejectedExec_closesChannelWithoutReading() = runTest {
        prepareSession()
        `when`(session.requestExec(mosh.buildMoshServerCommand(host))).thenReturn(false)

        assertNull(mosh.launchMoshServer(host))
        verify(session, never()).read()
        verify(session).close()
    }

    @Test
    fun launcher_withStderr_reportsServerDiagnostics() = runTest {
        prepareSession()
        val stderr = Channel<ByteArray>(Channel.UNLIMITED).apply {
            trySend("mosh-server: command not found".toByteArray())
            close()
        }
        `when`(session.stderr).thenReturn(stderr)

        assertNull(mosh.launchMoshServer(host))
        verify(bridge).outputLine("mosh-server: command not found")
    }

    @Test
    fun sshDisconnect_afterHandoff_doesNotDisconnectMosh() = runTest {
        Mosh::class.java.getDeclaredField("initialSshDetached").apply {
            isAccessible = true
            setBoolean(mosh, true)
        }

        mosh.handleConnectionLost(null)

        verify(bridge, never()).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
    }

    @Test
    fun clientEof_dispatchesDisconnectWhileTransportCanStillBeClosed() = runTest {
        prepareClientInput().apply {
            `when`(read(any(ByteArray::class.java), anyInt(), anyInt())).thenReturn(-1)
        }

        assertThrows(IOException::class.java) {
            runBlocking { mosh.read(ByteArray(32), 0, 32) }
        }

        verify(bridge).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
        assertTrue(mosh.isConnected())
    }

    @Test
    fun clientReadFailure_dispatchesIoError() = runTest {
        prepareClientInput().apply {
            `when`(read(any(ByteArray::class.java), anyInt(), anyInt())).thenThrow(IOException("PTY closed"))
        }

        assertThrows(IOException::class.java) {
            runBlocking { mosh.read(ByteArray(32), 0, 32) }
        }

        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
        assertTrue(mosh.isConnected())
    }

    private fun prepareClientInput(): FileInputStream = mock(FileInputStream::class.java).also { input ->
        Mosh::class.java.getDeclaredField("moshInputStream").apply {
            isAccessible = true
            set(mosh, input)
        }
        Mosh::class.java.getDeclaredField("moshConnected").apply {
            isAccessible = true
            setBoolean(mosh, true)
        }
    }

    private suspend fun prepareSession(vararg chunks: String) {
        `when`(client.openSession()).thenReturn(session)
        `when`(session.requestExec(mosh.buildMoshServerCommand(host))).thenReturn(true)
        `when`(session.stderr).thenReturn(Channel<ByteArray>().apply { close() })
        val output = ArrayDeque(chunks.map { it.toByteArray() })
        `when`(session.read()).thenAnswer { output.removeFirstOrNull() }
    }
}
