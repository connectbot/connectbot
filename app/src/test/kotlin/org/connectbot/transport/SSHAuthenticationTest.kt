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

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.PromptManager
import org.connectbot.service.PromptResponse
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthPublicKey
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshSigning
import org.connectbot.util.HostConstants
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.security.KeyPair
import java.security.KeyPairGenerator

class SSHAuthenticationTest {
    private val manager = mock(TerminalManager::class.java)
    private val bridge = mock(TerminalBridge::class.java)
    private val client = mock(SshClient::class.java)
    private val repository = mock(PubkeyRepository::class.java)
    private val loadedKeys = linkedMapOf<String, TerminalManager.KeyHolder>()
    private lateinit var handler: AuthHandler
    private val offered = mutableListOf<List<AuthPublicKey>>()
    private var beforeKeyRequest: () -> Unit = {}

    private suspend fun ssh(pubkeyId: Long = HostConstants.PUBKEYID_ANY): SSH {
        `when`(manager.loadedKeypairs).thenReturn(loadedKeys)
        `when`(manager.pubkeyRepository).thenReturn(repository)
        val fallback = mock(AuthHandler::class.java)
        `when`(client.authenticate(eq("alice") ?: "alice", any(AuthHandler::class.java) ?: fallback)).thenAnswer {
            handler = it.getArgument(1)
            beforeKeyRequest()
            offered.add(runBlocking { it.getArgument<AuthHandler>(1).onPublicKeysNeeded() })
            AuthResult.Failure(emptySet())
        }
        return SSH(Host(username = "alice", nickname = "target", pubkeyId = pubkeyId), bridge, manager).apply {
            SSH::class.java.getDeclaredField("client").apply { isAccessible = true }.set(this, this@SSHAuthenticationTest.client)
        }
    }

    private fun assertOffered(vararg pairs: KeyPair) {
        assertThat(offered.last().map { it.publicKeyBlob.toList() })
            .containsExactlyElementsOf(pairs.map { SshSigning.encodePublicKey(it).publicKeyBlob.toList() })
    }

    @Test
    fun anyKey_offersUnlockedKeysInNicknameOrder() = runTest {
        load("zulu", secondKey)
        load("alpha", firstKey)
        ssh().authenticate()
        assertOffered(firstKey, secondKey)
        verify(repository, never()).getByIdBlocking(anyLong())
    }

    @Test
    fun specificKey_doesNotOfferOtherUnlockedKeys() = runTest {
        val selected = load("selected", firstKey)
        load("other", secondKey)
        `when`(repository.getByIdBlocking(42)).thenReturn(selected)
        `when`(manager.isKeyLoaded("selected")).thenReturn(true)
        `when`(manager.getKey("selected")).thenReturn(firstKey)
        ssh(42).authenticate()
        assertOffered(firstKey)
    }

    @Test
    fun neverKey_doesNotOfferAnyUnlockedKeys() = runTest {
        load("first", firstKey)
        ssh(HostConstants.PUBKEYID_NEVER).authenticate()
        assertOffered()
        verify(repository, never()).getByIdBlocking(anyLong())
    }

    @Test
    fun keyUnlockedDuringAuthentication_isNotAddedToSnapshot() = runTest {
        load("first", firstKey)
        beforeKeyRequest = { load("second", secondKey) }
        ssh().authenticate()
        assertOffered(firstKey)
    }

    @Test
    fun jumpHostAndTarget_haveIndependentKeyAttempts() = runTest {
        load("first", firstKey)
        val transport = ssh()
        assertThat(transport.authenticateJumpHost(client, Host(username = "alice", nickname = "jump"))).isFalse()
        assertOffered(firstKey)
        transport.authenticate()
        assertOffered(firstKey)
        assertThat(offered).hasSize(2)
        verify(bridge).dismissAuthBannersFrom("jump")
        verify(bridge, atLeastOnce()).dismissAuthBannersFrom("target")
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun jumpAndTarget_requireApprovalOnlyWhenSigning() = runTest {
        load("first", firstKey, confirmation = true)
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        val transport = ssh()
        transport.authenticateJumpHost(client, Host(username = "alice", nickname = "jump"))
        assertThat(prompts.promptState.value).isNull()
        val key = offered.last().single()
        val signature = async { handler.onSignatureRequest(key, byteArrayOf(1)) }
        runCurrent()
        assertThat(prompts.promptState.value).isNotNull()
        prompts.respond(PromptResponse.BooleanResponse(false))
        assertThat(signature.await()).isNull()
        // Refusal survives another callback instead of asking on every retry.
        assertThat(handler.onSignatureRequest(key, byteArrayOf(1))).isNull()
        assertThat(prompts.promptState.value).isNull()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun approvedKey_isNotPromptedAgainAndUnloadedKeyCannotSign() = runTest {
        load("first", firstKey, confirmation = true)
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        ssh().authenticate()
        val key = offered.last().single()
        assertThat(prompts.promptState.value).isNull()
        val signature = async { handler.onSignatureRequest(key, byteArrayOf(1)) }
        runCurrent()
        prompts.respond(PromptResponse.BooleanResponse(true))
        assertThat(signature.await()).isNotNull()
        assertThat(handler.onSignatureRequest(key, byteArrayOf(2))).isNotNull()
        assertThat(prompts.promptState.value).isNull()
        loadedKeys.clear()
        assertThat(handler.onSignatureRequest(key, byteArrayOf(3))).isNull()
    }

    private fun load(nickname: String, pair: KeyPair, confirmation: Boolean = false): Pubkey {
        val pubkey = Pubkey(
            id = 42,
            nickname = nickname,
            type = "EC",
            publicKey = pair.public.encoded,
            privateKey = pair.private.encoded,
            encrypted = false,
            startup = false,
            confirmation = confirmation,
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
