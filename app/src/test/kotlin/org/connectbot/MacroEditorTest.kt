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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.ui.screens.keyboard.MacroEditor
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MacroEditorTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var saved: KeyboardMacro? = null
    private var dirty = false
    private var canSave = false
    private var cancelled = false
    private var draft: KeyboardMacro? = null

    private fun show(macro: KeyboardMacro, done: Boolean = false) {
        rule.setContent {
            ConnectBotTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MacroEditor(
                        macro,
                        onSave = { saved = it },
                        onCancel = { cancelled = true },
                        onDirtyChange = { dirty = it },
                        onDraftChange = { draft = it },
                        onCanSaveChange = { canSave = it },
                        done = done,
                    )
                }
            }
        }
    }

    private fun click(text: String, index: Int = 0) {
        rule.onAllNodesWithText(text)[index].performScrollTo().performClick()
    }

    private fun replace(label: String, value: String) {
        rule.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
    }

    @Test fun visualStepsCanBeEditedReorderedAndDeleted() {
        val macro = KeyboardMacro(name = "Deploy", document = MacroFormat.encode(listOf(MacroAction.Text("old"))))
        show(macro)
        replace("Name", "Deploy safely")
        replace("Text to send", "deploy")
        click("Add bytes")
        replace("Exact bytes (hexadecimal)", "0d 0a")
        click("Move up", 1)
        click("Save")
        assertEquals(macro.id, saved!!.id)
        assertEquals("Deploy safely", saved!!.name)
        assertEquals(listOf(MacroAction.Bytes("0d0a"), MacroAction.Text("deploy")), MacroFormat.decode(saved!!.document))
        click("Move down", 0)
        click("Delete", 1)
        click("Save")
        assertEquals(listOf(MacroAction.Text("deploy")), MacroFormat.decode(saved!!.document))
        click("Delete")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.runOnIdle { assertFalse(canSave) }
    }

    @Test fun invalidTextCannotBeSavedAndCorrectedSourceReturnsToVisualEditor() {
        show(KeyboardMacro(name = "Command", document = MacroFormat.encode(listOf(MacroAction.Text("old")))))
        click("Text editor")
        replace("Macro steps", "invalid command")
        rule.onNodeWithText("Save").assertIsNotEnabled()
        click("Visual editor")
        rule.onNodeWithText("Macro steps").assertExists()
        rule.runOnIdle {
            assertTrue(dirty)
            assertFalse(canSave)
            assertNull(saved)
        }
        replace("Macro steps", "text \"hello\"\nkey ctrl+c\nbytes 0d 0a")
        click("Visual editor")
        click("Save")
        assertEquals(listOf(MacroAction.Text("hello"), MacroAction.Key(character = "c", modifiers = listOf("ctrl")), MacroAction.Bytes("0d0a")), MacroFormat.decode(saved!!.document))
    }

    @Test fun characterKeyAndModifiersRoundTripThroughTextMode() {
        show(KeyboardMacro(name = "Interrupt", document = MacroFormat.encode(listOf(MacroAction.Key(key = "enter")))))
        click("Enter")
        click("Character")
        replace("Character", "c")
        rule.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        click("Text editor")
        rule.onNodeWithText("key ctrl+c").assertExists()
        click("Visual editor")
        rule.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        click("Character")
        click("Esc")
        click("Save")
        assertEquals(listOf(MacroAction.Key(key = "escape")), MacroFormat.decode(saved!!.document))
    }

    @Test fun blankNameDisablesDoneAndCancelDoesNotSave() {
        show(KeyboardMacro(name = "Command", document = MacroFormat.encode(listOf(MacroAction.Text("run")))), done = true)
        rule.runOnIdle {
            assertFalse(dirty)
            assertTrue(canSave)
        }
        replace("Name", " ")
        rule.onNodeWithText("Done").assertIsNotEnabled()
        rule.runOnIdle { assertFalse(canSave) }
        replace("Name", "Renamed")
        rule.runOnIdle {
            assertEquals("Renamed", draft!!.name)
            assertTrue(canSave)
        }
        click("Cancel")
        assertTrue(cancelled)
        assertNull(saved)
    }

    @Test fun unsupportedMacroIsPreservedAndCannotBeSaved() {
        val source = "{\"version\":999,\"actions\":[]}"
        show(KeyboardMacro(name = "Future macro", document = source))
        rule.onNodeWithText(source).assertExists()
        rule.onNodeWithText("Save").assertDoesNotExist()
        click("Cancel")
        assertTrue(cancelled)
        assertNull(saved)
    }
}
