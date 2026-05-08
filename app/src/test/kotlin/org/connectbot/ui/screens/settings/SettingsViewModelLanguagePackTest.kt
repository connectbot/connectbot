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
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ProfileRepository
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.di.FakeLanguagePackManager
import org.connectbot.util.LanguageDownloadState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelLanguagePackTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var profileRepository: ProfileRepository
    private lateinit var languagePackManager: FakeLanguagePackManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() = runTest {
        prefs = mock()
        prefsEditor = mock()
        profileRepository = mock()
        languagePackManager = FakeLanguagePackManager()

        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putString(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putFloat(any(), any())).thenReturn(prefsEditor)
        whenever(prefs.getBoolean(any(), any())).thenReturn(false)
        whenever(prefs.getString(any(), any())).thenReturn("")
        whenever(prefs.getFloat(any(), any())).thenReturn(0.25f)
        whenever(profileRepository.getAll()).thenReturn(emptyList<Profile>())

        viewModel = SettingsViewModel(
            prefs,
            profileRepository,
            RuntimeEnvironment.getApplication(),
            dispatchers,
            languagePackManager,
        )
        advanceUntilIdle()
    }

    @Test
    fun languageDownloadStates_isEmptyInitially() = runTest {
        assertThat(viewModel.uiState.value.languageDownloadStates).isEmpty()
    }

    @Test
    fun installedLanguages_reflectsManagerOnInit() = runTest(testDispatcher) {
        languagePackManager.fakeInstalledLanguages.addAll(setOf("de", "fr"))
        val vm = SettingsViewModel(
            prefs,
            profileRepository,
            RuntimeEnvironment.getApplication(),
            dispatchers,
            languagePackManager,
        )
        advanceUntilIdle()
        assertThat(vm.uiState.value.installedLanguages).containsAll(setOf("de", "fr"))
    }

    @Test
    fun requestLanguage_whenInstalled_selectsImmediately() = runTest {
        languagePackManager.fakeInstalledLanguages.add("de")
        viewModel.requestLanguage("de")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.language).isEqualTo("de")
        assertThat(viewModel.uiState.value.languageDownloadStates["de"]).isNull()
    }

    @Test
    fun requestLanguage_emptyTag_selectsImmediately() = runTest {
        viewModel.requestLanguage("")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.language).isEqualTo("")
        assertThat(viewModel.uiState.value.languageDownloadStates).isEmpty()
    }

    @Test
    fun requestLanguage_notInstalled_onSuccess_selectsAndClearsDownloadState() = runTest {
        languagePackManager.nextRequestResult = true
        viewModel.requestLanguage("fr")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.language).isEqualTo("fr")
        assertThat(viewModel.uiState.value.languageDownloadStates["fr"]).isNull()
    }

    @Test
    fun requestLanguage_notInstalled_onFailure_setsFailed() = runTest {
        languagePackManager.nextRequestResult = false
        viewModel.requestLanguage("ja")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.languageDownloadStates["ja"])
            .isEqualTo(LanguageDownloadState.Failed)
        assertThat(viewModel.uiState.value.language).isEqualTo("")
    }

    @Test
    fun requestLanguage_onSuccess_updatesInstalledLanguages() = runTest(testDispatcher) {
        languagePackManager.nextRequestResult = true
        viewModel.requestLanguage("ko")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.installedLanguages).contains("ko")
    }

    @Test
    fun requestLanguage_onSuccess_installedLanguagesUpdatedBeforeRefresh() = runTest(testDispatcher) {
        // Simulates session-ID-0 case: Play confirms installed but won't appear in
        // splitInstallManager.installedLanguages (bundled language, not a split).
        // The tag must be in installedLanguages immediately after success, not only
        // after refreshInstalledLanguages() completes.
        languagePackManager.nextRequestResult = true
        // Don't add to fakeInstalledLanguages so refreshInstalledLanguages won't find it
        languagePackManager.skipAddOnSuccess = true
        viewModel.requestLanguage("zh")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.installedLanguages).contains("zh")
        assertThat(viewModel.uiState.value.languageDownloadStates["zh"]).isNull()
    }

    @Test
    fun requestLanguage_whileDownloading_stateIsDownloading() = runTest(testDispatcher) {
        languagePackManager.deferCallback = true
        viewModel.requestLanguage("es")
        advanceUntilIdle()

        // Dialog should remain open: language not selected and state is Downloading
        assertThat(viewModel.uiState.value.language).isEqualTo("")
        assertThat(viewModel.uiState.value.languageDownloadStates["es"])
            .isEqualTo(LanguageDownloadState.Downloading)
    }

    @Test
    fun requestLanguage_whileDownloading_secondRequest_isIgnored() = runTest(testDispatcher) {
        languagePackManager.deferCallback = true
        viewModel.requestLanguage("es")
        advanceUntilIdle()
        viewModel.requestLanguage("es")
        advanceUntilIdle()

        assertThat(languagePackManager.requestedLanguage).isEqualTo("es")
        assertThat(viewModel.uiState.value.languageDownloadStates["es"])
            .isEqualTo(LanguageDownloadState.Downloading)
    }

    @Test
    fun requestLanguage_onFailure_stateIsFailedAndLanguageUnchanged() = runTest(testDispatcher) {
        languagePackManager.deferCallback = true
        languagePackManager.nextRequestResult = false
        viewModel.requestLanguage("pt")
        advanceUntilIdle()
        languagePackManager.completePendingRequest()
        advanceUntilIdle()

        // Dialog should remain open: language not selected and state is Failed
        assertThat(viewModel.uiState.value.language).isEqualTo("")
        assertThat(viewModel.uiState.value.languageDownloadStates["pt"])
            .isEqualTo(LanguageDownloadState.Failed)
    }
}
