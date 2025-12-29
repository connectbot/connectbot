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

package org.connectbot.sftp

import android.util.Log
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import com.trilead.ssh2.InteractiveCallback
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.fingerprint.KeyFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.Pubkey
import org.connectbot.util.HostConstants
import org.connectbot.util.PubkeyUtils
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SSH connections for SFTP operations.
 * This is separate from TerminalManager to allow SFTP browsing without a terminal session.
 */
@Singleton
class SftpConnectionManager @Inject constructor(
    private val hostRepository: HostRepository,
    private val pubkeyRepository: PubkeyRepository
) {
    companion object {
        private const val TAG = "CB.SftpConnMgr"
        private const val AUTH_PUBLICKEY = "publickey"
        private const val AUTH_PASSWORD = "password"
        private const val AUTH_KEYBOARDINTERACTIVE = "keyboard-interactive"
        private const val AUTH_TRIES = 20
    }

    /**
     * Represents an active SFTP session.
     */
    data class SftpSession(
        val hostId: Long,
        val connection: Connection,
        val sftpClient: SFTPv3Client,
        val operations: SftpOperations
    )

    private val activeSessions = mutableMapOf<Long, SftpSession>()
    private val sessionMutex = Mutex()

    /**
     * Connect to a host and establish an SFTP session.
     *
     * @param host The host to connect to
     * @param promptHandler Handler for authentication prompts
     * @return Result containing SftpOperations on success, or an exception on failure
     */
    suspend fun connect(
        host: Host,
        promptHandler: SftpPromptHandler
    ): Result<SftpOperations> = withContext(Dispatchers.IO) {
        // Check for existing session
        sessionMutex.withLock {
            activeSessions[host.id]?.let { session ->
                return@withContext Result.success(session.operations)
            }
        }

        try {
            val connection = Connection(host.hostname, host.port)

            // Set compression if enabled
            if (host.compression) {
                connection.setCompression(true)
            }

            // Connect with host key verification
            val hostKeyVerifier = SftpHostKeyVerifier(host, promptHandler)
            connection.connect(hostKeyVerifier)

            // Authenticate
            val authenticated = authenticate(connection, host, promptHandler)
            if (!authenticated) {
                connection.close()
                return@withContext Result.failure(IOException("Authentication failed"))
            }

            // Create SFTP client
            val sftpClient = SFTPv3Client(connection)
            val operations = SftpOperations(sftpClient)

            // Store session
            val session = SftpSession(host.id, connection, sftpClient, operations)
            sessionMutex.withLock {
                activeSessions[host.id] = session
            }

            Log.d(TAG, "SFTP connection established to ${host.hostname}")
            Result.success(operations)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to ${host.hostname}", e)
            Result.failure(e)
        }
    }

    /**
     * Disconnect from a host and close the SFTP session.
     *
     * @param hostId The ID of the host to disconnect from
     */
    suspend fun disconnect(hostId: Long) {
        sessionMutex.withLock {
            activeSessions.remove(hostId)?.let { session ->
                try {
                    session.sftpClient.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing SFTP client", e)
                }
                try {
                    session.connection.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing connection", e)
                }
                Log.d(TAG, "SFTP connection closed for host $hostId")
            }
        }
    }

    /**
     * Get the SFTP operations for an active session.
     *
     * @param hostId The ID of the host
     * @return SftpOperations if a session exists, null otherwise
     */
    suspend fun getOperations(hostId: Long): SftpOperations? {
        return sessionMutex.withLock {
            activeSessions[hostId]?.operations
        }
    }

    /**
     * Check if a session is active for a host.
     *
     * @param hostId The ID of the host
     * @return True if a session is active
     */
    suspend fun isConnected(hostId: Long): Boolean {
        return sessionMutex.withLock {
            activeSessions.containsKey(hostId)
        }
    }

    /**
     * Disconnect all active sessions.
     */
    suspend fun disconnectAll() {
        sessionMutex.withLock {
            activeSessions.keys.toList()
        }.forEach { hostId ->
            disconnect(hostId)
        }
    }

    /**
     * Authenticate to the SSH server.
     * Follows the same authentication flow as SSH.kt.
     */
    private suspend fun authenticate(
        connection: Connection,
        host: Host,
        promptHandler: SftpPromptHandler
    ): Boolean {
        // Try 'none' authentication first
        try {
            if (connection.authenticateWithNone(host.username)) {
                return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Host does not support 'none' authentication")
        }

        var tries = 0
        while (!connection.isAuthenticationComplete && tries++ < AUTH_TRIES) {
            // Try public key authentication
            if (host.pubkeyId != HostConstants.PUBKEYID_NEVER &&
                connection.isAuthMethodAvailable(host.username, AUTH_PUBLICKEY)
            ) {
                if (tryPublicKeyAuth(connection, host, promptHandler)) {
                    return true
                }
            }

            // Try keyboard-interactive authentication
            if (connection.isAuthMethodAvailable(host.username, AUTH_KEYBOARDINTERACTIVE)) {
                if (tryKeyboardInteractiveAuth(connection, host, promptHandler)) {
                    return true
                }
            }

            // Try password authentication
            if (connection.isAuthMethodAvailable(host.username, AUTH_PASSWORD)) {
                if (tryPasswordAuth(connection, host, promptHandler)) {
                    return true
                }
            }

            // If no auth methods available, break
            if (!connection.isAuthMethodAvailable(host.username, AUTH_PUBLICKEY) &&
                !connection.isAuthMethodAvailable(host.username, AUTH_KEYBOARDINTERACTIVE) &&
                !connection.isAuthMethodAvailable(host.username, AUTH_PASSWORD)
            ) {
                break
            }
        }

        return connection.isAuthenticationComplete
    }

    private suspend fun tryPublicKeyAuth(
        connection: Connection,
        host: Host,
        promptHandler: SftpPromptHandler
    ): Boolean {
        val pubkeyId = host.pubkeyId

        if (pubkeyId == HostConstants.PUBKEYID_ANY) {
            // Try all available keys
            val allKeys = pubkeyRepository.getAll()
            for (pubkey in allKeys) {
                val keyPair = loadKeyPair(pubkey, promptHandler) ?: continue
                try {
                    if (connection.authenticateWithPublicKey(host.username, keyPair)) {
                        Log.d(TAG, "Authenticated with key: ${pubkey.nickname}")
                        return true
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Key ${pubkey.nickname} failed: ${e.message}")
                }
            }
        } else if (pubkeyId > 0) {
            // Try specific key
            val pubkey = pubkeyRepository.getById(pubkeyId)
            if (pubkey != null) {
                val keyPair = loadKeyPair(pubkey, promptHandler)
                if (keyPair != null) {
                    try {
                        if (connection.authenticateWithPublicKey(host.username, keyPair)) {
                            Log.d(TAG, "Authenticated with key: ${pubkey.nickname}")
                            return true
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Key ${pubkey.nickname} failed: ${e.message}")
                    }
                }
            }
        }

        return false
    }

    private suspend fun loadKeyPair(
        pubkey: Pubkey,
        promptHandler: SftpPromptHandler
    ): KeyPair? {
        // Handle Android Keystore (biometric) keys
        if (pubkey.storageType == KeyStorageType.ANDROID_KEYSTORE) {
            val keystoreAlias = pubkey.keystoreAlias ?: return null

            val biometricSuccess = promptHandler.requestBiometricAuth(pubkey.nickname, keystoreAlias)
            if (!biometricSuccess) {
                return null
            }

            return try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val publicKey = keyStore.getCertificate(keystoreAlias)?.publicKey
                val privateKey = keyStore.getKey(keystoreAlias, null) as? java.security.PrivateKey

                if (publicKey == null || privateKey == null) {
                    null
                } else {
                    KeyPair(publicKey, privateKey)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load biometric key: ${pubkey.nickname}", e)
                null
            }
        }

        // Handle encrypted keys
        var password: String? = null
        if (pubkey.encrypted) {
            password = promptHandler.requestKeyPassphrase(pubkey.nickname)
            if (password == null) {
                return null
            }
        }

        return try {
            if (pubkey.type == "IMPORTED") {
                val privateKey = pubkey.privateKey ?: return null
                PEMDecoder.decode(String(privateKey, StandardCharsets.UTF_8).toCharArray(), password)
            } else {
                val privateKey = pubkey.privateKey ?: return null
                val privKey = PubkeyUtils.decodePrivate(privateKey, pubkey.type, password) ?: return null
                val pubKey = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)
                KeyPair(pubKey, privKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode key: ${pubkey.nickname}", e)
            null
        }
    }

    private suspend fun tryKeyboardInteractiveAuth(
        connection: Connection,
        host: Host,
        promptHandler: SftpPromptHandler
    ): Boolean {
        return try {
            connection.authenticateWithKeyboardInteractive(
                host.username,
                object : InteractiveCallback {
                    override fun replyToChallenge(
                        name: String,
                        instruction: String,
                        numPrompts: Int,
                        prompt: Array<String>,
                        echo: BooleanArray
                    ): Array<String> {
                        val responses = kotlinx.coroutines.runBlocking {
                            promptHandler.handleKeyboardInteractive(name, instruction, prompt, echo)
                        }
                        return responses ?: Array(numPrompts) { "" }
                    }
                }
            )
        } catch (e: Exception) {
            Log.d(TAG, "Keyboard-interactive auth failed: ${e.message}")
            false
        }
    }

    private suspend fun tryPasswordAuth(
        connection: Connection,
        host: Host,
        promptHandler: SftpPromptHandler
    ): Boolean {
        val password = promptHandler.requestPassword("Password for ${host.username}@${host.hostname}")
            ?: return false

        return try {
            connection.authenticateWithPassword(host.username, password)
        } catch (e: Exception) {
            Log.d(TAG, "Password auth failed: ${e.message}")
            false
        }
    }

    /**
     * Host key verifier for SFTP connections.
     */
    private inner class SftpHostKeyVerifier(
        private val host: Host,
        private val promptHandler: SftpPromptHandler
    ) : ExtendedServerHostKeyVerifier() {

        override fun verifyServerHostKey(
            hostname: String,
            port: Int,
            serverHostKeyAlgorithm: String,
            serverHostKey: ByteArray
        ): Boolean {
            val knownHosts = kotlinx.coroutines.runBlocking {
                hostRepository.getKnownHostsForHost(host.id)
            }

            val sha256 = KeyFingerprint.createSHA256Fingerprint(serverHostKey)

            // Check if we have any known hosts for this algorithm
            val hostsWithSameAlgo = knownHosts.filter { it.hostKeyAlgo == serverHostKeyAlgorithm }

            return when {
                // Key matches an existing known host
                knownHosts.any { it.hostKeyAlgo == serverHostKeyAlgorithm && it.hostKey.contentEquals(serverHostKey) } -> {
                    Log.d(TAG, "Host key verified for $hostname")
                    true
                }

                // No known hosts - this is a new key
                knownHosts.isEmpty() -> {
                    val accepted = kotlinx.coroutines.runBlocking {
                        promptHandler.confirmHostKey(
                            hostname = hostname,
                            keyType = getKeyType(serverHostKeyAlgorithm) ?: "UNKNOWN",
                            fingerprint = sha256,
                            isNewKey = true
                        )
                    }

                    if (accepted) {
                        kotlinx.coroutines.runBlocking {
                            hostRepository.saveKnownHost(host, hostname, port, serverHostKeyAlgorithm, serverHostKey)
                        }
                    }
                    accepted
                }

                // We have hosts with this algorithm but key doesn't match - key has changed
                hostsWithSameAlgo.isNotEmpty() -> {
                    val accepted = kotlinx.coroutines.runBlocking {
                        promptHandler.confirmHostKey(
                            hostname = hostname,
                            keyType = getKeyType(serverHostKeyAlgorithm) ?: "UNKNOWN",
                            fingerprint = sha256,
                            isNewKey = false
                        )
                    }

                    if (accepted) {
                        kotlinx.coroutines.runBlocking {
                            hostRepository.saveKnownHost(host, hostname, port, serverHostKeyAlgorithm, serverHostKey)
                        }
                    }
                    accepted
                }

                // New algorithm for this host - treat as new key
                else -> {
                    val accepted = kotlinx.coroutines.runBlocking {
                        promptHandler.confirmHostKey(
                            hostname = hostname,
                            keyType = getKeyType(serverHostKeyAlgorithm) ?: "UNKNOWN",
                            fingerprint = sha256,
                            isNewKey = true
                        )
                    }

                    if (accepted) {
                        kotlinx.coroutines.runBlocking {
                            hostRepository.saveKnownHost(host, hostname, port, serverHostKeyAlgorithm, serverHostKey)
                        }
                    }
                    accepted
                }
            }
        }

        override fun getKnownKeyAlgorithmsForHost(hostname: String, port: Int): List<String>? {
            return kotlinx.coroutines.runBlocking {
                hostRepository.getHostKeyAlgorithmsForHost(host.id).ifEmpty { null }
            }
        }

        override fun removeServerHostKey(hostname: String, port: Int, algorithm: String, hostKey: ByteArray) {
            kotlinx.coroutines.runBlocking {
                hostRepository.removeKnownHost(host.id, algorithm, hostKey)
            }
        }

        override fun addServerHostKey(hostname: String, port: Int, algorithm: String, hostKey: ByteArray) {
            kotlinx.coroutines.runBlocking {
                hostRepository.saveKnownHost(host, hostname, port, algorithm, hostKey)
            }
        }

        private fun getKeyType(openSshKeyType: String): String? {
            return when {
                openSshKeyType == "ssh-rsa" -> "RSA"
                openSshKeyType == "ssh-dss" -> "DSA"
                openSshKeyType == "ssh-ed25519" -> "Ed25519"
                openSshKeyType.startsWith("ecdsa-sha2-") -> "EC"
                else -> null
            }
        }
    }
}
