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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Unit tests for BackupFilter.
 *
 * These tests verify the filtering logic without requiring
 * actual backup/restore operations.
 */
@RunWith(AndroidJUnit4::class)
class BackupFilterTest {

    private lateinit var context: Context
    private lateinit var backupFilter: BackupFilter

    // Mock repositories for dependency injection
    private lateinit var mockHostRepository: HostRepository
    private lateinit var mockColorSchemeRepository: ColorSchemeRepository
    private lateinit var mockPubkeyRepository: PubkeyRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Initialize mock repositories
        mockHostRepository = mock(HostRepository::class.java)
        mockColorSchemeRepository = mock(ColorSchemeRepository::class.java)
        mockPubkeyRepository = mock(PubkeyRepository::class.java)

        backupFilter = BackupFilter(
            context,
            mockHostRepository,
            mockColorSchemeRepository,
            mockPubkeyRepository
        )
    }

    @Test
    fun filterBackupablePubkeys_AllBackupable_ReturnsAll() {
        val pubkeys = listOf(
            createPubkey("key1", allowBackup = true, storageType = KeyStorageType.EXPORTABLE),
            createPubkey("key2", allowBackup = true, storageType = KeyStorageType.EXPORTABLE),
            createPubkey("key3", allowBackup = true, storageType = KeyStorageType.EXPORTABLE)
        )

        val result = backupFilter.filterBackupablePubkeys(pubkeys)

        assertEquals(3, result.size)
        assertTrue(result.any { it.nickname == "key1" })
        assertTrue(result.any { it.nickname == "key2" })
        assertTrue(result.any { it.nickname == "key3" })
    }

    @Test
    fun filterBackupablePubkeys_MixedBackupSettings_FiltersCorrectly() {
        val pubkeys = listOf(
            createPubkey("backupable", allowBackup = true, storageType = KeyStorageType.EXPORTABLE),
            createPubkey("no-backup", allowBackup = false, storageType = KeyStorageType.EXPORTABLE),
            createPubkey("backupable2", allowBackup = true, storageType = KeyStorageType.EXPORTABLE)
        )

        val result = backupFilter.filterBackupablePubkeys(pubkeys)

        assertEquals(2, result.size)
        assertTrue(result.any { it.nickname == "backupable" })
        assertTrue(result.any { it.nickname == "backupable2" })
        assertFalse(result.any { it.nickname == "no-backup" })
    }

    @Test
    fun filterBackupablePubkeys_KeystoreKeys_AlwaysFiltered() {
        val pubkeys = listOf(
            createPubkey("exportable", allowBackup = true, storageType = KeyStorageType.EXPORTABLE),
            createPubkey(
                "keystore-yes",
                allowBackup = true,
                storageType = KeyStorageType.ANDROID_KEYSTORE
            ),
            createPubkey(
                "keystore-no",
                allowBackup = false,
                storageType = KeyStorageType.ANDROID_KEYSTORE
            )
        )

        val result = backupFilter.filterBackupablePubkeys(pubkeys)

        assertEquals(1, result.size)
        assertEquals("exportable", result[0].nickname)
        assertEquals(KeyStorageType.EXPORTABLE, result[0].storageType)
    }

    @Test
    fun filterBackupablePubkeys_AllNonBackupable_ReturnsEmpty() {
        val pubkeys = listOf(
            createPubkey(
                "no-backup-1",
                allowBackup = false,
                storageType = KeyStorageType.EXPORTABLE
            ),
            createPubkey(
                "no-backup-2",
                allowBackup = false,
                storageType = KeyStorageType.EXPORTABLE
            ),
            createPubkey(
                "keystore",
                allowBackup = true,
                storageType = KeyStorageType.ANDROID_KEYSTORE
            )
        )

        val result = backupFilter.filterBackupablePubkeys(pubkeys)

        assertEquals(0, result.size)
    }

    @Test
    fun filterBackupablePubkeys_EmptyList_ReturnsEmpty() {
        val pubkeys = emptyList<Pubkey>()

        val result = backupFilter.filterBackupablePubkeys(pubkeys)

        assertEquals(0, result.size)
    }

    @Test
    fun filterBackupablePubkeys_LargeDataset_FiltersCorrectly() {
        val pubkeys = mutableListOf<Pubkey>()

        // Add 50 backupable keys
        for (i in 1..50) {
            pubkeys.add(
                createPubkey(
                    "backupable-$i",
                    allowBackup = true,
                    storageType = KeyStorageType.EXPORTABLE
                )
            )
        }

        // Add 25 non-backupable keys
        for (i in 1..25) {
            pubkeys.add(
                createPubkey(
                    "no-backup-$i",
                    allowBackup = false,
                    storageType = KeyStorageType.EXPORTABLE
                )
            )
        }

        // Add 10 keystore keys
        for (i in 1..10) {
            pubkeys.add(
                createPubkey(
                    "keystore-$i",
                    allowBackup = true,
                    storageType = KeyStorageType.ANDROID_KEYSTORE
                )
            )
        }

        val result = backupFilter.filterBackupablePubkeys(pubkeys)

        assertEquals(50, result.size)
        assertTrue(result.all { it.allowBackup })
        assertTrue(result.all { it.storageType == KeyStorageType.EXPORTABLE })
    }

    @Test
    fun filterBackupablePubkeys_PreservesKeyData() {
        val originalKey = Pubkey(
            id = 123,
            nickname = "test-key",
            type = "RSA",
            privateKey = ByteArray(32) { 0x42 },
            publicKey = ByteArray(64) { 0x24 },
            encrypted = true,
            startup = true,
            confirmation = false,
            createdDate = 1234567890L,
            storageType = KeyStorageType.EXPORTABLE,
            allowBackup = true,
            keystoreAlias = null
        )

        val result = backupFilter.filterBackupablePubkeys(listOf(originalKey))

        assertEquals(1, result.size)
        val filteredKey = result[0]

        // Verify all fields are preserved
        assertEquals(originalKey.id, filteredKey.id)
        assertEquals(originalKey.nickname, filteredKey.nickname)
        assertEquals(originalKey.type, filteredKey.type)
        assertArrayEquals(originalKey.privateKey, filteredKey.privateKey)
        assertArrayEquals(originalKey.publicKey, filteredKey.publicKey)
        assertEquals(originalKey.encrypted, filteredKey.encrypted)
        assertEquals(originalKey.startup, filteredKey.startup)
        assertEquals(originalKey.confirmation, filteredKey.confirmation)
        assertEquals(originalKey.createdDate, filteredKey.createdDate)
        assertEquals(originalKey.storageType, filteredKey.storageType)
        assertEquals(originalKey.allowBackup, filteredKey.allowBackup)
    }

    @Test
    fun buildFilteredDatabase_WithBackupKeys_FiltersCorrectly() = runBlocking {
        // Prepare data for mocked repositories
        val host1 = Host(nickname = "test-host-1", protocol = "ssh", hostname = "example.com")
        val host2 = Host(nickname = "test-host-2", protocol = "ssh", hostname = "test.com")
        val backupableKey = createPubkey(
            "backupable-key",
            allowBackup = true,
            storageType = KeyStorageType.EXPORTABLE
        )
        val nonBackupableKey = createPubkey(
            "non-backupable-key",
            allowBackup = false,
            storageType = KeyStorageType.EXPORTABLE
        )
        val keystoreKey = createPubkey(
            "keystore-key",
            allowBackup = true,
            storageType = KeyStorageType.ANDROID_KEYSTORE
        )
        val colorScheme = ColorScheme(name = "test-scheme", isBuiltIn = false)

        // Mock repository behavior
        whenever(mockHostRepository.getHosts()).thenReturn(listOf(host1, host2))
        whenever(mockHostRepository.getPortForwardsForHost(host1.id)).thenReturn(emptyList())
        whenever(mockHostRepository.getKnownHostsForHost(host1.id)).thenReturn(emptyList())
        whenever(mockHostRepository.getPortForwardsForHost(host2.id)).thenReturn(emptyList())
        whenever(mockHostRepository.getKnownHostsForHost(host2.id)).thenReturn(emptyList())

        whenever(mockPubkeyRepository.getAll()).thenReturn(
            listOf(
                backupableKey,
                nonBackupableKey,
                keystoreKey
            )
        )
        whenever(mockColorSchemeRepository.getAllSchemes()).thenReturn(listOf(colorScheme))
        whenever(mockColorSchemeRepository.getSchemeColors(colorScheme.id)).thenReturn(intArrayOf())

        // Create temporary database file for backup
        val tempDbFile = File(context.cacheDir, "test_backup_temp.db")

        try {
            // Build filtered database with backupKeys=true
            backupFilter.buildFilteredDatabase(tempDbFile, backupKeys = true)

            // Open the temp database and verify contents
            val tempDb =
                Room.databaseBuilder(context, ConnectBotDatabase::class.java, tempDbFile.name)
                    .allowMainThreadQueries()
                    .build()

            try {
                // Verify hosts were all copied
                val hosts = tempDb.hostDao().getAll()
                assertEquals(2, hosts.size)
                assertTrue(hosts.any { it.nickname == "test-host-1" })
                assertTrue(hosts.any { it.nickname == "test-host-2" })

                // Verify only backupable key was copied
                val pubkeys = tempDb.pubkeyDao().getAll()
                assertEquals(1, pubkeys.size)
                assertEquals("backupable-key", pubkeys[0].nickname)
                assertEquals(KeyStorageType.EXPORTABLE, pubkeys[0].storageType)
                assertTrue(pubkeys[0].allowBackup)

                // Verify non-backupable keys were NOT copied
                assertFalse(pubkeys.any { it.nickname == "non-backupable-key" })
                assertFalse(pubkeys.any { it.nickname == "keystore-key" })

                // Verify color scheme was copied
                val colorSchemes = tempDb.colorSchemeDao().getAll()
                assertEquals(1, colorSchemes.size)
                assertEquals("test-scheme", colorSchemes[0].name)
            } finally {
                tempDb.close()
            }
        } finally {
            // Clean up temp database
            backupFilter.cleanupTempDatabase(tempDbFile)
            assertFalse(tempDbFile.exists())
        }
    }

    @Test
    fun buildFilteredDatabase_WithoutBackupKeys_ExcludesAllKeys() = runBlocking {
        // Add test host
        val host = Host(nickname = "test-host", protocol = "ssh", hostname = "example.com")

        // Add test pubkeys
        val key1 =
            createPubkey("key-1", allowBackup = true, storageType = KeyStorageType.EXPORTABLE)
        val key2 =
            createPubkey("key-2", allowBackup = true, storageType = KeyStorageType.EXPORTABLE)

        whenever(mockHostRepository.getHosts()).thenReturn(listOf(host))
        whenever(mockHostRepository.getPortForwardsForHost(host.id)).thenReturn(emptyList())
        whenever(mockHostRepository.getKnownHostsForHost(host.id)).thenReturn(emptyList())
        whenever(mockPubkeyRepository.getAll()).thenReturn(
            listOf(
                key1,
                key2
            )
        )
        whenever(mockColorSchemeRepository.getAllSchemes()).thenReturn(emptyList()) // No color schemes to backup

        // Create temporary database file for backup
        val tempDbFile = File(context.cacheDir, "test_backup_no_keys.db")

        try {
            // Build filtered database with backupKeys=false
            backupFilter.buildFilteredDatabase(tempDbFile, backupKeys = false)

            // Open the temp database and verify contents
            val tempDb =
                Room.databaseBuilder(context, ConnectBotDatabase::class.java, tempDbFile.name)
                    .allowMainThreadQueries()
                    .build()

            try {
                // Verify host was copied
                val hosts = tempDb.hostDao().getAll()
                assertEquals(1, hosts.size)

                // Verify NO pubkeys were copied
                val pubkeys = tempDb.pubkeyDao().getAll()
                assertEquals(0, pubkeys.size)
            } finally {
                tempDb.close()
            }
        } finally {
            // Clean up temp database
            backupFilter.cleanupTempDatabase(tempDbFile)
            assertFalse(tempDbFile.exists())
        }
    }

    // Helper function to create test pubkeys
    private fun createPubkey(
        nickname: String,
        allowBackup: Boolean,
        storageType: KeyStorageType
    ): Pubkey = Pubkey(
        nickname = nickname,
        type = "RSA",
        privateKey = ByteArray(16),
        publicKey = ByteArray(16),
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = System.currentTimeMillis(),
        storageType = storageType,
        allowBackup = allowBackup
    )
}
