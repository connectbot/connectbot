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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.ui.components.KeyboardGrid
import org.connectbot.ui.components.ReorderableKeyboardGrid
import org.connectbot.ui.screens.keyboard.moveKeyboardItem
import org.connectbot.ui.screens.keyboard.pinKeyboardItem
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardPinnedGridTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun fittedEditorCombinesBothRegionsWithoutPinningSections() {
        val layout = KeyboardLayout(name = "Fit", fitToWidth = true)
        val key = KeyboardItem(layoutId = layout.id, target = "escape")
        val dock = KeyboardItem(layoutId = layout.id, target = "enter", pinned = true)
        rule.setContent {
            ConnectBotTheme {
                ReorderableKeyboardGrid(layout, listOf(key, dock), emptyList(), 0, {}, { _, _ -> }, { _, _, _ -> }, Modifier.width(240.dp).heightIn(max = 240.dp))
            }
        }
        rule.onNodeWithText("Pinned keys").assertDoesNotExist()
        rule.onNodeWithText("Scrollable keys").assertDoesNotExist()
        val first = rule.onNodeWithTag("keyboard_button_${key.id}").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag("keyboard_button_${dock.id}").getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
        assertEquals(first.right, second.left)
        assertEquals(first.right - first.left, second.right - second.left)
    }

    @Test fun rtlDockUsesLeftEdge() {
        val layout = KeyboardLayout(name = "RTL")
        val key = KeyboardItem(layoutId = layout.id, target = "escape")
        val dock = KeyboardItem(layoutId = layout.id, target = "enter", pinned = true)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ConnectBotTheme { KeyboardGrid(layout, listOf(key, dock), emptyList(), onPress = {}, modifier = Modifier.width(240.dp)) }
            }
        }
        val pinned = rule.onNodeWithTag("keyboard_pinned_region").getUnclippedBoundsInRoot()
        val scrolling = rule.onNodeWithTag("keyboard_scrolling_region").getUnclippedBoundsInRoot()
        assertTrue(pinned.right <= scrolling.left)
    }

    @Test fun fittedConsoleCombinesPreviouslyPinnedKeys() {
        val layout = KeyboardLayout(name = "Fit", fitToWidth = true)
        val key = KeyboardItem(layoutId = layout.id, target = "escape")
        val dock = KeyboardItem(layoutId = layout.id, target = "enter", pinned = true)
        rule.setContent {
            ConnectBotTheme { KeyboardGrid(layout, listOf(key, dock), emptyList(), onPress = {}, modifier = Modifier.width(240.dp)) }
        }
        rule.onNodeWithTag("keyboard_pinned_region").assertDoesNotExist()
        val first = rule.onNodeWithTag("keyboard_button_${key.id}").getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag("keyboard_button_${dock.id}").getUnclippedBoundsInRoot()
        assertEquals(first.right, second.left)
        assertEquals(first.right - first.left, second.right - second.left)
    }

    @Test fun dragKeyBackIntoEmptyGridSlot() {
        val layout = KeyboardLayout(name = "Gap", fitToWidth = true, buttonWidth = 80f, buttonHeight = 44f)
        val key = KeyboardItem(layoutId = layout.id, target = "escape", column = 1)
        val items = mutableStateOf(listOf(key))
        rule.setContent {
            ConnectBotTheme {
                ReorderableKeyboardGrid(
                    layout,
                    items.value,
                    emptyList(),
                    0,
                    onPress = {},
                    onReorder = { from, to -> items.value = moveKeyboardItem(layout, items.value, from, to) },
                    onMove = { moved, row, column -> items.value = items.value.map { if (it.id == moved.id) moved.copy(row = row, column = column) else it } },
                    modifier = Modifier.width(240.dp).heightIn(max = 240.dp),
                )
            }
        }
        val source = rule.onNodeWithTag("keyboard_button_${key.id}")
        val gap = rule.onNodeWithTag("keyboard_gap_false_0_0")
        val start = source.getUnclippedBoundsInRoot()
        val end = gap.getUnclippedBoundsInRoot()
        val delta = with(rule.density) { Offset(((end.left + end.right - start.left - start.right) / 2).toPx(), ((end.top + end.bottom - start.top - start.bottom) / 2).toPx()) }
        source.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1000)
        source.performTouchInput {
            moveBy(delta / 2f, delayMillis = 100)
            moveBy(delta / 2f, delayMillis = 100)
        }
        rule.waitForIdle()
        source.performTouchInput { up() }
        rule.runOnIdle { assertEquals(0, items.value.single().column) }
    }

    private fun dragBetweenSections(empty: Boolean) {
        val layout = KeyboardLayout(name = "Sections", buttonWidth = 80f, buttonHeight = 44f)
        val key = KeyboardItem(layoutId = layout.id, target = "escape")
        val dock = KeyboardItem(layoutId = layout.id, kind = "app", target = "toggle_ime", pinned = true)
        val items = mutableStateOf(if (empty) listOf(key) else listOf(key, dock))
        rule.setContent {
            ConnectBotTheme {
                ReorderableKeyboardGrid(
                    layout, items.value, emptyList(), 0, onPress = {},
                    onReorder = { from, to -> items.value = moveKeyboardItem(layout, items.value, from, to) },
                    onMove = { _, _, _ -> },
                    onPin = { id, pinned -> items.value = pinKeyboardItem(layout, items.value, id, pinned) },
                    modifier = Modifier.width(240.dp).heightIn(max = 240.dp),
                )
            }
        }
        val source = rule.onNodeWithTag("keyboard_button_${key.id}")
        val target = if (empty) rule.onNodeWithText("Drag keys here") else rule.onNodeWithTag("keyboard_button_${dock.id}")
        val start = source.getUnclippedBoundsInRoot()
        val end = target.getUnclippedBoundsInRoot()
        val delta = with(rule.density) { Offset(((end.left + end.right - start.left - start.right) / 2).toPx(), ((end.top + end.bottom - start.top - start.bottom) / 2).toPx()) }
        source.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1000)
        source.performTouchInput {
            moveBy(delta / 2f, delayMillis = 100)
            moveBy(delta / 2f, delayMillis = 100)
        }
        rule.waitForIdle()
        source.performTouchInput { up() }
        rule.runOnIdle {
            assertTrue(items.value.single { it.id == key.id }.pinned)
            assertEquals(0, items.value.single { it.id == key.id }.column)
        }
    }

    @Test fun dragIntoPopulatedPinnedSection() = dragBetweenSections(false)

    @Test fun dragIntoEmptyPinnedSection() = dragBetweenSections(true)
}
