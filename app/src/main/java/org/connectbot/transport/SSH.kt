/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007-2026 Kenny Root
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
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.trilead.ssh2.AuthAgentCallback
import com.trilead.ssh2.ChannelCondition
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ConnectionMonitor
import com.trilead.ssh2.DynamicPortForwarder
import com.trilead.ssh2.ExtendedServerHostKeyVerifier
import com.trilead.ssh2.InteractiveCallback
import com.trilead.ssh2.IpVersion
import com.trilead.ssh2.KnownHosts
import com.trilead.ssh2.LocalPortForwarder
import com.trilead.ssh2.Session
import com.trilead.ssh2.UserAuthBannerCallback
import com.trilead.ssh2.crypto.PEMDecoder
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
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.service.requestBiometricAuth
import org.connectbot.service.requestBooleanPrompt
import org.connectbot.service.requestHostKeyFingerprintPrompt
import org.connectbot.service.requestStringPrompt
import org.connectbot.util.HostConstants
import org.connectbot.util.PubkeyUtils
import org.connectbot.util.SshKeyType
import org.connectbot.util.UrlUtils
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.interfaces.DSAPrivateKey
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Locale
import java.util.regex.Pattern

/**
 * @author Kenny Root
 */
open class SSH :
    AbsTransport,
    ConnectionMonitor,
    InteractiveCallback,
    AuthAgentCallback {

    @JvmField
    protected var compressionEnabled = false

    @Volatile
    protected var authenticated = false

    @Volatile
    protected var connected = false

    @Volatile
    protected var sessionOpen = false

    protected var connection: Connection? = null
    private val jumpConnections: MutableList<Connection> = mutableListOf()
    protected var session: Session? = null

    protected var stdin: OutputStream? = null
    protected var stdout: InputStream? = null
    protected var stderr: InputStream? = null

    private val portForwards = mutableListOf<PortForward>()
    private val userAuthBannerCallbacks = mutableListOf<Pair<Connection, UserAuthBannerCallback>>()

    protected var columns: Int = 0
    protected var rows: Int = 0

    protected var width: Int = 0
    protected var height: Int = 0

    private var useAuthAgent = HostConstants.AUTHAGENT_NO
    private var agentLockPassphrase: String? = null

    constructor() : super()

    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) : super(host, bridge, manager)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun setConnectionForTesting(connection: Connection?) {
        this.connection = connection
    }

    private fun registerUserAuthBanner(connection: Connection, sourceName: String) {
        val callback = UserAuthBannerCallback { banner, languageTag ->
            handleAuthBanner(sourceName, banner, languageTag)
        }
        connection.addUserAuthBanner(callback)
        synchronized(userAuthBannerCallbacks) {
            userAuthBannerCallbacks.add(connection to callback)
        }
    }

    private fun unregisterUserAuthBanner(connection: Connection) {
        synchronized(userAuthBannerCallbacks) {
            val iterator = userAuthBannerCallbacks.iterator()
            while (iterator.hasNext()) {
                val (registeredConnection, callback) = iterator.next()
                if (registeredConnection == connection) {
                    runCatching {
                        registeredConnection.removeUserAuthBanner(callback)
                    }
                    iterator.remove()
                }
            }
        }
    }

    @VisibleForTesting
    fun handleAuthBanner(sourceName: String, banner: String?, languageTag: String?) {
        val trimmedBanner = banner?.trim()
        if (trimmedBanner.isNullOrEmpty()) return

        val header = manager?.res?.getString(R.string.terminal_auth_banner_header, sourceName)
            ?: "[$sourceName] Authentication message:"
        bridge?.outputLine(header)
        bridge?.outputLine(trimmedBanner)

        val urls = UrlUtils.extractUrls(trimmedBanner)
        if (urls.isNotEmpty()) {
            bridge?.enqueueAuthBanner(sourceName, trimmedBanner, urls, languageTag)
        }
    }

    private fun Host.authBannerSourceName(): String {
        if (nickname.isNotBlank()) return nickname

        val userPrefix = username.takeIf { it.isNotBlank() }?.let { "$it@" } ?: ""
        return if (port == DEFAULT_PORT) {
            "$userPrefix$hostname"
        } else {
            "$userPrefix$hostname:$port"
        }
    }

    private fun decodePublicKey(algorithm: String, keyBlob: ByteArray): PublicKey? = try {
        when (algorithm) {
            "ssh-rsa", "rsa-sha2-256", "rsa-sha2-512" -> RSASHA1Verify.get().decodePublicKey(keyBlob)
            "ssh-dss" -> DSASHA1Verify.get().decodePublicKey(keyBlob)
            "ssh-ed25519" -> Ed25519Verify.get().decodePublicKey(keyBlob)
            "ecdsa-sha2-nistp256" -> ECDSASHA2Verify.ECDSASHA2NISTP256Verify.get().decodePublicKey(keyBlob)
            "ecdsa-sha2-nistp384" -> ECDSASHA2Verify.ECDSASHA2NISTP384Verify.get().decodePublicKey(keyBlob)
            "ecdsa-sha2-nistp521" -> ECDSASHA2Verify.ECDSASHA2NISTP521Verify.get().decodePublicKey(keyBlob)
            else -> null
        }
    } catch (e: IOException) {
        Timber.e(e, "Failed to decode public key")
        null
    }

    private fun getKeySize(publicKey: PublicKey?): Int = when (publicKey) {
        is RSAPublicKey -> publicKey.modulus.bitLength()
        is DSAPublicKey -> publicKey.params.p.bitLength()
        is ECPublicKey -> publicKey.params.curve.field.fieldSize
        is Ed25519PublicKey -> 256
        else -> 0
    }

    @VisibleForTesting
    internal fun getKeyType(openSshKeyType: String): String? = SshKeyType.fromOpenSshType(openSshKeyType)?.storedName

    open inner class HostKeyVerifier(private val verifyHost: Host? = host) : ExtendedServerHostKeyVerifier() {
        @Throws(IOException::class)
        override fun verifyServerHostKey(
            hostname: String,
            port: Int,
            serverHostKeyAlgorithm: String,
            serverHostKey: ByteArray,
        ): Boolean {
            // Get known hosts for this specific host entry
            val hostId = verifyHost?.id ?: return false
            val knownHostsList = manager?.hostRepository?.getKnownHostsForHostBlocking(hostId) ?: emptyList()

            // Convert to KnownHosts format, grouping by (algo, key) to handle renamed hosts
            val hosts = KnownHosts()
            data class HostKeyGroup(val algo: String, val key: ByteArray) {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other !is HostKeyGroup) return false
                    return algo == other.algo && key.contentEquals(other.key)
                }
                override fun hashCode(): Int {
                    var result = algo.hashCode()
                    result = 31 * result + key.contentHashCode()
                    return result
                }
            }

            knownHostsList.groupBy { HostKeyGroup(it.hostKeyAlgo, it.hostKey) }.forEach { (group, entries) ->
                try {
                    // Collect all hostname:port combinations for this key
                    val hostnames = entries.map { "${it.hostname}:${it.port}" }.toTypedArray()
                    hosts.addHostkey(hostnames, group.algo, group.key)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to add known host key")
                }
            }

            val matchName = String.format(Locale.US, "%s:%d", hostname, port)
            val algorithmName = getKeyType(serverHostKeyAlgorithm)
            val sha256 = KeyFingerprint.createSHA256Fingerprint(serverHostKey)
            val md5 = KeyFingerprint.createMD5Fingerprint(serverHostKey)
            val fingerprint = buildString {
                append("\nMD5:")
                append(md5)
                append("\n")
                append(sha256)
            }

            return when (hosts.verifyHostkey(matchName, serverHostKeyAlgorithm, serverHostKey)) {
                KnownHosts.HOSTKEY_IS_OK -> {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_sucess, algorithmName, fingerprint))
                    true
                }

                KnownHosts.HOSTKEY_IS_NEW -> {
                    // Keep terminal output for backward compatibility
                    bridge?.outputLine(manager?.res?.getString(R.string.host_authenticity_warning, hostname))
                    bridge?.outputLine(manager?.res?.getString(R.string.host_fingerprint, algorithmName, fingerprint))

                    // Prepare data for inline prompt
                    val publicKey = decodePublicKey(serverHostKeyAlgorithm, serverHostKey)
                    val keySize = getKeySize(publicKey)

                    val randomArt = KeyFingerprint.createRandomArt(
                        serverHostKey,
                        algorithmName ?: "UNKNOWN",
                        keySize,
                    )
                    val bubblebabble = KeyFingerprint.createBubblebabbleFingerprint(serverHostKey)

                    // Show inline prompt with all fingerprint formats
                    val result = bridge?.requestHostKeyFingerprintPrompt(
                        hostname = hostname,
                        keyType = algorithmName ?: "UNKNOWN",
                        keySize = keySize,
                        serverHostKey = serverHostKey,
                        randomArt = randomArt,
                        bubblebabble = bubblebabble,
                        sha256 = sha256,
                        md5 = md5,
                    )

                    if (result == null) {
                        return false
                    }
                    if (result) {
                        // save this key in known database
                        verifyHost.let {
                            manager?.hostRepository?.saveKnownHostBlocking(it, hostname, port, serverHostKeyAlgorithm, serverHostKey)
                        }
                    }
                    result
                }

                KnownHosts.HOSTKEY_HAS_CHANGED -> {
                    val header = String.format(
                        "@   %s   @",
                        manager?.res?.getString(R.string.host_verification_failure_warning_header),
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
                            fingerprint,
                        ),
                    )

                    // Users have no way to delete keys, so we'll prompt them for now.
                    val result = bridge?.requestBooleanPrompt(
                        null,
                        manager?.res?.getString(R.string.prompt_continue_connecting) ?: "",
                    )
                    if (result != null && result) {
                        // save this key in known database
                        verifyHost.let {
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

        override fun getKnownKeyAlgorithmsForHost(host: String, port: Int): List<String>? = verifyHost?.id?.let { hostId ->
            manager?.hostRepository?.getHostKeyAlgorithmsForHostBlocking(hostId)
        }

        override fun removeServerHostKey(host: String, port: Int, algorithm: String, hostKey: ByteArray?) {
            verifyHost?.id?.let { hostId ->
                manager?.hostRepository?.removeKnownHostBlocking(hostId, algorithm, hostKey)
            }
        }

        override fun addServerHostKey(hostname: String, port: Int, algorithm: String, hostKey: ByteArray) {
            verifyHost?.let {
                manager?.hostRepository?.saveKnownHostBlocking(it, hostname, port, algorithm, hostKey)
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun authenticate(): AuthenticationOutcome {
        if (host?.username.isNullOrEmpty()) {
            val username = bridge?.requestStringPrompt(null, manager?.res?.getString(R.string.prompt_username), false)
            if (username.isNullOrEmpty()) return AuthenticationOutcome.Cancelled
            host = host?.copy(username = username)
        }
        val currentHost = host ?: return AuthenticationOutcome.Cancelled
        val currentConnection = connection ?: return AuthenticationOutcome.ConnectionFailed
        val outcome = authenticateConnection(currentConnection, currentHost, false)
        if (outcome == AuthenticationOutcome.Authenticated) {
            finishConnection()
        } else if (outcome == AuthenticationOutcome.Exhausted) {
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_fail))
        }
        return outcome
    }

    private fun authenticateConnection(jc: Connection, authHost: Host, isJump: Boolean): AuthenticationOutcome {
        val sourceName = authHost.authBannerSourceName()
        // Freeze the unlocked identities at the start of this connection's authentication.
        val loadedKeys = manager?.loadedKeypairs?.entries?.mapNotNull { entry ->
            val pair = entry.value.pair ?: return@mapNotNull null
            Triple(entry.key, entry.value, pair)
        }?.sortedBy { it.first }.orEmpty()
        try {
            return SshAuthenticationRunner(
                connection = jc,
                username = authHost.username,
                identities = {
                    when (authHost.pubkeyId) {
                        HostConstants.PUBKEYID_NEVER -> emptyList()

                        HostConstants.PUBKEYID_ANY -> loadedKeys.mapNotNull { (nickname, holder, pair) ->
                            createAuthenticationIdentity(
                                jc,
                                nickname,
                                pair,
                                holder.pubkey,
                                isAvailable = { manager?.loadedKeypairs?.get(nickname) === holder },
                            )
                        }

                        else -> {
                            val pubkey = manager?.pubkeyRepository?.getByIdBlocking(authHost.pubkeyId)
                            if (pubkey == null) {
                                bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_invalid))
                                emptyList()
                            } else {
                                val pair = getAuthenticationKey(pubkey)
                                if (pair == null) {
                                    emptyList()
                                } else {
                                    val holder = manager?.loadedKeypairs?.get(pubkey.nickname)
                                    listOfNotNull(
                                        createAuthenticationIdentity(jc, pubkey.nickname, pair, pubkey) {
                                            holder == null || manager?.loadedKeypairs?.get(pubkey.nickname) === holder
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                savedPassword = {
                    manager?.securePasswordStorage?.getPassword(authHost.id)?.also {
                        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_saved_password))
                    }
                },
                passwordPrompt = {
                    val message = if (isJump) {
                        manager?.res?.getString(R.string.terminal_jump_password, authHost.nickname)
                    } else {
                        manager?.res?.getString(R.string.prompt_password)
                    }
                    bridge?.requestStringPrompt(null, message, true)
                },
                interactive = InteractiveCallback { _, instruction, count, prompts, echo ->
                    Array(count) { index ->
                        val prefix = if (isJump) manager?.res?.getString(R.string.terminal_jump_prompt, authHost.nickname).orEmpty() + " " else ""
                        bridge?.requestStringPrompt(instruction, prefix + prompts[index], index < echo.size && !echo[index])
                            ?: throw AuthenticationCancelledException()
                    }
                },
                onNoneComplete = {
                    if (!isJump) bridge?.dismissAuthBannersFrom(sourceName)
                },
                onMethod = { method ->
                    val message = when (method) {
                        AUTH_PUBLICKEY -> if (authHost.pubkeyId == HostConstants.PUBKEYID_ANY) R.string.terminal_auth_pubkey_any else R.string.terminal_auth_pubkey_specific
                        AUTH_KEYBOARDINTERACTIVE -> R.string.terminal_auth_ki
                        else -> R.string.terminal_auth_pass
                    }
                    bridge?.outputLine(manager?.res?.getString(message))
                },
                onKeyRejected = { nickname -> bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_fail, nickname)) },
                onMethodRejected = { method ->
                    val message = if (method == AUTH_KEYBOARDINTERACTIVE) R.string.terminal_auth_ki_fail else R.string.terminal_auth_pass_fail
                    bridge?.outputLine(manager?.res?.getString(message))
                },
                onFailure = { e -> Timber.e(e, "Connection failed during authentication for %s", sourceName) },
            ).authenticate()
        } finally {
            bridge?.dismissAuthBannersFrom(sourceName)
        }
    }

    private fun getAuthenticationKey(pubkey: Pubkey): KeyPair? = try {
        getOrUnlockKey(pubkey)
    } catch (e: AuthenticationCancelledException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Unable to unlock public key '%s'", pubkey.nickname)
        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_fail, pubkey.nickname))
        null
    }

    private fun createAuthenticationIdentity(
        jc: Connection,
        nickname: String,
        pair: KeyPair,
        pubkey: Pubkey?,
        isAvailable: () -> Boolean = { true },
    ): AuthenticationIdentity? = try {
        AuthenticationIdentity(
            nickname,
            pair,
            isAvailable,
            confirm = { pubkey?.confirmation != true || promptForPubkeyUse(nickname) },
            prepare = { preparePublicKeyAuthentication(jc, pair, pubkey?.storageType) },
        )
    } catch (e: IOException) {
        Timber.w(e, "Unable to encode public key '%s'", nickname)
        null
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
            Timber.d(String.format("Found unlocked key '%s' already in-memory", pubkey.nickname))
            return manager?.getKey(pubkey.nickname)
        }

        // Handle Android Keystore (biometric) keys
        if (pubkey.storageType == KeyStorageType.ANDROID_KEYSTORE) {
            val keystoreAlias = pubkey.keystoreAlias
            if (keystoreAlias == null) {
                val message = String.format("Keystore alias missing for key '%s'. Authentication failed.", pubkey.nickname)
                Timber.e(message)
                bridge?.outputLine(message)
                return null
            }

            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_biometric, pubkey.nickname))

            // Request biometric authentication
            val biometricSuccess = bridge?.requestBiometricAuth(pubkey.nickname, keystoreAlias) ?: false
            if (!biometricSuccess) {
                val message = String.format("Biometric authentication failed for key '%s'.", pubkey.nickname)
                Timber.e(message)
                bridge?.outputLine(message)
                return null
            }

            // Load the key from Keystore after successful biometric auth
            return try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore")
                keyStore.load(null)
                val publicKey = keyStore.getCertificate(keystoreAlias)?.publicKey
                val privateKey = keyStore.getKey(keystoreAlias, null) as? PrivateKey

                if (publicKey == null || privateKey == null) {
                    val message = String.format("Failed to load key '%s' from Keystore.", pubkey.nickname)
                    Timber.e(message)
                    bridge?.outputLine(message)
                    return null
                }

                val pair = KeyPair(publicKey, privateKey)
                when (val result = manager?.addBiometricKey(pubkey, keystoreAlias, publicKey)) {
                    is TerminalManager.BiometricKeyResult.Success -> {
                        Timber.d(String.format("Unlocked biometric key '%s'", pubkey.nickname))
                        pair
                    }

                    is TerminalManager.BiometricKeyResult.KeyInvalidated -> {
                        bridge?.outputLine(result.message)
                        null
                    }

                    is TerminalManager.BiometricKeyResult.Error -> {
                        bridge?.outputLine(result.message)
                        null
                    }

                    null -> {
                        val message = String.format("Failed to add biometric key '%s' to cache.", pubkey.nickname)
                        Timber.e(message)
                        bridge?.outputLine(message)
                        null
                    }
                }
            } catch (e: Exception) {
                val message = String.format("Failed to load biometric key '%s': %s", pubkey.nickname, e.message)
                Timber.e(e, message)
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
                true,
            )

            // Something must have interrupted the prompt.
            if (password == null) {
                throw AuthenticationCancelledException()
            }
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
                Timber.e(e, message)
                bridge?.outputLine(message)
                return null
            }

            if (privKey == null) {
                val message = String.format("Failed to decode private key '%s'. Authentication failed.", pubkey.nickname)
                Timber.e(message)
                bridge?.outputLine(message)
                return null
            }

            val pubKey = PubkeyUtils.decodePublic(pubkey.publicKey, pubkey.type)

            // convert key to trilead format
            KeyPair(pubKey, privKey).also {
                Timber.d("Unlocked key %s", PubkeyUtils.formatKey(pubKey))
            }
        }

        Timber.d(String.format("Unlocked key '%s'", pubkey.nickname))

        // save this key in memory
        manager?.addKey(pubkey, pair)

        return pair
    }

    private fun preparePublicKeyAuthentication(
        connection: Connection?,
        pair: KeyPair,
        storageType: KeyStorageType?,
    ): Boolean {
        if (storageType != KeyStorageType.ANDROID_KEYSTORE || pair.public !is RSAPublicKey) {
            return true
        }

        return connection != null && RsaSignatureAlgorithmPolicy.prepareForAuthentication(connection)
    }

    /**
     * Internal method to request actual PTY terminal once we've finished
     * authentication. If called before authenticated, it will just fail.
     */
    protected open fun finishConnection() {
        authenticated = true

        for (portForward in portForwards) {
            if (!portForward.startEnabled) continue
            try {
                enablePortForward(portForward)
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_enable_portfoward, portForward.getDescription()))
            } catch (e: Exception) {
                Timber.e(e, "Error setting up port forward during connect")
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

            if (useAuthAgent != HostConstants.AUTHAGENT_NO) {
                session?.requestAuthAgentForwarding(this)
            }

            session?.requestPTY(getEmulation(), columns, rows, width, height, null)
            session?.startShell()

            stdin = session?.stdin
            stdout = session?.stdout
            stderr = session?.stderr

            sessionOpen = true

            bridge?.onConnected()
        } catch (e1: IOException) {
            Timber.e(e1, "Problem while trying to create PTY in finishConnection()")
        }
    }

    /**
     * Establish and authenticate a connection to the jump host.
     * This is called before connecting to the target host when ProxyJump is configured.
     * The chain is resolved and validated by connect() before any connections are opened.
     *
     * @param jumpHost The jump host configuration
     * @param proxyConnection The already authenticated outer hop, if any
     * @return The authenticated Connection, or null if connection/authentication failed
     */
    private fun connectToJumpHost(jumpHost: Host, proxyConnection: Connection?): Connection? {
        bridge?.outputLine(manager?.res?.getString(R.string.terminal_connecting_via_jump, jumpHost.nickname))

        val jc = Connection(jumpHost.hostname, jumpHost.port)
        registerUserAuthBanner(jc, jumpHost.authBannerSourceName())

        try {
            proxyConnection?.let { jc.setProxyData(JumpHostProxyData(it)) }

            if (jumpHost.compression) {
                jc.setCompression(true)
            }

            // Connect to jump host
            jc.connect(HostKeyVerifier(jumpHost), parseIpVersion(jumpHost.ipVersion, jumpHost.hostname))

            // Track this connection for cleanup
            jumpConnections.add(jc)

            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_connected, jumpHost.nickname))

            // Authenticate to jump host
            if (!authenticateJumpHost(jc, jumpHost)) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_auth_failed, jumpHost.nickname))
                unregisterUserAuthBanner(jc)
                jc.close()
                jumpConnections.remove(jc)
                return null
            }

            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_authenticated, jumpHost.nickname))
            return jc
        } catch (e: IOException) {
            Timber.e(e, "Failed to connect to jump host: ${jumpHost.nickname}")
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_failed, jumpHost.nickname, e.message))
            try {
                unregisterUserAuthBanner(jc)
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
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun authenticateJumpHost(jc: Connection, jumpHost: Host): Boolean = authenticateConnection(jc, jumpHost, true) == AuthenticationOutcome.Authenticated

    override fun connect() {
        val currentHost = host ?: return

        val jumpHosts = try {
            resolveJumpHostChain(currentHost) { manager?.hostRepository?.findHostByIdBlocking(it) }
        } catch (e: JumpHostChainException) {
            bridge?.outputLine(manager?.res?.getString(e.messageResId))
            onDisconnect()
            return
        }

        var directJumpConnection: Connection? = null
        for (jumpHost in jumpHosts.asReversed()) {
            directJumpConnection = connectToJumpHost(jumpHost, directJumpConnection)
            if (directJumpConnection == null) {
                close()
                onDisconnect()
                return
            }
        }

        connection = Connection(currentHost.hostname, currentHost.port)
        connection?.addConnectionMonitor(this)
        connection?.let { registerUserAuthBanner(it, currentHost.authBannerSourceName()) }

        // If we have a jump host connection, set up the proxy
        directJumpConnection?.let {
            connection?.setProxyData(JumpHostProxyData(it))
        }

        try {
            connection?.setCompression(compressionEnabled)
        } catch (e: IOException) {
            Timber.e(e, "Could not enable compression!")
        }

        try {
            val connectionInfo = connection?.connect(
                HostKeyVerifier(),
                parseIpVersion(currentHost.ipVersion, currentHost.hostname),
            ) ?: throw IOException("Connection failed")
            connected = true

            bridge?.outputLine(
                manager?.res?.getString(R.string.terminal_kex_algorithm, connectionInfo.keyExchangeAlgorithm),
            )

            if (connectionInfo.clientToServerCryptoAlgorithm == connectionInfo.serverToClientCryptoAlgorithm &&
                connectionInfo.clientToServerMACAlgorithm == connectionInfo.serverToClientMACAlgorithm
            ) {
                bridge?.outputLine(
                    manager?.res?.getString(
                        R.string.terminal_using_algorithm,
                        connectionInfo.clientToServerCryptoAlgorithm,
                        connectionInfo.clientToServerMACAlgorithm ?: "",
                    ),
                )
            } else {
                bridge?.outputLine(
                    manager?.res?.getString(
                        R.string.terminal_using_c2s_algorithm,
                        connectionInfo.clientToServerCryptoAlgorithm,
                        connectionInfo.clientToServerMACAlgorithm ?: "",
                    ),
                )

                bridge?.outputLine(
                    manager?.res?.getString(
                        R.string.terminal_using_s2c_algorithm,
                        connectionInfo.serverToClientCryptoAlgorithm,
                        connectionInfo.serverToClientMACAlgorithm ?: "",
                    ),
                )
            }
        } catch (e: IOException) {
            Timber.e(e, "Problem in SSH connection thread during authentication")

            // Display the reason in the text.
            var t: Throwable? = e
            while (t != null) {
                val message = t.message
                if (message != null) {
                    bridge?.outputLine(message)
                    if (t is NoRouteToHostException) {
                        bridge?.outputLine(manager?.res?.getString(R.string.terminal_no_route))
                    }
                }
                t = t.cause
            }

            close()
            onDisconnect()
            return
        }

        try {
            val outcome = authenticate()
            if (connected && outcome != AuthenticationOutcome.Authenticated) {
                onDisconnect(if (outcome == AuthenticationOutcome.ConnectionFailed) DisconnectReason.IO_ERROR else DisconnectReason.AUTH_FAIL)
                close()
            }
        } catch (e: Exception) {
            Timber.e(e, "Problem in SSH connection thread during authentication")
            onDisconnect()
            close()
        }
    }

    override fun close() {
        // Don't close during grace period - wait for network restore
        if (bridge?.isInGracePeriod() == true) {
            Timber.d("Deferring SSH close - bridge in network grace period")
            return
        }

        connected = false

        session?.close()
        session = null

        connection?.let { unregisterUserAuthBanner(it) }
        connection?.close()
        connection = null

        // Close all jump host connections (in reverse order)
        jumpConnections.asReversed().forEach { jc ->
            try {
                unregisterUserAuthBanner(jc)
                jc.close()
            } catch (ignored: Exception) {
            }
        }
        jumpConnections.clear()
        synchronized(userAuthBannerCallbacks) {
            userAuthBannerCallbacks.clear()
        }
    }

    private fun onDisconnect(reason: DisconnectReason = DisconnectReason.IO_ERROR) {
        bridge?.dispatchDisconnect(reason)
    }

    @VisibleForTesting
    internal fun getDisconnectReasonForClosedSession(session: Session): DisconnectReason {
        if (session.exitStatus != null) return DisconnectReason.SESSION_EXIT

        // Channel EOF may arrive before the server's exit-status request.
        // This runs in Relay's IO dispatcher, so the short wait does not block UI.
        val closeCondition = session.waitForCondition(
            ChannelCondition.EXIT_STATUS or ChannelCondition.EXIT_SIGNAL,
            EXIT_STATUS_WAIT_MS,
        )
        if ((closeCondition and ChannelCondition.EXIT_SIGNAL) != 0 || session.exitSignal != null) {
            return DisconnectReason.REMOTE_EOF
        }
        return if (session.exitStatus != null) DisconnectReason.SESSION_EXIT else DisconnectReason.REMOTE_EOF
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
            // A final data packet and EOF may be reported together. Deliver the
            // data first; the next read will observe EOF and disconnect.
            if (bytesRead > 0) {
                return bytesRead
            }

            // TerminalBridge owns closing a connected transport after it has
            // transitioned the bridge to disconnected. Calling close() here as
            // well races with that asynchronous close and can re-enter through
            // ConnectionMonitor.connectionLost().
            val currentBridge = bridge
            if (currentBridge != null) {
                currentBridge.dispatchDisconnect(getDisconnectReasonForClosedSession(currentSession))
            } else {
                // SSH is normally always attached to a bridge. Preserve the
                // transport contract for callers that use it independently.
                close()
            }
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

    override fun getOptions(): Map<String, String> = mapOf("compression" to compressionEnabled.toString())

    override fun setOptions(options: Map<String, String>) {
        if (options.containsKey("compression")) {
            compressionEnabled = options["compression"]?.toBoolean() ?: false
        }
    }

    override fun isSessionOpen(): Boolean = sessionOpen

    override fun isConnected(): Boolean = connected

    override fun connectionLost(reason: Throwable) {
        // During grace period, SSH disconnect is EXPECTED (network loss)
        // Don't trigger disconnect - let grace period handle it
        if (bridge?.isInGracePeriod() == true) {
            Timber.d("SSH connection lost during grace period (expected due to network loss)")
            return
        }

        // Unexpected disconnect - normal flow
        Timber.d("SSH connection lost outside grace period - disconnecting")
        onDisconnect()
    }

    override fun canForwardPorts(): Boolean = true

    override fun getPortForwards(): List<PortForward> = portForwards

    override fun addPortForward(portForward: PortForward): Boolean = portForwards.add(portForward)

    override fun removePortForward(portForward: PortForward): Boolean {
        // Make sure we don't have a phantom forwarder.
        disablePortForward(portForward)
        return portForwards.remove(portForward)
    }

    override fun enablePortForward(portForward: PortForward): Boolean {
        if (!portForwards.contains(portForward)) {
            Timber.e("Attempt to enable port forward not in list")
            return false
        }

        if (!authenticated) {
            return false
        }

        return when (portForward.type) {
            HostConstants.PORTFORWARD_LOCAL -> {
                val lpf: LocalPortForwarder? = try {
                    connection?.createLocalPortForwarder(
                        InetSocketAddress(InetAddress.getLocalHost(), portForward.sourcePort),
                        portForward.destAddr,
                        portForward.destPort,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Could not create local port forward")
                    return false
                }

                if (lpf == null) {
                    Timber.e("returned LocalPortForwarder object is null")
                    return false
                }

                portForward.setIdentifier(lpf)
                portForward.setEnabled(true)
                true
            }

            HostConstants.PORTFORWARD_REMOTE -> {
                try {
                    connection?.requestRemotePortForwarding(portForward.sourceAddr, portForward.sourcePort, portForward.destAddr, portForward.destPort)
                } catch (e: Exception) {
                    Timber.e(e, "Could not create remote port forward")
                    return false
                }

                portForward.setEnabled(true)
                true
            }

            HostConstants.PORTFORWARD_DYNAMIC5 -> {
                val dpf: DynamicPortForwarder? = try {
                    connection?.createDynamicPortForwarder(
                        InetSocketAddress(InetAddress.getLocalHost(), portForward.sourcePort),
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Could not create dynamic port forward")
                    return false
                }

                portForward.setIdentifier(dpf)
                portForward.setEnabled(true)
                true
            }

            else -> {
                // Unsupported type
                Timber.e(String.format("attempt to forward unknown type %s", portForward.type))
                false
            }
        }
    }

    override fun disablePortForward(portForward: PortForward): Boolean {
        if (!portForwards.contains(portForward)) {
            Timber.e("Attempt to disable port forward not in list")
            return false
        }

        if (!authenticated) {
            return false
        }

        return when (portForward.type) {
            HostConstants.PORTFORWARD_LOCAL -> {
                val lpf = portForward.getIdentifier() as? LocalPortForwarder

                if (!portForward.isEnabled() || lpf == null) {
                    Timber.d(String.format("Could not disable %s; it appears to be not enabled or have no handler", portForward.nickname))
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
                    Timber.e(e, "Could not stop remote port forwarding, setting enabled to false")
                    return false
                }
                true
            }

            HostConstants.PORTFORWARD_DYNAMIC5 -> {
                val dpf = portForward.getIdentifier() as? DynamicPortForwarder

                if (!portForward.isEnabled() || dpf == null) {
                    Timber.d(String.format("Could not disable %s; it appears to be not enabled or have no handler", portForward.nickname))
                    return false
                }

                portForward.setEnabled(false)
                dpf.close()
                true
            }

            else -> {
                // Unsupported type
                Timber.e(String.format("attempt to forward unknown type %s", portForward.type))
                false
            }
        }
    }

    override fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        this.columns = columns
        this.rows = rows
        this.width = width
        this.height = height

        if (sessionOpen) {
            try {
                session?.resizePTY(columns, rows, width, height)
            } catch (e: IOException) {
                Timber.e(e, "Couldn't send resize PTY packet")
            }
        }
    }

    override fun getDefaultPort(): Int = DEFAULT_PORT

    override fun getDefaultNickname(username: String?, hostname: String?, port: Int): String = if (port == DEFAULT_PORT) {
        String.format(Locale.US, "%s@%s", username, hostname)
    } else {
        String.format(Locale.US, "%s@%s:%d", username, hostname, port)
    }

    /**
     * Handle challenges from keyboard-interactive authentication mode.
     */
    override fun replyToChallenge(
        name: String,
        instruction: String,
        numPrompts: Int,
        prompt: Array<String>,
        echo: BooleanArray,
    ): Array<String> = Array(numPrompts) { i ->
        bridge?.requestStringPrompt(instruction, prompt[i], i < echo.size && !echo[i])
            ?: throw AuthenticationCancelledException()
    }

    override fun createHost(uri: Uri): Host {
        val hostname = uri.host
        val username = uri.userInfo
        var port = uri.port
        if (port < 0) {
            port = DEFAULT_PORT
        }
        val nickname = getDefaultNickname(username, hostname, port)

        return Host.createSshHost(
            nickname,
            hostname ?: "",
            port,
            username ?: "",
        )
    }

    override fun getSelectionArgs(uri: Uri, selection: MutableMap<String, String>) {
        selection[HostConstants.FIELD_HOST_PROTOCOL] = PROTOCOL
        selection[HostConstants.FIELD_HOST_NICKNAME] = uri.fragment ?: ""
        selection[HostConstants.FIELD_HOST_HOSTNAME] = uri.host ?: ""

        var port = uri.port
        if (port < 0) {
            port = DEFAULT_PORT
        }
        selection[HostConstants.FIELD_HOST_PORT] = port.toString()
        selection[HostConstants.FIELD_HOST_USERNAME] = uri.userInfo ?: ""
    }

    override fun setCompression(compression: Boolean) {
        this.compressionEnabled = compression
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
            Timber.e("")
            return null
        }
        if (useAuthAgent == HostConstants.AUTHAGENT_CONFIRM) {
            val holder = manager?.loadedKeypairs?.get(nickname)
            if (holder != null && holder.pubkey?.confirmation == true && !promptForPubkeyUse(nickname)) {
                return null
            }
        }
        return manager?.getKey(nickname)
    }

    private fun promptForPubkeyUse(nickname: String): Boolean {
        val result = bridge?.requestBooleanPrompt(
            null,
            manager?.res?.getString(R.string.prompt_allow_agent_to_use_key, nickname) ?: "",
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
            keystoreAlias = null,
        )
        manager?.addKey(pubkey, pair)
        return true
    }

    override fun removeAllIdentities(): Boolean {
        manager?.loadedKeypairs?.clear()
        return true
    }

    override fun removeIdentity(publicKey: ByteArray): Boolean = manager?.removeKey(publicKey) ?: false

    override fun isAgentLocked(): Boolean = agentLockPassphrase != null

    override fun requestAgentUnlock(unlockPassphrase: String): Boolean {
        if (agentLockPassphrase == null) {
            return false
        }

        if (agentLockPassphrase == unlockPassphrase) {
            agentLockPassphrase = null
        }

        return agentLockPassphrase == null
    }

    override fun setAgentLock(lockPassphrase: String): Boolean {
        if (agentLockPassphrase != null) {
            return false
        }

        agentLockPassphrase = lockPassphrase
        return true
    }

    override fun usesNetwork(): Boolean = true

    override fun getLocalIpAddress(): String? = connection?.connectionInfo?.localSocketAddress?.address?.hostAddress

    /**
     * Returns the protocol name for this transport instance.
     * SSH returns "ssh", Mosh overrides to return "mosh".
     */
    open fun instanceProtocolName(): String = PROTOCOL

    companion object {
        init {
            // Since this class deals with Ed25519 keys, we need to make sure this is available.
            Ed25519Provider.insertIfNeeded()
        }

        private fun parseIpVersion(value: String, hostname: String): IpVersion {
            // If hostname is a literal IP address, use automatic (the address type is already determined)
            if (HostConstants.isIpAddress(hostname)) {
                return IpVersion.IPV4_AND_IPV6
            }
            return when (value) {
                HostConstants.IPVERSION_IPV4_ONLY -> IpVersion.IPV4_ONLY
                HostConstants.IPVERSION_IPV6_ONLY -> IpVersion.IPV6_ONLY
                else -> IpVersion.IPV4_AND_IPV6
            }
        }

        protected const val PROTOCOL = "ssh"
        protected const val DEFAULT_PORT = 22

        protected const val AUTH_PUBLICKEY = "publickey"
        protected const val AUTH_PASSWORD = "password"
        protected const val AUTH_KEYBOARDINTERACTIVE = "keyboard-interactive"

        private const val EXIT_STATUS_WAIT_MS = 250L

        protected val hostmask = Pattern.compile(
            "^(.+)@((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?\$",
            Pattern.CASE_INSENSITIVE,
        )

        protected const val conditions = (
            ChannelCondition.STDOUT_DATA
                or ChannelCondition.STDERR_DATA
                or ChannelCondition.CLOSED
                or ChannelCondition.EOF
            )

        @JvmStatic
        fun getProtocolName(): String = PROTOCOL

        @JvmStatic
        fun getUri(input: String): Uri? {
            val matcher = hostmask.matcher(input)

            if (!matcher.matches()) {
                return null
            }

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
                    if (port !in 1..65535) {
                        port = DEFAULT_PORT
                    }
                } catch (_: NumberFormatException) {
                    // Keep the default port
                }
            }

            if (port != DEFAULT_PORT) {
                sb.append(':')
                    .append(port)
            }

            sb.append("/#")
                .append(Uri.encode(input))

            return sb.toString().toUri()
        }

        @JvmStatic
        fun getFormatHint(context: Context): String = String.format(
            "%s@%s:%s",
            context.getString(R.string.format_username),
            context.getString(R.string.format_hostname),
            context.getString(R.string.format_port),
        )
    }
}
