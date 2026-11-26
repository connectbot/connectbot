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

package org.connectbot.util

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SecurePasswordStorageTest {
    @Test
    fun savingAnEmptyPasswordClearsOnlyThatHostsEntry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val host = UUID(0, 1)
        val otherHost = UUID(0, 2)
        context.getSharedPreferences("secure_host_passwords", Context.MODE_PRIVATE).edit {
            putString("password_$host", "saved value")
            putString("password_$otherHost", "another saved value")
        }
        val storage = SecurePasswordStorage(context)
        assertTrue(storage.hasPassword(host))

        storage.savePassword(host, "")

        assertFalse(storage.hasPassword(host))
        assertTrue(storage.hasPassword(otherHost))
    }
}
