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

package org.connectbot.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class TailscaleNetworkDetectorTest {
    @Test
    fun isTailscaleVpn_recognizesTailscaleLinkAddresses() {
        assertTrue(
            AndroidTailscaleNetworkDetector.isTailscaleVpn(
                isVpn = true,
                linkAddresses = listOf(InetAddress.getByName("100.72.18.4")),
                dnsServers = emptyList(),
            ),
        )

        assertTrue(
            AndroidTailscaleNetworkDetector.isTailscaleVpn(
                isVpn = true,
                linkAddresses = listOf(InetAddress.getByName("fd7a:115c:a1e0::1234")),
                dnsServers = emptyList(),
            ),
        )
    }

    @Test
    fun isTailscaleVpn_recognizesQuad100Dns() {
        assertTrue(
            AndroidTailscaleNetworkDetector.isTailscaleVpn(
                isVpn = true,
                linkAddresses = listOf(InetAddress.getByName("10.0.0.2")),
                dnsServers = listOf(InetAddress.getByName("100.100.100.100")),
            ),
        )
    }

    @Test
    fun isTailscaleVpn_rejectsNonVpnAndOtherVpnAddresses() {
        assertFalse(
            AndroidTailscaleNetworkDetector.isTailscaleVpn(
                isVpn = false,
                linkAddresses = listOf(InetAddress.getByName("100.72.18.4")),
                dnsServers = listOf(InetAddress.getByName("100.100.100.100")),
            ),
        )

        assertFalse(
            AndroidTailscaleNetworkDetector.isTailscaleVpn(
                isVpn = true,
                linkAddresses = listOf(InetAddress.getByName("10.0.0.2")),
                dnsServers = listOf(InetAddress.getByName("1.1.1.1")),
            ),
        )
    }
}
