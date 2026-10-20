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
import kotlinx.coroutines.test.runTest
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.HostCryptoConfig
import org.connectbot.data.sync.SyncDatabase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class CryptoPolicyPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun customConfigurationRoundTripsThroughDatabaseAndExport() = runTest {
        val database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        val imported = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        try {
            val crypto = HostCryptoConfig("CUSTOM", "^curve25519-sha256,+future@example.com", "aes256-ctr", "-hmac-sha1", "ssh-ed25519", "+ssh-rsa", 3072, 4096)
            val host = Host(nickname = "Policy host", hostname = "example.com", crypto = crypto)
            database.hostDao().insert(host)
            assertEquals(crypto, database.hostDao().getById(host.id)!!.crypto)
            val json = HostConfigJson.exportToJson(context, database).first
            HostConfigJson.importFromJson(context, imported, json)
            assertEquals(crypto, imported.hostDao().getAll().single().crypto)
            SyncDatabase.install(database.openHelper.writableDatabase)
            database.hostDao().update(host.copy(crypto = crypto.copy(preset = "FUTURE")))
            database.openHelper.writableDatabase.query("SELECT operation FROM sync_outbox WHERE kind='hosts' AND object_id=?", arrayOf(host.id.toString())).use {
                assertTrue(it.moveToFirst())
                assertEquals("put", it.getString(0))
            }
        } finally {
            imported.close()
            database.close()
        }
    }

    @Test
    fun version15MigrationInheritsDefaultAndRefreshesExistingSyncTrigger() = runTest {
        val name = "crypto-policy-migration-test"
        context.deleteDatabase(name)
        val id = UUID.randomUUID()
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/15.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            db.execSQL("INSERT INTO hosts (id,nickname,protocol,hostname,username,port,last_connect,use_keys,pubkey_id,want_session,compression,stay_connected,quick_disconnect,scrollback_lines,use_ctrl_alt_as_meta_key) VALUES (?, 'Existing', 'ssh', 'example.com', 'alice', 22, 0, 1, ?, 1, 0, 0, 0, 140, 0)", arrayOf(id.toString(), org.connectbot.util.HostConstants.PUBKEYID_ANY.toString()))
            db.execSQL("CREATE TRIGGER sync_hosts_update AFTER UPDATE ON hosts BEGIN SELECT 1; END")
            db.version = 15
        }
        val database = Room.databaseBuilder(context, ConnectBotDatabase::class.java, name).addCallback(object : RoomDatabaseCallback() {}).build()
        try {
            val host = database.hostDao().getById(id)!!
            assertEquals(HostCryptoConfig(), host.crypto)
            database.openHelper.writableDatabase.query("SELECT sql FROM sqlite_master WHERE name='sync_hosts_update'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getString(0).contains("crypto_policy"))
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private open class RoomDatabaseCallback : androidx.room.RoomDatabase.Callback() {
        override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            SyncDatabase.install(db)
        }
    }
}
