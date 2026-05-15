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

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.util.PreferenceConstants
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the user's on-screen key bar
 * configuration.
 *
 * - Reads the KEY_BAR_CONFIG pref on construction, running the
 *   migration default if absent.
 * - Exposes a [StateFlow] so the editor screen and the runtime
 *   TerminalKeyboard stay in sync.
 * - Listens for external pref changes (e.g. cross-process or
 *   future test scenarios) and re-emits.
 */
@Singleton
class KeyBarConfigRepository @Inject constructor(
    private val prefs: SharedPreferences,
) {

    private val _config = MutableStateFlow(load())
    val config: StateFlow<List<KeyEntry>> = _config.asStateFlow()

    @Suppress("unused") // Held to keep the listener alive for the repo's lifetime.
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PreferenceConstants.KEY_BAR_CONFIG) {
                _config.value = load()
            }
        }.also { prefs.registerOnSharedPreferenceChangeListener(it) }

    fun update(entries: List<KeyEntry>) {
        prefs.edit { putString(PreferenceConstants.KEY_BAR_CONFIG, KeyBarConfigJson.encode(entries)) }
    }

    fun reset() {
        update(defaultKeyBarConfig())
    }

    private fun load(): List<KeyEntry> {
        val raw = prefs.getString(PreferenceConstants.KEY_BAR_CONFIG, null)
        return if (raw.isNullOrBlank()) {
            // First launch: migrate to default and persist.
            val default = defaultKeyBarConfig()
            prefs.edit { putString(PreferenceConstants.KEY_BAR_CONFIG, KeyBarConfigJson.encode(default)) }
            default
        } else {
            KeyBarConfigJson.decode(raw).ifEmpty { defaultKeyBarConfig() }
        }
    }
}
