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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.connectbot.util.HostDatabase

/**
 * Repository for managing terminal color schemes.
 * Handles both built-in preset schemes and user-created custom schemes.
 */
class ColorSchemeRepository(private val context: Context) {

    private val hostDatabase = HostDatabase.get(context)

    /**
     * Get all available color schemes (built-in + custom).
     */
    suspend fun getAllSchemes(): List<ColorScheme> = withContext(Dispatchers.IO) {
        val schemes = mutableListOf<ColorScheme>()

        // Add default scheme (ID 0)
        schemes.add(
            ColorScheme(
                id = ColorScheme.DEFAULT_SCHEME_ID,
                name = "Default",
                isBuiltIn = true,
                description = "Standard terminal colors"
            )
        )

        // Add built-in preset schemes
        // Note: These are virtual - we'll initialize them on-demand
        ColorSchemePresets.builtInSchemes.forEachIndexed { index, preset ->
            schemes.add(
                ColorScheme(
                    id = -(index + 1), // Negative IDs for built-in presets
                    name = preset.name,
                    isBuiltIn = true,
                    description = preset.description
                )
            )
        }

        // Add custom schemes from database
        val cursor = hostDatabase.allColorSchemeMetadata
        try {
            val idIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_NAME)
            val descIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_DESCRIPTION)
            val builtInIndex = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_SCHEME_IS_BUILTIN)

            while (cursor.moveToNext()) {
                val id = cursor.getInt(idIndex)
                // Skip default scheme (already added above)
                if (id == ColorScheme.DEFAULT_SCHEME_ID) continue

                schemes.add(
                    ColorScheme(
                        id = id,
                        name = cursor.getString(nameIndex),
                        isBuiltIn = cursor.getInt(builtInIndex) == 1,
                        description = cursor.getString(descIndex) ?: ""
                    )
                )
            }
        } finally {
            cursor.close()
        }

        schemes
    }

    /**
     * Load a color scheme's palette into the database.
     * For built-in presets, this creates a copy in the database.
     *
     * @param schemeId The scheme ID (negative for built-in presets, 0 for default)
     * @param targetSchemeId The database scheme ID to save to (default 0 for global)
     */
    suspend fun loadScheme(schemeId: Int, targetSchemeId: Int = ColorScheme.DEFAULT_SCHEME_ID) =
        withContext(Dispatchers.IO) {
            if (schemeId == ColorScheme.DEFAULT_SCHEME_ID) {
                // Reset to default color scheme
                resetSchemeToDefaults(targetSchemeId)
            } else if (schemeId < 0) {
                // Built-in preset - load from ColorSchemePresets
                val presetIndex = -(schemeId + 1)
                if (presetIndex < ColorSchemePresets.builtInSchemes.size) {
                    val preset = ColorSchemePresets.builtInSchemes[presetIndex]
                    applyPresetToScheme(preset, targetSchemeId)
                }
            }
            // For positive IDs > 0, colors are already in database
        }

    /**
     * Apply a preset scheme's colors to a database scheme.
     */
    private fun applyPresetToScheme(
        preset: ColorSchemePresets.PresetScheme,
        targetSchemeId: Int
    ) {
        // Set default FG/BG
        hostDatabase.setDefaultColorsForScheme(
            targetSchemeId,
            preset.defaultFg,
            preset.defaultBg
        )

        // Set custom colors (only those different from Colors.defaults)
        preset.colors.forEach { (index, value) ->
            hostDatabase.setColorForScheme(targetSchemeId, index, value)
        }
    }

    /**
     * Get the color palette for a specific scheme.
     *
     * @param schemeId The scheme ID
     * @return Array of 256 ARGB color values
     */
    suspend fun getSchemeColors(schemeId: Int): IntArray = withContext(Dispatchers.IO) {
        if (schemeId < 0) {
            // Built-in preset
            val presetIndex = -(schemeId + 1)
            if (presetIndex < ColorSchemePresets.builtInSchemes.size) {
                ColorSchemePresets.builtInSchemes[presetIndex].getFullPalette()
            } else {
                hostDatabase.getColorsForScheme(ColorScheme.DEFAULT_SCHEME_ID)
            }
        } else {
            // Database scheme
            hostDatabase.getColorsForScheme(schemeId)
        }
    }

    /**
     * Get the default FG/BG indices for a scheme.
     *
     * @return Pair of (foreground index, background index)
     */
    suspend fun getSchemeDefaults(schemeId: Int): Pair<Int, Int> = withContext(Dispatchers.IO) {
        if (schemeId < 0) {
            // Built-in preset
            val presetIndex = -(schemeId + 1)
            if (presetIndex < ColorSchemePresets.builtInSchemes.size) {
                val preset = ColorSchemePresets.builtInSchemes[presetIndex]
                Pair(preset.defaultFg, preset.defaultBg)
            } else {
                val defaults = hostDatabase.getGlobalDefaultColors()
                Pair(defaults[0], defaults[1])
            }
        } else {
            // Database scheme
            val defaults = hostDatabase.getDefaultColorsForScheme(schemeId)
            Pair(defaults[0], defaults[1])
        }
    }

    /**
     * Set a specific color in a scheme's palette.
     *
     * @param schemeId The scheme ID (must be >= 0)
     * @param colorIndex The index in the color palette (0-255)
     * @param colorValue The RGB color value
     */
    suspend fun setColorForScheme(schemeId: Int, colorIndex: Int, colorValue: Int) =
        withContext(Dispatchers.IO) {
            hostDatabase.setColorForScheme(schemeId, colorIndex, colorValue)
        }

    /**
     * Set the default foreground and background color indices for a scheme.
     *
     * @param schemeId The scheme ID (must be >= 0)
     * @param foregroundColorIndex The index for the foreground color (0-255)
     * @param backgroundColorIndex The index for the background color (0-255)
     */
    suspend fun setDefaultColorsForScheme(
        schemeId: Int,
        foregroundColorIndex: Int,
        backgroundColorIndex: Int
    ) = withContext(Dispatchers.IO) {
        hostDatabase.setDefaultColorsForScheme(schemeId, foregroundColorIndex, backgroundColorIndex)
    }

    /**
     * Reset a scheme to standard defaults.
     *
     * @param schemeId The scheme ID to reset (must be >= 0)
     */
    suspend fun resetSchemeToDefaults(schemeId: Int) = withContext(Dispatchers.IO) {
        if (schemeId >= 0) {
            // Clear all custom colors for this scheme
            // This will make it fall back to Colors.defaults
            hostDatabase.clearAllColorsForScheme(schemeId)

            // Reset to default FG/BG
            hostDatabase.setDefaultColorsForScheme(
                schemeId,
                HostDatabase.DEFAULT_FG_COLOR,
                HostDatabase.DEFAULT_BG_COLOR
            )
        }
    }

    /**
     * Create a new custom color scheme.
     *
     * @param name The name for the new scheme
     * @param description Optional description
     * @param basedOnSchemeId The scheme ID to copy colors from (default 0)
     * @return The ID of the newly created scheme
     */
    suspend fun createCustomScheme(
        name: String,
        description: String = "",
        basedOnSchemeId: Int = ColorScheme.DEFAULT_SCHEME_ID
    ): Int = withContext(Dispatchers.IO) {
        // Get the next available custom scheme ID
        val existingSchemes = getAllSchemes()
        val maxCustomId = existingSchemes
            .filter { it.id > 0 }
            .maxOfOrNull { it.id } ?: 0
        val newSchemeId = maxCustomId + 1

        // Save scheme metadata to database
        hostDatabase.createColorScheme(newSchemeId, name, description, false)

        // Copy colors from the base scheme
        val sourcePalette = getSchemeColors(basedOnSchemeId)
        val sourceDefaults = getSchemeDefaults(basedOnSchemeId)

        // Set default FG/BG
        hostDatabase.setDefaultColorsForScheme(
            newSchemeId,
            sourceDefaults.first,
            sourceDefaults.second
        )

        // Copy all custom colors
        sourcePalette.forEachIndexed { index, color ->
            hostDatabase.setColorForScheme(newSchemeId, index, color)
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
    suspend fun duplicateScheme(sourceSchemeId: Int, newName: String): Int =
        withContext(Dispatchers.IO) {
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
        schemeId: Int,
        newName: String,
        newDescription: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (schemeId <= 0) return@withContext false

        val rowsAffected = hostDatabase.updateColorSchemeMetadata(schemeId, newName, newDescription)
        rowsAffected > 0
    }

    /**
     * Delete a custom color scheme.
     * Built-in schemes (ID <= 0) cannot be deleted.
     *
     * @param schemeId The scheme ID to delete (must be > 0)
     */
    suspend fun deleteCustomScheme(schemeId: Int) = withContext(Dispatchers.IO) {
        if (schemeId > 0) {
            // Delete all color data
            hostDatabase.clearAllColorsForScheme(schemeId)
            // Delete metadata
            hostDatabase.deleteColorSchemeMetadata(schemeId)
        }
    }

    /**
     * Check if a scheme name already exists.
     *
     * @param name The name to check
     * @param excludeSchemeId Optional scheme ID to exclude from the check (for renames)
     * @return true if the name exists, false otherwise
     */
    suspend fun schemeNameExists(name: String, excludeSchemeId: Int? = null): Boolean =
        withContext(Dispatchers.IO) {
            // Check against built-in schemes
            val builtInSchemes = listOf("Default") + ColorSchemePresets.builtInSchemes.map { it.name }
            if (builtInSchemes.any { it.equals(name, ignoreCase = true) }) {
                // If checking for a rename and the name matches a built-in scheme,
                // only allow if we're renaming from a negative ID (which shouldn't happen)
                if (excludeSchemeId != null && excludeSchemeId < 0) {
                    return@withContext false
                }
                return@withContext true
            }

            // Check against custom schemes in database
            hostDatabase.colorSchemeNameExists(name, excludeSchemeId)
        }

    /**
     * Export a color scheme to JSON.
     *
     * @param schemeId The scheme ID to export
     * @return ColorSchemeJson object ready for serialization
     */
    suspend fun exportScheme(schemeId: Int): ColorSchemeJson = withContext(Dispatchers.IO) {
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
    suspend fun importScheme(jsonString: String, allowOverwrite: Boolean = false): Int =
        withContext(Dispatchers.IO) {
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

            // Create new scheme
            val newSchemeId = createCustomScheme(
                name = finalName,
                description = schemeJson.description,
                basedOnSchemeId = ColorScheme.DEFAULT_SCHEME_ID
            )

            // Import colors
            val palette = schemeJson.toPalette()
            palette.forEachIndexed { index, color ->
                hostDatabase.setColorForScheme(newSchemeId, index, color)
            }

            newSchemeId
        }

    companion object {
        @Volatile
        private var instance: ColorSchemeRepository? = null

        fun get(context: Context): ColorSchemeRepository {
            return instance ?: synchronized(this) {
                instance ?: ColorSchemeRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
