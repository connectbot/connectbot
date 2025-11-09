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

package org.connectbot.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorSchemeDaoTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var colorSchemeDao: ColorSchemeDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        colorSchemeDao = database.colorSchemeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveColorScheme() = runTest {
        val scheme = createTestScheme(name = "Monokai")

        val id = colorSchemeDao.insert(scheme)
        assertThat(id).isGreaterThan(0)

        val retrieved = colorSchemeDao.getById(id.toInt())
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("Monokai")
        assertThat(retrieved?.isBuiltIn).isFalse()
    }

    @Test
    fun observeAllColorSchemes() = runTest {
        val scheme1 = createTestScheme(name = "Solarized Dark")
        val scheme2 = createTestScheme(name = "Monokai")
        val scheme3 = createTestScheme(name = "Dracula")

        colorSchemeDao.insert(scheme1)
        colorSchemeDao.insert(scheme2)
        colorSchemeDao.insert(scheme3)

        val schemes = colorSchemeDao.observeAll().first()
        assertThat(schemes).hasSize(3)
        // Should be sorted by name
        assertThat(schemes.map { it.name }).containsExactly("Dracula", "Monokai", "Solarized Dark")
    }

    @Test
    fun observeById() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val id = colorSchemeDao.insert(scheme)

        val observed = colorSchemeDao.observeById(id.toInt()).first()
        assertThat(observed).isNotNull()
        assertThat(observed?.name).isEqualTo("Test Scheme")
    }

    @Test
    fun getAllColorSchemes() = runTest {
        val scheme1 = createTestScheme(name = "Scheme A")
        val scheme2 = createTestScheme(name = "Scheme B")

        colorSchemeDao.insert(scheme1)
        colorSchemeDao.insert(scheme2)

        val schemes = colorSchemeDao.getAll()
        assertThat(schemes).hasSize(2)
        assertThat(schemes.map { it.name }).containsExactly("Scheme A", "Scheme B")
    }

    @Test
    fun updateColorScheme() = runTest {
        val scheme = createTestScheme(name = "Original Name")
        val id = colorSchemeDao.insert(scheme)

        val retrieved = colorSchemeDao.getById(id.toInt())!!
        val updated = retrieved.copy(name = "Updated Name", description = "New description")
        colorSchemeDao.update(updated)

        val afterUpdate = colorSchemeDao.getById(id.toInt())
        assertThat(afterUpdate?.name).isEqualTo("Updated Name")
        assertThat(afterUpdate?.description).isEqualTo("New description")
    }

    @Test
    fun insertOrUpdateColorScheme() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val id = colorSchemeDao.insert(scheme)

        val retrieved = colorSchemeDao.getById(id.toInt())!!
        val updated = retrieved.copy(name = "Updated Scheme")
        val updateId = colorSchemeDao.insertOrUpdate(updated)

        assertThat(updateId).isEqualTo(id)
        val afterUpdate = colorSchemeDao.getById(id.toInt())
        assertThat(afterUpdate?.name).isEqualTo("Updated Scheme")
    }

    @Test
    fun deleteColorScheme() = runTest {
        val scheme = createTestScheme(name = "To Delete")
        val id = colorSchemeDao.insert(scheme)

        val beforeDelete = colorSchemeDao.getById(id.toInt())
        assertThat(beforeDelete).isNotNull()

        colorSchemeDao.delete(beforeDelete!!)

        val afterDelete = colorSchemeDao.getById(id.toInt())
        assertThat(afterDelete).isNull()
    }

    @Test
    fun insertAndRetrieveColorPalette() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        val color = ColorPalette(
            schemeId = schemeId,
            colorIndex = 0,
            color = 0x000000
        )

        val colorId = colorSchemeDao.insertColor(color)
        assertThat(colorId).isGreaterThan(0)

        val retrieved = colorSchemeDao.getColor(schemeId.toInt(), 0)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.color).isEqualTo(0x000000)
    }

    @Test
    fun getColorsForScheme() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        // Insert multiple colors for the palette
        for (i in 0..15) {
            val color = ColorPalette(
                schemeId = schemeId,
                colorIndex = i,
                color = 0x000000 + (i * 0x111111)
            )
            colorSchemeDao.insertColor(color)
        }

        val colors = colorSchemeDao.getColors(schemeId)
        assertThat(colors).hasSize(16)
        assertThat(colors.map { it.colorIndex }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
    }

    @Test
    fun observeColorsForScheme() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        val color1 = ColorPalette(schemeId = schemeId, colorIndex = 0, color = 0xFF0000)
        val color2 = ColorPalette(schemeId = schemeId, colorIndex = 1, color = 0x00FF00)

        colorSchemeDao.insertColor(color1)
        colorSchemeDao.insertColor(color2)

        val colors = colorSchemeDao.observeColors(schemeId).first()
        assertThat(colors).hasSize(2)
        assertThat(colors[0].color).isEqualTo(0xFF0000)
        assertThat(colors[1].color).isEqualTo(0x00FF00)
    }

    @Test
    fun updateColorPalette() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        val color = ColorPalette(schemeId = schemeId, colorIndex = 0, color = 0xFF0000)
        colorSchemeDao.insertColor(color)

        val retrieved = colorSchemeDao.getColor(schemeId.toInt(), 0)!!
        val updated = retrieved.copy(color = 0x00FF00)
        colorSchemeDao.updateColor(updated)

        val afterUpdate = colorSchemeDao.getColor(schemeId.toInt(), 0)
        assertThat(afterUpdate?.color).isEqualTo(0x00FF00)
    }

    @Test
    fun insertOrUpdateColorPalette() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        val color = ColorPalette(schemeId = schemeId, colorIndex = 0, color = 0xFF0000)
        val colorId = colorSchemeDao.insertColor(color)

        val retrieved = colorSchemeDao.getColor(schemeId.toInt(), 0)!!
        val updated = retrieved.copy(color = 0x00FF00)
        val updateId = colorSchemeDao.insertOrUpdateColor(updated)

        assertThat(updateId).isEqualTo(colorId)
        val afterUpdate = colorSchemeDao.getColor(schemeId.toInt(), 0)
        assertThat(afterUpdate?.color).isEqualTo(0x00FF00)
    }

    @Test
    fun deleteColor() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        val color = ColorPalette(schemeId = schemeId, colorIndex = 0, color = 0xFF0000)
        colorSchemeDao.insertColor(color)

        val beforeDelete = colorSchemeDao.getColor(schemeId.toInt(), 0)
        assertThat(beforeDelete).isNotNull()

        colorSchemeDao.deleteColor(schemeId, 0)

        val afterDelete = colorSchemeDao.getColor(schemeId.toInt(), 0)
        assertThat(afterDelete).isNull()
    }

    @Test
    fun deleteAllColors() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        // Insert multiple colors
        for (i in 0..7) {
            val color = ColorPalette(schemeId = schemeId, colorIndex = i, color = 0x000000)
            colorSchemeDao.insertColor(color)
        }

        val beforeDelete = colorSchemeDao.getColors(schemeId)
        assertThat(beforeDelete).hasSize(8)

        colorSchemeDao.deleteAllColors(schemeId)

        val afterDelete = colorSchemeDao.getColors(schemeId)
        assertThat(afterDelete).isEmpty()
    }

    @Test
    fun clearColorsForScheme() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        // Insert multiple colors
        for (i in 0..3) {
            val color = ColorPalette(schemeId = schemeId, colorIndex = i, color = 0x000000)
            colorSchemeDao.insertColor(color)
        }

        val beforeClear = colorSchemeDao.getColors(schemeId)
        assertThat(beforeClear).hasSize(4)

        colorSchemeDao.clearColorsForScheme(schemeId)

        val afterClear = colorSchemeDao.getColors(schemeId)
        assertThat(afterClear).isEmpty()
    }

    @Test
    fun nameExists_ReturnsTrueWhenNameExists() = runTest {
        val scheme = createTestScheme(name = "Monokai")
        colorSchemeDao.insert(scheme)

        val exists = colorSchemeDao.nameExists("Monokai")
        assertThat(exists).isTrue()
    }

    @Test
    fun nameExists_ReturnsFalseWhenNameDoesNotExist() = runTest {
        val exists = colorSchemeDao.nameExists("NonExistent")
        assertThat(exists).isFalse()
    }

    @Test
    fun nameExists_IsCaseInsensitive() = runTest {
        val scheme = createTestScheme(name = "Monokai")
        colorSchemeDao.insert(scheme)

        assertThat(colorSchemeDao.nameExists("monokai")).isTrue()
        assertThat(colorSchemeDao.nameExists("MONOKAI")).isTrue()
        assertThat(colorSchemeDao.nameExists("MoNoKaI")).isTrue()
    }

    @Test
    fun nameExists_ExcludesSchemeId() = runTest {
        val scheme = createTestScheme(name = "Monokai")
        val id = colorSchemeDao.insert(scheme)

        // Should return false when excluding the only scheme with that name
        val exists = colorSchemeDao.nameExists("Monokai", excludeSchemeId = id.toInt())
        assertThat(exists).isFalse()
    }

    @Test
    fun nameExists_ExcludesOnlySpecifiedId() = runTest {
        val scheme1 = createTestScheme(name = "Monokai")
        val scheme2 = createTestScheme(name = "Monokai-Copy")
        val id1 = colorSchemeDao.insert(scheme1)
        val id2 = colorSchemeDao.insert(scheme2)

        // Should return false because we're checking a different name
        val existsForMonokai = colorSchemeDao.nameExists("Monokai", excludeSchemeId = id1.toInt())
        assertThat(existsForMonokai).isFalse()

        // Should return true because Monokai-Copy exists and we're excluding a different scheme
        val existsForCopy = colorSchemeDao.nameExists("Monokai-Copy", excludeSchemeId = id1.toInt())
        assertThat(existsForCopy).isTrue()
    }

    @Test
    fun deleteColorScheme_CascadesToColorPalette() = runTest {
        val scheme = createTestScheme(name = "Test Scheme")
        val schemeId = colorSchemeDao.insert(scheme)

        // Insert colors for the scheme
        for (i in 0..3) {
            val color = ColorPalette(schemeId = schemeId, colorIndex = i, color = 0x000000)
            colorSchemeDao.insertColor(color)
        }

        val colorsBeforeDelete = colorSchemeDao.getColors(schemeId)
        assertThat(colorsBeforeDelete).hasSize(4)

        // Delete the scheme
        val schemeToDelete = colorSchemeDao.getById(schemeId.toInt())!!
        colorSchemeDao.delete(schemeToDelete)

        // Colors should also be deleted due to CASCADE
        val colorsAfterDelete = colorSchemeDao.getColors(schemeId)
        assertThat(colorsAfterDelete).isEmpty()
    }

    private fun createTestScheme(
        name: String,
        isBuiltIn: Boolean = false,
        description: String = "",
        foreground: Int = 7,
        background: Int = 0
    ) = ColorScheme(
        name = name,
        isBuiltIn = isBuiltIn,
        description = description,
        foreground = foreground,
        background = background
    )
}
