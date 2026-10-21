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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Profile
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardRepository
import org.connectbot.di.CoroutineDispatchers
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class KeyboardViewModelTest {
    @Test fun temporaryUuidHostUsesItsProfileAndObservesLayoutChanges() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            val layout = KeyboardLayout(name = "Temporary layout")
            val replacement = KeyboardLayout(name = "Replacement")
            db.keyboardDao().saveLayout(layout)
            db.keyboardDao().saveLayout(replacement)
            val profile = Profile(name = "Temporary profile", keyboardLayoutId = layout.id)
            db.profileDao().insert(profile)
            db.profileDao().insert(Profile.createDefault())
            val repository = KeyboardRepository(db, CoroutineDispatchers(dispatcher, dispatcher, dispatcher), context.getSharedPreferences("keyboard-viewmodel", Context.MODE_PRIVATE))
            val vm = ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = KeyboardViewModel(repository, db) as T
                },
            )[KeyboardViewModel::class.java]
            val selection = vm.layoutSelection(Host(isTemporary = true, profileId = profile.id))
            assertEquals(layout.id, selection.first())
            val observed = CompletableDeferred<Unit>()
            val changes = backgroundScope.async(dispatcher) {
                selection.onEach { observed.complete(Unit) }.take(2).toList()
            }
            observed.await()
            db.profileDao().update(profile.copy(keyboardLayoutId = replacement.id))
            assertEquals(listOf(layout.id, replacement.id), changes.await())
        } finally {
            store.clear()
            db.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun savedHostObservesProfileReassignment() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        val store = ViewModelStore()
        try {
            val layout = KeyboardLayout(name = "Saved layout")
            db.keyboardDao().saveLayout(layout)
            val profile = Profile(name = "Saved profile", keyboardLayoutId = layout.id)
            db.profileDao().insert(profile)
            db.profileDao().insert(Profile.createDefault())
            val host = Host(nickname = "Saved")
            db.hostDao().insert(host)
            val repository = KeyboardRepository(db, CoroutineDispatchers(dispatcher, dispatcher, dispatcher), context.getSharedPreferences("keyboard-viewmodel", Context.MODE_PRIVATE))
            val vm = ViewModelProvider(
                store,
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = KeyboardViewModel(repository, db) as T
                },
            )[KeyboardViewModel::class.java]
            assertEquals(null, vm.layoutSelection(host).first())
            db.hostDao().update(host.copy(profileId = profile.id))
            assertEquals(layout.id, vm.layoutSelection(host).first())
            val override = KeyboardLayout(name = "Host override")
            db.keyboardDao().saveLayout(override)
            repository.setHostLayout(host.id, override.id)
            assertEquals(override.id, vm.layoutSelection(host).first())
            assertEquals("Saved profile", vm.hostContext(host).first().profileName)
            repository.setHostLayout(host.id, null)
            assertEquals(layout.id, vm.layoutSelection(host).first())
            db.profileDao().update(profile.copy(keyboardLayoutId = null))
            assertEquals(null, vm.layoutSelection(host).first())
        } finally {
            store.clear()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
