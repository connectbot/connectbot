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

package org.connectbot.service

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.util.HostConstants
import timber.log.Timber
import java.io.File

/**
 * Handles filtering logic for backup operations.
 *
 * This class is separated from BackupAgent to allow for unit testing
 * of the filtering logic without requiring system-level backup permissions.
 */
class BackupFilter(
    private val context: Context,
    private val hostRepository: HostRepository,
    private val colorSchemeRepository: ColorSchemeRepository,
    private val pubkeyRepository: PubkeyRepository,
    private val sourceDatabase: ConnectBotDatabase? = null,
) {
    /**
     * Build a filtered database containing only backupable data.
     *
     * Opens both the main database and a new temporary database,
     * then copies all data except non-backupable pubkeys.
     *
     * @param tempDbFile The temporary database file to create
     */
    suspend fun buildFilteredDatabase(tempDbFile: File, backupKeys: Boolean) {
        // Create a new temporary database
        val tempDb = Room.databaseBuilder(
            context,
            ConnectBotDatabase::class.java,
            tempDbFile.name,
        )
            .allowMainThreadQueries() // Backup runs on backup thread
            .build()

        try {
            if (sourceDatabase != null) {
                copySnapshot(sourceDatabase, tempDb, backupKeys)
                return
            }
            // Get all data from main database
            val allHosts = hostRepository.getHosts()
            val allColorSchemes = colorSchemeRepository.getAllSchemes()

            val backupablePubkeys = if (backupKeys) {
                // Filter pubkeys - only keep backupable ones
                val allPubkeys = pubkeyRepository.getAll()
                filterBackupablePubkeys(allPubkeys)
            } else {
                emptyList()
            }

            Timber.d("Backing up ${allHosts.size} hosts, ${backupablePubkeys.size} pubkeys, ${allColorSchemes.size} color schemes")

            // Insert all backupable data into temp database
            allHosts.forEach { host ->
                tempDb.hostDao().insert(host)
                // Also backup port forwards and known hosts for this host
                val portForwards = hostRepository.getPortForwardsForHost(host.id)
                portForwards.forEach { tempDb.portForwardDao().insert(it) }
                tempDb.automationActionDao().insert(hostRepository.getAutomation(host.id))

                val knownHosts = hostRepository.getKnownHostsForHost(host.id)
                knownHosts.forEach { tempDb.knownHostDao().insert(it) }
            }

            backupablePubkeys.forEach { pubkey ->
                tempDb.pubkeyDao().insert(pubkey)
            }

            allColorSchemes.forEach { scheme ->
                if (!scheme.isBuiltIn) {
                    tempDb.colorSchemeDao().insert(scheme)
                    // Also backup color palette for this scheme
                    val colors = colorSchemeRepository.getSchemeColors(scheme.id)
                    colors.forEachIndexed { index, color ->
                        tempDb.colorSchemeDao().insertColor(
                            ColorPalette(
                                schemeId = scheme.id,
                                colorIndex = index,
                                color = color,
                            ),
                        )
                    }
                }
            }
        } finally {
            tempDb.close()
        }
    }

    /** Copy a consistent snapshot, including UUID relationships and unacknowledged tombstones. */
    private fun copySnapshot(source: ConnectBotDatabase, target: ConnectBotDatabase, backupKeys: Boolean) {
        val input = source.openHelper.writableDatabase
        val output = target.openHelper.writableDatabase
        input.beginTransaction()
        output.beginTransaction()
        try {
            output.execSQL("PRAGMA defer_foreign_keys=ON")
            val entities = listOf("keyboard_layouts", "keyboard_macros", "keyboard_items", "keyboard_settings", "pubkeys", "color_schemes", "profiles", "hosts", "color_palette", "port_forwards", "known_hosts", "automation_actions")
            val metadata = input.query("SELECT name,sql FROM sqlite_master WHERE type='table' AND (name LIKE 'sync_%' OR name IN ('settings','legacy_id_map'))").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        output.execSQL(cursor.getString(1))
                        add(cursor.getString(0))
                    }
                }
            }
            for (table in entities + metadata) {
                val filter = if (table == "pubkeys") {
                    if (backupKeys) " WHERE allow_backup=1 AND storage_type!='ANDROID_KEYSTORE'" else " WHERE 0"
                } else {
                    ""
                }
                input.query("SELECT * FROM `$table`$filter").use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues()
                        cursor.columnNames.forEachIndexed { index, name ->
                            when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_NULL -> values.putNull(name)
                                Cursor.FIELD_TYPE_INTEGER -> values.put(name, cursor.getLong(index))
                                Cursor.FIELD_TYPE_FLOAT -> values.put(name, cursor.getDouble(index))
                                Cursor.FIELD_TYPE_BLOB -> values.put(name, cursor.getBlob(index))
                                else -> values.put(name, cursor.getString(index))
                            }
                        }
                        output.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
                    }
                }
            }
            output.execSQL("UPDATE hosts SET pubkey_id=? WHERE pubkey_id NOT IN (SELECT id FROM pubkeys) AND pubkey_id NOT IN (?,?)", arrayOf(HostConstants.PUBKEYID_ANY.toString(), HostConstants.PUBKEYID_ANY.toString(), HostConstants.PUBKEYID_NEVER.toString()))
            output.setTransactionSuccessful()
            input.setTransactionSuccessful()
        } finally {
            output.endTransaction()
            input.endTransaction()
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
    fun filterBackupablePubkeys(pubkeys: List<Pubkey>): List<Pubkey> = pubkeys.filter { pubkey ->
        val isBackupable = pubkey.allowBackup && pubkey.storageType != KeyStorageType.ANDROID_KEYSTORE

        if (!isBackupable) {
            Timber.d(
                "Filtering out pubkey: ${pubkey.nickname} " +
                    "(allowBackup=${pubkey.allowBackup}, storageType=${pubkey.storageType})",
            )
        }

        isBackupable
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
        Timber.d("Deleted temporary database files")
    }
}
