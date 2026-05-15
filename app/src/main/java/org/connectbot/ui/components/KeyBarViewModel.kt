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

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.keybar.KeyBarConfigRepository
import org.connectbot.util.keybar.KeyEntry
import javax.inject.Inject

/**
 * View-model surface for the on-screen key bar in the terminal
 * screen. Exposes:
 *  - the user's [KeyEntry] list (sourced from [KeyBarConfigRepository])
 *  - the current "bumpy arrows" preference, observed reactively so
 *    enabling/disabling it from Settings updates the running bar
 *    without recomposition tricks.
 *
 * Hilt-scoped to the [KeyBarViewModel] composable via
 * `hiltViewModel()`; one instance per terminal screen.
 */
@HiltViewModel
class KeyBarViewModel @Inject constructor(
    keyBarRepo: KeyBarConfigRepository,
    private val prefs: SharedPreferences,
) : ViewModel() {

    val config: StateFlow<List<KeyEntry>> = keyBarRepo.config

    private val _bumpyArrows = MutableStateFlow(readBumpyArrows())
    val bumpyArrows: StateFlow<Boolean> = _bumpyArrows.asStateFlow()

    @Suppress("unused") // Held to keep the listener alive for this VM's lifetime.
    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PreferenceConstants.BUMPY_ARROWS) {
                _bumpyArrows.value = readBumpyArrows()
            }
        }.also { prefs.registerOnSharedPreferenceChangeListener(it) }

    private fun readBumpyArrows(): Boolean = prefs.getBoolean(PreferenceConstants.BUMPY_ARROWS, false)
}
