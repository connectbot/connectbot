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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.Host
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShizukuShellTest {
    @Test
    fun protocol_isRegisteredAsOnDeviceTransport() {
        assertThat(TransportFactory.getTransportNames()).contains("shizuku")
        assertThat(TransportFactory.getTransport("shizuku")).isInstanceOf(ShizukuShell::class.java)
        assertThat(TransportFactory.usesNetwork("shizuku")).isFalse()
        assertThat(TransportFactory.canForwardPorts("shizuku")).isFalse()
    }

    @Test
    fun usesNetwork_defaultsToTrueForNetworkAndUnknownProtocols() {
        assertThat(TransportFactory.usesNetwork("ssh")).isTrue()
        assertThat(TransportFactory.usesNetwork("local")).isFalse()
        assertThat(TransportFactory.usesNetwork("bogus")).isTrue()
    }

    @Test
    fun uri_roundTripsNicknameThroughHost() {
        val uri = TransportFactory.getUri("shizuku", "adb shell")!!
        assertThat(uri.scheme).isEqualTo("shizuku")
        assertThat(uri.fragment).isEqualTo("adb shell")

        val host = ShizukuShell().createHost(uri)
        assertThat(host.protocol).isEqualTo("shizuku")
        assertThat(host.nickname).isEqualTo("adb shell")
        assertThat(host.hostname).isEmpty()

        val selection = mutableMapOf<String, String>()
        ShizukuShell().getSelectionArgs(host.copy(isTemporary = true).getUri(), selection)
        assertThat(selection).containsEntry("protocol", "shizuku").containsEntry("nickname", "adb shell")
    }

    @Test
    fun hostUri_omitsAuthority() {
        val uri = Host.createShizukuHost("adb shell").copy(isTemporary = true).getUri()
        assertThat(uri.toString()).isEqualTo("shizuku:#adb%20shell")
    }

    @Test
    fun transport_isDisconnectedBeforeConnect() {
        val transport = ShizukuShell()
        assertThat(transport.isConnected()).isFalse()
        assertThat(transport.isSessionOpen()).isFalse()
        assertThat(transport.usesNetwork()).isFalse()
    }
}
