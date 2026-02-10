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

import org.assertj.core.api.Assertions.assertThat
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshClientConfig
import org.junit.Test
import org.mockito.Mockito.mock

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
            RsaSignatureAlgorithmPolicy.prepareForAuthentication(
                SshClient(
                    SshClientConfig {
                        host = "example.com"
                        hostKeyVerifier = mock(HostKeyVerifier::class.java)
                    },
                ),
            ),
        ).isFalse()
    }

    private fun connectionWithAcceptedAlgorithms(vararg algorithms: String): SshClient {
        val connection = mock(Class.forName("org.connectbot.sshlib.client.SshConnection"))
        connection.javaClass.getDeclaredField("serverSigAlgs").apply {
            isAccessible = true
            set(connection, algorithms.toSet().takeIf { it.isNotEmpty() })
        }
        return SshClient(
            SshClientConfig {
                host = "example.com"
                hostKeyVerifier = mock(HostKeyVerifier::class.java)
            },
        ).apply client@{
            SshClient::class.java.getDeclaredField("connection").apply {
                isAccessible = true
                set(this@client, connection)
            }
        }
    }

    private fun SshClient.acceptedSignatureAlgorithms(): Set<String> {
        val connection = SshClient::class.java.getDeclaredField("connection").run {
            isAccessible = true
            get(this@acceptedSignatureAlgorithms)
        }
        return connection.javaClass.getDeclaredField("serverSigAlgs").run {
            isAccessible = true
            get(connection) as Set<String>
        }
    }
}
