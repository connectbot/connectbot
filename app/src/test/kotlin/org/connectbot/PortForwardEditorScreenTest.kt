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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.ui.screens.portforwardlist.PortForwardEditorScreenContent
import org.connectbot.ui.screens.portforwardlist.PortForwardEditorTestTags
import org.connectbot.ui.screens.portforwardlist.PortForwardEditorUiState
import org.connectbot.ui.screens.portforwardlist.SourceAddressOption
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.HostConstants
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PortForwardEditorScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private fun show(
        state: PortForwardEditorUiState,
        modifier: Modifier = Modifier,
        onNavigateBack: () -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                PortForwardEditorScreenContent(
                    uiState = state,
                    onNavigateBack = onNavigateBack,
                    onNicknameChange = {},
                    onTypeChange = {},
                    onSourcePortChange = {},
                    onSourceAddressOptionChange = {},
                    onSpecificAddressChange = {},
                    onDestinationChange = {},
                    onSave = onSave,
                    modifier = modifier,
                )
            }
        }
    }

    @Test
    fun newForwardCanSaveUntouchedDefaults() {
        var saved = false
        show(PortForwardEditorUiState(isLoading = false, hasUnsavedChanges = true)) { saved = true }

        composeTestRule.onNodeWithContentDescription("Create port forward").assertIsDisplayed().performClick()
        assertTrue(saved)
    }

    @Test
    fun existingForwardNeedsValidEdit() {
        show(PortForwardEditorUiState(forwardId = 12L, isLoading = false))
        composeTestRule.onNodeWithContentDescription("Save").assertDoesNotExist()
    }

    @Test
    fun invalidPortHidesSave() {
        show(PortForwardEditorUiState(isLoading = false, hasUnsavedChanges = true, sourcePort = "0"))
        composeTestRule.onNodeWithContentDescription("Create port forward").assertDoesNotExist()
    }

    @Test
    fun remoteForwardRequiresSpecificAddress() {
        show(
            PortForwardEditorUiState(
                isLoading = false,
                hasUnsavedChanges = true,
                type = HostConstants.PORTFORWARD_REMOTE,
                sourceAddressOption = SourceAddressOption.SPECIFIC,
            ),
        )

        composeTestRule.onNodeWithContentDescription("Create port forward").assertDoesNotExist()
    }

    @Test
    fun shortViewportCanScrollLastFieldAboveSaveFab() {
        show(
            PortForwardEditorUiState(isLoading = false, hasUnsavedChanges = true),
            modifier = Modifier.fillMaxWidth().height(320.dp),
        )

        composeTestRule.onNodeWithTag(PortForwardEditorTestTags.DESTINATION_FIELD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Create port forward").assertIsDisplayed()
    }

    @Test
    fun editedForwardPromptsBeforeDiscarding() {
        var navigatedBack = false
        show(
            PortForwardEditorUiState(isLoading = false, hasUnsavedChanges = true, hasEdited = true),
            onNavigateBack = { navigatedBack = true },
        )

        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(!navigatedBack)

        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()
        composeTestRule.onNodeWithText("Discard").performClick()
        assertTrue(navigatedBack)
    }

    @Test
    fun editedForwardCanSaveFromDiscardPrompt() {
        var saved = false
        show(
            PortForwardEditorUiState(isLoading = false, hasUnsavedChanges = true, hasEdited = true),
            onSave = { saved = true },
        )

        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()
        composeTestRule.onNodeWithText("Save").performClick()
        assertTrue(saved)
    }

    @Test
    fun invalidEditCannotSaveFromDiscardPrompt() {
        show(PortForwardEditorUiState(isLoading = false, hasUnsavedChanges = true, hasEdited = true, sourcePort = "0"))

        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()
        composeTestRule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun untouchedNewForwardLeavesWithoutPrompt() {
        var navigatedBack = false
        show(
            PortForwardEditorUiState(isLoading = false, hasUnsavedChanges = true),
            onNavigateBack = { navigatedBack = true },
        )

        composeTestRule.onNodeWithContentDescription("Navigate up").performClick()
        assertTrue(navigatedBack)
        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }
}
