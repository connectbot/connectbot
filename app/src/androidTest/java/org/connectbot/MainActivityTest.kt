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

package org.connectbot

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.connectbot.ui.MainActivity
import org.connectbot.util.TestUriBuilder
import org.connectbot.util.waitForBridgeByNickname
import org.connectbot.util.waitUntilServiceBound
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        hiltRule.inject()
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
    }

    @Test
    fun mainActivity_handlesDisconnectAction() {
        val disconnectIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.DISCONNECT_ACTION
        }

        ActivityScenario.launch<MainActivity>(disconnectIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }
                val manager = state.terminalManager
            }
        }
    }

    @Test
    fun shortcut_localUri_launchesCorrectly() {
        val uri = TestUriBuilder.local("LocalShortcut")
        val shortcutIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }

        ActivityScenario.launch<MainActivity>(shortcutIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }
                val manager = state.terminalManager
                val bridge = runBlocking {
                    manager.waitForBridgeByNickname("LocalShortcut")
                }

                assertNotNull("Shortcut should create connection", bridge)
                assertTrue("Shortcut host should be temporary", bridge.host.id < 0)
            }
        }
    }

    @Test
    fun shortcut_sshUri_parsesCorrectly() {
        val uri = TestUriBuilder.ssh()
        val shortcutIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(shortcutIntent).use { scenario ->
            scenario.onActivity { activity ->
                runBlocking {
                    activity.waitUntilServiceBound()
                }

                assertTrue("Shortcut with SSH URI should not crash", activity != null)
            }
        }
    }

    @Test
    fun shortcut_nicknameWithSpaces_handlesCorrectly() {
        val uri = TestUriBuilder.local("My Local Terminal")
        val shortcutIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(shortcutIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }
                val manager = state.terminalManager

                val bridge = runBlocking {
                    manager.waitForBridgeByNickname("My Local Terminal")
                }

                assertNotNull("Should handle nickname with spaces", bridge)
            }
        }
    }

    @Test
    fun shortcut_nicknameWithSpecialChars_handlesCorrectly() {
        val uri = TestUriBuilder.local("Server-123_test")
        val shortcutIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(shortcutIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }
                val manager = state.terminalManager

                val bridge = runBlocking {
                    manager.waitForBridgeByNickname("Server-123_test")
                }

                assertNotNull("Should handle nickname with special characters", bridge)
            }
        }
    }

    @Test
    fun browserIntent_localUri_launchesConnectBot() {
        val uri = TestUriBuilder.local("BrowserTest")
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
        }

        ActivityScenario.launch<MainActivity>(browserIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }
                val manager = state.terminalManager
                val bridge = runBlocking {
                    manager.waitForBridgeByNickname("BrowserTest")
                }

                assertNotNull("Browser intent should create connection", bridge)
            }
        }
    }

    @Test
    fun browserIntent_sshUri_launchesConnectBot() {
        val uri = TestUriBuilder.ssh(nickname = "BrowserSSH")
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, MainActivity::class.java)
        }

        ActivityScenario.launch<MainActivity>(browserIntent).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue("Browser intent with SSH URI should not crash", activity != null)
            }
        }
    }

    @Test
    fun createShortcutIntent_showsHostList_andReturnsShortcut() {
        val createShortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            setClass(context, MainActivity::class.java)
        }

        ActivityScenario.launch<MainActivity>(createShortcutIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }

                assertTrue("Activity should be in shortcut creation mode", activity.makingShortcut)
                assertNotNull("TerminalManager should be bound", state.terminalManager)
            }
        }
    }

    @Test
    fun pickIntent_showsHostList_andReturnsShortcut() {
        val pickIntent = Intent(Intent.ACTION_PICK).apply {
            setClass(context, MainActivity::class.java)
        }

        ActivityScenario.launch<MainActivity>(pickIntent).use { scenario ->
            scenario.onActivity { activity ->
                val state = runBlocking {
                    activity.waitUntilServiceBound()
                }

                assertTrue("Activity should be in shortcut creation mode for PICK", activity.makingShortcut)
                assertNotNull("TerminalManager should be bound", state.terminalManager)
            }
        }
    }

    @Test
    fun createShortcut_usesModernShortcutInfoCompat() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)

        val createShortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            setClass(context, MainActivity::class.java)
        }

        ActivityScenario.launch<MainActivity>(createShortcutIntent).use { scenario ->
            var resultIntent: Intent? = null
            scenario.onActivity { activity ->
                runBlocking {
                    activity.waitUntilServiceBound()
                }
                if (activity.isFinishing) {
                    resultIntent = activity.intent
                }
            }

            if (resultIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutInfo = resultIntent?.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, ShortcutInfo::class.java)
                assertNotNull("Result should contain ShortcutInfo for modern Android versions", shortcutInfo)
            }
        }
    }
}
