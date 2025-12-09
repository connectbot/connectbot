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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.connectbot.data.dao.HostDao
import org.connectbot.data.dao.KnownHostDao
import org.connectbot.data.dao.PortForwardDao
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KnownHost
import org.connectbot.data.entity.PortForward
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing SSH host configurations and connections.
 * Handles host CRUD operations, known hosts, and port forwards.
 *
 * @param hostDao The DAO for accessing host data
 * @param portForwardDao The DAO for accessing port forward data
 * @param knownHostDao The DAO for accessing known host data
 */
@Singleton
class HostRepository @Inject constructor(
    private val hostDao: HostDao,
    private val portForwardDao: PortForwardDao,
    private val knownHostDao: KnownHostDao
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

    // ============================================================================
    // Export/Import Operations
    // ============================================================================

    /**
     * Export all hosts and their port forwards to JSON string.
     *
     * @param pretty If true, format JSON with indentation
     * @return JSON string containing all host configurations
     */
    suspend fun exportHostsToJson(pretty: Boolean = true): String {
        val hosts = hostDao.getAll()
        val portForwardsMap = mutableMapOf<Long, List<PortForward>>()

        hosts.forEach { host ->
            val portForwards = portForwardDao.getByHost(host.id)
            if (portForwards.isNotEmpty()) {
                portForwardsMap[host.id] = portForwards
            }
        }

        return HostConfigJson.toJson(hosts, portForwardsMap, pretty)
    }

    /**
     * Import hosts from JSON string.
     * Hosts with duplicate nicknames will be updated (overwritten).
     * All ID references (jumpHostId, hostId) are automatically remapped.
     *
     * @param jsonString The JSON string containing host configurations
     * @return Pair of (inserted count, updated count)
     * @throws org.json.JSONException if JSON is invalid
     * @throws IllegalArgumentException if schema is invalid
     */
    suspend fun importHostsFromJson(jsonString: String): Pair<Int, Int> {
        val parseResult = HostConfigJson.fromJson(jsonString)
        val importedHosts = parseResult.hosts
        val importedPortForwards = parseResult.portForwards

        var insertedCount = 0
        var updatedCount = 0

        // Build map of existing hosts by nickname for conflict detection
        val existingHostsByNickname = hostDao.getAll().associateBy { it.nickname }

        // Track old ID -> new ID mapping for remapping references
        val hostIdMapping = mutableMapOf<Long, Long>()

        // First pass: import all hosts (with jumpHostId temporarily set to null for new references)
        for (host in importedHosts) {
            val oldId = host.id
            val existingHost = existingHostsByNickname[host.nickname]

            val savedHost = if (existingHost != null) {
                // Update existing host - preserve the existing ID, update other fields
                // Clear jumpHostId temporarily if it references an ID being imported
                val hostToUpdate = host.copy(
                    id = existingHost.id,
                    jumpHostId = null  // Will be set in second pass
                )
                hostDao.update(hostToUpdate)
                updatedCount++

                // Delete existing port forwards for this host (will be replaced)
                portForwardDao.getByHost(existingHost.id).forEach { pf ->
                    portForwardDao.delete(pf)
                }

                hostToUpdate
            } else {
                // Insert new host with ID=0 to let Room assign new ID
                val hostToInsert = host.copy(
                    id = 0,
                    jumpHostId = null  // Will be set in second pass
                )
                val newId = hostDao.insert(hostToInsert)
                insertedCount++
                hostToInsert.copy(id = newId)
            }

            hostIdMapping[oldId] = savedHost.id
        }

        // Second pass: update jumpHostId references using the ID mapping
        for (host in importedHosts) {
            val newId = hostIdMapping[host.id] ?: continue
            val originalJumpHostId = host.jumpHostId ?: continue

            // Remap jumpHostId to new ID
            val newJumpHostId = hostIdMapping[originalJumpHostId]
            if (newJumpHostId != null) {
                val currentHost = hostDao.getById(newId) ?: continue
                hostDao.update(currentHost.copy(jumpHostId = newJumpHostId))
            }
        }

        // Third pass: import port forwards with remapped hostId
        for (pf in importedPortForwards) {
            val newHostId = hostIdMapping[pf.hostId] ?: continue
            val pfToInsert = pf.copy(id = 0, hostId = newHostId)
            portForwardDao.insert(pfToInsert)
        }

        return Pair(insertedCount, updatedCount)
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