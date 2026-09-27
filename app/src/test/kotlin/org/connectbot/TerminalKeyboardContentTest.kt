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

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.KeyboardPreset
import org.connectbot.data.keyboard.KeyboardPresets
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.ui.components.KeyboardGrid
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

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

    @Test fun layerButtonsJumpDirectlyAndCycleWithoutDispatchingContentKeys() {
        val layout = KeyboardLayout(name = "Layers", fitToWidth = true)
        val base = KeyboardItem(layoutId = layout.id)
        val jump = KeyboardItem(layoutId = layout.id, column = 1, kind = "layer", target = "4")
        val fourth = KeyboardItem(layoutId = layout.id, layer = 4, target = "f12")
        val back = KeyboardItem(layoutId = layout.id, layer = 4, column = 1, kind = "layer", target = "0")
        val cycle = KeyboardItem(layoutId = layout.id, layer = 4, column = 2, kind = "layer", target = "next")
        val empty = KeyboardItem(layoutId = layout.id, column = 2, kind = "layer", target = "2")
        val session = mutableIntStateOf(0)
        val pressed = mutableListOf<KeyboardItem>()
        rule.setContent { ConnectBotTheme { KeyboardGrid(layout, listOf(base, jump, fourth, back, cycle, empty), emptyList(), onPress = { pressed.add(it) }, sessionKey = session.value) } }
        rule.onNodeWithTag("keyboard_button_${empty.id}").assertIsNotEnabled()
        rule.onNodeWithTag("keyboard_button_${jump.id}").performClick()
        rule.onNodeWithTag("keyboard_button_${base.id}").assertDoesNotExist()
        rule.onNodeWithTag("keyboard_button_${fourth.id}").assertIsDisplayed()
        rule.onNodeWithTag("keyboard_button_${back.id}").performClick()
        rule.onNodeWithTag("keyboard_button_${base.id}").assertIsDisplayed()
        rule.onNodeWithTag("keyboard_button_${jump.id}").performClick()
        rule.onNodeWithTag("keyboard_button_${cycle.id}").performClick()
        rule.onNodeWithTag("keyboard_button_${base.id}").assertIsDisplayed()
        rule.onNodeWithTag("keyboard_button_${jump.id}").performClick()
        val cycleAction = rule.onNodeWithTag("keyboard_button_${cycle.id}").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        // Two queued taps before recomposition must advance twice, rather than reuse the old layer.
        rule.runOnIdle {
            cycleAction()
            cycleAction()
        }
        rule.onNodeWithTag("keyboard_button_${fourth.id}").assertIsDisplayed()
        rule.runOnIdle { session.value++ }
        rule.onNodeWithTag("keyboard_button_${base.id}").assertIsDisplayed()
        assertTrue(pressed.all { it.kind == "layer" })
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1000dp-h1000dp")
    fun presetsFitNarrowAndWideContainersInRtlWithLargeFonts() {
        val selected = mutableStateOf(KeyboardPreset.CITRUS)
        val containerWidth = mutableIntStateOf(320)
        val drafts = KeyboardPreset.entries.associateWith { KeyboardPresets.create(it, it.id, rule.activity::getString) }
        rule.setContent {
            ConnectBotTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(containerWidth.value.dp).testTag("container")) {
                        val draft = drafts.getValue(selected.value)
                        KeyboardGrid(draft.layout, draft.items, draft.macros, onPress = {}, modifier = Modifier.fillMaxWidth().testTag("grid"))
                    }
                }
            }
        }
        for (preset in KeyboardPreset.entries) {
            for (width in listOf(320, 411, 800)) {
                rule.runOnIdle {
                    selected.value = preset
                    containerWidth.value = width
                }
                val draft = drafts.getValue(preset)
                val grid = rule.onNodeWithTag("grid").fetchSemanticsNode()
                assertTrue(!grid.config.contains(SemanticsActions.ScrollBy))
                val bounds = rule.onNodeWithTag("container").fetchSemanticsNode().boundsInRoot
                val keys = draft.items.filter { it.layer == 0 }
                val rects = keys.map { item ->
                    val node = rule.onNodeWithTag("keyboard_button_${item.id}")
                    node.assertIsDisplayed()
                    node.fetchSemanticsNode().boundsInRoot.also { rect ->
                        assertTrue(rect.left >= bounds.left && rect.right <= bounds.right)
                        assertTrue(rect.height > 0f)
                    }
                }
                assertEquals(2, rects.map { it.top }.distinct().size)
                // Physical arrows retain their positions when the surrounding UI uses RTL.
                assertTrue(rects[keys.indexOfFirst { it.target == "arrow_left" }].left < rects[keys.indexOfFirst { it.target == "arrow_right" }].left)
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @SuppressLint("NewApi")
    fun capturePresetPreviews() {
        val drafts = KeyboardPreset.entries.map { KeyboardPresets.create(it, rule.activity.getString(it.nameRes), rule.activity::getString) }
        rule.setContent {
            ConnectBotTheme {
                Column(Modifier.width(400.dp).background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                    drafts.forEach { draft ->
                        Column(Modifier.testTag("preview_${draft.layout.presetKey}")) {
                            androidx.compose.material3.Text(draft.layout.name)
                            androidx.compose.material3.Text(rule.activity.getString(KeyboardPresets.find(draft.layout.presetKey)!!.subtitleRes))
                            KeyboardGrid(draft.layout, draft.items, draft.macros, onPress = {}, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        drafts.forEach { draft ->
            val screenshot = File("build/reports/keyboard-presets/${draft.layout.presetKey}.png")
            screenshot.parentFile!!.mkdirs()
            val preview = rule.onNodeWithTag("preview_${draft.layout.presetKey}")
            preview.assertIsDisplayed()
            screenshot.outputStream().use { preview.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun fittedRowsShrinkToAvailableHeightWithoutScrolling() {
        val draft = KeyboardPresets.create(KeyboardPreset.CLAW, "Claw", rule.activity::getString)
        rule.setContent {
            ConnectBotTheme {
                KeyboardGrid(draft.layout, draft.items, draft.macros, onPress = {}, modifier = Modifier.width(320.dp).heightIn(max = 60.dp).testTag("grid"))
            }
        }
        rule.onNodeWithTag("grid").assertHeightIsEqualTo(60.dp)
        draft.items.filter { it.layer == 0 }.forEach { item ->
            rule.onNodeWithTag("keyboard_button_${item.id}").assertHeightIsEqualTo(30.dp)
        }
    }
}
