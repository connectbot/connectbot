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
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KnownHost
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Pubkey
import java.io.File

/**
 * Migrates data from legacy SQLite databases (HostDatabase, PubkeyDatabase)
 * to the new Room-based ConnectBotDatabase.
 *
 * Migration is a one-time operation that:
 * 1. Reads all data from legacy databases
 * 2. Transforms it to Room entity format
 * 3. Validates data integrity
 * 4. Writes to Room database
 * 5. Renames legacy databases to .migrated on success
 *
 * @param context Application context
 */
class DatabaseMigrator(
    private val context: Context,
) {

    companion object {
        private const val TAG = "DatabaseMigrator"
        private const val LEGACY_HOSTS_DB = "hosts"
        private const val LEGACY_PUBKEYS_DB = "pubkeys"
        private const val MIGRATED_SUFFIX = ".migrated"

        @Volatile
        private var instance: DatabaseMigrator? = null

        fun get(context: Context): DatabaseMigrator {
            return instance ?: synchronized(this) {
                instance ?: DatabaseMigrator(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val _migrationState = MutableStateFlow(MigrationState())
    val migrationState: Flow<MigrationState> = _migrationState.asStateFlow()

    private val legacyHostReader = LegacyHostDatabaseReader(context)
    private val legacyPubkeyReader = LegacyPubkeyDatabaseReader(context)
    private val roomDatabase = ConnectBotDatabase.getInstance(context)

    /**
     * Checks if migration is needed.
     * Migration is needed if:
     * 1. Legacy databases exist and haven't been migrated yet
     * 2. Room database is empty (or doesn't exist)
     */
    suspend fun isMigrationNeeded(): Boolean = withContext(Dispatchers.IO) {
        val legacyHostsExists = getLegacyDatabaseFile(LEGACY_HOSTS_DB).exists()
        val legacyPubkeysExists = getLegacyDatabaseFile(LEGACY_PUBKEYS_DB).exists()

        val alreadyMigrated = getLegacyDatabaseFile("$LEGACY_HOSTS_DB$MIGRATED_SUFFIX").exists() &&
                              getLegacyDatabaseFile("$LEGACY_PUBKEYS_DB$MIGRATED_SUFFIX").exists()

        // If legacy DBs don't exist or already migrated, no migration needed
        if (!legacyHostsExists && !legacyPubkeysExists) {
            Log.d(TAG, "No legacy databases found")
            return@withContext false
        }

        if (alreadyMigrated) {
            Log.d(TAG, "Legacy databases already migrated")
            return@withContext false
        }

        // Check if Room database has any data
        val roomHasData = roomDatabase.hostDao().getAll().isNotEmpty() ||
                         roomDatabase.pubkeyDao().getAll().isNotEmpty()

        if (roomHasData) {
            Log.d(TAG, "Room database already has data, skipping migration")
            return@withContext false
        }

        Log.d(TAG, "Migration needed: legacy databases exist and Room is empty")
        return@withContext true
    }

    /**
     * Performs the full database migration.
     * This is a long-running operation and should be called from a background coroutine.
     */
    suspend fun migrate(): MigrationResult = withContext(Dispatchers.IO) {
        // Check if migration is needed
        if (!isMigrationNeeded()) {
            Log.i(TAG, "Migration not needed - already migrated or no legacy databases found")
            _migrationState.update {
                it.copy(
                    status = MigrationStatus.COMPLETED,
                    currentStep = "No migration needed",
                    progress = 1.0f
                )
            }
            return@withContext MigrationResult.Success(
                hostsMigrated = 0,
                pubkeysMigrated = 0,
                portForwardsMigrated = 0,
                knownHostsMigrated = 0,
                colorSchemesMigrated = 0
            )
        }

        Log.i(TAG, "Starting database migration")
        _migrationState.update { it.copy(status = MigrationStatus.IN_PROGRESS, currentStep = "Starting migration") }

        try {
            // Step 1: Read legacy data
            _migrationState.update { it.copy(currentStep = "Reading legacy databases", progress = 0.1f) }
            val legacyData = readLegacyData()

            Log.d(TAG, "Legacy data read: ${legacyData.hosts.size} hosts, ${legacyData.pubkeys.size} pubkeys")

            // Step 2: Validate legacy data
            _migrationState.update { it.copy(currentStep = "Validating data", progress = 0.2f) }
            val warnings = validateLegacyData(legacyData)
            _migrationState.update { it.copy(warnings = warnings) }

            // Step 3: Transform to Room entities
            _migrationState.update { it.copy(currentStep = "Transforming data", progress = 0.3f) }
            val transformedData = transformToRoomEntities(legacyData)

            // Step 4: Write to Room database
            _migrationState.update { it.copy(currentStep = "Writing to new database", progress = 0.5f) }
            writeToRoomDatabase(transformedData)

            // Step 5: Verify migration
            _migrationState.update { it.copy(currentStep = "Verifying migration", progress = 0.8f) }
            val verification = verifyMigration(legacyData, transformedData)

            if (!verification.success) {
                throw MigrationException("Migration verification failed: ${verification.errors.joinToString()}")
            }

            // Step 6: Mark legacy databases as migrated
            _migrationState.update { it.copy(currentStep = "Finalizing migration", progress = 0.9f) }
            markLegacyDatabasesAsMigrated()

            Log.i(TAG, "Migration completed successfully")
            _migrationState.update {
                it.copy(
                    status = MigrationStatus.COMPLETED,
                    currentStep = "Migration completed",
                    progress = 1.0f,
                    hostsMigrated = legacyData.hosts.size,
                    pubkeysMigrated = legacyData.pubkeys.size,
                    portForwardsMigrated = legacyData.portForwards.size,
                    knownHostsMigrated = legacyData.knownHosts.size,
                    colorSchemesMigrated = legacyData.colorSchemes.size
                )
            }

            return@withContext MigrationResult.Success(
                hostsMigrated = legacyData.hosts.size,
                pubkeysMigrated = legacyData.pubkeys.size,
                portForwardsMigrated = legacyData.portForwards.size,
                knownHostsMigrated = legacyData.knownHosts.size,
                colorSchemesMigrated = legacyData.colorSchemes.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            _migrationState.update {
                it.copy(
                    status = MigrationStatus.FAILED,
                    currentStep = "Migration failed: ${e.message}",
                    error = e.message
                )
            }
            return@withContext MigrationResult.Failure(e)
        }
    }

    private suspend fun readLegacyData(): LegacyData {
        val hosts = if (getLegacyDatabaseFile(LEGACY_HOSTS_DB).exists()) {
            legacyHostReader.readHosts()
        } else {
            emptyList()
        }

        val portForwards = if (getLegacyDatabaseFile(LEGACY_HOSTS_DB).exists()) {
            legacyHostReader.readPortForwards()
        } else {
            emptyList()
        }

        val knownHosts = if (getLegacyDatabaseFile(LEGACY_HOSTS_DB).exists()) {
            legacyHostReader.readKnownHosts()
        } else {
            emptyList()
        }

        val colorSchemes = if (getLegacyDatabaseFile(LEGACY_HOSTS_DB).exists()) {
            legacyHostReader.readColorSchemes()
        } else {
            emptyList()
        }

        val colorPalettes = if (getLegacyDatabaseFile(LEGACY_HOSTS_DB).exists()) {
            legacyHostReader.readColorPalettes()
        } else {
            emptyList()
        }

        val pubkeys = if (getLegacyDatabaseFile(LEGACY_PUBKEYS_DB).exists()) {
            legacyPubkeyReader.readPubkeys()
        } else {
            emptyList()
        }

        return LegacyData(
            hosts = hosts,
            portForwards = portForwards,
            knownHosts = knownHosts,
            colorSchemes = colorSchemes,
            colorPalettes = colorPalettes,
            pubkeys = pubkeys
        )
    }

    private fun validateLegacyData(data: LegacyData): List<String> {
        val warnings = mutableListOf<String>()

        // Check for duplicate host nicknames (will be fixed in transformToRoomEntities)
        val hostNicknames = data.hosts.map { it.nickname }
        val duplicateHosts = hostNicknames.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicateHosts.isNotEmpty()) {
            val warning = "Found ${duplicateHosts.size} duplicate host nickname(s): ${duplicateHosts.keys.joinToString(", ")}. Appending suffixes to make them unique."
            Log.w(TAG, warning)
            warnings.add(warning)
        }

        // Check for duplicate pubkey nicknames (will be fixed in transformToRoomEntities)
        val pubkeyNicknames = data.pubkeys.map { it.nickname }
        val duplicatePubkeys = pubkeyNicknames.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicatePubkeys.isNotEmpty()) {
            val warning = "Found ${duplicatePubkeys.size} duplicate SSH key nickname(s): ${duplicatePubkeys.keys.joinToString(", ")}. Appending suffixes to make them unique."
            Log.w(TAG, warning)
            warnings.add(warning)
        }

        // Validate port forwards reference valid hosts
        val hostIds = data.hosts.map { it.id }.toSet()
        val invalidPortForwards = data.portForwards.filter { it.hostId !in hostIds }
        if (invalidPortForwards.isNotEmpty()) {
            val warning = "Found ${invalidPortForwards.size} port forward(s) referencing non-existent hosts. These will be skipped."
            Log.w(TAG, warning)
            warnings.add(warning)
        }

        return warnings
    }

    private fun transformToRoomEntities(legacy: LegacyData): TransformedData {
        // Fix duplicate host nicknames by appending " (1)", " (2)", etc.
        val fixedHosts = makeNicknamesUnique(legacy.hosts) { it.nickname }
            .map { (host, uniqueNickname) -> host.copy(nickname = uniqueNickname) }

        // Fix duplicate pubkey nicknames by appending " (1)", " (2)", etc.
        val fixedPubkeys = makeNicknamesUnique(legacy.pubkeys) { it.nickname }
            .map { (pubkey, uniqueNickname) -> pubkey.copy(nickname = uniqueNickname) }

        return TransformedData(
            hosts = fixedHosts,
            portForwards = legacy.portForwards,
            knownHosts = legacy.knownHosts,
            colorSchemes = legacy.colorSchemes,
            colorPalettes = legacy.colorPalettes,
            pubkeys = fixedPubkeys
        )
    }

    /**
     * Makes nicknames unique by appending " (1)", " (2)", etc. to duplicates.
     * Returns a list of (item, uniqueNickname) pairs.
     */
    private fun <T> makeNicknamesUnique(
        items: List<T>,
        getNickname: (T) -> String
    ): List<Pair<T, String>> {
        val nicknameCount = mutableMapOf<String, Int>()
        val result = mutableListOf<Pair<T, String>>()

        for (item in items) {
            val originalNickname = getNickname(item)
            val count = nicknameCount.getOrDefault(originalNickname, 0)
            nicknameCount[originalNickname] = count + 1

            val uniqueNickname = if (count == 0) {
                originalNickname
            } else {
                "$originalNickname ($count)"
            }

            result.add(item to uniqueNickname)
        }

        return result
    }

    private suspend fun writeToRoomDatabase(data: TransformedData) {
        // Wrap all writes in a single transaction for atomicity
        // If any write fails, all writes are rolled back
        roomDatabase.withTransaction {
            // Insert color schemes first (referenced by hosts)
            data.colorSchemes.forEach { scheme ->
                roomDatabase.colorSchemeDao().insert(scheme)
            }

            // Insert color palettes
            data.colorPalettes.forEach { palette ->
                roomDatabase.colorSchemeDao().insertColor(palette)
            }

            // Insert pubkeys (referenced by hosts)
            data.pubkeys.forEach { pubkey ->
                roomDatabase.pubkeyDao().insert(pubkey)
            }

            // Insert hosts and create ID mapping (old ID -> new ID)
            val hostIdMap = mutableMapOf<Long, Long>()
            data.hosts.forEach { host ->
                val oldId = host.id
                val newId = roomDatabase.hostDao().insert(host)
                hostIdMap[oldId] = newId
            }

            // Insert port forwards with remapped host IDs
            data.portForwards.forEach { portForward ->
                val newHostId = hostIdMap[portForward.hostId]
                    ?: throw MigrationException("Port forward references unknown host ID: ${portForward.hostId}")
                val remappedPortForward = portForward.copy(hostId = newHostId)
                roomDatabase.portForwardDao().insert(remappedPortForward)
            }

            // Insert known hosts with remapped host IDs
            data.knownHosts.forEach { knownHost ->
                val newHostId = knownHost.hostId?.let { oldHostId ->
                    hostIdMap[oldHostId]
                        ?: throw MigrationException("Known host references unknown host ID: $oldHostId")
                }
                val remappedKnownHost = knownHost.copy(hostId = newHostId)
                roomDatabase.knownHostDao().insert(remappedKnownHost)
            }
        }
    }

    private suspend fun verifyMigration(legacy: LegacyData, transformed: TransformedData): VerificationResult {
        val errors = mutableListOf<String>()

        // Verify counts match
        val hostCount = roomDatabase.hostDao().getAll().size
        if (hostCount != legacy.hosts.size) {
            errors.add("Host count mismatch: expected ${legacy.hosts.size}, got $hostCount")
        }

        val pubkeyCount = roomDatabase.pubkeyDao().getAll().size
        if (pubkeyCount != legacy.pubkeys.size) {
            errors.add("Pubkey count mismatch: expected ${legacy.pubkeys.size}, got $pubkeyCount")
        }

        val portForwardCount = roomDatabase.hostDao().getAll().sumOf { host ->
            roomDatabase.portForwardDao().getByHost(host.id).size
        }
        if (portForwardCount != legacy.portForwards.size) {
            errors.add("Port forward count mismatch: expected ${legacy.portForwards.size}, got $portForwardCount")
        }

        return VerificationResult(
            success = errors.isEmpty(),
            errors = errors
        )
    }

    private fun markLegacyDatabasesAsMigrated() {
        val hostsDb = getLegacyDatabaseFile(LEGACY_HOSTS_DB)
        val pubkeysDb = getLegacyDatabaseFile(LEGACY_PUBKEYS_DB)

        if (hostsDb.exists()) {
            hostsDb.renameTo(getLegacyDatabaseFile("$LEGACY_HOSTS_DB$MIGRATED_SUFFIX"))
        }

        if (pubkeysDb.exists()) {
            pubkeysDb.renameTo(getLegacyDatabaseFile("$LEGACY_PUBKEYS_DB$MIGRATED_SUFFIX"))
        }
    }

    private fun getLegacyDatabaseFile(name: String): File {
        return context.getDatabasePath(name)
    }

    /**
     * Resets migration state (for testing purposes).
     */
    fun resetMigrationState() {
        _migrationState.update { MigrationState() }
    }

    /**
     * Exposes transformToRoomEntities for testing purposes.
     * This allows unit tests to verify duplicate nickname handling.
     */
    @Suppress("unused")
    internal fun transformToRoomEntitiesForTesting(legacy: LegacyData): TransformedData {
        return transformToRoomEntities(legacy)
    }
}

/**
 * Legacy data read from old databases.
 */
data class LegacyData(
    val hosts: List<Host>,
    val portForwards: List<PortForward>,
    val knownHosts: List<KnownHost>,
    val colorSchemes: List<ColorScheme>,
    val colorPalettes: List<ColorPalette>,
    val pubkeys: List<Pubkey>
)

/**
 * Data transformed to Room entity format.
 */
data class TransformedData(
    val hosts: List<Host>,
    val portForwards: List<PortForward>,
    val knownHosts: List<KnownHost>,
    val colorSchemes: List<ColorScheme>,
    val colorPalettes: List<ColorPalette>,
    val pubkeys: List<Pubkey>
)

/**
 * Current state of the migration process.
 */
data class MigrationState(
    val status: MigrationStatus = MigrationStatus.NOT_STARTED,
    val currentStep: String = "",
    val progress: Float = 0f,
    val hostsMigrated: Int = 0,
    val pubkeysMigrated: Int = 0,
    val portForwardsMigrated: Int = 0,
    val knownHostsMigrated: Int = 0,
    val colorSchemesMigrated: Int = 0,
    val error: String? = null,
    val warnings: List<String> = emptyList()
)

enum class MigrationStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Result of migration verification.
 */
data class VerificationResult(
    val success: Boolean,
    val errors: List<String>
)

/**
 * Result of the migration operation.
 */
sealed class MigrationResult {
    data class Success(
        val hostsMigrated: Int,
        val pubkeysMigrated: Int,
        val portForwardsMigrated: Int,
        val knownHostsMigrated: Int,
        val colorSchemesMigrated: Int
    ) : MigrationResult()

    data class Failure(val error: Throwable) : MigrationResult()
}

/**
 * Exception thrown during migration.
 */
class MigrationException(message: String) : Exception(message)
