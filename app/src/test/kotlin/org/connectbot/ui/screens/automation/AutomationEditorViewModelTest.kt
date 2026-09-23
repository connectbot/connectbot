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

package org.connectbot.ui.screens.automation

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.AutomationAction
import org.connectbot.data.entity.AutomationActionType
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AutomationEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
    private val repository = Mockito.mock(HostRepository::class.java)
    private val actions = MutableStateFlow<List<AutomationAction>>(emptyList())

    @Before fun setUp() = runTest {
        Dispatchers.setMain(dispatcher)
        Mockito.`when`(repository.findHostById(1)).thenReturn(Host(id = 1, nickname = "test", protocol = "ssh", username = "user", hostname = "host", port = 22))
        Mockito.`when`(repository.observeAutomation(1)).thenReturn(actions)
        Mockito.`when`(repository.observePortForwardsForHost(1)).thenReturn(MutableStateFlow(emptyList()))
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun editReorderUndoAndSavePreserveIds() = runTest(dispatcher) {
        val a = AutomationAction(hostId = 1, text = "one")
        val b = AutomationAction(hostId = 1, position = 1, text = "two")
        actions.value = listOf(a, b)
        val vm = AutomationEditorViewModel(SavedStateHandle(mapOf("hostId" to 1L)), repository, dispatchers)
        advanceUntilIdle()
        vm.move(a.id, 1)
        assertTrue(vm.state.value.dirty)
        vm.move(a.id, 0)
        assertFalse(vm.state.value.dirty)
        vm.applyEdit(a.copy(text = "edited"))
        advanceUntilIdle()
        assertTrue(vm.state.value.dirty)
        assertTrue(vm.save())
        assertFalse(vm.state.value.dirty)
        Mockito.verify(repository).saveAutomation(1, listOf(a.copy(text = "edited"), b))
    }

    @Test fun unsavedDraftRestoresAndInvalidRegexDoesNotChangeSequence() = runTest(dispatcher) {
        val handle = SavedStateHandle(mapOf("hostId" to 1L))
        val vm = AutomationEditorViewModel(handle, repository, dispatchers)
        advanceUntilIdle()
        val action = AutomationAction(hostId = 1, text = "secret\r\n")
        vm.applyEdit(action)
        advanceUntilIdle()
        val restored = AutomationEditorViewModel(SavedStateHandle(mapOf("hostId" to 1L, "draft" to handle.get<String>("draft"))), repository, dispatchers)
        advanceUntilIdle()
        assertEquals(listOf(action), restored.state.value.actions)
        assertTrue(restored.state.value.dirty)
        restored.applyEdit(action.copy(type = AutomationActionType.WAIT_FOR_TEXT, regex = true, text = "["))
        advanceUntilIdle()
        assertNotNull(restored.state.value.editError)
        assertEquals(listOf(action), restored.state.value.actions)
    }

    @Test fun failedSaveKeepsDraftDirty() = runTest(dispatcher) {
        val vm = AutomationEditorViewModel(SavedStateHandle(mapOf("hostId" to 1L)), repository, dispatchers)
        advanceUntilIdle()
        val action = AutomationAction(hostId = 1, text = "one")
        vm.applyEdit(action)
        advanceUntilIdle()
        Mockito.`when`(repository.saveAutomation(1, listOf(action))).thenThrow(IllegalStateException())
        assertFalse(vm.save())
        assertTrue(vm.state.value.dirty)
        assertEquals(listOf(action), vm.state.value.actions)
        assertNotNull(vm.state.value.error)
    }
}
