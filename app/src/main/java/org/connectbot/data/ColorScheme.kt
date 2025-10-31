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

/**
 * Represents a terminal color scheme with metadata.
 *
 * @property id Database ID of the color scheme (0 = global default)
 * @property name Display name of the scheme
 * @property isBuiltIn Whether this is a built-in preset scheme (cannot be deleted/renamed)
 * @property description User-friendly description of the color scheme
 */
data class ColorScheme(
    val id: Int,
    val name: String,
    val isBuiltIn: Boolean,
    val description: String = ""
) {
    companion object {
        /**
         * The global default color scheme ID.
         * This scheme is always present and cannot be deleted.
         */
        const val DEFAULT_SCHEME_ID = 0
    }
}
