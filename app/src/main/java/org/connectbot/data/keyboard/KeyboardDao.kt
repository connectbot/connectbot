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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface KeyboardDao {
    @Query("SELECT * FROM keyboard_layouts ORDER BY name, id")
    fun observeLayouts(): Flow<List<KeyboardLayout>>

    @Query("SELECT * FROM keyboard_items ORDER BY row, column, id")
    fun observeItems(): Flow<List<KeyboardItem>>

    @Query("SELECT * FROM keyboard_macros ORDER BY name, id")
    fun observeMacros(): Flow<List<KeyboardMacro>>

    @Query("SELECT * FROM keyboard_settings LIMIT 1")
    fun observeSettings(): Flow<KeyboardSettings?>

    @Query("SELECT * FROM keyboard_layouts ORDER BY name, id")
    suspend fun layouts(): List<KeyboardLayout>

    @Query("SELECT * FROM keyboard_items")
    suspend fun items(): List<KeyboardItem>

    @Query("SELECT * FROM keyboard_macros")
    suspend fun macros(): List<KeyboardMacro>

    @Query("SELECT * FROM keyboard_settings LIMIT 1")
    suspend fun settings(): KeyboardSettings?

    @Upsert suspend fun saveLayout(layout: KeyboardLayout)

    @Upsert suspend fun saveItems(items: List<KeyboardItem>)

    @Upsert suspend fun saveMacro(macro: KeyboardMacro)

    @Upsert suspend fun saveSettings(settings: KeyboardSettings)

    @Query("DELETE FROM keyboard_items WHERE layout_id = :id")
    suspend fun deleteItems(id: UUID)

    @Query("DELETE FROM keyboard_layouts WHERE id = :id")
    suspend fun deleteLayout(id: UUID)

    @Query("DELETE FROM keyboard_macros WHERE id = :id")
    suspend fun deleteMacro(id: UUID)
}
