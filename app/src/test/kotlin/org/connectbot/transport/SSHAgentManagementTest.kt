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

import kotlinx.coroutines.test.runTest
import org.connectbot.service.TerminalManager
import org.connectbot.sshlib.AgentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.security.KeyPairGenerator

class SSHAgentManagementTest {
    private val manager = mock(TerminalManager::class.java)
    private val ssh = SSH().apply { setManager(this@SSHAgentManagementTest.manager) }
    private val provider = ssh.ConnectBotAgentProvider()

    @Test
    fun lockIsSharedAcrossProvidersAndProtectsCache() = runTest {
        val other = ssh.ConnectBotAgentProvider()
        assertEquals(AgentResult.Success(true), provider.lock("secret"))
        assertTrue(other.isLocked())
        assertEquals(AgentResult.Success(false), other.lock("replacement"))
        assertEquals(AgentResult.Success(false), other.unlock("wrong"))
        assertEquals(AgentResult.Success(emptyList<Any>()), other.getIdentities())
        assertEquals(AgentResult.Success(false), other.removeIdentity(byteArrayOf(1)))
        assertEquals(AgentResult.Success(false), other.removeAllIdentities())
        verify(manager, never()).removeKey(byteArrayOf(1))
        assertEquals(AgentResult.Success(true), other.unlock("secret"))
        assertFalse(provider.isLocked())
    }

    @Test
    fun removeOperationsUseManagerSoObserversAndExpiryJobsAreUpdated() = runTest {
        val blob = byteArrayOf(1)
        `when`(manager.removeKey(blob)).thenReturn(true)
        assertEquals(AgentResult.Success(true), provider.removeIdentity(blob))
        val keys = linkedMapOf("one" to TerminalManager.KeyHolder(), "two" to TerminalManager.KeyHolder())
        `when`(manager.loadedKeypairs).thenReturn(keys)
        assertEquals(AgentResult.Success(true), provider.removeAllIdentities())
        verify(manager).removeKey("one")
        verify(manager).removeKey("two")
    }

    @Test
    fun addedIdentityRetainsConfirmationAndStaysInMemory() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        `when`(manager.getKey("forwarded")).thenReturn(pair)
        assertEquals(AgentResult.Success(true), provider.addIdentity(pair, "forwarded", true, null))
        val invocation = mockingDetails(manager).invocations.single { it.method.name == "addKey" }
        val pubkey = invocation.getArgument<org.connectbot.data.entity.Pubkey>(0)
        assertEquals("forwarded", pubkey.nickname)
        assertTrue(pubkey.confirmation)
        assertTrue(pubkey.privateKey?.isEmpty() == true)
    }

    @Test
    fun unsupportedLifetimeIsRejectedInsteadOfSilentlyIgnored() = runTest {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        assertEquals(AgentResult.Success(false), provider.addIdentity(pair, "test", false, 10))
    }
}
