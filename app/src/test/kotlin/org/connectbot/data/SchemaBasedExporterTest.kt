/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Profile
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchemaBasedExporterTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var exporter: SchemaBasedExporter
    private lateinit var schema: DatabaseSchema

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        schema = DatabaseSchema.load(context)
        exporter = SchemaBasedExporter(database, schema)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportToJson_usesSchemaFieldsAndSkipsExcludedColumns() = runTest {
        val profileId = database.profileDao().insert(createProfile(name = "Ops"))
        val hostId = database.hostDao().insert(
            createHost(
                nickname = "prod",
                hostname = "prod.example.com",
                hostKeyAlgo = "ssh-ed25519",
                lastConnect = 1_700_000_000L,
                postLogin = null,
                profileId = profileId,
                ipVersion = "IPV6_ONLY",
            ),
        )
        database.portForwardDao().insert(
            PortForward(
                hostId = hostId,
                nickname = "web",
                type = "local",
                sourceAddr = "0.0.0.0",
                sourcePort = 8080,
                destAddr = null,
                destPort = 80,
            ),
        )

        val jsonString = exporter.exportToJson(
            listOf("unknown_table", "profiles", "hosts", "port_forwards"),
            pretty = false,
        )
        val json = JSONObject(jsonString)

        assertThat(json.getInt("version")).isEqualTo(schema.version)
        assertThat(json.has("unknown_table")).isFalse()

        val profile = json.getJSONArray("profiles").getJSONObject(0)
        assertThat(profile.getString("name")).isEqualTo("Ops")
        assertThat(profile.getLong("id")).isEqualTo(profileId)

        val host = json.getJSONArray("hosts").getJSONObject(0)
        assertThat(host.getString("nickname")).isEqualTo("prod")
        assertThat(host.getString("hostname")).isEqualTo("prod.example.com")
        assertThat(host.getLong("profileId")).isEqualTo(profileId)
        assertThat(host.getString("ipVersion")).isEqualTo("IPV6_ONLY")
        assertThat(host.has("hostKeyAlgo")).isFalse()
        assertThat(host.has("lastConnect")).isFalse()
        assertThat(host.has("postLogin")).isFalse()

        val portForward = json.getJSONArray("port_forwards").getJSONObject(0)
        assertThat(portForward.getString("sourceAddr")).isEqualTo("0.0.0.0")
        assertThat(portForward.has("destAddr")).isFalse()
    }

    @Test
    fun importFromJson_skipsExistingRowsAndRemapsChildrenToExistingIds() = runTest {
        val existingProfileId = database.profileDao().insert(
            createProfile(name = "Existing", fontSize = 10),
        )
        val existingHostId = database.hostDao().insert(
            createHost(
                nickname = "existing-host",
                hostname = "current.example.com",
                profileId = existingProfileId,
            ),
        )

        val results = exporter.importFromJson(
            """
            {
              "version": 999,
              "profiles": [
                {
                  "id": 100,
                  "name": "Existing",
                  "iconColor": "green",
                  "colorSchemeId": -1,
                  "fontFamily": null,
                  "fontSize": 42,
                  "delKey": "del",
                  "encoding": "UTF-8",
                  "emulation": "xterm-256color",
                  "forceSizeRows": null,
                  "forceSizeColumns": null
                }
              ],
              "hosts": [
                {
                  "id": 200,
                  "nickname": "existing-host",
                  "protocol": "ssh",
                  "username": "imported",
                  "hostname": "imported.example.com",
                  "port": 2200,
                  "color": "red",
                  "useKeys": 0,
                  "useAuthAgent": "no",
                  "postLogin": null,
                  "pubkeyId": -1,
                  "wantSession": 1,
                  "compression": 0,
                  "stayConnected": 0,
                  "quickDisconnect": 0,
                  "scrollbackLines": 200,
                  "useCtrlAltAsMetaKey": 0,
                  "jumpHostId": null,
                  "profileId": 100,
                  "ipVersion": "IPV4_AND_IPV6"
                }
              ],
              "port_forwards": [
                {
                  "id": 300,
                  "hostId": 200,
                  "nickname": "existing-forward",
                  "type": "local",
                  "sourceAddr": "127.0.0.1",
                  "sourcePort": 8080,
                  "destAddr": "localhost",
                  "destPort": 80
                }
              ]
            }
            """.trimIndent(),
            HostConfigJson.EXPORT_TABLES,
        )

        assertThat(results["profiles"]).isEqualTo(Pair(0, 1))
        assertThat(results["hosts"]).isEqualTo(Pair(0, 1))
        assertThat(results["port_forwards"]).isEqualTo(Pair(1, 0))

        val existingProfile = database.profileDao().getById(existingProfileId)
        assertThat(existingProfile?.fontSize).isEqualTo(10)

        val existingHost = database.hostDao().getById(existingHostId)
        assertThat(existingHost?.hostname).isEqualTo("current.example.com")

        val portForward = database.portForwardDao().getByHost(existingHostId).single()
        assertThat(portForward.nickname).isEqualTo("existing-forward")
        assertThat(portForward.hostId).isEqualTo(existingHostId)
    }

    @Test
    fun importFromJson_updatesSelfReferencingHostIds() = runTest {
        val results = exporter.importFromJson(
            """
            {
              "version": 8,
              "hosts": [
                {
                  "id": 50,
                  "nickname": "jump-host",
                  "protocol": "ssh",
                  "username": "jump",
                  "hostname": "jump.example.com",
                  "port": 22,
                  "color": "blue",
                  "useKeys": 0,
                  "useAuthAgent": "no",
                  "postLogin": null,
                  "pubkeyId": -1,
                  "wantSession": 1,
                  "compression": 0,
                  "stayConnected": 0,
                  "quickDisconnect": 0,
                  "scrollbackLines": 140,
                  "useCtrlAltAsMetaKey": 0,
                  "jumpHostId": null,
                  "profileId": null,
                  "ipVersion": "IPV4_AND_IPV6"
                },
                {
                  "id": 60,
                  "nickname": "target-host",
                  "protocol": "ssh",
                  "username": "target",
                  "hostname": "target.example.com",
                  "port": 22,
                  "color": "green",
                  "useKeys": 0,
                  "useAuthAgent": "no",
                  "postLogin": null,
                  "pubkeyId": -1,
                  "wantSession": 1,
                  "compression": 0,
                  "stayConnected": 0,
                  "quickDisconnect": 0,
                  "scrollbackLines": 140,
                  "useCtrlAltAsMetaKey": 0,
                  "jumpHostId": 50,
                  "profileId": null,
                  "ipVersion": "IPV4_AND_IPV6"
                }
              ]
            }
            """.trimIndent(),
            listOf("hosts"),
        )

        assertThat(results["hosts"]).isEqualTo(Pair(2, 0))

        val hosts = database.hostDao().getAll().associateBy { it.nickname }
        val jumpHost = hosts.getValue("jump-host")
        val targetHost = hosts.getValue("target-host")
        assertThat(targetHost.jumpHostId).isEqualTo(jumpHost.id)
        assertThat(targetHost.jumpHostId).isNotEqualTo(50L)
    }

    private fun createProfile(
        name: String,
        fontSize: Int = 10,
    ): Profile = Profile(
        name = name,
        fontSize = fontSize,
    )

    private fun createHost(
        nickname: String,
        hostname: String,
        hostKeyAlgo: String? = null,
        lastConnect: Long = 0,
        postLogin: String? = null,
        profileId: Long? = 1L,
        ipVersion: String = "IPV4_AND_IPV6",
    ): Host = Host(
        nickname = nickname,
        protocol = "ssh",
        username = "user",
        hostname = hostname,
        port = 22,
        hostKeyAlgo = hostKeyAlgo,
        lastConnect = lastConnect,
        color = "gray",
        useKeys = false,
        useAuthAgent = "no",
        postLogin = postLogin,
        pubkeyId = -1,
        wantSession = true,
        compression = false,
        stayConnected = false,
        quickDisconnect = false,
        scrollbackLines = 140,
        useCtrlAltAsMetaKey = false,
        profileId = profileId,
        ipVersion = ipVersion,
    )
}
