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

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.connectbot.data.dao.ColorSchemeDao
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.HostConstants
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
    private val dispatchers: CoroutineDispatchers
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
                    id = -(index + 1L), // Start from Default at -1
                    name = preset.name,
                    isBuiltIn = true,
                    description = preset.description
                )
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
     * Get the color palette for a specific scheme.
     *
     * @param schemeId The scheme ID (negative for built-in, positive for custom)
     * @return Array of 16 ARGB color values
     */
    suspend fun getSchemeColors(schemeId: Long): IntArray = withContext(dispatchers.io) {
        return@withContext when {
            // A negative ID signifies a built-in, preset color scheme
            schemeId < 0 -> {
                val presetIndexUnbounded = -(schemeId + 1).toInt()
                val presetIndex = presetIndexUnbounded.coerceIn(0, ColorSchemePresets.builtInSchemes.size - 1)
                ColorSchemePresets.builtInSchemes[presetIndex].colors
            }

            // Non-negative ID represents a scheme stored in the database.
            else -> {
                val colors = ColorSchemePresets.default.colors
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
    suspend fun getSchemeDefaults(schemeId: Long): Pair<Int, Int> = withContext(dispatchers.io) {
        return@withContext when {
            // A negative ID signifies a built-in, preset color scheme
            schemeId < 0 -> {
                val presetIndexUnbounded = -(schemeId + 1).toInt()
                val presetIndex = presetIndexUnbounded.coerceIn(0, ColorSchemePresets.builtInSchemes.size - 1)
                val preset = ColorSchemePresets.builtInSchemes[presetIndex]
                Pair(preset.defaultFg, preset.defaultBg)
            }

            // Non-negative ID represents a scheme stored in the database.
            else -> {
                val scheme = colorSchemeDao.getById(schemeId)
                Pair(
                    scheme?.foreground ?: ColorSchemePresets.default.defaultFg,
                    scheme?.background ?: ColorSchemePresets.default.defaultBg
                )
            }
        }
    }

    /**
     * Set a specific color in a scheme's palette.
     *
     * @param schemeId The scheme ID (must be >= 0)
     * @param colorIndex The index in the color palette (0-15)
     * @param colorValue The RGB color value
     */
    suspend fun setColorForScheme(schemeId: Long, colorIndex: Int, colorValue: Int) = withContext(dispatchers.io) {
        val colorEntry = ColorPalette(
            schemeId = schemeId,
            colorIndex = colorIndex,
            color = colorValue
        )
        colorSchemeDao.insertOrUpdateColor(colorEntry)
    }

    /**
     * Set the default foreground and background color indices for a scheme.
     *
     * @param schemeId The scheme ID (must be >= 0)
     * @param foregroundColorIndex The index for the foreground color (0-15)
     * @param backgroundColorIndex The index for the background color (0-15)
     */
    suspend fun setDefaultColorsForScheme(
        schemeId: Long,
        foregroundColorIndex: Int,
        backgroundColorIndex: Int
    ) = withContext(dispatchers.io) {
        val scheme = colorSchemeDao.getById(schemeId)
        if (scheme != null) {
            colorSchemeDao.update(
                scheme.copy(
                    foreground = foregroundColorIndex,
                    background = backgroundColorIndex
                )
            )
        }
    }

    /**
     * Reset a scheme to standard defaults.
     *
     * @param schemeId The scheme ID to reset (must be >= 0)
     */
    suspend fun resetSchemeToDefaults(schemeId: Long) = withContext(dispatchers.io) {
        if (schemeId >= 0) {
            // Clear all custom colors for this scheme
            // This will make it fall back to default color scheme
            colorSchemeDao.clearColorsForScheme(schemeId)

            // Reset to default FG/BG
            val scheme = colorSchemeDao.getById(schemeId)
            if (scheme != null) {
                colorSchemeDao.update(
                    scheme.copy(
                        foreground = HostConstants.DEFAULT_FG_COLOR,
                        background = HostConstants.DEFAULT_BG_COLOR
                    )
                )
            }
        }
    }

    /**
     * Create a new custom color scheme.
     *
     * @param name The name for the new scheme
     * @param description Optional description
     * @param basedOnSchemeId The scheme ID to copy colors from (default -1 for Default)
     * @return The ID of the newly created scheme
     */
    suspend fun createCustomScheme(
        name: String,
        description: String = "",
        basedOnSchemeId: Long = -1L
    ): Long = withContext(dispatchers.io) {
        // Copy colors from the base scheme
        val sourcePalette = getSchemeColors(basedOnSchemeId)
        val sourceDefaults = getSchemeDefaults(basedOnSchemeId)

        // Create new color scheme in database (ID will be auto-generated)
        val newScheme = ColorScheme(
            id = 0, // Auto-generate (will be assigned by Room)
            name = name,
            description = description,
            isBuiltIn = false,
            foreground = sourceDefaults.first,
            background = sourceDefaults.second
        )
        val newSchemeId = colorSchemeDao.insert(newScheme)

        // Copy all custom colors
        sourcePalette.forEachIndexed { index, color ->
            val colorEntry = ColorPalette(
                schemeId = newSchemeId,
                colorIndex = index,
                color = color
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
    suspend fun duplicateScheme(sourceSchemeId: Long, newName: String): Long = withContext(dispatchers.io) {
        createCustomScheme(
            name = newName,
            description = "",
            basedOnSchemeId = sourceSchemeId
        )
    }

    /**
     * Rename a custom color scheme.
     * Built-in schemes (ID <= 0) cannot be renamed.
     *
     * @param schemeId The scheme ID to rename (must be > 0)
     * @param newName The new name
     * @param newDescription Optional new description
     * @return true if successful, false otherwise
     */
    suspend fun renameScheme(
        schemeId: Long,
        newName: String,
        newDescription: String = ""
    ): Boolean = withContext(dispatchers.io) {
        if (schemeId <= 0) return@withContext false

        val scheme = colorSchemeDao.getById(schemeId) ?: return@withContext false
        colorSchemeDao.update(
            scheme.copy(
                name = newName,
                description = newDescription
            )
        )
        true
    }

    /**
     * Delete a custom color scheme.
     * Built-in schemes (ID <= 0) cannot be deleted.
     *
     * @param schemeId The scheme ID to delete (must be > 0)
     */
    suspend fun deleteCustomScheme(schemeId: Long) = withContext(dispatchers.io) {
        if (schemeId > 0) {
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
    suspend fun schemeNameExists(name: String, excludeSchemeId: Long? = null): Boolean = withContext(dispatchers.io) {
        // Check against built-in schemes
        val builtInSchemes = ColorSchemePresets.builtInSchemes.map { it.name }
        if (builtInSchemes.any { it.equals(name, ignoreCase = true) }) {
            // If checking for a rename and the name matches a built-in scheme,
            // only allow if we're renaming from a negative ID (which shouldn't happen)
            return@withContext !(excludeSchemeId != null && excludeSchemeId < 0)
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
    suspend fun exportScheme(schemeId: Long): ColorSchemeJson = withContext(dispatchers.io) {
        val schemes = getAllSchemes()
        val scheme = schemes.find { it.id == schemeId }
            ?: throw IllegalArgumentException("Scheme not found: $schemeId")

        val palette = getSchemeColors(schemeId)

        ColorSchemeJson.fromPalette(
            name = scheme.name,
            description = scheme.description,
            palette = palette
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
    suspend fun importScheme(jsonString: String, allowOverwrite: Boolean = false): Long = withContext(dispatchers.io) {
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

        // Create new scheme (based on Default -1)
        val newSchemeId = createCustomScheme(
            name = finalName,
            description = schemeJson.description,
            basedOnSchemeId = -1
        )

        // Import colors (note: createCustomScheme already copies from base scheme,
        // so we need to override with the imported colors)
        val palette = schemeJson.toPalette()
        palette.forEachIndexed { index, color ->
            val colorEntry = ColorPalette(
                schemeId = newSchemeId,
                colorIndex = index,
                color = color
            )
            colorSchemeDao.insertOrUpdateColor(colorEntry)
        }

        newSchemeId
    }

    /**
     * Blocking wrapper for getSchemeColors - for use from non-coroutine code.
     * Returns the color palette for a scheme as an IntArray.
     */
    fun getColorsForSchemeBlocking(schemeId: Long): IntArray = runBlocking { getSchemeColors(schemeId) }

    /**
     * Blocking wrapper for getSchemeDefaults - for use from non-coroutine code.
     * Returns [foregroundIndex, backgroundIndex] as an int array.
     */
    fun getDefaultColorsForSchemeBlocking(schemeId: Long): IntArray {
        val (fg, bg) = runBlocking { getSchemeDefaults(schemeId) }
        return intArrayOf(fg, bg)
    }
}
