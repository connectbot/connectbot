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

package org.connectbot.data.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.connectbot.data.ConnectBotDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MoshTimeoutMigrationTest {
    @Test
    fun existingHostsRetainSettingsAndDefaultToServerTimeout() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "mosh-timeout-migration"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/11.json").readText())
            .getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            db.execSQL(
                "INSERT INTO hosts (nickname, protocol, hostname, username, port, last_connect, use_keys, " +
                    "pubkey_id, want_session, compression, stay_connected, quick_disconnect, scrollback_lines, " +
                    "use_ctrl_alt_as_meta_key) VALUES ('Existing', 'mosh', 'example.com', 'alice', 22, 0, 1, -1, 1, 0, 0, 0, 140, 0)",
            )
            db.version = 11
        }
        val database = Room.databaseBuilder(context, ConnectBotDatabase::class.java, name)
            .addMigrations(ConnectBotDatabase.MIGRATION_10_11).build()
        try {
            val host = database.hostDao().getAll().single()
            assertEquals("Existing", host.nickname)
            assertEquals("example.com", host.hostname)
            assertEquals("alice", host.username)
            assertNull(host.moshNetworkTimeout)
            for (timeout in listOf(0, 86400, null)) {
                database.hostDao().update(host.copy(moshNetworkTimeout = timeout))
                assertEquals(timeout, database.hostDao().getAll().single().moshNetworkTimeout)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
