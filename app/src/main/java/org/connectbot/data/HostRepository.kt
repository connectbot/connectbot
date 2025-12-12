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
import android.util.Log
import com.trilead.ssh2.KnownHosts
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

    /**
     * Get the known hosts (SSH host keys).
     *
     * @return KnownHosts instance containing all known host keys
     */
    suspend fun getKnownHosts(): KnownHosts {
        val knownHostsList = knownHostDao.getAll()
        val knownHosts = KnownHosts()

        for (knownHost in knownHostsList) {
            try {
                // Format hostname with port for trilead KnownHosts (e.g., "example.com:22")
                val hostnameWithPort = "${knownHost.hostname}:${knownHost.port}"
                Log.d("HostRepository", "Adding known host $hostnameWithPort with key algorithm ${knownHost.hostKeyAlgo} and key ${knownHost.hostKey.contentToString()}")
                knownHosts.addHostkey(
                    arrayOf(hostnameWithPort),
                    knownHost.hostKeyAlgo,
                    knownHost.hostKey
                )
            } catch (e: Exception) {
                // Ignore invalid host keys
            }
        }

        return knownHosts
    }

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
        if (existing == null) {
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
    // Blocking Methods for Java Interop
    // ============================================================================

    /**
     * Get the known hosts (blocking version for Java interop).
     */
    fun getKnownHostsBlocking(): KnownHosts = runBlocking {
        getKnownHosts()
    }

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