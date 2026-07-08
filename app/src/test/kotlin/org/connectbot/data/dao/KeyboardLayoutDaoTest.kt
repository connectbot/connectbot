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
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyboardLayout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardLayoutDaoTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var dao: KeyboardLayoutDao
    private lateinit var hostDao: HostDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.keyboardLayoutDao()
        hostDao = database.hostDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieve() = runTest {
        val id = dao.insert(KeyboardLayout(name = "Mine", keysJson = "{}"))
        assertThat(id).isGreaterThan(0)
        assertThat(dao.getById(id)?.name).isEqualTo("Mine")
    }

    @Test
    fun observeAllOrdersByName() = runTest {
        dao.insert(KeyboardLayout(name = "Zeta", keysJson = "{}"))
        dao.insert(KeyboardLayout(name = "Alpha", keysJson = "{}"))
        assertThat(dao.observeAll().first().map { it.name }).containsExactly("Alpha", "Zeta")
    }

    @Test
    fun nameExistsIsCaseInsensitiveAndExcludable() = runTest {
        val id = dao.insert(KeyboardLayout(name = "Custom", keysJson = "{}"))
        assertThat(dao.nameExists("custom")).isTrue()
        assertThat(dao.nameExists("Custom", excludeLayoutId = id)).isFalse()
    }

    @Test
    fun countHostsUsingCountsOverrides() = runTest {
        val layoutId = dao.insert(KeyboardLayout(name = "Used", keysJson = "{}"))
        hostDao.insert(testHost("h1", layoutId))
        hostDao.insert(testHost("h2", layoutId))
        hostDao.insert(testHost("h3", null))

        assertThat(dao.countHostsUsing(layoutId)).isEqualTo(2)
    }

    @Test
    fun clearHostReferencesNullsOverrides() = runTest {
        val layoutId = dao.insert(KeyboardLayout(name = "Doomed", keysJson = "{}"))
        val hostId = hostDao.insert(testHost("h1", layoutId))

        dao.clearHostReferences(layoutId)

        assertThat(hostDao.getById(hostId)?.keyboardLayoutId).isNull()
        assertThat(dao.countHostsUsing(layoutId)).isEqualTo(0)
    }

    private fun testHost(nickname: String, keyboardLayoutId: Long?): Host = Host(
        nickname = nickname,
        protocol = "ssh",
        username = "user",
        hostname = "example.com",
        port = 22,
        keyboardLayoutId = keyboardLayoutId,
    )
}
