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

package org.connectbot.sftp

import com.trilead.ssh2.Connection
import com.trilead.ssh2.ConnectionInfo
import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import com.trilead.ssh2.InteractiveCallback
import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.ServerHostKeyVerifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KnownHost
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.HostConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SftpConnectionManagerTest {
    private val dispatcher = StandardTestDispatcher()
    private val hosts = mock(HostRepository::class.java)
    private val keys = mock(PubkeyRepository::class.java)
    private val manager = SftpConnectionManager(hosts, keys, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
    private val host = Host(id = UUID(0, 1), nickname = "host", hostname = "server", username = "user", pubkeyId = HostConstants.PUBKEYID_NEVER)
    private val keyBlob = byteArrayOf(1, 2, 3)
    private var method = "none"
    private var algorithm = "ssh-rsa"
    private var connectFailure = false
    private var authFailure = false
    private var closeFailure = false
    private var keyError: Exception? = null
    private var keyAccepted = true
    private val verifiers = mutableListOf<ExtendedServerHostKeyVerifier>()
    private val prompts = Prompts()

    private class Prompts : SftpPromptHandler {
        var accept = true
        var password: String? = "test input"
        var interactive: Array<String>? = arrayOf("response")
        var passphrase: String? = null
        var biometric = false
        val hostKeys = mutableListOf<Pair<String, Boolean>>()
        override suspend fun requestPassword(message: String) = password
        override suspend fun requestKeyPassphrase(keyName: String): String? = passphrase
        override suspend fun requestBiometricAuth(keyName: String, keystoreAlias: String) = biometric
        override suspend fun confirmHostKey(hostname: String, keyType: String, fingerprint: String, isNewKey: Boolean): Boolean {
            hostKeys.add(keyType to isNewKey)
            return accept
        }
        override suspend fun handleKeyboardInteractive(name: String, instruction: String, prompts: Array<String>, echoResponses: BooleanArray) = interactive
    }

    private fun connections() = mockConstruction(Connection::class.java) { connection, construction ->
        val hostname = construction.arguments()[0] as String
        var authenticated = false
        `when`(connection.isAuthenticationComplete).thenAnswer { authenticated }
        `when`(connection.authenticateWithNone("user")).thenAnswer {
            if (method == "none-error") throw IOException("none unsupported")
            (method == "none").also { authenticated = it }
        }
        `when`(connection.isAuthMethodAvailable(anyString(), anyString())).thenAnswer { it.getArgument<String>(1) == method }
        `when`(connection.authenticateWithPassword(anyString(), anyString())).thenAnswer {
            if (authFailure) throw IOException("rejected")
            authenticated = true
            true
        }
        `when`(connection.authenticateWithKeyboardInteractive(anyString(), any(InteractiveCallback::class.java))).thenAnswer {
            val callback = it.getArgument<InteractiveCallback>(1)
            val response = callback.replyToChallenge("name", "instruction", 1, arrayOf("Password"), booleanArrayOf(false))
            assertEquals(prompts.interactive?.toList() ?: listOf(""), response.toList())
            if (authFailure) throw IOException("rejected")
            authenticated = true
            true
        }
        `when`(connection.authenticateWithPublicKey(anyString(), any(java.security.KeyPair::class.java))).thenAnswer {
            keyError?.let { error -> throw error }
            keyAccepted.also { authenticated = it }
        }
        doAnswer {
            if (connectFailure) throw IOException("connection failed")
            val verifier = it.getArgument<ExtendedServerHostKeyVerifier>(0)
            verifiers.add(verifier)
            if (!verifier.verifyServerHostKey(hostname, 22, algorithm, keyBlob)) throw IOException("host key rejected")
            mock(ConnectionInfo::class.java)
        }.`when`(connection).connect(any(ServerHostKeyVerifier::class.java))
        doAnswer {
            if (closeFailure) throw IOException("close failed")
            Unit
        }.`when`(connection).close()
    }

    @Test
    fun reusesConnectionsAndClosesAllSessionsEvenWhenCloseThrows() = runTest(dispatcher) {
        `when`(hosts.getKnownHostsForHost(host.id)).thenReturn(emptyList())
        connections().use { connections ->
            mockConstruction(SFTPv3Client::class.java).use { clients ->
                val first = manager.connect(host.copy(compression = true), prompts).getOrThrow()
                assertSame(first, manager.connect(host, prompts).getOrThrow())
                assertEquals(1, connections.constructed().size)
                assertSame(first, manager.getOperations(host.id))
                assertTrue(manager.isConnected(host.id))
                verify(connections.constructed().single()).setCompression(true)
                closeFailure = true
                manager.disconnectAll()
                assertFalse(manager.isConnected(host.id))
                assertNull(manager.getOperations(host.id))
                verify(clients.constructed().single()).close()
                manager.disconnect(host.id)
            }
        }
    }

    @Test
    fun passwordAndKeyboardInteractiveAuthenticationRespectCancellationAndFailure() = runTest(dispatcher) {
        `when`(hosts.getKnownHostsForHost(host.id)).thenReturn(emptyList())
        connections().use {
            mockConstruction(SFTPv3Client::class.java).use {
                for (auth in listOf("password", "keyboard-interactive")) {
                    method = auth
                    assertTrue(manager.connect(host, prompts).isSuccess)
                    manager.disconnect(host.id)
                    prompts.interactive = null
                    if (auth == "keyboard-interactive") {
                        assertTrue(manager.connect(host, prompts).isSuccess)
                        manager.disconnect(host.id)
                    }
                    authFailure = true
                    assertTrue(manager.connect(host, prompts).isFailure)
                    authFailure = false
                    if (auth == "password") {
                        prompts.password = null
                        assertTrue(manager.connect(host, prompts).isFailure)
                        prompts.password = "test input"
                    }
                }
                method = "none-error"
                assertTrue(manager.connect(host, prompts).isFailure)
                method = "unsupported"
                assertTrue(manager.connect(host, prompts).isFailure)
            }
        }
    }

    @Test
    fun verifiesKnownKeysPromptsForChangedKeysAndTracksAdvertisedAlgorithms() = runTest(dispatcher) {
        connections().use {
            mockConstruction(SFTPv3Client::class.java).use {
                `when`(hosts.getKnownHostsForHost(host.id)).thenReturn(listOf(KnownHost(hostId = host.id, hostname = host.hostname, port = 22, hostKeyAlgo = algorithm, hostKey = keyBlob)))
                assertTrue(manager.connect(host, prompts).isSuccess)
                assertTrue(prompts.hostKeys.isEmpty())
                val verifier = verifiers.last()
                `when`(hosts.getHostKeyAlgorithmsForHost(host.id)).thenReturn(emptyList())
                assertNull(verifier.getKnownKeyAlgorithmsForHost("server", 22))
                `when`(hosts.getHostKeyAlgorithmsForHost(host.id)).thenReturn(listOf("ssh-rsa"))
                assertEquals(listOf("ssh-rsa"), verifier.getKnownKeyAlgorithmsForHost("server", 22))
                verifier.addServerHostKey("server", 22, "ssh-rsa", keyBlob)
                verifier.removeServerHostKey("server", 22, "ssh-rsa", keyBlob)
                manager.disconnect(host.id)
                `when`(hosts.getKnownHostsForHost(host.id)).thenReturn(listOf(KnownHost(hostId = host.id, hostname = host.hostname, port = 22, hostKeyAlgo = algorithm, hostKey = byteArrayOf(9))))
                assertTrue(manager.connect(host, prompts).isSuccess)
                assertEquals("RSA" to false, prompts.hostKeys.last())
                manager.disconnect(host.id)
                prompts.accept = false
                assertTrue(manager.connect(host, prompts).isFailure)
                for ((name, label) in listOf("ssh-dss" to "DSA", "ssh-ed25519" to "Ed25519", "ecdsa-sha2-nistp256" to "EC", "unknown" to "UNKNOWN")) {
                    algorithm = name
                    prompts.accept = true
                    assertTrue(manager.connect(host, prompts).isSuccess)
                    assertEquals(label to true, prompts.hostKeys.last())
                    manager.disconnect(host.id)
                    prompts.accept = false
                    assertTrue(manager.connect(host, prompts).isFailure)
                }
                `when`(hosts.getKnownHostsForHost(host.id)).thenReturn(emptyList())
                assertTrue(manager.connect(host, prompts).isFailure)
                connectFailure = true
                assertTrue(manager.connect(host, prompts).isFailure)
            }
        }
    }

    @Test
    fun authenticatesThroughNestedJumpHostsAndCleansUpOnFailure() = runTest(dispatcher) {
        val outer = host.copy(id = UUID(0, 2), nickname = "outer", hostname = "outer", compression = true)
        val inner = host.copy(id = UUID(0, 3), nickname = "inner", hostname = "inner", jumpHostId = outer.id)
        val target = host.copy(jumpHostId = inner.id)
        for (h in listOf(host, outer, inner)) {
            `when`(hosts.findHostById(h.id)).thenReturn(h)
            `when`(hosts.getKnownHostsForHost(h.id)).thenReturn(emptyList())
        }
        connections().use { connections ->
            mockConstruction(SFTPv3Client::class.java).use {
                assertNotNull(manager.connect(target, prompts).getOrThrow())
                assertEquals(3, connections.constructed().size)
                manager.disconnectAll()
                connections.constructed().forEach { verify(it).close() }
                method = "unsupported"
                assertTrue(manager.connect(target, prompts).isFailure)
                connectFailure = true
                assertTrue(manager.connect(target, prompts).isFailure)
            }
        }
        `when`(hosts.findHostById(inner.id)).thenReturn(null)
        assertTrue(manager.connect(target, prompts).isFailure)
        `when`(hosts.findHostById(inner.id)).thenReturn(inner.copy(jumpHostId = UUID(0, 99)))
        assertTrue(manager.connect(target, prompts).isFailure)
    }

    @Test
    fun authenticatesSpecificAndAnyKeysAndReusesUnlockedKeyAcrossJumpHosts() = runTest(dispatcher) {
        val pair = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val key = org.connectbot.data.entity.Pubkey(
            nickname = "key",
            type = "RSA",
            privateKey = pair.private.encoded,
            publicKey = pair.public.encoded,
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 0,
        )
        `when`(hosts.getKnownHostsForHost(host.id)).thenReturn(emptyList())
        `when`(keys.getAll()).thenReturn(listOf(key))
        `when`(keys.getById(key.id)).thenReturn(key)
        method = "publickey"
        connections().use {
            mockConstruction(SFTPv3Client::class.java).use {
                assertTrue(manager.connect(host.copy(pubkeyId = HostConstants.PUBKEYID_ANY), prompts).isSuccess)
                manager.disconnectAll()
                val outer = host.copy(id = UUID(0, 2), hostname = "jump", pubkeyId = key.id)
                `when`(hosts.findHostById(outer.id)).thenReturn(outer)
                `when`(hosts.getKnownHostsForHost(outer.id)).thenReturn(emptyList())
                assertTrue(manager.connect(host.copy(pubkeyId = key.id, jumpHostId = outer.id), prompts).isSuccess)
                manager.disconnectAll()
                keyAccepted = false
                assertTrue(manager.connect(host.copy(pubkeyId = key.id), prompts).isFailure)
                keyAccepted = true
                for (error in listOf(IOException("signing failed"), android.security.keystore.UserNotAuthenticatedException(), IOException("wrapped", android.security.keystore.KeyPermanentlyInvalidatedException()))) {
                    keyError = error
                    assertTrue(manager.connect(host.copy(pubkeyId = key.id), prompts).isFailure)
                    assertTrue(manager.connect(host.copy(pubkeyId = HostConstants.PUBKEYID_ANY), prompts).isFailure)
                }
                keyError = null
                `when`(keys.getById(key.id)).thenReturn(key.copy(encrypted = true))
                assertTrue(manager.connect(host.copy(pubkeyId = key.id), prompts).isFailure)
                prompts.passphrase = ""
                assertTrue(manager.connect(host.copy(pubkeyId = key.id), prompts).isSuccess)
                manager.disconnectAll()
            }
        }
    }

    @Test
    fun unavailableMalformedAndBiometricKeysFailWithoutCrashing() = runTest(dispatcher) {
        val key = org.connectbot.data.entity.Pubkey(
            nickname = "key",
            type = "RSA",
            privateKey = null,
            publicKey = byteArrayOf(),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 0,
        )
        `when`(hosts.getKnownHostsForHost(host.id)).thenReturn(emptyList())
        method = "publickey"
        connections().use {
            mockConstruction(SFTPv3Client::class.java).use {
                assertTrue(manager.connect(host.copy(pubkeyId = key.id), prompts).isFailure)
                for (invalid in listOf(
                    key,
                    key.copy(privateKey = byteArrayOf(1)),
                    key.copy(type = "IMPORTED"),
                    key.copy(type = "IMPORTED", privateKey = "invalid PEM".toByteArray()),
                    key.copy(storageType = org.connectbot.data.entity.KeyStorageType.ANDROID_KEYSTORE),
                    key.copy(storageType = org.connectbot.data.entity.KeyStorageType.ANDROID_KEYSTORE, keystoreAlias = "missing"),
                )) {
                    `when`(keys.getAll()).thenReturn(listOf(invalid))
                    `when`(keys.getById(key.id)).thenReturn(invalid)
                    assertTrue(manager.connect(host.copy(pubkeyId = HostConstants.PUBKEYID_ANY), prompts).isFailure)
                    assertTrue(manager.connect(host.copy(pubkeyId = key.id), prompts).isFailure)
                }
                prompts.biometric = true
                assertTrue(manager.connect(host.copy(pubkeyId = key.id), prompts).isFailure)
            }
        }
    }
}
