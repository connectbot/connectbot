/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root
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

package org.connectbot.transport

import android.content.Context
import android.net.Uri
import android.util.Log
import com.trilead.ssh2.AuthAgentCallback
import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ConnectionInfo
import com.trilead.ssh2.ConnectionMonitor
import com.trilead.ssh2.DynamicPortForwarder
import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import com.trilead.ssh2.InteractiveCallback
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.LocalPortForwarder
import com.trilead.ssh2.Session
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.PublicKeyUtils
import com.trilead.ssh2.crypto.fingerprint.KeyFingerprint
import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import com.trilead.ssh2.signature.DSASHA1Verify
import com.trilead.ssh2.signature.ECDSASHA2Verify
import com.trilead.ssh2.signature.Ed25519Verify
import com.trilead.ssh2.signature.RSASHA1Verify
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.service.requestBiometricAuth
import org.connectbot.service.requestBooleanPrompt
import org.connectbot.service.requestStringPrompt
import org.connectbot.util.HostConstants
import org.connectbot.util.PubkeyUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.DSAPrivateKey
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.util.Locale
import java.util.regex.Pattern

/**
 * @author Kenny Root
 */
class SSH : AbsTransport, ConnectionMonitor, InteractiveCallback, AuthAgentCallback {

    private var compression = false
    @Volatile
    private var authenticated = false
    @Volatile
    private var connected = false
    @Volatile
    private var sessionOpen = false

    private var pubkeysExhausted = false
    private var interactiveCanContinue = true

    private var connection: Connection? = null
    private val jumpConnections: MutableList<Connection> = mutableListOf()
    private var session: Session? = null

    private var stdin: OutputStream? = null
    private var stdout: InputStream? = null
    private var stderr: InputStream? = null

    private val portForwards = mutableListOf<PortForward>()

    private var columns: Int = 0
    private var rows: Int = 0

    private var width: Int = 0
    private var height: Int = 0

    private var useAuthAgent = HostConstants.AUTHAGENT_NO
    private var agentLockPassphrase: String? = null

