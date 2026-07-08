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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.keyboard.DefaultKeyboardLayouts
import org.connectbot.keyboard.KeySpec
import org.connectbot.keyboard.KeyboardLayoutSpec
import org.connectbot.keyboard.ModifierKey
import org.connectbot.keyboard.SpecialKey
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.ui.components.TerminalKeyboardContent
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TerminalKeyboardContentTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun defaultLayout_rendersBothRowsAndDispatchesKeys() {
        val actions = mutableListOf<KeySpec>()
        var interactionCount = 0
        var textInputOpened = false
        var showImeCalled = false

        setKeyboardContent(
            onKeyAction = { actions += it },
            onInteraction = { interactionCount++ },
            onOpenTextInput = { textInputOpened = true },
            onShowIme = { showImeCalled = true },
        )

        // Row 1 (Esc) and row 2 (Tab) are both present in the default layout.
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_esc))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText("⇥")
            .assertIsDisplayed()
            .performClick()
        // A symbol key sends its literal text.
        composeTestRule
            .onNodeWithText("/")
            .assertIsDisplayed()
            .performClick()
        // Pinned buttons still work.
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.terminal_keyboard_text_input_button))
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_show_keyboard))
            .performClick()

        assertEquals(
            listOf(
                KeySpec.Special(SpecialKey.ESC),
                KeySpec.Special(SpecialKey.TAB),
                KeySpec.Text("/"),
            ),
            actions,
        )
        assertTrue(textInputOpened)
        assertTrue(showImeCalled)
        // Two pinned-button presses fire onInteraction.
        assertEquals(2, interactionCount)
    }

    @Test
    fun imeVisible_invokesHideKeyboard() {
        var hideImeCalled = false
        var interactionCount = 0

        setKeyboardContent(
            imeVisible = true,
            onHideIme = { hideImeCalled = true },
            onInteraction = { interactionCount++ },
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_hide_keyboard))
            .assertIsDisplayed()
            .performClick()

        assertTrue(hideImeCalled)
        assertEquals(1, interactionCount)
    }

    @Test
    fun arrowKey_dispatchesSpecialKey() {
        val actions = mutableListOf<KeySpec>()

        setKeyboardContent(
            onKeyAction = { actions += it },
            bumpyArrows = true,
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_up))
            .performTouchInput {
                down(center)
                up()
            }

        assertEquals(listOf(KeySpec.Special(SpecialKey.UP)), actions)
    }

    @Test
    fun fnKey_opensPopupAndDispatchesFunctionKey() {
        val actions = mutableListOf<KeySpec>()

        setKeyboardContent(
            onKeyAction = { actions += it },
        )

        // Fn key does not dispatch itself; it opens the grid popup.
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_fn))
            .performClick()
        composeTestRule.onNodeWithTag("fn_popup").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_f5))
            .performClick()

        assertEquals(listOf(KeySpec.Special(SpecialKey.F5)), actions)
    }

    @Test
    fun modifierKey_dispatchesModifier() {
        val actions = mutableListOf<KeySpec>()

        setKeyboardContent(
            onKeyAction = { actions += it },
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_ctrl))
            .performClick()

        assertEquals(listOf<KeySpec>(KeySpec.Modifier(ModifierKey.CTRL)), actions)
    }

    @Test
    fun iconOnlyKey_exposesLabelAsContentDescription() {
        val actions = mutableListOf<KeySpec>()

        setKeyboardContent(
            layout = KeyboardLayoutSpec(
                listOf(listOf(KeySpec.Text("ls -la", label = "List", icon = "folder"))),
            ),
            onKeyAction = { actions += it },
        )

        // The key renders as an icon, so TalkBack must get its label instead.
        composeTestRule
            .onNodeWithContentDescription("List")
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf<KeySpec>(KeySpec.Text("ls -la", label = "List", icon = "folder")), actions)
    }

    @Test
    fun textSpecialKey_exposesContentDescription() {
        val actions = mutableListOf<KeySpec>()

        setKeyboardContent(
            onKeyAction = { actions += it },
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_send_tab_character))
            .assertIsDisplayed()
            .performClick()

        assertEquals(listOf<KeySpec>(KeySpec.Special(SpecialKey.TAB)), actions)
    }

    @Test
    fun reportsHorizontalScrollInteractions() {
        val scrollStates = mutableListOf<Boolean>()

        setKeyboardContent(
            onScrollInProgressChange = { scrollStates += it },
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_esc))
            .performTouchInput { swipeLeft() }

        assertTrue(scrollStates.isNotEmpty())
    }

    private fun setKeyboardContent(
        layout: KeyboardLayoutSpec = DefaultKeyboardLayouts.default,
        modifierState: ModifierState = ModifierState(
            ctrlState = ModifierLevel.OFF,
            altState = ModifierLevel.OFF,
            shiftState = ModifierLevel.OFF,
        ),
        onKeyAction: (KeySpec) -> Unit = {},
        onInteraction: () -> Unit = {},
        onHideIme: () -> Unit = {},
        onShowIme: () -> Unit = {},
        onOpenTextInput: () -> Unit = {},
        onOpenSnippets: () -> Unit = {},
        onLongPress: () -> Unit = {},
        onScrollInProgressChange: (Boolean) -> Unit = {},
        imeVisible: Boolean = false,
        bumpyArrows: Boolean = false,
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                TerminalKeyboardContent(
                    layout = layout,
                    modifierState = modifierState,
                    onKeyAction = onKeyAction,
                    onInteraction = onInteraction,
                    onHideIme = onHideIme,
                    onShowIme = onShowIme,
                    onOpenTextInput = onOpenTextInput,
                    onOpenSnippets = onOpenSnippets,
                    onLongPress = onLongPress,
                    onScrollInProgressChange = onScrollInProgressChange,
                    imeVisible = imeVisible,
                    playAnimation = false,
                    bumpyArrows = bumpyArrows,
                )
            }
        }
    }
}
