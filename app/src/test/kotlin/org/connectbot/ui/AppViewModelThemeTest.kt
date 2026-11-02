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

package org.connectbot.ui

import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.connectbot.data.migration.DatabaseMigrator
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.NotificationPermissionHelper
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelThemeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher,
    )
    private lateinit var migrator: DatabaseMigrator
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor
    private lateinit var notificationPermissionHelper: NotificationPermissionHelper
    private lateinit var viewModel: AppViewModel
    private val listenerCaptor = ArgumentCaptor.forClass(SharedPreferences.OnSharedPreferenceChangeListener::class.java)

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        migrator = mock(DatabaseMigrator::class.java)
        prefs = mock(SharedPreferences::class.java)
        prefsEditor = mock(SharedPreferences.Editor::class.java)
        notificationPermissionHelper = mock(NotificationPermissionHelper::class.java)

        `when`(migrator.isMigrationNeeded()).thenReturn(false)
        `when`(prefs.edit()).thenReturn(prefsEditor)
        `when`(prefsEditor.putBoolean(anyString(), anyBoolean())).thenReturn(prefsEditor)
        `when`(prefsEditor.putString(anyString(), anyString())).thenReturn(prefsEditor)
        // Default: no theme saved → SYSTEM
        `when`(prefs.getString(eq(PreferenceConstants.THEME_MODE), isNull())).thenReturn("SYSTEM")
        `when`(notificationPermissionHelper.isGranted()).thenReturn(true)

        viewModel = AppViewModel(migrator, prefs, dispatchers, notificationPermissionHelper)
        advanceUntilIdle()

        verify(prefs).registerOnSharedPreferenceChangeListener(listenerCaptor.capture())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun themeMode_defaultsToSystem_whenNoPreferenceSaved() {
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }

    @Test
    fun themeMode_initializesFromPreference_whenDarkSaved() = runTest {
        `when`(prefs.getString(eq(PreferenceConstants.THEME_MODE), isNull())).thenReturn("DARK")
        val vm = AppViewModel(migrator, prefs, dispatchers, notificationPermissionHelper)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun themeMode_updatesStateFlow_whenPreferenceChanges() = runTest {
        `when`(prefs.getString(eq(PreferenceConstants.THEME_MODE), isNull())).thenReturn("LIGHT")

        listenerCaptor.value.onSharedPreferenceChanged(prefs, PreferenceConstants.THEME_MODE)

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }

    @Test
    fun themeMode_doesNotUpdate_whenUnrelatedPreferenceChanges() = runTest {
        listenerCaptor.value.onSharedPreferenceChanged(prefs, "some_other_key")

        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }
}
