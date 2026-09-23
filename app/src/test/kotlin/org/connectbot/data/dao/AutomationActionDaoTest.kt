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

package org.connectbot.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.HostConfigJson
import org.connectbot.data.entity.AutomationAction
import org.connectbot.data.entity.AutomationActionType
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutomationActionDaoTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun database() = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
    private fun host(name: String) = Host(nickname = name, protocol = "ssh", username = "user", hostname = "example.com", port = 22)

    @Test
    fun reorderPreservesIdentityAndHostDeletionCascades() = runTest {
        val db = database()
        try {
            val hostId = db.hostDao().insert(host("one"))
            val first = AutomationAction(hostId = hostId, text = "first")
            val second = AutomationAction(hostId = hostId, text = "second")
            val dao = db.automationActionDao()
            dao.replace(hostId, listOf(first, second))
            dao.replace(hostId, listOf(second, first.copy(text = "edited")))
            val saved = dao.getByHost(hostId)
            assertEquals(listOf(second.id, first.id), saved.map { it.id })
            assertEquals(listOf(0, 1), saved.map { it.position })
            assertEquals("edited", saved[1].text)
            db.hostDao().delete(db.hostDao().getById(hostId)!!)
            assertTrue(dao.getByHost(hostId).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun failedReplacementRollsBackAndCannotStealAnotherHostsUuid() = runTest {
        val db = database()
        try {
            val firstHost = db.hostDao().insert(host("one"))
            val secondHost = db.hostDao().insert(host("two"))
            val a = AutomationAction(hostId = firstHost, text = "first")
            val b = AutomationAction(hostId = secondHost, text = "second")
            val dao = db.automationActionDao()
            dao.replace(firstHost, listOf(a))
            dao.replace(secondHost, listOf(b))
            assertTrue(runCatching { dao.replace(firstHost, listOf(b.copy(hostId = firstHost))) }.isFailure)
            assertEquals(a.id, dao.getByHost(firstHost).single().id)
            assertEquals(b.id, dao.getByHost(secondHost).single().id)
        } finally {
            db.close()
        }
    }

    @Test
    fun exportImportPreservesUuidAndRemapsForwardAndHostIds() = runTest {
        val source = database()
        val target = database()
        try {
            val hostId = source.hostDao().insert(host("source"))
            val forwardId = source.portForwardDao().insert(
                PortForward(
                    hostId = hostId,
                    nickname = "web",
                    type = "local",
                    sourcePort = 8080,
                    destAddr = "localhost",
                    destPort = 80,
                    startEnabled = false,
                ),
            )
            val action = AutomationAction(hostId = hostId, type = AutomationActionType.ENABLE_FORWARD, forwardId = forwardId)
            source.automationActionDao().replace(hostId, listOf(action))
            val otherHost = target.hostDao().insert(host("existing"))
            target.portForwardDao().insert(PortForward(hostId = otherHost, nickname = "other", type = "local", sourcePort = 8081, destAddr = "localhost", destPort = 80))
            val json = HostConfigJson.exportToJson(context, source).first
            HostConfigJson.importFromJson(context, target, json)
            val importedHost = target.hostDao().getAll().single { it.nickname == "source" }
            val importedForward = target.portForwardDao().getByHost(importedHost.id).single()
            val imported = target.automationActionDao().getByHost(importedHost.id).single()
            assertEquals(action.id, imported.id)
            assertNotEquals(hostId, imported.hostId)
            assertEquals(importedForward.id, imported.forwardId)
            assertNotEquals(forwardId, imported.forwardId)
            assertFalse(importedForward.startEnabled)
            HostConfigJson.importFromJson(context, target, json)
            assertEquals(1, target.automationActionDao().getByHost(importedHost.id).size)
        } finally {
            source.close()
            target.close()
        }
    }

    @Test
    fun duplicateHostAllocatesNewActionIdsAndRemapsForwards() = runTest {
        val db = database()
        try {
            val source = host("original").copy(id = db.hostDao().insert(host("original")))
            val forwardId = db.portForwardDao().insert(
                PortForward(hostId = source.id, nickname = "web", type = "local", sourcePort = 8080, destAddr = "localhost", destPort = 80, startEnabled = false),
            )
            val action = AutomationAction(hostId = source.id, type = AutomationActionType.ENABLE_FORWARD, forwardId = forwardId)
            db.automationActionDao().replace(source.id, listOf(action))
            val repository = org.connectbot.data.HostRepository(
                context,
                db,
                db.hostDao(),
                db.portForwardDao(),
                db.knownHostDao(),
                org.mockito.Mockito.mock(org.connectbot.util.SecurePasswordStorage::class.java),
            )
            val duplicate = repository.duplicateHostSettings(source.id, source.copy(nickname = "copy"))
            val copied = db.automationActionDao().getByHost(duplicate.id).single()
            val copiedForward = db.portForwardDao().getByHost(duplicate.id).single()
            assertNotEquals(action.id, copied.id)
            assertEquals(copiedForward.id, copied.forwardId)
            assertFalse(copiedForward.startEnabled)
            assertEquals(action.id, db.automationActionDao().getByHost(source.id).single().id)
        } finally {
            db.close()
        }
    }
}
