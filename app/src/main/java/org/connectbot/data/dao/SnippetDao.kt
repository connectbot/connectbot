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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.connectbot.data.entity.Snippet

/**
 * Data Access Object for command snippets.
 */
@Dao
interface SnippetDao {
    /**
     * Observe all snippets, ordered by name.
     */
    @Query("SELECT * FROM snippets ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Snippet>>

    /**
     * Observe the snippets available for a host: global snippets plus
     * snippets scoped to that host.
     */
    @Query("SELECT * FROM snippets WHERE host_id IS NULL OR host_id = :hostId ORDER BY name COLLATE NOCASE ASC")
    fun observeForHost(hostId: Long): Flow<List<Snippet>>

    /**
     * Get a single snippet by ID (one-time query).
     */
    @Query("SELECT * FROM snippets WHERE id = :snippetId")
    suspend fun getById(snippetId: Long): Snippet?

    /**
     * Insert a new snippet.
     * @return The ID of the newly inserted snippet
     */
    @Insert
    suspend fun insert(snippet: Snippet): Long

    /**
     * Update an existing snippet.
     */
    @Update
    suspend fun update(snippet: Snippet)

    /**
     * Delete a snippet.
     */
    @Delete
    suspend fun delete(snippet: Snippet)

    /**
     * Delete a snippet by ID.
     */
    @Query("DELETE FROM snippets WHERE id = :snippetId")
    suspend fun deleteById(snippetId: Long): Int
}
