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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.ProfileRepository
import org.connectbot.data.entity.Profile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mock(ProfileRepository::class.java)
    private val profile = Profile(name = "Profile")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        `when`(repository.observeAll()).thenReturn(MutableStateFlow(listOf(profile)))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun deletionUsesUuidAndClosesConfirmationAfterSuccess() = runTest {
        val model = ProfileListViewModel(repository)
        assertEquals(listOf(profile), model.uiState.value.profiles)
        assertFalse(model.uiState.value.isLoading)
        model.showDeleteDialog(profile)
        `when`(repository.delete(profile.id)).thenReturn(true)
        model.deleteProfile(profile)
        verify(repository).delete(profile.id)
        assertNull(model.uiState.value.showDeleteDialog)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun failedDeletionDismissesConfirmationAndExposesClearableError() = runTest {
        `when`(repository.delete(profile.id)).thenThrow(IllegalStateException("Repository is read-only"))
        val model = ProfileListViewModel(repository)
        model.showDeleteDialog(profile)
        model.deleteProfile(profile)
        assertEquals("Repository is read-only", model.uiState.value.error)
        assertNull(model.uiState.value.showDeleteDialog)
        model.clearError()
        assertNull(model.uiState.value.error)
    }

    @Test
    fun duplicationUsesSourceUuidAndSkipsConflictingCopyNames() = runTest {
        `when`(repository.nameExists("Profile (Copy)")).thenReturn(true)
        `when`(repository.nameExists("Profile (Copy 2)")).thenReturn(true)
        `when`(repository.nameExists("Profile (Copy 3)")).thenReturn(false)
        val model = ProfileListViewModel(repository)
        model.duplicateProfile(profile)
        verify(repository).duplicate(profile.id, "Profile (Copy 3)")
        assertNull(model.uiState.value.error)
    }

    @Test
    fun failedDuplicationReportsErrorWithoutDeletingSource() = runTest {
        `when`(repository.nameExists("Profile (Copy)")).thenReturn(false)
        `when`(repository.duplicate(profile.id, "Profile (Copy)")).thenThrow(IllegalStateException("Repository is read-only"))
        val model = ProfileListViewModel(repository)
        model.duplicateProfile(profile)
        assertEquals("Repository is read-only", model.uiState.value.error)
        verify(repository, never()).delete(profile.id)
    }

    @Test
    fun cancellationIsNotReportedAsARepositoryError() = runTest {
        `when`(repository.delete(profile.id)).thenThrow(CancellationException("Cancelled"))
        val model = ProfileListViewModel(repository)
        model.showDeleteDialog(profile)
        model.deleteProfile(profile)
        assertNull(model.uiState.value.error)
        assertEquals(profile, model.uiState.value.showDeleteDialog)
    }
}
