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
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Pubkey
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.service.requestBiometricAuth
import org.connectbot.service.requestBooleanPrompt
import org.connectbot.service.requestHostKeyFingerprintPrompt
import org.connectbot.service.requestStringPrompt
import org.connectbot.sshlib.AgentProvider
import org.connectbot.sshlib.AgentSignatureFlags
import org.connectbot.sshlib.AgentSigningContext
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthPublicKey
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.KeyFingerprint
import org.connectbot.sshlib.KeyboardInteractiveCallback
import org.connectbot.sshlib.PortForwarder
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshClientConfig
import org.connectbot.sshlib.SshKeys
import org.connectbot.sshlib.SshSession
import org.connectbot.sshlib.SshSigning
import org.connectbot.sshlib.transport.IpVersion
import org.connectbot.sshlib.transport.TransportFactory
import org.connectbot.util.HostConstants
import org.connectbot.util.PubkeyUtils
import timber.log.Timber
import java.io.IOException
import java.net.NoRouteToHostException
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.util.Locale
import java.util.regex.Pattern

/**
 * @author Kenny Root
 */
class SSH : AbsTransport {

    private var compression = false

    @Volatile
    private var authenticated = false

    @Volatile
    private var connected = false

    @Volatile
    private var sessionOpen = false

    private var client: SshClient? = null
    private var session: SshSession? = null
    private val jumpClients = mutableListOf<SshClient>()

    private var readBuffer: ByteArray? = null
    private var readBufferOffset: Int = 0

    private val portForwards = mutableListOf<PortForward>()

    private var columns: Int = 0
    private var rows: Int = 0

    private var width: Int = 0
    private var height: Int = 0

