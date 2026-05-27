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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.data.migration.MigrationState
import org.connectbot.data.migration.MigrationStatus
import org.connectbot.ui.AuthenticationScreen
import org.connectbot.ui.LoadingScreen
import org.connectbot.ui.MigrationScreen
import org.connectbot.ui.MigrationUiState
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppStateScreensTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun loadingScreen_displaysLoadingMessage() {
        val loadingMessage = composeTestRule.activity.getString(R.string.loading_message)

        composeTestRule.setContent {
            ConnectBotTheme {
                LoadingScreen()
            }
        }

        composeTestRule
            .onNodeWithText(loadingMessage)
            .assertIsDisplayed()
    }

    @Test
    fun authenticationScreen_withoutFragmentActivityContext_showsRetryButton() {
        val title = composeTestRule.activity.getString(R.string.auth_screen_title)
        val unlock = composeTestRule.activity.getString(R.string.auth_screen_unlock_button)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalContext provides ApplicationProvider.getApplicationContext()) {
                ConnectBotTheme {
                    AuthenticationScreen(onAuthenticationSuccess = {})
                }
            }
        }

        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(unlock)
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText(unlock)
            .assertIsDisplayed()
    }

    @Test
    fun migrationScreen_checking_displaysCheckingState() {
        val checking = composeTestRule.activity.getString(R.string.migration_checking)

        composeTestRule.setContent {
            ConnectBotTheme {
                MigrationScreen(
                    uiState = MigrationUiState.Checking,
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Checking database migration status")
            .assertExists()
        composeTestRule
            .onNodeWithText(checking)
            .assertIsDisplayed()
    }

    @Test
    fun migrationScreen_inProgress_displaysStatsAndWarnings() {
        val title = composeTestRule.activity.getString(R.string.migration_title)
        val warningTitle = composeTestRule.activity.getString(R.string.migration_warnings_title)
        val copyWarnings = composeTestRule.activity.getString(R.string.migration_copy_warnings)
        val migrationWarning = composeTestRule.activity.getString(R.string.migration_warning)
        val warning = "Duplicate host name renamed"

        composeTestRule.setContent {
            ConnectBotTheme {
                MigrationScreen(
                    uiState = MigrationUiState.InProgress(
                        MigrationState(
                            status = MigrationStatus.IN_PROGRESS,
                            progress = 0.5f,
                            currentStep = "Migrating hosts",
                            hostsMigrated = 2,
                            pubkeysMigrated = 1,
                            portForwardsMigrated = 3,
                            knownHostsMigrated = 4,
                            warnings = listOf(warning),
                        ),
                    ),
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Migrating hosts")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.migration_stats_hosts, 2))
            .assertIsDisplayed()
        composeTestRule
            .onNode(hasText(warning, substring = true))
            .assertExists()
        composeTestRule
            .onNodeWithText(warningTitle)
            .assertExists()
        composeTestRule
            .onNodeWithText(copyWarnings)
            .assertExists()
        composeTestRule
            .onNodeWithText(migrationWarning)
            .assertExists()
    }

    @Test
    fun migrationScreen_failed_displaysErrorDebugLogAndRetries() {
        var retryCalled = false
        val title = composeTestRule.activity.getString(R.string.migration_failed_title)
        val error = "Database corruption detected"
        val retry = composeTestRule.activity.getString(R.string.migration_retry)
        val debugLogTitle = composeTestRule.activity.getString(R.string.migration_debug_log_title)
        val copyDebugLog = composeTestRule.activity.getString(R.string.migration_copy_debug_log)
        val errorMessage = composeTestRule.activity.getString(R.string.migration_failed_message, error)

        composeTestRule.setContent {
            ConnectBotTheme {
                MigrationScreen(
                    uiState = MigrationUiState.Failed(
                        error = error,
                        debugLog = listOf(
                            "Starting database migration",
                            "ERROR: Database corruption detected",
                        ),
                    ),
                    onRetry = { retryCalled = true },
                )
            }
        }

        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(errorMessage)
            .assertExists()
        composeTestRule
            .onNodeWithText(debugLogTitle)
            .assertExists()
        composeTestRule
            .onNodeWithText("ERROR: Database corruption detected")
            .assertExists()
        composeTestRule
            .onNodeWithText(copyDebugLog)
            .assertExists()
        composeTestRule
            .onNodeWithText(retry)
            .performClick()

        assertTrue(retryCalled)
    }

    @Test
    fun migrationScreen_completed_displaysNoMigrationContent() {
        val title = composeTestRule.activity.getString(R.string.migration_title)

        composeTestRule.setContent {
            ConnectBotTheme {
                MigrationScreen(
                    uiState = MigrationUiState.Completed,
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onAllNodes(hasText(title))
            .fetchSemanticsNodes()
            .let { nodes -> assertTrue("Completed state should not render migration content", nodes.isEmpty()) }
    }
}
