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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.connectbot.service.DisconnectAction
import org.connectbot.service.DisconnectPolicy
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.sshlib.SessionExit
import org.connectbot.sshlib.SshException
import org.connectbot.sshlib.SshSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class SSHDisconnectTest {
    @Test
    fun connectionLost_withShellExitStatus_preservesSessionExit() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.exitInfo).thenReturn(CompletableDeferred<SessionExit>(SessionExit.Status(0)))

        transport(session, bridge).handleConnectionLost(null)

        verify(bridge).dispatchDisconnect(DisconnectReason.SESSION_EXIT)
    }

    @Test
    fun connectionLost_withErrorAfterShellExit_preservesSessionExit() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.exitInfo).thenReturn(CompletableDeferred<SessionExit>(SessionExit.Status(0)))

        transport(session, bridge).handleConnectionLost(java.io.EOFException())

        verify(bridge).dispatchDisconnect(DisconnectReason.SESSION_EXIT)
    }

    @Test
    fun connectionLost_withoutExitStatus_reportsRemoteEof() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.exitInfo).thenReturn(CompletableDeferred())

        transport(session, bridge).handleConnectionLost(null)

        verify(bridge).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
    }

    @Test
    fun connectionLost_withError_reportsIoError() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.exitInfo).thenReturn(CompletableDeferred())

        transport(session, bridge).handleConnectionLost(SshException("Connection reset"))

        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @Test
    fun connectionLost_duringNetworkGracePeriod_doesNotDisconnect() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(bridge.isInGracePeriod()).thenReturn(true)

        transport(session, bridge).handleConnectionLost(SshException("Network lost"))

        verify(bridge).isInGracePeriod()
        verifyNoMoreInteractions(bridge)
        verifyNoInteractions(session)
    }

    @Test
    fun read_withSshException_dispatchesIoError() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.read()).thenAnswer { throw SshException("Read failed") }
        val ssh = transport(session, bridge)

        assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { ssh.read(ByteArray(32), 0, 32) }
        }

        verify(bridge).dispatchDisconnect(DisconnectReason.IO_ERROR)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun read_whenRelayCancelled_doesNotDisconnect() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        val blockingSession = object : SshSession by session {
            override suspend fun read(): ByteArray? = awaitCancellation()
        }
        val ssh = transport(blockingSession, bridge)
        val reader = launch { ssh.read(ByteArray(32), 0, 32) }
        runCurrent()

        reader.cancel()
        reader.join()

        verifyNoInteractions(bridge)
    }

    @Test
    fun read_withFinalStdoutAndEof_returnsOutputBeforeDisconnecting() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.read()).thenReturn("logout\n".toByteArray())
        val ssh = transport(session, bridge)
        val output = ByteArray(32)
        val bytesRead = ssh.read(output, 0, output.size)
        assertEquals("logout\n", output.decodeToString(0, bytesRead))
        verifyNoInteractions(bridge)
    }

    @Test
    fun read_withEof_delegatesCloseToBridgeWithoutClosingTransportDirectly() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.read()).thenReturn(null)
        `when`(session.exitInfo).thenReturn(CompletableDeferred())
        val ssh = spy(transport(session, bridge))
        assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { ssh.read(ByteArray(32), 0, 32) }
        }
        verify(bridge).dispatchDisconnect(DisconnectReason.REMOTE_EOF)
        verify(ssh, never()).close()
    }

    @Test
    fun read_withShellExitStatus_closesTerminalInsteadOfShowingReconnect() = runTest {
        val session = mock(SshSession::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.read()).thenReturn(null)
        `when`(session.exitInfo).thenReturn(CompletableDeferred<SessionExit>(SessionExit.Status(0)))
        val ssh = transport(session, bridge)
        assertThrows(java.io.IOException::class.java) {
            kotlinx.coroutines.runBlocking { ssh.read(ByteArray(32), 0, 32) }
        }
        verify(bridge).dispatchDisconnect(DisconnectReason.SESSION_EXIT)
        assertEquals(
            DisconnectAction.CloseImmediately,
            DisconnectPolicy.decide(DisconnectReason.SESSION_EXIT, quickDisconnect = false, stayConnected = false),
        )
    }

    @Test
    fun getDisconnectReasonForClosedSession_whenExitStatusFollowsEof_reportsSessionExit() = runTest {
        val session = mock(SshSession::class.java)
        val exit = CompletableDeferred<SessionExit>()
        `when`(session.exitInfo).thenReturn(exit)
        val reason = async { SSH().getDisconnectReasonForClosedSession(session) }
        delay(100)
        exit.complete(SessionExit.Status(0))
        assertEquals(DisconnectReason.SESSION_EXIT, reason.await())
    }

    @Test
    fun getDisconnectReasonForClosedSession_withoutExitStatus_reportsRemoteEof() = runTest {
        val session = mock(SshSession::class.java)
        `when`(session.exitInfo).thenReturn(CompletableDeferred())
        assertEquals(DisconnectReason.REMOTE_EOF, SSH().getDisconnectReasonForClosedSession(session))
    }

    @Test
    fun getDisconnectReasonForClosedSession_withCancelledExitInfo_reportsRemoteEof() = runTest {
        val session = mock(SshSession::class.java)
        val exit = CompletableDeferred<SessionExit>().apply { cancel() }
        `when`(session.exitInfo).thenReturn(exit)

        assertEquals(DisconnectReason.REMOTE_EOF, SSH().getDisconnectReasonForClosedSession(session))
    }

    @Test
    fun getDisconnectReasonForClosedSession_withExitSignal_reportsRemoteEof() = runTest {
        val session = mock(SshSession::class.java)
        `when`(session.exitInfo).thenReturn(
            CompletableDeferred<SessionExit>(SessionExit.Signal("TERM", false, "")),
        )
        assertEquals(DisconnectReason.REMOTE_EOF, SSH().getDisconnectReasonForClosedSession(session))
    }

    private fun transport(session: SshSession, bridge: TerminalBridge): SSH = SSH().apply transport@{
        setBridge(bridge)
        SSH::class.java.getDeclaredField("session").apply {
            isAccessible = true
            set(this@transport, session)
        }
    }
}
