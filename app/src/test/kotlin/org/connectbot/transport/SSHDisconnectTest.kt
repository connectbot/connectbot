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

import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Session
import org.connectbot.service.DisconnectAction
import org.connectbot.service.DisconnectPolicy
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SSHDisconnectTest {

    @Test
    fun read_withFinalStdoutAndEof_returnsOutputBeforeDisconnecting() {
        val session = mock(Session::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.waitForCondition(anyInt(), eq(0L))).thenReturn(
            ChannelCondition.STDOUT_DATA or ChannelCondition.EOF,
        )
        val ssh = SSH().apply {
            setBridge(bridge)
            setPrivateField("session", session)
            setPrivateField("stdout", ByteArrayInputStream("logout\\n".toByteArray()))
        }

        val output = ByteArray(32)
        val bytesRead = ssh.read(output, 0, output.size)

        assertEquals("logout\\n", output.decodeToString(0, bytesRead))
        verifyNoInteractions(bridge)
    }

    @Test
    fun read_withEof_delegatesCloseToBridgeWithoutClosingTransportDirectly() {
        val session = mock(Session::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.exitStatus).thenReturn(null)
        `when`(session.waitForCondition(anyInt(), eq(0L))).thenReturn(ChannelCondition.EOF)
        val ssh = spy(SSH()).apply {
            setBridge(bridge)
            setPrivateField("session", session)
        }
        doNothing().`when`(ssh).close()

        assertThrows(java.io.IOException::class.java) {
            ssh.read(ByteArray(32), 0, 32)
        }

        verify(bridge).dispatchDisconnect(org.connectbot.service.DisconnectReason.REMOTE_EOF)
        verify(ssh, never()).close()
    }

    @Test
    fun read_withShellExitStatus_closesTerminalInsteadOfShowingReconnect() {
        val session = mock(Session::class.java)
        val bridge = mock(TerminalBridge::class.java)
        `when`(session.exitStatus).thenReturn(0)
        `when`(session.waitForCondition(anyInt(), eq(0L))).thenReturn(ChannelCondition.EOF)
        val ssh = SSH().apply {
            setBridge(bridge)
            setPrivateField("session", session)
        }

        assertThrows(java.io.IOException::class.java) {
            ssh.read(ByteArray(32), 0, 32)
        }

        val reason = mockingDetails(bridge).invocations
            .single { it.method.name == "dispatchDisconnect" }
            .getArgument<DisconnectReason>(0)
        assertEquals(
            DisconnectAction.CloseImmediately,
            DisconnectPolicy.decide(reason, quickDisconnect = false, stayConnected = false),
        )
    }

    @Test
    fun determineDisconnectReasonForClosedSession_whenExitStatusFollowsEof_reportsSessionExit() {
        val session = mock(Session::class.java)
        val ssh = SSH().apply {
            setPrivateField("stdin", ByteArrayOutputStream())
            setPrivateField("interactiveShellOpen", true)
        }
        `when`(session.exitStatus).thenReturn(null, null, 0)
        `when`(session.waitForCondition(eq(ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL), eq(0L)))
            .thenReturn(0)
        `when`(session.waitForCondition(eq(ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL), eq(250L)))
            .thenReturn(ChannelCondition.EXIT_STATUS)

        ssh.write(0x04)

        assertEquals(DisconnectReason.SESSION_EXIT, ssh.determineDisconnectReasonForClosedSession(session))
    }

    @Test
    fun determineDisconnectReasonForClosedSession_whenCtrlDPrecedesAnotherWrite_stillReportsSessionExit() {
        val session = mock(Session::class.java)
        val ssh = SSH().apply {
            setPrivateField("stdin", ByteArrayOutputStream())
            setPrivateField("interactiveShellOpen", true)
        }
        `when`(session.exitStatus).thenReturn(null, null, 0)
        `when`(session.waitForCondition(eq(ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL), eq(0L)))
            .thenReturn(0)
        `when`(session.waitForCondition(eq(ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL), eq(250L)))
            .thenReturn(ChannelCondition.EXIT_STATUS)

        ssh.write(0x04)
        ssh.write('x'.code)

        assertEquals(DisconnectReason.SESSION_EXIT, ssh.determineDisconnectReasonForClosedSession(session))
    }

    @Test
    fun determineDisconnectReasonForClosedSession_afterShellClose_doesNotReuseOldCtrlD() {
        val closedSession = mock(Session::class.java)
        val laterSession = mock(Session::class.java)
        val ssh = SSH().apply {
            setPrivateField("stdin", ByteArrayOutputStream())
            setPrivateField("interactiveShellOpen", true)
            setPrivateField("session", closedSession)
        }
        `when`(laterSession.exitStatus).thenReturn(null)
        `when`(laterSession.waitForCondition(eq(ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL), eq(0L)))
            .thenReturn(0)

        ssh.write(0x04)
        ssh.close()

        assertEquals(DisconnectReason.REMOTE_EOF, ssh.determineDisconnectReasonForClosedSession(laterSession))
        verify(laterSession, never()).waitForCondition(eq(ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL), eq(250L))
    }

    @Test
    fun determineDisconnectReasonForClosedSession_withoutExitStatus_reportsRemoteEof() {
        val session = mock(Session::class.java)
        `when`(session.exitStatus).thenReturn(null)
        `when`(session.waitForCondition(eq(ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL), eq(0L)))
            .thenReturn(0)

        assertEquals(DisconnectReason.REMOTE_EOF, SSH().determineDisconnectReasonForClosedSession(session))
        verify(session, never()).waitForCondition(eq(ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL), eq(250L))
    }

    @Test
    fun determineDisconnectReasonForClosedSession_withExitSignal_reportsRemoteEof() {
        val session = mock(Session::class.java)
        `when`(session.exitStatus).thenReturn(null)
        `when`(session.exitSignal).thenReturn("TERM")

        assertEquals(DisconnectReason.REMOTE_EOF, SSH().determineDisconnectReasonForClosedSession(session))
    }

    private fun SSH.setPrivateField(name: String, value: Any?) {
        SSH::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }
}
