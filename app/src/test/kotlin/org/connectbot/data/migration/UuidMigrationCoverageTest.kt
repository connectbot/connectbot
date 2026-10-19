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
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.connectbot.data.ColorSchemePresets
import org.connectbot.data.EntityIds
import org.connectbot.util.HostConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UuidMigrationCoverageTest {
    private fun fixture() = UuidMigrationFixture(ApplicationProvider.getApplicationContext<Context>()) {
        File("schemas/org.connectbot.data.ConnectBotDatabase/$it.json").readText()
    }

    @Test
    fun preservesEveryColumnAndIdentityAcrossReopen() {
        fixture().use {
            it.create()
            it.verifyReopen()
        }
    }

    @Test
    fun allDaosCanReadMigratedEntities() = runTest {
        fixture().use { fixture ->
            fixture.create()
            val database = fixture.open()
            fixture.verifyPreservation(database)
            fixture.verifyDaoReads(database)
        }
    }

    @Test
    fun nullableOrphanKnownHostIsRetainedWithoutItsHostLink() {
        fixture().use { fixture ->
            fixture.create(healthy = false) { db ->
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("UPDATE known_hosts SET host_id=999 WHERE id=1")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
            fixture.verifyReopen()
        }
    }

    @Test
    fun cascadeDeletesStillWorkAfterMigration() {
        fixture().use {
            it.create()
            it.verifyCascades()
        }
    }

    @Test
    fun failureDuringCopyRollsBackAndCanRetry() = rollback(UuidMigrationFixture.FailurePoint.COPY)

    @Test
    fun failureAfterDroppingHostsRollsBackAndCanRetry() = rollback(UuidMigrationFixture.FailurePoint.DROP)

    @Test
    fun failureDuringRenameRollsBackAndCanRetry() = rollback(UuidMigrationFixture.FailurePoint.RENAME)

    private fun rollback(point: UuidMigrationFixture.FailurePoint) {
        fixture().use {
            it.create()
            it.verifyRollback(point)
        }
    }

    @Test
    fun missingDefaultProfileDoesNotLoseCustomProfilesOrHosts() {
        fixture().use { fixture ->
            fixture.create { it.execSQL("DELETE FROM profiles WHERE id=1") }
            fixture.verifyReopen()
        }
    }

    @Test
    fun missingOptionalReferencesUseDocumentedFallbacks() {
        fixture().use { fixture ->
            fixture.create { db ->
                db.execSQL("UPDATE hosts SET jump_host_id=999, profile_id=999, pubkey_id=999 WHERE id=1")
                db.execSQL("UPDATE profiles SET color_scheme_id=999 WHERE id=1")
                db.execSQL("UPDATE automation_actions SET forward_id=999")
            }
            fixture.verifyReopen()
            val db = fixture.open().openHelper.readableDatabase
            val rows = fixture.snapshot(db::query)
            val host = rows.getValue("hosts").single { it["nickname"] == "hosts-0 世界" }
            assertEquals(null, host["jump_host_id"])
            assertEquals(null, host["profile_id"])
            assertEquals(HostConstants.PUBKEYID_ANY.toString(), host["pubkey_id"])
            assertEquals(EntityIds.DEFAULT_SCHEME.toString(), rows.getValue("profiles").single { it["name"] == "profiles-0 世界" }["color_scheme_id"])
            assertTrue(rows.getValue("automation_actions").all { it["forward_id"] == null })
        }
    }

    @Test
    fun everyBuiltInSchemeRetainsItsIdentity() {
        fixture().use { fixture ->
            fixture.create { db ->
                ColorSchemePresets.builtInSchemes.indices.forEach { index ->
                    db.execSQL("INSERT INTO profiles(id,name,color_scheme_id) VALUES(?,?,?)", arrayOf<Any>(10_000L + index, "preset-$index", -index - 1))
                }
            }
            fixture.verifyReopen()
        }
    }

    @Test
    fun invalidRequiredHostReferenceFailsWithoutLosingOriginalData() = invalidReference("port_forwards", "host_id")

    @Test
    fun invalidRequiredSchemeReferenceFailsWithoutLosingOriginalData() = invalidReference("color_palette", "scheme_id")

    @Test
    fun invalidRequiredAutomationHostFailsWithoutLosingOriginalData() = invalidReference("automation_actions", "host_id")

    private fun invalidReference(table: String, column: String) {
        fixture().use { fixture ->
            fixture.create(healthy = false) { db ->
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("UPDATE $table SET $column=999")
                db.execSQL("PRAGMA foreign_keys=ON")
            }
            val database = fixture.open()
            val failure = assertThrows(SQLiteConstraintException::class.java) { database.openHelper.writableDatabase }
            assertTrue(failure.message.orEmpty().contains(column))
            database.close()
            fixture.verifyOriginal()
        }
    }

    @Test
    fun populatedBatchPreservesEveryRow() {
        fixture().use { fixture ->
            fixture.create { db ->
                val columns = db.query("PRAGMA table_info(hosts)").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(1)) } }
                val names = columns.joinToString(",") { "`$it`" }
                val select = columns.joinToString(",") {
                    when (it) {
                        "id" -> "?"
                        "nickname" -> "?"
                        else -> "`$it`"
                    }
                }
                db.beginTransaction()
                try {
                    repeat(1000) { db.execSQL("INSERT INTO hosts($names) SELECT $select FROM hosts WHERE id=1", arrayOf<Any>(10_000_000_000L + it, "batch-$it")) }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
            fixture.verifyReopen()
        }
    }

    @Test
    fun productionStartupMigratesPreferencesAndPasswordsAndIsRepeatable() {
        fixture().use { fixture ->
            fixture.create()
            assertTrue(fixture.preferences.edit().putLong("defaultProfileId", UuidMigrationFixture.LARGE_ID).putString("unrelated", "keep").commit())
            assertTrue(fixture.passwords.edit().putString("password_${UuidMigrationFixture.LARGE_ID}", "opaque-ciphertext-世界").putString("password_999", "unrelated-secret").commit())
            val database = fixture.open(production = true)
            fixture.verifyPreservation(database)
            val ids = fixture.mappings(database.openHelper.readableDatabase)
            val host = ids.getValue("hosts" to UuidMigrationFixture.LARGE_ID)
            val profile = ids.getValue("profiles" to UuidMigrationFixture.LARGE_ID)
            assertEquals(profile, fixture.preferences.getString("defaultProfileId", null))
            assertEquals("keep", fixture.preferences.getString("unrelated", null))
            assertEquals("opaque-ciphertext-世界", fixture.passwords.getString("password_$host", null))
            assertFalse(fixture.passwords.contains("password_${UuidMigrationFixture.LARGE_ID}"))
            assertEquals("unrelated-secret", fixture.passwords.getString("password_999", null))
            val preferences = fixture.preferences.all.toMap()
            val passwords = fixture.passwords.all.toMap()
            database.close()
            fixture.verifyReopen(production = true)
            assertEquals(preferences, fixture.preferences.all)
            assertEquals(passwords, fixture.passwords.all)
        }
    }

    @Test
    fun productionStartupKeepsExistingDestinationPassword() {
        fixture().use { fixture ->
            fixture.create()
            val first = fixture.open()
            val host = fixture.mappings(first.openHelper.readableDatabase).getValue("hosts" to UuidMigrationFixture.LARGE_ID)
            first.close()
            assertTrue(fixture.passwords.edit().putString("password_$host", "new-value").putString("password_${UuidMigrationFixture.LARGE_ID}", "old-value").commit())
            fixture.open(production = true).openHelper.readableDatabase
            assertEquals("new-value", fixture.passwords.getString("password_$host", null))
            assertFalse(fixture.passwords.contains("password_${UuidMigrationFixture.LARGE_ID}"))
        }
    }

    @Test
    fun productionStartupUsesDefaultWhenPreferredProfileIsMissing() {
        fixture().use { fixture ->
            fixture.create()
            assertTrue(fixture.preferences.edit().putLong("defaultProfileId", 999).commit())
            fixture.open(production = true).openHelper.readableDatabase
            assertEquals(EntityIds.DEFAULT_PROFILE.toString(), fixture.preferences.getString("defaultProfileId", null))
        }
    }

    @Test
    fun productionStartupEnforcesForeignKeysOnWrites() {
        fixture().use { fixture ->
            fixture.create()
            val database = fixture.open(production = true)
            val db = database.openHelper.writableDatabase
            db.beginTransaction()
            try {
                fixture.assertHealthy(db)
                assertThrows(SQLiteConstraintException::class.java) {
                    db.execSQL("INSERT INTO port_forwards(id,host_id,nickname,type,source_port,dest_port) VALUES('invalid-forward','missing-host','invalid','local',8080,80)")
                }
            } finally {
                db.endTransaction()
            }
        }
    }

    @Test
    fun productionStartupLeavesAbsentPreferencesAbsent() {
        fixture().use { fixture ->
            fixture.create()
            fixture.verifyReopen(production = true)
            assertFalse(fixture.preferences.contains("defaultProfileId"))
            assertTrue(fixture.passwords.all.isEmpty())
        }
    }
}
