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
import com.trilead.ssh2.auth.SignatureProxy
import com.trilead.ssh2.crypto.PublicKeyUtils
import com.trilead.ssh2.signature.Ed25519Verify
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.TerminalManager
import org.connectbot.util.Ed25519SignatureProxy
import org.connectbot.util.HostConstants
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.same
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.util.concurrent.ConcurrentHashMap

class SSHJumpHostAuthTest {
    private lateinit var connection: Connection
    private lateinit var manager: TerminalManager
    private lateinit var repository: PubkeyRepository
    private lateinit var loadedKeypairs: ConcurrentHashMap<String, TerminalManager.KeyHolder>

    @Before
    fun setUp() {
        connection = mock(Connection::class.java)
        manager = mock(TerminalManager::class.java)
        repository = mock(PubkeyRepository::class.java)
        loadedKeypairs = ConcurrentHashMap()

        `when`(connection.authenticateWithNone("alice")).thenReturn(false)
        `when`(connection.isAuthMethodAvailable("alice", "publickey")).thenReturn(true)
        `when`(manager.loadedKeypairs).thenReturn(loadedKeypairs)
        `when`(manager.pubkeyRepository).thenReturn(repository)
    }

    @Test
    fun authenticateJumpHost_withAnyEd25519KeystoreKey_usesProxySigner() {
        val pair = ed25519KeystoreLikePair()
        loadedKeypairs["jump-ed25519"] = holderFor(pair)
        stubProxyAuthSucceeds()

        val authenticated = ssh().authenticateJumpHost(
            connection,
            jumpHost(pubkeyId = HostConstants.PUBKEYID_ANY),
        )

        assertThat(authenticated).isTrue()
        verifyProxyAuth(pair)
    }

    @Test
    fun authenticateJumpHost_withSpecificEd25519KeystoreKey_usesProxySigner() {
        val pair = ed25519KeystoreLikePair()
        val pubkey = pubkey("jump-ed25519", id = 7L, pair = pair)
        `when`(repository.getByIdBlocking(7L)).thenReturn(pubkey)
        `when`(manager.isKeyLoaded("jump-ed25519")).thenReturn(true)
        `when`(manager.getKey("jump-ed25519")).thenReturn(pair)
        stubProxyAuthSucceeds()

        val authenticated = ssh().authenticateJumpHost(
            connection,
            jumpHost(pubkeyId = 7L),
        )

        assertThat(authenticated).isTrue()
        verifyProxyAuth(pair)
    }

    private fun ssh(): SSH = SSH().apply {
        setManager(manager)
    }

    private fun jumpHost(pubkeyId: Long): Host = Host(
        nickname = "jump",
        username = "alice",
        hostname = "jump.example.com",
        pubkeyId = pubkeyId,
    )

    private fun ed25519KeystoreLikePair(): KeyPair {
        val provider = Security.getProviders("Signature.Ed25519").first()
        val generated = KeyPairGenerator.getInstance("Ed25519", provider).generateKeyPair()
        return KeyPair(Ed25519Verify.convertPublicKey(generated.public), generated.private)
    }

    private fun holderFor(pair: KeyPair): TerminalManager.KeyHolder = TerminalManager.KeyHolder().apply {
        this.pair = pair
        this.openSSHPubkey = PublicKeyUtils.extractPublicKeyBlob(pair.public)
    }

    private fun pubkey(nickname: String, id: Long, pair: KeyPair): Pubkey = Pubkey(
        id = id,
        nickname = nickname,
        type = "Ed25519",
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

    private fun stubProxyAuthSucceeds() {
        `when`(connection.authenticateWithPublicKey(eq("alice"), any(SignatureProxy::class.java))).thenReturn(true)
    }

    private fun verifyProxyAuth(pair: KeyPair) {
        val captor = ArgumentCaptor.forClass(SignatureProxy::class.java)
        verify(connection).authenticateWithPublicKey(eq("alice"), captor.capture())
        verify(connection, never()).authenticateWithPublicKey(eq("alice"), same(pair))
        assertThat(captor.value).isInstanceOf(Ed25519SignatureProxy::class.java)
    }
}
