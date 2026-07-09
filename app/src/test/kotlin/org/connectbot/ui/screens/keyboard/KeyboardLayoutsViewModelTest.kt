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
import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.KeyboardLayoutRepository
import org.connectbot.data.dao.KeyboardLayoutDao
import org.connectbot.data.entity.KeyboardLayout
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.keyboard.DefaultKeyboardLayouts
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutsViewModelTest {

    private lateinit var context: Context
    private lateinit var database: ConnectBotDatabase
    private lateinit var repository: KeyboardLayoutRepository
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("KeyboardLayoutsViewModelTest", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KeyboardLayoutRepository(
            database.keyboardLayoutDao(),
            CoroutineDispatchers(
                default = Dispatchers.Unconfined,
                io = Dispatchers.Unconfined,
                main = Dispatchers.Unconfined,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
        prefs.edit().clear().commit()
        Dispatchers.resetMain()
    }

    @Test
    fun renameReportsDuplicateName() = runBlocking<Unit> {
        val layoutId = repository.create("Original", DefaultKeyboardLayouts.default)
        repository.create("Taken", DefaultKeyboardLayouts.classic)
        val viewModel = KeyboardLayoutsViewModel(repository, prefs)

        assertThat(viewModel.rename(layoutId, "Renamed")).isTrue()
        assertThat(repository.getById(layoutId)?.name).isEqualTo("Renamed")

        assertThat(viewModel.rename(layoutId, " Taken ")).isFalse()
        assertThat(repository.getById(layoutId)?.name).isEqualTo("Renamed")
    }

    @Test
    fun renameToOwnNameSucceeds() = runBlocking<Unit> {
        val layoutId = repository.create("Original", DefaultKeyboardLayouts.default)
        val viewModel = KeyboardLayoutsViewModel(repository, prefs)

        assertThat(viewModel.rename(layoutId, "original")).isTrue()
        assertThat(repository.getById(layoutId)?.name).isEqualTo("original")
    }

    @Test
    fun createLayoutGeneratesUniqueNames() = runBlocking<Unit> {
        val viewModel = KeyboardLayoutsViewModel(repository, prefs)

        val first = viewModel.createLayout("New layout")
        val second = viewModel.createLayout("New layout")

        val names = repository.getAll().map { it.name }
        assertThat(names).hasSize(2)
        assertThat(names.toSet()).hasSize(2)
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun createLayoutStopsAfterMaxUniqueNameAttempts() = runBlocking<Unit> {
        val dao = AlwaysCollidingKeyboardLayoutDao()
        val viewModel = KeyboardLayoutsViewModel(
            KeyboardLayoutRepository(
                dao,
                CoroutineDispatchers(
                    default = Dispatchers.Unconfined,
                    io = Dispatchers.Unconfined,
                    main = Dispatchers.Unconfined,
                ),
            ),
            prefs,
        )

        try {
            viewModel.createLayout("New layout")
            fail("Expected createLayout to throw after max unique-name attempts")
        } catch (_: SQLiteConstraintException) {
            // Expected after the bounded retry budget is exhausted.
        }

        assertThat(dao.insertCalls).isEqualTo(10)
    }

    private class AlwaysCollidingKeyboardLayoutDao : KeyboardLayoutDao {
        var insertCalls = 0
            private set

        override fun observeAll(): Flow<List<KeyboardLayout>> = flowOf(emptyList())

        override fun observeById(layoutId: Long): Flow<KeyboardLayout?> = flowOf(null)

        override suspend fun getById(layoutId: Long): KeyboardLayout? = null

        override suspend fun getAll(): List<KeyboardLayout> = emptyList()

        override suspend fun insert(layout: KeyboardLayout): Long {
            insertCalls++
            throw SQLiteConstraintException("simulated collision")
        }

        override suspend fun update(layout: KeyboardLayout) = Unit

        override suspend fun deleteById(layoutId: Long): Int = 0

        override suspend fun nameExists(name: String, excludeLayoutId: Long?): Boolean = false

        override suspend fun countHostsUsing(layoutId: Long): Int = 0

        override suspend fun clearHostReferences(layoutId: Long) = Unit
    }
}
