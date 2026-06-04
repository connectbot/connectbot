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
package org.connectbot.util.keybar

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyBarConfigRepositoryTest {

    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        prefs = ctx.getSharedPreferences("keybar_repo_test", Context.MODE_PRIVATE)
        prefs.edit { clear() }
    }

    @After
    fun tearDown() {
        prefs.edit { clear() }
    }

    @Test
    fun migrationWritesDefaultOnEmptyPrefs() {
        val repo = KeyBarConfigRepository(prefs)
        assertThat(repo.config.value).isEqualTo(defaultKeyBarConfig())
        assertThat(prefs.getString(PreferenceConstants.KEY_BAR_CONFIG, null)).isNotNull()
    }

    @Test
    fun migrationIsIdempotentOnPopulatedPrefs() {
        val custom = listOf(
            KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
            KeyEntry.Macro("hi", "hello"),
        )
        prefs.edit { putString(PreferenceConstants.KEY_BAR_CONFIG, KeyBarConfigJson.encode(custom)) }
        val repo = KeyBarConfigRepository(prefs)
        assertThat(repo.config.value).isEqualTo(custom)
    }

    @Test
    fun updateWritesThroughAndEmits() {
        val repo = KeyBarConfigRepository(prefs)
        val next = listOf(KeyEntry.Builtin(BuiltinKeyId.ESC, visible = true))
        repo.update(next)
        assertThat(repo.config.value).isEqualTo(next)
    }

    @Test
    fun externalPrefChangeIsObserved() {
        val repo = KeyBarConfigRepository(prefs)
        val external = listOf(KeyEntry.Builtin(BuiltinKeyId.TAB, visible = false))
        prefs.edit {
            putString(PreferenceConstants.KEY_BAR_CONFIG, KeyBarConfigJson.encode(external))
        }
        assertThat(repo.config.value).isEqualTo(external)
    }

    @Test
    fun resetRestoresDefault() {
        val repo = KeyBarConfigRepository(prefs)
        repo.update(listOf(KeyEntry.Macro("x", "y")))
        repo.reset()
        assertThat(repo.config.value).isEqualTo(defaultKeyBarConfig())
    }
}
