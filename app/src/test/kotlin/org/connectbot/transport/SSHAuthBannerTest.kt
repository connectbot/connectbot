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

import org.connectbot.service.TerminalBridge
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class SSHAuthBannerTest {
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
            anyString(),
            anyString(),
            anyList(),
            any(),
        )
    }
}
