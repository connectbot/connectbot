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

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        click(R.string.keyboard_save)
        rule.waitUntil(5000) { viewModel.configuration.value.layouts.size == 2 }
        val saved = db.keyboardDao().layouts().single { it.presetKey == "claw" }
        val items = db.keyboardDao().items().filter { it.layoutId == saved.id }
        assertEquals(listOf("next"), items.filter { it.layer == 0 && it.kind == "layer" }.map { it.target })
        assertEquals(setOf("next", "0"), items.filter { it.layer == 1 && it.kind == "layer" }.map { it.target }.toSet())
        assertTrue(items.any { it.layer == 1 && it.target == "f12" })
    }
}
