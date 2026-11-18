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
import org.connectbot.data.sync.SyncDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class JumpHostFallbackMigrationTest {
    @Test
    fun existingJumpHostsKeepAlwaysUsingJumpAndSyncFallbackEdits() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "jump-host-fallback-migration"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/20.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            db.execSQL(
                "INSERT INTO hosts (id, nickname, protocol, username, hostname, port, last_connect, use_keys, pubkey_id, " +
                    "want_session, compression, stay_connected, quick_disconnect, scrollback_lines, use_ctrl_alt_as_meta_key, jump_host_id) " +
                    "VALUES ('00000000-0000-0000-0000-000000000001', 'Existing', 'ssh', 'alice', 'example.com', 22, 0, 1, " +
                    "'00000000-0000-0000-0000-000000000000', 1, 0, 0, 0, 140, 0, '00000000-0000-0000-0000-000000000002')",
            )
            // An existing sync trigger must be rebuilt with the new portable column.
            db.execSQL("CREATE TRIGGER sync_hosts_update BEFORE UPDATE ON hosts BEGIN SELECT 1; END")
            db.version = 20
        }
        val database = Room.databaseBuilder(context, ConnectBotDatabase::class.java, name).build()
        try {
            val host = database.hostDao().getAll().single()
            assertEquals("example.com", host.hostname)
            assertFalse(host.jumpHostFallback)
            assertEquals(3, host.jumpHostFallbackTimeout)
            val db = database.openHelper.writableDatabase
            SyncDatabase.install(db)
            database.hostDao().update(host.copy(jumpHostFallback = true))
            assertTrue(database.hostDao().getAll().single().jumpHostFallback)
            db.query("SELECT operation FROM sync_outbox WHERE kind='hosts'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("put", cursor.getString(0))
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
