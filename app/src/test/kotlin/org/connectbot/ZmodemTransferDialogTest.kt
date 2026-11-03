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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.connectbot.service.TerminalBridge
import org.connectbot.service.ZmodemController
import org.connectbot.ui.screens.console.ZmodemTransferDialog
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.UUID

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ZmodemTransferDialogTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    private val controller = mock(ZmodemController::class.java)
    private val bridge = mock(TerminalBridge::class.java)
    private val state = MutableStateFlow(ZmodemController.State())

    private fun setContent(initial: ZmodemController.State, registryOwner: ActivityResultRegistryOwner? = null) {
        hiltRule.inject()
        state.value = initial
        `when`(controller.state).thenReturn(state)
        `when`(bridge.zmodem).thenReturn(controller)
        composeTestRule.setContent {
            if (registryOwner == null) {
                ConnectBotTheme { ZmodemTransferDialog(bridge) }
            } else {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    ConnectBotTheme { ZmodemTransferDialog(bridge) }
                }
            }
        }
    }

    @Test
    fun selectionAndProgressUpdateAndCancelInvokesController() {
        setContent(ZmodemController.State(active = true))
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_select)).assertIsDisplayed()
        composeTestRule.runOnIdle { state.value = state.value.copy(name = "file.bin", bytes = 42) }
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_progress, "file.bin", 42L)).assertIsDisplayed()
        composeTestRule.runOnIdle { state.value = state.value.copy(total = 100) }
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_progress_total, "file.bin", 42L, 100L)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_cancel)).performClick()
        verify(controller).cancel()
        composeTestRule.runOnIdle { state.value = state.value.copy(cancelling = true) }
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_cancelling)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_cancel)).assertIsNotEnabled()
    }

    @Test
    fun successfulCompletionDismissesProgressDialog() {
        setContent(ZmodemController.State(active = true, name = "skullpl.gif", bytes = 100, total = 100))
        val cancel = composeTestRule.activity.getString(R.string.zmodem_cancel)
        composeTestRule.onNodeWithText(cancel).assertIsDisplayed()
        composeTestRule.runOnIdle { state.value = ZmodemController.State() }
        composeTestRule.onNodeWithText(cancel).assertDoesNotExist()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_title)).assertDoesNotExist()
    }

    @Test
    fun errorCanBeDismissed() {
        setContent(ZmodemController.State(error = "Transfer timed out"))
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_failed)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_dismiss)).performClick()
        verify(controller).dismissError()
        composeTestRule.runOnIdle { state.value = ZmodemController.State() }
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.zmodem_title)).assertDoesNotExist()
    }
    private fun pickerResult(upload: Boolean) {
        val id = UUID.randomUUID()
        val uri = Uri.parse("content://documents/selected")
        var launches = 0
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    launches++
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
                }
            }
        }
        doAnswer {
            state.value = state.value.copy(picker = null)
            null
        }.`when`(controller).selected(any(UUID::class.java) ?: id, anyList())
        setContent(ZmodemController.State(active = true, picker = id, upload = upload), owner)
        composeTestRule.waitForIdle()
        verify(controller).selected(id, listOf(uri))
        assertEquals(1, launches)
        composeTestRule.runOnIdle { state.value = state.value.copy(name = "progress") }
        composeTestRule.waitForIdle()
        assertEquals(1, launches)
    }

    @Test
    fun uploadPickerResultKeepsRequestIdentity() = pickerResult(upload = true)

    @Test
    fun downloadPickerResultKeepsRequestIdentity() = pickerResult(upload = false)
}
