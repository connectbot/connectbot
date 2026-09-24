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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.connectbot.data.HostRepository
import org.connectbot.data.entity.AutomationAction
import org.connectbot.data.entity.AutomationActionType
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.terminal.VTermKey
import org.connectbot.ui.screens.automation.AutomationEditorScreen
import org.connectbot.ui.screens.automation.AutomationEditorViewModel
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.TerminalKeyModifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AutomationEditorScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltComponentActivity>()
    private lateinit var viewModel: AutomationEditorViewModel
    private lateinit var repository: HostRepository
    private var navigatedBack = false
    private val hapticEvents = mutableListOf<HapticFeedbackType>()
    private val haptic = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            hapticEvents.add(hapticFeedbackType)
        }
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        repository = Mockito.mock(HostRepository::class.java)
        runBlocking {
            Mockito.`when`(repository.findHostById(1)).thenReturn(Host(id = 1, nickname = "test", protocol = "ssh", username = "user", hostname = "host", port = 22))
        }
        Mockito.`when`(repository.observeAutomation(1)).thenReturn(MutableStateFlow<List<AutomationAction>>(emptyList()))
        Mockito.`when`(repository.observePortForwardsForHost(1)).thenReturn(MutableStateFlow(emptyList()))
        compose.runOnUiThread {
            val dispatcher = Dispatchers.Main.immediate
            viewModel = AutomationEditorViewModel(SavedStateHandle(mapOf("hostId" to 1L)), repository, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
        }
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                ConnectBotTheme {
                    AutomationEditorScreen(onNavigateBack = { navigatedBack = true }, viewModel = viewModel)
                }
            }
        }
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private fun addString() {
        compose.onNodeWithText(text(R.string.automation_add)).performClick()
        compose.onNodeWithText(text(R.string.automation_send_text)).performClick()
        compose.onNodeWithText(text(R.string.automation_text)).performTextInput("echo test\n")
        compose.onNodeWithText(text(R.string.automation_save)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun addAndSaveShowsThenHidesFabAndPreservesNewline() {
        compose.onNodeWithContentDescription(text(R.string.automation_save)).assertDoesNotExist()
        addString()
        compose.onNodeWithContentDescription(text(R.string.automation_save)).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(text(R.string.automation_save)).assertDoesNotExist()
        assertEquals("echo test\n", viewModel.state.value.actions.single().text)
        assertTrue(!viewModel.state.value.dirty)
    }

    @Test
    fun deletingUnsavedAdditionHidesSaveFab() {
        addString()
        compose.onNodeWithText(text(R.string.automation_delete)).performClick()
        compose.onNodeWithContentDescription(text(R.string.automation_save)).assertDoesNotExist()
    }

    @Test
    fun addActionLabelsShareTheSameLeftEdge() {
        compose.onNodeWithText(text(R.string.automation_add)).performClick()

        val sendLeft = compose.onNodeWithText(text(R.string.automation_send_text), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val waitLeft = compose.onNodeWithText(text(R.string.automation_delay), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
        val disconnectLeft = compose.onNodeWithText(text(R.string.automation_disconnect), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left

        assertEquals(sendLeft, waitLeft, 1f)
        assertEquals(sendLeft, disconnectLeft, 1f)
    }

    @Test
    fun characterKeyUsesSeparateModifierToggles() {
        compose.runOnUiThread {
            viewModel.applyEdit(
                AutomationAction(
                    hostId = 1,
                    type = AutomationActionType.SEND_KEY,
                    key = -'C'.code,
                    modifiers = TerminalKeyModifiers.CTRL,
                ),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.automation_edit)).performClick()
        compose.onNodeWithText("Key: C").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.button_key_alt)).performClick()
        compose.onNodeWithText(text(R.string.automation_save)).performClick()
        compose.waitForIdle()

        val action = viewModel.state.value.actions.single()
        assertEquals(-'C'.code, action.key)
        assertEquals(TerminalKeyModifiers.CTRL or TerminalKeyModifiers.ALT, action.modifiers)
        compose.onNodeWithText("Ctrl+Alt+C").assertIsDisplayed()
    }

    @Test
    fun specialKeyUsesSeparateModifierToggles() {
        compose.runOnUiThread {
            viewModel.applyEdit(AutomationAction(hostId = 1, type = AutomationActionType.SEND_KEY))
        }
        compose.waitForIdle()
        compose.onNodeWithText(text(R.string.automation_edit)).performClick()
        compose.onNodeWithText(text(R.string.automation_key_shift)).performClick()
        compose.onNodeWithText(text(R.string.button_key_ctrl)).performClick()
        compose.onNodeWithText(text(R.string.button_key_alt)).performClick()
        compose.onNodeWithText(text(R.string.automation_save)).performClick()
        compose.waitForIdle()

        val action = viewModel.state.value.actions.single()
        assertEquals(VTermKey.ENTER, action.key)
        assertEquals(TerminalKeyModifiers.SHIFT or TerminalKeyModifiers.CTRL or TerminalKeyModifiers.ALT, action.modifiers)
        compose.onNodeWithText("Shift+Ctrl+Alt+Enter").assertIsDisplayed()
    }

    @Test
    fun savingKeepsTileBoundsAndBlocksEditingUntilCompletion() {
        addString()
        val actions = viewModel.state.value.actions
        var pendingSave: Continuation<Unit>? = null
        runBlocking {
            Mockito.doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val continuation = invocation.rawArguments.last() as Continuation<Unit>
                pendingSave = continuation
                COROUTINE_SUSPENDED
            }.`when`(repository).saveAutomation(1, actions)
        }
        val handle = compose.onNodeWithContentDescription(text(R.string.automation_drag))
        val beforeSave = handle.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(text(R.string.automation_save)).performClick()
        compose.waitForIdle()

        assertTrue(viewModel.state.value.saving)
        assertEquals(beforeSave, handle.fetchSemanticsNode().boundsInRoot)
        handle.assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.automation_edit)).assertIsNotEnabled().performClick()
        compose.onNodeWithText(text(R.string.automation_delete)).assertIsNotEnabled().performClick()
        compose.onNodeWithText(text(R.string.automation_add)).assertIsNotEnabled().performClick()
        assertEquals(actions, viewModel.state.value.actions)
        assertEquals(null, viewModel.state.value.editing)

        compose.runOnUiThread { checkNotNull(pendingSave).resumeWith(Result.success(Unit)) }
        compose.waitForIdle()
        assertTrue(!viewModel.state.value.saving)
        assertEquals(beforeSave, handle.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription(text(R.string.automation_save)).assertDoesNotExist()
    }

    @Test
    fun upNavigationIsDisplayedAndLeavesCleanEditor() {
        compose.onNodeWithContentDescription(text(R.string.button_navigate_up)).assertIsDisplayed().performClick()
        assertTrue(navigatedBack)
    }

    @Test
    fun backWithChangesOffersDiscardAndCancel() {
        addString()
        compose.onNodeWithContentDescription(text(R.string.button_navigate_up)).performClick()
        compose.onNodeWithText(text(R.string.editor_discard_changes_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.button_cancel)).performClick()
        assertTrue(!navigatedBack)
        compose.onNodeWithContentDescription(text(R.string.button_navigate_up)).performClick()
        compose.onNodeWithText(text(R.string.editor_discard)).performClick()
        assertTrue(navigatedBack)
    }

    @Test
    fun backWithChangesCanSaveFromDiscardPrompt() {
        addString()
        compose.onNodeWithContentDescription(text(R.string.button_navigate_up)).performClick()
        compose.onNodeWithText(text(R.string.editor_save)).performClick()
        compose.waitForIdle()
        assertTrue(navigatedBack)
        assertEquals("echo test\n", viewModel.state.value.actions.single().text)
        assertTrue(!viewModel.state.value.dirty)
    }

    @Test
    fun trailingHandleDragReordersActionTilesWithHaptics() {
        val firstAction = AutomationAction(hostId = 1, text = "first")
        val secondAction = AutomationAction(hostId = 1, text = "second")
        compose.runOnUiThread {
            viewModel.applyEdit(firstAction)
            viewModel.applyEdit(secondAction)
        }
        compose.waitForIdle()
        val handle = compose.onAllNodesWithContentDescription(text(R.string.automation_drag))[0].fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithText("first").fetchSemanticsNode().boundsInRoot
        assertTrue(handle.left >= summary.right)
        dragAction(0, 1)
        assertEquals(listOf(secondAction.id, firstAction.id), viewModel.state.value.actions.map { it.id })
        assertTrue(viewModel.state.value.dirty)
        assertEquals(HapticFeedbackType.GestureThresholdActivate, hapticEvents.first())
        assertTrue(hapticEvents.contains(HapticFeedbackType.SegmentFrequentTick))
        assertEquals(HapticFeedbackType.GestureEnd, hapticEvents.last())
        compose.onNodeWithContentDescription(text(R.string.automation_save)).performClick()
        compose.waitForIdle()
        runBlocking {
            Mockito.verify(repository).saveAutomation(1, listOf(secondAction.copy(position = 0), firstAction.copy(position = 1)))
        }
        compose.onNodeWithContentDescription(text(R.string.automation_save)).assertDoesNotExist()

        // Start a second drag after saving to catch stale item keys or positions.
        dragAction(1, 0)
        assertEquals(listOf(firstAction.id, secondAction.id), viewModel.state.value.actions.map { it.id })
        compose.onNodeWithContentDescription(text(R.string.automation_save)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.automation_add)).assertIsDisplayed()
    }

    private fun dragAction(from: Int, to: Int) {
        val handles = compose.onAllNodesWithContentDescription(text(R.string.automation_drag))
        val first = handles[from].fetchSemanticsNode().boundsInRoot
        val second = handles[to].fetchSemanticsNode().boundsInRoot
        handles[from].performTouchInput {
            // The handle starts dragging immediately; no long press is required.
            swipe(start = center, end = center + Offset(0f, second.center.y - first.center.y), durationMillis = 500)
        }
        compose.waitForIdle()
    }
}
