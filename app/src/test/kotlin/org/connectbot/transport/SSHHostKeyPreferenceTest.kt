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
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.HostCryptoConfig
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.SshCryptoPolicy
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.UUID

class SSHHostKeyPreferenceTest {
    private val repository = mock(HostRepository::class.java)
    private val manager = mock(TerminalManager::class.java).also {
        `when`(it.hostRepository).thenReturn(repository)
    }
    private val host = Host(id = UUID(0, 42))
    private val ssh = SSH(host, null, manager)
    private val supported = "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256"

    @Test
    fun prefersKnownAlgorithmsWithoutDisablingHostKeyUpdates() {
        `when`(repository.getHostKeyAlgorithmsForHostBlocking(host.id))
            .thenReturn(listOf("rsa-sha2-512", "rsa-sha2-256", "ssh-rsa"))

        assertThat(ssh.preferredHostKeyAlgorithms(host, supported))
            .isEqualTo("rsa-sha2-512,rsa-sha2-256,ssh-ed25519,ecdsa-sha2-nistp256")
    }

    @Test
    fun unknownHostKeepsLibraryPreference() {
        assertThat(ssh.preferredHostKeyAlgorithms(host, supported)).isEqualTo(supported)
    }

    @Test
    fun storedDisabledAlgorithmsAreNotEnabled() {
        `when`(repository.getHostKeyAlgorithmsForHostBlocking(host.id)).thenReturn(listOf("ssh-dss", "ssh-rsa"))

        assertThat(ssh.preferredHostKeyAlgorithms(host, supported)).isEqualTo(supported)
    }

    @Test
    fun usesEachJumpHostsOwnKnownAlgorithms() {
        val jump = Host(id = UUID(0, 99))
        `when`(repository.getHostKeyAlgorithmsForHostBlocking(jump.id)).thenReturn(listOf("ecdsa-sha2-nistp256"))

        assertThat(ssh.preferredHostKeyAlgorithms(jump, supported))
            .isEqualTo("ecdsa-sha2-nistp256,ssh-ed25519,rsa-sha2-512,rsa-sha2-256")
        assertThat(ssh.preferredHostKeyAlgorithms(host, supported)).isEqualTo(supported)
    }

    private fun policyOrder(host: Host): String = SSH::class.java.getDeclaredMethod("policyHostKeyOrder", Host::class.java, SshCryptoPolicy::class.java).apply { isAccessible = true }.invoke(ssh, host, host.crypto.resolve()) as String

    @Test
    fun exactPolicyOrderTakesPrecedenceOverKnownKeys() {
        `when`(repository.getHostKeyAlgorithmsForHostBlocking(host.id)).thenReturn(listOf("rsa-sha2-256"))
        val custom = host.copy(crypto = HostCryptoConfig(preset = "CUSTOM", hostKey = "ssh-ed25519,rsa-sha2-256"))
        assertThat(policyOrder(custom)).isEqualTo("ssh-ed25519,rsa-sha2-256")
    }

    @Test
    fun deltaPolicyKeepsExplicitEndsWhilePreferringKnownInheritedKeys() {
        `when`(repository.getHostKeyAlgorithmsForHostBlocking(host.id)).thenReturn(listOf("rsa-sha2-512"))
        val custom = host.copy(crypto = HostCryptoConfig(preset = "CUSTOM", hostKey = "^ssh-ed25519,+rsa-sha2-256,-ecdsa-sha2-nistp256,+unavailable"))
        val order = policyOrder(custom).split(',')
        assertThat(order.first()).isEqualTo("ssh-ed25519")
        assertThat(order[1]).isEqualTo("rsa-sha2-512")
        assertThat(order.last()).isEqualTo("rsa-sha2-256")
        assertThat(order).doesNotContain("ecdsa-sha2-nistp256", "unavailable")
        assertThat(order).doesNotHaveDuplicates()
    }

    @Test
    fun inheritedPolicyUsesKnownKeysAndIgnoresInactiveCustomOverrides() {
        `when`(repository.getHostKeyAlgorithmsForHostBlocking(host.id)).thenReturn(listOf("rsa-sha2-512"))
        listOf(HostCryptoConfig(), HostCryptoConfig(preset = "CUSTOM", hostKey = ""), HostCryptoConfig(hostKey = "^ssh-ed25519")).forEach {
            assertThat(policyOrder(host.copy(crypto = it)).split(',').first()).isEqualTo("rsa-sha2-512")
        }
    }
}
