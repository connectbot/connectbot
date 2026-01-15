/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.data

import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Pubkey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SshConfigExporter.
 */
class SshConfigExporterTest {

    private val exporter = SshConfigExporter()

    @Test
    fun `export basicHost outputsCorrectFormat`() {
        val host = Host(
            id = 1,
            nickname = "myserver",
            protocol = "ssh",
            hostname = "example.com",
            username = "admin",
            port = 2222
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("Host myserver"))
        assertTrue(result.configText.contains("HostName example.com"))
        assertTrue(result.configText.contains("User admin"))
        assertTrue(result.configText.contains("Port 2222"))
        assertEquals(1, result.hostCount)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `export nonDefaultPort includesPortDirective`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com",
            port = 2222
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("Port 2222"))
    }

    @Test
    fun `export defaultPort omitsPortDirective`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com",
            port = 22
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertFalse(result.configText.contains("Port "))
    }

    @Test
    fun `export nonSshHost skipped`() {
        val hosts = listOf(
            Host(id = 1, nickname = "ssh", protocol = "ssh", hostname = "a.com"),
            Host(id = 2, nickname = "telnet", protocol = "telnet", hostname = "b.com"),
            Host(id = 3, nickname = "local", protocol = "local", hostname = "")
        )

        val result = exporter.export(
            hosts = hosts.map { it to emptyList() },
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertEquals(1, result.hostCount)
        assertEquals(2, result.skippedCount)
        assertTrue(result.configText.contains("Host ssh"))
        assertFalse(result.configText.contains("Host telnet"))
        assertFalse(result.configText.contains("Host local"))
    }

    @Test
    fun `export hostWithPortForwards outputsForwards`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com"
        )
        val portForwards = listOf(
            PortForward(
                id = 1,
                hostId = 1,
                nickname = "web",
                type = "local",
                sourcePort = 8080,
                destAddr = "localhost",
                destPort = 80
            ),
            PortForward(
                id = 2,
                hostId = 1,
                nickname = "remote",
                type = "remote",
                sourcePort = 9090,
                destAddr = "localhost",
                destPort = 9000
            ),
            PortForward(
                id = 3,
                hostId = 1,
                nickname = "dynamic",
                type = "dynamic5",
                sourcePort = 1080,
                destAddr = null,
                destPort = 0
            )
        )

        val result = exporter.export(
            hosts = listOf(host to portForwards),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("LocalForward 8080 localhost:80"))
        assertTrue(result.configText.contains("RemoteForward 9090 localhost:9000"))
        assertTrue(result.configText.contains("DynamicForward 1080"))
    }

    @Test
    fun `export hostWithJumpHost outputsProxyJump`() {
        val jumpHost = Host(
            id = 1,
            nickname = "bastion",
            protocol = "ssh",
            hostname = "bastion.com"
        )
        val host = Host(
            id = 2,
            nickname = "internal",
            protocol = "ssh",
            hostname = "internal.com",
            jumpHostId = 1
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = mapOf(1L to jumpHost)
        )

        assertTrue(result.configText.contains("ProxyJump bastion"))
    }

    @Test
    fun `export hostWithPubkey outputsIdentityFile`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com",
            pubkeyId = 10
        )
        val pubkey = Pubkey(
            id = 10,
            nickname = "my_key",
            type = "RSA",
            privateKey = null,
            publicKey = ByteArray(0),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 0
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = mapOf(10L to pubkey),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("IdentityFile ~/.ssh/my_key"))
    }

    @Test
    fun `export compression outputsYes`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com",
            compression = true
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("Compression yes"))
    }

    @Test
    fun `export wantSessionFalse outputsRequestTtyNo`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com",
            wantSession = false
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("RequestTTY no"))
    }

    @Test
    fun `export postLogin outputsRemoteCommand`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com",
            postLogin = "/usr/bin/htop"
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("RemoteCommand /usr/bin/htop"))
    }

    @Test
    fun `export connectbotSpecificFields addedAsComments`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com",
            color = "#FF0000",
            stayConnected = true,
            quickDisconnect = true,
            scrollbackLines = 500,
            useCtrlAltAsMetaKey = true
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("# ConnectBot-specific settings not exported:"))
        assertTrue(result.configText.contains("# Color: #FF0000"))
        assertTrue(result.configText.contains("# StayConnected: yes"))
        assertTrue(result.configText.contains("# QuickDisconnect: yes"))
        assertTrue(result.configText.contains("# ScrollbackLines: 500"))
        assertTrue(result.configText.contains("# UseCtrlAltAsMetaKey: yes"))
    }

    @Test
    fun `export multipleHosts outputsAll`() {
        val hosts = listOf(
            Host(id = 1, nickname = "server1", protocol = "ssh", hostname = "server1.com"),
            Host(id = 2, nickname = "server2", protocol = "ssh", hostname = "server2.com"),
            Host(id = 3, nickname = "server3", protocol = "ssh", hostname = "server3.com")
        )

        val result = exporter.export(
            hosts = hosts.map { it to emptyList() },
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertEquals(3, result.hostCount)
        assertTrue(result.configText.contains("Host server1"))
        assertTrue(result.configText.contains("Host server2"))
        assertTrue(result.configText.contains("Host server3"))
    }

    @Test
    fun `export nicknameWithSpaces quoted`() {
        val host = Host(
            id = 1,
            nickname = "my server",
            protocol = "ssh",
            hostname = "server.com"
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("Host \"my server\""))
    }

    @Test
    fun `export countsCorrect`() {
        val hosts = listOf(
            Host(id = 1, nickname = "ssh1", protocol = "ssh", hostname = "a.com"),
            Host(id = 2, nickname = "ssh2", protocol = "ssh", hostname = "b.com"),
            Host(id = 3, nickname = "telnet", protocol = "telnet", hostname = "c.com")
        )

        val result = exporter.export(
            hosts = hosts.map { it to emptyList() },
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertEquals(2, result.hostCount)
        assertEquals(1, result.skippedCount)
    }

    @Test
    fun `export pubkeyAuthentication outputsYesOrNo`() {
        val hostWithKeys = Host(
            id = 1,
            nickname = "withkeys",
            protocol = "ssh",
            hostname = "server.com",
            useKeys = true
        )
        val hostNoKeys = Host(
            id = 2,
            nickname = "nokeys",
            protocol = "ssh",
            hostname = "server2.com",
            useKeys = false
        )

        val result = exporter.export(
            hosts = listOf(hostWithKeys to emptyList(), hostNoKeys to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("PubkeyAuthentication yes"))
        assertTrue(result.configText.contains("PubkeyAuthentication no"))
    }

    @Test
    fun `export useAuthAgent outputsAddKeysToAgent`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com",
            useAuthAgent = "confirm"
        )

        val result = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("AddKeysToAgent confirm"))
    }

    @Test
    fun `export emptyHostList outputsHeader`() {
        val result = exporter.export(
            hosts = emptyList(),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertEquals(0, result.hostCount)
        assertTrue(result.configText.contains("# ConnectBot SSH Config Export"))
    }

    @Test
    fun `export portForwardWithNullDestAddr usesLocalhost`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.com"
        )
        val portForward = PortForward(
            id = 1,
            hostId = 1,
            nickname = "forward",
            type = "local",
            sourcePort = 8080,
            destAddr = null,
            destPort = 80
        )

        val result = exporter.export(
            hosts = listOf(host to listOf(portForward)),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        assertTrue(result.configText.contains("LocalForward 8080 localhost:80"))
    }
}
