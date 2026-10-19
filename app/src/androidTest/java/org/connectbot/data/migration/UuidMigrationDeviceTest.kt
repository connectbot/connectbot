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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.connectbot.util.SecurePasswordStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Exercises the platform SQLite engine and actual production onOpen callbacks. */
@RunWith(AndroidJUnit4::class)
class UuidMigrationDeviceTest {
    private fun fixture(): UuidMigrationFixture {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return UuidMigrationFixture(instrumentation.targetContext) { version ->
            instrumentation.context.assets.open("org.connectbot.data.ConnectBotDatabase/$version.json").bufferedReader().use { it.readText() }
        }
    }

    @Test
    fun populatedProductionUpgradePreservesDataAndSurvivesReopen() {
        fixture().use {
            it.create()
            it.verifyReopen(production = true)
        }
    }

    @Test
    fun allDaosCanReadMigratedEntities() = runBlocking {
        fixture().use { fixture ->
            fixture.create()
            val database = fixture.open(production = true)
            fixture.verifyPreservation(database)
            fixture.verifyDaoReads(database)
        }
    }

    @Test
    fun everyHistoricalUpgradePreservesData() {
        (1..13).forEach { version ->
            fixture().use {
                it.create(version)
                it.verifyReopen(production = true)
            }
        }
    }

    @Test
    fun cascadeDeletesStillWork() {
        fixture().use {
            it.create()
            it.verifyCascades()
        }
    }

    @Test
    fun failureDuringCopyRollsBackAndRetries() = rollback(UuidMigrationFixture.FailurePoint.COPY)

    @Test
    fun failureAfterDropRollsBackAndRetries() = rollback(UuidMigrationFixture.FailurePoint.DROP)

    @Test
    fun failureDuringRenameRollsBackAndRetries() = rollback(UuidMigrationFixture.FailurePoint.RENAME)

    private fun rollback(point: UuidMigrationFixture.FailurePoint) {
        fixture().use {
            it.create()
            it.verifyRollback(point)
        }
    }

    @Test
    fun encryptedPasswordRemainsDecryptableAfterProductionStartup() {
        fixture().use { fixture ->
            fixture.create()
            val storage = SecurePasswordStorage(fixture.context)
            val temporaryId = UUID.randomUUID()
            val plaintext = "password-世界-\u0000-\r\n"
            storage.savePassword(temporaryId, plaintext)
            assertEquals(plaintext, storage.getPassword(temporaryId))
            val ciphertext = fixture.passwords.getString("password_$temporaryId", null)
            assertNotNull(ciphertext)
            assertTrue(
                fixture.passwords.edit().remove("password_$temporaryId")
                    .putString("password_${UuidMigrationFixture.LARGE_ID}", ciphertext).commit(),
            )
            assertTrue(fixture.preferences.edit().putLong("defaultProfileId", UuidMigrationFixture.LARGE_ID).commit())
            val database = fixture.open(production = true)
            fixture.verifyPreservation(database)
            val ids = fixture.mappings(database.openHelper.readableDatabase)
            val host = ids.getValue("hosts" to UuidMigrationFixture.LARGE_ID)
            assertEquals(ids.getValue("profiles" to UuidMigrationFixture.LARGE_ID), fixture.preferences.getString("defaultProfileId", null))
            assertEquals(ciphertext, fixture.passwords.getString("password_$host", null))
            assertFalse(fixture.passwords.contains("password_${UuidMigrationFixture.LARGE_ID}"))
            assertEquals(plaintext, storage.getPassword(UUID.fromString(host)))
            database.close()
            fixture.verifyReopen(production = true)
            assertEquals(plaintext, SecurePasswordStorage(fixture.context).getPassword(UUID.fromString(host)))
        }
    }
}
