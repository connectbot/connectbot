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

package org.connectbot.util

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Provider for loading fonts from local storage.
 * Fonts are copied to app's internal storage and cached.
 */
class LocalFontProvider(private val context: Context) {
    private val fontCache = ConcurrentHashMap<String, Typeface>()
    private val fontsDir = File(context.filesDir, FONTS_DIR)

    init {
        if (!fontsDir.exists()) {
            fontsDir.mkdirs()
        }
    }

    /**
     * Import a font from a URI (e.g., from a file picker).
     * Copies the font to app's internal storage.
     *
     * @param uri The URI of the font file
     * @param displayName The display name for the font
     * @return The internal path if successful, null otherwise
     */
    fun importFont(uri: Uri, displayName: String): String? {
        return try {
            val fileName = sanitizeFileName(displayName)
            val destFile = File(fontsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Validate the font can be loaded
            val typeface = Typeface.createFromFile(destFile)
            if (typeface != null) {
                fontCache[fileName] = typeface
                Log.d(TAG, "Imported font: $displayName -> $fileName")
                fileName
            } else {
                destFile.delete()
                Log.w(TAG, "Failed to create typeface from: $displayName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import font: $displayName", e)
            null
        }
    }

    /**
     * Load a font from internal storage by filename.
     */
    fun loadFont(fileName: String): Typeface? {
        // Check cache first
        fontCache[fileName]?.let { return it }

        val fontFile = File(fontsDir, fileName)
        if (!fontFile.exists()) {
            Log.w(TAG, "Font file not found: $fileName")
            return null
        }

        return try {
            val typeface = Typeface.createFromFile(fontFile)
            if (typeface != null) {
                fontCache[fileName] = typeface
            }
            typeface
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load font: $fileName", e)
            null
        }
    }

    /**
     * Get all imported local fonts.
     * Returns a list of (displayName, fileName) pairs.
     */
    fun getImportedFonts(): List<Pair<String, String>> {
        return fontsDir.listFiles()
            ?.filter { it.isFile && isFontFile(it.name) }
            ?.map { getDisplayName(it.name) to it.name }
            ?: emptyList()
    }

    /**
     * Delete a local font.
     */
    fun deleteFont(fileName: String): Boolean {
        fontCache.remove(fileName)
        val fontFile = File(fontsDir, fileName)
        return if (fontFile.exists()) {
            fontFile.delete()
        } else {
            false
        }
    }

    /**
     * Check if a font file exists.
     */
    fun fontExists(fileName: String): Boolean {
        return File(fontsDir, fileName).exists()
    }

    /**
     * Get typeface for a local font, with fallback.
     */
    fun getTypeface(fileName: String, fallback: Typeface = Typeface.MONOSPACE): Typeface {
        return loadFont(fileName) ?: fallback
    }

    private fun sanitizeFileName(name: String): String {
        // Remove path separators and other problematic characters
        var sanitized = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        // Ensure it has a font extension
        if (!isFontFile(sanitized)) {
            sanitized += ".ttf"
        }
        return sanitized
    }

    private fun isFontFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")
    }

    private fun getDisplayName(fileName: String): String {
        // Remove extension and replace underscores with spaces
        return fileName
            .substringBeforeLast(".")
            .replace("_", " ")
    }

    companion object {
        private const val TAG = "LocalFontProvider"
        private const val FONTS_DIR = "fonts"

        /**
         * Prefix used to identify local font names in storage.
         */
        const val LOCAL_PREFIX = "local:"

        /**
         * Check if a stored value represents a local font.
         */
        fun isLocalFont(storedValue: String?): Boolean {
            return storedValue?.startsWith(LOCAL_PREFIX) == true
        }

        /**
         * Get the filename from a local font stored value.
         */
        fun getLocalFontFileName(storedValue: String?): String? {
            if (storedValue == null || !storedValue.startsWith(LOCAL_PREFIX)) return null
            return storedValue.removePrefix(LOCAL_PREFIX)
        }

        /**
         * Create a stored value for a local font.
         */
        fun createLocalFontValue(fileName: String): String {
            return "$LOCAL_PREFIX$fileName"
        }
    }
}
