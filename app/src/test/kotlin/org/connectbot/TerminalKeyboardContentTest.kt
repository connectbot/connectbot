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

import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.terminal.VTermKey
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
    fun terminalKeyboardContent_displaysCoreKeysAndInvokesCallbacks() {
        var ctrlPressed = false
        var altPressed = false
        var escapePressed = false
        var tabPressed = false
        var interactionCount = 0
        var showImeCalled = false

        setKeyboardContent(
            onCtrlPress = { ctrlPressed = true },
            onAltPress = { altPressed = true },
            onEscPress = { escapePressed = true },
            onTabPress = { tabPressed = true },
            onInteraction = { interactionCount++ },
            onShowIme = { showImeCalled = true },
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_ctrl))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_alt))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_esc))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithText("⇥")
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_show_keyboard))
            .performClick()

        assertTrue(ctrlPressed)
        assertTrue(altPressed)
        assertTrue(escapePressed)
        assertTrue(tabPressed)
        assertTrue(showImeCalled)
        assertEquals(1, interactionCount)
    }

    @Test
    fun terminalKeyboardContent_imeVisibleInvokesHideKeyboard() {
        var hideImeCalled = false
        var interactionCount = 0

        setKeyboardContent(
            imeVisible = true,
            modifierState = ModifierState(
                ctrlState = ModifierLevel.LOCKED,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
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
    fun terminalKeyboardContent_arrowAndFunctionKeysInvokeKeyCallback() {
        val pressedKeys = mutableListOf<Int>()

        setKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.TRANSIENT,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onKeyPress = { pressedKeys += it },
            bumpyArrows = { true },
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_up))
            .performTouchInput {
                down(center)
                up()
            }
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_f1))
            .performClick()

        assertEquals(listOf(VTermKey.UP, VTermKey.FUNCTION_1), pressedKeys)
    }

    @Test
    fun terminalKeyboardContent_reportsHorizontalScrollInteractions() {
        val scrollStates = mutableListOf<Boolean>()

        setKeyboardContent(
            onScrollInProgressChange = { scrollStates += it },
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.button_key_ctrl))
            .performTouchInput { swipeLeft() }

        assertTrue(scrollStates.isNotEmpty())
    }

    @Test
    fun imeToggleInvokesCallbackAndReportsInteraction() {
        var toggles = 0
        var interactions = 0
        setKeyboardContent(
            onToggleComposeMode = { toggles++ },
            onInteraction = { interactions++ },
        )

        composeTestRule.onNodeWithText("IME").assertIsDisplayed().performClick()

        assertEquals(1, toggles)
        assertTrue(interactions > 0)
    }

    @Test
    fun imeToggleCanBeHiddenWithoutRemovingKeyboardToggle() {
        setKeyboardContent(showImeToggleKey = false)

        composeTestRule.onNodeWithText("IME").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.image_description_show_keyboard),
        ).assertIsDisplayed()
    }

    @Test
    fun keyboardKeysVibrateWhenBumpyKeysEnabled() {
        assertKeyHapticFeedback(enabled = true)
    }

    @Test
    fun keyboardKeysDoNotVibrateWhenBumpyKeysDisabled() {
        assertKeyHapticFeedback(enabled = false)
    }

    private fun assertKeyHapticFeedback(enabled: Boolean) {
        val feedback = mutableListOf<Int>()
        val view = object : View(composeTestRule.activity) {
            override fun performHapticFeedback(feedbackConstant: Int): Boolean {
                feedback += feedbackConstant
                return true
            }
        }
        composeTestRule.runOnUiThread {
            (composeTestRule.activity.window.decorView as ViewGroup).addView(view)
        }
        val bumpyKeys = mutableStateOf(enabled)
        setKeyboardContent(bumpyArrows = { bumpyKeys.value }, hapticView = view)

        for (label in listOf(
            R.string.button_key_ctrl,
            R.string.button_key_alt,
            R.string.button_key_esc,
            R.string.button_key_home,
            R.string.button_key_f1,
        )) {
            composeTestRule.onNodeWithText(composeTestRule.activity.getString(label)).performClick()
        }
        composeTestRule.onNodeWithText("⇥").performClick()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.image_description_up),
        ).performTouchInput {
            down(center)
            up()
        }
        composeTestRule.onNodeWithText("IME").performClick()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.image_description_show_keyboard),
        ).performClick()

        assertEquals(if (enabled) List(9) { HapticFeedbackConstants.KEYBOARD_TAP } else emptyList(), feedback)

        // Both ordinary and repeating keys must use the updated preference after recomposition.
        composeTestRule.runOnIdle { bumpyKeys.value = !enabled }
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.button_key_ctrl)).performClick()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.image_description_up),
        ).performTouchInput {
            down(center)
            up()
        }
        assertEquals(List(if (enabled) 9 else 2) { HapticFeedbackConstants.KEYBOARD_TAP }, feedback)
    }

    private fun setKeyboardContent(
        modifierState: ModifierState = ModifierState(
            ctrlState = ModifierLevel.OFF,
            altState = ModifierLevel.OFF,
            shiftState = ModifierLevel.OFF,
        ),
        onCtrlPress: () -> Unit = {},
        onAltPress: () -> Unit = {},
        onEscPress: () -> Unit = {},
        onTabPress: () -> Unit = {},
        onKeyPress: (Int) -> Unit = {},
        onInteraction: () -> Unit = {},
        onHideIme: () -> Unit = {},
        onShowIme: () -> Unit = {},
        onScrollInProgressChange: (Boolean) -> Unit = {},
        imeVisible: Boolean = false,
        bumpyArrows: () -> Boolean = { false },
        hapticView: View? = null,
        showImeToggleKey: Boolean = true,
        onToggleComposeMode: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalView provides (hapticView ?: LocalView.current)) {
                ConnectBotTheme {
                    TerminalKeyboardContent(
                        modifierState = modifierState,
                        onCtrlPress = onCtrlPress,
                        onAltPress = onAltPress,
                        onEscPress = onEscPress,
                        onTabPress = onTabPress,
                        onKeyPress = onKeyPress,
                        onInteraction = onInteraction,
                        onHideIme = onHideIme,
                        onShowIme = onShowIme,
                        onScrollInProgressChange = onScrollInProgressChange,
                        imeVisible = imeVisible,
                        playAnimation = false,
                        bumpyArrows = bumpyArrows(),
                        showImeToggleKey = showImeToggleKey,
                        onToggleComposeMode = onToggleComposeMode,
                    )
                }
            }
        }
    }
}
