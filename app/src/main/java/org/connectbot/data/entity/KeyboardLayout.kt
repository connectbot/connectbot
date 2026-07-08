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

package org.connectbot.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named, user-defined layout for the terminal special-keys bar.
 *
 * The ordered rows of keys are stored as JSON in [keysJson] (see
 * `org.connectbot.keyboard.KeyboardLayoutJson`). Built-in layouts are virtual
 * (negative IDs) and are never stored here.
 */
@Entity(
    tableName = "keyboard_layouts",
    indices = [
        Index(value = ["name"], unique = true),
    ],
)
data class KeyboardLayout(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "keys_json")
    val keysJson: String,
)
