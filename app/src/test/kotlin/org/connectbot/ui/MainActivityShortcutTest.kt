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

import android.app.Activity
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.util.IconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mockStatic
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityShortcutTest {
    private val activity = Robolectric.buildActivity(MainActivity::class.java).get()

    @Test
    fun emptyNicknameUsesHostnameForShortcutLabel() {
        createShortcut(Host(id = 1, nickname = "", hostname = "example.org"))

        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        assertEquals("example.org", shadowOf(activity).resultIntent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
        assertTrue(activity.isFinishing)
    }

    @Test
    fun blankNicknameUsesHostnameForShortcutLabel() {
        createShortcut(Host(id = 1, nickname = "   ", hostname = "example.org"))

        assertEquals("example.org", shadowOf(activity).resultIntent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
    }

    @Test
    fun localHostWithoutNicknameUsesLocalizedAppName() {
        createShortcut(Host.createLocalHost(""))

        assertEquals(activity.getString(R.string.app_name), shadowOf(activity).resultIntent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
    }

    @Test
    fun namedHostKeepsItsShortcutLabel() {
        createShortcut(Host(id = 1, nickname = "Production", hostname = "example.org"))

        assertEquals("Production", shadowOf(activity).resultIntent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
    }

    @Test
    fun shortcutServiceFailureCancelsWithoutCrashing() {
        mockStatic(ShortcutManagerCompat::class.java).use { shortcuts ->
            shortcuts.`when`<Intent> {
                ShortcutManagerCompat.createShortcutResultIntent(any(), any(ShortcutInfoCompat::class.java))
            }.thenThrow(IllegalStateException("Shortcut service unavailable"))

            createShortcut(Host(id = 1, nickname = "Production", hostname = "example.org"))

            assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
            assertTrue(activity.isFinishing)
        }
    }

    private fun createShortcut(host: Host) {
        val method = MainActivity::class.java.getDeclaredMethod(
            "createShortcutAndFinish",
            Host::class.java,
            String::class.java,
            IconStyle::class.java,
        ).apply { isAccessible = true }
        method.invoke(activity, host, null, IconStyle.TERMINAL)
    }
}
