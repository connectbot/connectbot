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

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.repeatsOnHold
import org.connectbot.data.keyboard.usesHapticFeedback
import org.connectbot.ui.components.KeyboardGrid
import org.connectbot.ui.screens.keyboard.KeyboardButtonBehavior
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class KeyboardButtonBehaviorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun defaultsAndOverridesKeepControlsSingleFire() {
        val arrow = KeyboardItem(layoutId = UUID.randomUUID(), target = "arrow_up")
        assertTrue(arrow.repeatsOnHold())
        assertFalse(arrow.copy(repeatOnHold = false).repeatsOnHold())
        assertFalse(arrow.copy(target = "backspace").repeatsOnHold())
        assertTrue(arrow.copy(target = "backspace", repeatOnHold = true).repeatsOnHold())
        assertTrue(arrow.copy(kind = "macro", repeatOnHold = true).repeatsOnHold())
        listOf("modifier", "app", "layer").forEach { kind -> assertFalse(arrow.copy(kind = kind, repeatOnHold = true).repeatsOnHold()) }
        assertTrue(arrow.usesHapticFeedback(true))
        assertFalse(arrow.usesHapticFeedback(false))
        assertFalse(arrow.copy(hapticFeedback = false).usesHapticFeedback(true))
        assertTrue(arrow.copy(hapticFeedback = true).usesHapticFeedback(false))
    }

    @Test fun editorControlsRepeatAndHapticOverrides() {
        val item = mutableStateOf(KeyboardItem(layoutId = UUID.randomUUID(), target = "arrow_up"))
        rule.setContent { ConnectBotTheme { KeyboardButtonBehavior(item.value, { item.value = it }) } }
        rule.onNodeWithText("Behavior").performClick()
        rule.onNodeWithContentDescription("Repeat while held").assertIsOn().performClick().assertIsOff()
        rule.runOnIdle { assertEquals(false, item.value.repeatOnHold) }
        rule.onNodeWithText("Use default repeat behavior").performClick()
        rule.runOnIdle { assertNull(item.value.repeatOnHold) }
        rule.onNodeWithText("Haptic feedback: App setting").performClick()
        rule.onNodeWithText("Off").performClick()
        rule.runOnIdle { assertEquals(false, item.value.hapticFeedback) }
        rule.onNodeWithText("Haptic feedback: Off").performClick()
        rule.onNodeWithText("On").performClick()
        rule.runOnIdle { assertEquals(true, item.value.hapticFeedback) }
        rule.onNodeWithText("Haptic feedback: On").performClick()
        rule.onNodeWithText("App setting").performClick()
        rule.runOnIdle { assertNull(item.value.hapticFeedback) }
    }

    @Test fun touchUsesUpdatedHapticAndActionSettingsWithoutRecreatingTheButton() {
        val layout = KeyboardLayout(name = "Live edits")
        val item = mutableStateOf(KeyboardItem(layoutId = layout.id, target = "escape"))
        var received: KeyboardItem? = null
        rule.setContent { ConnectBotTheme { KeyboardGrid(layout, listOf(item.value), emptyList(), onPress = { received = it }) } }
        val button = rule.onNodeWithTag("keyboard_button_${item.value.id}")
        button.performTouchInput {
            down(center)
            up()
        }
        rule.runOnIdle { item.value = item.value.copy(hapticFeedback = false) }
        button.performTouchInput {
            down(center)
            up()
        }
        rule.runOnIdle {
            assertEquals(false, received?.hapticFeedback)
            item.value = item.value.copy(target = "backspace")
        }
        button.performTouchInput {
            down(center)
            up()
        }
        rule.runOnIdle { assertEquals("backspace", received?.target) }
    }

    @Test fun disabledRepeatFiresOnceAfterALongHold() {
        val layout = KeyboardLayout(name = "Single activation")
        val item = KeyboardItem(layoutId = layout.id, target = "arrow_up", repeatOnHold = false)
        var presses = 0
        rule.setContent { ConnectBotTheme { KeyboardGrid(layout, listOf(item), emptyList(), onPress = { presses++ }) } }
        val button = rule.onNodeWithTag("keyboard_button_${item.id}")
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800)
        rule.runOnIdle { assertEquals(0, presses) }
        button.performTouchInput { up() }
        rule.runOnIdle { assertEquals(1, presses) }
    }

    @Test fun holdRepeatsAndStopsOnReleaseWhileDisabledRepeatFiresOnce() {
        val layout = KeyboardLayout(name = "Repeat")
        val item = mutableStateOf(KeyboardItem(layoutId = layout.id, target = "arrow_up"))
        var presses = 0
        rule.setContent { ConnectBotTheme { KeyboardGrid(layout, listOf(item.value), emptyList(), onPress = { presses++ }) } }
        val button = rule.onNodeWithTag("keyboard_button_${item.value.id}")
        rule.mainClock.autoAdvance = false
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800)
        button.performTouchInput { up() }
        rule.runOnIdle { assertTrue(presses > 1) }
        val afterRelease = presses
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle {
            assertEquals(afterRelease, presses)
        }
        rule.mainClock.autoAdvance = true
        rule.runOnIdle { item.value = item.value.copy(repeatOnHold = false) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(800)
        rule.runOnIdle { assertEquals(afterRelease, presses) }
        button.performTouchInput { up() }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertEquals(afterRelease + 1, presses) }
    }
}
