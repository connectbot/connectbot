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
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KnownHost
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnownHostDaoTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var knownHostDao: KnownHostDao
    private lateinit var hostDao: HostDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        knownHostDao = database.knownHostDao()
        hostDao = database.hostDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveKnownHost() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost = createTestKnownHost(
            hostId = hostId,
            hostname = "example.com",
            port = 22
        )

        val id = knownHostDao.insert(knownHost)
        assertThat(id).isGreaterThan(0)

        val retrievedList = knownHostDao.getByHostId(hostId)
        assertThat(retrievedList).isNotEmpty
        val retrieved = retrievedList[0]
        assertThat(retrieved.hostname).isEqualTo("example.com")
        assertThat(retrieved.port).isEqualTo(22)
        assertThat(retrieved.hostId).isEqualTo(hostId)
        assertThat(retrieved.hostKeyAlgo).isEqualTo("ssh-rsa")
    }

    @Test
    fun getByHostId_ReturnsAllKeysForHost() = runTest {
        val host1 = createTestHost(nickname = "host1")
        val hostId1 = hostDao.insert(host1)

        val host2 = createTestHost(nickname = "host2")
        val hostId2 = hostDao.insert(host2)

        val knownHost1 = createTestKnownHost(hostId1, "example.com", 22, hostKey = "rsa-key".toByteArray())
        val knownHost2 = createTestKnownHost(hostId1, "example.com", 22, hostKeyAlgo = "ssh-ed25519", hostKey = "ed25519-key".toByteArray())
        val knownHost3 = createTestKnownHost(hostId2, "test.com", 22, hostKey = "test-key".toByteArray())

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)
        knownHostDao.insert(knownHost3)

        val retrieved1 = knownHostDao.getByHostId(hostId1)
        assertThat(retrieved1).hasSize(2)
        assertThat(retrieved1.map { it.hostKeyAlgo }).containsExactlyInAnyOrder("ssh-rsa", "ssh-ed25519")

        val retrieved2 = knownHostDao.getByHostId(hostId2)
        assertThat(retrieved2).hasSize(1)
        assertThat(retrieved2[0].hostname).isEqualTo("test.com")
    }

    @Test
    fun getByHostId_ReturnsEmptyWhenNotFound() = runTest {
        val retrieved = knownHostDao.getByHostId(99999L)
        assertThat(retrieved).isEmpty()
    }

    @Test
    fun getByHostId_ReturnsAllKnownHostsForHost() = runTest {
        val host1 = createTestHost(nickname = "host1")
        val host2 = createTestHost(nickname = "host2")
        val hostId1 = hostDao.insert(host1)
        val hostId2 = hostDao.insert(host2)

        // Note: each (host_id, host_key) combination must be unique
        val knownHost1 = createTestKnownHost(hostId1, "server1.example.com", 22, hostKey = "key1".toByteArray())
        val knownHost2 = createTestKnownHost(hostId1, "server2.example.com", 22, hostKey = "key2".toByteArray())
        val knownHost3 = createTestKnownHost(hostId2, "test.com", 22, hostKey = "key3".toByteArray())

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)
        knownHostDao.insert(knownHost3)

        val host1KnownHosts = knownHostDao.getByHostId(hostId1)
        assertThat(host1KnownHosts).hasSize(2)
        assertThat(host1KnownHosts.map { it.hostname }).containsExactlyInAnyOrder("server1.example.com", "server2.example.com")
        assertThat(host1KnownHosts.map { it.port }).containsExactly(22, 22)

        val host2KnownHosts = knownHostDao.getByHostId(hostId2)
        assertThat(host2KnownHosts).hasSize(1)
        assertThat(host2KnownHosts[0].hostname).isEqualTo("test.com")
    }

    @Test
    fun getByHostId_ReturnsEmptyListWhenNoKnownHosts() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHosts = knownHostDao.getByHostId(hostId)
        assertThat(knownHosts).isEmpty()
    }

    @Test
    fun getAll_ReturnsAllKnownHosts() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost1 = createTestKnownHost(hostId, "alpha.com", 22, hostKey = "key1".toByteArray())
        val knownHost2 = createTestKnownHost(hostId, "beta.com", 22, hostKey = "key2".toByteArray())
        val knownHost3 = createTestKnownHost(hostId, "alpha.com", 2222, hostKey = "key3".toByteArray())

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)
        knownHostDao.insert(knownHost3)

        val allKnownHosts = knownHostDao.getAll()
        assertThat(allKnownHosts).hasSize(3)

        // Should be sorted by hostname, then port
        assertThat(allKnownHosts[0].hostname).isEqualTo("alpha.com")
        assertThat(allKnownHosts[0].port).isEqualTo(22)
        assertThat(allKnownHosts[1].hostname).isEqualTo("alpha.com")
        assertThat(allKnownHosts[1].port).isEqualTo(2222)
        assertThat(allKnownHosts[2].hostname).isEqualTo("beta.com")
        assertThat(allKnownHosts[2].port).isEqualTo(22)
    }

    @Test
    fun getAll_ReturnsEmptyListWhenNoKnownHosts() = runTest {
        val allKnownHosts = knownHostDao.getAll()
        assertThat(allKnownHosts).isEmpty()
    }

    @Test
    fun updateKnownHost() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val oldKeyBytes = "old-key".toByteArray()
        val knownHost = createTestKnownHost(hostId, "example.com", 22, hostKey = oldKeyBytes)
        val id = knownHostDao.insert(knownHost)

        val retrievedList = knownHostDao.getByHostId(hostId)
        assertThat(retrievedList).isNotEmpty
        val retrieved = retrievedList[0]

        val newKeyBytes = "new-key".toByteArray()
        val updated = retrieved.copy(hostKey = newKeyBytes)
        knownHostDao.update(updated)

        val afterUpdateList = knownHostDao.getByHostId(hostId)
        assertThat(afterUpdateList).isNotEmpty
        assertThat(afterUpdateList[0].hostKey).isEqualTo(newKeyBytes)
    }

    @Test
    fun deleteKnownHost() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost = createTestKnownHost(hostId, "example.com", 22)
        knownHostDao.insert(knownHost)

        val beforeDeleteList = knownHostDao.getByHostId(hostId)
        assertThat(beforeDeleteList).isNotEmpty

        knownHostDao.delete(beforeDeleteList[0])

        val afterDeleteList = knownHostDao.getByHostId(hostId)
        assertThat(afterDeleteList).isEmpty()
    }

    @Test
    fun deleteByHostnameAndPort() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost = createTestKnownHost(hostId, "example.com", 22)
        knownHostDao.insert(knownHost)

        val beforeDeleteList = knownHostDao.getByHostId(hostId)
        assertThat(beforeDeleteList).isNotEmpty

        knownHostDao.deleteByHostnameAndPort("example.com", 22)

        val afterDeleteList = knownHostDao.getByHostId(hostId)
        assertThat(afterDeleteList).isEmpty()
    }

    @Test
    fun deleteByHostnameAndPort_OnlyDeletesMatchingHost() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost1 = createTestKnownHost(hostId, "example.com", 22, hostKey = "key1".toByteArray())
        val knownHost2 = createTestKnownHost(hostId, "example.com", 2222, hostKey = "key2".toByteArray())
        val knownHost3 = createTestKnownHost(hostId, "test.com", 22, hostKey = "key3".toByteArray())

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)
        knownHostDao.insert(knownHost3)

        knownHostDao.deleteByHostnameAndPort("example.com", 22)

        val remaining = knownHostDao.getByHostId(hostId)
        assertThat(remaining).hasSize(2)
        assertThat(remaining.map { "${it.hostname}:${it.port}" })
            .containsExactlyInAnyOrder("example.com:2222", "test.com:22")
    }

    @Test
    fun deleteHost_CascadesToKnownHosts() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost1 = createTestKnownHost(hostId, "example.com", 22, hostKey = "key1".toByteArray())
        val knownHost2 = createTestKnownHost(hostId, "example.com", 2222, hostKey = "key2".toByteArray())

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)

        val knownHostsBeforeDelete = knownHostDao.getByHostId(hostId)
        assertThat(knownHostsBeforeDelete).hasSize(2)

        // Delete the host
        val hostToDelete = hostDao.getById(hostId)!!
        hostDao.delete(hostToDelete)

        // Known hosts should also be deleted due to CASCADE
        val knownHostsAfterDelete = knownHostDao.getByHostId(hostId)
        assertThat(knownHostsAfterDelete).isEmpty()
    }

    @Test
    fun hostKeyVerification_Scenario() = runTest {
        // Simulate a typical host key verification scenario
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        // First connection - no known host
        val firstConnection = knownHostDao.getByHostId(hostId)
        assertThat(firstConnection).isEmpty()

        // Accept and store the host key
        val initialKeyBytes = "ssh-rsa AAAAB3...".toByteArray()
        val newKnownHost = createTestKnownHost(hostId, "example.com", 22, hostKey = initialKeyBytes)
        knownHostDao.insert(newKnownHost)

        // Second connection - verify against stored key
        val secondConnection = knownHostDao.getByHostId(hostId)
        assertThat(secondConnection).hasSize(1)
        assertThat(secondConnection[0].hostKey).isEqualTo(initialKeyBytes)

        // Host key changed - add new key (allows both old and new during rotation)
        val newKeyBytes = "ssh-rsa AAAAB4...".toByteArray()
        val newKnownHost2 = createTestKnownHost(hostId, "example.com", 22, hostKey = newKeyBytes)
        knownHostDao.insert(newKnownHost2)

        // Third connection - verify both keys exist
        val thirdConnection = knownHostDao.getByHostId(hostId)
        assertThat(thirdConnection).hasSize(2)
        assertThat(thirdConnection.map { it.hostKey }).containsExactlyInAnyOrder(initialKeyBytes, newKeyBytes)
    }

    @Test
    fun insertMultipleKeysForSameHost() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHostRSA = createTestKnownHost(
            hostId = hostId,
            hostname = "example.com",
            port = 22,
            hostKeyAlgo = "ssh-rsa",
            hostKey = "rsa-key".toByteArray()
        )

        val knownHostECDSA = createTestKnownHost(
            hostId = hostId,
            hostname = "example.com",
            port = 22,
            hostKeyAlgo = "ecdsa-sha2-nistp256",
            hostKey = "ecdsa-key".toByteArray()
        )

        knownHostDao.insert(knownHostRSA)
        knownHostDao.insert(knownHostECDSA)

        val retrieved = knownHostDao.getByHostId(hostId)
        assertThat(retrieved).hasSize(2)
        assertThat(retrieved.map { it.hostKeyAlgo }).containsExactlyInAnyOrder("ssh-rsa", "ecdsa-sha2-nistp256")

        val rsaKeys = knownHostDao.getByHostIdAndAlgo(hostId, "ssh-rsa")
        assertThat(rsaKeys).hasSize(1)
        assertThat(rsaKeys[0].hostKey).isEqualTo("rsa-key".toByteArray())
    }

    @Test
    fun keyRotation_MultipleKeysOfSameAlgorithm() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val oldKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOldKey...".toByteArray()
        val newKey = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINewKey...".toByteArray()

        val knownHost1 = createTestKnownHost(
            hostId = hostId,
            hostname = "example.com",
            port = 22,
            hostKeyAlgo = "ssh-ed25519",
            hostKey = oldKey
        )

        val knownHost2 = createTestKnownHost(
            hostId = hostId,
            hostname = "example.com",
            port = 22,
            hostKeyAlgo = "ssh-ed25519",
            hostKey = newKey
        )

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)

        val allKeys = knownHostDao.getByHostId(hostId)
        assertThat(allKeys).hasSize(2)

        val ed25519Keys = knownHostDao.getByHostIdAndAlgo(hostId, "ssh-ed25519")
        assertThat(ed25519Keys).hasSize(2)
        assertThat(ed25519Keys.map { it.hostKey }).containsExactlyInAnyOrder(oldKey, newKey)

        val exactOldKey = knownHostDao.getByHostIdAlgoAndKey(hostId, "ssh-ed25519", oldKey)
        assertThat(exactOldKey).isNotNull()
        assertThat(exactOldKey?.hostKey).isEqualTo(oldKey)

        val exactNewKey = knownHostDao.getByHostIdAlgoAndKey(hostId, "ssh-ed25519", newKey)
        assertThat(exactNewKey).isNotNull()
        assertThat(exactNewKey?.hostKey).isEqualTo(newKey)
    }

    @Test
    fun getByHostIdAlgoAndKey_WithDuplicateKeys_ReturnsOnlyOne() = runTest {
        val host = createTestHost(nickname = "server", hostname = "server.example.com")
        val hostId = hostDao.insert(host)

        val sharedKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQC...".toByteArray()
        val algo = "ssh-rsa"

        val knownHost1 = createTestKnownHost(
            hostId = hostId,
            hostname = "server.example.com",
            port = 22,
            hostKeyAlgo = algo,
            hostKey = sharedKey
        )

        val knownHost2 = createTestKnownHost(
            hostId = hostId,
            hostname = "renamed-server.example.com",
            port = 22,
            hostKeyAlgo = algo,
            hostKey = sharedKey
        )

        val knownHost3 = createTestKnownHost(
            hostId = hostId,
            hostname = "server.example.com",
            port = 2222,
            hostKeyAlgo = algo,
            hostKey = sharedKey
        )

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)
        knownHostDao.insert(knownHost3)

        val result = knownHostDao.getByHostIdAlgoAndKey(hostId, algo, sharedKey)
        assertThat(result).isNotNull()
        assertThat(result?.hostId).isEqualTo(hostId)
        assertThat(result?.hostKey).isEqualTo(sharedKey)

        val allKeysForHost = knownHostDao.getByHostId(hostId)
        assertThat(allKeysForHost).hasSize(3)
    }

    @Test
    fun deleteByHostId_DeletesAllKnownHostsForHost() = runTest {
        val host1 = createTestHost(nickname = "host1")
        val host2 = createTestHost(nickname = "host2")
        val hostId1 = hostDao.insert(host1)
        val hostId2 = hostDao.insert(host2)

        // Create multiple known hosts for host1 with different algorithms
        val knownHost1 = createTestKnownHost(hostId1, "example.com", 22, hostKeyAlgo = "ssh-rsa", hostKey = "rsa-key".toByteArray())
        val knownHost2 = createTestKnownHost(hostId1, "example.com", 22, hostKeyAlgo = "ssh-ed25519", hostKey = "ed25519-key".toByteArray())
        val knownHost3 = createTestKnownHost(hostId1, "example.com", 2222, hostKey = "other-key".toByteArray())

        // Create a known host for host2
        val knownHost4 = createTestKnownHost(hostId2, "test.com", 22, hostKey = "test-key".toByteArray())

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)
        knownHostDao.insert(knownHost3)
        knownHostDao.insert(knownHost4)

        // Verify initial state
        assertThat(knownHostDao.getByHostId(hostId1)).hasSize(3)
        assertThat(knownHostDao.getByHostId(hostId2)).hasSize(1)

        // Delete all known hosts for host1
        knownHostDao.deleteByHostId(hostId1)

        // Verify host1's known hosts are deleted
        assertThat(knownHostDao.getByHostId(hostId1)).isEmpty()

        // Verify host2's known hosts are not affected
        val host2KnownHosts = knownHostDao.getByHostId(hostId2)
        assertThat(host2KnownHosts).hasSize(1)
        assertThat(host2KnownHosts[0].hostname).isEqualTo("test.com")
    }

    @Test
    fun deleteByHostId_DoesNothingWhenNoKnownHostsExist() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        // Try to delete when no known hosts exist
        knownHostDao.deleteByHostId(hostId)

        // Should not throw and should return empty list
        val knownHosts = knownHostDao.getByHostId(hostId)
        assertThat(knownHosts).isEmpty()
    }

    private fun createTestHost(
        nickname: String = "test-host",
        protocol: String = "ssh",
        hostname: String = "example.com"
    ) = Host(
        nickname = nickname,
        protocol = protocol,
        hostname = hostname
    )

    private fun createTestKnownHost(
        hostId: Long,
        hostname: String,
        port: Int,
        hostKeyAlgo: String = "ssh-rsa",
        hostKey: ByteArray = "AAAAB3NzaC1yc2EAAAADAQABAAABAQC...".toByteArray()
    ) = KnownHost(
        hostId = hostId,
        hostname = hostname,
        port = port,
        hostKeyAlgo = hostKeyAlgo,
        hostKey = hostKey
    )
}
