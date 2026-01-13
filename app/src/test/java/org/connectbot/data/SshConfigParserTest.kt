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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for SshConfigParser.
 */
class SshConfigParserTest {

    private val parser = SshConfigParser()

    @Test
    fun `parse basicHostBlock extractsAllFields`() {
        val config = """
            Host myserver
                HostName example.com
                User admin
                Port 2222
        """.trimIndent()

        val (hosts, warnings) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("myserver", hosts[0].hostPattern)
        assertEquals("example.com", hosts[0].hostname)
        assertEquals("admin", hosts[0].user)
        assertEquals(2222, hosts[0].port)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `parse multipleHosts returnsAllHosts`() {
        val config = """
            Host server1
                HostName server1.example.com
                User user1

            Host server2
                HostName server2.example.com
                User user2
                Port 22
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(2, hosts.size)
        assertEquals("server1", hosts[0].hostPattern)
        assertEquals("server2", hosts[1].hostPattern)
    }

    @Test
    fun `parse portForwards extractsLocalRemoteDynamic`() {
        val config = """
            Host server
                HostName server.com
                LocalForward 8080 localhost:80
                RemoteForward 9090 localhost:9000
                DynamicForward 1080
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals(1, hosts[0].localForwards.size)
        assertEquals(8080, hosts[0].localForwards[0].sourcePort)
        assertEquals("localhost", hosts[0].localForwards[0].destHost)
        assertEquals(80, hosts[0].localForwards[0].destPort)

        assertEquals(1, hosts[0].remoteForwards.size)
        assertEquals(9090, hosts[0].remoteForwards[0].sourcePort)

        assertEquals(1, hosts[0].dynamicForwards.size)
        assertEquals(1080, hosts[0].dynamicForwards[0])
    }

    @Test
    fun `parse proxyJump extractsJumpHostName`() {
        val config = """
            Host internal
                HostName internal.example.com
                ProxyJump bastion
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("bastion", hosts[0].proxyJump)
    }

    @Test
    fun `parse compression parsesYesNo`() {
        val config = """
            Host compressed
                HostName compressed.example.com
                Compression yes

            Host uncompressed
                HostName uncompressed.example.com
                Compression no
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(2, hosts.size)
        assertEquals(true, hosts[0].compression)
        assertEquals(false, hosts[1].compression)
    }

    @Test
    fun `parse requestTty parsesYesNo`() {
        val config = """
            Host withtty
                HostName withtty.example.com
                RequestTTY yes

            Host notty
                HostName notty.example.com
                RequestTTY no
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(2, hosts.size)
        assertEquals(true, hosts[0].requestTty)
        assertEquals(false, hosts[1].requestTty)
    }

    @Test
    fun `parse identityFile extractsKeyName`() {
        val config = """
            Host server
                HostName server.example.com
                IdentityFile ~/.ssh/id_rsa
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("id_rsa", hosts[0].identityFile)
    }

    @Test
    fun `parse identityFile extractsKeyNameFromFullPath`() {
        val config = """
            Host server
                HostName server.example.com
                IdentityFile /home/user/.ssh/my_special_key
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("my_special_key", hosts[0].identityFile)
    }

    @Test
    fun `parse wildcardHost skipsAndReturnsNull`() {
        val config = """
            Host *
                ServerAliveInterval 60

            Host myserver
                HostName example.com
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("myserver", hosts[0].hostPattern)
    }

    @Test
    fun `parse wildcardPattern skips`() {
        val config = """
            Host *.example.com
                User defaultuser

            Host prod?
                User produser

            Host realserver
                HostName real.example.com
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("realserver", hosts[0].hostPattern)
    }

    @Test
    fun `parse proxyCommand generatesWarning`() {
        val config = """
            Host server
                HostName server.example.com
                ProxyCommand ssh -W %h:%p bastion
        """.trimIndent()

        val (hosts, warnings) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals(1, warnings.size)
        assertEquals("ProxyCommand", warnings[0].directive)
        assertEquals(SshConfigWarningType.UNSUPPORTED_DIRECTIVE, warnings[0].type)
    }

    @Test
    fun `parse matchBlock generatesWarningAndSkips`() {
        val config = """
            Host server
                HostName server.example.com

            Match host *.example.com
                User matchuser

            Host other
                HostName other.example.com
        """.trimIndent()

        val (hosts, warnings) = parser.parse(config)

        assertEquals(2, hosts.size)
        assertEquals("server", hosts[0].hostPattern)
        assertEquals("other", hosts[1].hostPattern)
        assertTrue(warnings.any { it.type == SshConfigWarningType.MATCH_BLOCK_IGNORED })
    }

    @Test
    fun `parse includeDirective generatesWarning`() {
        val config = """
            Include ~/.ssh/config.d/*

            Host server
                HostName server.example.com
        """.trimIndent()

        val (hosts, warnings) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertTrue(warnings.any { it.type == SshConfigWarningType.INCLUDE_IGNORED })
    }

    @Test
    fun `parse unsupportedDirectives generatesWarnings`() {
        val config = """
            Host server
                HostName server.example.com
                ServerAliveInterval 60
                Ciphers aes256-ctr
                MACs hmac-sha2-256
        """.trimIndent()

        val (hosts, warnings) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals(3, warnings.size)
        assertTrue(warnings.any { it.directive == "ServerAliveInterval" })
        assertTrue(warnings.any { it.directive == "Ciphers" })
        assertTrue(warnings.any { it.directive == "MACs" })
    }

    @Test
    fun `parse emptyLines ignored`() {
        val config = """

            Host server

                HostName server.example.com

                User admin

        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("server.example.com", hosts[0].hostname)
        assertEquals("admin", hosts[0].user)
    }

    @Test
    fun `parse comments ignored`() {
        val config = """
            # This is a comment
            Host server
                # Another comment
                HostName server.example.com
                User admin # inline comment not supported by SSH but we handle it
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("server.example.com", hosts[0].hostname)
    }

    @Test
    fun `parse directiveWithEquals accepted`() {
        val config = """
            Host server
                HostName=server.example.com
                Port=2222
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("server.example.com", hosts[0].hostname)
        assertEquals(2222, hosts[0].port)
    }

    @Test
    fun `parse directiveWithSpace accepted`() {
        val config = """
            Host server
                HostName server.example.com
                Port 2222
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("server.example.com", hosts[0].hostname)
        assertEquals(2222, hosts[0].port)
    }

    @Test
    fun `parse invalidPortValue generatesWarning`() {
        val config = """
            Host server
                HostName server.example.com
                Port abc
        """.trimIndent()

        val (hosts, warnings) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertNull(hosts[0].port)
        assertEquals(1, warnings.size)
        assertEquals(SshConfigWarningType.INVALID_VALUE, warnings[0].type)
    }

    @Test
    fun `parse portOutOfRange generatesWarning`() {
        val config = """
            Host server
                HostName server.example.com
                Port 99999
        """.trimIndent()

        val (hosts, warnings) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertNull(hosts[0].port)
        assertEquals(1, warnings.size)
        assertEquals(SshConfigWarningType.INVALID_VALUE, warnings[0].type)
    }

    @Test
    fun `parse localForwardFormats parsesVariations`() {
        val config = """
            Host server
                HostName server.example.com
                LocalForward 8080 localhost:80
                LocalForward 9090 remotehost 9000
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals(2, hosts[0].localForwards.size)

        // Format: "8080 localhost:80"
        assertEquals(8080, hosts[0].localForwards[0].sourcePort)
        assertEquals("localhost", hosts[0].localForwards[0].destHost)
        assertEquals(80, hosts[0].localForwards[0].destPort)

        // Format: "9090 remotehost 9000"
        assertEquals(9090, hosts[0].localForwards[1].sourcePort)
        assertEquals("remotehost", hosts[0].localForwards[1].destHost)
        assertEquals(9000, hosts[0].localForwards[1].destPort)
    }

    @Test
    fun `parse localForwardWithBindAddress parsesPort`() {
        val config = """
            Host server
                HostName server.example.com
                LocalForward *:8080 localhost:80
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals(1, hosts[0].localForwards.size)
        assertEquals(8080, hosts[0].localForwards[0].sourcePort)
    }

    @Test
    fun `parse pubkeyAuthentication parsesYesNo`() {
        val config = """
            Host withkey
                HostName withkey.example.com
                PubkeyAuthentication yes

            Host nokey
                HostName nokey.example.com
                PubkeyAuthentication no
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(2, hosts.size)
        assertEquals(true, hosts[0].pubkeyAuthentication)
        assertEquals(false, hosts[1].pubkeyAuthentication)
    }

    @Test
    fun `parse addKeysToAgent extractsValue`() {
        val config = """
            Host server
                HostName server.example.com
                AddKeysToAgent yes
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("yes", hosts[0].addKeysToAgent)
    }

    @Test
    fun `parse remoteCommand extractsValue`() {
        val config = """
            Host server
                HostName server.example.com
                RemoteCommand /usr/bin/htop
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("/usr/bin/htop", hosts[0].remoteCommand)
    }

    @Test
    fun `parse emptyConfig returnsEmptyList`() {
        val config = ""

        val (hosts, warnings) = parser.parse(config)

        assertTrue(hosts.isEmpty())
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `parse onlyComments returnsEmptyList`() {
        val config = """
            # Comment 1
            # Comment 2
            # Comment 3
        """.trimIndent()

        val (hosts, warnings) = parser.parse(config)

        assertTrue(hosts.isEmpty())
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `parse caseInsensitiveDirectives works`() {
        val config = """
            HOST server
                HOSTNAME server.example.com
                USER admin
                PORT 22
        """.trimIndent()

        val (hosts, _) = parser.parse(config)

        assertEquals(1, hosts.size)
        assertEquals("server.example.com", hosts[0].hostname)
        assertEquals("admin", hosts[0].user)
        assertEquals(22, hosts[0].port)
    }
}
