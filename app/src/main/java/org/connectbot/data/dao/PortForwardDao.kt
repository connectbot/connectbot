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
import org.connectbot.data.entity.PortForward

/**
 * Data Access Object for SSH port forwarding rules.
 */
@Dao
interface PortForwardDao {
    /**
     * Observe all port forwards for a specific host.
     * Automatically updates when forwards are added, updated, or deleted.
     */
    @Query("SELECT * FROM port_forwards WHERE host_id = :hostId ORDER BY nickname ASC")
    fun observeByHost(hostId: Long): Flow<List<PortForward>>

    /**
     * Get all port forwards for a specific host (one-time query).
     */
    @Query("SELECT * FROM port_forwards WHERE host_id = :hostId ORDER BY nickname ASC")
    suspend fun getByHost(hostId: Long): List<PortForward>

    /**
     * Get a single port forward by ID.
     */
    @Query("SELECT * FROM port_forwards WHERE id = :id")
    suspend fun getById(id: Long): PortForward?

    /**
     * Insert a new port forward.
     * @return The ID of the newly inserted port forward
     */
    @Insert
    suspend fun insert(portForward: PortForward): Long

    /**
     * Update an existing port forward.
     */
    @Update
    suspend fun update(portForward: PortForward)

    /**
     * Delete a port forward.
     */
    @Delete
    suspend fun delete(portForward: PortForward)

    /**
     * Delete all port forwards for a specific host.
     * Note: This is also handled automatically by CASCADE DELETE
     * when the host is deleted.
     */
    @Query("DELETE FROM port_forwards WHERE host_id = :hostId")
    suspend fun deleteByHost(hostId: Long)
}
