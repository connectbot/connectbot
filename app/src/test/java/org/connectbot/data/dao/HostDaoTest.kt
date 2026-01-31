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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostDaoTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var hostDao: HostDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        hostDao = database.hostDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveHost() = runTest {
        val host = createTestHost(nickname = "test-server")

        val id = hostDao.insert(host)
        assertThat(id).isGreaterThan(0)

        val retrieved = hostDao.getById(id)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.nickname).isEqualTo("test-server")
        assertThat(retrieved?.protocol).isEqualTo("ssh")
        assertThat(retrieved?.hostname).isEqualTo("example.com")
    }

    @Test
    fun observeAllHosts() = runTest {
        val host1 = createTestHost(nickname = "alpha")
        val host2 = createTestHost(nickname = "beta")
        val host3 = createTestHost(nickname = "gamma")

        hostDao.insert(host1)
        hostDao.insert(host2)
        hostDao.insert(host3)

        val hosts = hostDao.observeAll().first()
        assertThat(hosts).hasSize(3)
        assertThat(hosts.map { it.nickname }).containsExactly("alpha", "beta", "gamma")
    }

    @Test
    fun observeAllSortedByColor() = runTest {
        val red = createTestHost(nickname = "red-server", color = "red")
        val blue = createTestHost(nickname = "blue-server", color = "blue")
        val green = createTestHost(nickname = "green-server", color = "green")
        val alsoBlue = createTestHost(nickname = "another-blue", color = "blue")

        hostDao.insert(red)
        hostDao.insert(blue)
        hostDao.insert(green)
        hostDao.insert(alsoBlue)

        val hosts = hostDao.observeAllSortedByColor().first()
        assertThat(hosts).hasSize(4)

        // Should be sorted by color, then by nickname within each color
        val colors = hosts.map { it.color }
        assertThat(colors).containsExactly("blue", "blue", "green", "red")

        // Within blue color group, should be sorted by nickname
        val blueHosts = hosts.filter { it.color == "blue" }
        assertThat(blueHosts.map { it.nickname }).containsExactly("another-blue", "blue-server")
    }

    @Test
    fun updateHost() = runTest {
        val host = createTestHost(nickname = "original")
        val id = hostDao.insert(host)

        val updated = host.copy(
            id = id,
            nickname = "updated",
            hostname = "new.example.com",
            port = 2222
        )
        hostDao.update(updated)

        val retrieved = hostDao.getById(id)
        assertThat(retrieved?.nickname).isEqualTo("updated")
        assertThat(retrieved?.hostname).isEqualTo("new.example.com")
        assertThat(retrieved?.port).isEqualTo(2222)
    }

    @Test
    fun deleteHost() = runTest {
        val host = createTestHost(nickname = "to-delete")
        val id = hostDao.insert(host)

        // Verify it exists
        assertThat(hostDao.getById(id)).isNotNull()

        // Delete it
        hostDao.delete(host.copy(id = id))

        // Verify it's gone
        assertThat(hostDao.getById(id)).isNull()
    }

    @Test
    fun deleteById() = runTest {
        val host = createTestHost(nickname = "to-delete")
        val id = hostDao.insert(host)

        // Verify it exists
        assertThat(hostDao.getById(id)).isNotNull()

        // Delete it by ID
        hostDao.deleteById(id)

        // Verify it's gone
        assertThat(hostDao.getById(id)).isNull()
    }

    @Test
    fun observeByIdReturnsFlowUpdates() = runTest {
        val host = createTestHost(nickname = "watch-me")
        val id = hostDao.insert(host)

        val flow = hostDao.observeById(id)

        // Initial value
        val initial = flow.first()
        assertThat(initial?.nickname).isEqualTo("watch-me")
        assertThat(initial?.port).isEqualTo(22)

        // Update and verify flow emits new value
        hostDao.update(host.copy(id = id, port = 2222))

        val updated = flow.first()
        assertThat(updated?.port).isEqualTo(2222)
    }

    @Test
    fun protocolFieldStored() = runTest {
        val ssh = createTestHost(nickname = "ssh-host", protocol = "ssh")
        val telnet = createTestHost(nickname = "telnet-host", protocol = "telnet")

        val sshId = hostDao.insert(ssh)
        val telnetId = hostDao.insert(telnet)

        val sshHost = hostDao.getById(sshId)
        assertThat(sshHost?.protocol).isEqualTo("ssh")

        val telnetHost = hostDao.getById(telnetId)
        assertThat(telnetHost?.protocol).isEqualTo("telnet")
    }

    @Test
    fun booleanFieldsStoredCorrectly() = runTest {
        val host = createTestHost(
            nickname = "bool-test",
            compression = true,
            useKeys = true,
            stayConnected = false,
            wantSession = false,
            useCtrlAltAsMetaKey = true
        )
        val id = hostDao.insert(host)

        val retrieved = hostDao.getById(id)
        assertThat(retrieved?.compression).isTrue()
        assertThat(retrieved?.useKeys).isTrue()
        assertThat(retrieved?.stayConnected).isFalse()
        assertThat(retrieved?.wantSession).isFalse()
        assertThat(retrieved?.useCtrlAltAsMetaKey).isTrue()
    }

    @Test
    fun nullableFieldsHandledCorrectly() = runTest {
        val host = createTestHost(
            nickname = "nullable-test",
            postLogin = null,
            hostKeyAlgo = null,
            color = null,
            useAuthAgent = null
        )
        val id = hostDao.insert(host)

        val retrieved = hostDao.getById(id)
        assertThat(retrieved?.postLogin).isNull()
        assertThat(retrieved?.hostKeyAlgo).isNull()
        assertThat(retrieved?.color).isNull()
        assertThat(retrieved?.useAuthAgent).isNull()
    }

    @Test
    fun pubkeyIdReference() = runTest {
        val host = createTestHost(nickname = "with-key", pubkeyId = 42)
        val id = hostDao.insert(host)

        val retrieved = hostDao.getById(id)
        assertThat(retrieved?.pubkeyId).isEqualTo(42)
    }

    @Test
    fun profileIdStored() = runTest {
        val host = createTestHost(nickname = "profiled", profileId = 5)
        val id = hostDao.insert(host)

        val retrieved = hostDao.getById(id)
        assertThat(retrieved?.profileId).isEqualTo(5)
    }

    @Test
    fun lastConnectTimestampStored() = runTest {
        val now = System.currentTimeMillis()
        val host = createTestHost(nickname = "recent", lastConnect = now)
        val id = hostDao.insert(host)

        val retrieved = hostDao.getById(id)
        assertThat(retrieved?.lastConnect).isEqualTo(now)
    }

    @Test
    fun scrollbackLinesStored() = runTest {
        val host = createTestHost(nickname = "scrollback-test", scrollbackLines = 500)
        val id = hostDao.insert(host)

        val retrieved = hostDao.getById(id)
        assertThat(retrieved?.scrollbackLines).isEqualTo(500)
    }

    @Test
    fun multipleHostsWithDifferentProtocols() = runTest {
        val ssh1 = createTestHost(nickname = "ssh-1", protocol = "ssh", port = 22)
        val ssh2 = createTestHost(nickname = "ssh-2", protocol = "ssh", port = 2222)
        val telnet1 = createTestHost(nickname = "telnet-1", protocol = "telnet", port = 23)

        hostDao.insert(ssh1)
        hostDao.insert(ssh2)
        hostDao.insert(telnet1)

        val all = hostDao.observeAll().first()
        assertThat(all).hasSize(3)
        assertThat(all.count { it.protocol == "ssh" }).isEqualTo(2)
        assertThat(all.count { it.protocol == "telnet" }).isEqualTo(1)
    }

    private fun createTestHost(
        nickname: String,
        protocol: String = "ssh",
        username: String = "user",
        hostname: String = "example.com",
        port: Int = 22,
        hostKeyAlgo: String? = null,
        lastConnect: Long = 0,
        color: String? = "gray",
        useKeys: Boolean = false,
        useAuthAgent: String? = null,
        stayConnected: Boolean = false,
        postLogin: String? = null,
        pubkeyId: Long = 0,
        wantSession: Boolean = true,
        compression: Boolean = false,
        scrollbackLines: Int = 140,
        useCtrlAltAsMetaKey: Boolean = false,
        jumpHostId: Long? = null,
        profileId: Long? = 1L
    ): Host = Host(
        nickname = nickname,
        protocol = protocol,
        username = username,
        hostname = hostname,
        port = port,
        hostKeyAlgo = hostKeyAlgo,
        lastConnect = lastConnect,
        color = color,
        useKeys = useKeys,
        useAuthAgent = useAuthAgent,
        stayConnected = stayConnected,
        postLogin = postLogin,
        pubkeyId = pubkeyId,
        wantSession = wantSession,
        compression = compression,
        scrollbackLines = scrollbackLines,
        useCtrlAltAsMetaKey = useCtrlAltAsMetaKey,
        jumpHostId = jumpHostId,
        profileId = profileId
    )
}
