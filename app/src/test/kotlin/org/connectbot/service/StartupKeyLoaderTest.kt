/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.service

import org.connectbot.data.entity.Pubkey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator

/**
 * Regression tests for issue #2081: encrypted startup keys must not be silently dropped.
 */
class StartupKeyLoaderTest {

    private val rsaPair: KeyPair by lazy {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        keyGen.generateKeyPair()
    }

    private fun pubkey(encrypted: Boolean) = Pubkey(
        id = 1L,
        nickname = "k",
        type = "RSA",
        privateKey = byteArrayOf(0),
        publicKey = byteArrayOf(0),
        encrypted = encrypted,
        startup = true,
        confirmation = false,
        createdDate = 0L,
    )

    @Test
    fun encryptedKey_requestsPassphrasePrompt_withoutInvokingConverter() {
        var converterInvocations = 0
        val outcome = classifyStartupKey(pubkey(encrypted = true)) { _, _ ->
            converterInvocations++
            null
        }
        assertTrue(
            "Encrypted startup keys must surface a passphrase prompt instead of being silently skipped",
            outcome is StartupKeyLoadOutcome.NeedsPassphrase,
        )
        assertEquals(
            "Encrypted key must not be attempted with a null password",
            0,
            converterInvocations,
        )
    }

    @Test
    fun unencryptedKey_loadsImmediately() {
        val key = pubkey(encrypted = false)
        val outcome = classifyStartupKey(key) { p, password ->
            assertEquals(key, p)
            assertNull("Unencrypted key must be loaded with null password", password)
            rsaPair
        }
        assertTrue(outcome is StartupKeyLoadOutcome.Loaded)
        assertEquals(rsaPair, (outcome as StartupKeyLoadOutcome.Loaded).pair)
    }

    @Test
    fun unencryptedKey_conversionReturningNull_reportsFailure() {
        val outcome = classifyStartupKey(pubkey(encrypted = false)) { _, _ -> null }
        assertTrue(outcome is StartupKeyLoadOutcome.Failed)
    }

    @Test
    fun unencryptedKey_conversionThrowing_reportsFailure() {
        val outcome = classifyStartupKey(pubkey(encrypted = false)) { _, _ ->
            throw IllegalStateException("boom")
        }
        assertTrue(outcome is StartupKeyLoadOutcome.Failed)
        assertEquals("boom", (outcome as StartupKeyLoadOutcome.Failed).reason)
    }
}
