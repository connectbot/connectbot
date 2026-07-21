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
import com.trilead.ssh2.ExtensionInfo
import com.trilead.ssh2.packets.PacketExtInfo
import com.trilead.ssh2.transport.TransportManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RsaSignatureAlgorithmPolicyTest {
    @Test
    fun prepareForAuthentication_serverDoesNotAdvertiseAlgorithms_prefersRsaSha2() {
        val connection = connectionWithAcceptedAlgorithms()

        assertThat(RsaSignatureAlgorithmPolicy.prepareForAuthentication(connection)).isTrue()

        assertThat(connection.acceptedSignatureAlgorithms())
            .containsExactlyInAnyOrderElementsOf(RsaSignatureAlgorithmPolicy.rsaSha2Algorithms)
    }

    @Test
    fun prepareForAuthentication_serverAdvertisesRsaSha2_preservesAlgorithms() {
        val connection = connectionWithAcceptedAlgorithms("ssh-ed25519", "rsa-sha2-256")

        assertThat(RsaSignatureAlgorithmPolicy.prepareForAuthentication(connection)).isTrue()

        assertThat(connection.acceptedSignatureAlgorithms())
            .containsExactlyInAnyOrder("ssh-ed25519", "rsa-sha2-256")
    }

    @Test
    fun prepareForAuthentication_serverAdvertisesOnlyLegacyRsa_rejectsFallback() {
        val connection = connectionWithAcceptedAlgorithms("ssh-rsa")

        assertThat(RsaSignatureAlgorithmPolicy.prepareForAuthentication(connection)).isFalse()

        assertThat(connection.acceptedSignatureAlgorithms()).containsExactly("ssh-rsa")
    }

    @Test
    fun prepareForAuthentication_connectionIsNotInitialized_returnsFalse() {
        assertThat(
            RsaSignatureAlgorithmPolicy.prepareForAuthentication(Connection("example.com")),
        ).isFalse()
    }

    private fun connectionWithAcceptedAlgorithms(vararg algorithms: String): Connection {
        val transportManager = TransportManager("example.com", 22)
        transportManager.setAcceptedSignatureAlgorithms(*algorithms)
        return Connection("example.com").apply {
            Connection::class.java.getDeclaredField("tm").run {
                isAccessible = true
                set(this@apply, transportManager)
            }
        }
    }

    private fun Connection.acceptedSignatureAlgorithms(): Set<String> = transportManager().extensionInfo.signatureAlgorithmsAccepted

    private fun Connection.transportManager(): TransportManager = Connection::class.java.getDeclaredField("tm").run {
        isAccessible = true
        get(this@transportManager) as TransportManager
    }

    private fun TransportManager.setAcceptedSignatureAlgorithms(vararg algorithms: String) {
        val extensionInfo = if (algorithms.isEmpty()) {
            ExtensionInfo.noExtInfoSeen()
        } else {
            ExtensionInfo.fromPacketExtInfo(
                PacketExtInfo(mapOf("server-sig-algs" to algorithms.joinToString(","))),
            )
        }
        TransportManager::class.java.getDeclaredField("extensionInfo").run {
            isAccessible = true
            set(this@setAcceptedSignatureAlgorithms, extensionInfo)
        }
    }
}
