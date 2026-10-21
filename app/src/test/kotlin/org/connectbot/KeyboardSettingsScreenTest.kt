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
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardRepository
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.ui.screens.keyboard.KeyboardSettingsScreen
import org.connectbot.ui.screens.keyboard.KeyboardViewModel
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
class KeyboardSettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: ConnectBotDatabase
    private val store = ViewModelStore()
    private lateinit var viewModel: KeyboardViewModel

    @Before fun setUp() {
        val context = rule.activity
        db = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        KeyboardDefaults.seed(db.openHelper.writableDatabase)
        val repository = KeyboardRepository(db, CoroutineDispatchers(Dispatchers.Default, Dispatchers.IO, Dispatchers.Main), context.getSharedPreferences("keyboard-settings-test", Context.MODE_PRIVATE))
        viewModel = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = KeyboardViewModel(repository, db) as T
            },
        )[KeyboardViewModel::class.java]
        rule.setContent { ConnectBotTheme { KeyboardSettingsScreen(onNavigateBack = {}, viewModel = viewModel) } }
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.isNotEmpty() }
    }

    @After fun tearDown() {
        rule.runOnIdle { store.clear() }
        db.close()
    }

    private fun click(resource: Int) = rule.onNodeWithText(rule.activity.getString(resource)).performScrollTo().performClick()

    @Test fun navigateUpDiscardsTheDraftAndReturnsToLayouts() {
        click(R.string.keyboard_add_layout)
        rule.onNodeWithText("Claw").performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.button_navigate_up)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_add_layout)).assertIsDisplayed()
    }

    @TargetApi(34)
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun compactEditorKeepsTheGridAndSaveButtonVisible() {
        click(R.string.keyboard_add_layout)
        rule.onNodeWithText("Claw").performClick()
        rule.onAllNodesWithText("Esc").assertCountEquals(1)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_save)).assertIsDisplayed()
        val directory = File("build/reports/keyboard-editor").apply { mkdirs() }
        File(directory, "integrated-grid.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun previewHasOneCopyOfEachKeyAndTappingOpensItsEditor() {
        click(R.string.keyboard_add_layout)
        rule.onNodeWithText("Claw").performClick()
        rule.onAllNodesWithText("Esc").assertCountEquals(1)
        rule.onNodeWithText("Esc").performClick()
        rule.onNode(isDialog()).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_row)).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.button_close)).performClick()
        rule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun draggingThePreviewReordersKeysWithoutOpeningTheEditor() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onNodeWithText("Claw").performClick()
        val escape = rule.onNodeWithText("Esc")
        val start = escape.getUnclippedBoundsInRoot()
        val stop = rule.onNodeWithText("Stop").getUnclippedBoundsInRoot()
        val delta = with(rule.density) { (stop.left - start.left).toPx() }
        escape.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1000)
        escape.performTouchInput {
            moveBy(Offset(delta / 2, 0f), delayMillis = 100)
            moveBy(Offset(delta / 2, 0f), delayMillis = 100)
        }
        rule.waitForIdle()
        escape.performTouchInput { up() }
        rule.waitForIdle()
        rule.onNode(isDialog()).assertDoesNotExist()
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val layout = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        assertEquals(1, db.keyboardDao().items().single { it.layoutId == layout.id && it.layer == 0 && it.target == "escape" && it.kind == "key" && it.macroId == null }.column)
    }

    @Test fun olderMultiRowKeysBecomeSingleRowOnlyWhenSaved() = runBlocking {
        val tall = db.keyboardDao().items().first().copy(rowSpan = 2)
        db.keyboardDao().saveItems(listOf(tall))
        rule.waitUntil(5000) { viewModel.configuration.value.items.any { it.id == tall.id && it.rowSpan == 2 } }
        click(R.string.keyboard_edit)
        click(R.string.keyboard_cancel)
        assertEquals(2, db.keyboardDao().items().single { it.id == tall.id }.rowSpan)
        click(R.string.keyboard_edit)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.items.any { it.id == tall.id && it.rowSpan == 1 } }
        assertEquals(1, db.keyboardDao().items().single { it.id == tall.id }.rowSpan)
    }

    @Test fun keyCanUseAnExistingSharedMacro() = runBlocking {
        val macro = org.connectbot.data.keyboard.KeyboardMacro(name = "Deploy", document = org.connectbot.data.keyboard.MacroFormat.encode(listOf(org.connectbot.data.keyboard.MacroAction.Text("deploy"))))
        db.keyboardDao().saveMacro(macro)
        rule.waitUntil(5000) { viewModel.configuration.value.macros.any { it.id == macro.id } }
        click(R.string.keyboard_add_layout)
        rule.onNodeWithText("Claw").performClick()
        rule.onNodeWithText("Esc").performClick()
        click(R.string.macro_assign)
        rule.onNodeWithText("Deploy").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.button_close)).performClick()
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val layout = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        val key = db.keyboardDao().items().single { it.layoutId == layout.id && it.layer == 0 && it.row == 0 && it.column == 0 }
        assertEquals("macro", key.kind)
        assertEquals(macro.id, key.macroId)
    }

    @Test fun cancellingPresetDraftWritesNoLayoutsOrMacros() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onNodeWithText("Claw").performClick()
        click(R.string.keyboard_cancel)
        assertEquals(listOf(KeyboardDefaults.layoutId), db.keyboardDao().layouts().map { it.id })
        assertTrue(db.keyboardDao().macros().isEmpty())
        assertEquals(KeyboardDefaults.layoutId, db.keyboardDao().settings()!!.defaultLayoutId)
    }

    @Test fun directLayerDestinationSavesWithPresetWithoutChangingDefault() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onNodeWithText("Claw").performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_layer_next)).performScrollTo().performClick()
        click(R.string.keyboard_layer_destination)
        rule.onAllNodesWithText(rule.activity.getString(R.string.keyboard_layer, 1))[1].performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.button_close)).performClick()
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val saved = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        assertTrue(saved.fitToWidth)
        val items = db.keyboardDao().items().filter { it.layoutId == saved.id }
        assertEquals("1", items.single { it.layer == 0 && it.kind == "layer" }.target)
        assertEquals("next", items.single { it.layer == 1 && it.kind == "layer" }.target)
        assertEquals(setOf(0, 1), items.map { it.layer }.toSet())
        assertEquals(KeyboardDefaults.layoutId, db.keyboardDao().settings()!!.defaultLayoutId)
        assertTrue(db.keyboardDao().macros().isNotEmpty())
    }

    @Test fun numberedLayerSwitchCanBeAddedToAnAlternateLayer() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onNodeWithText("Claw").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_layer, 1)).performScrollTo().performClick()
        click(R.string.keyboard_add_button)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_add_layer_switch)).performClick()
        rule.onAllNodesWithText(rule.activity.getString(R.string.keyboard_layer, 0))[1].performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.button_close)).performClick()
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val saved = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        val items = db.keyboardDao().items().filter { it.layoutId == saved.id }
        assertEquals(listOf("next"), items.filter { it.layer == 0 && it.kind == "layer" }.map { it.target })
        assertEquals(setOf("next", "0"), items.filter { it.layer == 1 && it.kind == "layer" }.map { it.target }.toSet())
        assertTrue(items.any { it.layer == 1 && it.target == "f12" })
    }
}
