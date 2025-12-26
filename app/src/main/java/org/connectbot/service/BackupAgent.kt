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

import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.SharedPreferencesBackupHelper
import android.os.ParcelFileDescriptor
import timber.log.Timber
import androidx.preference.PreferenceManager
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.PreferenceConstants
import java.io.File
import java.io.FileInputStream

/**
 * ConnectBot's backup agent with selective pubkey filtering.
 *
 * This agent backs up:
 * - Shared preferences
 * - The Room database (connectbot.db)
 *
 * However, before backing up the database, it filters out:
 * - Pubkeys with allowBackup = false
 * - Pubkeys with storageType = ANDROID_KEYSTORE
 *
 * This ensures sensitive SSH keys are not included in cloud backups
 * if the user has disabled backup for those keys.
 *
 * Implementation: We create a temporary database, insert the backupable
 * data into it, back it up as the original db name, then delete the
 * temporary database. Upon restore, the original db name is used
 * automatically.
 */
class BackupAgent : BackupAgentHelper() {
    companion object {
        private const val TAG = "CB.BackupAgent"
        private const val DATABASE_NAME = "connectbot.db"
        private const val TEMP_DATABASE_NAME = "connectbot_backup_temp.db"
    }

    override fun onCreate() {
        Timber.d("onCreate called")

        // Backup shared preferences
        val prefsHelper = SharedPreferencesBackupHelper(
            this,
            packageName + "_preferences"
        )
        addHelper(PreferenceConstants.BACKUP_PREF_KEY, prefsHelper)
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor
    ) {
        // First, handle preferences and other helpers
        super.onBackup(oldState, data, newState)

        // Check if user wants to backup the keys at all
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val backupKeys = prefs.getBoolean(
            PreferenceConstants.BACKUP_KEYS,
            PreferenceConstants.BACKUP_KEYS_DEFAULT
        )

        try {
            backupDatabaseWithFiltering(data, backupKeys)
        } catch (e: Exception) {
            Timber.e(e, "Failed to backup database")
        }
    }

    /**
     * Create a temporary database with only backupable data, back it up, then delete it.
     */
    private fun backupDatabaseWithFiltering(data: BackupDataOutput, backupKeys: Boolean) {
        val dbFile = getDatabasePath(DATABASE_NAME)
        if (!dbFile.exists()) {
            Timber.w("Database does not exist yet, skipping backup")
            return
        }

        val tempDbFile = getDatabasePath(TEMP_DATABASE_NAME)

        // Manually create database and repositories for BackupFilter
        val database = Room.databaseBuilder(
            applicationContext,
            ConnectBotDatabase::class.java,
            DATABASE_NAME
        ).build()
        val dispatchers = CoroutineDispatchers(default = Dispatchers.Default, io = Dispatchers.IO, main = Dispatchers.Main)
        val hostRepository = HostRepository(applicationContext, database, database.hostDao(), database.portForwardDao(), database.knownHostDao())
        val colorSchemeRepository = ColorSchemeRepository(database.colorSchemeDao(), dispatchers = dispatchers)
        val pubkeyRepository = PubkeyRepository(database.pubkeyDao())

        val filter = BackupFilter(applicationContext, hostRepository, colorSchemeRepository, pubkeyRepository)
        try {
            // Step 1: Build a temporary database with filtered data
            Timber.d("Building temporary database with backupable data")
            kotlinx.coroutines.runBlocking {
                filter.buildFilteredDatabase(tempDbFile, backupKeys)
            }

            // Step 2: Back up the filtered database
            Timber.d("Backing up filtered database")
            backupFile(tempDbFile, DATABASE_NAME, data)

        } catch (e: Exception) {
            Timber.e(e, "Error during database backup with filtering")
            throw e
        } finally {
            // Step 3: Clean up the temporary database
            filter.cleanupTempDatabase(tempDbFile)
            database.close() // Close the manually created database
        }
    }

    /**
     * Back up a file using BackupDataOutput.
     */
    private fun backupFile(file: File, key: String, data: BackupDataOutput) {
        if (!file.exists()) {
            Timber.w("File does not exist: ${file.path}")
            return
        }

        FileInputStream(file).use { input ->
            val fileSize = file.length().toInt()
            val buffer = ByteArray(fileSize)
            val bytesRead = input.read(buffer)

            if (bytesRead > 0) {
                data.writeEntityHeader(key, bytesRead)
                data.writeEntityData(buffer, bytesRead)
                Timber.d("Backed up $key ($bytesRead bytes)")
            }
        }
    }

    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor
    ) {
        Timber.d("onRestore called (app version: $appVersionCode)")

        // Restore shared preferences and other helpers
        super.onRestore(data, appVersionCode, newState)
    }
}
