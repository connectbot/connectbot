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
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Integration test to reproduce issue #1806:
 * "Migration failed: UNIQUE constraint failed: profiles.id"
 *
 * This test reproduces the scenario where:
 * 1. User has the stable version with legacy databases (hosts, pubkeys)
 * 2. User upgrades to beta version which has Room database with profiles table
 * 3. Room database is created with default profile (ID=1) via onCreate callback
 * 4. Legacy migration runs and tries to insert another profile with ID=1
 * 5. Crash: UNIQUE constraint failed
 *
 * @see <a href="https://github.com/connectbot/connectbot/issues/1806">Issue #1806</a>
 */
@HiltAndroidTest
class ProfileConflictMigrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: ConnectBotDatabase

    @Inject
    lateinit var migrator: DatabaseMigrator

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        hiltRule.inject()

        // Clean up any existing legacy databases
        context.deleteDatabase("hosts")
        context.deleteDatabase("pubkeys")
        context.deleteDatabase("hosts.migrated")
        context.deleteDatabase("pubkeys.migrated")

        // Note: We intentionally do NOT call database.clearAllTables() here
        // because we want to simulate the real-world scenario where the Room
        // database already has a default profile from the onCreate callback.
    }

    @After
    fun tearDown() {
        // Clean up
        database.clearAllTables()
        context.deleteDatabase("hosts")
        context.deleteDatabase("pubkeys")
        context.deleteDatabase("hosts.migrated")
        context.deleteDatabase("pubkeys.migrated")
    }

    /**
     * Verifies fix for issue #1806: UNIQUE constraint failed on profiles.id during migration.
     *
     * This test verifies that the fix correctly prevents the migration from running
     * when Room already has data (including profiles), which avoids the UNIQUE constraint
     * violation that would occur if migration tried to insert profile ID=1 again.
     *
     * The scenario:
     * - User upgrades from stable (legacy DBs) to beta (Room with profiles)
     * - Room database is initialized with default profile ID=1
     * - Migration should be skipped because Room already has data (profiles)
     * - This prevents the UNIQUE constraint violation
     */
    @Test
    fun migrationSkippedWhenRoomHasProfiles() {
        runBlocking {
            try {
                Timber.d("Starting test: migrationSkippedWhenRoomHasProfiles")

                // Step 1: Ensure Room database has the default profile
                // The DI-provided database should already have this from onCreate callback
                val existingProfiles = database.profileDao().getAll()
                Timber.d("Existing profiles in Room database: ${existingProfiles.size}")
                assertThat(existingProfiles).isNotEmpty()
                assertThat(existingProfiles.any { it.id == 1L }).isTrue()

                // Verify hosts and pubkeys are empty
                val existingHosts = database.hostDao().getAll()
                val existingPubkeys = database.pubkeyDao().getAll()
                Timber.d("Existing hosts: ${existingHosts.size}, pubkeys: ${existingPubkeys.size}")
                assertThat(existingHosts).isEmpty()
                assertThat(existingPubkeys).isEmpty()

                // Step 2: Create empty legacy databases with correct schema
                // This simulates having legacy DBs from the stable version
                createEmptyLegacyHostsDatabase()
                createEmptyLegacyPubkeysDatabase()

                // Verify legacy databases exist
                val hostsDbFile = context.getDatabasePath("hosts")
                val pubkeysDbFile = context.getDatabasePath("pubkeys")
                assertThat(hostsDbFile.exists()).isTrue()
                assertThat(pubkeysDbFile.exists()).isTrue()

                // Step 3: Check if migration thinks it's needed
                // It should return FALSE because Room already has profiles (the fix for #1806)
                // Even though legacy DBs exist and hosts/pubkeys are empty, the presence of
                // profiles indicates Room has already been initialized, so migration should
                // be skipped to avoid UNIQUE constraint violation on profiles.id
                val migrationNeeded = migrator.isMigrationNeeded()
                Timber.d("Migration needed: $migrationNeeded")
                assertThat(migrationNeeded)
                    .describedAs("Migration should NOT be needed because Room already has profiles (fix for #1806)")
                    .isFalse()

                // Step 4: Run migrate() anyway - it should return Success with 0 items migrated
                // because isMigrationNeeded() returns false
                val result = migrator.migrate()

                Timber.d("Migration result: $result")

                // Step 5: Verify migration returned success (no-op) instead of failing
                assertThat(result)
                    .describedAs("Migration should succeed as a no-op when Room already has profiles")
                    .isInstanceOf(MigrationResult.Success::class.java)

                val success = result as MigrationResult.Success
                assertThat(success.hostsMigrated).isEqualTo(0)
                assertThat(success.pubkeysMigrated).isEqualTo(0)
                Timber.d("Migration correctly skipped - 0 items migrated")

            } catch (e: Exception) {
                Timber.e(e, "Test failed with exception")
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Creates an empty legacy hosts database with the schema expected by LegacyHostDatabaseReader.
     * This simulates the HostDatabase v27 schema.
     */
    private fun createEmptyLegacyHostsDatabase() {
        val dbFile = context.getDatabasePath("hosts")
        dbFile.parentFile?.mkdirs()

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Create hosts table with legacy schema
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS hosts (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nickname TEXT NOT NULL,
                    protocol TEXT NOT NULL DEFAULT 'ssh',
                    username TEXT NOT NULL DEFAULT '',
                    hostname TEXT NOT NULL,
                    port INTEGER NOT NULL DEFAULT 22,
                    hostkeyalgo TEXT,
                    lastconnect INTEGER NOT NULL DEFAULT 0,
                    color TEXT,
                    usekeys TEXT NOT NULL DEFAULT 'true',
                    useauthagent TEXT,
                    postlogin TEXT,
                    pubkeyid INTEGER NOT NULL DEFAULT -1,
                    wantsession TEXT NOT NULL DEFAULT 'true',
                    compression TEXT NOT NULL DEFAULT 'false',
                    encoding TEXT NOT NULL DEFAULT 'UTF-8',
                    stayconnected TEXT NOT NULL DEFAULT 'false',
                    quickdisconnect TEXT DEFAULT 'false',
                    fontsize INTEGER NOT NULL DEFAULT 10,
                    scheme INTEGER DEFAULT 1,
                    delkey TEXT NOT NULL DEFAULT 'DEL',
                    scrollbacklines INTEGER DEFAULT 140,
                    usectrlaltasmeta TEXT DEFAULT 'false'
                )
            """.trimIndent())

            // Create portforwards table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS portforwards (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hostid INTEGER NOT NULL,
                    nickname TEXT NOT NULL,
                    type TEXT NOT NULL DEFAULT 'local',
                    sourceport INTEGER NOT NULL,
                    destaddr TEXT NOT NULL,
                    destport INTEGER NOT NULL
                )
            """.trimIndent())

            // Create knownhosts table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS knownhosts (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hostid INTEGER NOT NULL,
                    hostkeyalgo TEXT NOT NULL,
                    hostkey BLOB NOT NULL
                )
            """.trimIndent())

            // Create colorSchemes table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS colorSchemes (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT
                )
            """.trimIndent())

            // Create colors table (color palette)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS colors (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scheme INTEGER NOT NULL,
                    number INTEGER NOT NULL,
                    value INTEGER NOT NULL
                )
            """.trimIndent())

            Timber.d("Created empty legacy hosts database at: ${dbFile.absolutePath}")
        } finally {
            db.close()
        }
    }

    /**
     * Creates an empty legacy pubkeys database with the schema expected by LegacyPubkeyDatabaseReader.
     * This simulates the PubkeyDatabase v2 schema.
     */
    private fun createEmptyLegacyPubkeysDatabase() {
        val dbFile = context.getDatabasePath("pubkeys")
        dbFile.parentFile?.mkdirs()

        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Create pubkeys table with legacy schema
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS pubkeys (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nickname TEXT NOT NULL,
                    type TEXT NOT NULL,
                    private BLOB NOT NULL,
                    public BLOB NOT NULL,
                    encrypted INTEGER NOT NULL DEFAULT 0,
                    startup INTEGER NOT NULL DEFAULT 0,
                    confirmuse INTEGER NOT NULL DEFAULT 0,
                    lifetime INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            Timber.d("Created empty legacy pubkeys database at: ${dbFile.absolutePath}")
        } finally {
            db.close()
        }
    }
}
