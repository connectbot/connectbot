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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for SSH config export and import.
 */
class SshConfigRoundTripTest {

    private val exporter = SshConfigExporter()
    private val parser = SshConfigParser()

    @Test
    fun `roundTrip basicHost preservesFields`() {
        val originalHost = Host(
            id = 1,
            nickname = "testserver",
            protocol = "ssh",
            hostname = "test.example.com",
            username = "testuser",
            port = 2222
        )

        // Export to SSH config
        val exportResult = exporter.export(
            hosts = listOf(originalHost to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        // Parse the exported config
        val (parsedHosts, warnings) = parser.parse(exportResult.configText)

        // Verify
        assertEquals(1, parsedHosts.size)
        val parsed = parsedHosts[0]
        assertEquals("testserver", parsed.hostPattern)
        assertEquals("test.example.com", parsed.hostname)
        assertEquals("testuser", parsed.user)
        assertEquals(2222, parsed.port)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `roundTrip hostWithPortForwards preservesForwards`() {
        val host = Host(
            id = 1,
            nickname = "server",
            protocol = "ssh",
            hostname = "server.example.com"
        )
        val portForwards = listOf(
            PortForward(
                id = 1,
                hostId = 1,
                nickname = "local",
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
                destAddr = "remotehost",
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

        // Export
        val exportResult = exporter.export(
            hosts = listOf(host to portForwards),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        // Parse
        val (parsedHosts, _) = parser.parse(exportResult.configText)

        // Verify
        assertEquals(1, parsedHosts.size)
        val parsed = parsedHosts[0]

        assertEquals(1, parsed.localForwards.size)
        assertEquals(8080, parsed.localForwards[0].sourcePort)
        assertEquals("localhost", parsed.localForwards[0].destHost)
        assertEquals(80, parsed.localForwards[0].destPort)

        assertEquals(1, parsed.remoteForwards.size)
        assertEquals(9090, parsed.remoteForwards[0].sourcePort)
        assertEquals("remotehost", parsed.remoteForwards[0].destHost)
        assertEquals(9000, parsed.remoteForwards[0].destPort)

        assertEquals(1, parsed.dynamicForwards.size)
        assertEquals(1080, parsed.dynamicForwards[0])
    }

    @Test
    fun `roundTrip hostWithAllOptions preservesOptions`() {
        val host = Host(
            id = 1,
            nickname = "fullserver",
            protocol = "ssh",
            hostname = "full.example.com",
            username = "admin",
            port = 22,
            useKeys = true,
            useAuthAgent = "yes",
            compression = true,
            wantSession = false,
            postLogin = "/usr/bin/htop"
        )

        // Export
        val exportResult = exporter.export(
            hosts = listOf(host to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        // Parse
        val (parsedHosts, _) = parser.parse(exportResult.configText)

        // Verify
        assertEquals(1, parsedHosts.size)
        val parsed = parsedHosts[0]

        assertEquals("fullserver", parsed.hostPattern)
        assertEquals("full.example.com", parsed.hostname)
        assertEquals("admin", parsed.user)
        assertEquals(true, parsed.pubkeyAuthentication)
        assertEquals("yes", parsed.addKeysToAgent)
        assertEquals(true, parsed.compression)
        assertEquals(false, parsed.requestTty)
        assertEquals("/usr/bin/htop", parsed.remoteCommand)
    }

    @Test
    fun `roundTrip multipleHosts preservesAll`() {
        val hosts = listOf(
            Host(id = 1, nickname = "server1", protocol = "ssh", hostname = "s1.example.com", username = "user1"),
            Host(id = 2, nickname = "server2", protocol = "ssh", hostname = "s2.example.com", username = "user2"),
            Host(id = 3, nickname = "server3", protocol = "ssh", hostname = "s3.example.com", username = "user3")
        )

        // Export
        val exportResult = exporter.export(
            hosts = hosts.map { it to emptyList() },
            pubkeys = emptyMap(),
            jumpHosts = emptyMap()
        )

        // Parse
        val (parsedHosts, _) = parser.parse(exportResult.configText)

        // Verify
        assertEquals(3, parsedHosts.size)
        assertEquals("server1", parsedHosts[0].hostPattern)
        assertEquals("server2", parsedHosts[1].hostPattern)
        assertEquals("server3", parsedHosts[2].hostPattern)
    }

    @Test
    fun `roundTrip hostWithJumpHost preservesProxyJump`() {
        val jumpHost = Host(
            id = 1,
            nickname = "bastion",
            protocol = "ssh",
            hostname = "bastion.example.com"
        )
        val internalHost = Host(
            id = 2,
            nickname = "internal",
            protocol = "ssh",
            hostname = "internal.example.com",
            jumpHostId = 1
        )

        // Export
        val exportResult = exporter.export(
            hosts = listOf(internalHost to emptyList()),
            pubkeys = emptyMap(),
            jumpHosts = mapOf(1L to jumpHost)
        )

        // Parse
        val (parsedHosts, _) = parser.parse(exportResult.configText)

        // Verify
        assertEquals(1, parsedHosts.size)
        assertEquals("bastion", parsedHosts[0].proxyJump)
    }
}
