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
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.connectbot.data.entity.Profile

/**
 * Data Access Object for terminal profiles.
 */
@Dao
interface ProfileDao {
    /**
     * Observe all profiles, ordered by name.
     */
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun observeAll(): Flow<List<Profile>>

    /**
     * Observe a single profile by ID.
     */
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    fun observeById(profileId: Long): Flow<Profile?>

    /**
     * Get a single profile by ID (one-time query).
     */
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getById(profileId: Long): Profile?

    /**
     * Get all profiles (one-time query).
     */
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    suspend fun getAll(): List<Profile>

    /**
     * Get the default profile.
     */
    @Query("SELECT * FROM profiles WHERE id = 1")
    suspend fun getDefault(): Profile?

    /**
     * Insert a new profile.
     * @return The ID of the newly inserted profile
     */
    @Insert
    suspend fun insert(profile: Profile): Long

    /**
     * Update an existing profile.
     */
    @Update
    suspend fun update(profile: Profile)

    /**
     * Insert or update a profile.
     * @return The ID of the inserted/updated profile
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: Profile): Long

    /**
     * Delete a profile.
     */
    @Delete
    suspend fun delete(profile: Profile)

    /**
     * Delete a profile by ID.
     */
    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteById(profileId: Long): Int

    /**
     * Check if a profile name already exists (case-insensitive).
     *
     * @param name The name to check
     * @param excludeProfileId Optional profile ID to exclude from the check (for renames)
     * @return true if the name exists, false otherwise
     */
    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE LOWER(name) = LOWER(:name) AND (:excludeProfileId IS NULL OR id != :excludeProfileId))")
    suspend fun nameExists(name: String, excludeProfileId: Long? = null): Boolean

    /**
     * Get count of hosts using a specific profile.
     */
    @Query("SELECT COUNT(*) FROM hosts WHERE profile_id = :profileId")
    suspend fun getHostsUsingProfile(profileId: Long): Int
}
