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

import com.trilead.ssh2.crypto.PublicKeyUtils
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.TerminalManager
import org.connectbot.util.HostConstants
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.concurrent.ConcurrentHashMap

/**
 * Tests that the SSH auth agent offers Android Keystore-backed (biometric)
 * keys to forwarded agent clients (upstream issue connectbot#2212).
 */
class SSHAgentIdentitiesTest {

    private lateinit var manager: TerminalManager
    private lateinit var repository: PubkeyRepository
    private lateinit var loadedKeypairs: ConcurrentHashMap<String, TerminalManager.KeyHolder>

    @Before
    fun setUp() {
        manager = mock(TerminalManager::class.java)
        repository = mock(PubkeyRepository::class.java)
        loadedKeypairs = ConcurrentHashMap()
        `when`(manager.loadedKeypairs).thenReturn(loadedKeypairs)
        `when`(manager.pubkeyRepository).thenReturn(repository)
        `when`(repository.getKeystoreBackedBlocking()).thenReturn(emptyList())
    }

    private fun generateEcPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(256)
        return generator.generateKeyPair()
    }

    private fun keystorePubkey(nickname: String, pair: KeyPair) = Pubkey(
        id = 1L,
        nickname = nickname,
        type = "EC",
        privateKey = null,
        publicKey = pair.public.encoded,
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = 0L,
        storageType = KeyStorageType.ANDROID_KEYSTORE,
        allowBackup = false,
        keystoreAlias = "alias-$nickname",
    )

    private fun holderFor(pubkey: Pubkey, pair: KeyPair): TerminalManager.KeyHolder = TerminalManager.KeyHolder().apply {
        this.pubkey = pubkey
        this.pair = pair
        this.openSSHPubkey = PublicKeyUtils.extractPublicKeyBlob(pair.public)
    }

    @Test
    fun retrieveIdentities_usesPrecomputedBlob_forLoadedKeys() {
        // A loaded biometric key's opaque private half doesn't implement
        // ECPrivateKey, so identity listing must not depend on the private
        // key's JCA type.
        val pair = generateEcPair()
        val pubkey = keystorePubkey("biokey", pair)
        loadedKeypairs["biokey"] = holderFor(pubkey, pair)

        val identities = SSH().apply { manager = this@SSHAgentIdentitiesTest.manager }.retrieveIdentities()

        assertThat(identities).containsOnlyKeys("biokey")
        assertThat(identities["biokey"]).isEqualTo(PublicKeyUtils.extractPublicKeyBlob(pair.public))
    }

    @Test
    fun retrieveIdentities_includesKeystoreKeys_notCurrentlyLoaded() {
        // Biometric keys expire from the in-memory cache when their
        // authorization window closes; they must still be offered so a sign
        // request can trigger re-authentication.
        val pair = generateEcPair()
        `when`(repository.getKeystoreBackedBlocking()).thenReturn(listOf(keystorePubkey("expired", pair)))

        val identities = SSH().apply { manager = this@SSHAgentIdentitiesTest.manager }.retrieveIdentities()

        assertThat(identities).containsOnlyKeys("expired")
        assertThat(identities["expired"]).isEqualTo(PublicKeyUtils.extractPublicKeyBlob(pair.public))
    }

    @Test
    fun retrieveIdentities_prefersLoadedKey_overDatabaseCopy() {
        val pair = generateEcPair()
        val pubkey = keystorePubkey("biokey", pair)
        loadedKeypairs["biokey"] = holderFor(pubkey, pair)
        `when`(repository.getKeystoreBackedBlocking()).thenReturn(listOf(pubkey))

        val identities = SSH().apply { manager = this@SSHAgentIdentitiesTest.manager }.retrieveIdentities()

        assertThat(identities).containsOnlyKeys("biokey")
    }

    @Test
    fun getKeyPair_returnsNull_whenAgentForwardingDisabled() {
        val pair = generateEcPair()
        val blob = PublicKeyUtils.extractPublicKeyBlob(pair.public)
        `when`(manager.getKeyNickname(blob)).thenReturn("biokey")
        `when`(manager.getKey("biokey")).thenReturn(pair)

        val ssh = SSH().apply { manager = this@SSHAgentIdentitiesTest.manager }
        ssh.setUseAuthAgent(HostConstants.AUTHAGENT_NO)

        assertThat(ssh.getKeyPair(blob)).isNull()
    }

    @Test
    fun getKeyPair_returnsLoadedPair() {
        val pair = generateEcPair()
        val blob = PublicKeyUtils.extractPublicKeyBlob(pair.public)
        `when`(manager.getKeyNickname(blob)).thenReturn("biokey")
        `when`(manager.getKey("biokey")).thenReturn(pair)

        val ssh = SSH().apply { manager = this@SSHAgentIdentitiesTest.manager }
        ssh.setUseAuthAgent(HostConstants.AUTHAGENT_YES)

        assertThat(ssh.getKeyPair(blob)).isSameAs(pair)
    }

    @Test
    fun getKeyPair_returnsNull_forUnknownKey() {
        val pair = generateEcPair()
        val blob = PublicKeyUtils.extractPublicKeyBlob(pair.public)

        val ssh = SSH().apply { manager = this@SSHAgentIdentitiesTest.manager }
        ssh.setUseAuthAgent(HostConstants.AUTHAGENT_YES)

        assertThat(ssh.getKeyPair(blob)).isNull()
    }
}
