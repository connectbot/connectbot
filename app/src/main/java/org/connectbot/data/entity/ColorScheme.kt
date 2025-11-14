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

package org.connectbot.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a terminal color scheme with metadata.
 *
 * @property id Database ID of the color scheme (0 = global default)
 * @property name Display name of the scheme
 * @property isBuiltIn Whether this is a built-in preset scheme (cannot be deleted/renamed)
 * @property description User-friendly description of the color scheme
 */
@Entity(
    tableName = "color_schemes",
    indices = [Index(value = ["name"], unique = true)]
)
data class ColorScheme(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean,
    val description: String = "",
    val foreground: Int = 7,
    val background: Int = 0,
)
