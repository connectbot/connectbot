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
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.Host
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardDraft
import org.connectbot.data.keyboard.KeyboardRepository
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.TerminalBridge
import org.connectbot.ui.screens.keyboard.ConsoleKeyboardSettings
import org.connectbot.ui.screens.keyboard.KeyboardLayoutSelector
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
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class KeyboardSettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: ConnectBotDatabase
    private lateinit var restoration: StateRestorationTester
    private val store = ViewModelStore()
    private lateinit var viewModel: KeyboardViewModel
    private val terminalBridge = mutableStateOf<TerminalBridge?>(null)
    private val selecting = mutableStateOf(false)
    private val typographyScale = mutableFloatStateOf(1f)
    private val selected = mutableStateOf<UUID?>(null)
    private val pending = mutableStateOf<KeyboardDraft?>(null)

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
        restoration = StateRestorationTester(rule)
        restoration.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, typographyScale.floatValue)) {
                ConnectBotTheme {
                    if (terminalBridge.value != null) {
                        ConsoleKeyboardSettings(terminalBridge.value!!, onNavigateBack = { terminalBridge.value = null }, viewModel = viewModel)
                    } else if (selecting.value) {
                        KeyboardLayoutSelector(
                            selected = selected.value,
                            onSelect = { selected.value = it },
                            ownerName = "Production",
                            pendingDraft = pending.value,
                            onDraft = { pending.value = it },
                            viewModel = viewModel,
                        )
                    } else {
                        KeyboardSettingsScreen(onNavigateBack = {}, viewModel = viewModel)
                    }
                }
            }
        }
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.isNotEmpty() }
    }

    @After fun tearDown() {
        rule.runOnIdle {
            rule.activity.setContent { }
            store.clear()
        }
        rule.waitForIdle()
        db.close()
    }

    private fun navigateUp() {
        val arrows = rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.button_navigate_up))
        arrows[arrows.fetchSemanticsNodes().lastIndex].performClick()
    }

    private fun click(resource: Int) {
        val nodes = rule.onAllNodes(hasText(rule.activity.getString(resource)) or hasContentDescription(rule.activity.getString(resource)))
        val node = nodes[nodes.fetchSemanticsNodes().lastIndex]
        if (runCatching { node.assertIsDisplayed() }.isFailure) node.performScrollTo()
        node.performClick()
    }

    @TargetApi(34)
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun terminalCustomizationPersistsForSavedHost() = runBlocking {
        val host = Host(nickname = "Production", hostname = "host")
        db.hostDao().insert(host)
        val bridge = Mockito.mock(TerminalBridge::class.java)
        Mockito.`when`(bridge.host).thenReturn(host)
        rule.runOnIdle { terminalBridge.value = bridge }
        rule.waitUntil(5000) { rule.onAllNodesWithText(rule.activity.getString(R.string.keyboard_edit_copy)).fetchSemanticsNodes().isNotEmpty() }
        click(R.string.keyboard_edit_copy)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { runBlocking { db.hostDao().getById(host.id)?.keyboardLayoutId != null } }
        val id = db.hostDao().getById(host.id)!!.keyboardLayoutId!!
        assertEquals("Default — Production", db.keyboardDao().layouts().first { it.id == id }.name)
        val directory = File("build/reports/keyboard-editor").apply { mkdirs() }
        File(directory, "terminal-keyboard-selection.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        click(R.string.keyboard_reset_inherited)
        rule.waitUntil(5000) { runBlocking { db.hostDao().getById(host.id)?.keyboardLayoutId == null } }
    }

    @Test fun builtInPresetsAreListedAndSelectionWaitsForParentSave() = runBlocking {
        rule.runOnIdle { selecting.value = true }
        click(R.string.keyboard_change)
        listOf(R.string.keyboard_preset_citrus, R.string.keyboard_preset_last_stop, R.string.keyboard_preset_claw, R.string.keyboard_preset_scribe).forEach { resource ->
            rule.onNodeWithText(rule.activity.getString(resource)).assertExists()
        }
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_preset_scribe)).performScrollTo().performClick()
        rule.waitUntil(5000) { pending.value != null }
        assertEquals("scribe", pending.value!!.layout.presetKey)
        assertEquals(pending.value!!.layout.id, selected.value)
        assertTrue(pending.value!!.macros.isNotEmpty())
        assertEquals(1, db.keyboardDao().layouts().size)
        assertTrue(db.keyboardDao().macros().isEmpty())
    }

    @Test fun editingBuiltInPresetCreatesAnIndependentLayout() = runBlocking {
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_preset_claw)).performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_preset_claw) + rule.activity.getString(R.string.keyboard_copy_suffix)).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_name)).performTextReplacement("My Claw")
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val saved = db.keyboardDao().layouts().single { it.name == "My Claw" }
        assertEquals("claw", saved.presetKey)
        assertEquals(KeyboardDefaults.layoutId, db.keyboardDao().settings()!!.defaultLayoutId)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_preset_claw)).performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_preset_claw) + rule.activity.getString(R.string.keyboard_copy_suffix)).assertExists()
        navigateUp()
        click(R.string.editor_discard)
        assertEquals(2, db.keyboardDao().layouts().size)
    }

    @Test fun temporaryTerminalSelectionDoesNotInsertAHost() {
        val host = Host(nickname = "Temporary", isTemporary = true)
        val bridge = Mockito.mock(TerminalBridge::class.java)
        Mockito.`when`(bridge.host).thenReturn(host)
        rule.runOnIdle { terminalBridge.value = bridge }
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_session_only)).assertIsDisplayed()
        click(R.string.keyboard_edit_copy)
        click(R.string.keyboard_save)
        try {
            rule.waitUntil(5000) {
                rule.runOnIdle { }
                viewModel.repository.sessionSelection(bridge).value != null
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError(rule.onRoot().printToString(), e)
        }
        runBlocking {
            assertTrue(db.hostDao().getAll().isEmpty())
            assertEquals(2, db.keyboardDao().layouts().size)
        }
        navigateUp()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_add_layout)).assertExists()
        assertTrue(viewModel.repository.sessionSelection(bridge).value != null)
    }

    @Test fun recreationKeepsLayoutAndInvalidMacroDrafts() {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_name)).performTextReplacement("Unfinished layout")
        restoration.emulateSavedInstanceStateRestore()
        rule.onAllNodesWithText("Unfinished layout").assertCountEquals(1)
        navigateUp()
        click(R.string.editor_discard)
        click(R.string.keyboard_macros)
        click(R.string.keyboard_add_macro)
        click(R.string.keyboard_add_text)
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_text)).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_save)).assertIsNotEnabled()
    }

    @TargetApi(34)
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun contextualCustomizationWaitsForParentSave() = runBlocking {
        rule.runOnIdle { selecting.value = true }
        val profileDirectory = File("build/reports/keyboard-editor").apply { mkdirs() }
        File(profileDirectory, "profile-keyboard-selection.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        click(R.string.keyboard_edit_copy)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { pending.value != null }
        assertEquals("Default — Production", pending.value!!.layout.name)
        assertEquals(pending.value!!.layout.id, selected.value)
        assertEquals(listOf(KeyboardDefaults.layoutId), db.keyboardDao().layouts().map { it.id })
        val directory = File("build/reports/keyboard-editor").apply { mkdirs() }
        File(directory, "host-keyboard-selection.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        click(R.string.keyboard_reset_inherited)
        assertEquals(null, selected.value)
        assertEquals(null, pending.value)
        assertEquals(1, db.keyboardDao().layouts().size)
    }

    @TargetApi(34)
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun macroLibraryExplainsAllUsesAndOffersCopyOrSharedEdit() = runBlocking {
        val macro = org.connectbot.data.keyboard.KeyboardMacro(name = "Deploy", document = org.connectbot.data.keyboard.MacroFormat.encode(listOf(org.connectbot.data.keyboard.MacroAction.Text("deploy"))))
        db.keyboardDao().saveMacro(macro)
        val item = db.keyboardDao().items().first().copy(kind = "macro", macroId = macro.id)
        db.keyboardDao().saveItems(listOf(item))
        val host = org.connectbot.data.entity.Host(nickname = "Production")
        db.hostDao().insert(host)
        db.automationActionDao().replace(host.id, listOf(org.connectbot.data.entity.AutomationAction(hostId = host.id, macroId = macro.id)))
        rule.waitUntil(5000) { viewModel.configuration.value.macros.any { it.id == macro.id } }
        click(R.string.keyboard_macros)
        rule.waitUntil(5000) { rule.onAllNodesWithText(rule.activity.getString(R.string.keyboard_usage_automation, "Production")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_usage_automation, "Production")).assertExists()
        click(R.string.keyboard_edit)
        rule.onNodeWithText(rule.activity.getString(R.string.macro_shared_title)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.macro_edit_shared)).assertIsDisplayed()
        val directory = File("build/reports/keyboard-editor").apply { mkdirs() }
        File(directory, "shared-macro-editing.png").outputStream().use { rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText(rule.activity.getString(R.string.macro_make_copy)).performClick()
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.macros.size == 2 }
        assertEquals(macro, db.keyboardDao().macros().first { it.id == macro.id })
        assertEquals(macro.id, db.keyboardDao().items().first { it.id == item.id }.macroId)
        assertEquals(macro.id, db.automationActionDao().getByHost(host.id).single().macroId)
    }

    @Test fun savedLayoutCanBeSelectedAndInheritanceRestored() = runBlocking {
        val layout = org.connectbot.data.keyboard.KeyboardLayout(name = "Work keys")
        viewModel.repository.save(layout, KeyboardDefaults.items(layout.id))
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        rule.runOnIdle { selecting.value = true }
        click(R.string.keyboard_change)
        rule.onNodeWithText("Work keys").performScrollTo().performClick()
        rule.waitUntil(5000) { selected.value == layout.id }
        assertEquals(null, pending.value)
        click(R.string.keyboard_change)
        click(R.string.keyboard_inherited_selection)
        rule.waitUntil(5000) { selected.value == null }
        assertEquals(2, db.keyboardDao().layouts().size)
    }

    @Test fun libraryCopyCanBecomeDefaultAndDeletionRestoresFallback() = runBlocking {
        click(R.string.keyboard_more_layout_actions)
        click(R.string.keyboard_edit_copy)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_name)).performTextReplacement("My keys")
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val id = db.keyboardDao().layouts().single { it.name == "My keys" }.id
        click(R.string.keyboard_more_layout_actions)
        click(R.string.keyboard_make_default)
        rule.waitUntil(5000) { runBlocking { db.keyboardDao().settings()!!.defaultLayoutId == id } }
        click(R.string.keyboard_more_layout_actions)
        click(R.string.keyboard_delete)
        click(R.string.button_cancel)
        assertEquals(2, db.keyboardDao().layouts().size)
        click(R.string.keyboard_more_layout_actions)
        click(R.string.keyboard_delete)
        click(R.string.keyboard_confirm)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 1 }
        assertEquals(KeyboardDefaults.layoutId, db.keyboardDao().settings()!!.defaultLayoutId)
    }

    @Test fun resetPresetRestoresKeysOnlyAfterSaving() = runBlocking {
        rule.onNodeWithText("Claw").performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_name)).performTextReplacement("Reset me")
        click(R.string.keyboard_more_layout_actions)
        click(R.string.keyboard_reset)
        click(R.string.keyboard_confirm)
        assertEquals(1, db.keyboardDao().layouts().size)
        navigateUp()
        click(R.string.editor_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val saved = db.keyboardDao().layouts().single { it.name == "Reset me" }
        assertEquals("claw", saved.presetKey)
        assertTrue(saved.fitToWidth)
        assertTrue(db.keyboardDao().items().any { it.layoutId == saved.id })
    }

    @Test fun unusedMacroCanBeEditedSavedFromBackDialogAndDeleted() = runBlocking {
        val macro = org.connectbot.data.keyboard.KeyboardMacro(name = "Unused", document = org.connectbot.data.keyboard.MacroFormat.encode(listOf(org.connectbot.data.keyboard.MacroAction.Text("original"))))
        db.keyboardDao().saveMacro(macro)
        rule.waitUntil(5000) { viewModel.configuration.value.macros.size == 1 }
        click(R.string.keyboard_macros)
        click(R.string.keyboard_edit)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_text)).performTextReplacement("replacement")
        navigateUp()
        click(R.string.button_cancel)
        navigateUp()
        click(R.string.editor_save)
        rule.waitUntil(5000) { viewModel.configuration.value.macros.single().document != macro.document }
        assertEquals(listOf(org.connectbot.data.keyboard.MacroAction.Text("replacement")), org.connectbot.data.keyboard.MacroFormat.decode(db.keyboardDao().macros().single().document))
        click(R.string.keyboard_delete)
        click(R.string.keyboard_confirm)
        rule.waitUntil(5000) { viewModel.configuration.value.macros.isEmpty() }
    }

    @Test fun keyActionCanChangeToModifierThenAppActionAndBeRemoved() = runBlocking {
        rule.onNodeWithText("Claw").performScrollTo().performClick()
        rule.onNodeWithText("Esc").performClick()
        click(R.string.keyboard_key_type)
        click(R.string.keyboard_action_modifier)
        rule.onAllNodesWithText("Alt").onLast().performClick()
        click(R.string.keyboard_action_modifier)
        click(R.string.keyboard_action_app)
        rule.onAllNodesWithText("Paste").onLast().performClick()
        click(R.string.keyboard_save)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val saved = db.keyboardDao().layouts().single { it.id != KeyboardDefaults.layoutId }
        val paste = db.keyboardDao().items().single { it.layoutId == saved.id && it.kind == "app" && it.target == "paste" }
        rule.waitUntil(5000) { rule.onAllNodesWithText(rule.activity.getString(R.string.keyboard_add_layout)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(saved.name).performScrollTo().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_canvas_title)).performScrollTo()
        rule.onNodeWithContentDescription("Paste").performClick()
        click(R.string.keyboard_remove_button)
        click(R.string.keyboard_cancel)
        click(R.string.keyboard_remove_button)
        click(R.string.keyboard_remove_button)
        assertTrue(db.keyboardDao().items().any { it.id == paste.id })
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.items.none { it.id == paste.id } }
    }

    @Test fun navigateUpConfirmsBeforeDiscardingTheDraft() {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.button_navigate_up)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.editor_discard)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_add_layout)).assertIsDisplayed()
    }

    @TargetApi(34)
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun compactEditorKeepsTheGridAndSaveButtonVisible() {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onAllNodesWithText("Esc").assertCountEquals(1)
        rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.keyboard_save)).onLast().assertIsDisplayed()
        val directory = File("build/reports/keyboard-editor").apply { mkdirs() }
        File(directory, "integrated-grid.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun previewHasOneCopyOfEachKeyAndTappingOpensItsEditor() {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onAllNodesWithText("Esc").assertCountEquals(1)
        rule.onNodeWithText("Esc").performClick()
        rule.onNode(isDialog()).assertExists()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_row)).assertDoesNotExist()
        click(R.string.keyboard_advanced)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_row)).assertExists()
        click(R.string.keyboard_save)
        rule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test fun draggingThePreviewReordersKeysWithoutOpeningTheEditor() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
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
        rule.waitUntil(5000) {
            rule.runOnIdle {}
            viewModel.configuration.value.layouts.size == 2
        }
        val layout = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        assertEquals(1, db.keyboardDao().items().single { it.layoutId == layout.id && it.layer == 0 && it.target == "escape" && it.kind == "key" && it.macroId == null }.column)
    }

    @Test fun olderMultiRowKeysBecomeSingleRowOnlyWhenSaved() = runBlocking {
        val tall = db.keyboardDao().items().first().copy(rowSpan = 2)
        db.keyboardDao().saveItems(listOf(tall))
        rule.waitUntil(5000) { viewModel.configuration.value.items.any { it.id == tall.id && it.rowSpan == 2 } }
        click(R.string.keyboard_library_edit)
        click(R.string.keyboard_edit_shared)
        navigateUp()
        assertEquals(2, db.keyboardDao().items().single { it.id == tall.id }.rowSpan)
        click(R.string.keyboard_library_edit)
        click(R.string.keyboard_edit_shared)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.items.any { it.id == tall.id && it.rowSpan == 1 } }
        assertEquals(1, db.keyboardDao().items().single { it.id == tall.id }.rowSpan)
    }

    @Test fun keyCanUseAnExistingSharedMacro() = runBlocking {
        val macro = org.connectbot.data.keyboard.KeyboardMacro(name = "Deploy", document = org.connectbot.data.keyboard.MacroFormat.encode(listOf(org.connectbot.data.keyboard.MacroAction.Text("deploy"))))
        db.keyboardDao().saveMacro(macro)
        rule.waitUntil(5000) { viewModel.configuration.value.macros.any { it.id == macro.id } }
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithText("Esc").performClick()
        click(R.string.keyboard_key_type)
        click(R.string.keyboard_action_macro)
        rule.onNodeWithText("Deploy").performClick()
        click(R.string.keyboard_save)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) {
            rule.runOnIdle {}
            viewModel.configuration.value.layouts.size == 2
        }
        val layout = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        val key = db.keyboardDao().items().single { it.layoutId == layout.id && it.layer == 0 && it.row == 0 && it.column == 0 }
        assertEquals("macro", key.kind)
        assertEquals(macro.id, key.macroId)
    }

    @Test fun cancellingPresetDraftWritesNoLayoutsOrMacros() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        navigateUp()
        click(R.string.editor_discard)
        assertEquals(listOf(KeyboardDefaults.layoutId), db.keyboardDao().layouts().map { it.id })
        assertTrue(db.keyboardDao().macros().isEmpty())
        assertEquals(KeyboardDefaults.layoutId, db.keyboardDao().settings()!!.defaultLayoutId)
    }

    @Test fun directLayerDestinationSavesWithPresetWithoutChangingDefault() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_layer_next)).performScrollTo().performClick()
        click(R.string.keyboard_layer_destination)
        rule.onAllNodesWithText(rule.activity.getString(R.string.keyboard_layer, 1))[1].performClick()
        click(R.string.keyboard_save)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) {
            rule.runOnIdle {}
            viewModel.configuration.value.layouts.size == 2
        }
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
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_layer, 1)).performScrollTo().performClick()
        click(R.string.keyboard_add_button)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_action_layer)).performClick()
        click(R.string.keyboard_layer_destination)
        rule.onAllNodesWithText(rule.activity.getString(R.string.keyboard_layer, 0))[1].performClick()
        click(R.string.keyboard_save)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) {
            rule.runOnIdle {}
            viewModel.configuration.value.layouts.size == 2
        }
        val saved = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        val items = db.keyboardDao().items().filter { it.layoutId == saved.id }
        assertEquals(listOf("next"), items.filter { it.layer == 0 && it.kind == "layer" }.map { it.target })
        assertEquals(setOf("next", "0"), items.filter { it.layer == 1 && it.kind == "layer" }.map { it.target }.toSet())
        assertTrue(items.any { it.layer == 1 && it.target == "f12" })
    }

    @Test fun keyChangesAreStagedAndCanBeDiscardedAfterRecreation() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithText("Esc").performClick()
        click(R.string.keyboard_behavior)
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_repeat_on_hold)).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_repeat_on_hold)).assertIsOn()
        navigateUp()
        click(R.string.editor_discard)
        rule.onNodeWithText("Esc").performClick()
        click(R.string.keyboard_behavior)
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_repeat_on_hold)).assertIsOff()
        assertEquals(1, db.keyboardDao().layouts().size)
        assertTrue(db.keyboardDao().macros().isEmpty())
    }

    @Test fun keyBackDialogCanCancelThenSaveIntoLayoutDraft() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithText("Esc").performClick()
        click(R.string.keyboard_behavior)
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_repeat_on_hold)).performScrollTo().performClick()
        navigateUp()
        rule.onNodeWithText(rule.activity.getString(R.string.editor_discard_changes_title)).assertIsDisplayed()
        click(R.string.button_cancel)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_edit_key_title)).assertIsDisplayed()
        navigateUp()
        click(R.string.editor_save)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_edit_key_title)).assertDoesNotExist()
        assertEquals(1, db.keyboardDao().layouts().size)
        rule.onNodeWithText("Esc").performClick()
        click(R.string.keyboard_behavior)
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_repeat_on_hold)).assertIsOn()
        Unit
    }

    @Test fun completedKeyChangesWaitForLayoutSave() = runBlocking {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithText("Esc").performClick()
        click(R.string.keyboard_behavior)
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_repeat_on_hold)).performScrollTo().performClick()
        click(R.string.keyboard_save)
        assertEquals(1, db.keyboardDao().layouts().size)
        click(R.string.keyboard_save)
        rule.waitUntil(5000) {
            rule.runOnIdle {}
            viewModel.configuration.value.layouts.size == 2
        }
        val layout = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        assertTrue(db.keyboardDao().items().single { it.layoutId == layout.id && it.layer == 0 && it.target == "escape" }.repeatOnHold == true)
    }

    @Test fun libraryPreviewDoesNotExposeTerminalKeyActions() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.keyboard_key_escape)).assertDoesNotExist()
        click(R.string.keyboard_library_edit)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_shared_title)).assertIsDisplayed()
    }

    @Config(qualifiers = "w640dp-h360dp")
    @Test
    fun landscapeKeepsSaveAvailableWhileSettingsScroll() {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performScrollTo().performClick()
        click(R.string.keyboard_layout_settings)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_height)).performScrollTo()
        rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.keyboard_save)).onLast().assertIsDisplayed()
    }

    @TargetApi(34)
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun fullScreenKeyEditorShowsGroupedActionsAndSave() {
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performClick()
        rule.onNodeWithText("Esc").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_edit_key_title)).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_mode_scrollable)).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_pin_choice)).assertDoesNotExist()
        rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.keyboard_save)).onLast().assertIsDisplayed()
        val directory = File("build/reports/keyboard-editor").apply { mkdirs() }
        File(directory, "key-editor-redesign.png").outputStream().use { rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Config(qualifiers = "w320dp-h640dp")
    @Test
    fun enlargedTextKeepsKeyControlsReachable() {
        rule.runOnIdle { typographyScale.floatValue = 1.5f }
        click(R.string.keyboard_add_layout)
        rule.onAllNodesWithText("Claw").onLast().performScrollTo().performClick()
        rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.keyboard_save)).onLast().assertIsDisplayed()
        rule.onNodeWithText("Esc").performScrollTo().performClick()
        click(R.string.keyboard_appearance)
        rule.onNodeWithText(rule.activity.getString(R.string.keyboard_reset_appearance)).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithContentDescription(rule.activity.getString(R.string.keyboard_save)).onLast().assertIsDisplayed()
    }
}
