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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostConfigJsonTest {

    private lateinit var database: ConnectBotDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importFromJson_ignoresNewerVersionAndUnknownFields() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val counts = HostConfigJson.importFromJson(
            context,
            database,
            """
            {
              "version": 9,
              "branchOnlyTopLevel": true,
              "profiles": [
                {
                  "id": 1,
                  "name": "Default",
                  "iconColor": "blue",
                  "colorSchemeId": -1,
                  "fontFamily": null,
                  "fontSize": 12,
                  "delKey": "del",
                  "encoding": "UTF-8",
                  "emulation": "xterm-256color",
                  "forceSizeRows": null,
                  "forceSizeColumns": null,
                  "branchOnlyProfileField": "ignored"
                }
              ],
              "hosts": [
                {
                  "id": 10,
                  "nickname": "branch-host",
                  "protocol": "ssh",
                  "username": "user",
                  "hostname": "example.com",
                  "port": 22,
                  "color": "red",
                  "useKeys": 1,
                  "useAuthAgent": "no",
                  "postLogin": null,
                  "pubkeyId": -1,
                  "wantSession": 1,
                  "compression": 0,
                  "stayConnected": 0,
                  "quickDisconnect": 0,
                  "scrollbackLines": 140,
                  "useCtrlAltAsMetaKey": 0,
                  "profileId": 1,
                  "ipVersion": "IPV4_AND_IPV6",
                  "branchOnlyHostField": "ignored"
                }
              ],
              "port_forwards": [
                {
                  "id": 20,
                  "hostId": 10,
                  "nickname": "web",
                  "type": "local",
                  "sourceAddr": "0.0.0.0",
                  "sourcePort": 8080,
                  "destAddr": "localhost",
                  "destPort": 80,
                  "branchOnlyForwardField": "ignored"
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(counts.hostsImported).isEqualTo(1)
        assertThat(counts.profilesImported).isEqualTo(1)

        val host = database.hostDao().getAll().single()
        assertThat(host.nickname).isEqualTo("branch-host")

        val portForward = database.portForwardDao().getByHost(host.id).single()
        assertThat(portForward.sourceAddr).isEqualTo("0.0.0.0")
        assertThat(portForward.sourcePort).isEqualTo(8080)
    }
}
