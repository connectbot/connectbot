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
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.connectbot.di.DatabaseModule
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class Version19RecoveryMigrationTest {
    @Test
    fun originalVersion19PreservesStartupKeysAndHosts() = runTest {
        assertMigration(hasPivSlot = false)
    }

    @Test
    fun shippedPivVersion19PreservesExistingSlotAndStartupKeys() = runTest {
        assertMigration(hasPivSlot = true)
    }

    private suspend fun assertMigration(hasPivSlot: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "connectbot.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/19.json").readText())
            .getJSONObject("database")
        assertEquals("c4c3049d41c3d15d5cfacfee549ebe5d", schema.getString("identityHash"))
        val privateKey = byteArrayOf(1, 2, 3, 4)
        val publicKey = byteArrayOf(5, 6, 7, 8)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
            if (hasPivSlot) {
                db.execSQL("ALTER TABLE pubkeys ADD COLUMN piv_slot INTEGER")
                db.execSQL("UPDATE room_master_table SET identity_hash = '45757c3c5f344ecec97598a680e1489c' WHERE id = 42")
            }
            db.execSQL(
                "INSERT INTO pubkeys (id, nickname, type, private_key, public_key, encrypted, startup, confirmation, " +
                    "created_date, storage_type, allow_backup) VALUES (?, 'Existing key', 'RSA', ?, ?, 1, 1, 0, 123, 'EXPORTABLE', 1)",
                arrayOf("00000000-0000-0000-0000-000000000001", privateKey, publicKey),
            )
            if (hasPivSlot) db.execSQL("UPDATE pubkeys SET piv_slot = 154")
            db.execSQL(
                "INSERT INTO hosts (id, nickname, protocol, hostname, username, port, last_connect, use_keys, " +
                    "pubkey_id, want_session, compression, stay_connected, quick_disconnect, scrollback_lines, " +
                    "use_ctrl_alt_as_meta_key) VALUES ('00000000-0000-0000-0000-000000000002', 'Existing host', " +
                    "'ssh', 'example.com', 'alice', 22, 123, 1, '00000000-0000-0000-0000-000000000001', 1, 0, 0, 0, 140, 0)",
            )
            db.version = 19
        }
        try {
            repeat(2) {
                val database = DatabaseModule.provideConnectBotDatabase(context)
                try {
                    val key = database.pubkeyDao().getStartupKeys().single()
                    assertEquals("Existing key", key.nickname)
                    assertArrayEquals(privateKey, key.privateKey)
                    assertArrayEquals(publicKey, key.publicKey)
                    assertTrue(key.encrypted)
                    assertEquals(if (hasPivSlot) 154 else null, key.pivSlot)
                    database.pubkeyDao().update(key.copy(confirmation = true))
                    assertEquals(key.pivSlot, database.pubkeyDao().getById(key.id)!!.pivSlot)
                    val host = database.hostDao().getAll().single()
                    assertEquals("Existing host", host.nickname)
                    assertEquals("example.com", host.hostname)
                    assertEquals("alice", host.username)
                    assertEquals(key.id, host.pubkeyId)
                    val db = database.openHelper.writableDatabase
                    assertEquals(20, db.version)
                    db.query("SELECT piv_slot FROM pubkeys").use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        if (hasPivSlot) assertEquals(154, cursor.getInt(0)) else assertTrue(cursor.isNull(0))
                    }
                    db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                    db.query("PRAGMA integrity_check").use {
                        assertTrue(it.moveToFirst())
                        assertEquals("ok", it.getString(0))
                    }
                } finally {
                    database.close()
                }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
