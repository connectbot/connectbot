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

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.ui.components.KeyboardGrid
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
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
    val rule = createAndroidComposeRule<HiltComponentActivity>()

    @Before fun setUp() {
        hiltRule.inject()
    }

    @Test fun allButtonKindsResizeTogetherAndSpansStayProportional() {
        val layout = mutableStateOf(KeyboardLayout(name = "Sizing", buttonWidth = 45f, buttonHeight = 30f))
        val macro = KeyboardMacro(name = "Run", document = MacroFormat.encode(listOf(MacroAction.Text("ls"))))
        val items = listOf(
            KeyboardItem(layoutId = layout.value.id, target = "arrow_up"),
            KeyboardItem(layoutId = layout.value.id, row = 1, kind = "modifier", target = "ctrl"),
            KeyboardItem(layoutId = layout.value.id, row = 2, kind = "macro", macroId = macro.id),
            KeyboardItem(layoutId = layout.value.id, row = 3, kind = "app", target = "toggle_ime", columnSpan = 2),
        )
        rule.setContent { ConnectBotTheme { KeyboardGrid(layout.value, items, listOf(macro), onPress = {}, modifier = Modifier.heightIn(max = 400.dp)) } }
        items.forEach { rule.onNodeWithTag("keyboard_button_${it.id}").assertWidthIsEqualTo((45 * it.columnSpan).dp).assertHeightIsEqualTo(30.dp) }
        rule.runOnIdle { layout.value = layout.value.copy(buttonWidth = 64f, buttonHeight = 48f) }
        items.forEach { rule.onNodeWithTag("keyboard_button_${it.id}").assertWidthIsEqualTo((64 * it.columnSpan).dp).assertHeightIsEqualTo(48.dp) }
    }

    @Test fun hiddenButtonsDoNotAppearAndUtilityButtonsDispatchTheirOwnAction() {
        val layout = KeyboardLayout(name = "Controls")
        val hidden = KeyboardItem(layoutId = layout.id, target = "escape", visible = false)
        val text = KeyboardItem(layoutId = layout.id, row = 1, kind = "app", target = "text_input")
        val compose = KeyboardItem(layoutId = layout.id, row = 2, kind = "app", target = "toggle_compose")
        val pressed = mutableListOf<KeyboardItem>()
        rule.setContent { ConnectBotTheme { KeyboardGrid(layout, listOf(hidden, text, compose), emptyList(), onPress = { pressed.add(it) }) } }
        rule.onNodeWithTag("keyboard_button_${hidden.id}").assertDoesNotExist()
        rule.onNodeWithTag("keyboard_button_${text.id}").performClick()
        rule.onNodeWithTag("keyboard_button_${compose.id}").performClick()
        assertEquals(listOf(text, compose), pressed)
    }

    @Test fun editingDisplaysHiddenButtonsWithoutExecutingThem() {
        val layout = KeyboardLayout(name = "Edit")
        val item = KeyboardItem(layoutId = layout.id, visible = false)
        var selected: KeyboardItem? = null
        rule.setContent { ConnectBotTheme { KeyboardGrid(layout, listOf(item), emptyList(), onPress = { selected = it }, editing = true) } }
        rule.onNodeWithTag("keyboard_button_${item.id}").performClick()
        assertEquals(item, selected)
    }
}
