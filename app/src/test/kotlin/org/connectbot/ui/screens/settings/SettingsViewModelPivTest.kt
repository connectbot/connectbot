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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.connectbot.data.ProfileRepository
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.di.FakeLanguagePackManager
import org.connectbot.util.PreferenceConstants
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyFloat
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelPivTest {
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(default = testDispatcher, io = testDispatcher, main = testDispatcher)
    private val prefs = mock(SharedPreferences::class.java)
    private val editor = mock(SharedPreferences.Editor::class.java)
    private val profileRepository = mock(ProfileRepository::class.java)

    @Before
    fun setUp() = runTest {
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), any())).thenReturn(editor)
        `when`(prefs.getBoolean(anyString(), anyBoolean())).thenReturn(false)
        `when`(prefs.getString(anyString(), any())).thenReturn("")
        `when`(prefs.getFloat(anyString(), anyFloat())).thenReturn(0.25f)
        `when`(profileRepository.getAll()).thenReturn(emptyList<Profile>())
    }

    private fun TestScope.viewModel(): SettingsViewModel = SettingsViewModel(
        prefs,
        profileRepository,
        RuntimeEnvironment.getApplication(),
        dispatchers,
        FakeLanguagePackManager(),
    ).also { advanceUntilIdle() }

    @Test
    fun sessionIdle_defaultsToFiveMinutes() = runTest {
        `when`(prefs.getString(eq(PreferenceConstants.PIV_SESSION_IDLE_SECONDS), isNull())).thenReturn(null)
        assertEquals("300", viewModel().uiState.value.pivSessionIdleSeconds)
    }

    @Test
    fun sessionIdle_loadsSavedValue() = runTest {
        `when`(prefs.getString(eq(PreferenceConstants.PIV_SESSION_IDLE_SECONDS), isNull())).thenReturn("900")
        assertEquals("900", viewModel().uiState.value.pivSessionIdleSeconds)
    }

    @Test
    fun updateSessionIdle_savesAndUpdatesState() = runTest {
        val viewModel = viewModel()
        viewModel.updatePivSessionIdle("0")
        advanceUntilIdle()
        verify(editor).putString(PreferenceConstants.PIV_SESSION_IDLE_SECONDS, "0")
        assertEquals("0", viewModel.uiState.value.pivSessionIdleSeconds)
    }
}
