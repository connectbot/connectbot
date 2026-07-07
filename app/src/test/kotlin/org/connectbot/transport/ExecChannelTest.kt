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
import com.trilead.ssh2.Session
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIOException
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class ExecChannelTest {
    @Test
    fun `openExecChannel throws when not connected`() {
        val ssh = SSH()

        assertThatIOException()
            .isThrownBy { ssh.openExecChannel("tmux -V") }
            .withMessage("Not connected")
    }

    @Test
    fun `openExecChannel throws when not authenticated`() {
        val ssh = SSH()
        ssh.setConnectionForTesting(mock<Connection>())

        assertThatIOException()
            .isThrownBy { ssh.openExecChannel("tmux -V") }
            .withMessage("Not authenticated")
    }

    @Test
    fun `openExecChannel runs command on new session without pty by default`() {
        val session = mock<Session>()
        val connection = mock<Connection> {
            on { openSession() } doReturn session
        }
        val ssh = SSH()
        ssh.setConnectionForTesting(connection)
        ssh.setAuthenticatedForTesting(true)

        val channel = ssh.openExecChannel("tmux -u -C attach-session -t '\$1'")

        assertThat(channel).isInstanceOf(TrileadExecChannel::class.java)
        verify(session).execCommand("tmux -u -C attach-session -t '\$1'")
        verify(session, never()).requestPTY(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `openExecChannel requests pty when asked`() {
        val session = mock<Session>()
        val connection = mock<Connection> {
            on { openSession() } doReturn session
        }
        val ssh = SSH()
        ssh.setConnectionForTesting(connection)
        ssh.setAuthenticatedForTesting(true)

        ssh.openExecChannel("top", allocPty = true, ptyTerm = "xterm-256color")

        verify(session).requestPTY(eq("xterm-256color"), eq(0), eq(0), eq(0), eq(0), eq(null))
        verify(session).execCommand("top")
    }

    @Test
    fun `openExecChannel closes session when exec fails`() {
        val session = mock<Session> {
            on { execCommand(any()) } doThrow IOException("channel rejected")
        }
        val connection = mock<Connection> {
            on { openSession() } doReturn session
        }
        val ssh = SSH()
        ssh.setConnectionForTesting(connection)
        ssh.setAuthenticatedForTesting(true)

        assertThatIOException()
            .isThrownBy { ssh.openExecChannel("tmux -V") }
            .withMessage("channel rejected")
        verify(session).close()
    }

    @Test
    fun `default transport does not support exec channels`() {
        val transport = object : AbsTransport() {
            override fun connect() = Unit
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = -1
            override fun write(buffer: ByteArray) = Unit
            override fun write(c: Int) = Unit
            override fun flush() = Unit
            override fun close() = Unit
            override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) = Unit
            override fun isConnected(): Boolean = false
            override fun isSessionOpen(): Boolean = false
            override fun getDefaultPort(): Int = 0
            override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String = ""
            override fun getSelectionArgs(uri: android.net.Uri, selection: MutableMap<String, String>) = Unit
            override fun createHost(uri: android.net.Uri): org.connectbot.data.entity.Host = throw UnsupportedOperationException()
            override fun usesNetwork(): Boolean = false
            override fun getLocalIpAddress(): String? = null
        }

        assertThat(transport.canOpenExecChannels()).isFalse()
        assertThatIOException()
            .isThrownBy { transport.openExecChannel("true") }
    }

    @Test
    fun `trilead channel delegates streams and lifecycle to session`() {
        val stdin = java.io.ByteArrayOutputStream()
        val stdout = java.io.ByteArrayInputStream(byteArrayOf(1))
        val stderr = java.io.ByteArrayInputStream(byteArrayOf(2))
        val session = mock<Session> {
            on { getStdin() } doReturn stdin
            on { getStdout() } doReturn stdout
            on { getStderr() } doReturn stderr
            on { exitStatus } doReturn 42
        }

        val channel = TrileadExecChannel(session)

        assertThat(channel.stdin).isSameAs(stdin)
        assertThat(channel.stdout).isSameAs(stdout)
        assertThat(channel.stderr).isSameAs(stderr)
        assertThat(channel.exitStatus()).isEqualTo(42)
        channel.close()
        verify(session).close()
    }
}
