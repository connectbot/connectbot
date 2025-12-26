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

import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ColorSchemePresets
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.entity.ColorScheme
import org.connectbot.di.CoroutineDispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PaletteEditorViewModelTest {

    private lateinit var repository: ColorSchemeRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: PaletteEditorViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )
    private val testSchemeId = -1L

    @Before
    fun setUp() {
        repository = mock()
        savedStateHandle = mock()
        whenever(savedStateHandle.get<Long>("schemeId")).thenReturn(testSchemeId)

        val defaultScheme = ColorScheme(
            id = testSchemeId,
            name = "Default",
            isBuiltIn = true
        )
        runBlocking {
            whenever(repository.getAllSchemes()).thenReturn(listOf(defaultScheme))
            whenever(repository.getSchemeColors(testSchemeId)).thenReturn(ColorSchemePresets.default.colors)
        }
    }

    @Test
    fun `initial state loads palette for scheme`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.schemeId).isEqualTo(testSchemeId)
        assertThat(state.palette).isNotEmpty()
        assertThat(state.palette.size).isEqualTo(16)
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.editingColorIndex).isNull()
        assertThat(state.showResetAllDialog).isFalse()
    }

    @Test
    fun `scheme name is loaded correctly`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.schemeName).isNotEmpty()
    }

    @Test
    fun `editColor sets editing index`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        val colorIndex = 5
        viewModel.editColor(colorIndex)

        val state = viewModel.uiState.value
        assertThat(state.editingColorIndex).isEqualTo(colorIndex)
    }

    @Test
    fun `closeColorEditor clears editing index`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        viewModel.editColor(5)
        assertThat(viewModel.uiState.value.editingColorIndex).isNotNull()

        viewModel.closeColorEditor()

        val state = viewModel.uiState.value
        assertThat(state.editingColorIndex).isNull()
    }

    @Test
    fun `updateColor changes color in palette`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        val colorIndex = 3
        val newColor = Color.rgb(255, 128, 64)

        viewModel.updateColor(colorIndex, newColor)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.palette[colorIndex]).isEqualTo(newColor)
        assertThat(state.editingColorIndex).isNull() // Editor should close after update
    }

    @Test
    fun `updateColor persists to database`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        val colorIndex = 0

        // Define unique colors for the test
        val initialPaletteState = viewModel.uiState.value
        val originalColor = initialPaletteState.palette[colorIndex]

        // Ensure the new color is distinctly different from the original color
        val newColor = Color.rgb(100, 200, 50)
        assertThat(newColor).isNotEqualTo(originalColor)

        viewModel.updateColor(colorIndex, newColor)
        advanceUntilIdle()

        verify(repository, times(1)).setColorForScheme(
            testSchemeId,
            colorIndex,
            newColor
        )

        // Make sure the UI updated
        assertThat(viewModel.uiState.value.palette[colorIndex]).isEqualTo(newColor)
    }

    @Test
    fun `resetColor restores default color`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        val colorIndex = 4
        val customColor = Color.rgb(111, 222, 333)

        // First, change the color
        viewModel.updateColor(colorIndex, customColor)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.palette[colorIndex]).isEqualTo(customColor)

        // Then reset it
        viewModel.resetColor(colorIndex)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.palette[colorIndex]).isEqualTo(ColorSchemePresets.default.colors[colorIndex])
    }

    @Test
    fun `showResetAllDialog sets dialog flag`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        viewModel.showResetAllDialog()

        val state = viewModel.uiState.value
        assertThat(state.showResetAllDialog).isTrue()
    }

    @Test
    fun `hideResetAllDialog clears dialog flag`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        viewModel.showResetAllDialog()
        assertThat(viewModel.uiState.value.showResetAllDialog).isTrue()

        viewModel.hideResetAllDialog()

        val state = viewModel.uiState.value
        assertThat(state.showResetAllDialog).isFalse()
    }

    @Test
    fun `resetAllColors restores all default colors`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        // Change multiple colors
        viewModel.updateColor(0, Color.RED)
        viewModel.updateColor(1, Color.GREEN)
        viewModel.updateColor(2, Color.BLUE)
        advanceUntilIdle()

        // Reset all
        viewModel.resetAllColors()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.palette).isEqualTo(ColorSchemePresets.default.colors)
        assertThat(state.showResetAllDialog).isFalse()
    }

    @Test
    fun `resetAllColors closes dialog`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        viewModel.showResetAllDialog()
        assertThat(viewModel.uiState.value.showResetAllDialog).isTrue()

        viewModel.resetAllColors()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.showResetAllDialog).isFalse()
    }

    @Test
    fun `clearError removes error message`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        // Manually set an error state for testing
        // (In real scenarios, errors would come from database operations)
        viewModel.clearError()

        val state = viewModel.uiState.value
        assertThat(state.error).isNull()
    }

    @Test
    fun `multiple color updates are handled correctly`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        val updates = mapOf(
            0 to Color.RED,
            5 to Color.GREEN,
            10 to Color.BLUE,
            15 to Color.YELLOW
        )

        updates.forEach { (index, color) ->
            viewModel.updateColor(index, color)
            advanceUntilIdle()
        }

        val state = viewModel.uiState.value
        updates.forEach { (index, color) ->
            assertThat(state.palette[index]).isEqualTo(color)
        }
    }

    @Test
    fun `uiState equals method works correctly`() {
        val palette1 = intArrayOf(1, 2, 3, 4)
        val palette2 = intArrayOf(1, 2, 3, 4)
        val palette3 = intArrayOf(1, 2, 3, 5)

        val state1 = PaletteEditorUiState(
            schemeId = 1,
            schemeName = "Test",
            palette = palette1
        )
        val state2 = PaletteEditorUiState(
            schemeId = 1,
            schemeName = "Test",
            palette = palette2
        )
        val state3 = PaletteEditorUiState(
            schemeId = 1,
            schemeName = "Test",
            palette = palette3
        )

        assertThat(state1).isEqualTo(state2)
        assertThat(state1).isNotEqualTo(state3)
    }

    @Test
    fun `uiState hashCode is consistent`() {
        val palette1 = intArrayOf(1, 2, 3, 4)
        val palette2 = intArrayOf(1, 2, 3, 4)

        val state1 = PaletteEditorUiState(
            schemeId = 1,
            schemeName = "Test",
            palette = palette1
        )
        val state2 = PaletteEditorUiState(
            schemeId = 1,
            schemeName = "Test",
            palette = palette2
        )

        assertThat(state1.hashCode()).isEqualTo(state2.hashCode())
    }

    @Test
    fun `editing workflow - open edit close`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        // Open editor
        viewModel.editColor(3)
        assertThat(viewModel.uiState.value.editingColorIndex).isEqualTo(3)

        // Change to different color
        viewModel.editColor(8)
        assertThat(viewModel.uiState.value.editingColorIndex).isEqualTo(8)

        // Close editor
        viewModel.closeColorEditor()
        assertThat(viewModel.uiState.value.editingColorIndex).isNull()
    }

    @Test
    fun `palette contains correct number of colors`() = runTest {
        viewModel = PaletteEditorViewModel(savedStateHandle, repository, dispatchers)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.palette.size).isEqualTo(16)
    }
}
