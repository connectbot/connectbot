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

package org.connectbot.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.connectbot.data.entity.ColorScheme
import org.connectbot.util.HostConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for ColorSchemeRepository.
 */
@RunWith(AndroidJUnit4::class)
class ColorSchemeRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: ConnectBotDatabase
    private lateinit var repository: ColorSchemeRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ColorSchemeRepository(database.colorSchemeDao())
    }

    @After
    fun tearDown() {
        database.close()
        ColorSchemeRepository.clearInstance()
    }

    @Test
    fun getAllSchemes_Initial_ContainsBuiltInSchemes() = runBlocking {
        val schemes = repository.getAllSchemes()

        // Should have: Default + 7 built-in presets
        assertTrue("Should have at least 8 schemes", schemes.size >= 8)

        // Check Default scheme exists
        val defaultScheme = schemes.find { it.id == -1 }
        assertNotNull("Should have Default scheme", defaultScheme)
        assertEquals("Default", defaultScheme?.name)
        assertTrue("Default should be built-in", defaultScheme?.isBuiltIn == true)

        // Check some preset schemes exist
        val solarizedDark = schemes.find { it.name == "Solarized Dark" }
        assertNotNull("Should have Solarized Dark", solarizedDark)
        assertTrue("Solarized Dark should be built-in", solarizedDark?.isBuiltIn == true)
        assertTrue("Solarized Dark should have negative ID", (solarizedDark?.id ?: 0) < 0)
    }

    @Test
    fun createCustomScheme_ValidData_Success() = runBlocking {
        val schemeId = repository.createCustomScheme(
            name = "My Custom Scheme",
            description = "A test scheme",
            basedOnSchemeId = -1
        )

        assertTrue("Scheme ID should be positive", schemeId > 0)

        val schemes = repository.getAllSchemes()
        val customScheme = schemes.find { it.id == schemeId }

        assertNotNull("Custom scheme should exist", customScheme)
        assertEquals("My Custom Scheme", customScheme?.name)
        assertEquals("A test scheme", customScheme?.description)
        assertFalse("Custom scheme should not be built-in", customScheme?.isBuiltIn == true)
    }

    @Test
    fun createCustomScheme_MultipleSchemes_IncrementingIds() = runBlocking {
        val id1 = repository.createCustomScheme("Scheme 1", "", -1)
        val id2 = repository.createCustomScheme("Scheme 2", "", -1)
        val id3 = repository.createCustomScheme("Scheme 3", "", -1)

        assertEquals("IDs should increment", 1, id1)
        assertEquals("IDs should increment", 2, id2)
        assertEquals("IDs should increment", 3, id3)
    }

    @Test
    fun duplicateScheme_FromDefault_Success() = runBlocking {
        val newId = repository.duplicateScheme(-1, "Copy of Default")

        assertTrue("New ID should be positive", newId > 0)

        val schemes = repository.getAllSchemes()
        val duplicated = schemes.find { it.id == newId }

        assertNotNull("Duplicated scheme should exist", duplicated)
        assertEquals("Copy of Default", duplicated?.name)
    }

    @Test
    fun duplicateScheme_FromPreset_CopiesColors() = runBlocking {
        // Duplicate Solarized Dark (ID -2 since Default is -1)
        val newId = repository.duplicateScheme(-2, "My Solarized")

        val schemes = repository.getAllSchemes()
        val duplicated = schemes.find { it.id == newId }

        assertNotNull("Duplicated scheme should exist", duplicated)
        assertEquals("My Solarized", duplicated?.name)
        assertFalse("Duplicated scheme should not be built-in", duplicated?.isBuiltIn == true)

        // Verify colors were copied
        val palette = repository.getSchemeColors(newId)
        assertEquals("Should have 256 colors", 256, palette.size)
    }

    @Test
    fun renameScheme_CustomScheme_Success() = runBlocking {
        val schemeId = repository.createCustomScheme("Original Name", "Original desc", -1)

        val success = repository.renameScheme(schemeId, "New Name", "New description")

        assertTrue("Rename should succeed", success)

        val schemes = repository.getAllSchemes()
        val renamed = schemes.find { it.id == schemeId }

        assertEquals("New Name", renamed?.name)
        assertEquals("New description", renamed?.description)
    }

    @Test
    fun renameScheme_BuiltInScheme_Fails() = runBlocking {
        val success = repository.renameScheme(-2, "Cannot Rename", "")

        assertFalse("Should not rename built-in scheme", success)
    }

    @Test
    fun deleteCustomScheme_Exists_Success() = runBlocking {
        val schemeId = repository.createCustomScheme("To Delete", "", -1)

        repository.deleteCustomScheme(schemeId)

        val schemes = repository.getAllSchemes()
        val deleted = schemes.find { it.id == schemeId }

        assertNull("Scheme should be deleted", deleted)
    }

    @Test
    fun deleteCustomScheme_BuiltInScheme_NoOp() = runBlocking {
        val schemesBefore = repository.getAllSchemes()
        val builtInCount = schemesBefore.count { it.isBuiltIn }

        repository.deleteCustomScheme(-2) // Try to delete built-in (Solarized Dark)

        val schemesAfter = repository.getAllSchemes()
        val builtInCountAfter = schemesAfter.count { it.isBuiltIn }

        assertEquals("Built-in schemes should not be deleted", builtInCount, builtInCountAfter)
    }

    @Test
    fun schemeNameExists_ExistingBuiltIn_ReturnsTrue() = runBlocking {
        val exists = repository.schemeNameExists("Default")

        assertTrue("Should find Default scheme", exists)
    }

    @Test
    fun schemeNameExists_ExistingPreset_ReturnsTrue() = runBlocking {
        val exists = repository.schemeNameExists("Solarized Dark")

        assertTrue("Should find Solarized Dark preset", exists)
    }

    @Test
    fun schemeNameExists_NonExistent_ReturnsFalse() = runBlocking {
        val exists = repository.schemeNameExists("Does Not Exist")

        assertFalse("Should not find non-existent scheme", exists)
    }

    @Test
    fun schemeNameExists_CustomScheme_ReturnsTrue() = runBlocking {
        repository.createCustomScheme("Custom Scheme", "", -1)

        val exists = repository.schemeNameExists("Custom Scheme")

        assertTrue("Should find custom scheme", exists)
    }

    @Test
    fun schemeNameExists_CaseInsensitive_ReturnsTrue() = runBlocking {
        repository.createCustomScheme("Test Scheme", "", -1)

        val exists = repository.schemeNameExists("test scheme")

        assertTrue("Should be case-insensitive", exists)
    }

    @Test
    fun schemeNameExists_WithExclusion_IgnoresExcluded() = runBlocking {
        val schemeId = repository.createCustomScheme("Test Scheme", "", -1)

        val exists = repository.schemeNameExists("Test Scheme", excludeSchemeId = schemeId)

        assertFalse("Should exclude the specified scheme", exists)
    }

    @Test
    fun getSchemeColors_Default_Returns256Colors() = runBlocking {
        val palette = repository.getSchemeColors(-1)

        assertEquals("Should have 256 colors", 256, palette.size)
    }

    @Test
    fun getSchemeColors_Preset_Returns256Colors() = runBlocking {
        val palette = repository.getSchemeColors(-2) // Solarized Dark (ID -2)

        assertEquals("Should have 256 colors", 256, palette.size)
    }

    @Test
    fun getSchemeDefaults_Default_ReturnsCorrectValues() = runBlocking {
        val (fg, bg) = repository.getSchemeDefaults(-1)

        assertEquals("Default FG should be 7", HostConstants.DEFAULT_FG_COLOR, fg)
        assertEquals("Default BG should be 0", HostConstants.DEFAULT_BG_COLOR, bg)
    }

    @Test
    fun getSchemeDefaults_SolarizedDark_ReturnsCorrectValues() = runBlocking {
        val (fg, bg) = repository.getSchemeDefaults(-2) // Solarized Dark (ID -2 since Default is -1)

        assertEquals("Solarized Dark FG should be 12", 12, fg)
        assertEquals("Solarized Dark BG should be 8", 8, bg)
    }

    @Test
    fun resetSchemeToDefaults_Success() = runBlocking {
        // Create a custom scheme based on Solarized Dark
        val customId = repository.createCustomScheme("Test Scheme", "", basedOnSchemeId = -2)

        // Verify it has Solarized colors
        var (fg, bg) = repository.getSchemeDefaults(customId)
        assertEquals("FG should be Solarized Dark FG", 12, fg)
        assertEquals("BG should be Solarized Dark BG", 8, bg)

        // Reset to defaults
        repository.resetSchemeToDefaults(customId)

        // Verify it now has default colors
        val (fgAfter, bgAfter) = repository.getSchemeDefaults(customId)
        assertEquals("FG should be reset to default", HostConstants.DEFAULT_FG_COLOR, fgAfter)
        assertEquals("BG should be reset to default", HostConstants.DEFAULT_BG_COLOR, bgAfter)
    }
}
