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
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortForwardDaoTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var portForwardDao: PortForwardDao
    private lateinit var hostDao: HostDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        portForwardDao = database.portForwardDao()
        hostDao = database.hostDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrievePortForward() = runTest {
        val hostId = createTestHost()
        val portForward = createTestPortForward(hostId = hostId, nickname = "test-forward")

        val id = portForwardDao.insert(portForward)
        assertThat(id).isGreaterThan(0)

        val retrieved = portForwardDao.getById(id)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.nickname).isEqualTo("test-forward")
        assertThat(retrieved?.hostId).isEqualTo(hostId)
    }

    @Test
    fun observePortForwardsForHost() = runTest {
        val host1Id = createTestHost(nickname = "host1")
        val host2Id = createTestHost(nickname = "host2")

        val forward1 = createTestPortForward(hostId = host1Id, nickname = "forward1")
        val forward2 = createTestPortForward(hostId = host1Id, nickname = "forward2")
        val forward3 = createTestPortForward(hostId = host2Id, nickname = "forward3")

        portForwardDao.insert(forward1)
        portForwardDao.insert(forward2)
        portForwardDao.insert(forward3)

        val host1Forwards = portForwardDao.observeByHost(host1Id).first()
        assertThat(host1Forwards).hasSize(2)
        assertThat(host1Forwards.map { it.nickname }).containsExactlyInAnyOrder("forward1", "forward2")

        val host2Forwards = portForwardDao.observeByHost(host2Id).first()
        assertThat(host2Forwards).hasSize(1)
        assertThat(host2Forwards.first().nickname).isEqualTo("forward3")
    }

    @Test
    fun cascadeDeleteWhenHostDeleted() = runTest {
        val hostId = createTestHost()
        val forward1 = createTestPortForward(hostId = hostId, nickname = "forward1")
        val forward2 = createTestPortForward(hostId = hostId, nickname = "forward2")

        portForwardDao.insert(forward1)
        portForwardDao.insert(forward2)

        // Verify forwards exist
        val forwardsBefore = portForwardDao.observeByHost(hostId).first()
        assertThat(forwardsBefore).hasSize(2)

        // Delete the host
        val host = hostDao.getById(hostId)
        assertThat(host).isNotNull()
        hostDao.delete(host!!)

        // Verify forwards were cascade deleted
        val forwardsAfter = portForwardDao.observeByHost(hostId).first()
        assertThat(forwardsAfter).isEmpty()
    }

    @Test
    fun updatePortForward() = runTest {
        val hostId = createTestHost()
        val portForward = createTestPortForward(hostId = hostId, nickname = "original")
        val id = portForwardDao.insert(portForward)

        val updated = portForward.copy(
            id = id,
            nickname = "updated",
            type = "remote",
            sourcePort = 9999,
            destPort = 8888
        )
        portForwardDao.update(updated)

        val retrieved = portForwardDao.getById(id)
        assertThat(retrieved?.nickname).isEqualTo("updated")
        assertThat(retrieved?.type).isEqualTo("remote")
        assertThat(retrieved?.sourcePort).isEqualTo(9999)
        assertThat(retrieved?.destPort).isEqualTo(8888)
    }

    @Test
    fun deletePortForward() = runTest {
        val hostId = createTestHost()
        val portForward = createTestPortForward(hostId = hostId, nickname = "to-delete")
        val id = portForwardDao.insert(portForward)

        // Verify it exists
        assertThat(portForwardDao.getById(id)).isNotNull()

        // Delete it
        portForwardDao.delete(portForward.copy(id = id))

        // Verify it's gone
        assertThat(portForwardDao.getById(id)).isNull()
    }

    @Test
    fun deleteAllByHost() = runTest {
        val hostId = createTestHost()
        val forward1 = createTestPortForward(hostId = hostId, nickname = "forward1")
        val forward2 = createTestPortForward(hostId = hostId, nickname = "forward2")

        portForwardDao.insert(forward1)
        portForwardDao.insert(forward2)

        // Verify forwards exist
        val forwardsBefore = portForwardDao.observeByHost(hostId).first()
        assertThat(forwardsBefore).hasSize(2)

        // Delete all forwards for this host
        portForwardDao.deleteByHost(hostId)

        // Verify they're gone
        val forwardsAfter = portForwardDao.observeByHost(hostId).first()
        assertThat(forwardsAfter).isEmpty()
    }

    @Test
    fun portForwardTypes() = runTest {
        val hostId = createTestHost()
        val localId = portForwardDao.insert(createTestPortForward(hostId = hostId, nickname = "local", type = "local"))
        val remoteId = portForwardDao.insert(createTestPortForward(hostId = hostId, nickname = "remote", type = "remote"))
        val dynamicId = portForwardDao.insert(createTestPortForward(hostId = hostId, nickname = "dynamic", type = "dynamic"))

        val localForward = portForwardDao.getById(localId)
        assertThat(localForward?.type).isEqualTo("local")

        val remoteForward = portForwardDao.getById(remoteId)
        assertThat(remoteForward?.type).isEqualTo("remote")

        val dynamicForward = portForwardDao.getById(dynamicId)
        assertThat(dynamicForward?.type).isEqualTo("dynamic")
    }

    @Test
    fun portForwardWithDestinationAddress() = runTest {
        val hostId = createTestHost()
        val portForward = createTestPortForward(
            hostId = hostId,
            nickname = "with-dest",
            destAddr = "192.168.1.100"
        )
        val id = portForwardDao.insert(portForward)

        val retrieved = portForwardDao.getById(id)
        assertThat(retrieved?.destAddr).isEqualTo("192.168.1.100")
    }

    @Test
    fun getByHostReturnsCorrectForwards() = runTest {
        val hostId = createTestHost()

        val forwards = (1..5).map { i ->
            createTestPortForward(
                hostId = hostId,
                nickname = "forward$i",
                sourcePort = 8000 + i
            )
        }

        forwards.forEach { portForwardDao.insert(it) }

        val retrieved = portForwardDao.getByHost(hostId)
        assertThat(retrieved).hasSize(5)
        assertThat(retrieved.map { it.sourcePort }).containsExactlyInAnyOrder(8001, 8002, 8003, 8004, 8005)
    }

    private suspend fun createTestHost(nickname: String = "test-host"): Long {
        val host = Host(
            nickname = nickname,
            protocol = "ssh",
            username = "user",
            hostname = "example.com",
            port = 22,
            hostKeyAlgo = null,
            lastConnect = 0,
            color = "gray",
            useKeys = false,
            useAuthAgent = null,
            stayConnected = false,
            postLogin = null,
            pubkeyId = 0,
            wantSession = true,
            fontSize = 10,
            encoding = "UTF-8",
            compression = false,
            colorSchemeId = 1,
            delKey = "BACKSPACE",
            scrollbackLines = 140,
            useCtrlAltAsMetaKey = false
        )
        return hostDao.insert(host)
    }

    private fun createTestPortForward(
        hostId: Long,
        nickname: String,
        type: String = "local",
        sourcePort: Int = 8080,
        destAddr: String = "localhost",
        destPort: Int = 80
    ): PortForward {
        return PortForward(
            hostId = hostId,
            nickname = nickname,
            type = type,
            sourcePort = sourcePort,
            destAddr = destAddr,
            destPort = destPort
        )
    }
}
