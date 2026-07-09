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

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.InetAddress

/**
 * Hostname and address detection for Tailscale MagicDNS (.ts.net)
 * resolution.
 */
class TailscaleResolverTest {
    @Test
    fun isTailscaleHostname_recognizesMagicDnsNames() {
        assertThat(TailscaleResolver.isTailscaleHostname("pi.tailnet.ts.net")).isTrue()
        assertThat(TailscaleResolver.isTailscaleHostname("PI.TAILNET.TS.NET")).isTrue()
        assertThat(TailscaleResolver.isTailscaleHostname("pi.tailnet.ts.net.")).isTrue()
        assertThat(TailscaleResolver.isTailscaleHostname("host.example.com.ts.net")).isTrue()
    }

    @Test
    fun isTailscaleHostname_rejectsNonMagicDnsNames() {
        assertThat(TailscaleResolver.isTailscaleHostname("ts.net")).isFalse()
        // A bare tailnet name, not a host on one.
        assertThat(TailscaleResolver.isTailscaleHostname("foo.ts.net")).isFalse()
        assertThat(TailscaleResolver.isTailscaleHostname("example.com")).isFalse()
        assertThat(TailscaleResolver.isTailscaleHostname("pits.net")).isFalse()
        assertThat(TailscaleResolver.isTailscaleHostname("a.b.pits.net")).isFalse()
        assertThat(TailscaleResolver.isTailscaleHostname("pi.tailnet.ts.net.evil.com")).isFalse()
        assertThat(TailscaleResolver.isTailscaleHostname("host..ts.net")).isFalse()
        assertThat(TailscaleResolver.isTailscaleHostname("100.64.0.1")).isFalse()
        assertThat(TailscaleResolver.isTailscaleHostname("::1")).isFalse()
    }

    @Test
    fun isTailscaleHostname_rejectsInvalidLabelLengths() {
        assertThat(TailscaleResolver.isTailscaleHostname("${"a".repeat(64)}.tailnet.ts.net")).isFalse()
    }

    @Test
    fun isTailscaleAddress_recognizesTailscaleRanges() {
        assertThat(TailscaleResolver.isTailscaleAddress("100.64.0.1")).isTrue()
        assertThat(TailscaleResolver.isTailscaleAddress("100.100.100.100")).isTrue()
        assertThat(TailscaleResolver.isTailscaleAddress("100.127.255.255")).isTrue()
        assertThat(TailscaleResolver.isTailscaleAddress("fd7a:115c:a1e0::1")).isTrue()
        assertThat(TailscaleResolver.isTailscaleAddress("[fd7a:115c:a1e0::1]")).isTrue()
    }

    @Test
    fun isTailscaleAddress_rejectsOtherAddressesAndNames() {
        assertThat(TailscaleResolver.isTailscaleAddress("100.63.255.255")).isFalse()
        assertThat(TailscaleResolver.isTailscaleAddress("100.128.0.1")).isFalse()
        assertThat(TailscaleResolver.isTailscaleAddress("10.0.0.1")).isFalse()
        assertThat(TailscaleResolver.isTailscaleAddress("fd7a:115c:a1e1::1")).isFalse()
        assertThat(TailscaleResolver.isTailscaleAddress("::1")).isFalse()
        assertThat(TailscaleResolver.isTailscaleAddress("pi.tailnet.ts.net")).isFalse()
        assertThat(TailscaleResolver.isTailscaleAddress("notanip")).isFalse()
    }

    @Test
    fun isTailscaleIp_checksRangeBoundaries() {
        assertThat(TailscaleResolver.isTailscaleIp(InetAddress.getByName("100.64.0.0"))).isTrue()
        assertThat(TailscaleResolver.isTailscaleIp(InetAddress.getByName("100.127.255.255"))).isTrue()
        assertThat(TailscaleResolver.isTailscaleIp(InetAddress.getByName("100.63.255.255"))).isFalse()
        assertThat(TailscaleResolver.isTailscaleIp(InetAddress.getByName("100.128.0.0"))).isFalse()
        assertThat(TailscaleResolver.isTailscaleIp(InetAddress.getByName("fd7a:115c:a1e0:ab12::1"))).isTrue()
        assertThat(TailscaleResolver.isTailscaleIp(InetAddress.getByName("fd7a:115c::1"))).isFalse()
    }
}
