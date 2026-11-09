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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.connectbot.ctap2.Fido2Result
import org.connectbot.ctap2.Fido2SignatureResult
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.fido2.Fido2Manager
import org.connectbot.service.PromptManager
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.SshClient
import org.connectbot.util.HostConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Answers
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.stubbing.Answer

@OptIn(ExperimentalCoroutinesApi::class)
class SSHFido2AuthenticationTest {
    private var allowConnection = true
    private var pin: String? = "1234"
    private var hardwareAvailable = true
    private var signature = ByteArray(64)
    private val promptOrder = mutableListOf<String>()

    private fun key(type: String = "SK-Ed25519", transport: Fido2Transport = Fido2Transport.USB): Pubkey {
        val wire = if (type == "SK-ECDSA") {
            org.connectbot.sshlib.sk.SkPublicKeyEncoder.encode(org.connectbot.sshlib.sk.SkAlgorithm.ECDSA_P256, ByteArray(65).apply { this[0] = 4 }, "ssh:")
        } else {
            org.connectbot.sshlib.sk.SkPublicKeyEncoder.encode(org.connectbot.sshlib.sk.SkAlgorithm.ED25519, ByteArray(32), "ssh:")
        }
        return Pubkey(
            nickname = "hardware", type = type, privateKey = null, publicKey = wire,
            encrypted = false, startup = false, confirmation = false, createdDate = 0,
            storageType = KeyStorageType.FIDO2_RESIDENT_KEY, credentialId = byteArrayOf(1), fido2RpId = "ssh:", fido2Transport = transport,
        )
    }

    private fun promptManager() = mock(
        PromptManager::class.java,
        Answer { call ->
            when (call.method.name) {
                "requestFido2Connect" -> {
                    promptOrder.add("connect")
                    allowConnection
                }

                "requestFido2Pin" -> {
                    promptOrder.add("pin")
                    pin
                }

                else -> Answers.RETURNS_DEFAULTS.answer(call)
            }
        },
    )

    @Test
    fun offersWireAlgorithmsAndSignsSpecificAnyAndJumpHostHardwareKeys() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        val repository = mock(PubkeyRepository::class.java)
        var invalidPin = false
        var signingOwner: Any? = null
        val hardware = mock(
            Fido2Manager::class.java,
            Answer { call ->
                when (call.method.name) {
                    "signSshChallenge" -> {
                        signingOwner = call.getArgument(5)
                        if (invalidPin) Fido2Result.PinInvalid(2) else Fido2Result.Success(Fido2SignatureResult(ByteArray(37), signature, 0))
                    }

                    else -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            },
        )
        val manager = mock(TerminalManager::class.java)
        `when`(manager.pubkeyRepository).thenReturn(repository)
        `when`(manager.dispatchers).thenReturn(dispatchers)
        val prompts = promptManager()
        val bridge = mock(
            TerminalBridge::class.java,
            Answer { call ->
                when (call.method.name) {
                    "getFido2Manager" -> if (hardwareAvailable) hardware else null
                    "getPromptManager" -> prompts
                    else -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            },
        )
        for (type in listOf("SK-Ed25519", "SK-ECDSA", "sk-ssh-ed25519@openssh.com")) {
            for (transport in Fido2Transport.entries) {
                val stored = key(type, transport)
                signature = if (type == "SK-ECDSA") byteArrayOf(0x30, 6, 2, 1, 1, 2, 1, 2) else ByteArray(64)
                `when`(repository.getByIdBlocking(stored.id)).thenReturn(stored)
                `when`(repository.getAll()).thenReturn(listOf(stored))
                val expected = if (type == "SK-ECDSA") org.connectbot.sshlib.sk.SkAlgorithm.ECDSA_P256.sshName else org.connectbot.sshlib.sk.SkAlgorithm.ED25519.sshName
                for (id in listOf(stored.id, HostConstants.PUBKEYID_ANY)) {
                    val host = Host(username = "user", pubkeyId = id)
                    val client = mock(SshClient::class.java)
                    val fallback = mock(AuthHandler::class.java)
                    `when`(client.authenticate(anyString(), any(AuthHandler::class.java) ?: fallback)).thenAnswer {
                        val handler = it.getArgument<AuthHandler>(1)
                        val offered = runBlocking { handler.onPublicKeysNeeded() }
                        assertEquals(expected, offered.single().algorithmName)
                        promptOrder.clear()
                        assertNotNull(runBlocking { handler.onSignatureRequest(offered.single(), byteArrayOf(9)) })
                        org.junit.Assert.assertSame(bridge, signingOwner)
                        assertEquals(if (transport == Fido2Transport.USB) listOf("pin", "connect") else listOf("pin"), promptOrder)
                        invalidPin = true
                        try {
                            val error = assertThrows(java.io.IOException::class.java) {
                                runBlocking { handler.onSignatureRequest(offered.single(), byteArrayOf(9)) }
                            }
                            assertEquals("FIDO2 PIN is invalid. 2 attempts remaining.", error.message)
                            verify(bridge, atLeastOnce()).authenticationFailure = error.message
                        } finally {
                            invalidPin = false
                        }
                        AuthResult.Success
                    }
                    val ssh = SSH(host, bridge, manager)
                    SSH::class.java.getDeclaredField("client").apply { isAccessible = true }.set(ssh, client)
                    assertEquals(AuthResult.Success, ssh.authenticate())
                    assertEquals(true, ssh.authenticateJumpHost(client, host))
                }
            }
        }
    }

    @Test
    fun malformedCredentialsCancelledPromptsAndUnavailableHardwareDoNotSign() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = mock(PubkeyRepository::class.java)
        val manager = mock(TerminalManager::class.java)
        `when`(manager.pubkeyRepository).thenReturn(repository)
        `when`(manager.dispatchers).thenReturn(CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        val hardware = mock(Fido2Manager::class.java)
        val prompts = promptManager()
        val bridge = mock(
            TerminalBridge::class.java,
            Answer { call ->
                when (call.method.name) {
                    "getFido2Manager" -> if (hardwareAvailable) hardware else null
                    "getPromptManager" -> prompts
                    else -> Answers.RETURNS_DEFAULTS.answer(call)
                }
            },
        )
        val stored = key()
        var current = stored
        val client = mock(SshClient::class.java)
        val fallback = mock(AuthHandler::class.java)
        `when`(client.authenticate(anyString(), any(AuthHandler::class.java) ?: fallback)).thenAnswer {
            val handler = it.getArgument<AuthHandler>(1)
            val offered = runBlocking { handler.onPublicKeysNeeded() }
            assertNull(runBlocking { handler.onSignatureRequest(offered.single(), byteArrayOf(9)) })
            AuthResult.Success
        }
        for (variant in listOf("noCredential", "noRp", "unknown", "cancelConnect", "cancelPin", "noHardware")) {
            allowConnection = variant != "cancelConnect"
            pin = if (variant == "cancelPin") null else "1234"
            hardwareAvailable = variant != "noHardware"
            current = when (variant) {
                "noCredential" -> stored.copy(credentialId = null)
                "noRp" -> stored.copy(fido2RpId = null)
                "unknown" -> stored.copy(type = "sk-unknown")
                else -> stored
            }
            `when`(repository.getByIdBlocking(current.id)).thenReturn(current)
            `when`(repository.getAll()).thenReturn(listOf(current))
            val ssh = SSH(Host(username = "user", pubkeyId = current.id), bridge, manager)
            SSH::class.java.getDeclaredField("client").apply { isAccessible = true }.set(ssh, client)
            assertEquals(AuthResult.Success, ssh.authenticate())
        }
    }
}
