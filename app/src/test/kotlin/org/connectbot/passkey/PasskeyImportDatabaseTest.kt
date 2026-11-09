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

package org.connectbot.passkey

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.PasskeyEntriesChanged
import org.connectbot.data.PasskeyIdentityConflict
import org.connectbot.data.PasskeyNicknameExists
import org.connectbot.data.entity.Host
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PasskeyImportDatabaseTest {
    private lateinit var db: ConnectBotDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ConnectBotDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        db.close()
    }

    private fun candidate() = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().let {
        val public = it.public as ECPublicKey
        fun bytes(value: BigInteger): ByteArray {
            val raw = value.toByteArray().takeLast(32).toByteArray()
            return ByteArray(32 - raw.size) + raw
        }
        PasskeySupport.fromPublicKey(byteArrayOf(1), byteArrayOf(4) + bytes(public.w.affineX) + bytes(public.w.affineY), "")
    }

    @Test fun reconnectPreservesHostReferencesAndCurrentSettings() = runBlocking {
        val imported = candidate()
        val existing = imported.copy(id = UUID.randomUUID(), nickname = "server-key", credentialId = null, fido2RpId = null, confirmation = true)
        db.pubkeyDao().insert(existing)
        val host = Host(nickname = "host", hostname = "server.example", pubkeyId = existing.id)
        db.hostDao().insert(host)
        // A setting changed after the provider completed: use the current database row.
        db.pubkeyDao().update(existing.copy(allowBackup = true))
        val saved = db.pubkeyDao().importPasskey(imported, existing.id, "ignored")
        assertEquals(existing.id, saved.id)
        assertEquals(existing.nickname, saved.nickname)
        assertEquals(true, saved.confirmation)
        assertEquals(true, saved.allowBackup)
        assertEquals(existing.id, db.hostDao().getById(host.id)?.pubkeyId)
        assertEquals(1, db.pubkeyDao().getAll().size)
        assertArrayEquals(imported.credentialId, saved.credentialId)
    }

    @Test fun rechecksMatchesBeforeCreatingNewEntry() = runBlocking {
        val imported = candidate()
        val appeared = imported.copy(nickname = "appeared")
        db.pubkeyDao().insert(appeared)
        try {
            db.pubkeyDao().importPasskey(imported.copy(id = UUID.randomUUID()), null, "duplicate")
            fail("A stale import must not create a duplicate")
        } catch (_: PasskeyEntriesChanged) {
            assertEquals(listOf(appeared), db.pubkeyDao().getAll())
        }
    }

    @Test fun conflictsAndDuplicateNicknamesLeaveDatabaseUnchanged() = runBlocking {
        val imported = candidate()
        val unrelated = candidate().copy(nickname = "existing")
        db.pubkeyDao().insert(unrelated)
        try {
            db.pubkeyDao().importPasskey(imported, null, "new")
            fail("A credential ID conflict must not overwrite a public key")
        } catch (_: PasskeyIdentityConflict) {
            assertEquals(listOf(unrelated), db.pubkeyDao().getAll())
        }
        val differentId = imported.copy(credentialId = byteArrayOf(2))
        try {
            db.pubkeyDao().importPasskey(differentId, null, "existing")
            fail("A duplicate nickname must not overwrite another key")
        } catch (_: PasskeyNicknameExists) {
            assertEquals(listOf(unrelated), db.pubkeyDao().getAll())
        }
        val created = db.pubkeyDao().importPasskey(differentId, null, "new")
        assertEquals("new", created.nickname)
        assertEquals(2, db.pubkeyDao().getAll().size)
    }
}
