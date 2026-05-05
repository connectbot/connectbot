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

import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.connectbot.data.ProfileRepository
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelThemeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var profileRepository: ProfileRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() = runTest {
        prefs = mock()
        prefsEditor = mock()
        profileRepository = mock()

        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putString(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putFloat(any(), any())).thenReturn(prefsEditor)
        whenever(prefs.getBoolean(any(), any())).thenReturn(false)
        whenever(prefs.getString(any(), any())).thenReturn("")
        whenever(prefs.getFloat(any(), any())).thenReturn(0.25f)
        whenever(prefs.getString(eq(PreferenceConstants.THEME_MODE), isNull())).thenReturn("SYSTEM")
        whenever(profileRepository.getAll()).thenReturn(emptyList<Profile>())

        viewModel = SettingsViewModel(
            prefs,
            profileRepository,
            RuntimeEnvironment.getApplication(),
            dispatchers,
        )
        advanceUntilIdle()
    }

    @Test
    fun themeMode_defaultsToSystem() = runTest {
        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
    }

    @Test
    fun themeMode_loadsFromPreferences_whenDarkSaved() = runTest {
        whenever(prefs.getString(eq(PreferenceConstants.THEME_MODE), isNull())).thenReturn("DARK")
        val vm = SettingsViewModel(
            prefs,
            profileRepository,
            RuntimeEnvironment.getApplication(),
            dispatchers,
        )
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
    }

    @Test
    fun updateThemeMode_updatesUiState() = runTest {
        viewModel.updateThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
    }

    @Test
    fun updateThemeMode_writesStringToPreferences() = runTest {
        viewModel.updateThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        verify(prefsEditor).putString(PreferenceConstants.THEME_MODE, "DARK")
    }
}
