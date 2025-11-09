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

        val retrieved = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.hostname).isEqualTo("example.com")
        assertThat(retrieved?.port).isEqualTo(22)
        assertThat(retrieved?.hostId).isEqualTo(hostId)
        assertThat(retrieved?.hostKeyAlgo).isEqualTo("ssh-rsa")
    }

    @Test
    fun getByHostnameAndPort_ReturnsCorrectHost() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost1 = createTestKnownHost(hostId, "example.com", 22)
        val knownHost2 = createTestKnownHost(hostId, "example.com", 2222)
        val knownHost3 = createTestKnownHost(hostId, "test.com", 22)

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)
        knownHostDao.insert(knownHost3)

        val retrieved1 = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(retrieved1?.hostname).isEqualTo("example.com")
        assertThat(retrieved1?.port).isEqualTo(22)

        val retrieved2 = knownHostDao.getByHostnameAndPort("example.com", 2222)
        assertThat(retrieved2?.hostname).isEqualTo("example.com")
        assertThat(retrieved2?.port).isEqualTo(2222)

        val retrieved3 = knownHostDao.getByHostnameAndPort("test.com", 22)
        assertThat(retrieved3?.hostname).isEqualTo("test.com")
        assertThat(retrieved3?.port).isEqualTo(22)
    }

    @Test
    fun getByHostnameAndPort_ReturnsNullWhenNotFound() = runTest {
        val retrieved = knownHostDao.getByHostnameAndPort("nonexistent.com", 22)
        assertThat(retrieved).isNull()
    }

    @Test
    fun getByHostId_ReturnsAllKnownHostsForHost() = runTest {
        val host1 = createTestHost(nickname = "host1")
        val host2 = createTestHost(nickname = "host2")
        val hostId1 = hostDao.insert(host1)
        val hostId2 = hostDao.insert(host2)

        // Note: each (hostname, port) combination must be unique
        val knownHost1 = createTestKnownHost(hostId1, "server1.example.com", 22)
        val knownHost2 = createTestKnownHost(hostId1, "server2.example.com", 22)
        val knownHost3 = createTestKnownHost(hostId2, "test.com", 22)

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

        val knownHost1 = createTestKnownHost(hostId, "alpha.com", 22)
        val knownHost2 = createTestKnownHost(hostId, "beta.com", 22)
        val knownHost3 = createTestKnownHost(hostId, "alpha.com", 2222)

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

        val retrieved = knownHostDao.getByHostnameAndPort("example.com", 22)!!
        val newKeyBytes = "new-key".toByteArray()
        val updated = retrieved.copy(hostKey = newKeyBytes)
        knownHostDao.update(updated)

        val afterUpdate = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(afterUpdate?.hostKey).isEqualTo(newKeyBytes)
    }

    @Test
    fun deleteKnownHost() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost = createTestKnownHost(hostId, "example.com", 22)
        knownHostDao.insert(knownHost)

        val beforeDelete = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(beforeDelete).isNotNull()

        knownHostDao.delete(beforeDelete!!)

        val afterDelete = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(afterDelete).isNull()
    }

    @Test
    fun deleteByHostnameAndPort() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost = createTestKnownHost(hostId, "example.com", 22)
        knownHostDao.insert(knownHost)

        val beforeDelete = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(beforeDelete).isNotNull()

        knownHostDao.deleteByHostnameAndPort("example.com", 22)

        val afterDelete = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(afterDelete).isNull()
    }

    @Test
    fun deleteByHostnameAndPort_OnlyDeletesMatchingHost() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost1 = createTestKnownHost(hostId, "example.com", 22)
        val knownHost2 = createTestKnownHost(hostId, "example.com", 2222)
        val knownHost3 = createTestKnownHost(hostId, "test.com", 22)

        knownHostDao.insert(knownHost1)
        knownHostDao.insert(knownHost2)
        knownHostDao.insert(knownHost3)

        knownHostDao.deleteByHostnameAndPort("example.com", 22)

        val deleted = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(deleted).isNull()

        val stillExists1 = knownHostDao.getByHostnameAndPort("example.com", 2222)
        assertThat(stillExists1).isNotNull()

        val stillExists2 = knownHostDao.getByHostnameAndPort("test.com", 22)
        assertThat(stillExists2).isNotNull()
    }

    @Test
    fun deleteHost_CascadesToKnownHosts() = runTest {
        val host = createTestHost()
        val hostId = hostDao.insert(host)

        val knownHost1 = createTestKnownHost(hostId, "example.com", 22)
        val knownHost2 = createTestKnownHost(hostId, "example.com", 2222)

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
        val firstConnection = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(firstConnection).isNull()

        // Accept and store the host key
        val initialKeyBytes = "ssh-rsa AAAAB3...".toByteArray()
        val newKnownHost = createTestKnownHost(hostId, "example.com", 22, hostKey = initialKeyBytes)
        knownHostDao.insert(newKnownHost)

        // Second connection - verify against stored key
        val secondConnection = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(secondConnection).isNotNull()
        assertThat(secondConnection?.hostKey).isEqualTo(initialKeyBytes)

        // Host key changed - update it
        val newKeyBytes = "ssh-rsa AAAAB4...".toByteArray()
        val updated = secondConnection!!.copy(hostKey = newKeyBytes)
        knownHostDao.update(updated)

        // Third connection - verify against new key
        val thirdConnection = knownHostDao.getByHostnameAndPort("example.com", 22)
        assertThat(thirdConnection?.hostKey).isEqualTo(newKeyBytes)
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
