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
import kotlinx.coroutines.test.setMain
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.di.CoroutineDispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
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
        dispatchers = dispatchers
    )

    @Test
    fun commonEncodings_hasExpectedFiveEntriesInOrder() {
        val viewModel = createViewModel()
        assertEquals(
            listOf("UTF-8", "ISO-8859-1", "US-ASCII", "windows-1252", "CP437"),
            viewModel.commonEncodings
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
}
