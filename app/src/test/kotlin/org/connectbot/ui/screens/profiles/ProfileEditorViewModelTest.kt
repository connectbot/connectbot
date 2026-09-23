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
import org.connectbot.data.EntityIds
import org.connectbot.data.ProfileRepository
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.HostConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.util.UUID

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
        profileRepository = mock(ProfileRepository::class.java)
        colorSchemeRepository = mock(ColorSchemeRepository::class.java)
        sharedPreferences = mock(SharedPreferences::class.java)

        `when`(colorSchemeRepository.observeAllSchemes()).thenReturn(MutableStateFlow(emptyList()))
        `when`(sharedPreferences.getString("customFonts", "")).thenReturn("")
        `when`(sharedPreferences.getString("customTerminalTypes", "")).thenReturn("")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun rejectedSharedProfileSaveIsShownWithoutClosingEditor() = runTest {
        val profile = Profile(name = "Shared", repositoryId = UUID.randomUUID())
        `when`(profileRepository.getById(profile.id)).thenReturn(profile)
        `when`(profileRepository.nameExists("Shared", profile.id)).thenReturn(false)
        `when`(profileRepository.save(profile)).thenThrow(IllegalStateException("Repository is read-only"))
        val viewModel = ProfileEditorViewModel(SavedStateHandle(mapOf("profileId" to profile.id.toString())), profileRepository, colorSchemeRepository, sharedPreferences, context, dispatchers)
        var closed = false
        viewModel.updateName("Shared")
        viewModel.save { closed = true }
        val saved = ArgumentCaptor.forClass(Profile::class.java)
        verify(profileRepository).save(saved.capture() ?: Profile(name = "unused"))
        assertEquals(profile, saved.value)
        assertEquals("Repository is read-only", viewModel.uiState.value.saveError)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(closed)
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
    fun changingOnlyKeyboardLayoutCanBeSavedIncludingAppDefault() = runTest {
        val originalLayoutId = UUID.randomUUID()
        val profile = Profile(name = "Keyboard", keyboardLayoutId = originalLayoutId)
        `when`(profileRepository.getById(profile.id)).thenReturn(profile)
        `when`(profileRepository.nameExists(profile.name, profile.id)).thenReturn(false)
        for (layoutId in listOf(UUID.randomUUID(), null)) {
            val viewModel = ProfileEditorViewModel(SavedStateHandle(mapOf("profileId" to profile.id.toString())), profileRepository, colorSchemeRepository, sharedPreferences, context, dispatchers)
            assertFalse(viewModel.uiState.value.hasUnsavedChanges)
            viewModel.updateKeyboardLayoutId(layoutId)
            assertTrue(viewModel.uiState.value.hasUnsavedChanges)
            var closed = false
            viewModel.save { closed = true }
            verify(profileRepository).save(profile.copy(keyboardLayoutId = layoutId))
            assertTrue(closed)
            assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        }
    }

    @Test
    fun changingKeyboardLayoutClearsPreviousSaveError() = runTest {
        val viewModel = createViewModel()
        viewModel.updateName("Duplicate")
        `when`(profileRepository.nameExists("Duplicate", null)).thenReturn(true)
        viewModel.save {}
        assertTrue(viewModel.uiState.value.saveError != null)
        viewModel.updateKeyboardLayoutId(UUID.randomUUID())
        assertEquals(null, viewModel.uiState.value.saveError)
    }

    @Test
    fun boldAsBrightDefaultsToEnabledAndSavesDisabledSelection() = runTest {
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.boldAsBright)
        assertTrue(Profile.createDefault().boldAsBright)
        viewModel.updateName("Colors")
        viewModel.updateBoldAsBright(false)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        `when`(profileRepository.nameExists("Colors", null)).thenReturn(false)
        viewModel.save {}
        val captured = ArgumentCaptor.forClass(Profile::class.java)
        verify(profileRepository).save(captured.capture() ?: Profile(name = "unused"))
        assertEquals("Colors", captured.value.name)
        assertEquals(false, captured.value.boldAsBright)
    }

    @Test
    fun boldAsBrightLoadsSavedSelection() = runTest {
        `when`(profileRepository.getById(UUID(0, 2))).thenReturn(Profile(id = UUID(0, 2), name = "Colors", boldAsBright = false))
        val viewModel = ProfileEditorViewModel(
            SavedStateHandle(mapOf("profileId" to UUID(0, 2).toString())),
            profileRepository,
            colorSchemeRepository,
            sharedPreferences,
            context,
            dispatchers,
        )
        assertEquals(false, viewModel.uiState.value.boldAsBright)
    }

    @Test
    fun inlineImagesDefaultsToAskAndSavesSelection() = runTest {
        val viewModel = createViewModel()
        assertEquals("ask", viewModel.uiState.value.inlineImages)
        assertEquals("ask", Profile.createDefault().inlineImages)
        viewModel.updateName("Images")
        viewModel.updateInlineImages("on")
        `when`(profileRepository.nameExists("Images", null)).thenReturn(false)
        viewModel.save {}
        val captured = ArgumentCaptor.forClass(Profile::class.java)
        verify(profileRepository).save(captured.capture() ?: Profile(name = "unused"))
        assertEquals("Images", captured.value.name)
        assertEquals("on", captured.value.inlineImages)
    }

    @Test
    fun inlineImagesLoadsSavedSelection() = runTest {
        `when`(profileRepository.getById(UUID(0, 2))).thenReturn(Profile(id = UUID(0, 2), name = "Images", inlineImages = "off"))
        val viewModel = ProfileEditorViewModel(
            SavedStateHandle(mapOf("profileId" to UUID(0, 2).toString())),
            profileRepository,
            colorSchemeRepository,
            sharedPreferences,
            context,
            dispatchers,
        )
        assertEquals("off", viewModel.uiState.value.inlineImages)
    }

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
}
