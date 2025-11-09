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

package org.connectbot.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.connectbot.data.dao.PubkeyDao
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey

/**
 * Repository for managing SSH public/private key pairs.
 * Handles pubkey CRUD operations.
 *
 * @param pubkeyDao The DAO for accessing pubkey data
 */
class PubkeyRepository(
    private val pubkeyDao: PubkeyDao
) {

    // ============================================================================
    // Pubkey Operations
    // ============================================================================

    /**
     * Observe all pubkeys reactively.
     *
     * @return Flow of pubkey list that updates automatically
     */
    fun observeAll(): Flow<List<Pubkey>> = pubkeyDao.observeAll()

    /**
     * Observe pubkeys by storage type reactively.
     *
     * @param type The storage type (EXPORTABLE or ANDROID_KEYSTORE)
     * @return Flow of pubkey list filtered by type
     */
    fun observeByStorageType(type: KeyStorageType): Flow<List<Pubkey>> =
        pubkeyDao.observeByStorageType(type)

    /**
     * Observe a specific pubkey reactively.
     *
     * @param pubkeyId The pubkey ID
     * @return Flow of pubkey that updates automatically
     */
    fun observePubkey(pubkeyId: Long): Flow<Pubkey?> = pubkeyDao.observeById(pubkeyId)

    /**
     * Get all pubkeys.
     *
     * @return List of all pubkeys
     */
    suspend fun getAll(): List<Pubkey> = pubkeyDao.getAll()

    /**
     * Find a pubkey by its unique ID.
     *
     * @param pubkeyId The pubkey ID
     * @return The pubkey, or null if not found
     */
    suspend fun getById(pubkeyId: Long): Pubkey? = pubkeyDao.getById(pubkeyId)

    /**
     * Find a pubkey by its unique ID (blocking).
     * For Java interop from service layer.
     *
     * @param pubkeyId The pubkey ID
     * @return The pubkey, or null if not found
     */
    fun getByIdBlocking(pubkeyId: Long): Pubkey? = runBlocking {
        pubkeyDao.getById(pubkeyId)
    }

    /**
     * Find a pubkey by nickname.
     *
     * @param nickname The pubkey nickname
     * @return The pubkey, or null if not found
     */
    suspend fun getByNickname(nickname: String): Pubkey? =
        pubkeyDao.getByNickname(nickname)

    /**
     * Get all pubkeys that allow backup.
     *
     * @return List of backupable pubkeys
     */
    suspend fun getBackupable(): List<Pubkey> = pubkeyDao.getBackupable()

    /**
     * Get all exportable pubkeys (not stored in Android Keystore).
     *
     * @return List of exportable pubkeys
     */
    suspend fun getExportable(): List<Pubkey> = pubkeyDao.getExportable()

    /**
     * Get all pubkeys marked for automatic unlocking at startup.
     *
     * @return List of startup pubkeys
     */
    suspend fun getStartupKeys(): List<Pubkey> = pubkeyDao.getStartupKeys()

    /**
     * Get all pubkeys marked for automatic unlocking at startup (blocking).
     * For Java interop from service layer.
     *
     * @return List of startup pubkeys
     */
    fun getStartupKeysBlocking(): List<Pubkey> = runBlocking {
        pubkeyDao.getStartupKeys()
    }

    /**
     * Save a pubkey (insert or update).
     *
     * @param pubkey The pubkey to save
     * @return The saved pubkey with updated ID
     */
    suspend fun save(pubkey: Pubkey): Pubkey {
        return if (pubkey.id == 0L) {
            // New pubkey - insert
            val newId = pubkeyDao.insert(pubkey)
            pubkey.copy(id = newId)
        } else {
            // Existing pubkey - update
            pubkeyDao.update(pubkey)
            pubkey
        }
    }

    /**
     * Delete a pubkey.
     *
     * @param pubkey The pubkey to delete
     */
    suspend fun delete(pubkey: Pubkey) {
        pubkeyDao.delete(pubkey)
    }

    /**
     * Update backup permission for a pubkey.
     *
     * @param pubkeyId The pubkey ID
     * @param allowBackup Whether to allow backup
     */
    suspend fun updateBackupPermission(pubkeyId: Long, allowBackup: Boolean) {
        pubkeyDao.updateBackupPermission(pubkeyId, allowBackup)
    }

    companion object {
        @Volatile
        private var instance: PubkeyRepository? = null

        /**
         * Get the singleton repository instance.
         * Uses the production database.
         *
         * @param context Application context
         * @return PubkeyRepository instance
         */
        fun get(context: Context): PubkeyRepository {
            return instance ?: synchronized(this) {
                instance ?: PubkeyRepository(
                    ConnectBotDatabase.getInstance(context.applicationContext).pubkeyDao()
                ).also {
                    instance = it
                }
            }
        }

        /**
         * Clear the singleton instance.
         * Used for testing purposes.
         */
        @androidx.annotation.VisibleForTesting
        fun clearInstance() {
            instance = null
        }
    }
}
