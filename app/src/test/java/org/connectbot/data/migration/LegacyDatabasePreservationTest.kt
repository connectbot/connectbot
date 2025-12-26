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
import androidx.room.Room
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
import java.io.File

/**
 * Tests to verify that legacy database files (hosts.db and pubkeys.db)
 * remain intact when migration fails, allowing future versions to retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LegacyDatabasePreservationTest {

    private lateinit var context: Context
    private lateinit var hostsDbFile: File
    private lateinit var pubkeysDbFile: File
    private lateinit var hostsMigratedFile: File
    private lateinit var pubkeysMigratedFile: File
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(
        default = testDispatcher,
        io = testDispatcher,
        main = testDispatcher
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        hostsDbFile = context.getDatabasePath("hosts")
        pubkeysDbFile = context.getDatabasePath("pubkeys")
        hostsMigratedFile = context.getDatabasePath("hosts.migrated")
        pubkeysMigratedFile = context.getDatabasePath("pubkeys.migrated")

        // Clean up any existing files
        cleanupDatabaseFiles()
    }

    @After
    fun tearDown() {
        cleanupDatabaseFiles()
    }

    private fun createDatabaseMigrator(): DatabaseMigrator {
        val database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val legacyHostReader = LegacyHostDatabaseReader(context)
        val legacyPubkeyReader = LegacyPubkeyDatabaseReader(context)
        return DatabaseMigrator(context, database, legacyHostReader, legacyPubkeyReader, dispatchers)
    }

    @Test
    fun `legacy databases are NOT renamed when migration not attempted`() = runTest {
        // Create empty legacy databases
        createEmptyLegacyDatabase(hostsDbFile)
        createEmptyLegacyDatabase(pubkeysDbFile)

        val migrator = createDatabaseMigrator()

        // Check if migration is needed (doesn't run migration, just checks)
        migrator.isMigrationNeeded()

        // Verify original files still exist and haven't been renamed
        assertThat(hostsDbFile.exists()).isTrue()
        assertThat(pubkeysDbFile.exists()).isTrue()
        assertThat(hostsMigratedFile.exists()).isFalse()
        assertThat(pubkeysMigratedFile.exists()).isFalse()
    }


    @Test
    fun `legacy databases can be read again after failed migration`() = runTest {
        // Create a legacy database with some data
        createLegacyDatabaseWithTestData(hostsDbFile)
        createEmptyLegacyDatabase(pubkeysDbFile)

        val migrator = createDatabaseMigrator()

        // First migration attempt (will fail for some reason)
        val result1 = migrator.migrate()

        // Even if migration failed, we should be able to read the legacy database again
        val reader = LegacyHostDatabaseReader(context)
        val hosts = reader.readHosts()

        // Verify we can still read the original data
        assertThat(hosts).isNotEmpty()
        assertThat(hostsDbFile.exists()).isTrue()
    }




    @Test
    fun `partial write failure leaves legacy files intact`() = runTest {
        // This test verifies that even if some data is written to Room
        // but the transaction fails, legacy files remain untouched

        createLegacyDatabaseWithTestData(hostsDbFile)
        createEmptyLegacyDatabase(pubkeysDbFile)

        val originalHostsSize = hostsDbFile.length()
        val originalPubkeysSize = pubkeysDbFile.length()

        val migrator = createDatabaseMigrator()

        // Attempt migration (will fail)
        migrator.migrate()

        // Verify legacy database files have same size (weren't modified)
        assertThat(hostsDbFile.length()).isEqualTo(originalHostsSize)
        assertThat(pubkeysDbFile.length()).isEqualTo(originalPubkeysSize)

        // Verify files still exist
        assertThat(hostsDbFile.exists()).isTrue()
        assertThat(pubkeysDbFile.exists()).isTrue()
    }

    // Helper methods to create test databases

    private fun createEmptyLegacyDatabase(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS test (id INTEGER PRIMARY KEY)")
        db.close()
    }

    private fun createLegacyDatabaseWithTestData(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)

        // Create hosts table with basic structure
        db.execSQL("""
            CREATE TABLE hosts (
                _id INTEGER PRIMARY KEY,
                nickname TEXT UNIQUE,
                protocol TEXT,
                username TEXT,
                hostname TEXT,
                port INTEGER,
                hostkeyalgo TEXT,
                lastconnect INTEGER,
                color TEXT,
                usekeys TEXT,
                useauthagent TEXT,
                postlogin TEXT,
                pubkeyid INTEGER,
                wantsession TEXT,
                compression TEXT,
                encoding TEXT,
                stayconnected TEXT,
                fontsize INTEGER,
                scheme INTEGER,
                delkey TEXT,
                scrollbacklines INTEGER,
                usectrlaltasmeta TEXT,
                quickdisconnect TEXT
            )
        """)

        // Insert test data
        db.execSQL("""
            INSERT INTO hosts (nickname, protocol, username, hostname, port,
                             hostkeyalgo, lastconnect, color, usekeys, useauthagent,
                             postlogin, pubkeyid, wantsession, compression, encoding,
                             stayconnected, fontsize, scheme, delkey, scrollbacklines,
                             usectrlaltasmeta, quickdisconnect)
            VALUES ('test-host', 'ssh', 'user', 'example.com', 22,
                    NULL, 0, 'gray', 'false', NULL,
                    NULL, 0, 'true', 'false', 'UTF-8',
                    'false', 10, 1, 'BACKSPACE', 140, 'false', 'false')
        """)

        db.close()
    }

    private fun createLegacyDatabaseWithDuplicateNicknames(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)

        // Create hosts table
        db.execSQL("""
            CREATE TABLE hosts (
                _id INTEGER PRIMARY KEY,
                nickname TEXT,
                protocol TEXT,
                username TEXT,
                hostname TEXT,
                port INTEGER,
                hostkeyalgo TEXT,
                lastconnect INTEGER,
                color TEXT,
                usekeys TEXT,
                useauthagent TEXT,
                postlogin TEXT,
                pubkeyid INTEGER,
                wantsession TEXT,
                compression TEXT,
                encoding TEXT,
                stayconnected TEXT,
                fontsize INTEGER,
                scheme INTEGER,
                delkey TEXT,
                scrollbacklines INTEGER,
                usectrlaltasmeta TEXT,
                quickdisconnect TEXT
            )
        """)

        // Insert duplicate nicknames (no unique constraint in legacy DB)
        db.execSQL("""
            INSERT INTO hosts (nickname, protocol, username, hostname, port,
                             hostkeyalgo, lastconnect, color, usekeys, useauthagent,
                             postlogin, pubkeyid, wantsession, compression, encoding,
                             stayconnected, fontsize, scheme, delkey, scrollbacklines,
                             usectrlaltasmeta, quickdisconnect)
            VALUES ('duplicate', 'ssh', 'user', 'example.com', 22,
                    NULL, 0, 'gray', 'false', NULL,
                    NULL, 0, 'true', 'false', 'UTF-8',
                    'false', 10, 1, 'BACKSPACE', 140, 'false', 'false')
        """)

        db.execSQL("""
            INSERT INTO hosts (nickname, protocol, username, hostname, port,
                             hostkeyalgo, lastconnect, color, usekeys, useauthagent,
                             postlogin, pubkeyid, wantsession, compression, encoding,
                             stayconnected, fontsize, scheme, delkey, scrollbacklines,
                             usectrlaltasmeta, quickdisconnect)
            VALUES ('duplicate', 'ssh', 'user', 'example.com', 22,
                    NULL, 0, 'gray', 'false', NULL,
                    NULL, 0, 'true', 'false', 'UTF-8',
                    'false', 10, 1, 'BACKSPACE', 140, 'false', 'false')
        """)

        db.close()
    }

    private fun createLegacyDatabaseWithTestPubkeys(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)

        // Create pubkeys table
        db.execSQL("""
            CREATE TABLE pubkeys (
                _id INTEGER PRIMARY KEY,
                nickname TEXT UNIQUE,
                type TEXT,
                private BLOB,
                public BLOB,
                encrypted INTEGER,
                startup INTEGER,
                confirmuse INTEGER,
                lifetime INTEGER
            )
        """)

        // Insert test pubkey
        db.execSQL("""
            INSERT INTO pubkeys (nickname, type, private, public, encrypted, startup, confirmuse, lifetime)
            VALUES ('test-key', 'ssh-rsa', X'010203', X'040506', 0, 0, 0, 0)
        """)

        db.close()
    }

    private fun cleanupDatabaseFiles() {
        hostsDbFile.delete()
        pubkeysDbFile.delete()
        hostsMigratedFile.delete()
        pubkeysMigratedFile.delete()

        // Also delete -journal and -wal files
        File(hostsDbFile.absolutePath + "-journal").delete()
        File(hostsDbFile.absolutePath + "-wal").delete()
        File(pubkeysDbFile.absolutePath + "-journal").delete()
        File(pubkeysDbFile.absolutePath + "-wal").delete()
    }
}
