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

package org.connectbot.service

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.KeyStorageType
import java.io.File

/**
 * Handles filtering logic for backup operations.
 *
 * This class is separated from BackupAgent to allow for unit testing
 * of the filtering logic without requiring system-level backup permissions.
 */
class BackupFilter(private val context: Context) {

    companion object {
        private const val TAG = "CB.BackupFilter"
    }

    /**
     * Build a filtered database containing only backupable data.
     *
     * Opens both the main database and a new temporary database,
     * then copies all data except non-backupable pubkeys.
     *
     * @param tempDbFile The temporary database file to create
     */
    fun buildFilteredDatabase(tempDbFile: File, backupKeys: Boolean): Unit = runBlocking {
        // Open the main database (singleton)
        val mainDb = ConnectBotDatabase.getInstance(context)

        // Create a new temporary database
        val tempDb = Room.databaseBuilder(
            context,
            ConnectBotDatabase::class.java,
            tempDbFile.name
        )
            .allowMainThreadQueries() // Backup runs on backup thread
            .build()

        try {
            // Get all data from main database
            val allHosts = mainDb.hostDao().getAll()
            val allColorSchemes = mainDb.colorSchemeDao().getAll()

            val backupablePubkeys = if (backupKeys) {
                // Filter pubkeys - only keep backupable ones
                val allPubkeys = mainDb.pubkeyDao().getAll()
                filterBackupablePubkeys(allPubkeys)
            } else {
                emptyList()
            }

            Log.d(TAG, "Backing up ${allHosts.size} hosts, ${backupablePubkeys.size} pubkeys, ${allColorSchemes.size} color schemes")

            // Insert all backupable data into temp database
            allHosts.forEach { host ->
                tempDb.hostDao().insert(host)
                // Also backup port forwards and known hosts for this host
                val portForwards = mainDb.portForwardDao().getByHost(host.id)
                portForwards.forEach { tempDb.portForwardDao().insert(it) }

                val knownHosts = mainDb.knownHostDao().getByHostId(host.id)
                knownHosts.forEach { tempDb.knownHostDao().insert(it) }
            }

            backupablePubkeys.forEach { pubkey ->
                tempDb.pubkeyDao().insert(pubkey)
            }

            allColorSchemes.forEach { scheme ->
                tempDb.colorSchemeDao().insert(scheme)
                // Also backup color palette for this scheme
                val colors = mainDb.colorSchemeDao().getColors(scheme.id.toLong())
                colors.forEach { tempDb.colorSchemeDao().insertColor(it) }
            }
        } finally {
            tempDb.close()
        }
    }

    /**
     * Filter a list of pubkeys to only include backupable ones.
     *
     * A pubkey is backupable if:
     * - allowBackup = true
     * - storageType != ANDROID_KEYSTORE
     *
     * This method is public and suspend to allow for easy unit testing.
     *
     * @param pubkeys The list of pubkeys to filter
     * @return The list of backupable pubkeys
     */
    fun filterBackupablePubkeys(pubkeys: List<org.connectbot.data.entity.Pubkey>):
            List<org.connectbot.data.entity.Pubkey> {
        return pubkeys.filter { pubkey ->
            val isBackupable = pubkey.allowBackup && pubkey.storageType != KeyStorageType.ANDROID_KEYSTORE

            if (!isBackupable) {
                Log.d(TAG, "Filtering out pubkey: ${pubkey.nickname} " +
                        "(allowBackup=${pubkey.allowBackup}, storageType=${pubkey.storageType})")
            }

            isBackupable
        }
    }

    /**
     * Clean up temporary database files.
     *
     * @param tempDbFile The temporary database file
     */
    fun cleanupTempDatabase(tempDbFile: File) {
        if (tempDbFile.exists()) {
            tempDbFile.delete()
        }
        // Also delete temp database files (WAL, SHM)
        File(tempDbFile.path + "-wal").delete()
        File(tempDbFile.path + "-shm").delete()
        Log.d(TAG, "Deleted temporary database files")
    }
}
