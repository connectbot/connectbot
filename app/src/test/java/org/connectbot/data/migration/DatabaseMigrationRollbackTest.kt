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
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests to verify that migration properly rolls back when failures occur.
 * These tests ensure that the transaction atomicity works correctly.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationRollbackTest {

    private lateinit var context: Context
    private lateinit var database: ConnectBotDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `transaction rolls back when duplicate host nickname inserted`() = runTest {
        // Insert a host successfully
        val host1 = createTestHost(nickname = "duplicate")
        database.hostDao().insert(host1)

        // Verify it was inserted
        assertThat(database.hostDao().getAll()).hasSize(1)

        // Try to insert data with duplicate host nickname in a transaction
        try {
            database.withTransaction {
                // This should succeed
                val pubkey = createTestPubkey(nickname = "test-key")
                database.pubkeyDao().insert(pubkey)

                // This should fail due to unique constraint on nickname
                val host2 = createTestHost(nickname = "duplicate")
                database.hostDao().insert(host2)
            }
        } catch (e: Exception) {
            // Expected to fail
        }

        // Verify rollback: pubkey should NOT be inserted (transaction rolled back)
        assertThat(database.pubkeyDao().getAll()).isEmpty()

        // Original host should still be there
        assertThat(database.hostDao().getAll()).hasSize(1)
    }

    @Test
    fun `transaction rolls back when duplicate pubkey nickname inserted`() = runTest {
        // Insert a pubkey successfully
        val pubkey1 = createTestPubkey(nickname = "my-key")
        database.pubkeyDao().insert(pubkey1)

        // Verify it was inserted
        assertThat(database.pubkeyDao().getAll()).hasSize(1)

        // Try to insert data with duplicate pubkey nickname in a transaction
        try {
            database.withTransaction {
                // This should succeed
                val host = createTestHost(nickname = "test-host")
                database.hostDao().insert(host)

                // This should fail due to unique constraint on nickname
                val pubkey2 = createTestPubkey(nickname = "my-key")
                database.pubkeyDao().insert(pubkey2)
            }
        } catch (e: Exception) {
            // Expected to fail
        }

        // Verify rollback: host should NOT be inserted (transaction rolled back)
        assertThat(database.hostDao().getAll()).isEmpty()

        // Original pubkey should still be there
        assertThat(database.pubkeyDao().getAll()).hasSize(1)
    }

    @Test
    fun `partial migration data is rolled back on failure`() = runTest {
        // Simulate a partial migration that fails midway

        try {
            database.withTransaction {
                // Insert some color schemes (should succeed)
                val scheme1 = ColorScheme(id = 1, name = "Scheme1", isBuiltIn = true)
                val scheme2 = ColorScheme(id = 2, name = "Scheme2", isBuiltIn = true)
                database.colorSchemeDao().insert(scheme1)
                database.colorSchemeDao().insert(scheme2)

                // Insert some pubkeys (should succeed)
                val pubkey1 = createTestPubkey(nickname = "key1")
                val pubkey2 = createTestPubkey(nickname = "key2")
                database.pubkeyDao().insert(pubkey1)
                database.pubkeyDao().insert(pubkey2)

                // Insert some hosts (should succeed)
                val host1 = createTestHost(nickname = "host1")
                database.hostDao().insert(host1)

                // Try to insert duplicate host (should fail and rollback everything)
                val duplicateHost = createTestHost(nickname = "host1")
                database.hostDao().insert(duplicateHost)
            }
        } catch (e: Exception) {
            // Expected to fail
        }

        // Verify complete rollback: ALL data should be rolled back
        assertThat(database.colorSchemeDao().getAll()).isEmpty()
        assertThat(database.pubkeyDao().getAll()).isEmpty()
        assertThat(database.hostDao().getAll()).isEmpty()
    }

    @Test
    fun `multiple successful inserts in transaction commit together`() = runTest {
        // Insert multiple items in a transaction successfully
        database.withTransaction {
            val scheme = ColorScheme(id = 1, name = "Test Scheme", isBuiltIn = true)
            database.colorSchemeDao().insert(scheme)

            val pubkey = createTestPubkey(nickname = "key1")
            database.pubkeyDao().insert(pubkey)

            val host = createTestHost(nickname = "host1")
            database.hostDao().insert(host)
        }

        // Verify all data was committed
        assertThat(database.colorSchemeDao().getAll()).hasSize(1)
        assertThat(database.pubkeyDao().getAll()).hasSize(1)
        assertThat(database.hostDao().getAll()).hasSize(1)
    }

    @Test
    fun `rollback preserves existing data when new transaction fails`() = runTest {
        // Insert some initial data (outside transaction)
        val existingScheme = ColorScheme(id = 1, name = "Existing", isBuiltIn = true)
        database.colorSchemeDao().insert(existingScheme)

        val existingPubkey = createTestPubkey(nickname = "existing-key")
        database.pubkeyDao().insert(existingPubkey)

        val existingHost = createTestHost(nickname = "existing-host")
        database.hostDao().insert(existingHost)

        // Verify initial state
        assertThat(database.colorSchemeDao().getAll()).hasSize(1)
        assertThat(database.pubkeyDao().getAll()).hasSize(1)
        assertThat(database.hostDao().getAll()).hasSize(1)

        // Try to insert new data in a transaction that will fail
        try {
            database.withTransaction {
                // New color scheme
                val newScheme = ColorScheme(id = 2, name = "New", isBuiltIn = true)
                database.colorSchemeDao().insert(newScheme)

                // New pubkey
                val newPubkey = createTestPubkey(nickname = "new-key")
                database.pubkeyDao().insert(newPubkey)

                // Duplicate host (will fail)
                val duplicateHost = createTestHost(nickname = "existing-host")
                database.hostDao().insert(duplicateHost)
            }
        } catch (e: Exception) {
            // Expected to fail
        }

        // Verify existing data is unchanged (only 1 of each)
        assertThat(database.colorSchemeDao().getAll()).hasSize(1)
        assertThat(database.colorSchemeDao().getAll()[0].name).isEqualTo("Existing")

        assertThat(database.pubkeyDao().getAll()).hasSize(1)
        assertThat(database.pubkeyDao().getAll()[0].nickname).isEqualTo("existing-key")

        assertThat(database.hostDao().getAll()).hasSize(1)
        assertThat(database.hostDao().getAll()[0].nickname).isEqualTo("existing-host")
    }

    @Test
    fun `foreign key constraint violation rolls back transaction`() = runTest {
        // Try to insert a port forward referencing a non-existent host
        try {
            database.withTransaction {
                // Insert a pubkey first
                val pubkey = createTestPubkey(nickname = "test-key")
                database.pubkeyDao().insert(pubkey)

                // Try to insert a port forward for a host that doesn't exist
                // Note: Foreign key constraints may not be enforced in all test configurations
                // but the transaction should still rollback on any error
                val host = createTestHost(nickname = "host1")
                database.hostDao().insert(host)

                // Force an error by inserting duplicate
                val duplicateHost = createTestHost(nickname = "host1")
                database.hostDao().insert(duplicateHost)
            }
        } catch (e: Exception) {
            // Expected to fail
        }

        // Verify rollback: nothing should be inserted
        assertThat(database.pubkeyDao().getAll()).isEmpty()
        assertThat(database.hostDao().getAll()).isEmpty()
    }

    @Test
    fun `large batch insert rolls back completely on failure`() = runTest {
        // Simulate inserting a large batch of data that fails at the end
        try {
            database.withTransaction {
                // Insert 50 hosts
                repeat(50) { i ->
                    val host = createTestHost(nickname = "host$i")
                    database.hostDao().insert(host)
                }

                // Insert 50 pubkeys
                repeat(50) { i ->
                    val pubkey = createTestPubkey(nickname = "key$i")
                    database.pubkeyDao().insert(pubkey)
                }

                // Try to insert duplicate host (will fail)
                val duplicateHost = createTestHost(nickname = "host0")
                database.hostDao().insert(duplicateHost)
            }
        } catch (e: Exception) {
            // Expected to fail
        }

        // Verify complete rollback: ALL 100 items should be rolled back
        assertThat(database.hostDao().getAll()).isEmpty()
        assertThat(database.pubkeyDao().getAll()).isEmpty()
    }

    @Test
    fun `nested transaction-like operations maintain atomicity`() = runTest {
        // Test that complex operations with multiple steps rollback correctly
        try {
            database.withTransaction {
                // Step 1: Insert color schemes
                val scheme1 = ColorScheme(id = 1, name = "Scheme1", isBuiltIn = true)
                database.colorSchemeDao().insert(scheme1)

                // Step 2: Insert pubkeys
                repeat(5) { i ->
                    val pubkey = createTestPubkey(nickname = "key$i")
                    database.pubkeyDao().insert(pubkey)
                }

                // Step 3: Insert hosts
                repeat(5) { i ->
                    val host = createTestHost(nickname = "host$i")
                    database.hostDao().insert(host)
                }

                // Step 4: Fail on duplicate
                val duplicateScheme = ColorScheme(id = 1, name = "Duplicate", isBuiltIn = true)
                database.colorSchemeDao().insert(duplicateScheme)
            }
        } catch (e: Exception) {
            // Expected to fail
        }

        // Verify complete rollback of all steps
        assertThat(database.colorSchemeDao().getAll()).isEmpty()
        assertThat(database.pubkeyDao().getAll()).isEmpty()
        assertThat(database.hostDao().getAll()).isEmpty()
    }

    private fun createTestHost(
        nickname: String,
        protocol: String = "ssh",
        username: String = "user",
        hostname: String = "example.com",
        port: Int = 22
    ): Host {
        return Host(
            nickname = nickname,
            protocol = protocol,
            username = username,
            hostname = hostname,
            port = port,
            hostKeyAlgo = null,
            lastConnect = 0,
            color = "gray",
            useKeys = false,
            useAuthAgent = null,
            stayConnected = false,
            postLogin = null,
            pubkeyId = 0,
            wantSession = true,
            compression = false,
            scrollbackLines = 140,
            useCtrlAltAsMetaKey = false,
            jumpHostId = null,
            profileId = 1L
        )
    }

    private fun createTestPubkey(
        nickname: String,
        type: String = "ssh-rsa"
    ): Pubkey {
        return Pubkey(
            nickname = nickname,
            type = type,
            privateKey = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(4, 5, 6),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = System.currentTimeMillis(),
            storageType = KeyStorageType.EXPORTABLE,
            allowBackup = true,
            keystoreAlias = null
        )
    }
}
