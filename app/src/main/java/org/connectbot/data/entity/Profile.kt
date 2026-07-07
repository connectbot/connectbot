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
import androidx.room.Index
import androidx.room.PrimaryKey

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
 * @property fontFamily Font family name (null uses system default)
 * @property fontSize Terminal font size
 * @property delKey DEL key behavior ("del" or "backspace")
 * @property encoding Character encoding (e.g., "UTF-8")
 * @property emulation Terminal emulation mode (e.g., "xterm-256color")
 * @property forceSizeRows Forced terminal rows (null = auto-size based on screen)
 * @property forceSizeColumns Forced terminal columns (null = auto-size based on screen)
 * @property startupCommand Command(s) to run when a connection is established (null = none)
 * @property startupCommandMode How to run the startup command: [STARTUP_MODE_INJECT] types it
 *   into the interactive shell after login; [STARTUP_MODE_EXEC_PTY] and [STARTUP_MODE_EXEC_NO_PTY]
 *   run it as the SSH session command (like `ssh -t` / `ssh -T`) with or without a pseudo-terminal
 * @property environmentVariables Environment variables to set on connect, one KEY=VALUE per line
 */
@Entity(
    tableName = "profiles",
    indices = [
        Index(value = ["name"], unique = true),
    ],
    // Note: No foreign key to color_schemes because built-in color schemes use negative IDs
    // and are virtual (not stored in the database). Only custom schemes have positive IDs.
)
data class Profile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "icon_color")
    val iconColor: String? = null,

    @ColumnInfo(name = "color_scheme_id", defaultValue = "-1")
    val colorSchemeId: Long = -1L,

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

    @ColumnInfo(name = "force_size_rows")
    val forceSizeRows: Int? = null,

    @ColumnInfo(name = "force_size_columns")
    val forceSizeColumns: Int? = null,

    @ColumnInfo(name = "startup_command")
    val startupCommand: String? = null,

    @ColumnInfo(name = "startup_command_mode", defaultValue = "'inject'")
    val startupCommandMode: String = STARTUP_MODE_INJECT,

    @ColumnInfo(name = "environment_variables")
    val environmentVariables: String? = null,
) {
    companion object {
        /** Type the startup command into the interactive shell after login. */
        const val STARTUP_MODE_INJECT = "inject"

        /** Run the startup command as the SSH session command with a PTY (like `ssh -t`). */
        const val STARTUP_MODE_EXEC_PTY = "exec_pty"

        /** Run the startup command as the SSH session command without a PTY (like `ssh -T`). */
        const val STARTUP_MODE_EXEC_NO_PTY = "exec_no_pty"

        /**
         * Create a default profile with auto-generated ID.
         */
        fun createDefault(): Profile = Profile(
            id = 0,
            name = "Default",
        )
    }
}
