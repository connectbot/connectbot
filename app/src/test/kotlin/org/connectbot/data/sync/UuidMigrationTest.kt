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

package org.connectbot.data.sync

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.EntityIds
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class UuidMigrationTest {
    @Test
    fun migrationPreservesReferencesAndData() = verifyMigration(9)

    @Test
    fun migrationPreservesVersion14SettingsAndAutomation() = verifyMigration(14)

    private fun verifyMigration(version: Int) = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "uuid-migration-${UUID.randomUUID()}.db"
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/$version.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val old = helper.writableDatabase
        old.execSQL("INSERT INTO profiles(id,name,color_scheme_id) VALUES(1,'Default',-1)")
        val hostSchema = (0 until schema.getJSONArray("entities").length()).map { schema.getJSONArray("entities").getJSONObject(it) }.first { it.getString("tableName") == "hosts" }
        val values = ContentValues()
        val fields = hostSchema.getJSONArray("fields")
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            if (field.optBoolean("notNull", false) && !field.has("defaultValue")) {
                if (field.getString("affinity") == "INTEGER") values.put(field.getString("columnName"), 0) else values.put(field.getString("columnName"), "")
            }
        }
        values.put("id", 7)
        values.put("nickname", "jump")
        values.put("protocol", "ssh")
        values.put("hostname", "jump.example")
        values.put("port", 22)
        values.put("pubkey_id", -1)
        values.put("profile_id", 1)
        old.insert("hosts", 2, values)
        values.put("id", 9)
        values.put("nickname", "target")
        values.put("jump_host_id", 7)
        old.insert("hosts", 2, values)
        old.execSQL("INSERT INTO port_forwards(id,host_id,nickname,type,source_port,dest_port) VALUES(2,9,'forward','local',8080,80)")
        old.execSQL("INSERT INTO known_hosts(id,host_id,hostname,port,host_key_algo,host_key) VALUES(3,9,'jump.example',22,'ssh-ed25519',X'010203')")
        if (version == 14) {
            old.execSQL("UPDATE hosts SET mosh_port=60001, mosh_server='custom-mosh', locale='de_DE.UTF-8', mosh_network_timeout=120, telnet_echo_mode='ON' WHERE id=9")
            old.execSQL("UPDATE port_forwards SET start_enabled=0 WHERE id=2")
            old.execSQL("UPDATE profiles SET bold_as_bright=0 WHERE id=1")
            old.execSQL("INSERT INTO automation_actions(id,host_id,position,type,text,regex,duration_ms,`key`,modifiers,forward_id,failure_policy) VALUES('00000000-0000-0000-0000-000000000007',9,0,'ENABLE_FORWARD','',0,30000,1,0,2,'STOP')")
        }
        helper.close()
        val room = Room.databaseBuilder(context, ConnectBotDatabase::class.java, name).addMigrations(ConnectBotDatabase.MIGRATION_10_11, ConnectBotDatabase.MIGRATION_14_15).allowMainThreadQueries().build()
        try {
            val hosts = room.hostDao().getAll()
            val target = hosts.first { it.nickname == "target" }
            val jump = hosts.first { it.nickname == "jump" }
            if (version == 14) {
                assertEquals(60001, target.moshPort)
                assertEquals("custom-mosh", target.moshServer)
                assertEquals("de_DE.UTF-8", target.locale)
                assertEquals(120, target.moshNetworkTimeout)
                assertFalse(room.profileDao().getAll().single().boldAsBright)
                val forward = room.portForwardDao().getByHost(target.id).single()
                assertFalse(forward.startEnabled)
                val action = room.automationActionDao().getByHost(target.id).single()
                assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000007"), action.id)
                assertEquals(forward.id, action.forwardId)
            }
            assertEquals(jump.id, target.jumpHostId)
            assertEquals(EntityIds.DEFAULT_PROFILE, target.profileId)
            assertEquals(EntityIds.LOCAL, target.repositoryId)
            assertEquals(target.id, room.portForwardDao().getByHost(target.id).single().hostId)
            assertArrayEquals(byteArrayOf(1, 2, 3), room.knownHostDao().getByHostId(target.id).single().hostKey)
            room.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            room.openHelper.readableDatabase.query("SELECT uuid FROM legacy_id_map WHERE entity='hosts' AND old_id=9").use {
                assertTrue(it.moveToFirst())
                assertEquals(target.id.toString(), it.getString(0))
            }
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