    constructor() : super()

    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) : super(host, bridge, manager)

    inner class HostKeyVerifier : ExtendedServerHostKeyVerifier() {
        @Throws(IOException::class)
        override fun verifyServerHostKey(
            hostname: String,
            port: Int,
            serverHostKeyAlgorithm: String,
            serverHostKey: ByteArray
        ): Boolean {
            // read in all known hosts from hostdb
            val hosts = manager?.hostRepository?.getKnownHostsBlocking() ?: return false

            val matchName = String.format(Locale.US, "%s:%d", hostname, port)
            val algorithmName = PublicKeyUtils.detectKeyType(serverHostKey)
            val fingerprint = buildString {
                append("MD5:")
                append(KeyFingerprint.createMD5Fingerprint(serverHostKey))
                append(" SHA256:")
                append(KeyFingerprint.createSHA256Fingerprint(serverHostKey))
            }

            return when (hosts.verifyHostkey(matchName, serverHostKeyAlgorithm, serverHostKey)) {
                KnownHosts.HOSTKEY_IS_OK -> {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_sucess, algorithmName, fingerprint))
                    true
                }

                KnownHosts.HOSTKEY_IS_NEW -> {
                    // prompt user
                    bridge?.outputLine(manager?.res?.getString(R.string.host_authenticity_warning, hostname))
                    bridge?.outputLine(manager?.res?.getString(R.string.host_fingerprint, algorithmName, fingerprint))

                    val result = bridge?.requestBooleanPrompt(
                        null,
                        manager?.res?.getString(R.string.prompt_continue_connecting) ?: ""
                    )
                    if (result == null) {
                        return false
                    }
                    if (result) {
                        // save this key in known database
                        host?.let {
                            manager?.hostRepository?.saveKnownHostBlocking(it, hostname, port, serverHostKeyAlgorithm, serverHostKey)
                        }
                    }
                    result
                }

                KnownHosts.HOSTKEY_HAS_CHANGED -> {
                    val header = String.format(
                        "@   %s   @",
                        manager?.res?.getString(R.string.host_verification_failure_warning_header)
                    )

                    val atsigns = CharArray(header.length) { '@' }
                    val border = String(atsigns)

                    bridge?.outputLine(border)
                    bridge?.outputLine(header)
                    bridge?.outputLine(border)

                    bridge?.outputLine(manager?.res?.getString(R.string.host_verification_failure_warning))

                    bridge?.outputLine(
                        String.format(
                            manager?.res?.getString(R.string.host_fingerprint) ?: "",
                            algorithmName,
                            fingerprint
                        )
                    )

                    // Users have no way to delete keys, so we'll prompt them for now.
                    val result = bridge?.requestBooleanPrompt(
                        null,
                        manager?.res?.getString(R.string.prompt_continue_connecting) ?: ""
                    )
                    if (result != null && result) {
                        // save this key in known database
                        host?.let {
                            manager?.hostRepository?.saveKnownHostBlocking(it, hostname, port, serverHostKeyAlgorithm, serverHostKey)
                        }
                        true
                    } else {
                        false
                    }
                }

                else -> {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_failed))
                    false
                }
            }
        }

        override fun getKnownKeyAlgorithmsForHost(host: String, port: Int): List<String>? {
            return manager?.hostRepository?.getHostKeyAlgorithmsForHostBlocking(host, port)
        }

        override fun removeServerHostKey(host: String, port: Int, algorithm: String, hostKey: ByteArray) {
            manager?.hostRepository?.removeKnownHostBlocking(host, port, algorithm, hostKey)
        }

        override fun addServerHostKey(hostname: String, port: Int, algorithm: String, hostKey: ByteArray) {
            this@SSH.host?.let {
                manager?.hostRepository?.saveKnownHostBlocking(it, hostname, port, algorithm, hostKey)
            }
        }
    }

    private fun authenticate() {
        try {
            val currentHost = host ?: return
            if (connection?.authenticateWithNone(currentHost.username) == true) {
                finishConnection()
                return
            }
        } catch (e: Exception) {
            Log.d(TAG, "Host does not support 'none' authentication.")
        }

        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth))

        try {
            val currentHost = host ?: return
            val pubkeyId = currentHost.pubkeyId

            if (!pubkeysExhausted &&
                pubkeyId != HostConstants.PUBKEYID_NEVER &&
                connection?.isAuthMethodAvailable(currentHost.username, AUTH_PUBLICKEY) == true
            ) {

                // if explicit pubkey defined for this host, then prompt for password as needed
                // otherwise just try all in-memory keys held in terminalmanager

                if (pubkeyId == HostConstants.PUBKEYID_ANY) {
                    // try each of the in-memory keys
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_any))
                    manager?.loadedKeypairs?.entries?.forEach { entry ->
                        if (entry.value.pubkey?.confirmation == true && !promptForPubkeyUse(entry.key))
                            return@forEach

                        val keyPair = entry.value.pair ?: return@forEach

                        if (tryPublicKey(currentHost.username, entry.key, keyPair)) {
                            finishConnection()
                            return
                        }
                    }
                } else {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_specific))
                    // use a specific key for this host, as requested
                    val pubkey = manager?.pubkeyRepository?.getByIdBlocking(pubkeyId)

                    if (pubkey == null)
                        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_invalid))
                    else if (tryPublicKey(pubkey))
                        finishConnection()
                }

                pubkeysExhausted = true
            } else if (interactiveCanContinue &&
                connection?.isAuthMethodAvailable(currentHost.username, AUTH_KEYBOARDINTERACTIVE) == true
            ) {
                // this auth method will talk with us using InteractiveCallback interface
                // it blocks until authentication finishes
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_ki))
                interactiveCanContinue = false
                if (connection?.authenticateWithKeyboardInteractive(currentHost.username, this) == true) {
                    finishConnection()
                } else {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_ki_fail))
                }
            } else if (connection?.isAuthMethodAvailable(currentHost.username, AUTH_PASSWORD) == true) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pass))
                val password = bridge?.requestStringPrompt(
                    null,
                    manager?.res?.getString(R.string.prompt_password),
                    true
                )
                if (password != null && connection?.authenticateWithPassword(currentHost.username, password) == true) {
                    finishConnection()
                } else {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pass_fail))
                }
            } else {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_fail))
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Connection went away while we were trying to authenticate", e)
        } catch (e: Exception) {
            Log.e(TAG, "Problem during handleAuthentication()", e)
        }
    }

    /**
     * Attempt connection with given [pubkey].
     * @return `true` for successful authentication
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws IOException
     */
    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class, IOException::class)
    private fun tryPublicKey(pubkey: Pubkey): Boolean {
        if (pubkey.confirmation && manager?.isKeyLoaded(pubkey.nickname) == true) {
            if (!promptForPubkeyUse(pubkey.nickname))
                return false
        }

        val pair = getOrUnlockKey(pubkey) ?: return false

        val currentHost = host ?: return false
        return tryPublicKey(currentHost.username, pubkey.nickname, pair)
    }

    /**
     * Gets a key pair from memory cache, or unlocks it by prompting for password/biometric as needed.
     *
     * @param pubkey the public key record to get or unlock
     * @return the KeyPair if successful, null if the key couldn't be loaded/unlocked
     */
    private fun getOrUnlockKey(pubkey: Pubkey): KeyPair? {
        if (manager?.isKeyLoaded(pubkey.nickname) == true) {
            // load this key from memory if it's already there
            Log.d(TAG, String.format("Found unlocked key '%s' already in-memory", pubkey.nickname))
            return manager?.getKey(pubkey.nickname)
        }

        // Handle Android Keystore (biometric) keys
        if (pubkey.storageType == KeyStorageType.ANDROID_KEYSTORE) {
            val keystoreAlias = pubkey.keystoreAlias
            if (keystoreAlias == null) {
                val message = String.format("Keystore alias missing for key '%s'. Authentication failed.", pubkey.nickname)
                Log.e(TAG, message)
                bridge?.outputLine(message)
                return null
            }

            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_biometric, pubkey.nickname))

            // Request biometric authentication
            val biometricSuccess = bridge?.requestBiometricAuth(pubkey.nickname, keystoreAlias) ?: false
            if (!biometricSuccess) {
                val message = String.format("Biometric authentication failed for key '%s'.", pubkey.nickname)
                Log.e(TAG, message)
                bridge?.outputLine(message)
                return null
            }

            // Load the key from Keystore after successful biometric auth
            return try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val publicKey = keyStore.getCertificate(keystoreAlias)?.publicKey
                val privateKey = keyStore.getKey(keystoreAlias, null) as? java.security.PrivateKey

                if (publicKey == null || privateKey == null) {
                    val message = String.format("Failed to load key '%s' from Keystore.", pubkey.nickname)
                    Log.e(TAG, message)
                    bridge?.outputLine(message)
                    return null
                }

                val pair = KeyPair(publicKey, privateKey)
                manager?.addBiometricKey(pubkey, keystoreAlias, publicKey)
                Log.d(TAG, String.format("Unlocked biometric key '%s'", pubkey.nickname))
                pair
            } catch (e: Exception) {
                val message = String.format("Failed to load biometric key '%s': %s", pubkey.nickname, e.message)
                Log.e(TAG, message, e)
                bridge?.outputLine(message)
                null
            }
        }

        // otherwise load key from database and prompt for password as needed
        var password: String? = null
        if (pubkey.encrypted) {
            password = bridge?.requestStringPrompt(
                null,
                manager?.res?.getString(R.string.prompt_pubkey_password, pubkey.nickname),
                true
            )

            // Something must have interrupted the prompt.
            if (password == null)
                return null
        }

        val pair = if (pubkey.type == "IMPORTED") {
            // load specific key using pem format
            val privateKey = pubkey.privateKey ?: return null
            PEMDecoder.decode(String(privateKey, StandardCharsets.UTF_8).toCharArray(), password)
        } else {
            // load using internal generated format
            val privateKey = pubkey.privateKey ?: return null
            val privKey = try {
                PubkeyUtils.decodePrivate(privateKey, pubkey.type, password)
            } catch (e: Exception) {
                val message = String.format("Bad password for key '%s'. Authentication failed.", pubkey.nickname)
                Log.e(TAG, message, e)
                bridge?.outputLine(message)
                return null
            }

            if (privKey == null) {
                val message = String.format("Failed to decode private key '%s'. Authentication failed.", pubkey.nickname)
                Log.e(TAG, message)
                bridge?.outputLine(message)
                return null
            }

            val pubKey = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)

            // convert key to trilead format
            KeyPair(pubKey, privKey).also {
                Log.d(TAG, "Unlocked key " + PubkeyUtils.formatKey(pubKey))
            }
        }

        Log.d(TAG, String.format("Unlocked key '%s'", pubkey.nickname))

        // save this key in memory
        manager?.addKey(pubkey, pair)

        return pair
    }

    @Throws(IOException::class)
    private fun tryPublicKey(username: String, keyNickname: String, pair: KeyPair): Boolean {
        val success = connection?.authenticateWithPublicKey(username, pair) == true
        if (!success)
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_fail, keyNickname))
        return success
    }

    /**
     * Internal method to request actual PTY terminal once we've finished
     * authentication. If called before authenticated, it will just fail.
     */
    private fun finishConnection() {
        authenticated = true

        for (portForward in portForwards) {
            try {
                enablePortForward(portForward)
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_enable_portfoward, portForward.getDescription()))
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up port forward during connect", e)
            }
        }

        val currentHost = host ?: return
        if (!currentHost.wantSession) {
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_no_session))
            bridge?.onConnected()
            return
        }

        try {
            session = connection?.openSession()

            if (useAuthAgent != HostConstants.AUTHAGENT_NO)
                session?.requestAuthAgentForwarding(this)

            session?.requestPTY(getEmulation(), columns, rows, width, height, null)
            session?.startShell()

            stdin = session?.stdin
            stdout = session?.stdout
            stderr = session?.stderr

            sessionOpen = true

            bridge?.onConnected()
        } catch (e1: IOException) {
            Log.e(TAG, "Problem while trying to create PTY in finishConnection()", e1)
        }
    }

    /**
     * Establish and authenticate a connection to the jump host.
     * This is called before connecting to the target host when ProxyJump is configured.
     * Supports chained jump hosts (jump host that requires another jump host).
     *
     * @param jumpHost The jump host configuration
     * @return The authenticated Connection, or null if connection/authentication failed
     */
    private fun connectToJumpHost(jumpHost: Host): Connection? {
        bridge?.outputLine(manager?.res?.getString(R.string.terminal_connecting_via_jump, jumpHost.nickname))

        val jc = Connection(jumpHost.hostname, jumpHost.port)

        try {
            // Check if this jump host itself requires a jump host (chained ProxyJump)
            val nestedJumpHostId = jumpHost.jumpHostId
            if (nestedJumpHostId != null && nestedJumpHostId > 0) {
                val nestedJumpHost = manager?.hostRepository?.findHostByIdBlocking(nestedJumpHostId)
                if (nestedJumpHost != null) {
                    val nestedConnection = connectToJumpHost(nestedJumpHost)
                    if (nestedConnection == null) {
                        return null
                    }
                    // Use the nested jump host connection as proxy for this jump host
                    jc.setProxyData(JumpHostProxyData(nestedConnection))
                } else {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_not_found))
                    return null
                }
            }

            if (jumpHost.compression) {
                jc.setCompression(true)
            }

            // Connect to jump host
            jc.connect(HostKeyVerifier())

            // Track this connection for cleanup
            jumpConnections.add(jc)

            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_connected, jumpHost.nickname))

            // Authenticate to jump host
            if (!authenticateJumpHost(jc, jumpHost)) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_auth_failed, jumpHost.nickname))
                jc.close()
                jumpConnections.remove(jc)
                return null
            }

            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_authenticated, jumpHost.nickname))
            return jc
        } catch (e: IOException) {
            Log.e(TAG, "Failed to connect to jump host: ${jumpHost.nickname}", e)
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_failed, jumpHost.nickname, e.message))
            try {
                jc.close()
                jumpConnections.remove(jc)
            } catch (ignored: Exception) {
            }
            return null
        }
    }

    /**
     * Authenticate to a jump host connection.
     *
     * @param jc The jump host connection
     * @param jumpHost The jump host configuration
     * @return true if authentication succeeded
     */
    private fun authenticateJumpHost(jc: Connection, jumpHost: Host): Boolean {
        try {
            // Try 'none' authentication first
            if (jc.authenticateWithNone(jumpHost.username)) {
                return true
            }

            val pubkeyId = jumpHost.pubkeyId

            // Try public key authentication
            if (pubkeyId != HostConstants.PUBKEYID_NEVER &&
                jc.isAuthMethodAvailable(jumpHost.username, AUTH_PUBLICKEY)
            ) {
                if (pubkeyId == HostConstants.PUBKEYID_ANY) {
                    // Try all in-memory keys
                    manager?.loadedKeypairs?.entries?.forEach { entry ->
                        try {
                            if (jc.authenticateWithPublicKey(jumpHost.username, entry.value.pair)) {
                                return true
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Jump host pubkey auth failed with key: ${entry.key}")
                        }
                    }
                } else {
                    // Try specific key (with unlock prompt if needed)
                    val pubkey = manager?.pubkeyRepository?.getByIdBlocking(pubkeyId)
                    if (pubkey != null) {
                        val pair = getOrUnlockKey(pubkey)
                        if (pair != null) {
                            try {
                                if (jc.authenticateWithPublicKey(jumpHost.username, pair)) {
                                    return true
                                }
                            } catch (e: Exception) {
                                Log.d(TAG, "Jump host specific pubkey auth failed")
                            }
                        }
                    }
                }
            }

            // Try keyboard-interactive authentication
            if (jc.isAuthMethodAvailable(jumpHost.username, AUTH_KEYBOARDINTERACTIVE)) {
                try {
                    if (jc.authenticateWithKeyboardInteractive(
                            jumpHost.username
                        ) { name, instruction, numPrompts, prompt, echo ->
                            val responses = Array(numPrompts) { i ->
                                val isPassword = echo != null && i < echo.size && !echo[i]
                                val promptPrefix = manager?.res?.getString(R.string.terminal_jump_prompt, jumpHost.nickname) ?: ""
                                bridge?.requestStringPrompt(
                                    instruction,
                                    "$promptPrefix ${prompt[i]}",
                                    isPassword
                                ) ?: ""
                            }
                            responses
                        }
                    ) {
                        return true
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Jump host keyboard-interactive auth failed", e)
                }
            }

            // Try password authentication
            if (jc.isAuthMethodAvailable(jumpHost.username, AUTH_PASSWORD)) {
                val passwordPrompt = manager?.res?.getString(R.string.terminal_jump_password, jumpHost.nickname)
                val password = bridge?.requestStringPrompt(null, passwordPrompt, true)
                if (password != null) {
                    try {
                        if (jc.authenticateWithPassword(jumpHost.username, password)) {
                            return true
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Jump host password auth failed", e)
                    }
                }
            }

            return jc.isAuthenticationComplete
        } catch (e: Exception) {
            Log.e(TAG, "Error during jump host authentication", e)
            return false
        }
    }

    override fun connect() {
        val currentHost = host ?: return

        // Check if we need to connect through a jump host
        val jumpHostId = currentHost.jumpHostId
        var directJumpConnection: Connection? = null
        if (jumpHostId != null && jumpHostId > 0) {
            val jumpHost = manager?.hostRepository?.findHostByIdBlocking(jumpHostId)
            if (jumpHost != null) {
                directJumpConnection = connectToJumpHost(jumpHost)
                if (directJumpConnection == null) {
                    onDisconnect()
                    return
                }
            } else {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_not_found))
                onDisconnect()
                return
            }
        }

        connection = Connection(currentHost.hostname, currentHost.port)
        connection?.addConnectionMonitor(this)

        // If we have a jump host connection, set up the proxy
        directJumpConnection?.let {
            connection?.setProxyData(JumpHostProxyData(it))
        }

        try {
            connection?.setCompression(compression)
        } catch (e: IOException) {
            Log.e(TAG, "Could not enable compression!", e)
        }

        try {
            val connectionInfo = connection?.connect(HostKeyVerifier())
            connected = true

            connectionInfo?.let { info ->
                bridge?.outputLine(
                    manager?.res?.getString(R.string.terminal_kex_algorithm, info.keyExchangeAlgorithm)
                )
                if (info.clientToServerCryptoAlgorithm == info.serverToClientCryptoAlgorithm &&
                    info.clientToServerMACAlgorithm == info.serverToClientMACAlgorithm
                ) {
                    bridge?.outputLine(
                        manager?.res?.getString(
                            R.string.terminal_using_algorithm,
                            info.clientToServerCryptoAlgorithm,
                            info.clientToServerMACAlgorithm ?: ""
                        )
                    )
                } else {
                    bridge?.outputLine(
                        manager?.res?.getString(
                            R.string.terminal_using_c2s_algorithm,
                            info.clientToServerCryptoAlgorithm,
                            info.clientToServerMACAlgorithm ?: ""
                        )
                    )

                    bridge?.outputLine(
                        manager?.res?.getString(
                            R.string.terminal_using_s2c_algorithm,
                            info.serverToClientCryptoAlgorithm,
                            info.serverToClientMACAlgorithm ?: ""
                        )
                    )
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Problem in SSH connection thread during authentication", e)

            // Display the reason in the text.
            var t: Throwable? = e
            while (t != null) {
                val message = t.message
                if (message != null) {
                    bridge?.outputLine(message)
                    if (t is NoRouteToHostException)
                        bridge?.outputLine(manager?.res?.getString(R.string.terminal_no_route))
                }
                t = t.cause
            }

            close()
            onDisconnect()
            return
        }

        try {
            // enter a loop to keep trying until authentication
            var tries = 0
            while (connected && connection?.isAuthenticationComplete != true && tries++ < AUTH_TRIES) {
                authenticate()

                // sleep to make sure we dont kill system
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Problem in SSH connection thread during authentication", e)
        }
    }

    override fun close() {
        connected = false

        session?.close()
        session = null

        connection?.close()
        connection = null

        // Close all jump host connections (in reverse order)
        jumpConnections.asReversed().forEach { jc ->
            try {
                jc.close()
            } catch (ignored: Exception) {
            }
        }
        jumpConnections.clear()
    }

    private fun onDisconnect() {
        bridge?.dispatchDisconnect(false)
    }

    @Throws(IOException::class)
    override fun flush() {
        stdin?.flush()
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        var bytesRead = 0

        val currentSession = session ?: return 0

        val newConditions = currentSession.waitForCondition(conditions, 0)

        if ((newConditions and ChannelCondition.STDOUT_DATA) != 0) {
            bytesRead = stdout?.read(buffer, offset, length) ?: 0
        }

        if ((newConditions and ChannelCondition.STDERR_DATA) != 0) {
            val discard = ByteArray(256)
            while (stderr?.available() ?: 0 > 0) {
                stderr?.read(discard)
            }
        }

        if ((newConditions and ChannelCondition.EOF) != 0) {
            close()
            onDisconnect()
            throw IOException("Remote end closed connection")
        }

        return bytesRead
    }

    @Throws(IOException::class)
    override fun write(buffer: ByteArray) {
        stdin?.write(buffer)
    }

    @Throws(IOException::class)
    override fun write(c: Int) {
        stdin?.write(c)
    }

    override fun getOptions(): Map<String, String> {
        return mapOf("compression" to compression.toString())
    }

    override fun setOptions(options: Map<String, String>) {
        if (options.containsKey("compression"))
            compression = options["compression"]?.toBoolean() ?: false
    }

    override fun isSessionOpen(): Boolean = sessionOpen

    override fun isConnected(): Boolean = connected

    override fun connectionLost(reason: Throwable) {
        onDisconnect()
    }

    override fun canForwardPorts(): Boolean = true

    override fun getPortForwards(): List<PortForward> = portForwards

    override fun addPortForward(portForward: PortForward): Boolean {
        return portForwards.add(portForward)
    }

    override fun removePortForward(portForward: PortForward): Boolean {
        // Make sure we don't have a phantom forwarder.
        disablePortForward(portForward)
        return portForwards.remove(portForward)
    }

    override fun enablePortForward(portForward: PortForward): Boolean {
        if (!portForwards.contains(portForward)) {
            Log.e(TAG, "Attempt to enable port forward not in list")
            return false
        }

        if (!authenticated)
            return false

        return when (portForward.type) {
            HostConstants.PORTFORWARD_LOCAL -> {
                val lpf: LocalPortForwarder? = try {
                    connection?.createLocalPortForwarder(
                        InetSocketAddress(InetAddress.getLocalHost(), portForward.sourcePort),
                        portForward.destAddr,
                        portForward.destPort
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Could not create local port forward", e)
                    return false
                }

                if (lpf == null) {
                    Log.e(TAG, "returned LocalPortForwarder object is null")
                    return false
                }

                portForward.setIdentifier(lpf)
                portForward.setEnabled(true)
                true
            }

            HostConstants.PORTFORWARD_REMOTE -> {
                try {
                    connection?.requestRemotePortForwarding("", portForward.sourcePort, portForward.destAddr, portForward.destPort)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not create remote port forward", e)
                    return false
                }

                portForward.setEnabled(true)
                true
            }

            HostConstants.PORTFORWARD_DYNAMIC5 -> {
                val dpf: DynamicPortForwarder? = try {
                    connection?.createDynamicPortForwarder(
                        InetSocketAddress(InetAddress.getLocalHost(), portForward.sourcePort)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Could not create dynamic port forward", e)
                    return false
                }

                portForward.setIdentifier(dpf)
                portForward.setEnabled(true)
                true
            }

            else -> {
                // Unsupported type
                Log.e(TAG, String.format("attempt to forward unknown type %s", portForward.type))
                false
            }
        }
    }

    override fun disablePortForward(portForward: PortForward): Boolean {
        if (!portForwards.contains(portForward)) {
            Log.e(TAG, "Attempt to disable port forward not in list")
            return false
        }

        if (!authenticated)
            return false

        return when (portForward.type) {
            HostConstants.PORTFORWARD_LOCAL -> {
                val lpf = portForward.getIdentifier() as? LocalPortForwarder

                if (!portForward.isEnabled() || lpf == null) {
                    Log.d(TAG, String.format("Could not disable %s; it appears to be not enabled or have no handler", portForward.nickname))
                    return false
                }

                portForward.setEnabled(false)
                lpf.close()
                true
            }

            HostConstants.PORTFORWARD_REMOTE -> {
                portForward.setEnabled(false)

                try {
                    connection?.cancelRemotePortForwarding(portForward.sourcePort)
                } catch (e: IOException) {
                    Log.e(TAG, "Could not stop remote port forwarding, setting enabled to false", e)
                    return false
                }
                true
            }

            HostConstants.PORTFORWARD_DYNAMIC5 -> {
                val dpf = portForward.getIdentifier() as? DynamicPortForwarder

                if (!portForward.isEnabled() || dpf == null) {
                    Log.d(TAG, String.format("Could not disable %s; it appears to be not enabled or have no handler", portForward.nickname))
                    return false
                }

                portForward.setEnabled(false)
                dpf.close()
                true
            }

            else -> {
                // Unsupported type
                Log.e(TAG, String.format("attempt to forward unknown type %s", portForward.type))
                false
            }
        }
    }

    override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        this.columns = columns
        this.rows = rows

        if (sessionOpen) {
            try {
                session?.resizePTY(columns, rows, width, height)
            } catch (e: IOException) {
                Log.e(TAG, "Couldn't send resize PTY packet", e)
            }
        }
    }

    override fun getDefaultPort(): Int = DEFAULT_PORT

    override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String {
        return if (port == DEFAULT_PORT) {
            String.format(Locale.US, "%s@%s", username, hostname)
        } else {
            String.format(Locale.US, "%s@%s:%d", username, hostname, port)
        }
    }

    /**
     * Handle challenges from keyboard-interactive authentication mode.
     */
    override fun replyToChallenge(
        name: String,
        instruction: String,
        numPrompts: Int,
        prompt: Array<String>,
        echo: BooleanArray
    ): Array<String> {
        interactiveCanContinue = true
        val responses = Array(numPrompts) { i ->
            // request response from user for each prompt
            val isPassword = i < echo.size && !echo[i]
            bridge?.requestStringPrompt(instruction, prompt[i], isPassword) ?: ""
        }
        return responses
    }

    override fun createHost(uri: Uri): Host {
        val hostname = uri.host
        val username = uri.userInfo
        var port = uri.port
        if (port < 0)
            port = DEFAULT_PORT
        val nickname = getDefaultNickname(username, hostname, port)

        return Host.createSshHost(
            nickname,
            hostname ?: "",
            port,
            username ?: ""
        )
    }

    override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) {
        selection[HostConstants.FIELD_HOST_PROTOCOL] = PROTOCOL
        selection[HostConstants.FIELD_HOST_NICKNAME] = uri.fragment ?: ""
        selection[HostConstants.FIELD_HOST_HOSTNAME] = uri.host ?: ""

        var port = uri.port
        if (port < 0)
            port = DEFAULT_PORT
        selection[HostConstants.FIELD_HOST_PORT] = port.toString()
        selection[HostConstants.FIELD_HOST_USERNAME] = uri.userInfo ?: ""
    }

    override fun setCompression(compression: Boolean) {
        this.compression = compression
    }

    override fun setUseAuthAgent(useAuthAgent: String) {
        this.useAuthAgent = useAuthAgent
    }

    override fun retrieveIdentities(): Map<String, ByteArray> {
        val pubKeys = HashMap<String, ByteArray>(manager?.loadedKeypairs?.size ?: 0)

        manager?.loadedKeypairs?.entries?.forEach { entry ->
            val pair = entry.value.pair ?: return@forEach
            try {
                val privKey = pair.private
                when (privKey) {
                    is RSAPrivateKey -> {
                        val pubkey = pair.public as RSAPublicKey
                        pubKeys[entry.key] = RSASHA1Verify.get().encodePublicKey(pubkey)
                    }
                    is DSAPrivateKey -> {
                        val pubkey = pair.public as DSAPublicKey
                        pubKeys[entry.key] = DSASHA1Verify.get().encodePublicKey(pubkey)
                    }
                    is ECPrivateKey -> {
                        val pubkey = pair.public as ECPublicKey
                        pubKeys[entry.key] = ECDSASHA2Verify.getVerifierForKey(pubkey).encodePublicKey(pubkey)
                    }
                    is Ed25519PrivateKey -> {
                        val pubkey = pair.public as Ed25519PublicKey
                        pubKeys[entry.key] = Ed25519Verify.get().encodePublicKey(pubkey)
                    }
                }
            } catch (ignored: IOException) {
            }
        }

        return pubKeys
    }

    override fun getKeyPair(publicKey: ByteArray): KeyPair? {
        val nickname = manager?.getKeyNickname(publicKey) ?: return null

        if (useAuthAgent == HostConstants.AUTHAGENT_NO) {
            Log.e(TAG, "")
            return null
        }
        if (useAuthAgent == HostConstants.AUTHAGENT_CONFIRM) {
            val holder = manager?.loadedKeypairs?.get(nickname)
            if (holder != null && holder.pubkey?.confirmation == true && !promptForPubkeyUse(nickname))
                return null
        }
        return manager?.getKey(nickname)
    }

    private fun promptForPubkeyUse(nickname: String): Boolean {
        val result = bridge?.requestBooleanPrompt(
            null,
            manager?.res?.getString(R.string.prompt_allow_agent_to_use_key, nickname) ?: ""
        )
        return result ?: false
    }

    override fun addIdentity(pair: KeyPair, comment: String, confirmUse: Boolean, lifetime: Int): Boolean {
        // Create a temporary pubkey for in-memory storage (not persisted to database)
        // Note: lifetime functionality is not yet implemented in Pubkey entity
        val pubkey = Pubkey(
            id = 0L, // temporary, not saved to database
            nickname = comment,
            type = "IMPORTED",
            privateKey = byteArrayOf(), // not needed for agent forwarding
            publicKey = pair.public.encoded,
            encrypted = false,
            startup = false,
            confirmation = confirmUse,
            createdDate = System.currentTimeMillis(),
            storageType = KeyStorageType.EXPORTABLE,
            allowBackup = true,
            keystoreAlias = null
        )
        manager?.addKey(pubkey, pair)
        return true
    }

    override fun removeAllIdentities(): Boolean {
        manager?.loadedKeypairs?.clear()
        return true
    }

    override fun removeIdentity(publicKey: ByteArray): Boolean {
        return manager?.removeKey(publicKey) ?: false
    }

    override fun isAgentLocked(): Boolean = agentLockPassphrase != null

    override fun requestAgentUnlock(unlockPassphrase: String): Boolean {
        if (agentLockPassphrase == null)
            return false

        if (agentLockPassphrase == unlockPassphrase)
            agentLockPassphrase = null

        return agentLockPassphrase == null
    }

    override fun setAgentLock(lockPassphrase: String): Boolean {
        if (agentLockPassphrase != null)
            return false

        agentLockPassphrase = lockPassphrase
        return true
    }

    override fun usesNetwork(): Boolean = true

    companion object {
        init {
            // Since this class deals with Ed25519 keys, we need to make sure this is available.
            Ed25519Provider.insertIfNeeded()
        }

        private const val PROTOCOL = "ssh"
        private const val TAG = "CB.SSH"
        private const val DEFAULT_PORT = 22

        private const val AUTH_PUBLICKEY = "publickey"
        private const val AUTH_PASSWORD = "password"
        private const val AUTH_KEYBOARDINTERACTIVE = "keyboard-interactive"

        private const val AUTH_TRIES = 20

        private val hostmask = Pattern.compile(
            "^(.+)@((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?\$",
            Pattern.CASE_INSENSITIVE
        )

        private const val conditions = (ChannelCondition.STDOUT_DATA
                or ChannelCondition.STDERR_DATA
                or ChannelCondition.CLOSED
                or ChannelCondition.EOF)

        @JvmStatic
        fun getProtocolName(): String = PROTOCOL

        @JvmStatic
        fun getUri(input: String): Uri? {
            val matcher = hostmask.matcher(input)

            if (!matcher.matches())
                return null

            val sb = StringBuilder()

            sb.append(PROTOCOL)
                .append("://")
                .append(Uri.encode(matcher.group(1)))
                .append('@')
                .append(Uri.encode(matcher.group(2)))

            val portString = matcher.group(3)
            var port = DEFAULT_PORT
            if (portString != null) {
                try {
                    port = portString.toInt()
                    if (port < 1 || port > 65535) {
                        port = DEFAULT_PORT
                    }
                } catch (nfe: NumberFormatException) {
                    // Keep the default port
                }
            }

            if (port != DEFAULT_PORT) {
                sb.append(':')
                    .append(port)
            }

            sb.append("/#")
                .append(Uri.encode(input))

            return Uri.parse(sb.toString())
        }

        @JvmStatic
        fun getFormatHint(context: Context): String {
            return String.format(
                "%s@%s:%s",
                context.getString(R.string.format_username),
                context.getString(R.string.format_hostname),
                context.getString(R.string.format_port)
            )
        }
    }
}
