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
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import timber.log.Timber
import javax.inject.Inject
import java.io.File
import java.io.FileOutputStream

@HiltAndroidTest
class LegacyDatabaseMigrationIntegrationTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: ConnectBotDatabase

    @Inject
    lateinit var migrator: DatabaseMigrator

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var hostsDbFile: File
    private lateinit var pubkeysDbFile: File

    @Before
    fun setUp() {
        hiltRule.inject()

        // Clean up any existing databases
        context.deleteDatabase("hosts")
        context.deleteDatabase("pubkeys")
        database.clearAllTables()

        hostsDbFile = context.getDatabasePath("hosts")
        pubkeysDbFile = context.getDatabasePath("pubkeys")
    }

    @After
    fun tearDown() {
        // Clean up
        database.clearAllTables()
        context.deleteDatabase("hosts")
        context.deleteDatabase("pubkeys")
        context.deleteDatabase("hosts.migrated")
        context.deleteDatabase("pubkeys.migrated")
    }

    @Test
    fun migrateSampleLegacyDatabases() {
        runBlocking {
            try {
                Timber.d("Starting test: migrateSampleLegacyDatabases")

                // Copy sample databases from assets to database directory
                Timber.d("Copying sample databases")
                copyAssetToDatabase("sample_hosts.db", "hosts")
                copyAssetToDatabase("sample_pubkeys.db", "pubkeys")
                Timber.d("Databases copied successfully")

                // Verify migration is needed
                Timber.d("Checking if migration is needed")
                assertThat(migrator.isMigrationNeeded()).isTrue()
                Timber.d("Migration is needed")

                // Perform migration
                val result = migrator.migrate()

                Timber.d("Migration completed: $result")

                // Verify migration succeeded
                assertThat(result).isInstanceOf(MigrationResult.Success::class.java)
                val success = result as MigrationResult.Success

                // Verify counts
                assertThat(success.hostsMigrated).isEqualTo(3)
                assertThat(success.pubkeysMigrated).isEqualTo(3)
                assertThat(success.portForwardsMigrated).isEqualTo(1)
                assertThat(success.knownHostsMigrated).isEqualTo(1)

                // Verify legacy databases were renamed
                assertThat(hostsDbFile.exists()).isFalse()
                assertThat(pubkeysDbFile.exists()).isFalse()
                assertThat(context.getDatabasePath("hosts.migrated").exists()).isTrue()
                assertThat(context.getDatabasePath("pubkeys.migrated").exists()).isTrue()

                Timber.d("Test completed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Test failed with exception")
                e.printStackTrace()
                throw e
            }
        }
    }

    @Test
    fun verifyMigratedHostsData() {
        runBlocking {
            // Copy sample databases
            copyAssetToDatabase("sample_hosts.db", "hosts")
            copyAssetToDatabase("sample_pubkeys.db", "pubkeys")

            // Perform migration
            val result = migrator.migrate()

            assertThat(result).isInstanceOf(MigrationResult.Success::class.java)

            // Query migrated data
            val hosts = database.hostDao().getAll()

            // Verify host data
            assertThat(hosts).hasSize(3)

            // Find specific hosts by nickname
            val prodHost = hosts.find { it.nickname == "production-server" }
            assertThat(prodHost).isNotNull()
            assertThat(prodHost?.protocol).isEqualTo("ssh")
            assertThat(prodHost?.username).isEqualTo("admin")
            assertThat(prodHost?.hostname).isEqualTo("prod.example.com")
            assertThat(prodHost?.port).isEqualTo(22)
            assertThat(prodHost?.color).isEqualTo("red")
            assertThat(prodHost?.useKeys).isTrue()
            assertThat(prodHost?.compression).isTrue()

            val devHost = hosts.find { it.nickname == "dev-server" }
            assertThat(devHost).isNotNull()
            assertThat(devHost?.protocol).isEqualTo("ssh")
            assertThat(devHost?.username).isEqualTo("developer")
            assertThat(devHost?.hostname).isEqualTo("dev.example.com")
            assertThat(devHost?.useKeys).isFalse()
            assertThat(devHost?.compression).isFalse()

            val telnetHost = hosts.find { it.nickname == "legacy-telnet" }
            assertThat(telnetHost).isNotNull()
            assertThat(telnetHost?.protocol).isEqualTo("telnet")
            assertThat(telnetHost?.port).isEqualTo(23)
        }
    }

    @Test
    fun verifyMigratedPubkeysData() {
        runBlocking {
            // Copy sample databases
            copyAssetToDatabase("sample_hosts.db", "hosts")
            copyAssetToDatabase("sample_pubkeys.db", "pubkeys")

            // Perform migration
            val result = migrator.migrate()

            assertThat(result).isInstanceOf(MigrationResult.Success::class.java)

            // Query migrated data
            val pubkeys = database.pubkeyDao().getAll()

            // Verify pubkey data
            assertThat(pubkeys).hasSize(3)

            // Find specific keys by nickname
            val rsaKey = pubkeys.find { it.nickname == "my-rsa-key" }
            assertThat(rsaKey).isNotNull()
            assertThat(rsaKey?.type).isEqualTo("RSA")
            assertThat(rsaKey?.encrypted).isFalse()
            assertThat(rsaKey?.startup).isTrue()
            assertThat(rsaKey?.confirmation).isFalse()
            assertThat(rsaKey?.allowBackup).isTrue()

            val ed25519Key = pubkeys.find { it.nickname == "fast-ed25519" }
            assertThat(ed25519Key).isNotNull()
            assertThat(ed25519Key?.type).isEqualTo("ED25519")
            assertThat(ed25519Key?.startup).isFalse()
            assertThat(ed25519Key?.confirmation).isTrue()

            val encryptedKey = pubkeys.find { it.nickname == "encrypted-key" }
            assertThat(encryptedKey).isNotNull()
            assertThat(encryptedKey?.encrypted).isTrue()
        }
    }

    @Test
    fun verifyMigratedPortForwards() {
        runBlocking {
            // Copy sample databases
            copyAssetToDatabase("sample_hosts.db", "hosts")
            copyAssetToDatabase("sample_pubkeys.db", "pubkeys")

            // Perform migration
            val result = migrator.migrate()

            assertThat(result).isInstanceOf(MigrationResult.Success::class.java)

            // Query migrated data
            val hosts = database.hostDao().getAll()
            val prodHost = hosts.find { it.nickname == "production-server" }
            assertThat(prodHost).isNotNull()

            // Get port forwards for production server
            val portForwards = database.portForwardDao().getByHost(prodHost!!.id)
            assertThat(portForwards).hasSize(1)

            val forward = portForwards.first()
            assertThat(forward.nickname).isEqualTo("web-forward")
            assertThat(forward.type).isEqualTo("local")
            assertThat(forward.sourcePort).isEqualTo(8080)
            assertThat(forward.destAddr).isEqualTo("localhost")
            assertThat(forward.destPort).isEqualTo(80)
        }
    }

    @Test
    fun verifyMigratedKnownHosts() {
        runBlocking {
            // Copy sample databases
            copyAssetToDatabase("sample_hosts.db", "hosts")
            copyAssetToDatabase("sample_pubkeys.db", "pubkeys")

            // Perform migration
            val result = migrator.migrate()

            assertThat(result).isInstanceOf(MigrationResult.Success::class.java)

            // Query migrated data
            val hosts = database.hostDao().getAll()
            val prodHost = hosts.find { it.nickname == "production-server" }
            assertThat(prodHost).isNotNull()

            // Get known hosts for production server
            val knownHosts = database.knownHostDao().getByHostId(prodHost!!.id)
            assertThat(knownHosts).hasSize(1)

            val knownHost = knownHosts.first()
            assertThat(knownHost.hostname).isEqualTo("prod.example.com")
            assertThat(knownHost.port).isEqualTo(22)
            assertThat(knownHost.hostKeyAlgo).isEqualTo("ssh-rsa")
            assertThat(knownHost.hostKey).isNotEmpty()
        }
    }

    @Test
    fun migrateComplexLegacyDatabases() {
        runBlocking {
            // Copy complex databases from assets
            copyAssetToDatabase("complex_hosts.db", "hosts")
            copyAssetToDatabase("complex_pubkeys.db", "pubkeys")

            // Verify migration is needed
            assertThat(migrator.isMigrationNeeded()).isTrue()

            // Perform migration
            val result = migrator.migrate()

            // Verify migration succeeded
            assertThat(result).isInstanceOf(MigrationResult.Success::class.java)
            val success = result as MigrationResult.Success

            // Verify counts for complex databases
            assertThat(success.hostsMigrated).isEqualTo(50)
            assertThat(success.pubkeysMigrated).isEqualTo(20)
        }
    }

    @Test
    fun verifyMigrationProgress() {
        runBlocking {
            // Copy sample databases
            copyAssetToDatabase("sample_hosts.db", "hosts")
            copyAssetToDatabase("sample_pubkeys.db", "pubkeys")

            // Track migration state changes
            val stateUpdates = mutableListOf<MigrationState>()

            // Start migration in background
            val result = migrator.migrate()

            // Verify final state
            val finalState = migrator.migrationState.first()
            assertThat(finalState.status).isEqualTo(MigrationStatus.COMPLETED)
            assertThat(finalState.progress).isEqualTo(1.0f)
        }
    }

    @Test
    fun secondMigrationIsNotNeededAfterSuccess() {
        runBlocking {
            // Copy sample databases
            copyAssetToDatabase("sample_hosts.db", "hosts")
            copyAssetToDatabase("sample_pubkeys.db", "pubkeys")

            // First migration
            val result1 = migrator.migrate()
            assertThat(result1).isInstanceOf(MigrationResult.Success::class.java)

            // Verify migration is no longer needed
            assertThat(migrator.isMigrationNeeded()).isFalse()

            // Second migration should do nothing
            val result2 = migrator.migrate()

            assertThat(result2).isInstanceOf(MigrationResult.Success::class.java)
            val success = result2 as MigrationResult.Success
            assertThat(success.hostsMigrated).isEqualTo(0)
            assertThat(success.pubkeysMigrated).isEqualTo(0)
        }
    }

    /**
     * Copy a database file from test resources to the app's database directory.
     */
    private fun copyAssetToDatabase(assetName: String, dbName: String) {
        val resourcePath = "/test_databases/$assetName"
        javaClass.getResourceAsStream(resourcePath)?.use { input ->
            val outputFile = context.getDatabasePath(dbName)
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Test database not found: $resourcePath")
    }
}
