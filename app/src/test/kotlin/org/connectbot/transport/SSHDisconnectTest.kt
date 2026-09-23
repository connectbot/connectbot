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
import org.connectbot.service.TerminalBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream

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

    private fun SSH.setPrivateField(name: String, value: Any?) {
        SSH::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }
}
