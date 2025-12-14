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

/**
 * Available terminal fonts that can be downloaded via Google Fonts.
 * All fonts are monospace fonts suitable for terminal display.
 */
enum class TerminalFont(
    val displayName: String,
    val googleFontName: String
) {
    SYSTEM_DEFAULT("Default (Monospace)", ""),
    JETBRAINS_MONO("JetBrains Mono", "JetBrains Mono"),
    FIRA_CODE("Fira Code", "Fira Code"),
    FIRA_MONO("Fira Mono", "Fira Mono"),
    SOURCE_CODE_PRO("Source Code Pro", "Source Code Pro"),
    NOTO_SANS_MONO("Noto Sans Mono", "Noto Sans Mono"),
    ROBOTO_MONO("Roboto Mono", "Roboto Mono"),
    UBUNTU_MONO("Ubuntu Mono", "Ubuntu Mono"),
    INCONSOLATA("Inconsolata", "Inconsolata"),
    SPACE_MONO("Space Mono", "Space Mono"),
    IBM_PLEX_MONO("IBM Plex Mono", "IBM Plex Mono");

    companion object {
        /**
         * Prefix used to identify custom font names in storage.
         * Custom fonts are stored as "custom:FontName" to distinguish from preset enum names.
         */
        const val CUSTOM_PREFIX = "custom:"

        /**
         * Get a TerminalFont by its name (case-insensitive).
         * Returns null if the name represents a custom font (starts with "custom:").
         * Returns SYSTEM_DEFAULT if not found and not a custom font.
         */
        fun fromName(name: String?): TerminalFont? {
            if (name.isNullOrBlank()) return SYSTEM_DEFAULT
            if (name.startsWith(CUSTOM_PREFIX)) return null
            return entries.find {
                it.name.equals(name, ignoreCase = true) ||
                it.displayName.equals(name, ignoreCase = true)
            } ?: SYSTEM_DEFAULT
        }

        /**
         * Check if a font name represents a custom font.
         */
        fun isCustomFont(name: String?): Boolean {
            return name?.startsWith(CUSTOM_PREFIX) == true
        }

        /**
         * Extract the Google Fonts name from a custom font string.
         * Returns null if not a custom font.
         */
        fun getCustomFontName(name: String?): String? {
            if (name == null || !name.startsWith(CUSTOM_PREFIX)) return null
            return name.removePrefix(CUSTOM_PREFIX)
        }

        /**
         * Create a custom font storage string from a Google Fonts name.
         */
        fun createCustomFontValue(googleFontName: String): String {
            return "$CUSTOM_PREFIX$googleFontName"
        }

        /**
         * Get the Google Fonts name for a given stored value.
         * Works for both preset fonts and custom fonts.
         */
        fun getGoogleFontName(storedValue: String?): String {
            if (storedValue.isNullOrBlank()) return ""
            if (storedValue.startsWith(CUSTOM_PREFIX)) {
                return storedValue.removePrefix(CUSTOM_PREFIX)
            }
            return fromName(storedValue)?.googleFontName ?: ""
        }

        /**
         * Get a display name for a stored font value.
         * Works for preset fonts, custom fonts, and local fonts.
         */
        fun getDisplayName(storedValue: String?): String {
            if (storedValue.isNullOrBlank()) return SYSTEM_DEFAULT.displayName
            if (storedValue.startsWith(CUSTOM_PREFIX)) {
                return storedValue.removePrefix(CUSTOM_PREFIX)
            }
            if (storedValue.startsWith(LocalFontProvider.LOCAL_PREFIX)) {
                val fileName = storedValue.removePrefix(LocalFontProvider.LOCAL_PREFIX)
                // Convert filename to display name (remove extension, replace underscores)
                return fileName.substringBeforeLast(".").replace("_", " ")
            }
            return fromName(storedValue)?.displayName ?: SYSTEM_DEFAULT.displayName
        }

        /**
         * Check if a stored value is a preset font (not custom or local).
         */
        fun isPresetFont(storedValue: String?): Boolean {
            if (storedValue.isNullOrBlank()) return true
            if (storedValue.startsWith(CUSTOM_PREFIX)) return false
            if (storedValue.startsWith(LocalFontProvider.LOCAL_PREFIX)) return false
            return entries.any {
                it.name.equals(storedValue, ignoreCase = true) ||
                it.displayName.equals(storedValue, ignoreCase = true)
            }
        }

        /**
         * Check if a stored value is a local font.
         */
        fun isLocalFont(storedValue: String?): Boolean {
            return LocalFontProvider.isLocalFont(storedValue)
        }
    }
}
