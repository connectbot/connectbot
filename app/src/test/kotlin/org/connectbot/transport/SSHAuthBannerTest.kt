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
import org.connectbot.data.entity.Host
import org.connectbot.service.TerminalBridge
import org.connectbot.util.HostConstants
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.io.IOException

class SSHAuthBannerTest {
    @Test
    fun authenticate_whenNoneAuthSucceeds_dismissesAuthBanner() {
        val bridge = mock(TerminalBridge::class.java)
        val connection = mock(Connection::class.java)
        `when`(connection.authenticateWithNone("alice")).thenReturn(true)
        val ssh = sshWithConnection(bridge, connection)

        ssh.authenticate()

        verify(bridge).dismissAuthBannersFrom("target")
    }

    @Test
    fun authenticate_whenNoneAuthFails_dismissesAuthBannerBeforeContinuing() {
        val bridge = mock(TerminalBridge::class.java)
        val connection = mock(Connection::class.java)
        `when`(connection.authenticateWithNone("alice")).thenReturn(false)
        val ssh = sshWithConnection(bridge, connection)

        ssh.authenticate()

        verify(bridge).dismissAuthBannersFrom("target")
    }

    @Test
    fun authenticate_whenNoneAuthThrows_dismissesAuthBannerBeforeContinuing() {
        val bridge = mock(TerminalBridge::class.java)
        val connection = mock(Connection::class.java)
        `when`(connection.authenticateWithNone("alice")).thenThrow(IOException("none failed"))
        val ssh = sshWithConnection(bridge, connection)

        ssh.authenticate()

        verify(bridge).dismissAuthBannersFrom("target")
    }

    @Test
    fun authenticateJumpHost_whenNoneAuthThrows_dismissesAuthBanner() {
        val bridge = mock(TerminalBridge::class.java)
        val connection = mock(Connection::class.java)
        val jumpHost = Host(
            nickname = "jump",
            username = "alice",
            hostname = "jump.example.com",
        )
        `when`(connection.authenticateWithNone("alice")).thenThrow(IOException("none failed"))
        val ssh = SSH().apply {
            setBridge(bridge)
        }

        ssh.authenticateJumpHost(connection, jumpHost)

        verify(bridge).dismissAuthBannersFrom("jump")
    }

    @Test
    fun handleAuthBanner_outputsSourceAndBanner() {
        val bridge = mock(TerminalBridge::class.java)
        val ssh = SSH().apply {
            setBridge(bridge)
        }

        ssh.handleAuthBanner("target", "Visit https://login.tailscale.com/a/123456", "en")

        verify(bridge).outputLine("[target] Authentication message:")
        verify(bridge).outputLine("Visit https://login.tailscale.com/a/123456")
    }

    @Test
    fun handleAuthBanner_blankBanner_isIgnored() {
        val bridge = mock(TerminalBridge::class.java)
        val ssh = SSH().apply {
            setBridge(bridge)
        }

        ssh.handleAuthBanner("target", "   ", "en")

        verifyNoInteractions(bridge)
    }

    @Test
    fun handleAuthBanner_withUrl_enqueuesAuthBanner() {
        val bridge = mock(TerminalBridge::class.java)
        val ssh = SSH().apply {
            setBridge(bridge)
        }

        ssh.handleAuthBanner("jump", "Visit https://login.tailscale.com/a/123456", "en")

        verify(bridge).enqueueAuthBanner(
            "jump",
            "Visit https://login.tailscale.com/a/123456",
            listOf("https://login.tailscale.com/a/123456"),
            "en",
        )
    }

    @Test
    fun handleAuthBanner_withoutUrl_doesNotEnqueueAuthBanner() {
        val bridge = mock(TerminalBridge::class.java)
        val ssh = SSH().apply {
            setBridge(bridge)
        }

        ssh.handleAuthBanner("target", "Authentication will continue.", null)

        verify(bridge, never()).enqueueAuthBanner(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.any(),
        )
    }

    private fun sshWithConnection(bridge: TerminalBridge, connection: Connection): SSH = SSH().apply {
        setHost(
            Host(
                nickname = "target",
                username = "alice",
                hostname = "example.com",
                pubkeyId = HostConstants.PUBKEYID_NEVER,
            ),
        )
        setBridge(bridge)
        setConnectionForTesting(connection)
    }
}
