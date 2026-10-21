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
import org.connectbot.data.EntityIds
import org.connectbot.data.sync.SyncDatabase
import org.connectbot.util.TerminalFontWeight
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FontWeightMigrationTest {
    @Test
    fun upgradePreservesAppearanceAndSyncsWeightEdits() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "font-weight-migration"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/17.json").readText()).getJSONObject("database")
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
            db.execSQL("INSERT INTO profiles (id, name, color_scheme_id, bold_as_bright) VALUES ('${EntityIds.DEFAULT_PROFILE}', 'Existing', '${EntityIds.DEFAULT_SCHEME}', 0)")
            // An existing sync trigger must be rebuilt with the new portable column.
            db.execSQL("CREATE TRIGGER sync_profiles_update BEFORE UPDATE ON profiles BEGIN SELECT 1; END")
            db.version = 17
        }
        val database = Room.databaseBuilder(context, ConnectBotDatabase::class.java, name).build()
        try {
            val profile = database.profileDao().getAll().single()
            assertEquals(TerminalFontWeight.NORMAL, profile.fontWeight)
            assertEquals(false, profile.boldAsBright)
            val db = database.openHelper.writableDatabase
            SyncDatabase.install(db)
            database.profileDao().update(profile.copy(fontWeight = TerminalFontWeight.BOLD))
            assertEquals(TerminalFontWeight.BOLD, database.profileDao().getAll().single().fontWeight)
            db.query("SELECT operation FROM sync_outbox WHERE kind='profiles'").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("put", cursor.getString(0))
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
