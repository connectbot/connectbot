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
import org.connectbot.di.FakeLanguagePackManager
import org.connectbot.util.PreferenceConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelReconnectTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var profileRepository: ProfileRepository

    @Before
    fun setUp() = runTest {
        prefs = mock(SharedPreferences::class.java)
        prefsEditor = mock(SharedPreferences.Editor::class.java)
        profileRepository = mock(ProfileRepository::class.java)

        `when`(prefs.edit()).thenReturn(prefsEditor)
        `when`(prefsEditor.putString(anyString(), nullable(String::class.java))).thenReturn(prefsEditor)
        `when`(prefsEditor.putBoolean(anyString(), anyBoolean())).thenReturn(prefsEditor)
        `when`(prefsEditor.putFloat(anyString(), anyFloat())).thenReturn(prefsEditor)
        `when`(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer { invocation ->
            invocation.arguments[1] as Boolean
        }
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenAnswer { invocation ->
            invocation.arguments[1] as String?
        }
        `when`(prefs.getFloat(anyString(), anyFloat())).thenAnswer { invocation ->
            invocation.arguments[1] as Float
        }
        `when`(profileRepository.getAll()).thenReturn(emptyList<Profile>())
    }

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(
        prefs = prefs,
        profileRepository = profileRepository,
        context = RuntimeEnvironment.getApplication(),
        dispatchers = dispatchers,
        languagePackManager = FakeLanguagePackManager(),
    )

    @Test
    fun reconnectPreferences_loadFromSharedPreferences() = runTest {
        `when`(prefs.getString(eq(PreferenceConstants.RECONNECT_MAX_ATTEMPTS), nullable(String::class.java)))
            .thenReturn("4")
        `when`(prefs.getString(eq(PreferenceConstants.RECONNECT_INTERVAL), nullable(String::class.java)))
            .thenReturn("9")
        `when`(prefs.getBoolean(eq(PreferenceConstants.RECONNECT_BACKOFF), anyBoolean())).thenReturn(false)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("4", viewModel.uiState.value.reconnectMaxAttempts)
        assertEquals("9", viewModel.uiState.value.reconnectInterval)
        assertFalse(viewModel.uiState.value.reconnectBackoff)
    }

    @Test
    fun updateReconnectMaxAttempts_writesNumericString() = runTest {
        val viewModel = createViewModel()

        viewModel.updateReconnectMaxAttempts("6")
        advanceUntilIdle()

        assertEquals("6", viewModel.uiState.value.reconnectMaxAttempts)
        verify(prefsEditor).putString(PreferenceConstants.RECONNECT_MAX_ATTEMPTS, "6")
    }

    @Test
    fun updateReconnectInterval_ignoresNonNumericValue() = runTest {
        val viewModel = createViewModel()

        viewModel.updateReconnectInterval("slow")
        advanceUntilIdle()

        assertEquals(
            PreferenceConstants.DEFAULT_RECONNECT_INTERVAL_SECONDS.toString(),
            viewModel.uiState.value.reconnectInterval,
        )
        verify(prefsEditor, never()).putString(PreferenceConstants.RECONNECT_INTERVAL, "slow")
    }

    @Test
    fun updateReconnectBackoff_writesBoolean() = runTest {
        val viewModel = createViewModel()

        viewModel.updateReconnectBackoff(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.reconnectBackoff)
        verify(prefsEditor).putBoolean(PreferenceConstants.RECONNECT_BACKOFF, false)
    }
}
