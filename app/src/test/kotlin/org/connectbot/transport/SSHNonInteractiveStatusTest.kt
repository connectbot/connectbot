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

import androidx.test.core.app.ApplicationProvider
import com.trilead.ssh2.Connection
import com.trilead.ssh2.LocalPortForwarder
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostConstants
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress

/**
 * Tests that connecting to a host without an interactive shell reports the
 * connection status and port forward information instead of leaving the
 * terminal blank (upstream issue connectbot#1725).
 */
@RunWith(RobolectricTestRunner::class)
class SSHNonInteractiveStatusTest {

    private lateinit var bridge: TerminalBridge
    private lateinit var connection: Connection
    private lateinit var manager: TerminalManager
    private lateinit var ssh: SSH

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Before
    fun setUp() {
        bridge = mock(TerminalBridge::class.java)
        connection = mock(Connection::class.java)
        manager = mock(TerminalManager::class.java)
        `when`(manager.res).thenReturn(res)
        // Authenticating with "none" succeeds, driving straight into
        // finishConnection().
        `when`(connection.authenticateWithNone("alice")).thenReturn(true)

        ssh = SSH()
        ssh.setHost(
            Host(
                nickname = "target",
                username = "alice",
                hostname = "example.com",
                pubkeyId = HostConstants.PUBKEYID_NEVER,
                wantSession = false,
            ),
        )
        ssh.setBridge(bridge)
        ssh.setManager(manager)
        ssh.setConnectionForTesting(connection)
    }

    private fun expectedString(id: Int, vararg args: Any): String = res.getString(id, *args)

    @Test
    fun connectWithoutShell_reportsConnectionEstablished() {
        ssh.authenticate()

        verify(bridge).outputLine(expectedString(R.string.terminal_no_session))
        verify(bridge).outputLine(
            expectedString(
                R.string.terminal_portforward_only,
                expectedString(R.string.hostpref_wantsession_title),
            ),
        )
        verify(bridge).onConnected()
    }

    @Test
    fun connectWithoutShell_andNoForwards_warnsNothingIsForwarded() {
        ssh.authenticate()

        verify(bridge).outputLine(expectedString(R.string.terminal_no_portforwards))
    }

    @Test
    fun authenticationFailure_doesNotReportConnected() {
        `when`(connection.authenticateWithNone("alice")).thenReturn(false)
        `when`(connection.isAuthMethodAvailable("alice", "keyboard-interactive")).thenReturn(false)
        `when`(connection.isAuthMethodAvailable("alice", "password")).thenReturn(false)

        ssh.authenticate()

        verify(bridge, never()).onConnected()
    }

    @Test
    fun connectWithoutShell_withWorkingForward_reportsForwardEnabled() {
        val forward = PortForward(
            hostId = 1L,
            nickname = "web",
            type = HostConstants.PORTFORWARD_LOCAL,
            sourcePort = 8080,
            destAddr = "localhost",
            destPort = 80,
        )
        ssh.addPortForward(forward)
        `when`(connection.createLocalPortForwarder(any(InetSocketAddress::class.java), anyString(), anyInt()))
            .thenReturn(mock(LocalPortForwarder::class.java))

        ssh.authenticate()

        verify(bridge).outputLine(
            expectedString(R.string.terminal_enable_portfoward, forward.getDescription()),
        )
        verify(bridge).onConnected()
    }

    @Test
    fun connectWithoutShell_withBrokenForward_reportsForwardFailure() {
        val forward = PortForward(
            hostId = 1L,
            nickname = "web",
            type = HostConstants.PORTFORWARD_LOCAL,
            sourcePort = 8080,
            destAddr = "localhost",
            destPort = 80,
        )
        ssh.addPortForward(forward)
        `when`(connection.createLocalPortForwarder(any(InetSocketAddress::class.java), anyString(), anyInt()))
            .thenThrow(RuntimeException("bind failed"))

        ssh.authenticate()

        verify(bridge).outputLine(
            expectedString(R.string.terminal_enable_portfoward_failed, forward.getDescription()),
        )
        verify(bridge).onConnected()
    }
}
