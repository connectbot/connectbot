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

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

object KeyboardDefaults {
    val layoutId: UUID = UUID.fromString("5a46e491-229f-4ff9-a9ce-7b975f300001")
    val settingsId: UUID = UUID.fromString("5a46e491-229f-4ff9-a9ce-7b975f300002")
    val keys = listOf("escape", "tab", "arrow_up", "arrow_down", "arrow_left", "arrow_right", "home", "end", "page_up", "page_down") +
        (1..12).map { "f$it" } + listOf("enter", "backspace", "delete", "insert")
    val modifiers = listOf("ctrl", "alt", "shift")
    val controls = listOf("text_input", "toggle_compose", "toggle_ime")
    fun items(id: UUID): List<KeyboardItem> = (
        listOf("modifier" to "ctrl", "modifier" to "alt") + keys.dropLast(4).map { "key" to it } +
            listOf("app" to "text_input", "app" to "toggle_compose", "app" to "toggle_ime")
        ).mapIndexed { index, (kind, target) ->
        KeyboardItem(
            id = UUID.nameUUIDFromBytes("$id/$index".toByteArray(Charsets.UTF_8)),
            layoutId = id,
            column = index,
            kind = kind,
            target = target,
        )
    }

    fun seed(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT OR IGNORE INTO keyboard_layouts (id, name, button_width, button_height) VALUES (?, 'Default', 45, 30)", arrayOf(layoutId.toString()))
        items(layoutId).forEach { item ->
            db.execSQL(
                "INSERT OR IGNORE INTO keyboard_items (id, layout_id, row, column, row_span, column_span, visible, kind, target, macro_id) VALUES (?, ?, 0, ?, 1, 1, 1, ?, ?, NULL)",
                arrayOf<Any>(item.id.toString(), layoutId.toString(), item.column, item.kind, item.target),
            )
        }
        db.execSQL("INSERT OR IGNORE INTO keyboard_settings (id, default_layout_id) VALUES (?, ?)", arrayOf(settingsId.toString(), layoutId.toString()))
    }

    fun validate(layout: KeyboardLayout, items: List<KeyboardItem>) {
        require(layout.name.isNotBlank()) { "A layout needs a name" }
        require(layout.buttonWidth.isFinite() && layout.buttonWidth > 0 && layout.buttonWidth <= 1000) { "Button width must be between 0 and 1000 dp" }
        require(layout.buttonHeight.isFinite() && layout.buttonHeight > 0 && layout.buttonHeight <= 1000) { "Button height must be between 0 and 1000 dp" }
        require(items.size <= 256) { "A layout supports up to 256 buttons" }
        require(items.map { it.id }.distinct().size == items.size)
        items.forEachIndexed { index, item ->
            require(item.layoutId == layout.id)
            require(
                item.row >= 0 && item.column >= 0 && item.rowSpan > 0 && item.columnSpan > 0 &&
                    item.row.toLong() + item.rowSpan <= 1000 && item.column.toLong() + item.columnSpan <= 1000,
            ) { "Invalid grid position or span" }
            require(
                (item.column.toLong() + item.columnSpan) * layout.buttonWidth <= 3000 &&
                    (item.row.toLong() + item.rowSpan) * layout.buttonHeight <= 3000,
            ) { "Grid width and height must not exceed 3000 dp" }
            require(
                items.take(index).none { other ->
                    item.row < other.row + other.rowSpan && other.row < item.row + item.rowSpan &&
                        item.column < other.column + other.columnSpan && other.column < item.column + item.columnSpan
                },
            ) { "Buttons overlap" }
        }
    }
}
