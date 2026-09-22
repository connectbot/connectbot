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

package org.connectbot.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.connectbot.data.EntityIds
import org.connectbot.data.dao.ColorSchemeDao
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.HostConstants
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing terminal color schemes.
 * Handles both built-in preset schemes and user-created custom schemes.
 *
 * @param colorSchemeDao The DAO for accessing color scheme data
 */
@Singleton
class ColorSchemeRepository @Inject constructor(
    private val colorSchemeDao: ColorSchemeDao,
    private val dispatchers: CoroutineDispatchers,
) {

    /**
     * Get all available color schemes (built-in + custom).
     */
    suspend fun getAllSchemes(): List<ColorScheme> = withContext(dispatchers.io) {
        val schemes = mutableListOf<ColorScheme>()

        // Add all built-in preset schemes (including Default)
        // Note: These are virtual - they exist only in code, not in the database
        ColorSchemePresets.builtInSchemes.forEachIndexed { index, preset ->
            schemes.add(
                ColorScheme(
                    id = EntityIds.preset(index), // Stable UUID for this built-in preset
                    name = preset.name,
                    isBuiltIn = true,
                    description = preset.description,
                ),
            )
        }

        // Add custom schemes from database
        val userSchemes = colorSchemeDao.getAll()
        for (userScheme in userSchemes) {
            schemes.add(userScheme)
        }

        schemes
    }

    /**
     * Observe all available color schemes (built-in + custom).
     */
    fun observeAllSchemes(): Flow<List<ColorScheme>> = colorSchemeDao.observeAll().map { userSchemes ->
        val schemes = mutableListOf<ColorScheme>()

        // Add all built-in preset schemes (including Default)
        ColorSchemePresets.builtInSchemes.forEachIndexed { index, preset ->
            schemes.add(
                ColorScheme(
                    id = EntityIds.preset(index), // Stable UUID for this built-in preset
                    name = preset.name,
                    isBuiltIn = true,
                    description = preset.description,
                ),
            )
        }

        // Add custom schemes from database
        schemes.addAll(userSchemes)
        schemes
    }

    /**
     * Observe a specific color scheme's metadata.
     *
     * @param schemeId The scheme ID
     * @return Flow of ColorScheme (or null if not found)
     */
    fun observeScheme(schemeId: UUID): Flow<ColorScheme?> = if (EntityIds.isPreset(schemeId)) {
        val presetIndex = EntityIds.presetIndex(schemeId)
        if (presetIndex < 0 || presetIndex >= ColorSchemePresets.builtInSchemes.size) {
            flowOf(null)
        } else {
            val preset = ColorSchemePresets.builtInSchemes[presetIndex]
            flowOf(
                ColorScheme(
                    id = schemeId,
                    name = preset.name,
                    isBuiltIn = true,
                    description = preset.description,
                ),
            )
        }
    } else {
        colorSchemeDao.observeById(schemeId)
    }

    /**
     * Observe the color palette for a specific scheme.
     *
     * @param schemeId The scheme ID
     * @return Flow of IntArray of 16 ARGB colors
     */
    fun observeSchemeColors(schemeId: UUID): Flow<IntArray> = if (EntityIds.isPreset(schemeId)) {
        val presetIndex = EntityIds.presetIndex(schemeId)
        val safeIndex = presetIndex.coerceIn(0, ColorSchemePresets.builtInSchemes.size - 1)
        flowOf(ColorSchemePresets.builtInSchemes[safeIndex].colors.clone())
    } else {
        colorSchemeDao.observeColors(schemeId).map { paletteEntries ->
            val colors = ColorSchemePresets.default.colors.clone()
            paletteEntries.forEach { entry ->
                if (entry.colorIndex in 0..15) {
                    colors[entry.colorIndex] = entry.color
                }
            }
            colors
        }
    }

    /**
     * Observe the default FG/BG indices for a scheme.
     *
     * @param schemeId The scheme ID
     * @return Flow of Pair of (foreground index, background index)
     */
    fun observeSchemeDefaults(schemeId: UUID): Flow<Pair<Int, Int>> = if (EntityIds.isPreset(schemeId)) {
        val presetIndex = EntityIds.presetIndex(schemeId).coerceIn(0, ColorSchemePresets.builtInSchemes.size - 1)
        val preset = ColorSchemePresets.builtInSchemes[presetIndex]
        flowOf(Pair(preset.defaultFg, preset.defaultBg))
    } else {
        colorSchemeDao.observeById(schemeId).map { scheme ->
            Pair(
                scheme?.foreground ?: ColorSchemePresets.default.defaultFg,
                scheme?.background ?: ColorSchemePresets.default.defaultBg,
            )
        }
    }

    /**
     * Get the color palette for a specific scheme.
     *
     * @param schemeId The UUID of a built-in preset or custom scheme
     * @return Array of 16 ARGB color values
     */
    suspend fun getSchemeColors(schemeId: UUID): IntArray = withContext(dispatchers.io) {
        return@withContext when {
            // Preset UUIDs identify virtual built-in color schemes
            EntityIds.isPreset(schemeId) -> {
                val presetIndexUnbounded = EntityIds.presetIndex(schemeId)
                val presetIndex = presetIndexUnbounded.coerceIn(0, ColorSchemePresets.builtInSchemes.size - 1)
                ColorSchemePresets.builtInSchemes[presetIndex].colors.clone()
            }

            // Custom scheme UUIDs identify rows stored in the database.
            else -> {
                val colors = ColorSchemePresets.default.colors.clone()
                colorSchemeDao.getColors(schemeId).map { colors[it.colorIndex] = it.color }
                colors
            }
        }
    }

    /**
     * Get the default FG/BG indices for a scheme.
     *
     * @return Pair of (foreground index, background index)
     */
    suspend fun getSchemeDefaults(schemeId: UUID): Pair<Int, Int> = withContext(dispatchers.io) {
        return@withContext when {
            // Preset UUIDs identify virtual built-in color schemes
            EntityIds.isPreset(schemeId) -> {
                val presetIndexUnbounded = EntityIds.presetIndex(schemeId)
                val presetIndex = presetIndexUnbounded.coerceIn(0, ColorSchemePresets.builtInSchemes.size - 1)
                val preset = ColorSchemePresets.builtInSchemes[presetIndex]
                Pair(preset.defaultFg, preset.defaultBg)
            }

            // Custom scheme UUIDs identify rows stored in the database.
            else -> {
                val scheme = colorSchemeDao.getById(schemeId)
                Pair(
                    scheme?.foreground ?: ColorSchemePresets.default.defaultFg,
                    scheme?.background ?: ColorSchemePresets.default.defaultBg,
                )
            }
        }
    }

    /**
     * Set a specific color in a scheme's palette.
     *
     * @param schemeId The UUID of a custom scheme stored in the database
     * @param colorIndex The index in the color palette (0-15)
     * @param colorValue The RGB color value
     */
    suspend fun setColorForScheme(schemeId: UUID, colorIndex: Int, colorValue: Int) = withContext(dispatchers.io) {
        val colorEntry = ColorPalette(
            schemeId = schemeId,
            repositoryId = colorSchemeDao.getById(schemeId)?.repositoryId ?: EntityIds.LOCAL,
            colorIndex = colorIndex,
            color = colorValue,
        )
        colorSchemeDao.insertOrUpdateColor(colorEntry)
    }

    /**
     * Set the default foreground and background color indices for a scheme.
     *
     * @param schemeId The UUID of a custom scheme stored in the database
     * @param foregroundColorIndex The index for the foreground color (0-15)
     * @param backgroundColorIndex The index for the background color (0-15)
     */
    suspend fun setDefaultColorsForScheme(
        schemeId: UUID,
        foregroundColorIndex: Int,
        backgroundColorIndex: Int,
    ) = withContext(dispatchers.io) {
        val scheme = colorSchemeDao.getById(schemeId)
        if (scheme != null) {
            colorSchemeDao.update(
                scheme.copy(
                    foreground = foregroundColorIndex,
                    background = backgroundColorIndex,
                ),
            )
        }
    }

    /**
     * Reset a scheme to standard defaults.
     *
     * @param schemeId The UUID of the custom scheme to reset
     */
    suspend fun resetSchemeToDefaults(schemeId: UUID) = withContext(dispatchers.io) {
        if (!EntityIds.isPreset(schemeId)) {
            // Clear all custom colors for this scheme
            // This will make it fall back to default color scheme
            colorSchemeDao.clearColorsForScheme(schemeId)

            // Reset to default FG/BG
            val scheme = colorSchemeDao.getById(schemeId)
            if (scheme != null) {
                colorSchemeDao.update(
                    scheme.copy(
                        foreground = HostConstants.DEFAULT_FG_COLOR,
                        background = HostConstants.DEFAULT_BG_COLOR,
                    ),
                )
            }
        }
    }

    /**
     * Create a new custom color scheme.
     *
     * @param name The name for the new scheme
     * @param description Optional description
     * @param basedOnSchemeId The scheme UUID to copy colors from (defaults to the Default preset)
     * @return The ID of the newly created scheme
     */
    suspend fun createCustomScheme(
        name: String,
        description: String = "",
        basedOnSchemeId: UUID = EntityIds.DEFAULT_SCHEME,
    ): UUID = withContext(dispatchers.io) {
        // Copy colors from the base scheme
        val sourcePalette = getSchemeColors(basedOnSchemeId)
        val sourceDefaults = getSchemeDefaults(basedOnSchemeId)

        // Create a custom color scheme with a new UUID before insertion
        val newScheme = ColorScheme(
            id = UUID.randomUUID(),
            name = name,
            description = description,
            isBuiltIn = false,
            foreground = sourceDefaults.first,
            background = sourceDefaults.second,
        )
        val newSchemeId = colorSchemeDao.insert(newScheme)

        // Copy all custom colors
        sourcePalette.forEachIndexed { index, color ->
            val colorEntry = ColorPalette(
                schemeId = newSchemeId,
                colorIndex = index,
                color = color,
            )
            colorSchemeDao.insertOrUpdateColor(colorEntry)
        }

        newSchemeId
    }

    /**
     * Duplicate an existing scheme.
     *
     * @param sourceSchemeId The scheme to duplicate
     * @param newName The name for the duplicated scheme
     * @return The ID of the newly created scheme
     */
    suspend fun duplicateScheme(sourceSchemeId: UUID, newName: String): UUID = withContext(dispatchers.io) {
        createCustomScheme(
            name = newName,
            description = "",
            basedOnSchemeId = sourceSchemeId,
        )
    }

    /**
     * Rename a custom color scheme.
     * Built-in preset UUIDs cannot be renamed.
     *
     * @param schemeId The UUID of the custom scheme to rename
     * @param newName The new name
     * @param newDescription Optional new description
     * @return true if successful, false otherwise
     */
    suspend fun renameScheme(
        schemeId: UUID,
        newName: String,
        newDescription: String = "",
    ): Boolean = withContext(dispatchers.io) {
        if (EntityIds.isPreset(schemeId)) return@withContext false

        val scheme = colorSchemeDao.getById(schemeId) ?: return@withContext false
        colorSchemeDao.update(
            scheme.copy(
                name = newName,
                description = newDescription,
            ),
        )
        true
    }

    /**
     * Delete a custom color scheme.
     * Built-in preset UUIDs cannot be deleted.
     *
     * @param schemeId The UUID of the custom scheme to delete
     */
    suspend fun deleteCustomScheme(schemeId: UUID) = withContext(dispatchers.io) {
        if (!EntityIds.isPreset(schemeId)) {
            // Delete all color data (CASCADE will handle this, but we'll do it explicitly)
            colorSchemeDao.clearColorsForScheme(schemeId)

            // Delete the scheme metadata
            val scheme = colorSchemeDao.getById(schemeId)
            if (scheme != null) {
                colorSchemeDao.delete(scheme)
            }
        }
    }

    /**
     * Check if a scheme name already exists.
     *
     * @param name The name to check
     * @param excludeSchemeId Optional scheme ID to exclude from the check (for renames)
     * @return true if the name exists, false otherwise
     */
    suspend fun schemeNameExists(name: String, excludeSchemeId: UUID? = null): Boolean = withContext(dispatchers.io) {
        // Check against built-in schemes
        val builtInSchemes = ColorSchemePresets.builtInSchemes.map { it.name }
        if (builtInSchemes.any { it.equals(name, ignoreCase = true) }) {
            // If checking for a rename and the name matches a built-in scheme,
            // exclude that preset's own UUID from the duplicate-name check
            return@withContext !(excludeSchemeId != null && EntityIds.isPreset(excludeSchemeId))
        }

        // Check against custom schemes in database
        colorSchemeDao.nameExists(name, excludeSchemeId)
    }

    /**
     * Export a color scheme to JSON.
     *
     * @param schemeId The scheme ID to export
     * @return ColorSchemeJson object ready for serialization
     */
    suspend fun exportScheme(schemeId: UUID): ColorSchemeJson = withContext(dispatchers.io) {
        val schemes = getAllSchemes()
        val scheme = schemes.find { it.id == schemeId }
            ?: throw IllegalArgumentException("Scheme not found: $schemeId")

        val palette = getSchemeColors(schemeId)

        ColorSchemeJson.fromPalette(
            name = scheme.name,
            description = scheme.description,
            palette = palette,
        )
    }

    /**
     * Import a color scheme from JSON.
     *
     * @param jsonString The JSON string to import
     * @param allowOverwrite If false and name exists, will auto-rename
     * @return The ID of the imported scheme
     * @throws org.json.JSONException if JSON is invalid
     * @throws IllegalArgumentException if schema is invalid
     */
    suspend fun importScheme(jsonString: String, allowOverwrite: Boolean = false): UUID = withContext(dispatchers.io) {
        // Parse JSON
        val schemeJson = ColorSchemeJson.fromJson(jsonString)

        // Check for name conflicts
        var finalName = schemeJson.name
        if (schemeNameExists(finalName)) {
            if (!allowOverwrite) {
                // Auto-rename by appending number
                var counter = 1
                while (schemeNameExists("$finalName ($counter)")) {
                    counter++
                }
                finalName = "$finalName ($counter)"
            }
        }

        // Create a new scheme based on the Default preset
        val newSchemeId = createCustomScheme(
            name = finalName,
            description = schemeJson.description,
            basedOnSchemeId = EntityIds.DEFAULT_SCHEME,
        )

        // Import colors (note: createCustomScheme already copies from base scheme,
        // so we need to override with the imported colors)
        val palette = schemeJson.toPalette()
        palette.forEachIndexed { index, color ->
            val colorEntry = ColorPalette(
                schemeId = newSchemeId,
                colorIndex = index,
                color = color,
            )
            colorSchemeDao.insertOrUpdateColor(colorEntry)
        }

        newSchemeId
    }

    /**
     * Blocking wrapper for getSchemeColors - for use from non-coroutine code.
     * Returns the color palette for a scheme as an IntArray.
     */
    fun getColorsForSchemeBlocking(schemeId: UUID): IntArray = runBlocking { getSchemeColors(schemeId) }

    /**
     * Blocking wrapper for getSchemeDefaults - for use from non-coroutine code.
     * Returns [foregroundIndex, backgroundIndex] as an int array.
     */
    fun getDefaultColorsForSchemeBlocking(schemeId: UUID): IntArray {
        val (fg, bg) = runBlocking { getSchemeDefaults(schemeId) }
        return intArrayOf(fg, bg)
    }
}
