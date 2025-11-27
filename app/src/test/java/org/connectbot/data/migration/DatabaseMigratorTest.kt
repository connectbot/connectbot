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

package org.connectbot.data.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigratorTest {

    private lateinit var context: Context
    private lateinit var migrator: DatabaseMigrator
    private lateinit var database: ConnectBotDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val legacyHostReader = LegacyHostDatabaseReader(context)
        val legacyPubkeyReader = LegacyPubkeyDatabaseReader(context)
        migrator = DatabaseMigrator(context, database, legacyHostReader, legacyPubkeyReader)
    }

    @After
    fun tearDown() {
        database.close()
        migrator.resetMigrationState()
    }

    @Test
    fun initialStateShouldBeNotStarted() = runTest {
        val state = migrator.migrationState.first()
        assertThat(state.status).isEqualTo(MigrationStatus.NOT_STARTED)
        assertThat(state.progress).isEqualTo(0f)
        assertThat(state.currentStep).isEmpty()
    }

    @Test
    fun isMigrationNeededReturnsFalseWhenNoLegacyDatabases() = runTest {
        // In test environment, there should be no legacy databases
        val needed = migrator.isMigrationNeeded()
        assertThat(needed).isFalse()
    }


    @Test
    fun resetMigrationStateWorks() = runTest {
        migrator.resetMigrationState()
        val state = migrator.migrationState.first()
        assertThat(state.status).isEqualTo(MigrationStatus.NOT_STARTED)
        assertThat(state.progress).isEqualTo(0f)
    }

    @Test
    fun migrationResultTypesExist() {
        // Verify the sealed class structure
        val success = MigrationResult.Success(
            hostsMigrated = 1,
            pubkeysMigrated = 2,
            portForwardsMigrated = 3,
            knownHostsMigrated = 4,
            colorSchemesMigrated = 5
        )
        assertThat(success).isInstanceOf(MigrationResult::class.java)

        val failure = MigrationResult.Failure(Exception("test"))
        assertThat(failure).isInstanceOf(MigrationResult::class.java)
    }

    @Test
    fun migrationStatusEnumValues() {
        val statuses = MigrationStatus.entries.toTypedArray()
        assertThat(statuses).contains(
            MigrationStatus.NOT_STARTED,
            MigrationStatus.IN_PROGRESS,
            MigrationStatus.COMPLETED,
            MigrationStatus.FAILED
        )
    }

    @Test
    fun legacyDataStructureIsValid() {
        val legacyData = LegacyData(
            hosts = emptyList(),
            portForwards = emptyList(),
            knownHosts = emptyList(),
            colorSchemes = emptyList(),
            colorPalettes = emptyList(),
            pubkeys = emptyList()
        )

        assertThat(legacyData.hosts).isEmpty()
        assertThat(legacyData.pubkeys).isEmpty()
    }

    @Test
    fun transformedDataStructureIsValid() {
        val transformedData = TransformedData(
            hosts = emptyList(),
            portForwards = emptyList(),
            knownHosts = emptyList(),
            colorSchemes = emptyList(),
            colorPalettes = emptyList(),
            pubkeys = emptyList()
        )

        assertThat(transformedData.hosts).isEmpty()
        assertThat(transformedData.pubkeys).isEmpty()
    }

    @Test
    fun verificationResultStructure() {
        val successResult = VerificationResult(
            success = true,
            errors = emptyList()
        )
        assertThat(successResult.success).isTrue()
        assertThat(successResult.errors).isEmpty()

        val failureResult = VerificationResult(
            success = false,
            errors = listOf("error1", "error2")
        )
        assertThat(failureResult.success).isFalse()
        assertThat(failureResult.errors).hasSize(2)
    }

    @Test
    fun migrationExceptionCanBeThrown() {
        val exception = MigrationException("Test error")
        assertThat(exception.message).isEqualTo("Test error")
        assertThat(exception).isInstanceOf(Exception::class.java)
    }

    @Test
    fun duplicateHostNicknamesAreHandledCorrectly() = runTest {
        val host1 = createTestHost(id = 1, nickname = "server")
        val host2 = createTestHost(id = 2, nickname = "server")
        val host3 = createTestHost(id = 3, nickname = "server")
        val host4 = createTestHost(id = 4, nickname = "other")

        val legacyData = LegacyData(
            hosts = listOf(host1, host2, host3, host4),
            portForwards = emptyList(),
            knownHosts = emptyList(),
            colorSchemes = emptyList(),
            colorPalettes = emptyList(),
            pubkeys = emptyList()
        )

        val transformed = migrator.transformToRoomEntitiesForTesting(legacyData)

        assertThat(transformed.hosts).hasSize(4)
        assertThat(transformed.hosts[0].nickname).isEqualTo("server")
        assertThat(transformed.hosts[1].nickname).isEqualTo("server (1)")
        assertThat(transformed.hosts[2].nickname).isEqualTo("server (2)")
        assertThat(transformed.hosts[3].nickname).isEqualTo("other")
    }

    @Test
    fun duplicatePubkeyNicknamesAreHandledCorrectly() = runTest {
        val pubkey1 = createTestPubkey(id = 1, nickname = "my key")
        val pubkey2 = createTestPubkey(id = 2, nickname = "my key")
        val pubkey3 = createTestPubkey(id = 3, nickname = "different")

        val legacyData = LegacyData(
            hosts = emptyList(),
            portForwards = emptyList(),
            knownHosts = emptyList(),
            colorSchemes = emptyList(),
            colorPalettes = emptyList(),
            pubkeys = listOf(pubkey1, pubkey2, pubkey3)
        )

        val transformed = migrator.transformToRoomEntitiesForTesting(legacyData)

        assertThat(transformed.pubkeys).hasSize(3)
        assertThat(transformed.pubkeys[0].nickname).isEqualTo("my key")
        assertThat(transformed.pubkeys[1].nickname).isEqualTo("my key (1)")
        assertThat(transformed.pubkeys[2].nickname).isEqualTo("different")
    }

    @Test
    fun orphanedColorPaletteSynthesizesColorScheme() = runTest {
        val scheme1 = createTestColorScheme(id = 1, name = "Default")
        val scheme2 = createTestColorScheme(id = 2, name = "Dark")

        val palette1 = createTestColorPalette(schemeId = 1, colorIndex = 0, color = 0x000000)
        val palette2 = createTestColorPalette(schemeId = 2, colorIndex = 0, color = 0xFFFFFF)
        val orphanedPalette1 = createTestColorPalette(schemeId = 5, colorIndex = 0, color = 0xFF0000)
        val orphanedPalette2 = createTestColorPalette(schemeId = 5, colorIndex = 1, color = 0x00FF00)
        val orphanedPalette3 = createTestColorPalette(schemeId = 7, colorIndex = 0, color = 0x0000FF)

        val legacyData = LegacyData(
            hosts = emptyList(),
            portForwards = emptyList(),
            knownHosts = emptyList(),
            colorSchemes = listOf(scheme1, scheme2),
            colorPalettes = listOf(palette1, palette2, orphanedPalette1, orphanedPalette2, orphanedPalette3),
            pubkeys = emptyList()
        )

        val transformed = migrator.transformToRoomEntitiesForTesting(legacyData)

        assertThat(transformed.colorSchemes).hasSize(4)
        assertThat(transformed.colorSchemes.map { it.id }).containsExactlyInAnyOrder(1, 2, 5, 7)

        val synthesizedScheme5 = transformed.colorSchemes.find { it.id == 5L }
        assertThat(synthesizedScheme5).isNotNull
        assertThat(synthesizedScheme5?.name).isEqualTo("Recovered Scheme 5")
        assertThat(synthesizedScheme5?.isBuiltIn).isFalse()
        assertThat(synthesizedScheme5?.description).contains("Auto-generated during migration")

        val synthesizedScheme7 = transformed.colorSchemes.find { it.id == 7L }
        assertThat(synthesizedScheme7).isNotNull
        assertThat(synthesizedScheme7?.name).isEqualTo("Recovered Scheme 7")
        assertThat(synthesizedScheme7?.isBuiltIn).isFalse()

        assertThat(transformed.colorPalettes).hasSize(5)
    }

    private fun createTestHost(
        id: Long,
        nickname: String
    ) = Host(
        id = id,
        nickname = nickname,
        protocol = "ssh",
        username = "user",
        hostname = "example.com",
        port = 22,
        hostKeyAlgo = null,
        lastConnect = 0L,
        color = null,
        useKeys = true,
        useAuthAgent = null,
        postLogin = null,
        pubkeyId = 0L,
        wantSession = true,
        compression = false,
        encoding = "UTF-8",
        stayConnected = false,
        quickDisconnect = false,
        fontSize = 12,
        colorSchemeId = 1L,
        delKey = "BACKSPACE",
        scrollbackLines = 140,
        useCtrlAltAsMetaKey = false
    )

    private fun createTestPubkey(
        id: Long,
        nickname: String
    ) = Pubkey(
        id = id,
        nickname = nickname,
        type = "RSA",
        privateKey = byteArrayOf(1, 2, 3),
        publicKey = byteArrayOf(4, 5, 6),
        encrypted = false,
        startup = false,
        confirmation = false,
        createdDate = System.currentTimeMillis(),
        storageType = KeyStorageType.EXPORTABLE,
        allowBackup = true,
        keystoreAlias = null
    )

    private fun createTestColorScheme(
        id: Long,
        name: String
    ) = ColorScheme(
        id = id,
        name = name,
        isBuiltIn = true,
        description = "Test color scheme"
    )

    private fun createTestColorPalette(
        schemeId: Int,
        colorIndex: Int,
        color: Int
    ) = ColorPalette(
        schemeId = schemeId.toLong(),
        colorIndex = colorIndex,
        color = color
    )
}
