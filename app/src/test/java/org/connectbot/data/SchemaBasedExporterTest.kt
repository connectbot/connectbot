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

package org.connectbot.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchemaBasedExporterTest {

    private lateinit var context: Context
    private lateinit var database: ConnectBotDatabase
    private lateinit var schema: DatabaseSchema

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        schema = DatabaseSchema.load(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importMinimalHost() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "test-server",
                        "protocol": "ssh",
                        "username": "root",
                        "hostname": "example.com",
                        "port": 22,
                        "useKeys": 1,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6"
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        val results = exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        assertThat(results["hosts"]?.first).isEqualTo(1) // inserted
        assertThat(results["hosts"]?.second).isEqualTo(0) // updated
        assertThat(results["hosts"]?.third).isEqualTo(0) // skipped

        val hosts = database.hostDao().observeAll().first()
        assertThat(hosts).hasSize(1)
        assertThat(hosts[0].nickname).isEqualTo("test-server")
        assertThat(hosts[0].hostname).isEqualTo("example.com")
        assertThat(hosts[0].pubkeyId).isEqualTo(-1)
    }

    @Test
    fun importHostWithNullColor() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "null-color",
                        "protocol": "ssh",
                        "username": "user",
                        "hostname": "example.com",
                        "port": 22,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6",
                        "color": null
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        val host = database.hostDao().observeAll().first().single()
        assertThat(host.color).isNull()
    }

    @Test
    fun importHostWithHexColor() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "hex-color",
                        "protocol": "ssh",
                        "username": "user",
                        "hostname": "example.com",
                        "port": 22,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6",
                        "color": "#FF5733"
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        val host = database.hostDao().observeAll().first().single()
        assertThat(host.color).isEqualTo("#FF5733")
    }

    @Test
    fun importMultipleHosts() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "server-1",
                        "protocol": "ssh",
                        "username": "admin",
                        "hostname": "host1.example.com",
                        "port": 22,
                        "useKeys": 1,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6",
                        "postLogin": "tmux attach -t main"
                    },
                    {
                        "nickname": "server-2",
                        "protocol": "ssh",
                        "username": "admin",
                        "hostname": "host2.example.com",
                        "port": 2222,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 1,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 500,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6"
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        val results = exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        assertThat(results["hosts"]?.first).isEqualTo(2)

        val hosts = database.hostDao().observeAll().first()
        assertThat(hosts).hasSize(2)
        assertThat(hosts.map { it.nickname }).containsExactlyInAnyOrder("server-1", "server-2")

        val server1 = hosts.find { it.nickname == "server-1" }!!
        assertThat(server1.postLogin).isEqualTo("tmux attach -t main")
        assertThat(server1.port).isEqualTo(22)

        val server2 = hosts.find { it.nickname == "server-2" }!!
        assertThat(server2.compression).isTrue()
        assertThat(server2.scrollbackLines).isEqualTo(500)
        assertThat(server2.port).isEqualTo(2222)
    }

    @Test
    fun importUpdatesDuplicateNicknames() = runTest {
        val json1 = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "same-name",
                        "protocol": "ssh",
                        "username": "user",
                        "hostname": "first.example.com",
                        "port": 22,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6"
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val json2 = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "same-name",
                        "protocol": "ssh",
                        "username": "admin",
                        "hostname": "updated.example.com",
                        "port": 2222,
                        "useKeys": 1,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 1,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 500,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6",
                        "postLogin": "tmux attach\n"
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)

        // First import — insert
        val first = exporter.importFromJson(json1, HostConfigJson.EXPORT_TABLES)
        assertThat(first["hosts"]?.first).isEqualTo(1) // inserted
        assertThat(first["hosts"]?.second).isEqualTo(0) // updated

        // Second import — same nickname should be updated
        val second = exporter.importFromJson(json2, HostConfigJson.EXPORT_TABLES)
        assertThat(second["hosts"]?.first).isEqualTo(0) // inserted
        assertThat(second["hosts"]?.second).isEqualTo(1) // updated

        val hosts = database.hostDao().observeAll().first()
        assertThat(hosts).hasSize(1)

        val host = hosts.single()
        assertThat(host.hostname).isEqualTo("updated.example.com")
        assertThat(host.port).isEqualTo(2222)
        assertThat(host.username).isEqualTo("admin")
        assertThat(host.compression).isTrue()
        assertThat(host.scrollbackLines).isEqualTo(500)
        assertThat(host.postLogin).isEqualTo("tmux attach\n")
    }

    @Test
    fun importWithOptionalFieldsMissing() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "bare-minimum",
                        "protocol": "ssh",
                        "username": "user",
                        "hostname": "example.com",
                        "port": 22,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6"
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        val host = database.hostDao().observeAll().first().single()
        assertThat(host.color).isNull()
        assertThat(host.postLogin).isNull()
        assertThat(host.useAuthAgent).isNull()
        assertThat(host.jumpHostId).isNull()
    }

    @Test
    fun importWithProfile() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [
                    {
                        "id": 10,
                        "name": "Custom Profile",
                        "fontSize": 14,
                        "delKey": "del",
                        "encoding": "UTF-8",
                        "emulation": "xterm-256color",
                        "colorSchemeId": -1
                    }
                ],
                "hosts": [
                    {
                        "nickname": "profiled-host",
                        "protocol": "ssh",
                        "username": "user",
                        "hostname": "example.com",
                        "port": 22,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6",
                        "profileId": 10
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        val results = exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        assertThat(results["profiles"]?.first).isEqualTo(1)
        assertThat(results["hosts"]?.first).isEqualTo(1)

        val host = database.hostDao().observeAll().first().single()
        assertThat(host.profileId).isNotNull()
    }

    @Test
    fun roundTripExportImport() = runTest {
        // Insert hosts directly
        val hostDao = database.hostDao()
        hostDao.insert(
            org.connectbot.data.entity.Host(
                nickname = "round-trip",
                protocol = "ssh",
                username = "admin",
                hostname = "rt.example.com",
                port = 443,
                color = "#AABBCC",
                useKeys = true,
                postLogin = "screen -r",
                pubkeyId = -1,
                wantSession = true,
                compression = true,
                scrollbackLines = 300
            )
        )

        // Export
        val exporter = SchemaBasedExporter(database, schema)
        val exported = exporter.exportToJson(HostConfigJson.EXPORT_TABLES)

        // Clear database
        database.clearAllTables()
        assertThat(hostDao.observeAll().first()).isEmpty()

        // Re-import
        val results = exporter.importFromJson(exported, HostConfigJson.EXPORT_TABLES)
        assertThat(results["hosts"]?.first).isEqualTo(1)

        val host = hostDao.observeAll().first().single()
        assertThat(host.nickname).isEqualTo("round-trip")
        assertThat(host.hostname).isEqualTo("rt.example.com")
        assertThat(host.port).isEqualTo(443)
        assertThat(host.color).isEqualTo("#AABBCC")
        assertThat(host.useKeys).isTrue()
        assertThat(host.postLogin).isEqualTo("screen -r")
        assertThat(host.compression).isTrue()
        assertThat(host.scrollbackLines).isEqualTo(300)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsNewerSchemaVersion() {
        val json = """
            {
                "version": 999,
                "profiles": [],
                "hosts": [],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)
    }

    @Test
    fun importAcceptsOlderSchemaVersion() = runTest {
        val json = """
            {
                "version": 1,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "old-version",
                        "protocol": "ssh",
                        "username": "user",
                        "hostname": "example.com",
                        "port": 22,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6"
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        val results = exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        assertThat(results["hosts"]?.first).isEqualTo(1)
    }

    @Test
    fun importWithPortForwards() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "id": 1,
                        "nickname": "with-forwards",
                        "protocol": "ssh",
                        "username": "user",
                        "hostname": "example.com",
                        "port": 22,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6"
                    }
                ],
                "port_forwards": [
                    {
                        "hostId": 1,
                        "nickname": "web",
                        "type": "local",
                        "sourcePort": 8080,
                        "destAddr": "localhost",
                        "destPort": 80
                    }
                ]
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        val results = exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        assertThat(results["hosts"]?.first).isEqualTo(1)
        assertThat(results["port_forwards"]?.first).isEqualTo(1)
    }

    @Test
    fun importedColorMustBeParseable() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [
                    {
                        "nickname": "bad-color",
                        "protocol": "ssh",
                        "username": "user",
                        "hostname": "example.com",
                        "port": 22,
                        "useKeys": 0,
                        "pubkeyId": -1,
                        "wantSession": 1,
                        "compression": 0,
                        "stayConnected": 0,
                        "quickDisconnect": 0,
                        "scrollbackLines": 140,
                        "useCtrlAltAsMetaKey": 0,
                        "ipVersion": "IPV4_AND_IPV6",
                        "color": "default"
                    }
                ],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        val host = database.hostDao().observeAll().first().single()

        // "default" is not a valid color — import must sanitize it to null
        // so that HostListScreen.parseColor() falls back to host_blue
        // instead of crashing with IllegalArgumentException.
        assertThat(host.color).isNull()
    }

    @Test
    fun importEmptyJson() = runTest {
        val json = """
            {
                "version": 7,
                "profiles": [],
                "hosts": [],
                "port_forwards": []
            }
        """.trimIndent()

        val exporter = SchemaBasedExporter(database, schema)
        val results = exporter.importFromJson(json, HostConfigJson.EXPORT_TABLES)

        assertThat(results["hosts"]?.first).isEqualTo(0) // inserted
        assertThat(results["hosts"]?.second).isEqualTo(0) // updated
        assertThat(results["hosts"]?.third).isEqualTo(0) // skipped
    }
}
