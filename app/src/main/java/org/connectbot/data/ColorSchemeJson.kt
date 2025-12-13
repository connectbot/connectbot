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

import org.json.JSONObject

/**
 * Data class representing a color scheme in JSON format for import/export.
 *
 * @property name The name of the color scheme
 * @property description Optional description of the color scheme
 * @property version Schema version for future compatibility (currently 1)
 * @property colors Map of color index (0-15) to hex color string
 */
data class ColorSchemeJson(
    val name: String,
    val description: String = "",
    val version: Int = 1,
    val colors: Map<Int, String>
) {
    companion object {
        const val CURRENT_VERSION = 1

        /**
         * Parse a color scheme from JSON string.
         *
         * @param jsonString The JSON string to parse
         * @return ColorSchemeJson object
         * @throws org.json.JSONException if JSON is invalid
         * @throws IllegalArgumentException if schema is invalid
         */
        fun fromJson(jsonString: String): ColorSchemeJson {
            val json = JSONObject(jsonString)

            // Parse metadata
            val name = json.getString("name")
            val description = json.optString("description", "")
            val version = json.optInt("version", 1)

            // Parse colors
            val colorsJson = json.getJSONObject("colors")
            val colors = mutableMapOf<Int, String>()

            colorsJson.keys().forEach { key ->
                val index = key.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid color index: $key")

                if (index !in 0..15) {
                    throw IllegalArgumentException("Color index out of range: $index")
                }

                val hexColor = colorsJson.getString(key)
                if (!isValidHexColor(hexColor)) {
                    throw IllegalArgumentException("Invalid hex color at index $index: $hexColor")
                }

                colors[index] = hexColor
            }

            return ColorSchemeJson(name, description, version, colors)
        }

        /**
         * Create a ColorSchemeJson from a palette and metadata.
         *
         * @param name Scheme name
         * @param description Scheme description
         * @param palette Array of 16 ARGB color integers
         * @return ColorSchemeJson object
         */
        fun fromPalette(name: String, description: String, palette: IntArray): ColorSchemeJson {
            require(palette.size == 16) { "Palette must contain exactly 16 colors" }

            val colors = palette.mapIndexed { index, color ->
                index to String.format("#%06X", color and 0xFFFFFF)
            }.toMap()

            return ColorSchemeJson(name, description, CURRENT_VERSION, colors)
        }

        /**
         * Validate if a string is a valid hex color (with or without #).
         */
        private fun isValidHexColor(hex: String): Boolean {
            val cleaned = hex.removePrefix("#")
            return cleaned.length in listOf(3, 6) &&
                   cleaned.all { it in "0123456789ABCDEFabcdef" }
        }
    }

    /**
     * Convert this color scheme to JSON string.
     *
     * @param pretty If true, format JSON with indentation
     * @return JSON string
     */
    fun toJson(pretty: Boolean = true): String {
        val json = JSONObject()
        json.put("name", name)
        json.put("description", description)
        json.put("version", version)

        val colorsJson = JSONObject()
        colors.forEach { (index, hex) ->
            colorsJson.put(index.toString(), hex)
        }
        json.put("colors", colorsJson)

        return if (pretty) {
            json.toString(2) // Indent with 2 spaces
        } else {
            json.toString()
        }
    }

    /**
     * Convert JSON colors map to IntArray palette.
     *
     * @return IntArray of 16 ARGB colors
     */
    fun toPalette(): IntArray {
        val palette = IntArray(16)

        colors.forEach { (index, hex) ->
            palette[index] = parseHexColor(hex)
        }

        return palette
    }

    /**
     * Parse hex color string to ARGB integer.
     * Supports 3-digit (abc -> aabbcc) and 6-digit formats.
     */
    private fun parseHexColor(hex: String): Int {
        val cleaned = hex.removePrefix("#").uppercase()

        val rgb = when (cleaned.length) {
            3 -> {
                // Expand abc to aabbcc
                val r = cleaned[0].toString().repeat(2)
                val g = cleaned[1].toString().repeat(2)
                val b = cleaned[2].toString().repeat(2)
                (r + g + b).toInt(16)
            }
            6 -> cleaned.toInt(16)
            else -> throw IllegalArgumentException("Invalid hex color: $hex")
        }

        // Add alpha channel
        return (0xFF shl 24) or rgb
    }
}
