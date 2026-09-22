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

package org.connectbot.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.connectbot.data.dao.ProfileDao
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing terminal profiles.
 * Profiles bundle terminal-specific settings like color scheme, font, encoding, etc.
 *
 * @param profileDao The DAO for accessing profile data
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val dispatchers: CoroutineDispatchers,
) {
    /**
     * Observe all profiles.
     */
    fun observeAll(): Flow<List<Profile>> = profileDao.observeAll()

    /**
     * Observe a single profile by ID.
     */
    fun observeById(profileId: UUID): Flow<Profile?> = profileDao.observeById(profileId)

    /**
     * Get all profiles.
     */
    suspend fun getAll(): List<Profile> = withContext(dispatchers.io) {
        profileDao.getAll()
    }

    /**
     * Get a profile by ID.
     */
    suspend fun getById(profileId: UUID): Profile? = withContext(dispatchers.io) {
        profileDao.getById(profileId)
    }

    /**
     * Get the default profile.
     */
    suspend fun getDefault(): Profile = withContext(dispatchers.io) {
        profileDao.getDefault() ?: Profile.createDefault()
    }

    /**
     * Create a new profile.
     *
     * @param name The name for the new profile
     * @param basedOnProfileId Optional profile ID to copy settings from
     * @return The ID of the newly created profile
     */
    suspend fun create(
        name: String,
        basedOnProfileId: UUID? = null,
    ): UUID = withContext(dispatchers.io) {
        val baseProfile = if (basedOnProfileId != null) {
            profileDao.getById(basedOnProfileId) ?: Profile.createDefault()
        } else {
            Profile.createDefault()
        }

        val newProfile = baseProfile.copy(
            id = UUID.randomUUID(),
            name = name,
        )
        profileDao.insert(newProfile)
    }

    /**
     * Update an existing profile.
     */
    suspend fun update(profile: Profile) = withContext(dispatchers.io) {
        profileDao.update(profile)
    }

    /**
     * Save a profile (insert or update).
     *
     * @return The ID of the saved profile
     */
    suspend fun save(profile: Profile): UUID = withContext(dispatchers.io) {
        if (profileDao.getById(profile.id) == null) {
            profileDao.insert(profile)
        } else {
            profileDao.update(profile)
            profile.id
        }
    }

    /**
     * Delete a profile by ID.
     *
     * @param profileId The profile ID to delete
     * @return true if deleted, false if not found
     */
    suspend fun delete(profileId: UUID): Boolean = withContext(dispatchers.io) {
        profileDao.deleteById(profileId) > 0
    }

    /**
     * Check if a profile name already exists.
     *
     * @param name The name to check
     * @param excludeProfileId Optional profile ID to exclude from the check (for renames)
     * @return true if the name exists, false otherwise
     */
    suspend fun nameExists(name: String, excludeProfileId: UUID? = null): Boolean = withContext(dispatchers.io) {
        profileDao.nameExists(name, excludeProfileId)
    }

    /**
     * Get the count of hosts using a specific profile.
     */
    suspend fun getHostsUsingProfile(profileId: UUID): Int = withContext(dispatchers.io) {
        profileDao.getHostsUsingProfile(profileId)
    }

    /**
     * Blocking wrapper for getById - for use from non-coroutine code.
     */
    fun getByIdBlocking(profileId: UUID): Profile? = runBlocking {
        getById(profileId)
    }

    /**
     * Blocking wrapper that returns the profile or the default if not found.
     * This ensures a profile is always returned.
     */
    fun getByIdOrDefaultBlocking(profileId: UUID?): Profile = runBlocking {
        if (profileId == null) {
            getDefault()
        } else {
            getById(profileId) ?: getDefault()
        }
    }

    /**
     * Duplicate an existing profile.
     *
     * @param sourceProfileId The profile to duplicate
     * @param newName The name for the duplicated profile
     * @return The ID of the newly created profile
     */
    suspend fun duplicate(sourceProfileId: UUID, newName: String): UUID = withContext(dispatchers.io) {
        val source = profileDao.getById(sourceProfileId) ?: Profile.createDefault()
        val newProfile = source.copy(
            id = UUID.randomUUID(),
            name = newName,
        )
        profileDao.insert(newProfile)
    }
}
