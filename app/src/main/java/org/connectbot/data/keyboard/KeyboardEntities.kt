/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package org.connectbot.data.keyboard

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "keyboard_layouts")
data class KeyboardLayout(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    @ColumnInfo(name = "button_width", defaultValue = "45") val buttonWidth: Float = 45f,
    @ColumnInfo(name = "button_height", defaultValue = "30") val buttonHeight: Float = 30f,
    @ColumnInfo(name = "fit_to_width", defaultValue = "0") val fitToWidth: Boolean = false,
    @ColumnInfo(name = "preset_key") val presetKey: String? = null,
)

@Entity(tableName = "keyboard_macros")
data class KeyboardMacro(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val document: String,
)

@Entity(
    tableName = "keyboard_items",
    foreignKeys = [
        ForeignKey(entity = KeyboardLayout::class, parentColumns = ["id"], childColumns = ["layout_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = KeyboardMacro::class, parentColumns = ["id"], childColumns = ["macro_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("layout_id"), Index("macro_id")],
)
data class KeyboardItem(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    @ColumnInfo(name = "layout_id") val layoutId: UUID,
    val row: Int = 0,
    val column: Int = 0,
    @ColumnInfo(name = "row_span") val rowSpan: Int = 1,
    @ColumnInfo(name = "column_span") val columnSpan: Int = 1,
    val visible: Boolean = true,
    /** Stable contract: key, modifier, app, macro, or layer. */
    val kind: String = "key",
    val target: String = "escape",
    @ColumnInfo(defaultValue = "0") val layer: Int = 0,
    /** Null appearance values follow the theme; zero outline width removes the border. */
    @ColumnInfo(name = "background_color") val backgroundColor: Int? = null,
    @ColumnInfo(name = "background_opacity") val backgroundOpacity: Float? = null,
    @ColumnInfo(name = "text_color") val textColor: Int? = null,
    @ColumnInfo(name = "outline_color") val outlineColor: Int? = null,
    @ColumnInfo(name = "outline_width") val outlineWidth: Float? = null,
    /** Pinned and scrolling regions each have their own row and column coordinates. */
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    /** Null keeps the default: arrows repeat; other buttons fire once. */
    @ColumnInfo(name = "repeat_on_hold") val repeatOnHold: Boolean? = null,
    /** Null follows the app-wide keyboard haptics preference. */
    @ColumnInfo(name = "haptic_feedback") val hapticFeedback: Boolean? = null,
    @ColumnInfo(name = "macro_id") val macroId: UUID? = null,
)

@Entity(
    tableName = "keyboard_settings",
    foreignKeys = [ForeignKey(entity = KeyboardLayout::class, parentColumns = ["id"], childColumns = ["default_layout_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("default_layout_id")],
)
data class KeyboardSettings(
    @PrimaryKey val id: UUID = KeyboardDefaults.settingsId,
    @ColumnInfo(name = "default_layout_id") val defaultLayoutId: UUID? = null,
)

data class KeyboardConfiguration(
    val layouts: List<KeyboardLayout> = emptyList(),
    val items: List<KeyboardItem> = emptyList(),
    val macros: List<KeyboardMacro> = emptyList(),
    val settings: KeyboardSettings? = null,
    val unsupportedMacroIds: Set<UUID> = emptySet(),
) {
    fun layout(id: UUID?): KeyboardLayout? = layouts.find { it.id == id }
        ?: layouts.find { it.id == settings?.defaultLayoutId }
        ?: layouts.firstOrNull()
}
