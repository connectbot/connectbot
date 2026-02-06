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

package org.connectbot.data.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import timber.log.Timber

/**
 * Reads data from the legacy PubkeyDatabase (version 2).
 * This is a read-only class that extracts data from the old database format.
 */
class LegacyPubkeyDatabaseReader(private val context: Context) {

    companion object {
        private const val DB_NAME = "pubkeys"
    }

    /**
     * Reads all pubkeys from the legacy database.
     * All migrated keys are marked as EXPORTABLE with allowBackup=true by default.
     */
    fun readPubkeys(): List<Pubkey> {
        val pubkeys = mutableListOf<Pubkey>()

        withReadableDatabase { db ->
            db.query(
                "pubkeys",
                null, // all columns
                null, // no where clause
                null, // no where args
                null, // no group by
                null, // no having
                "nickname ASC" // order by
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val pubkey = cursorToPubkey(cursor)
                        pubkeys.add(pubkey)
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading pubkey from cursor")
                    }
                }
            }
        }

        Timber.d("Read ${pubkeys.size} pubkeys from legacy database")
        return pubkeys
    }

    private fun cursorToPubkey(cursor: Cursor): Pubkey {
        val idIndex = cursor.getColumnIndexOrThrow("_id")
        val nicknameIndex = cursor.getColumnIndexOrThrow("nickname")
        val typeIndex = cursor.getColumnIndexOrThrow("type")
        val privateIndex = cursor.getColumnIndexOrThrow("private")
        val publicIndex = cursor.getColumnIndexOrThrow("public")
        val encryptedIndex = cursor.getColumnIndexOrThrow("encrypted")
        val startupIndex = cursor.getColumnIndexOrThrow("startup")
        val confirmUseIndex = cursor.getColumnIndexOrThrow("confirmuse")

        // Note: The legacy database has a "lifetime" field that we're not migrating
        // as it's not used in the new schema

        return Pubkey(
            id = cursor.getLong(idIndex),
            nickname = cursor.getString(nicknameIndex),
            type = cursor.getString(typeIndex),
            privateKey = cursor.getBlob(privateIndex),
            publicKey = cursor.getBlob(publicIndex),
            encrypted = cursor.getInt(encryptedIndex) == 1,
            startup = cursor.getInt(startupIndex) == 1,
            confirmation = cursor.getInt(confirmUseIndex) == 1,
            createdDate = System.currentTimeMillis(), // Legacy DB doesn't track this, use current time
            storageType = KeyStorageType.EXPORTABLE, // All legacy keys are exportable
            allowBackup = true, // Default to allowing backup for legacy keys
            keystoreAlias = null // Legacy keys don't use Android Keystore
        )
    }

    private inline fun withReadableDatabase(block: (SQLiteDatabase) -> Unit) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            Timber.w("Legacy database file does not exist: ${dbFile.absolutePath}")
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            try {
                block(db)
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error opening legacy database")
            throw MigrationException("Failed to open legacy pubkeys database: ${e.message}")
        }
    }
}
