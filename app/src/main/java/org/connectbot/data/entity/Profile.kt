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

package org.connectbot.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.connectbot.data.EntityIds
import org.connectbot.data.keyboard.KeyboardLayout
import java.util.UUID

/**
 * Terminal profile entity that bundles terminal-specific settings.
 *
 * Profiles allow users to define reusable terminal configurations that can be
 * assigned to multiple hosts. This is inspired by iTerm2's profile system.
 *
 * @property id Database ID of the profile
 * @property name Display name of the profile
 * @property iconColor Icon color for visual identification (e.g., "blue", "#4CAF50")
 * @property colorSchemeId Reference to the color scheme
 * @property boldAsBright Whether bold text promotes low-intensity ANSI colors to bright colors
 * @property fontFamily Font family name (null uses system default)
 * @property fontSize Terminal font size
 * @property delKey DEL key behavior ("del" or "backspace")
 * @property encoding Character encoding (e.g., "UTF-8")
 * @property emulation Terminal emulation mode (e.g., "xterm-256color")
 * @property inlineImages Inline image policy ("off", "ask", or "on"); Ask remembers consent per session
 * @property forceSizeRows Forced terminal rows (null = auto-size based on screen)
 * @property forceSizeColumns Forced terminal columns (null = auto-size based on screen)
 */
@Entity(
    tableName = "profiles",
    indices = [
        Index(value = ["repository_id", "name"], unique = true),
        Index(value = ["keyboard_layout_id"]),
    ],
    foreignKeys = [ForeignKey(entity = KeyboardLayout::class, parentColumns = ["id"], childColumns = ["keyboard_layout_id"], onDelete = ForeignKey.SET_NULL)],
    // Built-in schemes have stable virtual UUIDs and are not rows in color_schemes.
)
data class Profile(
    @PrimaryKey
    val id: UUID = UUID.randomUUID(),

    @ColumnInfo(name = "repository_id", defaultValue = "'" + EntityIds.LOCAL_TEXT + "'")
    val repositoryId: UUID = EntityIds.LOCAL,

    val name: String,

    @ColumnInfo(name = "icon_color")
    val iconColor: String? = null,

    @ColumnInfo(name = "color_scheme_id")
    val colorSchemeId: UUID = EntityIds.DEFAULT_SCHEME,

    @ColumnInfo(name = "bold_as_bright", defaultValue = "1")
    val boldAsBright: Boolean = true,

    @ColumnInfo(name = "font_family")
    val fontFamily: String? = null,

    @ColumnInfo(name = "font_size", defaultValue = "10")
    val fontSize: Int = 10,

    @ColumnInfo(name = "del_key", defaultValue = "'del'")
    val delKey: String = "del",

    @ColumnInfo(defaultValue = "'UTF-8'")
    val encoding: String = "UTF-8",

    @ColumnInfo(defaultValue = "'xterm-256color'")
    val emulation: String = "xterm-256color",

    @ColumnInfo(name = "inline_images", defaultValue = "'ask'")
    val inlineImages: String = "ask",

    @ColumnInfo(name = "force_size_rows")
    val forceSizeRows: Int? = null,

    @ColumnInfo(name = "force_size_columns")
    val forceSizeColumns: Int? = null,

    @ColumnInfo(name = "keyboard_layout_id")
    val keyboardLayoutId: UUID? = null,
) {
    companion object {
        /**
         * Create the Local default profile with its stable UUID.
         */
        fun createDefault(): Profile = Profile(
            id = EntityIds.DEFAULT_PROFILE,
            name = "Default",
        )
    }
}
