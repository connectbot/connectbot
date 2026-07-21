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

package org.connectbot

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.Host
import org.connectbot.ui.screens.hosteditor.HostEditorScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HostEditorScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Inject
    lateinit var repository: HostRepository

    private lateinit var navController: TestNavHostController
    private var knownHostsHostId: Long? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        composeTestRule.setContent {
            val context = LocalContext.current
            navController = TestNavHostController(context)
            navController.navigatorProvider.addNavigator(ComposeNavigator())
            ConnectBotTheme {
                NavHost(navController = navController, startDestination = "start") {
                    composable("start") {}
                    composable(
                        route = "hostEditor/{hostId}",
                        arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
                    ) {
                        HostEditorScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToProfile = {},
                            onNavigateToKnownHosts = { knownHostsHostId = it },
                        )
                    }
                }
            }
        }
    }

    private fun navigateToHostEditorScreen(hostId: Long) {
        composeTestRule.runOnUiThread {
            navController.navigate("hostEditor/$hostId")
        }
    }

    @Test
    fun automationRowAlignsWithPreferencesAndRequiresSavedHost() {
        navigateToHostEditorScreen(-1L)
        val automationTitle = composeTestRule.activity.getString(R.string.hostpref_postlogin_title)
        val precedingTitle = composeTestRule.activity.getString(R.string.hostpref_quickdisconnect_title)
        composeTestRule.onNodeWithText(automationTitle).performScrollTo().assertIsNotEnabled()
        val automation = composeTestRule.onNodeWithText(automationTitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val preceding = composeTestRule.onNodeWithText(precedingTitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(preceding.left, automation.left, 0.5f)
    }

    @Test
    fun hostEditorScreen_newHost_addButtonHasContentDescription() {
        navigateToHostEditorScreen(-1L)

        composeTestRule
            .onNodeWithText("Nickname")
            .performClick()
            .performTextInput("test@example.com")

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription("Add host")
            .assertIsDisplayed()
    }

    @Test
    fun hostEditorScreen_newHost_saveButtonIsHiddenByDefault() {
        navigateToHostEditorScreen(-1L)

        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsNotDisplayed()
    }

    @Test
    fun hostEditorScreen_newHost_saveButtonEnabledAfterInput() {
        navigateToHostEditorScreen(-1L)

        composeTestRule
            .onNodeWithText("Nickname")
            .performClick()
            .performTextInput("test@example.com")

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsDisplayed()
    }

    @Test
    fun hostEditorScreen_newHost_callsNavigateBackOnSave() {
        navigateToHostEditorScreen(-1L)

        composeTestRule
            .onNodeWithText("Nickname")
            .performClick()
            .performTextInput("test@example.com")

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("add_host_button")
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentBackStackEntry?.destination?.route == "start"
        }

        composeTestRule.runOnIdle {
            assertTrue(navController.currentBackStackEntry?.destination?.route == "start")
        }
    }

    @Test
    fun hostEditorScreen_hasBackButton() {
        navigateToHostEditorScreen(-1L)

        composeTestRule
            .onNodeWithContentDescription("Navigate up")
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(navController.currentBackStackEntry?.destination?.route == "start")
        }
    }

    @Test
    fun hostEditorScreen_connectionDetailsVisibleAndAdvancedFieldsHiddenByDefault() {
        navigateToHostEditorScreen(-1L)

        composeTestRule.onNodeWithText("Nickname").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quick connect").assertDoesNotExist()
        composeTestRule.onNodeWithText("ssh").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Host").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Port").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.hostpref_ipversion_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.hostpref_password_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.hostpref_jumphost_title)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.hostpref_postlogin_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hostEditorScreen_firstNicknameEntryPopulatesFieldsThroughIncompleteInput() {
        navigateToHostEditorScreen(-1L)
        composeTestRule.onNodeWithText("Nickname").performClick().performTextInput("admin@")
        composeTestRule.onNodeWithTag("add_host_button").assertIsNotDisplayed()
        composeTestRule.onNodeWithText("Nickname").performTextInput("example.com:2222")

        composeTestRule.onNodeWithText("Username").performScrollTo().assert(hasText("admin"))
        composeTestRule.onNodeWithText("Host").performScrollTo().assert(hasText("example.com"))
        composeTestRule.onNodeWithText("Port").performScrollTo().assert(hasText("2222"))
        composeTestRule.onNodeWithTag("add_host_button").assertIsDisplayed()
    }

    @Test
    fun hostEditorScreen_nicknameAndConnectionFieldsAreIndependentAfterFirstEntry() {
        navigateToHostEditorScreen(-1L)
        composeTestRule.onNodeWithText("Nickname").performTextInput("admin@example.com:2222")
        composeTestRule.onNodeWithText("Host").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Nickname").performScrollTo().performTextReplacement("root@other.example:8022")

        composeTestRule.onNodeWithText("Username").performScrollTo().assert(hasText("admin"))
        composeTestRule.onNodeWithText("Host").performScrollTo().assert(hasText("example.com"))
        composeTestRule.onNodeWithText("Port").performScrollTo().assert(hasText("2222"))
        composeTestRule.onNodeWithText("Host").performTextReplacement("third.example")
        composeTestRule.onNodeWithText("Nickname").performScrollTo().assert(hasText("root@other.example:8022"))
    }

    @Test
    fun hostEditorScreen_advancedOptionsRetainValuesAndCollapseWithCustomNickname() {
        navigateToHostEditorScreen(-1L)
        composeTestRule.onNodeWithText("Nickname").performTextInput("user@example.com")
        composeTestRule.onNodeWithText("Host").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Nickname").performScrollTo().performTextReplacement("My Server")
        composeTestRule.onNodeWithText("Show advanced options").performScrollTo().performClick()

        val passwordLabel = composeTestRule.activity.getString(R.string.hostpref_password_title)
        val ipVersionLabel = composeTestRule.activity.getString(R.string.hostpref_ipversion_title)
        composeTestRule.onNodeWithText(ipVersionLabel).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(passwordLabel).performScrollTo().performTextInput("secret")
        composeTestRule.onNodeWithText("Hide advanced options").performScrollTo().performClick()

        composeTestRule.onNodeWithText(ipVersionLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(passwordLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText("My Server").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("example.com").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_host_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show advanced options").performScrollTo().performClick()
        composeTestRule.onNodeWithText(passwordLabel).performScrollTo().assert(hasText("secret"))
    }

    @Test
    fun hostEditorScreen_moshOptionsAreAdvancedAndRetainValues() {
        val host = runBlocking {
            repository.saveHost(
                Host(
                    nickname = "Mosh Server",
                    protocol = "mosh",
                    username = "user",
                    hostname = "example.com",
                    port = 22,
                    moshPort = 60001,
                    moshNetworkTimeout = 86400,
                    moshServer = "custom-mosh-server",
                    locale = "de_DE.UTF-8",
                ),
            )
        }
        navigateToHostEditorScreen(host.id)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Mosh Server").fetchSemanticsNodes().isNotEmpty()
        }

        val commandLabel = composeTestRule.activity.getString(R.string.hostpref_mosh_server_title)
        val moshPortLabel = composeTestRule.activity.getString(R.string.hostpref_mosh_port_title)
        val timeoutLabel = composeTestRule.activity.getString(R.string.hostpref_mosh_network_timeout_title)
        val localeLabel = composeTestRule.activity.getString(R.string.hostpref_locale_title)
        composeTestRule.onNodeWithText("Username").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(commandLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(moshPortLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(localeLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText(timeoutLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText("Show advanced options").performScrollTo().performClick()

        composeTestRule.onNodeWithText(commandLabel).performScrollTo().assert(hasText("custom-mosh-server"))
        composeTestRule.onNodeWithText(moshPortLabel).performScrollTo().assert(hasText("60001"))
        composeTestRule.onNodeWithText(timeoutLabel).performScrollTo().assert(hasText("86400"))
        composeTestRule.onNodeWithText(timeoutLabel).performTextReplacement("-1")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.hostpref_mosh_network_timeout_error))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(timeoutLabel).performTextReplacement("0")
        composeTestRule.onNodeWithText(localeLabel).performScrollTo().assert(hasText("de_DE.UTF-8"))
        composeTestRule.onNodeWithText("Hide advanced options").performScrollTo().performClick()
        composeTestRule.onNodeWithText(commandLabel).assertDoesNotExist()
        composeTestRule.onNodeWithText("Show advanced options").performScrollTo().performClick()
        composeTestRule.onNodeWithText(commandLabel).performScrollTo().assert(hasText("custom-mosh-server"))
        composeTestRule.onNodeWithText(timeoutLabel).performScrollTo().assert(hasText("0"))
    }

    @Test
    fun hostEditorScreen_telnetProtocolShowsHostAndPortWithoutUsernameOrPassword() {
        navigateToHostEditorScreen(-1L)
        composeTestRule.onNodeWithText("ssh").performClick()
        composeTestRule.onNodeWithText("telnet").performClick()

        composeTestRule.onNodeWithText("Nickname").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertDoesNotExist()
        composeTestRule.onNodeWithText("Host").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Port").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Show advanced options").performScrollTo().performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.hostpref_ipversion_title)).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.hostpref_password_title)).assertDoesNotExist()
    }

    @Test
    fun hostEditorScreen_existingSshHost_opensKnownHostKeys() {
        navigateToHostEditorScreen(42L)

        composeTestRule.onNodeWithText("Show advanced options").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("known_host_keys_preference").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertTrue(knownHostsHostId == 42L)
        }
    }

    @Test
    fun hostEditorScreen_localProtocol_hidesUserHostPortFields() {
        navigateToHostEditorScreen(-1L)

        composeTestRule
            .onNodeWithText("Username")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Host")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Port")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("ssh")
            .performScrollTo()
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("local")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("Username")
            .assertIsNotDisplayed()
        composeTestRule
            .onNodeWithText("Host")
            .assertIsNotDisplayed()
        composeTestRule
            .onNodeWithText("Port")
            .assertIsNotDisplayed()
    }

    @Test
    fun hostEditorScreen_localProtocol_saveButtonEnabled() {
        navigateToHostEditorScreen(-1L)

        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsNotDisplayed()

        composeTestRule
            .onNodeWithText("ssh")
            .performScrollTo()
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText("local")
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("add_host_button")
            .assertIsDisplayed()
    }
}
