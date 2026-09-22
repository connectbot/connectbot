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

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.SshClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.UUID

class SSHImportedKeyTest {
    @Test
    fun invalidImportedKey_doesNotAbortAuthentication() = runTest {
        val manager = mock(TerminalManager::class.java)
        val repository = mock(PubkeyRepository::class.java)
        val client = mock(SshClient::class.java)
        val bridge = mock(TerminalBridge::class.java)
        val pubkey = Pubkey(
            id = UUID(0, 42),
            nickname = "broken",
            type = "IMPORTED",
            publicKey = byteArrayOf(),
            privateKey = "invalid PEM".toByteArray(),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 0,
        )
        `when`(manager.pubkeyRepository).thenReturn(repository)
        `when`(repository.getByIdBlocking(UUID(0, 42))).thenReturn(pubkey)
        val fallback = mock(AuthHandler::class.java)
        `when`(client.authenticate(eq("alice") ?: "alice", any(AuthHandler::class.java) ?: fallback)).thenAnswer {
            val handler = it.getArgument<AuthHandler>(1)
            assertEquals(emptyList<Any>(), runBlocking { handler.onPublicKeysNeeded() })
            AuthResult.Failure(setOf("password"))
        }
        val ssh = SSH(Host(username = "alice", pubkeyId = UUID(0, 42)), bridge, manager)
        SSH::class.java.getDeclaredField("client").apply { isAccessible = true }.set(ssh, client)
        assertEquals(AuthResult.Failure(setOf("password")), ssh.authenticate())
    }
}
