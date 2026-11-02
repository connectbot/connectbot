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

package org.connectbot.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.connectbot.data.ProfileRepository
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.di.FakeLanguagePackManager
import org.connectbot.util.PreferenceConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelZmodemTest {
    @Test
    fun zmodemDefaultsEnabledAndPersistsChangesAcrossViewModels() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(UUID.randomUUID().toString(), Context.MODE_PRIVATE)
        val repository = mock(ProfileRepository::class.java)
        `when`(repository.getAll()).thenReturn(emptyList())
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
        fun create() = SettingsViewModel(prefs, repository, context, dispatchers, FakeLanguagePackManager())
        val viewModel = create()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.zmodem)
        viewModel.updateZmodem(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.zmodem)
        assertFalse(prefs.getBoolean(PreferenceConstants.ZMODEM, true))
        val restored = create()
        advanceUntilIdle()
        assertFalse(restored.uiState.value.zmodem)
        restored.updateZmodem(true)
        advanceUntilIdle()
        assertTrue(restored.uiState.value.zmodem)
        assertTrue(prefs.getBoolean(PreferenceConstants.ZMODEM, false))
    }
}
