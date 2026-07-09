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
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/**
 * Tests that hosts that look tunnel-bound (Tailscale MagicDNS names or
 * Tailscale/CGNAT-range addresses) produce a terminal warning when no VPN is
 * active, and stay silent otherwise.
 */
@RunWith(RobolectricTestRunner::class)
class SSHVpnWarningTest {

    private lateinit var bridge: TerminalBridge
    private lateinit var manager: TerminalManager
    private lateinit var ssh: SSH

    private val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    @Before
    fun setUp() {
        bridge = mock(TerminalBridge::class.java)
        manager = mock(TerminalManager::class.java)
        `when`(manager.res).thenReturn(res)

        ssh = SSH()
        ssh.setBridge(bridge)
        ssh.setManager(manager)
    }

    private fun host(hostname: String): Host = Host(nickname = hostname, hostname = hostname)

    @Test
    fun tailscaleAddressWithoutVpn_warns() {
        ssh.warnIfVpnInactive(host("100.100.1.2"))

        verify(bridge).outputLine(res.getString(R.string.terminal_vpn_inactive, "100.100.1.2"))
    }

    @Test
    fun magicDnsNameWithoutVpn_warns() {
        ssh.warnIfVpnInactive(host("pi.tailnet.ts.net"))

        verify(bridge).outputLine(res.getString(R.string.terminal_vpn_inactive, "pi.tailnet.ts.net"))
    }

    @Test
    fun tailscaleAddressWithVpn_staysSilent() {
        `when`(manager.isVpnActive()).thenReturn(true)

        ssh.warnIfVpnInactive(host("100.100.1.2"))

        verify(bridge, never()).outputLine(anyString())
    }

    @Test
    fun tailscaleAddressWithoutManager_staysSilent() {
        ssh.setManager(null)

        ssh.warnIfVpnInactive(host("100.100.1.2"))

        verify(bridge, never()).outputLine(anyString())
    }

    @Test
    fun ordinaryHost_staysSilent() {
        ssh.warnIfVpnInactive(host("example.com"))
        ssh.warnIfVpnInactive(host("192.168.1.4"))

        verify(bridge, never()).outputLine(anyString())
    }
}
