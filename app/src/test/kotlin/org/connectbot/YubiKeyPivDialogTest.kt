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
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.connectbot.ui.components.YubiKeyPivDialog
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNfcAdapter

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
        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_nfc_off)).assertIsDisplayed()

        setNfc(enabled = true)
        context.sendBroadcast(
            Intent(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED).putExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_ON),
        )
        shadowOf(Looper.getMainLooper()).idle()

        composeTestRule.onNodeWithText(context.getString(R.string.pubkey_piv_nfc_off)).assertDoesNotExist()
    }
}
