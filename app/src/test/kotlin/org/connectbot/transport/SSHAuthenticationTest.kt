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
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostConstants
import org.junit.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.security.KeyPair
import java.security.KeyPairGenerator

class SSHAuthenticationTest {
    private val manager = mock(TerminalManager::class.java)
    private val bridge = mock(TerminalBridge::class.java)
    private val connection = mock(Connection::class.java)
    private val repository = mock(PubkeyRepository::class.java)
    private val loadedKeys = linkedMapOf<String, TerminalManager.KeyHolder>()

    private fun ssh(pubkeyId: Long = HostConstants.PUBKEYID_ANY): SSH {
        `when`(manager.loadedKeypairs).thenReturn(loadedKeys)
        `when`(manager.pubkeyRepository).thenReturn(repository)
        `when`(connection.getRemainingAuthMethods("alice")).thenReturn(arrayOf("publickey"))
        return SSH(Host(username = "alice", nickname = "target", pubkeyId = pubkeyId), bridge, manager).apply {
            setConnectionForTesting(connection)
        }
    }

    @Test
    fun anyKey_offersUnlockedKeysInNicknameOrder() {
        load("zulu", secondKey)
        load("alpha", firstKey)

        assertThat(ssh().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)

        val order = inOrder(connection)
        order.verify(connection).probePublicKey("alice", firstKey.public)
        order.verify(connection).probePublicKey("alice", secondKey.public)
        verify(repository, never()).getByIdBlocking(anyLong())
    }

    @Test
    fun specificKey_doesNotOfferOtherUnlockedKeys() {
        val selected = load("selected", firstKey)
        load("other", secondKey)
        `when`(repository.getByIdBlocking(42)).thenReturn(selected)
        `when`(manager.isKeyLoaded("selected")).thenReturn(true)
        `when`(manager.getKey("selected")).thenReturn(firstKey)

        assertThat(ssh(42).authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)

        verify(connection).probePublicKey("alice", firstKey.public)
        verify(connection, never()).probePublicKey("alice", secondKey.public)
    }

    @Test
    fun neverKey_doesNotOfferAnyUnlockedKeys() {
        load("first", firstKey)
        assertThat(ssh(HostConstants.PUBKEYID_NEVER).authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, never()).probePublicKey("alice", firstKey.public)
        verify(repository, never()).getByIdBlocking(anyLong())
    }

    @Test
    fun keyUnlockedDuringAuthentication_isNotAddedToSnapshot() {
        load("first", firstKey)
        `when`(connection.authenticateWithNone("alice")).thenAnswer {
            load("second", secondKey)
            false
        }
        assertThat(ssh().authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection).probePublicKey("alice", firstKey.public)
        verify(connection, never()).probePublicKey("alice", secondKey.public)
    }

    @Test
    fun jumpHostAndTarget_haveIndependentKeyAttempts() {
        load("first", firstKey)
        val transport = ssh()
        assertThat(transport.authenticateJumpHost(connection, Host(username = "alice", nickname = "jump"))).isFalse()
        assertThat(transport.authenticate()).isEqualTo(AuthenticationOutcome.Exhausted)
        verify(connection, times(2)).probePublicKey("alice", firstKey.public)
        verify(connection, times(2)).authenticateWithNone("alice")
        verify(bridge).dismissAuthBannersFrom("jump")
        verify(bridge, atLeastOnce()).dismissAuthBannersFrom("target")
    }

    private fun load(nickname: String, pair: KeyPair): Pubkey {
        val pubkey = Pubkey(
            id = 42,
            nickname = nickname,
            type = "EC",
            publicKey = pair.public.encoded,
            privateKey = pair.private.encoded,
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 0,
        )
        loadedKeys[nickname] = TerminalManager.KeyHolder().apply {
            this.pubkey = pubkey
            this.pair = pair
        }
        return pubkey
    }

    companion object {
        private val generator = KeyPairGenerator.getInstance("EC").apply { initialize(256) }
        private val firstKey = generator.generateKeyPair()
        private val secondKey = generator.generateKeyPair()
    }
}
