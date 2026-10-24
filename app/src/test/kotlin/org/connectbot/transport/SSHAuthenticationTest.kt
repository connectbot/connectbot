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

import android.content.res.Resources
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.R
import org.connectbot.data.EntityIds
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
import org.connectbot.util.PubkeyUtils
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.UUID

class SSHAuthenticationTest {
    private val manager = mock(TerminalManager::class.java)
    private val bridge = mock(TerminalBridge::class.java)
    private val client = mock(SshClient::class.java)
    private val repository = mock(PubkeyRepository::class.java)
    private val loadedKeys = linkedMapOf<String, TerminalManager.KeyHolder>()
    private lateinit var handler: AuthHandler
    private val offered = mutableListOf<List<AuthPublicKey>>()
    private var beforeKeyRequest: () -> Unit = {}

    private suspend fun ssh(pubkeyId: UUID = HostConstants.PUBKEYID_ANY): SSH {
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
        verify(repository, never()).getByIdBlocking(any(UUID::class.java) ?: EntityIds.NONE)
    }

    @Test
    fun specificKey_doesNotOfferOtherUnlockedKeys() = runTest {
        val selected = load("selected", firstKey)
        load("other", secondKey)
        `when`(repository.getByIdBlocking(UUID(0, 42))).thenReturn(selected)
        `when`(manager.isKeyLoaded("selected")).thenReturn(true)
        `when`(manager.getKey("selected")).thenReturn(firstKey)
        ssh(UUID(0, 42)).authenticate()
        assertOffered(firstKey)
    }

    @Test
    fun encryptedRsaKey_withoutRememberingUnlockedKeys_canSignForTargetAndJumpHost() = runTest {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(4096) }.generateKeyPair()
        val pubkey = Pubkey(
            id = UUID(0, 42),
            nickname = "encrypted-rsa",
            type = "RSA",
            publicKey = pair.public.encoded,
            privateKey = PubkeyUtils.getEncodedPrivate(pair.private, "passphrase"),
            encrypted = true,
            startup = false,
            confirmation = false,
            createdDate = 0,
        )
        `when`(repository.getByIdBlocking(pubkey.id)).thenReturn(pubkey)
        val prompts = mock(PromptManager::class.java)
        `when`(bridge.promptManager).thenReturn(prompts)
        `when`(prompts.requestStringPrompt(null, null, true)).thenReturn("passphrase")
        val transport = ssh(pubkey.id)
        val data = "authentication request".toByteArray()

        suspend fun verifySignature() {
            val key = offered.last().single()
            assertThat(key.algorithmName).isEqualTo("ssh-rsa")
            val signature = handler.onSignatureRequest(key, data)
            assertThat(signature).isNotNull()
            val blob = ByteBuffer.wrap(signature!!)
            val algorithm = ByteArray(blob.int).also(blob::get)
            assertThat(String(algorithm)).isEqualTo("ssh-rsa")
            val signed = ByteArray(blob.int).also(blob::get)
            val verifier = Signature.getInstance("SHA1withRSA")
            verifier.initVerify(pair.public)
            verifier.update(data)
            assertThat(verifier.verify(signed)).isTrue()
            assertThat(loadedKeys).isEmpty()
            // Only the key offered by this handler may use the temporary private key.
            assertThat(handler.onSignatureRequest(SshSigning.encodePublicKey(firstKey), data)).isNull()
        }

