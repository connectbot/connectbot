/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import java.util.UUID

/**
 * Data Access Object for terminal color schemes and palettes.
 */
@Dao
interface ColorSchemeDao {
    /**
     * Observe all color schemes, ordered by name.
     */
    @Query("SELECT * FROM color_schemes ORDER BY name ASC")
    fun observeAll(): Flow<List<ColorScheme>>

    /**
     * Observe a single color scheme by ID.
     */
    @Query("SELECT * FROM color_schemes WHERE id = :schemeId")
    fun observeById(schemeId: UUID): Flow<ColorScheme?>

    /**
     * Get a single color scheme by ID (one-time query).
     */
    @Query("SELECT * FROM color_schemes WHERE id = :schemeId")
    suspend fun getById(schemeId: UUID): ColorScheme?

    /**
     * Get all color schemes (one-time query).
     */
    @Query("SELECT * FROM color_schemes ORDER BY name ASC")
    suspend fun getAll(): List<ColorScheme>

    /**
     * Insert a new color scheme.
     * @return The ID of the newly inserted scheme
     */
    @Insert
    suspend fun insertRow(colorScheme: ColorScheme)

    suspend fun insert(colorScheme: ColorScheme): UUID {
        insertRow(colorScheme)
        return colorScheme.id
    }

    /**
     * Update an existing color scheme.
     */
    @Update
    suspend fun update(colorScheme: ColorScheme)

    /**
     * Insert or update a color scheme.
     * @return The ID of the newly inserted scheme entry
     */
    @Upsert
    suspend fun insertOrUpdateRow(color: ColorScheme)

    suspend fun insertOrUpdate(color: ColorScheme): UUID {
        insertOrUpdateRow(color)
        return color.id
    }

    /**
     * Delete a color scheme.
     * Also cascades to delete associated color palette entries.
     */
    @Delete
    suspend fun delete(colorScheme: ColorScheme)

    // Color palette operations

    /**
     * Get all color palette entries for a scheme.
     */
    @Query("SELECT * FROM color_palette WHERE scheme_id = :schemeId ORDER BY color_index ASC")
    suspend fun getColors(schemeId: UUID): List<ColorPalette>

    /**
     * Observe color palette entries for a scheme.
     */
    @Query("SELECT * FROM color_palette WHERE scheme_id = :schemeId ORDER BY color_index ASC")
    fun observeColors(schemeId: UUID): Flow<List<ColorPalette>>

    /**
     * Get a specific color from the palette.
     */
    @Query("SELECT * FROM color_palette WHERE scheme_id = :schemeId AND color_index = :colorIndex")
    suspend fun getColor(schemeId: UUID, colorIndex: Int): ColorPalette?

    /**
     * Insert a color palette entry.
     * @return The ID of the newly inserted palette entry
     */
    @Insert
    suspend fun insertColorRow(colorPalette: ColorPalette)

    suspend fun insertColor(colorPalette: ColorPalette): UUID {
        insertColorRow(colorPalette)
        return colorPalette.id
    }

    /**
     * Update a color palette entry.
     */
    @Update
    suspend fun updateColor(colorPalette: ColorPalette)

    /**
     * Insert or update a color palette entry.
     * @return The ID of the newly inserted palette entry
     */
    @Upsert
    suspend fun insertOrUpdateColorRow(color: ColorPalette)

    @Transaction
    suspend fun insertOrUpdateColor(color: ColorPalette): UUID {
        val existing = getColor(color.schemeId, color.colorIndex)
        val saved = if (existing == null) color else color.copy(id = existing.id, repositoryId = existing.repositoryId)
        insertOrUpdateColorRow(saved)
        return saved.id
    }

    /**
     * Delete a specific color from the palette.
     */
    @Query("DELETE FROM color_palette WHERE scheme_id = :schemeId AND color_index = :colorIndex")
    suspend fun deleteColor(schemeId: UUID, colorIndex: Int)

    /**
     * Delete all colors for a scheme.
     */
    @Query("DELETE FROM color_palette WHERE scheme_id = :schemeId")
    suspend fun deleteAllColors(schemeId: UUID)

    /**
     * Clear all colors for a scheme (alias for deleteAllColors).
     */
    suspend fun clearColorsForScheme(schemeId: UUID) = deleteAllColors(schemeId)

    /**
     * Check if a scheme name already exists (case-insensitive).
     *
     * @param name The name to check
     * @param excludeSchemeId Optional scheme ID to exclude from the check (for renames)
     * @return true if the name exists, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM color_schemes WHERE repository_id = COALESCE((SELECT repository_id FROM color_schemes WHERE id = :excludeSchemeId), 'b6ba8cb6-02d4-492d-8de9-5747abbf3b21') AND LOWER(name) = LOWER(:name) AND (:excludeSchemeId IS NULL OR id != :excludeSchemeId))")
    suspend fun nameExists(name: String, excludeSchemeId: UUID? = null): Boolean
}
