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
import org.connectbot.data.keyboard.KeyboardDefaults
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class KeyboardMigrationTest {
    @Test
    fun existingProfilesUseSeededUuidKeyboardAfterUpgrade() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "keyboard-migration"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/9.json").readText())
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
            db.execSQL("INSERT INTO profiles (name) VALUES ('Existing')")
            db.version = 9
        }
        val database = Room.databaseBuilder(context, ConnectBotDatabase::class.java, name).build()
        try {
            val profile = database.profileDao().getAll().single()
            assertEquals(null, profile.keyboardLayoutId)
            val layout = database.keyboardDao().layouts().single()
            assertEquals(KeyboardDefaults.layoutId, layout.id)
            assertEquals(45f, layout.buttonWidth)
            assertEquals(30f, layout.buttonHeight)
            assertEquals(layout.id, database.keyboardDao().settings()!!.defaultLayoutId)
            assertEquals(KeyboardDefaults.items(layout.id), database.keyboardDao().items().sortedBy { it.column })
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