        transport.authenticate()
        verifySignature()
        transport.authenticateJumpHost(client, Host(username = "alice", pubkeyId = pubkey.id))
        verifySignature()
        verify(prompts, times(2)).requestStringPrompt(null, null, true)
    }

    @Test
    fun uncachedKey_approvalIsRememberedWithinEachAuthentication() = runTest {
        assertUncachedKeyDecision(approved = true)
    }

    @Test
    fun uncachedKey_refusalIsRememberedWithinEachAuthentication() = runTest {
        assertUncachedKeyDecision(approved = false)
    }

    private suspend fun assertUncachedKeyDecision(approved: Boolean) {
        val pubkey = uncachedPubkey()
        val prompts = mock(PromptManager::class.java)
        val resources = mock(Resources::class.java)
        `when`(manager.res).thenReturn(resources)
        `when`(resources.getString(R.string.prompt_allow_agent_to_use_key, pubkey.nickname))
            .thenReturn("Allow selected key?")
        `when`(bridge.promptManager).thenReturn(prompts)
        `when`(prompts.requestBooleanPrompt(null, "Allow selected key?")).thenReturn(approved)
        val transport = ssh(pubkey.id)

        suspend fun assertDecision() {
            val key = offered.last().single()
            repeat(2) {
                val signature = handler.onSignatureRequest(key, byteArrayOf(it.toByte()))
                assertThat(signature != null).isEqualTo(approved)
            }
            assertThat(loadedKeys).isEmpty()
        }

        transport.authenticate()
        verify(prompts, never()).requestBooleanPrompt(null, "Allow selected key?")
        assertDecision()
        verify(prompts).requestBooleanPrompt(null, "Allow selected key?")

        transport.authenticateJumpHost(client, Host(username = "alice", pubkeyId = pubkey.id))
        assertDecision()
        verify(prompts, times(2)).requestBooleanPrompt(null, "Allow selected key?")

        // A later authentication needs its own decision, even for the same client and key.
        transport.authenticate()
        assertDecision()
        verify(prompts, times(3)).requestBooleanPrompt(null, "Allow selected key?")
    }

    @Test
    fun uncachedKey_withoutConfirmationPrompts_cannotSign() = runTest {
        val pubkey = uncachedPubkey()
        ssh(pubkey.id).authenticate()
        val key = offered.last().single()

        assertThat(handler.onSignatureRequest(key, byteArrayOf(1))).isNull()
        assertThat(handler.onSignatureRequest(key, byteArrayOf(2))).isNull()
        assertThat(loadedKeys).isEmpty()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun cachedKey_unloadedWhileAwaitingApproval_cannotSign() = runTest {
        load("selected", firstKey, confirmation = true)
        val prompts = PromptManager()
        `when`(bridge.promptManager).thenReturn(prompts)
        ssh().authenticate()
        val signature = async { handler.onSignatureRequest(offered.last().single(), byteArrayOf(1)) }
        runCurrent()
        assertThat(prompts.promptState.value).isNotNull()

        loadedKeys.clear()
        prompts.respond(PromptResponse.BooleanResponse(true))

        assertThat(signature.await()).isNull()
    }

    @Test
    fun cachedKey_missingKeyPair_doesNotPreventSigningWithAnotherKey() = runTest {
        loadedKeys["empty"] = TerminalManager.KeyHolder()
        load("selected", firstKey)
        ssh().authenticate()
        assertOffered(firstKey)

        assertThat(handler.onSignatureRequest(offered.last().single(), byteArrayOf(1))).isNotNull()
    }

    private fun uncachedPubkey(): Pubkey {
        val pubkey = load("selected", firstKey, confirmation = true)
        loadedKeys.clear()
        `when`(repository.getByIdBlocking(pubkey.id)).thenReturn(pubkey)
        return pubkey
    }

    @Test
    fun publicKeyOnlyFailure_displaysAuthenticationFailure() = runTest {
        val resources = mock(Resources::class.java)
        `when`(manager.res).thenReturn(resources)
        `when`(resources.getString(R.string.terminal_auth_fail)).thenReturn("Authentication failed")
        val transport = ssh()
        val fallback = mock(AuthHandler::class.java)
        `when`(client.authenticate(eq("alice") ?: "alice", any(AuthHandler::class.java) ?: fallback))
            .thenReturn(AuthResult.Failure(setOf("publickey")))

        transport.authenticate()

        verify(bridge).outputLine("Authentication failed")
    }

    @Test
    fun neverKey_doesNotOfferAnyUnlockedKeys() = runTest {
        load("first", firstKey)
        ssh(HostConstants.PUBKEYID_NEVER).authenticate()
        assertOffered()
        verify(repository, never()).getByIdBlocking(any(UUID::class.java) ?: EntityIds.NONE)
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
            id = UUID(0, 42),
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
