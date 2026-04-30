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
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
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
    private val listenerCaptor = argumentCaptor<SharedPreferences.OnSharedPreferenceChangeListener>()

    @Before
    fun setUp() = runTest {
        Dispatchers.setMain(testDispatcher)
        migrator = mock()
        prefs = mock()
        prefsEditor = mock()
        notificationPermissionHelper = mock()

        whenever(migrator.isMigrationNeeded()).thenReturn(false)
        whenever(prefs.edit()).thenReturn(prefsEditor)
        whenever(prefsEditor.putBoolean(any(), any())).thenReturn(prefsEditor)
        whenever(prefsEditor.putString(any(), any())).thenReturn(prefsEditor)
        // Default: no theme saved → SYSTEM
        whenever(prefs.getString(eq(PreferenceConstants.THEME_MODE), isNull())).thenReturn("SYSTEM")
        whenever(notificationPermissionHelper.isGranted()).thenReturn(true)

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
        whenever(prefs.getString(eq(PreferenceConstants.THEME_MODE), isNull())).thenReturn("DARK")
        val vm = AppViewModel(migrator, prefs, dispatchers, notificationPermissionHelper)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun themeMode_updatesStateFlow_whenPreferenceChanges() = runTest {
        whenever(prefs.getString(eq(PreferenceConstants.THEME_MODE), isNull())).thenReturn("LIGHT")

        listenerCaptor.firstValue.onSharedPreferenceChanged(prefs, PreferenceConstants.THEME_MODE)

        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }

    @Test
    fun themeMode_doesNotUpdate_whenUnrelatedPreferenceChanges() = runTest {
        listenerCaptor.firstValue.onSharedPreferenceChanged(prefs, "some_other_key")

        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.value)
    }
}
