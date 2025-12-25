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
import org.connectbot.e2e.util.E2ETestConfig
import org.connectbot.e2e.util.createPasswordOnlyTestHost
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
import org.connectbot.service.TerminalBridge
import org.connectbot.ui.MainActivity
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
 * E2E tests for password authentication.
 *
 * Run with: ./gradlew e2eTest
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PasswordAuthE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var hostRepository: HostRepository

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
            hostRepository.getHosts()
                .filter { it.nickname.startsWith("E2E") }
                .forEach { hostRepository.deleteHost(it) }
        }
    }

    @Test
    fun testPasswordAuth_withValidCredentials_succeeds() {
        val testHost = createPasswordOnlyTestHost(nickname = "E2E-Password-Auth-Test")

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
                        "E2E-Password-Auth-Test",
                        timeoutMillis = 15000
                    )

                    // Handle prompts (host key and password)
                    val sessionOpened = handleAuthenticationPrompts(
                        bridge,
                        password = E2ETestConfig.sshPassword
                    )

                    assertThat(sessionOpened)
                        .withFailMessage("SSH session should be established with valid password")
                        .isTrue()
                }
            }
        }
    }

    @Test
    fun testPasswordAuth_withInvalidPassword_fails() {
        val testHost = createPasswordOnlyTestHost(nickname = "E2E-Invalid-Password-Test")

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
                        "E2E-Invalid-Password-Test",
                        timeoutMillis = 15000
                    )

                    // Try with invalid password - expect failure
                    val sessionOpened = handleAuthenticationPrompts(
                        bridge,
                        password = "wrong-password-12345"
                    )

                    // With wrong password, session should not open successfully
                    // (may get disconnected or get another password prompt)
                    // For this test, we just verify the initial auth attempt behavior
                    assertThat(bridge.transport?.isSessionOpen() != true || !sessionOpened)
                        .withFailMessage("SSH session should not succeed with invalid password")
                        .isTrue()
                }
            }
        }
    }

    /**
     * Helper to handle authentication prompts (host key verification and password).
     * Returns true if session is successfully opened.
     */
    private suspend fun handleAuthenticationPrompts(
        bridge: TerminalBridge,
        password: String,
        timeoutMillis: Long = 30000
    ): Boolean {
        return try {
            withTimeout(timeoutMillis) {
                var hostKeyAccepted = false
                var passwordProvided = false

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
                            if (prompt.isPassword && !passwordProvided) {
                                bridge.promptManager.respond(PromptResponse.StringResponse(password))
                                passwordProvided = true
                            }
                        }
                        else -> {}
                    }

                    delay(200)

                    // Check for disconnection (auth failure)
                    if (bridge.transport?.isConnected() == false && passwordProvided) {
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
