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

import android.annotation.TargetApi
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.ui.components.KeyboardGridButton
import org.connectbot.ui.screens.keyboard.KeyboardButtonAppearance
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class KeyboardButtonAppearanceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @TargetApi(34)
    @Config(sdk = [34])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun rendersBackgroundTransparencyTextAndOutline() {
        val layout = KeyboardLayout(name = "Appearance")
        val item = mutableStateOf(KeyboardItem(layoutId = layout.id, backgroundColor = Color.Red.toArgb(), backgroundOpacity = 1f, textColor = Color.Cyan.toArgb(), outlineWidth = 0f))
        rule.setContent {
            ConnectBotTheme {
                Box(Modifier.background(Color.Blue)) {
                    KeyboardGridButton(layout, item.value, emptyList(), 0, {}, listOf(item.value), modifier = Modifier.size(80.dp, 40.dp))
                }
            }
        }
        val button = rule.onNodeWithTag("keyboard_button_${item.value.id}")
        val solid = button.captureToImage().toPixelMap()
        assertEquals(Color.Red, solid[2, 2])
        assertTrue((0 until solid.width).any { x -> (0 until solid.height).any { y -> solid[x, y].green > 0.8f && solid[x, y].blue > 0.8f && solid[x, y].red < 0.2f } })
        rule.runOnIdle { item.value = item.value.copy(backgroundOpacity = 0f) }
        assertEquals(Color.Blue, button.captureToImage().toPixelMap()[2, 2])
        rule.runOnIdle { item.value = item.value.copy(outlineWidth = 3f, outlineColor = Color.Green.toArgb()) }
        val outlined = button.captureToImage().toPixelMap()
        assertEquals(Color.Green, outlined[0, outlined.height / 2])
    }

    @Test fun colorPickersApplyToTheirOwnPropertyAndCancelPreservesTheColor() {
        val item = mutableStateOf(KeyboardItem(layoutId = UUID.randomUUID()))
        rule.setContent { ConnectBotTheme { KeyboardButtonAppearance(item.value, { item.value = it }) } }
        rule.onNodeWithText("Appearance").performClick()
        listOf(R.string.keyboard_background_color, R.string.keyboard_text_color, R.string.keyboard_outline_color).forEachIndexed { index, label ->
            val row = rule.activity.getString(R.string.keyboard_color_value, rule.activity.getString(label), rule.activity.getString(R.string.keyboard_theme_color))
            rule.onNodeWithText(row).performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.label_hex_color)).performTextReplacement("123456")
            rule.onNodeWithText(rule.activity.getString(R.string.button_ok)).performClick()
            rule.runOnIdle {
                val colors = listOf(item.value.backgroundColor, item.value.textColor, item.value.outlineColor)
                assertEquals(0xFF123456.toInt(), colors[index])
                colors.drop(index + 1).forEach { assertNull(it) }
            }
            val selectedRow = rule.activity.getString(R.string.keyboard_color_value, rule.activity.getString(label), "#123456")
            rule.onNodeWithText(selectedRow).performClick()
            rule.onNodeWithText(rule.activity.getString(R.string.label_hex_color)).performTextReplacement("ABCDEF")
            rule.onNodeWithText(rule.activity.getString(R.string.button_cancel)).performClick()
            rule.onNodeWithText(selectedRow).assertExists()
        }
    }

    @Test fun editOpacityAndOutlineThenResetToTheme() {
        val item = mutableStateOf(KeyboardItem(layoutId = UUID.randomUUID(), backgroundColor = 0xFF123456.toInt(), textColor = 0xFF654321.toInt(), outlineColor = 0xFFFFFFFF.toInt()))
        rule.setContent { ConnectBotTheme { KeyboardButtonAppearance(item.value, { item.value = it }) } }
        rule.onNodeWithText("Appearance").performClick()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0].performSemanticsAction(SemanticsActions.SetProgress) { it(0.25f) }
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[1].performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        rule.runOnIdle {
            assertEquals(0.25f, item.value.backgroundOpacity!!, 0.001f)
            assertEquals(3f, item.value.outlineWidth!!, 0.001f)
        }
        rule.onNodeWithContentDescription("Outlined").performClick()
        rule.runOnIdle { assertEquals(0f, item.value.outlineWidth!!, 0f) }
        rule.onNodeWithText("Reset appearance").performClick()
        rule.runOnIdle {
            assertNull(item.value.backgroundColor)
            assertNull(item.value.backgroundOpacity)
            assertNull(item.value.textColor)
            assertNull(item.value.outlineColor)
            assertNull(item.value.outlineWidth)
        }
    }
}
