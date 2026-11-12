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

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.screens.generatepubkey.GeneratePubkeyScreen
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GeneratePubkeyScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun generatePubkeyScreen_displaysTitle() {
        composeTestRule.setContent {
            ConnectBotTheme {
                GeneratePubkeyScreen(onNavigateBack = {})
            }
        }

        composeTestRule
            .onAllNodesWithText("Generate")[0]
            .assertIsDisplayed()
    }

    @Test
    fun generatePubkeyScreen_hasBackButton() {
        var backCalled = false

        composeTestRule.setContent {
            ConnectBotTheme {
                GeneratePubkeyScreen(onNavigateBack = { backCalled = true })
            }
        }

        composeTestRule
            .onNodeWithContentDescription("Navigate up")
            .performClick()

        assertTrue(backCalled)
    }

    @Test
    fun generatePubkeyScreen_displaysNicknameField() {
        composeTestRule.setContent {
            ConnectBotTheme {
                GeneratePubkeyScreen(onNavigateBack = {})
            }
        }

        composeTestRule
            .onNodeWithText("Nickname:")
            .assertIsDisplayed()
    }

    @Test
    fun generatePubkeyScreen_displaysKeyTypeOptions() {
        composeTestRule.setContent {
            ConnectBotTheme {
                GeneratePubkeyScreen(onNavigateBack = {})
            }
        }

        composeTestRule
            .onNodeWithText("Key type")
            .assertIsDisplayed()
    }

    @Test
    fun generatePubkeyScreen_rsaSliderSnapsTo1024BitSteps() {
        composeTestRule.setContent {
            ConnectBotTheme {
                GeneratePubkeyScreen(onNavigateBack = {})
            }
        }

        val bitsLabel = composeTestRule.activity.getString(R.string.prompt_bits)
        val slider = composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        composeTestRule.onNodeWithText("$bitsLabel 2048").assertIsDisplayed()

        listOf(
            4104 to 4096,
            3072 to 3072,
            4096 to 4096,
            8192 to 8192,
            1024 to 1024,
            16384 to 16384,
            1032 to 1024,
            16376 to 16384,
        ).forEach { (requested, expected) ->
            slider.performSemanticsAction(SemanticsActions.SetProgress) { it(requested.toFloat()) }
            composeTestRule.onNodeWithText("$bitsLabel $expected").assertIsDisplayed()
            slider.assertRangeInfoEquals(ProgressBarRangeInfo(expected.toFloat(), 1024f..16384f, 14))
        }
    }

    @Test
    fun generatePubkeyScreen_sliderUsesSelectedKeyTypeConfiguration() {
        composeTestRule.setContent {
            ConnectBotTheme {
                GeneratePubkeyScreen(onNavigateBack = {})
            }
        }

        val bitsLabel = composeTestRule.activity.getString(R.string.prompt_bits)
        val slider = composeTestRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_type_ecdsa))
            .performScrollTo().performClick()
        val ecRange = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(256f..521f, ecRange.range)
        assertEquals(32, ecRange.steps)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(384f) }
        composeTestRule.onNodeWithText("$bitsLabel 384").assertIsDisplayed()

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_type_rsa))
            .performScrollTo().performClick()
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(2048f, 1024f..16384f, 14))
        composeTestRule.onNodeWithText("$bitsLabel 2048").assertIsDisplayed()

        listOf(R.string.pubkey_type_dsa, R.string.pubkey_type_ed25519).forEach { keyTypeLabel ->
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(keyTypeLabel))
                .performScrollTo().performClick()
            slider.assertDoesNotExist()
        }

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.pubkey_type_rsa))
            .performScrollTo().performClick()
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(2048f, 1024f..16384f, 14))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(4104f) }
        composeTestRule.onNodeWithText("$bitsLabel 4096").assertIsDisplayed()
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(4096f, 1024f..16384f, 14))
    }
}
