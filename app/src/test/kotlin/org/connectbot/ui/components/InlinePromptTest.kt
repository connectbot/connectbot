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

package org.connectbot.ui.components

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.connectbot.R
import org.connectbot.service.PromptRequest
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InlinePromptTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun copiesTheSelectedHostKeyFingerprint() {
        val prompt = PromptRequest.HostKeyFingerprintPrompt(
            hostname = "example.org",
            keyType = "ssh-ed25519",
            keySize = 256,
            serverHostKey = byteArrayOf(1),
            randomArt = "random art",
            bubblebabble = "bubble babble",
            sha256 = "SHA256:fingerprint",
            md5 = "MD5:aa:bb",
        )
        rule.setContent {
            ConnectBotTheme {
                InlinePrompt(prompt, onResponse = {}, onCancel = {})
            }
        }

        val sha256 = rule.activity.getString(R.string.fingerprint_format_sha256)
        val md5 = rule.activity.getString(R.string.fingerprint_format_md5)
        val copy = rule.onNodeWithContentDescription(rule.activity.getString(R.string.fingerprint_copy_description))

        copy.performClick()
        assertClipboard(sha256, prompt.sha256)
        rule.onNodeWithText(sha256).performClick()
        rule.onNodeWithText(md5).performClick()
        copy.performClick()
        assertClipboard(md5, prompt.md5)
    }

    private fun assertClipboard(label: String, value: String) {
        rule.runOnIdle {
            val clipboard = checkNotNull(rule.activity.getSystemService(ClipboardManager::class.java))
            val clip = checkNotNull(clipboard.primaryClip)
            assertEquals(label, clip.description.label.toString())
            assertEquals(value, clip.getItemAt(0).text.toString())
        }
    }
}
