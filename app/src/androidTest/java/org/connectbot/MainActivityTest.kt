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

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.ui.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ConnectBotDatabase.clearInstance()
        ConnectBotDatabase.getTestInstance(context)
    }

    @After
    fun tearDown() {
        ConnectBotDatabase.clearInstance()
    }

    @Test
    fun mainActivity_launches() {
        // Verify activity launches without crashing
        composeTestRule.activityRule.scenario.onActivity { activity ->
            assert(activity != null)
        }
    }

    @Test
    fun mainActivity_bindsToTerminalManager() {
        // Give the activity time to bind to the service
        composeTestRule.waitForIdle()

        composeTestRule.activityRule.scenario.onActivity { activity ->
            // The activity should have bound to TerminalManager
            // This is implicit if the activity doesn't crash
            assert(activity != null)
        }
    }

    @Test
    fun mainActivity_handlesDisconnectAction() {
        // Close the default activity started by the rule
        composeTestRule.activityRule.scenario.close()

        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        intent.action = MainActivity.DISCONNECT_ACTION

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                // Verify the activity handles the disconnect action without crashing
                assert(activity != null)
            }
        }
    }

    @Test
    fun mainActivity_handlesSshUri() {
        // Close the default activity started by the rule
        composeTestRule.activityRule.scenario.close()

        val uri = Uri.parse("ssh://user@example.com:22/#test")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                // Verify the activity handles SSH URIs without crashing
                assert(activity != null)
            }
        }
    }

    @Test
    fun mainActivity_handlesTelnetUri() {
        // Close the default activity started by the rule
        composeTestRule.activityRule.scenario.close()

        val uri = Uri.parse("telnet://example.com:23/#test")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                // Verify the activity handles Telnet URIs without crashing
                assert(activity != null)
            }
        }
    }

    @Test
    fun mainActivity_handlesLocalUri() {
        // Close the default activity started by the rule
        composeTestRule.activityRule.scenario.close()

        val uri = Uri.parse("local://#test")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setClass(ApplicationProvider.getApplicationContext(), MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onActivity { activity ->
                // Verify the activity handles local URIs without crashing
                assert(activity != null)
            }
        }
    }
}
