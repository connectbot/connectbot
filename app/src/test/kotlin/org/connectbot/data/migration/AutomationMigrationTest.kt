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
import org.connectbot.data.entity.AutomationActionType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AutomationMigrationTest {
    @Test
    fun migratesVerbatimTextAndDefaultsForwardsToEnabled() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "automation-migration"
        context.deleteDatabase(name)
        val texts = listOf(null, "", " \t\n", "echo '世界'\r\n\u0003", "\u0000")
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/10.json").readText()).getJSONObject("database")
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
            texts.forEachIndexed { i, text ->
                db.execSQL(
                    "INSERT INTO hosts (id,nickname,protocol,username,hostname,port,last_connect,use_keys,pubkey_id,want_session,compression,stay_connected,quick_disconnect,scrollback_lines,use_ctrl_alt_as_meta_key,post_login) VALUES (?,?,'ssh','user','example.com',22,0,1,-1,1,0,0,0,140,0,?)",
                    arrayOf<Any?>(i + 1, "host-$i", text),
                )
            }
            db.execSQL("INSERT INTO port_forwards (host_id,nickname,type,source_port,dest_port) VALUES (1,'forward','local',8080,80)")
            db.version = 10
        }
        val database = Room.databaseBuilder(context, ConnectBotDatabase::class.java, name)
            .addMigrations(ConnectBotDatabase.MIGRATION_10_11).build()
        try {
            val ids = mutableSetOf<String>()
            texts.forEachIndexed { i, text ->
                val actions = database.automationActionDao().getByHost(i + 1L)
                if (text.isNullOrEmpty()) {
                    assertTrue(actions.isEmpty())
                } else {
                    val action = actions.single()
                    assertEquals(text, action.text)
                    assertEquals(AutomationActionType.SEND_TEXT, action.type)
                    assertEquals(4, UUID.fromString(action.id).version())
                    assertTrue(ids.add(action.id))
                }
                assertNull(database.hostDao().getById(i + 1L)!!.postLogin)
            }
            assertTrue(database.portForwardDao().getByHost(1).single().startEnabled)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