    private var useAuthAgent = HostConstants.AUTHAGENT_NO
    private var agentLockPassphrase: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    constructor() : super()

    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) : super(host, bridge, manager)

    inner class ConnectBotHostKeyVerifier(private val verifyHost: Host? = host) : HostKeyVerifier {
        override suspend fun verify(key: org.connectbot.sshlib.PublicKey): Boolean {
            val hostId = verifyHost?.id ?: return false
            val knownHostsList = manager?.hostRepository?.getKnownHostsForHostBlocking(hostId) ?: emptyList()

            val serverHostKeyAlgorithm = key.type
            val serverHostKey = key.encoded
            val hostname = verifyHost.hostname
            val port = verifyHost.port

            val algorithmName = getKeyType(serverHostKeyAlgorithm)
            val sha256 = KeyFingerprint.sha256(serverHostKey)
            val md5 = KeyFingerprint.md5(serverHostKey)
            val fingerprint = buildString {
                append("\nMD5:")
                append(md5)
                append("\nSHA256:")
                append(sha256)
            }

            val matchingKey = knownHostsList.find {
                it.hostKeyAlgo == serverHostKeyAlgorithm && it.hostKey.contentEquals(serverHostKey)
            }

            return if (matchingKey != null) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_sucess, algorithmName, fingerprint))
                true
            } else if (knownHostsList.none { it.hostKeyAlgo == serverHostKeyAlgorithm }) {
                // New key
                bridge?.outputLine(manager?.res?.getString(R.string.host_authenticity_warning, hostname))
                bridge?.outputLine(manager?.res?.getString(R.string.host_fingerprint, algorithmName, fingerprint))

                val keySize = getKeySizeFromAlgorithm(serverHostKeyAlgorithm, serverHostKey)
                val randomArt = KeyFingerprint.randomArt(
                    serverHostKey,
                    algorithmName ?: "UNKNOWN",
                    keySize
                )
                val bubblebabble = KeyFingerprint.bubblebabble(serverHostKey)

                val result = bridge?.requestHostKeyFingerprintPrompt(
                    hostname = hostname,
                    keyType = algorithmName ?: "UNKNOWN",
                    keySize = keySize,
                    serverHostKey = serverHostKey,
                    randomArt = randomArt,
                    bubblebabble = bubblebabble,
                    sha256 = sha256,
                    md5 = md5
                )

                if (result == null) {
                    return false
                }
                if (result) {
                    verifyHost.let {
                        manager?.hostRepository?.saveKnownHostBlocking(it, hostname, port, serverHostKeyAlgorithm, serverHostKey)
                    }
                }
                result
            } else {
                // Key has changed
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

                val result = bridge?.requestBooleanPrompt(
                    null,
                    manager?.res?.getString(R.string.prompt_continue_connecting) ?: ""
                )
                if (result != null && result) {
                    verifyHost.let {
                        manager?.hostRepository?.saveKnownHostBlocking(it, hostname, port, serverHostKeyAlgorithm, serverHostKey)
                    }
                    true
                } else {
                    false
                }
            }
        }

        override suspend fun addKeys(keys: List<org.connectbot.sshlib.PublicKey>) {
            val currentHost = verifyHost ?: return
            for (key in keys) {
                manager?.hostRepository?.saveKnownHostBlocking(
                    currentHost,
                    currentHost.hostname,
                    currentHost.port,
                    key.type,
                    key.encoded
                )
            }
        }

        override suspend fun removeKeys(keys: List<org.connectbot.sshlib.PublicKey>) {
            val hostId = verifyHost?.id ?: return
            for (key in keys) {
                manager?.hostRepository?.removeKnownHostBlocking(hostId, key.type, key.encoded)
            }
        }
    }

    private suspend fun authenticate() {
        if (host?.username.isNullOrEmpty()) {
            val username = bridge?.requestStringPrompt(
                null,
                manager?.res?.getString(R.string.prompt_username),
                false
            )
            if (username.isNullOrEmpty()) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_fail))
                return
            }
            host = host?.copy(username = username)
        }

        val currentHost = host ?: return
        var savedPasswordTried = false

        val result = client?.authenticate(
            currentHost.username,
            object : AuthHandler {
                override suspend fun onAuthMethodsAvailable(methods: Set<String>) {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth))
                }

                override suspend fun onPublicKeysNeeded(): List<AuthPublicKey> {
                    val pubkeyId = currentHost.pubkeyId
                    if (pubkeyId == HostConstants.PUBKEYID_NEVER) return emptyList()

                    val keys = mutableListOf<AuthPublicKey>()

                    if (pubkeyId == HostConstants.PUBKEYID_ANY) {
                        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_any))
                        manager?.loadedKeypairs?.entries?.forEach { entry ->
                            if (entry.value.pubkey?.confirmation == true && !promptForPubkeyUse(entry.key)) {
                                return@forEach
                            }
                            val keyPair = entry.value.pair ?: return@forEach
                            try {
                                keys.add(SshSigning.encodePublicKey(keyPair))
                            } catch (e: Exception) {
                                Timber.d(e, "Could not encode public key: ${entry.key}")
                            }
                        }
                    } else {
                        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_specific))
                        val pubkey = manager?.pubkeyRepository?.getByIdBlocking(pubkeyId)
                        if (pubkey == null) {
                            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_invalid))
                        } else {
                            if (pubkey.confirmation && manager?.isKeyLoaded(pubkey.nickname) == true) {
                                if (!promptForPubkeyUse(pubkey.nickname)) {
                                    return keys
                                }
                            }
                            val pair = getOrUnlockKey(pubkey)
                            if (pair != null) {
                                try {
                                    keys.add(SshSigning.encodePublicKey(pair))
                                } catch (e: Exception) {
                                    Timber.d(e, "Could not encode public key: ${pubkey.nickname}")
                                }
                            }
                        }
                    }

                    return keys
                }

                override suspend fun onSignatureRequest(key: AuthPublicKey, dataToSign: ByteArray): ByteArray? {
                    val keyPair = findKeyPairForPublicKey(key) ?: return null
                    return try {
                        SshSigning.signWithKeyPair(key.algorithmName, keyPair, dataToSign)
                    } catch (e: Exception) {
                        val cause = e.cause ?: e
                        val isKeyInvalidated = cause is KeyPermanentlyInvalidatedException ||
                            cause is UserNotAuthenticatedException ||
                            e is KeyPermanentlyInvalidatedException ||
                            e is UserNotAuthenticatedException
                        if (isKeyInvalidated) {
                            val nickname = findKeyNicknameForPublicKey(key)
                            val message = manager?.res?.getString(R.string.terminal_auth_biometric_invalidated, nickname)
                                ?: "Biometric key has been invalidated."
                            Timber.e(e, message)
                            bridge?.outputLine(message)
                        } else {
                            Timber.e(e, "Signature request failed")
                        }
                        null
                    }
                }

                override suspend fun onKeyboardInteractivePrompt(
                    name: String,
                    instruction: String,
                    prompts: List<KeyboardInteractiveCallback.Prompt>
                ): List<String>? {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_ki))
                    return prompts.map { prompt ->
                        val isPassword = !prompt.echo

                        if (isPassword && !savedPasswordTried) {
                            val savedPassword = manager?.securePasswordStorage?.getPassword(currentHost.id)
                            if (savedPassword != null) {
                                savedPasswordTried = true
                                bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_saved_password))
                                return@map savedPassword
                            }
                        }

                        bridge?.requestStringPrompt(instruction, prompt.text, isPassword) ?: ""
                    }
                }

                override suspend fun onPasswordNeeded(): String? {
                    val savedPassword = manager?.securePasswordStorage?.getPassword(currentHost.id)
                    if (savedPassword != null && !savedPasswordTried) {
                        savedPasswordTried = true
                        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_saved_password))
                        return savedPassword
                    }

                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pass))
                    return bridge?.requestStringPrompt(
                        null,
                        manager?.res?.getString(R.string.prompt_password),
                        true
                    )
                }
            }
        )

        if (result == true) {
            finishConnection()
        }
    }

    private fun findKeyPairForPublicKey(key: AuthPublicKey): KeyPair? {
        manager?.loadedKeypairs?.entries?.forEach { entry ->
            val pair = entry.value.pair ?: return@forEach
            try {
                val encoded = SshSigning.encodePublicKey(pair)
                if (encoded.publicKeyBlob.contentEquals(key.publicKeyBlob)) {
                    return pair
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun findKeyNicknameForPublicKey(key: AuthPublicKey): String? {
        manager?.loadedKeypairs?.entries?.forEach { entry ->
            val pair = entry.value.pair ?: return@forEach
            try {
                val encoded = SshSigning.encodePublicKey(pair)
                if (encoded.publicKeyBlob.contentEquals(key.publicKeyBlob)) {
                    return entry.key
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun getOrUnlockKey(pubkey: Pubkey): KeyPair? {
        if (manager?.isKeyLoaded(pubkey.nickname) == true) {
            Timber.d("Found unlocked key '%s' already in-memory", pubkey.nickname)
            return manager?.getKey(pubkey.nickname)
        }

        if (pubkey.storageType == KeyStorageType.ANDROID_KEYSTORE) {
            val keystoreAlias = pubkey.keystoreAlias
            if (keystoreAlias == null) {
                val message = String.format("Keystore alias missing for key '%s'. Authentication failed.", pubkey.nickname)
                Timber.e(message)
                bridge?.outputLine(message)
                return null
            }

            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_biometric, pubkey.nickname))

            val biometricSuccess = bridge?.requestBiometricAuth(pubkey.nickname, keystoreAlias) ?: false
            if (!biometricSuccess) {
                val message = String.format("Biometric authentication failed for key '%s'.", pubkey.nickname)
                Timber.e(message)
                bridge?.outputLine(message)
                return null
            }

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
                        Timber.d("Unlocked biometric key '%s'", pubkey.nickname)
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

        var password: String? = null
        if (pubkey.encrypted) {
            password = bridge?.requestStringPrompt(
                null,
                manager?.res?.getString(R.string.prompt_pubkey_password, pubkey.nickname),
                true
            )

            if (password == null) {
                return null
            }
        }

        val pair = if (pubkey.type == "IMPORTED") {
            val privateKey = pubkey.privateKey ?: return null
            SshKeys.decodePemPrivateKey(String(privateKey, StandardCharsets.UTF_8), password)
        } else {
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

            KeyPair(pubKey, privKey).also {
                Timber.d("Unlocked key %s", PubkeyUtils.formatKey(pubKey))
            }
        }

        Timber.d("Unlocked key '%s'", pubkey.nickname)

        manager?.addKey(pubkey, pair)

        return pair
    }

    private suspend fun finishConnection() {
        authenticated = true

        for (portForward in portForwards) {
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
            if (useAuthAgent != HostConstants.AUTHAGENT_NO) {
                client?.enableAgentForwarding(ConnectBotAgentProvider())
            }

            session = client?.openSession()
            session?.requestPty(getEmulation() ?: "xterm", columns, rows, width, height)
            session?.requestShell()

            sessionOpen = true

            bridge?.onConnected()
        } catch (e: Exception) {
            Timber.e(e, "Problem while trying to create PTY in finishConnection()")
        }
    }

    private suspend fun connectToJumpHost(jumpHostId: Long): TransportFactory? {
        val jumpHost = manager?.hostRepository?.findHostByIdBlocking(jumpHostId) ?: return null
        bridge?.outputLine(manager?.res?.getString(R.string.terminal_connecting_via_jump, jumpHost.nickname))

        var jumpTransportFactory: TransportFactory? = null
        val nestedJumpHostId = jumpHost.jumpHostId
        if (nestedJumpHostId != null && nestedJumpHostId > 0) {
            jumpTransportFactory = connectToJumpHost(nestedJumpHostId) ?: return null
        }

        val config = SshClientConfig {
            if (jumpTransportFactory != null) {
                this.transportFactory = jumpTransportFactory
            } else {
                this.host = jumpHost.hostname
                this.port = jumpHost.port
            }
            this.hostKeyVerifier = ConnectBotHostKeyVerifier(jumpHost)
            this.enableCompression = jumpHost.compression
            this.ipVersion = parseIpVersion(jumpHost.ipVersion, jumpHost.hostname)
        }

        val jumpClient = SshClient(config)
        if (!jumpClient.connect()) {
            Timber.e("Failed to connect to jump host: ${jumpHost.nickname}")
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_failed, jumpHost.nickname, "connection failed"))
            return null
        }
        jumpClients.add(jumpClient)

        bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_connected, jumpHost.nickname))

        if (!authenticateJumpHost(jumpClient, jumpHost)) {
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_auth_failed, jumpHost.nickname))
            jumpClient.disconnect()
            jumpClients.remove(jumpClient)
            return null
        }

        bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_authenticated, jumpHost.nickname))

        val currentHost = host ?: return null
        return jumpClient.openDirectTcpipTransport(currentHost.hostname, currentHost.port)
    }

    private suspend fun authenticateJumpHost(jumpClient: SshClient, jumpHost: Host): Boolean {
        try {
            var savedPasswordTried = false

            val result = jumpClient.authenticate(
                jumpHost.username,
                object : AuthHandler {
                    override suspend fun onPublicKeysNeeded(): List<AuthPublicKey> {
                        val pubkeyId = jumpHost.pubkeyId
                        if (pubkeyId == HostConstants.PUBKEYID_NEVER) return emptyList()

                        val keys = mutableListOf<AuthPublicKey>()
                        if (pubkeyId == HostConstants.PUBKEYID_ANY) {
                            manager?.loadedKeypairs?.entries?.forEach { entry ->
                                val keyPair = entry.value.pair ?: return@forEach
                                try {
                                    keys.add(SshSigning.encodePublicKey(keyPair))
                                } catch (_: Exception) {
                                    Timber.d("Jump host: could not encode key ${entry.key}")
                                }
                            }
                        } else {
                            val pubkey = manager?.pubkeyRepository?.getByIdBlocking(pubkeyId)
                            if (pubkey != null) {
                                val pair = getOrUnlockKey(pubkey)
                                if (pair != null) {
                                    try {
                                        keys.add(SshSigning.encodePublicKey(pair))
                                    } catch (_: Exception) {
                                        Timber.d("Jump host: could not encode specific key")
                                    }
                                }
                            }
                        }
                        return keys
                    }

                    override suspend fun onSignatureRequest(key: AuthPublicKey, dataToSign: ByteArray): ByteArray? {
                        val keyPair = findKeyPairForPublicKey(key) ?: return null
                        return try {
                            SshSigning.signWithKeyPair(key.algorithmName, keyPair, dataToSign)
                        } catch (e: Exception) {
                            Timber.d(e, "Jump host signature request failed")
                            null
                        }
                    }

                    override suspend fun onKeyboardInteractivePrompt(
                        name: String,
                        instruction: String,
                        prompts: List<KeyboardInteractiveCallback.Prompt>
                    ): List<String>? = prompts.map { prompt ->
                        val isPassword = !prompt.echo
                        val promptPrefix = manager?.res?.getString(R.string.terminal_jump_prompt, jumpHost.nickname) ?: ""
                        bridge?.requestStringPrompt(
                            instruction,
                            "$promptPrefix ${prompt.text}",
                            isPassword
                        ) ?: ""
                    }

                    override suspend fun onPasswordNeeded(): String? {
                        val savedPassword = manager?.securePasswordStorage?.getPassword(jumpHost.id)
                        if (savedPassword != null && !savedPasswordTried) {
                            savedPasswordTried = true
                            return savedPassword
                        }

                        val passwordPrompt = manager?.res?.getString(R.string.terminal_jump_password, jumpHost.nickname)
                        return bridge?.requestStringPrompt(null, passwordPrompt, true)
                    }
                }
            )

            return result
        } catch (e: Exception) {
            Timber.e(e, "Error during jump host authentication")
            return false
        }
    }

    override suspend fun connect() {
        val currentHost = host ?: return

        var transportFactory: TransportFactory? = null
        val jumpHostId = currentHost.jumpHostId
        if (jumpHostId != null && jumpHostId > 0) {
            transportFactory = connectToJumpHost(jumpHostId) ?: run {
                onDisconnect()
                return
            }
        }

        val config = SshClientConfig {
            if (transportFactory != null) {
                this.transportFactory = transportFactory
            } else {
                this.host = currentHost.hostname
                this.port = currentHost.port
            }
            this.hostKeyVerifier = ConnectBotHostKeyVerifier(currentHost)
            this.enableCompression = compression
            this.ipVersion = parseIpVersion(currentHost.ipVersion, currentHost.hostname)
        }

        client = SshClient(config)

        try {
            if (client?.connect() != true) {
                close()
                onDisconnect()
                return
            }
        } catch (e: Exception) {
            Timber.e(e, "Problem in SSH connection thread during connect")

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
        connected = true

        scope.launch {
            client?.disconnectedFlow?.collect { reason ->
                if (bridge?.isInGracePeriod() == true) {
                    Timber.d("SSH connection lost during grace period (expected due to network loss)")
                    return@collect
                }
                Timber.d("SSH connection lost outside grace period - disconnecting")
                onDisconnect()
            }
        }

        try {
            var tries = 0
            while (connected && client?.isAuthenticated != true && tries++ < AUTH_TRIES) {
                authenticate()
                delay(1000)
            }
        } catch (e: Exception) {
            Timber.e(e, "Problem in SSH connection thread during authentication")
        }
    }

    override suspend fun close() {
        if (bridge?.isInGracePeriod() == true) {
            Timber.d("Deferring SSH close - bridge in network grace period")
            return
        }

        connected = false
        sessionOpen = false

        session?.close()
        session = null

        client?.disconnect()
        client = null

        jumpClients.asReversed().forEach {
            try {
                it.disconnect()
            } catch (ignored: Exception) {
            }
        }
        jumpClients.clear()
    }

    private fun onDisconnect() {
        bridge?.dispatchDisconnect(false)
    }

    @Throws(IOException::class)
    override suspend fun flush() {
        // New library handles flushing internally
    }

    @Throws(IOException::class)
    override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (readBuffer != null) {
            val available = readBuffer!!.size - readBufferOffset
            val toCopy = minOf(available, length)
            readBuffer!!.copyInto(buffer, offset, readBufferOffset, readBufferOffset + toCopy)
            readBufferOffset += toCopy
            if (readBufferOffset >= readBuffer!!.size) {
                readBuffer = null
                readBufferOffset = 0
            }
            return toCopy
        }

        val data = session?.read() ?: throw IOException("Remote end closed connection")
        if (data.size <= length) {
            data.copyInto(buffer, offset)
            return data.size
        }
        data.copyInto(buffer, offset, 0, length)
        readBuffer = data
        readBufferOffset = length
        return length
    }

    @Throws(IOException::class)
    override suspend fun write(buffer: ByteArray) {
        session?.write(buffer)
    }

    @Throws(IOException::class)
    override suspend fun write(c: Int) {
        session?.write(byteArrayOf(c.toByte()))
    }

    override fun getOptions(): Map<String, String> = mapOf("compression" to compression.toString())

    override fun setOptions(options: Map<String, String>) {
        if (options.containsKey("compression")) {
            compression = options["compression"]?.toBoolean() ?: false
        }
    }

    override fun isSessionOpen(): Boolean = sessionOpen

    override fun isConnected(): Boolean = connected

    override fun canForwardPorts(): Boolean = true

    override fun getPortForwards(): List<PortForward> = portForwards

    override fun addPortForward(portForward: PortForward): Boolean = portForwards.add(portForward)

    override suspend fun removePortForward(portForward: PortForward): Boolean {
        disablePortForward(portForward)
        return portForwards.remove(portForward)
    }

    override suspend fun enablePortForward(portForward: PortForward): Boolean {
        if (!portForwards.contains(portForward)) {
            Timber.e("Attempt to enable port forward not in list")
            return false
        }

        if (!authenticated) {
            return false
        }

        val forwarder: PortForwarder? = try {
            when (portForward.type) {
                HostConstants.PORTFORWARD_LOCAL ->
                    client?.localPortForward(portForward.sourcePort, portForward.destAddr ?: "", portForward.destPort)

                HostConstants.PORTFORWARD_REMOTE ->
                    client?.remotePortForward("", portForward.sourcePort, portForward.destAddr ?: "", portForward.destPort)

                HostConstants.PORTFORWARD_DYNAMIC5 ->
                    client?.dynamicPortForward(portForward.sourcePort)

                else -> {
                    Timber.e("attempt to forward unknown type %s", portForward.type)
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Could not create port forward")
            return false
        }

        if (forwarder == null) {
            return false
        }

        portForward.setIdentifier(forwarder)
        portForward.setEnabled(true)
        return true
    }

    override suspend fun disablePortForward(portForward: PortForward): Boolean {
        if (!portForwards.contains(portForward)) {
            Timber.e("Attempt to disable port forward not in list")
            return false
        }

        if (!authenticated) {
            return false
        }

        val forwarder = portForward.getIdentifier() as? PortForwarder

        if (!portForward.isEnabled() || forwarder == null) {
            Timber.d("Could not disable %s; it appears to be not enabled or have no handler", portForward.nickname)
            return false
        }

        portForward.setEnabled(false)
        forwarder.stop()
        return true
    }

    override suspend fun setDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        this.columns = columns
        this.rows = rows
        this.width = width
        this.height = height

        if (sessionOpen) {
            try {
                session?.resizeTerminal(columns, rows, width, height)
            } catch (e: Exception) {
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
            username ?: ""
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
        this.compression = compression
    }

    override fun setUseAuthAgent(useAuthAgent: String) {
        this.useAuthAgent = useAuthAgent
    }

    private fun promptForPubkeyUse(nickname: String): Boolean {
        val result = bridge?.requestBooleanPrompt(
            null,
            manager?.res?.getString(R.string.prompt_allow_agent_to_use_key, nickname) ?: ""
        )
        return result ?: false
    }

    inner class ConnectBotAgentProvider : AgentProvider {
        override suspend fun getIdentities(): List<org.connectbot.sshlib.AgentIdentity> {
            return manager?.loadedKeypairs?.entries?.mapNotNull { entry ->
                val pair = entry.value.pair ?: return@mapNotNull null
                try {
                    val encoded = SshSigning.encodePublicKey(pair)
                    org.connectbot.sshlib.AgentIdentity(encoded.publicKeyBlob, entry.key)
                } catch (e: Exception) {
                    Timber.d(e, "Could not encode key for agent: ${entry.key}")
                    null
                }
            } ?: emptyList()
        }

        override suspend fun signData(context: AgentSigningContext): ByteArray? {
            if (agentLockPassphrase != null) return null

            val nickname = manager?.getKeyNickname(context.publicKeyBlob) ?: return null

            if (useAuthAgent == HostConstants.AUTHAGENT_NO) return null
            if (useAuthAgent == HostConstants.AUTHAGENT_CONFIRM) {
                val holder = manager?.loadedKeypairs?.get(nickname)
                if (holder != null && holder.pubkey?.confirmation == true && !promptForPubkeyUse(nickname)) {
                    return null
                }
            }

            val keyPair = manager?.getKey(nickname) ?: return null
            val algo = determineAlgorithm(keyPair, context.flags)
            return try {
                SshSigning.signWithKeyPair(algo, keyPair, context.dataToSign)
            } catch (e: Exception) {
                Timber.e(e, "Agent signing failed for key: $nickname")
                null
            }
        }
    }

    private fun determineAlgorithm(keyPair: KeyPair, flags: Int): String = when {
        keyPair.private is java.security.interfaces.RSAPrivateKey -> {
            when {
                flags and AgentSignatureFlags.RSA_SHA2_512 != 0 -> "rsa-sha2-512"
                flags and AgentSignatureFlags.RSA_SHA2_256 != 0 -> "rsa-sha2-256"
                else -> "ssh-rsa"
            }
        }

        keyPair.private is java.security.interfaces.DSAPrivateKey -> "ssh-dss"

        keyPair.private is java.security.interfaces.ECPrivateKey -> {
            val ecKey = keyPair.private as java.security.interfaces.ECPrivateKey
            when (ecKey.params.curve.field.fieldSize) {
                256 -> "ecdsa-sha2-nistp256"
                384 -> "ecdsa-sha2-nistp384"
                521 -> "ecdsa-sha2-nistp521"
                else -> "ecdsa-sha2-nistp256"
            }
        }

        else -> {
            // Ed25519 or other
            SshSigning.encodePublicKey(keyPair).algorithmName
        }
    }

    override fun usesNetwork(): Boolean = true

    companion object {
        private fun getKeyType(openSshKeyType: String): String? = when {
            openSshKeyType == "ssh-rsa" || openSshKeyType.startsWith("rsa-sha2-") -> "RSA"
            openSshKeyType == "ssh-dss" -> "DSA"
            openSshKeyType == "ssh-ed25519" -> "Ed25519"
            openSshKeyType.startsWith("ecdsa-sha2-") -> "EC"
            else -> null
        }

        private fun getKeySizeFromAlgorithm(algorithm: String, keyBlob: ByteArray): Int = when {
            algorithm == "ssh-rsa" || algorithm.startsWith("rsa-sha2-") -> {
                // RSA key size is in the key blob; approximate from blob size
                (keyBlob.size - 20) * 8 / 3
            }

            algorithm == "ssh-dss" -> 1024

            algorithm == "ssh-ed25519" -> 256

            algorithm == "ecdsa-sha2-nistp256" -> 256

            algorithm == "ecdsa-sha2-nistp384" -> 384

            algorithm == "ecdsa-sha2-nistp521" -> 521

            else -> 0
        }

        private fun parseIpVersion(value: String, hostname: String): IpVersion {
            if (HostConstants.isIpAddress(hostname)) {
                return IpVersion.AUTO
            }
            return when (value) {
                HostConstants.IPVERSION_IPV4_ONLY -> IpVersion.IPV4_ONLY
                HostConstants.IPVERSION_IPV6_ONLY -> IpVersion.IPV6_ONLY
                else -> IpVersion.AUTO
            }
        }

        private const val PROTOCOL = "ssh"
        private const val DEFAULT_PORT = 22
        private const val AUTH_TRIES = 20

        private val hostmask = Pattern.compile(
            "^(.+)@((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?\$",
            Pattern.CASE_INSENSITIVE
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
            context.getString(R.string.format_port)
        )
    }
}
