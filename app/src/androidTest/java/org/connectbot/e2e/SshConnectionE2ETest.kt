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
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.HostRepository
import org.connectbot.e2e.util.E2ETestConfig
import org.connectbot.e2e.util.createE2ETestHost
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
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
 * E2E tests for basic SSH connection functionality.
 * These tests connect to a real SSH server running in Docker.
 *
 * Run with: ./gradlew e2eTest
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SshConnectionE2ETest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var hostRepository: HostRepository

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Skip tests if E2E config is not available
        Assume.assumeTrue(
            "E2E tests require SSH server config (run via ./gradlew e2eTest)",
            E2ETestConfig.isConfigured
        )
    }

    @After
    fun tearDown() {
        // Clean up any hosts created during tests
        runBlocking {
            hostRepository.getHosts()
                .filter { it.nickname.startsWith("E2E") }
                .forEach { hostRepository.deleteHost(it) }
        }
    }

    @Test
    @org.junit.Ignore("URI intent handling requires notification permission flow that doesn't work reliably in instrumented tests")
    fun testSshConnection_viaUri_createsBridge() {
        // Build SSH URI for Docker SSH server
        val uri = Uri.parse(
            "ssh://${E2ETestConfig.sshUser}@${E2ETestConfig.sshHost}:${E2ETestConfig.sshPort}/#E2E-URI-Test"
        )

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    // Wait for service to bind
                    val state = activity.waitUntilServiceBound(timeoutMillis = 10000)
                    val manager = state.terminalManager

                    // Wait for bridge to be created
                    val bridge = manager.waitForBridgeByNickname(
                        "E2E-URI-Test",
                        timeoutMillis = 15000
                    )

                    // Verify bridge was created with correct host info
                    assertThat(bridge).isNotNull
                    assertThat(bridge.host.hostname).isEqualTo(E2ETestConfig.sshHost)
                    assertThat(bridge.host.username).isEqualTo(E2ETestConfig.sshUser)
                    assertThat(bridge.host.port).isEqualTo(E2ETestConfig.sshPort)
                }
            }
        }
    }

    @Test
    fun testSshConnection_fromSavedHost_createsBridge() {
        // Create and save a host configuration
        val testHost = createE2ETestHost(nickname = "E2E-Saved-Host-Test")

        val savedHostId = runBlocking {
            val saved = hostRepository.saveHost(testHost)
            assertThat(saved.id).isGreaterThan(0)
            saved.id
        }

        // Launch MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    val state = activity.waitUntilServiceBound(timeoutMillis = 10000)
                    val manager = state.terminalManager

                    // Open connection to saved host
                    manager.openConnectionForHostId(savedHostId)

                    // Wait for bridge
                    val bridge = manager.waitForBridgeByNickname(
                        "E2E-Saved-Host-Test",
                        timeoutMillis = 15000
                    )

                    assertThat(bridge).isNotNull
                    assertThat(bridge.host.id).isEqualTo(savedHostId)
                    assertThat(bridge.host.hostname).isEqualTo(E2ETestConfig.sshHost)
                }
            }
        }
    }

    @Test
    fun testSshConnection_hostKeyVerification_promptsUser() {
        val testHost = createE2ETestHost(nickname = "E2E-HostKey-Test")

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
                        "E2E-HostKey-Test",
                        timeoutMillis = 15000
                    )

                    // Wait for and verify host key prompt appears
                    var hostKeyPromptReceived = false
                    var attempts = 0
                    while (!hostKeyPromptReceived && attempts < 60) {
                        val prompt = bridge.promptManager.promptState.value
                        if (prompt is PromptRequest.HostKeyFingerprintPrompt) {
                            hostKeyPromptReceived = true
                            // Verify it's for our SSH server
                            assertThat(prompt.hostname).isEqualTo(E2ETestConfig.sshHost)
                            assertThat(prompt.keyType).isNotEmpty()

                            // Accept the host key
                            bridge.promptManager.respond(PromptResponse.BooleanResponse(true))
                        }
                        if (!hostKeyPromptReceived) {
                            kotlinx.coroutines.delay(500)
                            attempts++
                        }
                    }

                    assertThat(hostKeyPromptReceived)
                        .withFailMessage("Expected host key verification prompt")
                        .isTrue()
                }
            }
        }
    }
}
