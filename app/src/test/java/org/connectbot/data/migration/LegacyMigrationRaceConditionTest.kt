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
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.di.CoroutineDispatchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Test to verify the root cause of issue #1623: SSH keys disappearing after upgrade.
 *
 * The bug is a race condition in DatabaseMigrator.isMigrationNeeded():
 * 1. User upgrades from old ConnectBot (legacy SQLite databases) to new beta (Room database)
 * 2. isMigrationNeeded() checks if Room database has any data
 * 3. This check triggers lazy initialization of Room, which fires the onCreate callback
 * 4. The onCreate callback inserts a default profile
 * 5. The check sees the default profile and concludes "Room has data, skip migration"
 * 6. User's legacy pubkeys and hosts are never migrated
 *
 * This test reproduces the exact production scenario to verify the bug.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LegacyMigrationRaceConditionTest {

    private lateinit var context: Context
    private val roomDbName = "connectbot_test_race.db"
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Clean up any existing databases
        context.deleteDatabase(roomDbName)
        context.deleteDatabase("hosts")
        context.deleteDatabase("pubkeys")
        context.deleteDatabase("hosts.migrated")
        context.deleteDatabase("pubkeys.migrated")
    }

    @After
    fun tearDown() {
        // Clean up
        context.deleteDatabase(roomDbName)
        context.deleteDatabase("hosts")
        context.deleteDatabase("pubkeys")
        context.deleteDatabase("hosts.migrated")
        context.deleteDatabase("pubkeys.migrated")
    }

    /**
     * This test reproduces the exact scenario from issue #1623.
     *
     * Expected behavior: When legacy databases exist and Room database doesn't exist yet,
     * isMigrationNeeded() should return true.
     *
     * Actual behavior (the bug): isMigrationNeeded() returns false because checking the
     * Room database causes Room to create a default profile, making it appear that Room
     * already has data.
     */
    @Test
    fun isMigrationNeeded_withLegacyDatabases_shouldReturnTrue() = runTest {
        // Step 1: Create legacy database files (simulating an upgrade from old ConnectBot)
        createLegacyHostsDatabase()
        createLegacyPubkeysDatabase()

        // Verify legacy databases exist
        val hostsDbFile = context.getDatabasePath("hosts")
        val pubkeysDbFile = context.getDatabasePath("pubkeys")
        assertThat(hostsDbFile.exists()).isTrue()
        assertThat(pubkeysDbFile.exists()).isTrue()

        // Step 2: Verify Room database file does NOT exist yet
        val roomDbFile = context.getDatabasePath(roomDbName)
        assertThat(roomDbFile.exists()).isFalse()

        // Step 3: Create Room database with the EXACT same configuration as production
        // This includes the onCreate callback that inserts a default profile
        val database = Room.databaseBuilder(
            context,
            ConnectBotDatabase::class.java,
            roomDbName
        )
            .addMigrations(ConnectBotDatabase.MIGRATION_4_5)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // This is the exact same code from DatabaseModule.kt
                    // It creates a default profile when the database is first created
                    Timber.d("Room onCreate callback - inserting default profile")
                    db.execSQL(
                        """
                        INSERT INTO profiles (name, color_scheme_id, font_size, del_key, encoding, emulation)
                        VALUES ('Default', -1, 10, 'del', 'UTF-8', 'xterm-256color')
                        """.trimIndent()
                    )
                }
            })
            .allowMainThreadQueries()
            .build()

        try {
            // Step 4: Create the migrator (same as production)
            val legacyHostReader = LegacyHostDatabaseReader(context)
            val legacyPubkeyReader = LegacyPubkeyDatabaseReader(context)
            val migrator = DatabaseMigrator(context, database, legacyHostReader, legacyPubkeyReader, dispatchers)

            // Step 5: Call isMigrationNeeded() - this is where the bug manifests
            // The bug: This call triggers Room lazy initialization, which fires onCreate,
            // which inserts a default profile, which makes the check fail
            val isMigrationNeeded = migrator.isMigrationNeeded()

            // Step 6: Assert the expected behavior
            // BUG: Due to the race condition, this returns FALSE when it should return TRUE
            // The assertion below will FAIL until the bug is fixed
            assertThat(isMigrationNeeded)
                .describedAs(
                    "isMigrationNeeded() should return true when legacy databases exist " +
                        "and Room database was just created. The bug causes it to return false " +
                        "because Room's onCreate callback inserts a default profile before " +
                        "the migration check completes."
                )
                .isTrue()
        } finally {
            database.close()
        }
    }

    /**
     * This test verifies that the migration check incorrectly sees a non-empty profiles table.
     *
     * This demonstrates the mechanism of the bug: the profiles table is populated by Room's
     * onCreate callback before isMigrationNeeded() can check if migration is needed.
     */
    @Test
    fun roomOnCreate_insertsDefaultProfile_beforeMigrationCheck() = runTest {
        // Create legacy databases
        createLegacyHostsDatabase()
        createLegacyPubkeysDatabase()

        // Verify Room database doesn't exist
        val roomDbFile = context.getDatabasePath(roomDbName)
        assertThat(roomDbFile.exists()).isFalse()

        // Create Room database with production-like configuration
        val database = Room.databaseBuilder(
            context,
            ConnectBotDatabase::class.java,
            roomDbName
        )
            .addMigrations(ConnectBotDatabase.MIGRATION_4_5)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL(
                        """
                        INSERT INTO profiles (name, color_scheme_id, font_size, del_key, encoding, emulation)
                        VALUES ('Default', -1, 10, 'del', 'UTF-8', 'xterm-256color')
                        """.trimIndent()
                    )
                }
            })
            .allowMainThreadQueries()
            .build()

        try {
            // This is the first database access - triggers onCreate
            val profiles = database.profileDao().getAll()

            // The bug mechanism: profiles table already has the default profile
            // even though we never explicitly inserted it
            assertThat(profiles).isNotEmpty()
            assertThat(profiles.first().name).isEqualTo("Default")

            // And hosts/pubkeys are empty (legacy data was never migrated)
            val hosts = database.hostDao().getAll()
            val pubkeys = database.pubkeyDao().getAll()
            assertThat(hosts).isEmpty()
            assertThat(pubkeys).isEmpty()

            // This is the exact scenario that causes the bug:
            // - Legacy databases exist with user data
            // - Room database was just created
            // - profiles table has a default profile (from onCreate)
            // - hosts and pubkeys tables are empty
            // - isMigrationNeeded() checks: hosts.isEmpty && pubkeys.isEmpty && profiles.isEmpty
            // - profiles.isEmpty is FALSE (because of the default profile)
            // - So isMigrationNeeded() returns false, skipping migration
        } finally {
            database.close()
        }
    }

    /**
     * Creates a minimal legacy hosts database with one host entry.
     * This simulates the database structure from the old ConnectBot version.
     */
    private fun createLegacyHostsDatabase() {
        val dbFile = context.getDatabasePath("hosts")
        dbFile.parentFile?.mkdirs()

        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Create the hosts table with the legacy schema (version 27)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hosts (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nickname TEXT NOT NULL,
                    protocol TEXT NOT NULL DEFAULT 'ssh',
                    username TEXT NOT NULL DEFAULT '',
                    hostname TEXT NOT NULL,
                    port INTEGER NOT NULL DEFAULT 22,
                    hostkey TEXT,
                    lastconnect INTEGER DEFAULT 0,
                    color TEXT,
                    usekeys INTEGER DEFAULT 1,
                    useauthagent TEXT,
                    postlogin TEXT,
                    pubkeyid INTEGER DEFAULT -1,
                    wantsession INTEGER DEFAULT 1,
                    compression INTEGER DEFAULT 0,
                    encoding TEXT DEFAULT 'UTF-8',
                    stayconnected INTEGER DEFAULT 0,
                    quickdisconnect INTEGER DEFAULT 0,
                    fontsize INTEGER DEFAULT 10,
                    colorscheme INTEGER DEFAULT 1,
                    delkey TEXT DEFAULT 'DEL',
                    scrollback INTEGER DEFAULT 140,
                    ctrl_alt_meta INTEGER DEFAULT 0
                )
                """.trimIndent()
            )

            // Insert a sample host
            db.execSQL(
                """
                INSERT INTO hosts (nickname, protocol, username, hostname, port, usekeys)
                VALUES ('test-server', 'ssh', 'testuser', 'test.example.com', 22, 1)
                """.trimIndent()
            )

            // Create other tables that might be expected
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS portforwards (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hostid INTEGER NOT NULL,
                    nickname TEXT NOT NULL,
                    type TEXT NOT NULL DEFAULT 'local',
                    sourceport INTEGER NOT NULL,
                    destaddr TEXT NOT NULL,
                    destport INTEGER NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS knownhosts (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    hostid INTEGER,
                    hostname TEXT NOT NULL,
                    port INTEGER NOT NULL,
                    hostkeyalgo TEXT NOT NULL,
                    hostkey BLOB NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS color_schemes (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS color_palette (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    scheme_id INTEGER NOT NULL,
                    color_index INTEGER NOT NULL,
                    color INTEGER NOT NULL
                )
                """.trimIndent()
            )
        } finally {
            db.close()
        }
    }

    /**
     * Creates a minimal legacy pubkeys database with one key entry.
     * This simulates the database structure from the old ConnectBot version.
     */
    private fun createLegacyPubkeysDatabase() {
        val dbFile = context.getDatabasePath("pubkeys")
        dbFile.parentFile?.mkdirs()

        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Create the pubkeys table with the legacy schema (version 2)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pubkeys (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nickname TEXT NOT NULL,
                    type TEXT NOT NULL,
                    private BLOB NOT NULL,
                    public BLOB NOT NULL,
                    encrypted INTEGER DEFAULT 0,
                    startup INTEGER DEFAULT 0,
                    confirmuse INTEGER DEFAULT 0,
                    lifetime INTEGER DEFAULT 0
                )
                """.trimIndent()
            )

            // Insert a sample pubkey
            db.execSQL(
                """
                INSERT INTO pubkeys (nickname, type, private, public, encrypted, startup, confirmuse)
                VALUES ('my-test-key', 'RSA', X'010203', X'040506', 0, 1, 0)
                """.trimIndent()
            )
        } finally {
            db.close()
        }
    }
}
