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
import org.connectbot.data.entity.KeyStorageType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PivSlotMigrationTest {
    @Test
    fun version18KeysSurviveAndPivMetadataRoundTripsInVersion19() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "piv-slot-migration"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/org.connectbot.data.ConnectBotDatabase/18.json").readText())
            .getJSONObject("database")
        val existingId = UUID.randomUUID()
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
            db.execSQL(
                "INSERT INTO pubkeys (id, nickname, type, private_key, public_key, encrypted, startup, confirmation, " +
                    "created_date, storage_type, allow_backup) VALUES (?, 'Existing', 'EC', X'01', X'02', 0, 0, 0, 1, 'EXPORTABLE', 1)",
                arrayOf(existingId.toString()),
            )
            db.version = 18
        }

        val database = Room.databaseBuilder(context, ConnectBotDatabase::class.java, name).build()
        try {
            val dao = database.pubkeyDao()
            val existing = requireNotNull(dao.getById(existingId))
            assertEquals(19, database.openHelper.writableDatabase.version)
            assertEquals("EC", existing.type)
            assertEquals(KeyStorageType.EXPORTABLE, existing.storageType)
            assertTrue(existing.privateKey!!.contentEquals(byteArrayOf(1)))
            assertNull(existing.pivSlot)
            assertNull(existing.credentialId)
            assertNull(existing.fido2RpId)
            assertNull(existing.fido2Transport)

            for (slot in listOf(0x9A, 0x9C, 0x82, 0x95)) {
                val piv = existing.copy(
                    id = UUID.randomUUID(),
                    nickname = "PIV-$slot",
                    privateKey = null,
                    storageType = KeyStorageType.PIV,
                    pivSlot = slot,
                    allowBackup = false,
                )
                dao.insert(piv)
                val restored = requireNotNull(dao.getById(piv.id))
                assertEquals(piv, restored)
                assertEquals(piv.hashCode(), restored.hashCode())
                assertTrue(restored.isPiv)
                assertFalse(restored.canExportPrivateKey)
                assertFalse(restored.canLoadIntoMemory)
            }
            assertEquals(listOf(existing), dao.getExportable())
            assertEquals(listOf(existing), dao.getBackupable())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
