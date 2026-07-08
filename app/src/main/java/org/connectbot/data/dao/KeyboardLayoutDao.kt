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

package org.connectbot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.connectbot.data.entity.KeyboardLayout

/**
 * Data Access Object for user-defined keyboard layouts.
 */
@Dao
interface KeyboardLayoutDao {
    @Query("SELECT * FROM keyboard_layouts ORDER BY name ASC")
    fun observeAll(): Flow<List<KeyboardLayout>>

    @Query("SELECT * FROM keyboard_layouts WHERE id = :layoutId")
    fun observeById(layoutId: Long): Flow<KeyboardLayout?>

    @Query("SELECT * FROM keyboard_layouts WHERE id = :layoutId")
    suspend fun getById(layoutId: Long): KeyboardLayout?

    @Query("SELECT * FROM keyboard_layouts ORDER BY name ASC")
    suspend fun getAll(): List<KeyboardLayout>

    @Insert
    suspend fun insert(layout: KeyboardLayout): Long

    @Update
    suspend fun update(layout: KeyboardLayout)

    @Query("DELETE FROM keyboard_layouts WHERE id = :layoutId")
    suspend fun deleteById(layoutId: Long): Int

    /**
     * Check if a layout name already exists (case-insensitive).
     *
     * @param excludeLayoutId Optional layout ID to exclude from the check (for renames).
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM keyboard_layouts WHERE LOWER(name) = LOWER(:name) " +
            "AND (:excludeLayoutId IS NULL OR id != :excludeLayoutId))",
    )
    suspend fun nameExists(name: String, excludeLayoutId: Long? = null): Boolean

    /** Number of hosts that override to a specific layout. */
    @Query("SELECT COUNT(*) FROM hosts WHERE keyboard_layout_id = :layoutId")
    suspend fun countHostsUsing(layoutId: Long): Int

    /** Clear a layout override from any hosts pointing at it (before deletion). */
    @Query("UPDATE hosts SET keyboard_layout_id = NULL WHERE keyboard_layout_id = :layoutId")
    suspend fun clearHostReferences(layoutId: Long)
}
