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

package org.connectbot.ui

import android.content.Intent
import android.os.Bundle
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.service.ConnectionNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class MainActivityIntentTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun createShortcutIntentEnablesHostSelection() {
        val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            putExtra(ConnectionNotifier.EXTRA_SESSION_ID, "shortcut-session")
            putExtra(ConnectionNotifier.EXTRA_RECONNECT, true)
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).create()
        try {
            assertTrue(controller.get().makingShortcut)
            val savedState = Bundle()
            controller.saveInstanceState(savedState)
            assertEquals("shortcut-session", savedState.getString(ConnectionNotifier.EXTRA_SESSION_ID))
            assertTrue(savedState.getBoolean(ConnectionNotifier.EXTRA_RECONNECT))
        } finally {
            controller.destroy()
        }
    }
}
