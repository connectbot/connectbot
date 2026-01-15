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
import android.database.sqlite.SQLiteConstraintException
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

/**
 * Test to reproduce issue #1839: Database migration failure with
 * "UNIQUE constraint failed: profiles.id" error.
 *
 * The bug occurs when:
 * 1. User upgrades from old ConnectBot (legacy databases) to new beta (Room database)
 * 2. Legacy database has a host with custom terminal settings (non-default fontSize, encoding, etc.)
 * 3. Migration creates 2 profiles: Default (ID=1) + custom settings profile (ID=2)
 * 4. During writeToRoomDatabase(), the first Room access triggers lazy initialization
 * 5. Room's onCreate callback inserts a default profile with auto-generated ID (becomes 1)
 * 6. Migration then tries to insert its default profile with explicit ID=1 → CONFLICT!
 *
 * @see <a href="https://github.com/connectbot/connectbot/issues/1839">Issue #1839</a>
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Issue1839MigrationTest {

    private lateinit var context: Context
    private val roomDbName = "connectbot_test_1839.db"
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
     * This test reproduces the exact scenario from issue #1839:
     * - 1 host with custom terminal settings (fontSize=12 instead of default 10)
     * - 1 pubkey
     * - Migration creates 2 profiles (Default + Migrated Profile 2)
     * - Conflict occurs when Room onCreate callback inserts profile ID=1
     *   before migration tries to insert its profile with ID=1
     *
     * Expected: Migration should complete successfully without constraint violation.
     * Actual (bug): SQLiteConstraintException: UNIQUE constraint failed: profiles.id
     */
    @Test
    fun migration_withCustomTerminalSettings_shouldNotCauseProfileIdConflict() = runTest {
        // Step 1: Create legacy database with host that has CUSTOM terminal settings
        // This causes migration to create 2 profiles (Default + custom)
        createLegacyHostsDatabaseWithCustomSettings()
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

            // Step 5: Verify migration is needed
            val isMigrationNeeded = migrator.isMigrationNeeded()
            assertThat(isMigrationNeeded)
                .describedAs("Migration should be needed when legacy databases have data and Room doesn't exist")
                .isTrue()

            // Step 6: Run migration - this is where the bug manifests
            // The bug: Room onCreate callback inserts profile ID=1, then migration
            // tries to insert another profile with ID=1, causing a constraint violation
            val result = migrator.migrate()

            // Step 7: Verify migration succeeded
            assertThat(result)
                .describedAs(
                    "Migration should succeed without UNIQUE constraint violation on profiles.id. " +
                        "Bug #1839 causes this to fail because Room's onCreate callback inserts " +
                        "a default profile with ID=1 before migration inserts its profile with ID=1."
                )
                .isInstanceOf(MigrationResult.Success::class.java)

            // Verify the migrated data
            val success = result as MigrationResult.Success
            assertThat(success.hostsMigrated).isEqualTo(1)
            assertThat(success.pubkeysMigrated).isEqualTo(1)

            // Verify profiles were created correctly
            val profiles = database.profileDao().getAll()
            assertThat(profiles).isNotEmpty()

            // Verify the host was migrated
            val hosts = database.hostDao().getAll()
            assertThat(hosts).hasSize(1)
        } finally {
            database.close()
        }
    }

    /**
     * This test demonstrates that the bug only occurs when legacy hosts have
     * NON-DEFAULT terminal settings. With default settings, only one profile
     * is created and there's no conflict because the migration's profile ID=1
     * would match the onCreate callback's profile.
     *
     * However, even this scenario has a race condition issue.
     */
    @Test
    fun migration_withDefaultTerminalSettings_shouldAlsoHandleProfileIdConflict() = runTest {
        // Create legacy database with host using DEFAULT terminal settings
        createLegacyHostsDatabaseWithDefaultSettings()
        createLegacyPubkeysDatabase()

        val roomDbFile = context.getDatabasePath(roomDbName)
        assertThat(roomDbFile.exists()).isFalse()

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
            val legacyHostReader = LegacyHostDatabaseReader(context)
            val legacyPubkeyReader = LegacyPubkeyDatabaseReader(context)
            val migrator = DatabaseMigrator(context, database, legacyHostReader, legacyPubkeyReader, dispatchers)

            val isMigrationNeeded = migrator.isMigrationNeeded()
            assertThat(isMigrationNeeded).isTrue()

            // Even with default settings, there could be a conflict because:
            // - onCreate inserts profile with auto-generated ID (becomes 1)
            // - Migration inserts profile with explicit ID=1
            val result = migrator.migrate()

            assertThat(result)
                .describedAs("Migration should succeed even when Room onCreate creates a default profile")
                .isInstanceOf(MigrationResult.Success::class.java)
        } finally {
            database.close()
        }
    }

    /**
     * Creates a legacy hosts database with ONE host that has CUSTOM terminal settings.
     * This triggers the bug because migration will create 2 profiles:
     * 1. Default profile (ID=1)
     * 2. Migrated Profile 2 (ID=2) for the custom settings
     *
     * Settings that differ from default (fontSize=10, encoding=UTF-8, delKey=del):
     * - fontSize=12 (custom)
     */
    private fun createLegacyHostsDatabaseWithCustomSettings() {
        val dbFile = context.getDatabasePath("hosts")
        dbFile.parentFile?.mkdirs()

        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Create the hosts table with the legacy schema (version 27)
            // Note: Column names must match what LegacyHostDatabaseReader expects
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hosts (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nickname TEXT NOT NULL,
                    protocol TEXT NOT NULL DEFAULT 'ssh',
                    username TEXT NOT NULL DEFAULT '',
                    hostname TEXT NOT NULL,
                    port INTEGER NOT NULL DEFAULT 22,
                    hostkeyalgo TEXT,
                    lastconnect INTEGER DEFAULT 0,
                    color TEXT,
                    usekeys TEXT DEFAULT 'true',
                    useauthagent TEXT,
                    postlogin TEXT,
                    pubkeyid INTEGER DEFAULT -1,
                    wantsession TEXT DEFAULT 'true',
                    compression TEXT DEFAULT 'false',
                    encoding TEXT DEFAULT 'UTF-8',
                    stayconnected TEXT DEFAULT 'false',
                    quickdisconnect TEXT DEFAULT 'false',
                    fontsize INTEGER DEFAULT 10,
                    scheme INTEGER DEFAULT -1,
                    delkey TEXT DEFAULT 'del',
                    scrollbacklines INTEGER DEFAULT 140,
                    usectrlaltasmeta TEXT DEFAULT 'false'
                )
                """.trimIndent()
            )

            // Insert a host with CUSTOM terminal settings (fontSize=12 instead of default 10)
            // This causes migration to create a second profile for these non-default settings
            db.execSQL(
                """
                INSERT INTO hosts (nickname, protocol, username, hostname, port, usekeys, fontsize, encoding, delkey, scheme, wantsession, compression, stayconnected)
                VALUES ('ubuntu-server', 'ssh', 'user', 'server.example.com', 22, 'true', 12, 'UTF-8', 'del', -1, 'true', 'false', 'false')
                """.trimIndent()
            )

            // Create other required tables
            createSupportingTables(db)

            // Create a known host entry (as mentioned in issue: 1 known host)
            db.execSQL(
                """
                INSERT INTO knownhosts (hostid, hostname, port, hostkeyalgo, hostkey)
                VALUES (1, 'server.example.com', 22, 'ssh-ed25519', X'00000000')
                """.trimIndent()
            )
        } finally {
            db.close()
        }
    }

    /**
     * Creates a legacy hosts database with ONE host that has DEFAULT terminal settings.
     */
    private fun createLegacyHostsDatabaseWithDefaultSettings() {
        val dbFile = context.getDatabasePath("hosts")
        dbFile.parentFile?.mkdirs()

        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            // Create the hosts table with the legacy schema (version 27)
            // Note: Column names must match what LegacyHostDatabaseReader expects
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS hosts (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nickname TEXT NOT NULL,
                    protocol TEXT NOT NULL DEFAULT 'ssh',
                    username TEXT NOT NULL DEFAULT '',
                    hostname TEXT NOT NULL,
                    port INTEGER NOT NULL DEFAULT 22,
                    hostkeyalgo TEXT,
                    lastconnect INTEGER DEFAULT 0,
                    color TEXT,
                    usekeys TEXT DEFAULT 'true',
                    useauthagent TEXT,
                    postlogin TEXT,
                    pubkeyid INTEGER DEFAULT -1,
                    wantsession TEXT DEFAULT 'true',
                    compression TEXT DEFAULT 'false',
                    encoding TEXT DEFAULT 'UTF-8',
                    stayconnected TEXT DEFAULT 'false',
                    quickdisconnect TEXT DEFAULT 'false',
                    fontsize INTEGER DEFAULT 10,
                    scheme INTEGER DEFAULT -1,
                    delkey TEXT DEFAULT 'del',
                    scrollbacklines INTEGER DEFAULT 140,
                    usectrlaltasmeta TEXT DEFAULT 'false'
                )
                """.trimIndent()
            )

            // Insert a host with DEFAULT terminal settings (fontSize=10)
            // Migration will only create 1 profile (the Default profile)
            db.execSQL(
                """
                INSERT INTO hosts (nickname, protocol, username, hostname, port, usekeys, fontsize, encoding, delkey, scheme, wantsession, compression, stayconnected)
                VALUES ('test-server', 'ssh', 'testuser', 'test.example.com', 22, 'true', 10, 'UTF-8', 'del', -1, 'true', 'false', 'false')
                """.trimIndent()
            )

            createSupportingTables(db)
        } finally {
            db.close()
        }
    }

    /**
     * Creates supporting tables for the legacy hosts database.
     * Table and column names must match what LegacyHostDatabaseReader expects.
     */
    private fun createSupportingTables(db: android.database.sqlite.SQLiteDatabase) {
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

        // colorSchemes table (legacy name, used by LegacyHostDatabaseReader.readColorSchemes())
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS colorSchemes (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT
            )
            """.trimIndent()
        )

        // colors table (legacy name, used by LegacyHostDatabaseReader.readColorPalettes())
        // Columns: scheme, number, value
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS colors (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                scheme INTEGER NOT NULL,
                number INTEGER NOT NULL,
                value INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    /**
     * Creates a legacy pubkeys database with ONE SSH key.
     * This matches the issue scenario: 1 host, 1 pubkey, 1 known host.
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

            // Insert one Ed25519 pubkey (as mentioned in issue: Ed25519 public keys)
            db.execSQL(
                """
                INSERT INTO pubkeys (nickname, type, private, public, encrypted, startup, confirmuse)
                VALUES ('my-ed25519-key', 'ED25519', X'010203', X'040506', 0, 1, 0)
                """.trimIndent()
            )
        } finally {
            db.close()
        }
    }
}
