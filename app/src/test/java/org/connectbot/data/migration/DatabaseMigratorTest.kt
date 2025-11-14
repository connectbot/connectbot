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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigratorTest {

    private lateinit var context: Context
    private lateinit var migrator: DatabaseMigrator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        migrator = DatabaseMigrator.get(context)
    }

    @After
    fun tearDown() {
        // Clean up database
        ConnectBotDatabase.clearInstance()
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
        val statuses = MigrationStatus.values()
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
}
