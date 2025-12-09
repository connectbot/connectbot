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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roomDatabase: ConnectBotDatabase,
    private val legacyHostReader: LegacyHostDatabaseReader,
    private val legacyPubkeyReader: LegacyPubkeyDatabaseReader
) {
    companion object {
        private const val TAG = "DatabaseMigrator"
        private const val LEGACY_HOSTS_DB = "hosts"
        private const val LEGACY_PUBKEYS_DB = "pubkeys"
        private const val MIGRATED_SUFFIX = ".migrated"
    }

    private val _migrationState = MutableStateFlow(MigrationState())
    val migrationState: Flow<MigrationState> = _migrationState.asStateFlow()

    private fun logDebug(message: String) {
        Log.d(TAG, message)
        _migrationState.update { state ->
            state.copy(debugLog = state.debugLog + message)
        }
    }

    private fun logWarning(message: String) {
        Log.w(TAG, message)
        _migrationState.update { state ->
            state.copy(
                warnings = state.warnings + message,
                debugLog = state.debugLog + "WARNING: $message"
            )
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
        val logMessage = if (throwable != null) {
            "$message: ${throwable.message}\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        _migrationState.update { state ->
            state.copy(debugLog = state.debugLog + "ERROR: $logMessage")
        }
    }

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
            logDebug("No legacy databases found")
            return@withContext false
        }

        if (alreadyMigrated) {
            logDebug("Legacy databases already migrated")
            return@withContext false
        }

        // Check if Room database has any data
        val roomHasData = roomDatabase.hostDao().getAll().isNotEmpty() ||
                         roomDatabase.pubkeyDao().getAll().isNotEmpty()

        if (roomHasData) {
            logDebug("Room database already has data, skipping migration")
            return@withContext false
        }

        logDebug("Migration needed: legacy databases exist and Room is empty")
        return@withContext true
    }

    /**
     * Performs the full database migration.
     * This is a long-running operation and should be called from a background coroutine.
     */
    suspend fun migrate(): MigrationResult = withContext(Dispatchers.IO) {
        // Check if migration is needed
        if (!isMigrationNeeded()) {
            logDebug("Migration not needed - already migrated or no legacy databases found")
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

        logDebug("Starting database migration")
        _migrationState.update { it.copy(status = MigrationStatus.IN_PROGRESS, currentStep = "Starting migration") }

        try {
            // Step 1: Read legacy data
            logDebug("Step 1: Reading legacy databases")
            _migrationState.update { it.copy(currentStep = "Reading legacy databases", progress = 0.1f) }
            val legacyData = readLegacyData()

            logDebug("Legacy data read: ${legacyData.hosts.size} hosts, ${legacyData.pubkeys.size} pubkeys, ${legacyData.portForwards.size} port forwards, ${legacyData.knownHosts.size} known hosts, ${legacyData.colorSchemes.size} color schemes")

            // Step 2: Validate legacy data
            logDebug("Step 2: Validating data")
            _migrationState.update { it.copy(currentStep = "Validating data", progress = 0.2f) }
            validateLegacyData(legacyData)

            // Step 3: Transform to Room entities
            logDebug("Step 3: Transforming data to Room entities")
            _migrationState.update { it.copy(currentStep = "Transforming data", progress = 0.3f) }
            val transformedData = transformToRoomEntities(legacyData)

            // Step 4: Write to Room database
            logDebug("Step 4: Writing to new database")
            _migrationState.update { it.copy(currentStep = "Writing to new database", progress = 0.5f) }
            writeToRoomDatabase(transformedData)

            // Step 5: Verify migration
            logDebug("Step 5: Verifying migration")
            _migrationState.update { it.copy(currentStep = "Verifying migration", progress = 0.8f) }
            val verification = verifyMigration(legacyData, transformedData)

            if (!verification.success) {
                val errorMsg = "Migration verification failed: ${verification.errors.joinToString()}"
                logError(errorMsg)
                throw MigrationException(errorMsg)
            }
            logDebug("Verification successful")

            // Step 6: Mark legacy databases as migrated
            logDebug("Step 6: Finalizing migration")
            _migrationState.update { it.copy(currentStep = "Finalizing migration", progress = 0.9f) }
            markLegacyDatabasesAsMigrated()

            logDebug("Migration completed successfully")
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
            logError("Migration failed", e)
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

    private fun validateLegacyData(data: LegacyData) {
        val hostIds = data.hosts.map { it.id }.toSet()
        val pubkeyIds = data.pubkeys.map { it.id }.toSet()
        val colorSchemeIds = data.colorSchemes.map { it.id }.toSet()

        // Check for duplicate host nicknames (will be fixed in transformToRoomEntities)
        val hostNicknames = data.hosts.map { it.nickname }
        val duplicateHosts = hostNicknames.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicateHosts.isNotEmpty()) {
            val warning = "Found ${duplicateHosts.size} duplicate host nickname(s): ${duplicateHosts.keys.joinToString(", ")}. Appending suffixes to make them unique."
            logWarning(warning)
        }

        // Check for duplicate pubkey nicknames (will be fixed in transformToRoomEntities)
        val pubkeyNicknames = data.pubkeys.map { it.nickname }
        val duplicatePubkeys = pubkeyNicknames.groupingBy { it }.eachCount().filter { it.value > 1 }
        if (duplicatePubkeys.isNotEmpty()) {
            val warning = "Found ${duplicatePubkeys.size} duplicate SSH key nickname(s): ${duplicatePubkeys.keys.joinToString(", ")}. Appending suffixes to make them unique."
            logWarning(warning)
        }

        // Validate port forwards reference valid hosts
        val invalidPortForwards = data.portForwards.filter { it.hostId !in hostIds }
        if (invalidPortForwards.isNotEmpty()) {
            val warning = "Found ${invalidPortForwards.size} port forward(s) referencing non-existent hosts. These will be skipped."
            logWarning(warning)
        }

        // Validate known hosts with hostId reference valid hosts (will be cleaned in transformToRoomEntities)
        val invalidKnownHosts = data.knownHosts.filter { it.hostId != null && it.hostId !in hostIds }
        if (invalidKnownHosts.isNotEmpty()) {
            val warning = "Found ${invalidKnownHosts.size} known host(s) referencing non-existent hosts. These will have their host reference removed."
            logWarning(warning)
        }

        // Validate hosts reference valid pubkeys (will be fixed in transformToRoomEntities)
        val hostsWithInvalidPubkeys = data.hosts.filter { it.pubkeyId > 0 && it.pubkeyId !in pubkeyIds }
        if (hostsWithInvalidPubkeys.isNotEmpty()) {
            val warning = "Found ${hostsWithInvalidPubkeys.size} host(s) referencing non-existent SSH keys. References will be cleared."
            logWarning(warning)
        }

        // Validate hosts reference valid color schemes (will be fixed in transformToRoomEntities)
        val hostsWithInvalidColorSchemes = data.hosts.filter { it.colorSchemeId > 0 && it.colorSchemeId !in colorSchemeIds }
        if (hostsWithInvalidColorSchemes.isNotEmpty()) {
            val warning = "Found ${hostsWithInvalidColorSchemes.size} host(s) referencing non-existent color schemes. Will use default color scheme."
            logWarning(warning)
        }

        // Validate color palettes reference valid color schemes (will be fixed in transformToRoomEntities)
        val palettesWithInvalidSchemes = data.colorPalettes.filter { it.schemeId !in colorSchemeIds }
        if (palettesWithInvalidSchemes.isNotEmpty()) {
            val orphanedSchemeIds = palettesWithInvalidSchemes.map { it.schemeId }.toSet()
            val warning = "Found ${palettesWithInvalidSchemes.size} color palette(s) referencing ${orphanedSchemeIds.size} non-existent color scheme(s): ${orphanedSchemeIds.joinToString(", ")}. Will synthesize missing schemes."
            logWarning(warning)
        }
    }

    private fun transformToRoomEntities(legacy: LegacyData): TransformedData {
        val hostIds = legacy.hosts.map { it.id }.toSet()
        val pubkeyIds = legacy.pubkeys.map { it.id }.toSet()
        var colorSchemeIds = legacy.colorSchemes.map { it.id }.toSet()

        // Synthesize missing ColorScheme entries for orphaned ColorPalette entries
        val orphanedSchemeIds = legacy.colorPalettes
            .map { it.schemeId }
            .filter { it !in colorSchemeIds }
            .toSet()

        val synthesizedSchemes = orphanedSchemeIds.map { schemeId ->
            logDebug("Synthesizing missing ColorScheme with ID $schemeId for orphaned palette entries")
            ColorScheme(
                id = schemeId,
                name = "Recovered Scheme $schemeId",
                isBuiltIn = false,
                description = "Auto-generated during migration to recover orphaned color palette entries"
            )
        }

        // Combine original and synthesized schemes
        val allColorSchemes = legacy.colorSchemes + synthesizedSchemes
        colorSchemeIds = allColorSchemes.map { it.id }.toSet()

        // Fix duplicate host nicknames by appending " (1)", " (2)", etc.
        val hostsWithUniqueNicknames = makeNicknamesUnique(legacy.hosts) { it.nickname }
            .map { (host, uniqueNickname) -> host.copy(nickname = uniqueNickname) }

        // Fix hosts with invalid foreign key references
        val fixedHosts = hostsWithUniqueNicknames.map { host ->
            var fixedHost = host

            // Clear invalid pubkey references
            if (fixedHost.pubkeyId > 0 && fixedHost.pubkeyId !in pubkeyIds) {
                logDebug("Clearing invalid pubkey reference (ID ${fixedHost.pubkeyId}) for host '${fixedHost.nickname}'")
                fixedHost = fixedHost.copy(pubkeyId = -1L)
            }

            // Reset invalid color scheme references to default
            if (fixedHost.colorSchemeId > 0 && fixedHost.colorSchemeId !in colorSchemeIds) {
                logDebug("Resetting invalid color scheme reference (ID ${fixedHost.colorSchemeId}) to default for host '${fixedHost.nickname}'")
                fixedHost = fixedHost.copy(colorSchemeId = 1L)
            }

            fixedHost
        }

        // Fix duplicate pubkey nicknames by appending " (1)", " (2)", etc.
        val fixedPubkeys = makeNicknamesUnique(legacy.pubkeys) { it.nickname }
            .map { (pubkey, uniqueNickname) -> pubkey.copy(nickname = uniqueNickname) }

        // Filter out invalid port forwards
        val validPortForwards = legacy.portForwards.filter { portForward ->
            if (portForward.hostId !in hostIds) {
                logDebug("Skipping invalid port forward (host ID ${portForward.hostId})")
                false
            } else {
                true
            }
        }

        // Clean up known hosts with invalid host references
        val cleanedKnownHosts = legacy.knownHosts.map { knownHost ->
            if (knownHost.hostId != null && knownHost.hostId !in hostIds) {
                logDebug("Removing invalid host reference (ID ${knownHost.hostId}) from known host for ${knownHost.hostname}:${knownHost.port}")
                knownHost.copy(hostId = null)
            } else {
                knownHost
            }
        }

        // Log summary of recovery actions
        val skippedPortForwards = legacy.portForwards.size - validPortForwards.size
        if (skippedPortForwards > 0) {
            logDebug("Recovery: Skipped $skippedPortForwards invalid port forward(s)")
        }

        val cleanedKnownHostsCount = cleanedKnownHosts.count { it.hostId == null && legacy.knownHosts.find { kh -> kh.id == it.id }?.hostId != null }
        if (cleanedKnownHostsCount > 0) {
            logDebug("Recovery: Cleaned $cleanedKnownHostsCount known host(s) with invalid references")
        }

        val hostsWithClearedPubkeys = fixedHosts.count { host ->
            val original = legacy.hosts.find { h -> h.id == host.id }
            original != null && original.pubkeyId != host.pubkeyId
        }
        if (hostsWithClearedPubkeys > 0) {
            logDebug("Recovery: Cleared invalid pubkey references from $hostsWithClearedPubkeys host(s)")
        }

        val hostsWithResetColorSchemes = fixedHosts.count { host ->
            val original = legacy.hosts.find { h -> h.id == host.id }
            original != null && original.colorSchemeId != host.colorSchemeId
        }
        if (hostsWithResetColorSchemes > 0) {
            logDebug("Recovery: Reset invalid color scheme references for $hostsWithResetColorSchemes host(s)")
        }

        if (synthesizedSchemes.isNotEmpty()) {
            logDebug("Recovery: Synthesized ${synthesizedSchemes.size} missing color scheme(s) for orphaned palette entries")
        }

        return TransformedData(
            hosts = fixedHosts,
            portForwards = validPortForwards,
            knownHosts = cleanedKnownHosts,
            colorSchemes = allColorSchemes,
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
            // Insert color schemes first (referenced by hosts and palettes)
            // Create ID mapping since Room auto-generates IDs
            val colorSchemeIdMap = mutableMapOf<Long, Long>()
            data.colorSchemes.forEach { scheme ->
                val oldId = scheme.id
                val newId = roomDatabase.colorSchemeDao().insert(scheme)
                colorSchemeIdMap[oldId] = newId
            }

            // Insert color palettes with remapped scheme IDs
            data.colorPalettes.forEach { palette ->
                val newSchemeId = colorSchemeIdMap[palette.schemeId]
                    ?: throw MigrationException("Color palette references unknown color scheme ID: ${palette.schemeId}")
                val remappedPalette = palette.copy(schemeId = newSchemeId)
                roomDatabase.colorSchemeDao().insertColor(remappedPalette)
            }

            // Insert pubkeys (referenced by hosts)
            // Create ID mapping since Room auto-generates IDs
            val pubkeyIdMap = mutableMapOf<Long, Long>()
            data.pubkeys.forEach { pubkey ->
                val oldId = pubkey.id
                val newId = roomDatabase.pubkeyDao().insert(pubkey)
                pubkeyIdMap[oldId] = newId
            }

            // Insert hosts with remapped foreign key references and create ID mapping
            val hostIdMap = mutableMapOf<Long, Long>()
            data.hosts.forEach { host ->
                val oldId = host.id

                // Remap pubkeyId if it references a valid pubkey
                val newPubkeyId = if (host.pubkeyId > 0) {
                    pubkeyIdMap[host.pubkeyId]
                        ?: throw MigrationException("Host references unknown pubkey ID: ${host.pubkeyId}")
                } else {
                    host.pubkeyId // Keep special values like -1 (any key), -2 (same as last), etc.
                }

                // Remap colorSchemeId if it's not the default scheme (ID 1)
                // colorSchemeId = 1 is a virtual "Default" scheme that doesn't exist in the database
                val newColorSchemeId = if (host.colorSchemeId == 1L) {
                    1L // Keep default scheme ID as-is
                } else {
                    colorSchemeIdMap[host.colorSchemeId]
                        ?: throw MigrationException("Host references unknown color scheme ID: ${host.colorSchemeId}")
                }

                val remappedHost = host.copy(
                    pubkeyId = newPubkeyId,
                    colorSchemeId = newColorSchemeId
                )
                val newId = roomDatabase.hostDao().insert(remappedHost)
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
        if (hostCount != transformed.hosts.size) {
            errors.add("Host count mismatch: expected ${transformed.hosts.size}, got $hostCount")
        }

        val pubkeyCount = roomDatabase.pubkeyDao().getAll().size
        if (pubkeyCount != transformed.pubkeys.size) {
            errors.add("Pubkey count mismatch: expected ${transformed.pubkeys.size}, got $pubkeyCount")
        }

        val portForwardCount = roomDatabase.hostDao().getAll().sumOf { host ->
            roomDatabase.portForwardDao().getByHost(host.id).size
        }
        if (portForwardCount != transformed.portForwards.size) {
            errors.add("Port forward count mismatch: expected ${transformed.portForwards.size}, got $portForwardCount")
        }

        val knownHostCount = roomDatabase.knownHostDao().getAll().size
        if (knownHostCount != transformed.knownHosts.size) {
            errors.add("Known host count mismatch: expected ${transformed.knownHosts.size}, got $knownHostCount")
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
    val warnings: List<String> = emptyList(),
    val debugLog: List<String> = emptyList()
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
