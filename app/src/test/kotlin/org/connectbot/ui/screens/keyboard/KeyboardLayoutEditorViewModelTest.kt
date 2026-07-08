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

package org.connectbot.ui.screens.keyboard

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.KeyboardLayoutRepository
import org.connectbot.data.entity.KeyboardLayout
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.keyboard.KeySpec
import org.connectbot.keyboard.KeyboardLayoutJson
import org.connectbot.keyboard.KeyboardLayoutSpec
import org.connectbot.keyboard.SpecialKey
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutEditorViewModelTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var repository: KeyboardLayoutRepository
    private var layoutId: Long = 0

    @Before
    fun setUp() {
        // Real dispatchers: the ViewModel reads from Room asynchronously, which
        // does not settle under a virtual-time test scheduler.
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KeyboardLayoutRepository(
            database.keyboardLayoutDao(),
            CoroutineDispatchers(default = Dispatchers.Unconfined, io = Dispatchers.IO, main = Dispatchers.Unconfined),
        )
        val singleRow = KeyboardLayoutSpec(listOf(listOf(KeySpec.Special(SpecialKey.ESC))))
        layoutId = runBlocking {
            database.keyboardLayoutDao().insert(
                KeyboardLayout(name = "Test", keysJson = KeyboardLayoutJson.encode(singleRow)),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun loadedViewModel(): KeyboardLayoutEditorViewModel {
        val vm = KeyboardLayoutEditorViewModel(SavedStateHandle(mapOf("layoutId" to layoutId)), repository)
        withTimeout(3000) {
            while (vm.uiState.value.isLoading) delay(5)
        }
        return vm
    }

    private suspend fun awaitPersisted(): List<List<KeySpec>> {
        withTimeout(3000) { delay(20) }
        return KeyboardLayoutJson.decode(database.keyboardLayoutDao().getById(layoutId)!!.keysJson)!!.rows
    }

    @Test
    fun loadsExistingLayout() = runBlocking<Unit> {
        val vm = loadedViewModel()
        assertThat(vm.uiState.value.name).isEqualTo("Test")
        assertThat(vm.uiState.value.rows[0]).containsExactly(KeySpec.Special(SpecialKey.ESC))
    }

    @Test
    fun addKeyUpdatesState() = runBlocking<Unit> {
        val vm = loadedViewModel()
        vm.addKey(0, KeySpec.Text("/"))
        assertThat(vm.uiState.value.rows[0])
            .containsExactly(KeySpec.Special(SpecialKey.ESC), KeySpec.Text("/"))
    }

    @Test
    fun moveKeyReorders() = runBlocking<Unit> {
        val vm = loadedViewModel()
        vm.addKey(0, KeySpec.Text("/"))
        vm.moveKey(0, 1, -1) // move "/" before Esc
        assertThat(vm.uiState.value.rows[0])
            .containsExactly(KeySpec.Text("/"), KeySpec.Special(SpecialKey.ESC))
    }

    @Test
    fun addAndRemoveSecondRowMergesKeys() = runBlocking<Unit> {
        val vm = loadedViewModel()
        vm.addSecondRow()
        vm.addKey(1, KeySpec.Text("|"))
        assertThat(vm.uiState.value.rows).hasSize(2)

        vm.removeSecondRow()
        val rows = vm.uiState.value.rows
        assertThat(rows).hasSize(1)
        assertThat(rows[0]).containsExactly(KeySpec.Special(SpecialKey.ESC), KeySpec.Text("|"))
    }

    @Test
    fun moveKeyToOtherRow() = runBlocking<Unit> {
        val vm = loadedViewModel()
        vm.moveKeyToRow(0, 0, 1) // move Esc into a new second row
        val rows = vm.uiState.value.rows
        assertThat(rows).hasSize(2)
        assertThat(rows[0]).isEmpty()
        assertThat(rows[1]).containsExactly(KeySpec.Special(SpecialKey.ESC))
    }

    @Test
    fun removeKeyLeavesEmptyRow() = runBlocking<Unit> {
        val vm = loadedViewModel()
        vm.removeKey(0, 0)
        assertThat(vm.uiState.value.rows[0]).isEmpty()
    }

    @Test
    fun persistsToRepository() = runBlocking<Unit> {
        val vm = loadedViewModel()
        vm.addKey(0, KeySpec.Text("/"))
        assertThat(awaitPersisted()[0])
            .containsExactly(KeySpec.Special(SpecialKey.ESC), KeySpec.Text("/"))
    }
}
