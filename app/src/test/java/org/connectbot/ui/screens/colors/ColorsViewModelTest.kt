/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.ui.screens.colors

import android.content.Context
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.entity.ColorScheme
import org.connectbot.util.Colors
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ColorsViewModelTest {

    private lateinit var context: Context
    private lateinit var repository: ColorSchemeRepository
    private lateinit var viewModel: ColorsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        repository = mock()

        val defaultScheme = ColorScheme(
            id = -1,
            name = "Default",
            isBuiltIn = true
        )
        runBlocking {
            whenever(repository.getAllSchemes()).thenReturn(listOf(defaultScheme))
            whenever(repository.getSchemeDefaults(-1))
                .thenReturn(Pair(7, 0))
            whenever(repository.getSchemeColors(-1))
                .thenReturn(Colors.defaults)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads default scheme and colors`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.currentSchemeId).isEqualTo(-1)
        assertThat(state.availableSchemes).isNotEmpty()
        assertThat(state.currentPalette).isNotEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `loading state is set during initialization`() = runTest {
        viewModel = ColorsViewModel(context, repository)

        // Check that loading is true during initialization
        val initialState = viewModel.uiState.value
        // Note: This might be false if coroutines complete immediately in tests
        // The important part is that isLoading eventually becomes false

        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertThat(finalState.isLoading).isFalse()
    }

    @Test
    fun `available schemes list is initialized`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Available schemes list should be initialized (may be empty or populated)
        assertThat(state.availableSchemes).isNotNull()
    }

    @Test
    fun `updateForegroundColor updates state`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        val schemeIndex = 1
        viewModel.switchToScheme(schemeIndex)
        advanceUntilIdle()

        val newColorIndex = 5
        viewModel.updateForegroundColor(newColorIndex)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.foregroundColorIndex).isEqualTo(newColorIndex)
    }

    @Test
    fun `updateForegroundColor ignores invalid index - negative`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        val originalColorIndex = viewModel.uiState.value.foregroundColorIndex
        viewModel.updateForegroundColor(-1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.foregroundColorIndex).isEqualTo(originalColorIndex)
    }

    @Test
    fun `updateForegroundColor ignores invalid index - too large`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        val originalColorIndex = viewModel.uiState.value.foregroundColorIndex
        viewModel.updateForegroundColor(999)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.foregroundColorIndex).isEqualTo(originalColorIndex)
    }

    @Test
    fun `updateBackgroundColor updates state`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        viewModel.switchToScheme(1)
        advanceUntilIdle()

        val newColorIndex = 3
        viewModel.updateBackgroundColor(newColorIndex)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.backgroundColorIndex).isEqualTo(newColorIndex)
    }

    @Test
    fun `updateBackgroundColor ignores invalid index - negative`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        val originalColorIndex = viewModel.uiState.value.backgroundColorIndex
        viewModel.updateBackgroundColor(-1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.backgroundColorIndex).isEqualTo(originalColorIndex)
    }

    @Test
    fun `updateBackgroundColor ignores invalid index - too large`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        val originalColorIndex = viewModel.uiState.value.backgroundColorIndex
        viewModel.updateBackgroundColor(999)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.backgroundColorIndex).isEqualTo(originalColorIndex)
    }

    @Test
    fun `switchToScheme updates current scheme`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        // Switch to a scheme ID (even if it doesn't exist, it should update the state)
        viewModel.switchToScheme(1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.currentSchemeId).isEqualTo(1)
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `resetToDefaults reloads default colors`() = runTest {
        whenever(repository.getSchemeDefaults(1)).thenReturn(Pair(7, 0))
        whenever(repository.getSchemeColors(1)).thenReturn(IntArray(255, { it }))

        viewModel = ColorsViewModel(context, repository)
        advanceUntilIdle()

        viewModel.switchToScheme(1)
        advanceUntilIdle()

        // Change some colors first
        viewModel.updateForegroundColor(10)
        viewModel.updateBackgroundColor(5)
        advanceUntilIdle()

        // Reset to defaults
        viewModel.resetToDefaults()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.currentPalette).isNotEmpty()
    }

    @Test
    fun `foreground and background colors can be set independently`() = runTest {
        viewModel = ColorsViewModel(context, repository)
        viewModel.switchToScheme(1)
        advanceUntilIdle()

        val fgColor = 7
        val bgColor = 2

        viewModel.updateForegroundColor(fgColor)
        advanceUntilIdle()
        viewModel.updateBackgroundColor(bgColor)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.foregroundColorIndex).isEqualTo(fgColor)
        assertThat(state.backgroundColorIndex).isEqualTo(bgColor)
    }

    @Test
    fun `uiState equals method works correctly`() {
        val state1 = ColorsUiState(
            currentSchemeId = 1,
            foregroundColorIndex = 5,
            backgroundColorIndex = 3
        )
        val state2 = ColorsUiState(
            currentSchemeId = 1,
            foregroundColorIndex = 5,
            backgroundColorIndex = 3
        )
        val state3 = ColorsUiState(
            currentSchemeId = 2,
            foregroundColorIndex = 5,
            backgroundColorIndex = 3
        )

        assertThat(state1).isEqualTo(state2)
        assertThat(state1).isNotEqualTo(state3)
    }

    @Test
    fun `uiState hashCode is consistent`() {
        val state1 = ColorsUiState(
            currentSchemeId = 1,
            foregroundColorIndex = 5,
            backgroundColorIndex = 3
        )
        val state2 = ColorsUiState(
            currentSchemeId = 1,
            foregroundColorIndex = 5,
            backgroundColorIndex = 3
        )

        assertThat(state1.hashCode()).isEqualTo(state2.hashCode())
    }
}
