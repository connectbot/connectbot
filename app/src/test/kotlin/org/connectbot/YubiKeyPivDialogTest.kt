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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yubico.yubikit.core.application.InvalidPinException
import com.yubico.yubikit.piv.Slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.connectbot.piv.YubiKeyPiv
import org.connectbot.service.PromptRequest
import org.connectbot.ui.components.YubiKeyPivDialog
import org.connectbot.ui.components.observePivNfcDisabled
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNfcAdapter
import java.security.PublicKey

@RunWith(AndroidJUnit4::class)
class YubiKeyPivDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ShadowNfcAdapter.reset()
    }

    private fun setNfc(enabled: Boolean) {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_NFC, true)
        ShadowNfcAdapter.setNfcHardwareExists(true)
        shadowOf(NfcAdapter.getDefaultAdapter(context)).setEnabled(enabled)
    }

    @Test
    fun nfcReceiverRegistrationAndCleanupRunOffMain() = runBlocking {
        setNfc(enabled = false)
        val registeredOnMain = CompletableDeferred<Boolean>()
        val unregisteredOnMain = CompletableDeferred<Boolean>()
        val wrapped = object : ContextWrapper(context) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, broadcastPermission: String?, scheduler: Handler?, flags: Int): Intent? {
                registeredOnMain.complete(Looper.myLooper() == Looper.getMainLooper())
                return super.registerReceiver(receiver, filter, broadcastPermission, scheduler, flags)
            }

            override fun unregisterReceiver(receiver: BroadcastReceiver) {
                unregisteredOnMain.complete(Looper.myLooper() == Looper.getMainLooper())
                super.unregisterReceiver(receiver)
            }
        }
        withTimeout(5_000) {
            assertTrue(observePivNfcDisabled(wrapped).first())
            assertFalse(registeredOnMain.await())
            assertFalse(unregisteredOnMain.await())
        }
    }

    private fun showDialog() {
        composeTestRule.setContent {
            ConnectBotTheme {
                YubiKeyPivDialog<Unit>(
                    onDismiss = {},
                    onComplete = { _, _ -> },
                    operation = { _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun nfcDisabled_showsHintAndSettingsButton() {
        setNfc(enabled = false)

        showDialog()

        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(context.getString(R.string.pubkey_piv_nfc_off)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_nfc_off)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_nfc_settings)).assertIsDisplayed()
    }

    @Test
    fun nfcEnabled_showsNoHint() {
        setNfc(enabled = true)

        showDialog()

        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_nfc_off)).assertDoesNotExist()
    }

    @Test
    fun noNfcHardware_showsNoHint() {
        ShadowNfcAdapter.setNfcHardwareExists(false)

        showDialog()

        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_nfc_off)).assertDoesNotExist()
    }

    @Test
    fun nfcTurnedOnWhileOpen_hidesHint() {
        setNfc(enabled = false)
        showDialog()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithText(context.getString(R.string.pubkey_piv_nfc_off)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_nfc_off)).assertIsDisplayed()

        setNfc(enabled = true)
        context.sendBroadcast(
            Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED).putExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_ON),
        )
        shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_nfc_off)).assertDoesNotExist()
    }

    private fun showSigningDialog(
        lifecycleOwner: LifecycleOwner,
        probe: suspend (Slot) -> Boolean,
        operation: suspend (Activity, Slot, CharArray) -> Unit,
    ) {
        val activity = mock(Activity::class.java)
        val prompt = PromptRequest.YubiKeyPivPrompt(
            keyNickname = "Test key",
            destination = "test@example.com",
            slot = Slot.AUTHENTICATION,
            publicKey = mock(PublicKey::class.java),
            dataToSign = byteArrayOf(1),
        )
        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivity provides activity, LocalLifecycleOwner provides lifecycleOwner) {
                ConnectBotTheme {
                    YubiKeyPivDialog(
                        onDismiss = {},
                        onComplete = { _, _ -> },
                        operation = operation,
                        signingPrompt = prompt,
                        canSignWithoutPin = probe,
                    )
                }
            }
        }
    }

    private fun readySigningDialog(
        probe: suspend (Slot) -> Boolean = { false },
        onComplete: (Slot, Unit) -> Unit = { _, _ -> },
        onDismiss: () -> Unit = {},
        operation: suspend (Activity, Slot, CharArray) -> Unit,
    ) {
        val prompt = PromptRequest.YubiKeyPivPrompt(
            "Key",
            "user@host",
            Slot.AUTHENTICATION,
            mock(PublicKey::class.java),
            byteArrayOf(1),
        )
        composeTestRule.setContent {
            ConnectBotTheme {
                YubiKeyPivDialog(
                    onDismiss = onDismiss,
                    onComplete = onComplete,
                    operation = operation,
                    signingPrompt = prompt,
                    canSignWithoutPin = probe,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun continueSigning() {
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_continue)).performClick()
    }

    private fun assertSigningError(failure: Exception, message: String) {
        readySigningDialog { _, _, _ -> throw failure }
        continueSigning()
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_continue)).assertIsEnabled()
    }

    @Test
    fun wrongPinShowsAttempts() = assertSigningError(
        InvalidPinException(2),
        context.getString(R.string.pubkey_piv_pin_wrong, 2),
    )

    @Test
    fun blockedPinShowsBlockedError() = assertSigningError(
        InvalidPinException(0),
        context.getString(R.string.pubkey_piv_pin_blocked),
    )

    @Test
    fun manualPinRequiredShowsError() = assertSigningError(
        YubiKeyPiv.PinRequired(),
        context.getString(R.string.pubkey_piv_pin_required),
    )

    @Test
    fun timeoutShowsError() = assertSigningError(
        YubiKeyPiv.DeviceTimeout(),
        context.getString(R.string.pubkey_piv_timeout),
    )

    @Test
    fun unsupportedKeyShowsError() = assertSigningError(
        YubiKeyPiv.UnsupportedKey(),
        context.getString(R.string.pubkey_piv_unsupported_key),
    )

    @Test
    fun wrongKeyShowsError() = assertSigningError(
        YubiKeyPiv.WrongKey(),
        context.getString(R.string.pubkey_piv_wrong_key),
    )

    @Test
    fun unexpectedFailureShowsError() = assertSigningError(
        IllegalStateException(),
        context.getString(R.string.pubkey_piv_failed),
    )

    @Test
    fun automaticPinRequiredFallsBackToManualEntry() {
        var calls = 0
        readySigningDialog(probe = { true }) { _, _, _ ->
            calls++
            throw YubiKeyPiv.PinRequired()
        }
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_pin_required)).assertDoesNotExist()
        continueSigning()
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_pin_required)).assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals(2, calls) }
    }

    @Test
    fun manualSigningPassesPinThenErasesBuffer() {
        var buffer: CharArray? = null
        var completed = false
        readySigningDialog(onComplete = { slot, _ ->
            assertEquals(Slot.AUTHENTICATION, slot)
            completed = true
        }) { _, _, pin ->
            assertEquals("123456", String(pin))
            buffer = pin
        }
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_pin)).performTextReplacement("123456")
        continueSigning()
        composeTestRule.runOnIdle {
            assertTrue(completed)
            assertTrue(buffer!!.all { it == '\u0000' })
        }
    }

    @Test
    fun invalidPinsDoNotReachHardware() {
        var calls = 0
        readySigningDialog { _, _, _ -> calls++ }
        val field = composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_pin))
        for (pin in listOf("123", "12345\u0000", "ééééé")) {
            field.performTextReplacement(pin)
            continueSigning()
            composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_pin_length)).assertIsDisplayed()
        }
        field.performTextReplacement("123456789")
        continueSigning()
        composeTestRule.runOnIdle { assertEquals(0, calls) }
    }

    @Test
    fun cancelStopsPendingSigningAndErasesPin() {
        val pending = CompletableDeferred<Unit>()
        var buffer: CharArray? = null
        var cancelled = false
        var dismissed = false
        readySigningDialog(onDismiss = { dismissed = true }) { _, _, pin ->
            buffer = pin
            try {
                pending.await()
            } finally {
                cancelled = true
            }
        }
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_pin)).performTextReplacement("123456")
        continueSigning()
        composeTestRule.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        composeTestRule.runOnIdle {
            assertTrue(dismissed)
            assertTrue(cancelled)
            assertTrue(buffer!!.all { it == '\u0000' })
        }
    }

    @Test
    fun importValidatesSlotAndReturnsSelectedSlot() {
        var selected: Slot? = null
        composeTestRule.setContent {
            YubiKeyPivDialog(
                onDismiss = {},
                onComplete = { slot, _: Unit -> selected = slot },
                operation = { _, _, _ -> },
            )
        }
        val field = composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_slot))
        val read = composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_read))
        for (slot in listOf("", "00", "f9")) {
            field.performTextReplacement(slot)
            read.assertIsNotEnabled()
        }
        field.performTextReplacement("9c")
        read.assertIsEnabled()
        field.performTextReplacement("xyz")
        read.assertIsEnabled()
        field.performTextReplacement("gg")
        read.assertIsEnabled()
        read.performClick()
        composeTestRule.runOnIdle { assertEquals(Slot.SIGNATURE, selected) }
    }

    @Test
    fun missingActivityDisablesOperation() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivity provides null) {
                YubiKeyPivDialog<Unit>(onDismiss = {}, onComplete = { _, _ -> }, operation = { _, _, _ -> })
            }
        }
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_unavailable)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_read)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
    }

    private class DialogLifecycleOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    @Test
    fun pendingAutomaticProbe_disablesManualSubmission() {
        val owner = DialogLifecycleOwner()
        composeTestRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        val probe = CompletableDeferred<Boolean>()
        var signatures = 0
        showSigningDialog(owner, { probe.await() }, { _, _, _ -> signatures++ })

        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_continue)).assertIsNotEnabled()
        composeTestRule.runOnIdle { probe.complete(true) }
        composeTestRule.runOnIdle { assertEquals(1, signatures) }
    }

    @Test
    fun stoppedDuringAutomaticProbe_cancelsProbeWithoutSigning() {
        val owner = DialogLifecycleOwner()
        composeTestRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        val probe = CompletableDeferred<Boolean>()
        var cancelled = false
        var signatures = 0
        showSigningDialog(owner, {
            try {
                probe.await()
            } finally {
                cancelled = true
            }
        }, { _, _, _ -> signatures++ })

        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_continue)).assertIsNotEnabled()
        composeTestRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        composeTestRule.runOnIdle {
            assertTrue(cancelled)
            probe.complete(true)
        }
        composeTestRule.runOnIdle { assertEquals(0, signatures) }
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_continue)).assertIsEnabled()
    }
}
