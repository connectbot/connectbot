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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.connectbot.data.dao.HostDao
import org.connectbot.data.dao.KnownHostDao
import org.connectbot.data.dao.PortForwardDao
import org.connectbot.data.dao.PubkeyDao
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KnownHost
import org.connectbot.data.entity.PortForward
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing SSH host configurations and connections.
 * Handles host CRUD operations, known hosts, and port forwards.
 *
 * @param context Application context for accessing schema assets
 * @param database The Room database instance for export/import operations
 * @param hostDao The DAO for accessing host data
 * @param portForwardDao The DAO for accessing port forward data
 * @param knownHostDao The DAO for accessing known host data
 * @param pubkeyDao The DAO for accessing pubkey data (for SSH config export)
 */
@Singleton
class HostRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ConnectBotDatabase,
    private val hostDao: HostDao,
    private val portForwardDao: PortForwardDao,
    private val knownHostDao: KnownHostDao,
    private val pubkeyDao: PubkeyDao
) {

    // ============================================================================
    // Host Operations
    // ============================================================================

    /**
     * Observe all hosts reactively.
     *
     * @return Flow of host list that updates automatically
     */
    fun observeHosts(): Flow<List<Host>> = hostDao.observeAll()

    /**
     * Observe all hosts sorted by color reactively.
     *
     * @return Flow of host list sorted by color
     */
    fun observeHostsSortedByColor(): Flow<List<Host>> = hostDao.observeAllSortedByColor()

    /**
     * Observe a specific host reactively.
     *
     * @param hostId The host ID
     * @return Flow of host that updates automatically
     */
    fun observeHost(hostId: Long): Flow<Host?> = hostDao.observeById(hostId)

    /**
     * Get all hosts.
     *
     * @param sortedByColor If true, hosts will be grouped by color
     * @return List of all hosts
     */
    suspend fun getHosts(sortedByColor: Boolean = false): List<Host> {
        return if (sortedByColor) {
            // For now, sort by color in memory
            // TODO: Add a proper DAO query for this
            hostDao.getAll().sortedBy { it.color }
        } else {
            hostDao.getAll()
        }
    }

    /**
     * Find a host by its unique ID.
     *
     * @param hostId The host ID
     * @return The host, or null if not found
     */
    suspend fun findHostById(hostId: Long): Host? = hostDao.getById(hostId)

    /**
     * Get all SSH hosts that can be used as jump hosts.
     *
     * @return List of SSH hosts
     */
    suspend fun getSshHosts(): List<Host> = hostDao.getSshHosts()

    /**
     * Observe all SSH hosts (for jump host selection UI).
     *
     * @return Flow of SSH hosts
     */
    fun observeSshHosts(): Flow<List<Host>> = hostDao.observeSshHosts()

    /**
     * Find a host by its unique ID (blocking version for Java interop).
     *
     * @param hostId The host ID
     * @return The host, or null if not found
     */
    fun findHostByIdBlocking(hostId: Long): Host? = runBlocking {
        findHostById(hostId)
    }

    /**
     * Save a host (insert or update).
     *
     * @param host The host to save
     * @return The saved host with updated ID
     */
    suspend fun saveHost(host: Host): Host {
        return if (host.id <= 0L) {
            // New or temporary host - insert (assigns new positive ID)
            val newId = hostDao.insert(host)
            host.copy(id = newId)
        } else {
            // Existing host - update
            hostDao.update(host)
            host
        }
    }

    /**
     * Delete a host.
     *
     * @param host The host to delete
     */
    suspend fun deleteHost(host: Host) {
        hostDao.delete(host)
    }

    /**
     * Update the last connected time for a host.
     *
     * @param host The host to update
     */
    suspend fun touchHost(host: Host) {
        if (host.id <= 0L) {
            // Skip for temporary hosts (negative IDs)
            return
        }
        val updatedHost = host.copy(lastConnect = System.currentTimeMillis())
        hostDao.update(updatedHost)
    }

    // ============================================================================
    // Port Forward Operations
    // ============================================================================

    /**
     * Observe port forwards for a host reactively.
     *
     * @param hostId The host ID
     * @return Flow of port forwards that updates automatically
     */
    fun observePortForwardsForHost(hostId: Long): Flow<List<PortForward>> =
        portForwardDao.observeByHost(hostId)

    /**
     * Get all port forwards for a host.
     *
     * @param hostId The host ID
     * @return List of port forwards
     */
    suspend fun getPortForwardsForHost(hostId: Long): List<PortForward> =
        portForwardDao.getByHost(hostId)

    /**
     * Save a port forward (insert or update).
     *
     * @param portForward The port forward to save
     * @return The saved port forward with updated ID
     */
    suspend fun savePortForward(portForward: PortForward): PortForward {
        return if (portForward.id == 0L) {
            // New port forward - insert
            val newId = portForwardDao.insert(portForward)
            portForward.copy(id = newId)
        } else {
            // Existing port forward - update
            portForwardDao.update(portForward)
            portForward
        }
    }

    /**
     * Delete a port forward.
     *
     * @param portForward The port forward to delete
     */
    suspend fun deletePortForward(portForward: PortForward) {
        portForwardDao.delete(portForward)
    }

    // ============================================================================
    // Known Host Operations
    // ============================================================================

    suspend fun getKnownHostsForHost(hostId: Long): List<KnownHost> {
        return knownHostDao.getByHostId(hostId)
    }

    /**
     * Get the list of host key algorithms known for a specific host.
     *
     * @param hostId The host ID
     * @return List of algorithm names
     */
    suspend fun getHostKeyAlgorithmsForHost(hostId: Long): List<String> {
        val knownHosts = knownHostDao.getByHostId(hostId)
        return knownHosts.map { it.hostKeyAlgo }.distinct()
    }

    /**
     * Save a known host key to the database.
     *
     * @param host The Host entity to associate with this known host key
     * @param hostname The hostname
     * @param port The port
     * @param serverHostKeyAlgorithm The key algorithm (e.g., "ssh-rsa")
     * @param serverHostKey The public key bytes
     */
    suspend fun saveKnownHost(
        host: Host,
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) {
        // Check if this exact key already exists for this host
        val existing = knownHostDao.getByHostIdAlgoAndKey(
            host.id, serverHostKeyAlgorithm, serverHostKey
        )
        // If it does not exist or exists but has a different hostname and port, add it.
        if (existing == null || existing.hostname != hostname || existing.port != port) {
            // Insert new key - this allows multiple keys per algorithm for key rotation
            val knownHost = KnownHost(
                hostId = host.id,
                hostname = hostname,
                port = port,
                hostKeyAlgo = serverHostKeyAlgorithm,
                hostKey = serverHostKey
            )
            knownHostDao.insert(knownHost)
        }
    }

    /**
     * Remove a known host key from the database.
     *
     * @param hostId The host ID
     * @param serverHostKeyAlgorithm The key algorithm
     * @param serverHostKey The public key bytes
     */
    suspend fun removeKnownHost(
        hostId: Long,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) {
        // Find the exact key to remove
        val knownHost = knownHostDao.getByHostIdAlgoAndKey(
            hostId, serverHostKeyAlgorithm, serverHostKey
        )
        if (knownHost != null) {
            knownHostDao.delete(knownHost)
        }
    }

    /**
     * Delete all known host keys for a specific host configuration.
     * Use this when the user wants to forget all stored host keys and
     * re-verify on next connection.
     *
     * @param hostId The host ID
     */
    suspend fun deleteKnownHostsForHost(hostId: Long) {
        knownHostDao.deleteByHostId(hostId)
    }

    // ============================================================================
    // Export/Import Operations
    // ============================================================================

    /**
     * Export all hosts and their port forwards to JSON string.
     * Uses schema-driven serialization that automatically adapts to database schema changes.
     *
     * @param pretty If true, format JSON with indentation
     * @return Pair of JSON string and export counts (hosts and profiles)
     */
    suspend fun exportHostsToJson(pretty: Boolean = true): Pair<String, ExportCounts> {
        return HostConfigJson.exportToJson(context, database, pretty)
    }

    /**
     * Import hosts from JSON string.
     * Uses schema-driven deserialization that automatically handles:
     * - Field mapping based on database schema
     * - Foreign key ID remapping
     * - Conflict resolution via unique constraints
     *
     * @param jsonString The JSON string containing host configurations
     * @return Import counts for hosts and profiles
     * @throws org.json.JSONException if JSON is invalid
     * @throws IllegalArgumentException if schema version is incompatible
     */
    suspend fun importHostsFromJson(jsonString: String): ImportCounts {
        return HostConfigJson.importFromJson(context, database, jsonString)
    }

    /**
     * Export all SSH hosts to OpenSSH config format.
     * Only exports hosts with protocol="ssh", skipping telnet and local.
     *
     * @return SshConfigExportResult with config text and counts
     */
    suspend fun exportToSshConfig(): SshConfigExportResult {
        val hosts = hostDao.getAll()
        val allPortForwards = mutableMapOf<Long, List<PortForward>>()

        for (host in hosts) {
            allPortForwards[host.id] = portForwardDao.getByHost(host.id)
        }

        val pubkeys = pubkeyDao.getAll().associateBy { it.id }
        val hostMap = hosts.associateBy { it.id }

        val exporter = SshConfigExporter()
        return exporter.export(
            hosts = hosts.map { it to (allPortForwards[it.id] ?: emptyList()) },
            pubkeys = pubkeys,
            jumpHosts = hostMap
        )
    }

    /**
     * Import hosts from OpenSSH config format.
     * Uses Default profile (profileId=1) for all imported hosts.
     *
     * @param configText The SSH config file contents
     * @return SshConfigImportResult with counts and warnings
     */
    suspend fun importFromSshConfig(configText: String): SshConfigImportResult {
        val parser = SshConfigParser()
        val (parsedHosts, parseWarnings) = parser.parse(configText)

        val warnings = parseWarnings.toMutableList()
        var imported = 0
        var skipped = 0

        // Build pubkey lookup by nickname
        val pubkeyByNickname = pubkeyDao.getAll().associateBy { it.nickname }

        // Build existing hosts lookup by nickname
        val existingHosts = hostDao.getAll().associateBy { it.nickname }

        // First pass: import hosts (without ProxyJump resolution for new hosts)
        val importedHostIds = mutableMapOf<String, Long>()

        for (parsed in parsedHosts) {
            // Check if host already exists
            if (existingHosts.containsKey(parsed.hostPattern)) {
                skipped++
                continue
            }

            // Resolve pubkey ID from IdentityFile
            var pubkeyId = -1L
            parsed.identityFile?.let { keyName ->
                val pubkey = pubkeyByNickname[keyName]
                if (pubkey != null) {
                    pubkeyId = pubkey.id
                } else {
                    warnings.add(
                        SshConfigWarning(
                            hostPattern = parsed.hostPattern,
                            directive = "IdentityFile",
                            message = "Key '$keyName' not found in ConnectBot",
                            type = SshConfigWarningType.PUBKEY_NOT_FOUND
                        )
                    )
                }
            }

            // Resolve jump host ID from ProxyJump (existing hosts only in first pass)
            var jumpHostId: Long? = null
            parsed.proxyJump?.let { jumpHostName ->
                val jumpHost = existingHosts[jumpHostName]
                if (jumpHost != null) {
                    jumpHostId = jumpHost.id
                }
                // If not found in existing, will be resolved in second pass
            }

            // Create the host entity
            val host = Host(
                nickname = parsed.hostPattern,
                protocol = "ssh",
                hostname = parsed.hostname ?: parsed.hostPattern,
                username = parsed.user ?: "",
                port = parsed.port ?: 22,
                pubkeyId = pubkeyId,
                useKeys = parsed.pubkeyAuthentication ?: true,
                useAuthAgent = parsed.addKeysToAgent ?: "no",
                compression = parsed.compression ?: false,
                wantSession = parsed.requestTty ?: true,
                postLogin = parsed.remoteCommand,
                jumpHostId = jumpHostId,
                profileId = 1L // Default profile
            )

            // Insert host
            val newId = hostDao.insert(host)
            imported++
            importedHostIds[parsed.hostPattern] = newId

            // Insert port forwards
            for (lf in parsed.localForwards) {
                portForwardDao.insert(
                    PortForward(
                        hostId = newId,
                        nickname = "Local ${lf.sourcePort}",
                        type = "local",
                        sourcePort = lf.sourcePort,
                        destAddr = lf.destHost,
                        destPort = lf.destPort
                    )
                )
            }
            for (rf in parsed.remoteForwards) {
                portForwardDao.insert(
                    PortForward(
                        hostId = newId,
                        nickname = "Remote ${rf.sourcePort}",
                        type = "remote",
                        sourcePort = rf.sourcePort,
                        destAddr = rf.destHost,
                        destPort = rf.destPort
                    )
                )
            }
            for (df in parsed.dynamicForwards) {
                portForwardDao.insert(
                    PortForward(
                        hostId = newId,
                        nickname = "Dynamic $df",
                        type = "dynamic5", // Default to SOCKS5
                        sourcePort = df,
                        destAddr = null,
                        destPort = 0
                    )
                )
            }
        }

        // Second pass: resolve ProxyJump references to newly imported hosts
        for (parsed in parsedHosts) {
            val jumpHostName = parsed.proxyJump ?: continue
            val hostId = importedHostIds[parsed.hostPattern] ?: continue

            // Skip if already resolved to an existing host
            if (existingHosts.containsKey(jumpHostName)) continue

            // Check if the jump host was also imported
            val jumpHostId = importedHostIds[jumpHostName]
            if (jumpHostId != null) {
                // Update the host with the jump host ID
                val host = hostDao.getById(hostId)
                if (host != null) {
                    hostDao.update(host.copy(jumpHostId = jumpHostId))
                }
            }
        }

        // Notify Room's InvalidationTracker
        database.invalidationTracker.refreshVersionsAsync()

        return SshConfigImportResult(
            hostsImported = imported,
            hostsSkipped = skipped,
            warnings = warnings
        )
    }

    // ============================================================================
    // Blocking Methods for Java Interop
    // ============================================================================

    /**
     * Save a known host key (blocking version for Java interop).
     */
    fun saveKnownHostBlocking(
        host: Host,
        hostname: String,
        port: Int,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) = runBlocking {
        saveKnownHost(host, hostname, port, serverHostKeyAlgorithm, serverHostKey)
    }

    /**
     * Get known hosts for a specific host (blocking version for Java interop).
     */
    fun getKnownHostsForHostBlocking(hostId: Long): List<KnownHost> = runBlocking {
        getKnownHostsForHost(hostId)
    }

    /**
     * Get host key algorithms for a host (blocking version for Java interop).
     */
    fun getHostKeyAlgorithmsForHostBlocking(hostId: Long): List<String> = runBlocking {
        getHostKeyAlgorithmsForHost(hostId)
    }

    /**
     * Remove a known host key (blocking version for Java interop).
     */
    fun removeKnownHostBlocking(
        hostId: Long,
        serverHostKeyAlgorithm: String,
        serverHostKey: ByteArray
    ) = runBlocking {
        removeKnownHost(hostId, serverHostKeyAlgorithm, serverHostKey)
    }

    /**
     * Find a host by selection criteria (blocking version for Java interop).
     *
     * This method attempts to find a host matching the given criteria.
     * It tries to match by nickname first (most specific), then falls back
     * to matching by protocol, username, hostname, and port.
     *
     * @param selection Map of field names to values (e.g., "nickname", "protocol", "hostname", etc.)
     * @return The matching host, or null if not found
     */
    suspend fun findHost(selection: Map<String, String>): Host? {
        // Try to find by nickname first (most specific)
        val nickname = selection["nickname"]
        if (nickname != null) {
            val allHosts = hostDao.getAll()
            allHosts.find { it.nickname == nickname }?.let { return it }
        }

        // Fall back to finding by protocol, username, hostname, port
        val protocol = selection["protocol"]
        val hostname = selection["hostname"]
        val username = selection["username"]
        val portStr = selection["port"]
        val port = portStr?.toIntOrNull()

        if (protocol != null && hostname != null) {
            val allHosts = hostDao.getAll()
            allHosts.find { host ->
                host.protocol == protocol &&
                host.hostname == hostname &&
                (username == null || host.username == username) &&
                (port == null || host.port == port)
            }?.let { return it }
        }

        return null
    }
}
