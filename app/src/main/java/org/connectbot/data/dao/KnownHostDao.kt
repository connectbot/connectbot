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
import org.connectbot.data.entity.KnownHost

/**
 * Data Access Object for SSH known hosts (host key verification).
 */
@Dao
interface KnownHostDao {
    /**
     * Get a known host by hostname and port.
     * Used for SSH host key verification.
     */
    @Query("SELECT * FROM known_hosts WHERE hostname = :hostname AND port = :port")
    suspend fun getByHostnameAndPort(hostname: String, port: Int): KnownHost?

    /**
     * Get all known hosts for a specific host configuration.
     */
    @Query("SELECT * FROM known_hosts WHERE host_id = :hostId")
    suspend fun getByHostId(hostId: Long): List<KnownHost>

    /**
     * Get all known hosts.
     */
    @Query("SELECT * FROM known_hosts ORDER BY hostname, port")
    suspend fun getAll(): List<KnownHost>

    /**
     * Insert a new known host.
     * @return The ID of the newly inserted known host
     */
    @Insert
    suspend fun insert(knownHost: KnownHost): Long

    /**
     * Update an existing known host (e.g., if host key changes).
     */
    @Update
    suspend fun update(knownHost: KnownHost)

    /**
     * Delete a known host.
     */
    @Delete
    suspend fun delete(knownHost: KnownHost)

    /**
     * Delete a known host by hostname and port.
     */
    @Query("DELETE FROM known_hosts WHERE hostname = :hostname AND port = :port")
    suspend fun deleteByHostnameAndPort(hostname: String, port: Int)
}
