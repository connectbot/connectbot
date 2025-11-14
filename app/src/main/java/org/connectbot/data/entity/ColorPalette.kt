/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root, Jeffrey Sharkey
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
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Terminal color palette override entity.
 * Stores custom color values for specific palette indices within a color scheme.
 */
@Entity(
    tableName = "color_palette",
    foreignKeys = [
        ForeignKey(
            entity = ColorScheme::class,
            parentColumns = ["id"],
            childColumns = ["scheme_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("scheme_id"),
        Index(value = ["scheme_id", "color_index"], unique = true)
    ]
)
data class ColorPalette(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "scheme_id")
    val schemeId: Long,

    @ColumnInfo(name = "color_index")
    val colorIndex: Int,

    val color: Int
)
