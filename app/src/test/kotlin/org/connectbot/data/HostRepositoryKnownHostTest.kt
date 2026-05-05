/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.entity.Host
import org.connectbot.util.SecurePasswordStorage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Regression test for https://github.com/connectbot/connectbot/issues/2060
 *
 * When the app is launched via an ssh:// URI against an empty host database,
 * [org.connectbot.service.TerminalManager.openConnection] synthesises an
 * in-memory Host with a negative "temporary" id and never inserts it into the
 * hosts table. If the SSH transport then calls
 * [HostRepository.saveKnownHost] (directly or via the verifier's
 * addServerHostKey callback), Room tries to insert a KnownHost row whose
 * host_id foreign key points at a non-existent hosts row and SQLite aborts
 * the transaction with SQLITE_CONSTRAINT_FOREIGNKEY (code 787). That
 * exception propagates out of the verifier callback and aborts the SSH
 * session with "Key exchange was not finished".
 */
@RunWith(AndroidJUnit4::class)
class HostRepositoryKnownHostTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var repository: HostRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HostRepository(
            context,
            database,
            database.hostDao(),
            database.portForwardDao(),
            database.knownHostDao(),
            mock<SecurePasswordStorage>(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveKnownHost_withTemporaryHost_doesNotThrowForeignKeyFailure() = runTest {
        // Simulates the URI-launch path on an empty database: the host exists
        // only in memory with a negative id assigned by
        // TerminalManager.generateTemporaryHostId().
        val temporaryHost = Host(
            id = -1L,
            nickname = "",
            protocol = "ssh",
            username = "root",
            hostname = "127.0.0.1",
            port = 10000,
        )

        repository.saveKnownHost(
            host = temporaryHost,
            hostname = "127.0.0.1",
            port = 10000,
            serverHostKeyAlgorithm = "ssh-ed25519",
            serverHostKey = "server-ed25519-key".toByteArray(),
        )

        // Nothing should have been persisted against the bogus host id: the
        // hosts table is still empty and no dangling known_hosts row exists.
        assertThat(database.hostDao().getAll()).isEmpty()
        assertThat(database.knownHostDao().getAll()).isEmpty()
    }
}
