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

package org.connectbot.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.e2e.util.E2ETestConfig
import org.connectbot.e2e.util.createE2ETestHost
import org.connectbot.e2e.util.createTestPubkey
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
import org.connectbot.service.TerminalBridge
import org.connectbot.ui.MainActivity
import org.connectbot.util.HostConstants
import org.connectbot.util.PubkeyUtils
import org.connectbot.util.waitForBridgeByNickname
import org.connectbot.util.waitUntilServiceBound
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * E2E tests for public key authentication.
 *
 * Run with: ./gradlew e2eTest
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PubkeyAuthE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var hostRepository: HostRepository

    @Inject
    lateinit var pubkeyRepository: PubkeyRepository

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hiltRule.inject()
        Assume.assumeTrue(
            "E2E tests require SSH server config (run via ./gradlew e2eTest)",
            E2ETestConfig.isConfigured
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            // Clean up hosts
            hostRepository.getHosts()
                .filter { it.nickname.startsWith("E2E") }
                .forEach { hostRepository.deleteHost(it) }
            // Clean up pubkeys
            pubkeyRepository.getAll()
                .filter { it.nickname.startsWith("E2E") }
                .forEach { pubkeyRepository.delete(it) }
        }
    }

    @Test
    @org.junit.Ignore("Pubkey auth test requires further integration work with PEMDecoder")
    fun testPubkeyAuth_withSpecificKey_succeeds() {
        // Create and save test pubkey
        val testPubkey = createTestPubkey(context, nickname = "E2E-Specific-Key")
        val savedKeyId = runBlocking {
            val saved = pubkeyRepository.save(testPubkey)
            assertThat(saved.id).isGreaterThan(0)
            saved.id
        }

        // Create host configured to use specific key
        val testHost = createE2ETestHost(
            nickname = "E2E-Pubkey-Specific-Test",
            useKeys = true,
            pubkeyId = savedKeyId
        )
        val savedHostId = runBlocking {
            val saved = hostRepository.saveHost(testHost)
            saved.id
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    val state = activity.waitUntilServiceBound(timeoutMillis = 10000)
                    val manager = state.terminalManager

                    manager.openConnectionForHostId(savedHostId)

                    val bridge = manager.waitForBridgeByNickname(
                        "E2E-Pubkey-Specific-Test",
                        timeoutMillis = 15000
                    )

                    // Handle host key verification (pubkey auth should be automatic)
                    val sessionOpened = handleHostKeyAndWaitForSession(bridge)

                    assertThat(sessionOpened)
                        .withFailMessage("SSH session should be established with pubkey auth")
                        .isTrue()
                }
            }
        }
    }

    @Test
    @org.junit.Ignore("Pubkey auth test requires further integration work with key loading")
    fun testPubkeyAuth_withAnyKey_succeeds() {
        // Create and save test pubkey with startup=true so it's loaded into memory
        val testPubkey = createTestPubkey(context, nickname = "E2E-Any-Key").copy(
            startup = true
        )
        val savedKeyId = runBlocking {
            val saved = pubkeyRepository.save(testPubkey)
            saved.id
        }

        // Create host configured to try any available key
        val testHost = createE2ETestHost(
            nickname = "E2E-Pubkey-Any-Test",
            useKeys = true,
            pubkeyId = HostConstants.PUBKEYID_ANY
        )
        val savedHostId = runBlocking {
            val saved = hostRepository.saveHost(testHost)
            saved.id
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    val state = activity.waitUntilServiceBound(timeoutMillis = 10000)
                    val manager = state.terminalManager

                    // Manually load the key into memory (simulating startup load)
                    val key = pubkeyRepository.getById(savedKeyId)
                    if (key != null) {
                        try {
                            val keyPair = PubkeyUtils.convertToKeyPair(key, null)
                            if (keyPair != null) {
                                manager.addKey(key, keyPair, force = true)
                            }
                        } catch (e: Exception) {
                            // Key loading may fail in test environment
                        }
                    }

                    manager.openConnectionForHostId(savedHostId)

                    val bridge = manager.waitForBridgeByNickname(
                        "E2E-Pubkey-Any-Test",
                        timeoutMillis = 15000
                    )

                    // Handle authentication
                    val sessionOpened = handleHostKeyAndWaitForSession(bridge)

                    assertThat(sessionOpened)
                        .withFailMessage("SSH session should be established with any-key pubkey auth")
                        .isTrue()
                }
            }
        }
    }

    /**
     * Helper to handle host key verification and wait for session to open.
     * Pubkey auth should happen automatically after host key is accepted.
     */
    private suspend fun handleHostKeyAndWaitForSession(
        bridge: TerminalBridge,
        timeoutMillis: Long = 30000
    ): Boolean {
        return try {
            withTimeout(timeoutMillis) {
                var hostKeyAccepted = false

                while (bridge.transport?.isSessionOpen() != true) {
                    val prompt = bridge.promptManager.promptState.value

                    when (prompt) {
                        is PromptRequest.HostKeyFingerprintPrompt -> {
                            if (!hostKeyAccepted) {
                                bridge.promptManager.respond(PromptResponse.BooleanResponse(true))
                                hostKeyAccepted = true
                            }
                        }
                        is PromptRequest.StringPrompt -> {
                            // If we get a password prompt, pubkey auth failed
                            // Cancel and return false
                            bridge.promptManager.respond(PromptResponse.StringResponse(null))
                            return@withTimeout false
                        }
                        else -> {}
                    }

                    delay(200)

                    // Check for disconnection
                    if (bridge.transport?.isConnected() == false) {
                        return@withTimeout false
                    }
                }

                true
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            false
        }
    }
}
