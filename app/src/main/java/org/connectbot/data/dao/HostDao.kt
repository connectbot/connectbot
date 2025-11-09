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

package org.connectbot.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.connectbot.data.entity.Host

/**
 * Data Access Object for SSH/Telnet host configurations.
 */
@Dao
interface HostDao {
    /**
     * Observe all hosts, ordered by nickname.
     */
    @Query("SELECT * FROM hosts ORDER BY nickname ASC")
    fun observeAll(): Flow<List<Host>>

    /**
     * Observe all hosts, ordered by color then nickname.
     * Used for color-grouped host list display.
     */
    @Query("SELECT * FROM hosts ORDER BY color, nickname ASC")
    fun observeAllSortedByColor(): Flow<List<Host>>

    /**
     * Observe a single host by ID.
     */
    @Query("SELECT * FROM hosts WHERE id = :hostId")
    fun observeById(hostId: Long): Flow<Host?>

    /**
     * Get a single host by ID (one-time query).
     */
    @Query("SELECT * FROM hosts WHERE id = :hostId")
    suspend fun getById(hostId: Long): Host?

    /**
     * Get all hosts (one-time query).
     */
    @Query("SELECT * FROM hosts ORDER BY nickname ASC")
    suspend fun getAll(): List<Host>

    /**
     * Get all hosts sorted by color.
     */
    @Query("SELECT * FROM hosts ORDER BY color, nickname ASC")
    suspend fun getAllSortedByColor(): List<Host>

    /**
     * Insert a new host.
     * @return The ID of the newly inserted host
     */
    @Insert
    suspend fun insert(host: Host): Long

    /**
     * Update an existing host.
     */
    @Update
    suspend fun update(host: Host)

    /**
     * Delete a host.
     * Also cascades to delete associated port forwards and known hosts.
     */
    @Delete
    suspend fun delete(host: Host)

    /**
     * Delete a host by ID.
     */
    @Query("DELETE FROM hosts WHERE id = :hostId")
    suspend fun deleteById(hostId: Long)

    /**
     * Find host associated with a known host entry.
     */
    @Query("""
        SELECT h.* FROM hosts h
        JOIN known_hosts kh ON h.id = kh.host_id
        WHERE kh.hostname = :hostname AND kh.port = :port
    """)
    suspend fun findByKnownHost(hostname: String, port: Int): Host?
}
