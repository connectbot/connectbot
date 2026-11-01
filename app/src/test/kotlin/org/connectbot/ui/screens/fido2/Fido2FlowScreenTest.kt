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

package org.connectbot.ui.screens.fido2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.connectbot.HiltComponentActivity
import org.connectbot.R
import org.connectbot.data.entity.Fido2Transport
import org.connectbot.fido2.Fido2Algorithm
import org.connectbot.fido2.Fido2ConnectionState
import org.connectbot.fido2.Fido2Credential
import org.connectbot.service.PromptRequest
import org.connectbot.service.PromptResponse
import org.connectbot.ui.components.Fido2NfcTapOverlay
import org.connectbot.ui.components.InlinePrompt
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class Fido2FlowScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<HiltComponentActivity>()
    private val vm = mock(ImportFido2ViewModel::class.java)
    private val state = MutableStateFlow(ImportFido2UiState())
    private val visible = mutableStateOf(true)
    private var backs = 0
    private val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        `when`(vm.uiState).thenReturn(state)
        rule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
    }
    private fun text(id: Int, vararg args: Any) = rule.activity.getString(id, *args)
    private fun click(id: Int) {
        rule.onNode(hasText(text(id)) and hasClickAction()).performClick()
    }
    private fun update(value: ImportFido2UiState) {
        rule.runOnIdle { state.value = value }
    }
    private fun render() {
        rule.setContent { ConnectBotTheme { CompositionLocalProvider(LocalLifecycleOwner provides owner) { if (visible.value) ImportFido2Screen(onNavigateBack = { backs++ }, viewModel = vm) } } }
    }
    private fun credential(algorithm: Fido2Algorithm, name: String?) = Fido2Credential(byteArrayOf(1), "ssh:server", null, name, byteArrayOf(1), algorithm)

    @Test
    fun transportSelectionForwardsUsbNfcContinueAndCancelActions() {
        render()
        click(R.string.fido2_transport_nfc)
        verify(vm).updateTransport(Fido2Transport.NFC)
        update(state.value.copy(selectedTransport = Fido2Transport.NFC))
        click(R.string.fido2_transport_usb)
        verify(vm).updateTransport(Fido2Transport.USB)
        click(R.string.fido2_continue)
        verify(vm).confirmTransportSelection()
        click(R.string.fido2_cancel)
        assertEquals(1, backs)
    }

    @Test
    fun rendersUsbAndNfcConnectionProgressErrorsAndTapInstructions() {
        update(state.value.copy(transportSelected = true))
        render()
        verify(vm).startUsbDiscovery()
        rule.onNodeWithText(text(R.string.fido2_connect_usb)).assertIsDisplayed()
        update(state.value.copy(connectionState = Fido2ConnectionState.Connecting))
        rule.onNodeWithText(text(R.string.fido2_waiting_connection)).assertIsDisplayed()
        update(state.value.copy(connectionState = Fido2ConnectionState.Connected("USB", "key")))
        rule.onNodeWithText(text(R.string.fido2_connected)).assertIsDisplayed()
        update(state.value.copy(isScanning = true))
        rule.onNodeWithText(text(R.string.fido2_scanning)).assertIsDisplayed()
        update(state.value.copy(isScanning = false, connectionState = Fido2ConnectionState.Error("offline")))
        rule.onNodeWithText("offline").assertIsDisplayed()
        update(state.value.copy(connectionState = Fido2ConnectionState.Disconnected, selectedTransport = Fido2Transport.NFC))
        rule.onNodeWithText(text(R.string.fido2_waiting_connection)).assertIsDisplayed()
        verify(vm, atLeastOnce()).startNfcDiscovery(rule.activity)
        rule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        rule.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        verify(vm, atLeastOnce()).stopNfcDiscovery(rule.activity)
        update(state.value.copy(waitingForNfcTap = true))
        rule.onNodeWithText(text(R.string.fido2_tap_and_hold)).assertIsDisplayed()
        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        verify(vm, atLeastOnce()).stopUsbDiscovery()
    }

    @Test
    fun pinEntryDisplaysErrorsAndSubmitsThroughButtonAndKeyboard() {
        update(state.value.copy(transportSelected = true, needsPin = true))
        render()
        rule.onNodeWithText(text(R.string.button_ok)).assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("1234")
        click(R.string.button_ok)
        rule.onNode(hasSetTextAction()).performImeAction()
        verify(vm, times(2)).submitPin("1234")
        update(state.value.copy(pinError = "Try again"))
        rule.onNodeWithText("Try again").assertIsDisplayed()
        click(R.string.fido2_cancel)
        assertEquals(1, backs)
    }

    @Test
    fun credentialSelectionNicknameAndTransportControlsCompleteImport() {
        val ed = credential(Fido2Algorithm.EDDSA, "user")
        val ec = credential(Fido2Algorithm.ES256, null)
        update(state.value.copy(transportSelected = true, credentials = listOf(ed, ec)))
        render()
        rule.onNodeWithText("user").performClick()
        verify(vm).selectCredential(ed)
        rule.onNodeWithText("SSH Key").performClick()
        verify(vm).selectCredential(ec)
        update(state.value.copy(selectedCredential = ec))
        rule.onNode(hasText(text(R.string.fido2_import_credential)) and hasClickAction()).assertIsNotEnabled()
        rule.onNode(hasSetTextAction()).performTextReplacement("nickname")
        verify(vm).updateNickname("nickname")
        update(state.value.copy(nickname = "nickname"))
        click(R.string.fido2_transport_nfc)
        verify(vm).updateTransport(Fido2Transport.NFC)
        update(state.value.copy(selectedTransport = Fido2Transport.NFC, selectedCredential = ed))
        click(R.string.fido2_transport_usb)
        verify(vm).updateTransport(Fido2Transport.USB)
        click(R.string.fido2_import_credential)
        verify(vm).importSelectedCredential()
        click(R.string.fido2_cancel)
        verify(vm).clearSelection()
        update(state.value.copy(importSuccess = true))
        rule.waitForIdle()
        assertEquals(1, backs)
    }

    @Test
    fun inlineFidoPromptsShowRetryCountsSubmitPinAndCancel() {
        val prompt = mutableStateOf<PromptRequest?>(PromptRequest.Fido2ConnectPrompt("key", byteArrayOf(1), Fido2Transport.USB))
        var cancels = 0
        val responses = mutableListOf<PromptResponse>()
        rule.setContent { ConnectBotTheme { InlinePrompt(prompt.value, onResponse = { responses.add(it) }, onCancel = { cancels++ }) } }
        rule.onNodeWithText(text(R.string.fido2_connect_title)).assertIsDisplayed()
        click(R.string.fido2_cancel)
        for (attempts in listOf(null, 5, 2)) {
            rule.runOnIdle { prompt.value = PromptRequest.Fido2PinPrompt("key", attempts) }
            rule.onNodeWithText(text(R.string.fido2_pin_title)).assertIsDisplayed()
            rule.onNode(hasSetTextAction()).performTextReplacement("1234")
            click(R.string.button_ok)
            rule.onNode(hasSetTextAction()).performImeAction()
            click(R.string.fido2_cancel)
        }
        assertEquals(6, responses.size)
        assertTrue(responses.all { (it as PromptResponse.Fido2PinResponse).pin == "1234" })
        rule.runOnIdle { prompt.value = PromptRequest.Fido2TouchPrompt("key") }
        rule.onNodeWithText(text(R.string.fido2_touch_title)).assertIsDisplayed()
        click(R.string.fido2_cancel)
        assertEquals(5, cancels)
        rule.runOnIdle { prompt.value = null }
    }

    @Test
    fun nfcSigningOverlayExplainsTapAndOffersCancellation() {
        var cancelled = false
        rule.setContent { ConnectBotTheme { Fido2NfcTapOverlay(onCancel = { cancelled = true }) } }
        rule.onNodeWithText(text(R.string.fido2_nfc_tap_title)).assertIsDisplayed()
        click(R.string.fido2_cancel)
        assertTrue(cancelled)
    }

    @Test
    fun keyEditorAllowsChangingTransportForHardwareKeys() {
        val editor = mutableStateOf(org.connectbot.ui.screens.pubkeyeditor.PubkeyEditorUiState(isLoading = false, isFido2 = true, nickname = "key"))
        val selected = mutableListOf<Fido2Transport>()
        rule.setContent {
            ConnectBotTheme {
                org.connectbot.ui.screens.pubkeyeditor.PubkeyEditorScreenContent(
                    uiState = editor.value, onNavigateBack = {}, onNicknameChange = {},
                    onOldPasswordChange = {}, onNewPassword1Change = {}, onNewPassword2Change = {},
                    onUnlockAtStartupChange = {}, onConfirmUseChange = {},
                    onFido2TransportChange = { selected.add(it) }, onSave = {},
                )
            }
        }
        rule.onNodeWithText(text(R.string.fido2_transport_nfc)).performScrollTo().performClick()
        rule.runOnIdle { editor.value = editor.value.copy(fido2Transport = Fido2Transport.NFC) }
        rule.onNodeWithText(text(R.string.fido2_transport_usb)).performScrollTo().performClick()
        assertEquals(listOf(Fido2Transport.NFC, Fido2Transport.USB), selected)
    }
}
