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

package org.connectbot.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PubkeyDaoTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var pubkeyDao: PubkeyDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pubkeyDao = database.pubkeyDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrievePubkey() = runTest {
        val pubkey = createTestPubkey(nickname = "test-key")

        val id = pubkeyDao.insert(pubkey)
        assertThat(id).isGreaterThan(0)

        val retrieved = pubkeyDao.getById(id)
        assertThat(retrieved).isNotNull
        assertThat(retrieved?.nickname).isEqualTo("test-key")
        assertThat(retrieved?.type).isEqualTo("ssh-rsa")
    }

    @Test
    fun observeAllPubkeys() = runTest {
        val key1 = createTestPubkey(nickname = "key-1")
        val key2 = createTestPubkey(nickname = "key-2")
        val key3 = createTestPubkey(nickname = "key-3")

        pubkeyDao.insert(key1)
        pubkeyDao.insert(key2)
        pubkeyDao.insert(key3)

        val keys = pubkeyDao.observeAll().first()
        assertThat(keys).hasSize(3)
        assertThat(keys.map { it.nickname }).containsExactly("key-1", "key-2", "key-3")
    }

    @Test
    fun getBackupableKeysOnly() = runTest {
        val backupable1 = createTestPubkey(nickname = "backup-1", allowBackup = true)
        val backupable2 = createTestPubkey(nickname = "backup-2", allowBackup = true)
        val nonBackupable = createTestPubkey(nickname = "no-backup", allowBackup = false)

        pubkeyDao.insert(backupable1)
        pubkeyDao.insert(backupable2)
        pubkeyDao.insert(nonBackupable)

        val backupableKeys = pubkeyDao.getBackupable()
        assertThat(backupableKeys).hasSize(2)
        assertThat(backupableKeys.map { it.nickname }).containsExactlyInAnyOrder("backup-1", "backup-2")
        assertThat(backupableKeys.all { it.allowBackup }).isTrue()
    }

    @Test
    fun getExportableKeysOnly() = runTest {
        val exportable1 = createTestPubkey(
            nickname = "export-1",
            storageType = KeyStorageType.EXPORTABLE
        )
        val exportable2 = createTestPubkey(
            nickname = "export-2",
            storageType = KeyStorageType.EXPORTABLE
        )
        val keystore = createTestPubkey(
            nickname = "keystore-1",
            storageType = KeyStorageType.ANDROID_KEYSTORE,
            keystoreAlias = "alias1"
        )

        pubkeyDao.insert(exportable1)
        pubkeyDao.insert(exportable2)
        pubkeyDao.insert(keystore)

        val exportableKeys = pubkeyDao.getExportable()
        assertThat(exportableKeys).hasSize(2)
        assertThat(exportableKeys.map { it.nickname }).containsExactlyInAnyOrder("export-1", "export-2")
        assertThat(exportableKeys.all { it.storageType == KeyStorageType.EXPORTABLE }).isTrue()
    }

    @Test
    fun updateBackupPermission() = runTest {
        val pubkey = createTestPubkey(nickname = "test-key", allowBackup = true)
        val id = pubkeyDao.insert(pubkey)

        // Verify initial state
        val initial = pubkeyDao.getById(id)
        assertThat(initial?.allowBackup).isTrue()

        // Update backup permission
        pubkeyDao.updateBackupPermission(id, false)

        // Verify update
        val updated = pubkeyDao.getById(id)
        assertThat(updated?.allowBackup).isFalse()
    }

    @Test
    fun updatePubkey() = runTest {
        val pubkey = createTestPubkey(nickname = "original")
        val id = pubkeyDao.insert(pubkey)

        val updated = pubkey.copy(id = id, nickname = "updated", encrypted = true)
        pubkeyDao.update(updated)

        val retrieved = pubkeyDao.getById(id)
        assertThat(retrieved?.nickname).isEqualTo("updated")
        assertThat(retrieved?.encrypted).isTrue()
    }

    @Test
    fun deletePubkey() = runTest {
        val pubkey = createTestPubkey(nickname = "to-delete")
        val id = pubkeyDao.insert(pubkey)

        // Verify it exists
        assertThat(pubkeyDao.getById(id)).isNotNull()

        // Delete it
        pubkeyDao.delete(pubkey.copy(id = id))

        // Verify it's gone
        assertThat(pubkeyDao.getById(id)).isNull()
    }

    @Test
    fun getByNickname() = runTest {
        val pubkey = createTestPubkey(nickname = "unique-name")
        pubkeyDao.insert(pubkey)

        val retrieved = pubkeyDao.getByNickname("unique-name")
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.nickname).isEqualTo("unique-name")
    }

    @Test
    fun getByNicknameReturnsNullForNonExistent() = runTest {
        val retrieved = pubkeyDao.getByNickname("does-not-exist")
        assertThat(retrieved).isNull()
    }

    @Test
    fun observeByIdReturnsFlowUpdates() = runTest {
        val pubkey = createTestPubkey(nickname = "watch-me")
        val id = pubkeyDao.insert(pubkey)

        val flow = pubkeyDao.observeById(id)

        // Initial value
        val initial = flow.first()
        assertThat(initial?.nickname).isEqualTo("watch-me")
        assertThat(initial?.encrypted).isFalse()

        // Update and verify flow emits new value
        pubkeyDao.update(pubkey.copy(id = id, encrypted = true))

        val updated = flow.first()
        assertThat(updated?.encrypted).isTrue()
    }

    @Test
    fun privateKeyCanBeNull() = runTest {
        val pubkey = createTestPubkey(nickname = "no-private", privateKey = null)
        val id = pubkeyDao.insert(pubkey)

        val retrieved = pubkeyDao.getById(id)
        assertThat(retrieved?.privateKey).isNull()
        assertThat(retrieved?.publicKey).isNotNull()
    }

    @Test
    fun byteArrayFieldsStoredCorrectly() = runTest {
        val privateKeyBytes = byteArrayOf(1, 2, 3, 4, 5)
        val publicKeyBytes = byteArrayOf(10, 20, 30, 40, 50)

        val pubkey = createTestPubkey(
            nickname = "byte-test",
            privateKey = privateKeyBytes,
            publicKey = publicKeyBytes
        )
        val id = pubkeyDao.insert(pubkey)

        val retrieved = pubkeyDao.getById(id)
        assertThat(retrieved?.privateKey).isEqualTo(privateKeyBytes)
        assertThat(retrieved?.publicKey).isEqualTo(publicKeyBytes)
    }

    @Test
    fun startupAndConfirmationFlags() = runTest {
        val startup = createTestPubkey(nickname = "startup", startup = true, confirmation = false)
        val confirm = createTestPubkey(nickname = "confirm", startup = false, confirmation = true)

        pubkeyDao.insert(startup)
        pubkeyDao.insert(confirm)

        val startupKey = pubkeyDao.getByNickname("startup")
        assertThat(startupKey?.startup).isTrue()
        assertThat(startupKey?.confirmation).isFalse()

        val confirmKey = pubkeyDao.getByNickname("confirm")
        assertThat(confirmKey?.startup).isFalse()
        assertThat(confirmKey?.confirmation).isTrue()
    }

    @Test
    fun createdDateIsStored() = runTest {
        val now = System.currentTimeMillis()
        val pubkey = createTestPubkey(nickname = "dated", createdDate = now)
        val id = pubkeyDao.insert(pubkey)

        val retrieved = pubkeyDao.getById(id)
        assertThat(retrieved?.createdDate).isEqualTo(now)
    }

    @Test
    fun keystoreAliasStoredForKeystoreKeys() = runTest {
        val pubkey = createTestPubkey(
            nickname = "keystore-test",
            storageType = KeyStorageType.ANDROID_KEYSTORE,
            keystoreAlias = "my-keystore-alias"
        )
        val id = pubkeyDao.insert(pubkey)

        val retrieved = pubkeyDao.getById(id)
        assertThat(retrieved?.storageType).isEqualTo(KeyStorageType.ANDROID_KEYSTORE)
        assertThat(retrieved?.keystoreAlias).isEqualTo("my-keystore-alias")
    }

    private fun createTestPubkey(
        nickname: String,
        type: String = "ssh-rsa",
        privateKey: ByteArray? = byteArrayOf(1, 2, 3),
        publicKey: ByteArray = byteArrayOf(4, 5, 6),
        encrypted: Boolean = false,
        startup: Boolean = false,
        confirmation: Boolean = false,
        createdDate: Long = System.currentTimeMillis(),
        storageType: KeyStorageType = KeyStorageType.EXPORTABLE,
        allowBackup: Boolean = true,
        keystoreAlias: String? = null
    ): Pubkey = Pubkey(
        nickname = nickname,
        type = type,
        privateKey = privateKey,
        publicKey = publicKey,
        encrypted = encrypted,
        startup = startup,
        confirmation = confirmation,
        createdDate = createdDate,
        storageType = storageType,
        allowBackup = allowBackup,
        keystoreAlias = keystoreAlias
    )
}
