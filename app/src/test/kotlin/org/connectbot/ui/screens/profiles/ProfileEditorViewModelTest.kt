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

package org.connectbot.ui.screens.profiles

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var context: Context
    private lateinit var profileRepository: ProfileRepository
    private lateinit var colorSchemeRepository: ColorSchemeRepository
    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        profileRepository = mock()
        colorSchemeRepository = mock()
        sharedPreferences = mock()

        whenever(colorSchemeRepository.observeAllSchemes()).thenReturn(MutableStateFlow(emptyList()))
        whenever(sharedPreferences.getString("customFonts", "")).thenReturn("")
        whenever(sharedPreferences.getString("customTerminalTypes", "")).thenReturn("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileEditorViewModel = ProfileEditorViewModel(
        savedStateHandle = SavedStateHandle(),
        profileRepository = profileRepository,
        colorSchemeRepository = colorSchemeRepository,
        prefs = sharedPreferences,
        context = context,
        dispatchers = dispatchers,
    )

    @Test
    fun commonEncodings_hasExpectedFiveEntriesInOrder() {
        val viewModel = createViewModel()
        assertEquals(
            listOf("UTF-8", "ISO-8859-1", "US-ASCII", "windows-1252", "CP437"),
            viewModel.commonEncodings,
        )
    }

    @Test
    fun allEncodings_containsCP437() {
        val viewModel = createViewModel()
        assertTrue("allEncodings must contain CP437", viewModel.allEncodings.contains("CP437"))
    }

    @Test
    fun allEncodings_isSortedAlphabetically() {
        val viewModel = createViewModel()
        val sorted = viewModel.allEncodings.sortedWith(String.CASE_INSENSITIVE_ORDER)
        assertEquals("allEncodings must be sorted alphabetically (case-insensitive)", sorted, viewModel.allEncodings)
    }

    @Test
    fun allEncodings_containsAllCommonEncodings() {
        val viewModel = createViewModel()
        val common = listOf("UTF-8", "ISO-8859-1", "US-ASCII", "windows-1252", "CP437")
        common.forEach { encoding ->
            assertTrue("allEncodings must contain $encoding", viewModel.allEncodings.contains(encoding))
        }
    }

    @Test
    fun save_InvalidEnvironmentVariables_SetsErrorAndDoesNotSave() = runTest(testDispatcher) {
        whenever(profileRepository.nameExists("Test", null)).thenReturn(false)
        val viewModel = createViewModel()
        viewModel.updateName("Test")
        viewModel.updateEnvironmentVariables("FOO=bar\nnot a var")

        var saved = false
        viewModel.save { saved = true }

        assertFalse("save must not complete with invalid env vars", saved)
        assertEquals(
            "Environment variables: line 2 is not a valid KEY=VALUE entry",
            viewModel.uiState.value.saveError,
        )
        verify(profileRepository, never()).save(any())
    }

    @Test
    fun save_StartupFields_PersistedOnProfile() = runTest(testDispatcher) {
        whenever(profileRepository.nameExists("Test", null)).thenReturn(false)
        whenever(profileRepository.save(any())).thenReturn(1L)
        val viewModel = createViewModel()
        viewModel.updateName("Test")
        viewModel.updateStartupCommand("tmux attach")
        viewModel.updateStartupCommandMode(Profile.STARTUP_MODE_EXEC_PTY)
        viewModel.updateEnvironmentVariables("FOO=bar")

        var saved = false
        viewModel.save { saved = true }

        assertTrue("save must complete", saved)
        val captor = argumentCaptor<Profile>()
        verify(profileRepository).save(captor.capture())
        assertEquals("tmux attach", captor.firstValue.startupCommand)
        assertEquals(Profile.STARTUP_MODE_EXEC_PTY, captor.firstValue.startupCommandMode)
        assertEquals("FOO=bar", captor.firstValue.environmentVariables)
    }

    @Test
    fun save_BlankStartupFields_PersistedAsNull() = runTest(testDispatcher) {
        whenever(profileRepository.nameExists("Test", null)).thenReturn(false)
        whenever(profileRepository.save(any())).thenReturn(1L)
        val viewModel = createViewModel()
        viewModel.updateName("Test")
        viewModel.updateStartupCommand("   ")
        viewModel.updateEnvironmentVariables("")

        viewModel.save { }

        val captor = argumentCaptor<Profile>()
        verify(profileRepository).save(captor.capture())
        assertNull(captor.firstValue.startupCommand)
        assertNull(captor.firstValue.environmentVariables)
        assertEquals(Profile.STARTUP_MODE_INJECT, captor.firstValue.startupCommandMode)
    }
}
