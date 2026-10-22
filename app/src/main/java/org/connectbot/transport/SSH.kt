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
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.DisconnectReason
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.service.requestBiometricAuth
import org.connectbot.service.requestBooleanPrompt
import org.connectbot.service.requestHostKeyFingerprintPrompt
import org.connectbot.service.requestStringPrompt
import org.connectbot.sshlib.AgentProvider
import org.connectbot.sshlib.AgentResult
import org.connectbot.sshlib.AgentSignatureFlags
import org.connectbot.sshlib.AgentSigningContext
import org.connectbot.sshlib.AlgorithmCategory
import org.connectbot.sshlib.AlgorithmPolicyWarning
import org.connectbot.sshlib.AuthHandler
import org.connectbot.sshlib.AuthPublicKey
import org.connectbot.sshlib.AuthResult
import org.connectbot.sshlib.ConnectResult
import org.connectbot.sshlib.ConnectionInfo
import org.connectbot.sshlib.HostKeyVerifier
import org.connectbot.sshlib.KeyFingerprint
import org.connectbot.sshlib.KeyboardInteractiveCallback
import org.connectbot.sshlib.PortForwarder
import org.connectbot.sshlib.SessionExit
import org.connectbot.sshlib.SshClient
import org.connectbot.sshlib.SshClientConfig
import org.connectbot.sshlib.SshCryptoPolicy
import org.connectbot.sshlib.SshKeys
import org.connectbot.sshlib.SshSession
import org.connectbot.sshlib.SshSigning
import org.connectbot.sshlib.transport.IpVersion
import org.connectbot.sshlib.transport.TransportFactory
import org.connectbot.util.CryptoProviderState
import org.connectbot.util.HostConstants
import org.connectbot.util.PubkeyUtils
import org.connectbot.util.SshKeyType
import org.connectbot.util.UrlUtils
import org.connectbot.util.titleRes
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.net.NoRouteToHostException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * @author Kenny Root
 */
open class SSH : AbsTransport {

    @JvmField
    protected var compressionEnabled = false

    @Volatile
    protected var authenticated = false

    @Volatile
    protected var connected = false

    @Volatile
    protected var sessionOpen = false

    @Volatile
    private var localIpAddress: String? = null

    @Volatile
    private var lastConnectionInfo: ConnectionInfo? = null
    private val policyDetails = ConcurrentHashMap<UUID, SshPolicyDetails>()
    private val policyClients = ConcurrentHashMap<SshClient, Host>()

    override fun getSshPolicyDetails(): List<SshPolicyDetails> = policyDetails.values.toList()

    private fun outputPolicyWarnings(currentHost: Host, warnings: List<AlgorithmPolicyWarning>) {
        warnings.forEach { warning ->
            val res = manager?.res ?: return@forEach
            bridge?.outputLine(res.getString(R.string.crypto_connection_warning, currentHost.nickname.ifBlank { currentHost.hostname }, res.getString(warning.category.titleRes()), warning.algorithm, res.getString(warning.reason.titleRes())))
        }
    }

    private fun capturePolicyWarnings(sshClient: SshClient, currentHost: Host) {
        recordPolicyWarnings(currentHost, sshClient.policyWarnings)
    }

    private fun recordPolicyWarnings(currentHost: Host, warnings: List<AlgorithmPolicyWarning>) {
        var additional = emptyList<AlgorithmPolicyWarning>()
        policyDetails.computeIfPresent(currentHost.id) { _, existing ->
            additional = warnings.distinct().filterNot { it in existing.warnings }
            existing.copy(warnings = existing.warnings + additional)
        }
        outputPolicyWarnings(currentHost, additional)
    }

    internal suspend fun resolveCryptoPolicy(currentHost: Host, dispatchers: CoroutineDispatchers): SshCryptoPolicy? = try {
        val policy = withContext(dispatchers.default) { currentHost.crypto.resolve() }
        val emptyCategory = policy.algorithms.entries.firstOrNull { it.value.isEmpty() }?.key
        val error = emptyCategory?.let { manager?.res?.getString(R.string.crypto_no_algorithms, manager?.res?.getString(it.titleRes())) ?: "No usable algorithms: $it" }
        policyDetails[currentHost.id] = SshPolicyDetails(currentHost.nickname.ifBlank { currentHost.hostname }, currentHost.crypto.preset, policy.minimumRsaBits, policy.minimumDhBits, policy.warnings, error)
        outputPolicyWarnings(currentHost, policy.warnings)
        if (error != null) {
            bridge?.outputLine(error)
            null
        } else {
            policy
        }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        val error = manager?.res?.getString(R.string.crypto_policy_failed, currentHost.nickname.ifBlank { currentHost.hostname }, e.message.orEmpty()) ?: e.message.orEmpty()
        policyDetails[currentHost.id] = SshPolicyDetails(currentHost.hostname, currentHost.crypto.preset, error = error)
        bridge?.outputLine(error)
        null
    }

    private fun policyHostKeyOrder(currentHost: Host, policy: SshCryptoPolicy): String {
        val names = policy.algorithms.getValue(AlgorithmCategory.HOST_KEY)
        val expression = currentHost.crypto.takeIf { it.preset == "CUSTOM" }?.hostKey
        if (expression.isNullOrBlank()) return preferredHostKeyAlgorithms(currentHost, names.joinToString(","))
        val rules = expression.split(',').map { it.trim() }
        if (rules.first().first() !in "^+-") return names.joinToString(",")
        val explicit = rules.map { it.drop(1) }.toSet()
        val inherited = names.filter { it !in explicit }
        val reordered = preferredHostKeyAlgorithms(currentHost, inherited.joinToString(",")).split(',').filter { it.isNotEmpty() }
        val prepend = rules.filter { it.startsWith('^') }.map { it.drop(1) }.filter { it in names }
        val append = rules.filter { it.startsWith('+') }.map { it.drop(1) }.filter { it in names }
        return (prepend + reordered + append).joinToString(",")
    }

    protected var client: SshClient? = null
    private var session: SshSession? = null
    private val jumpClients = mutableListOf<SshClient>()

    private val savedPasswordsTried = mutableSetOf<UUID>()

    // Advertised methods alone do not mean the server will accept credentials for them.
    private val credentialMethods = mutableSetOf<String>()
    private val keyUseDecisions = mutableMapOf<Pair<SshClient, TerminalManager.KeyHolder>, Boolean>()
    private var loginCancelled = false
    private var jumpFailureReason = DisconnectReason.IO_ERROR

    @Volatile
    private var outputDrained: CompletableDeferred<Unit>? = null

    @Volatile
    private var connectionLossCause: Throwable? = null

    @Volatile
    private var locallyClosed = false

    private class LoginCancelled : Exception()

    private suspend fun requestLoginPrompt(instructions: String?, prompt: String?, password: Boolean): String = try {
        bridge?.promptManager?.requestStringPrompt(instructions, prompt, password) ?: run {
            loginCancelled = true
            throw LoginCancelled()
        }
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        loginCancelled = true
        throw LoginCancelled()
    }

    private var readBuffer: ByteArray? = null
    private var readBufferOffset: Int = 0

    private val portForwards = mutableListOf<PortForward>()

    protected var columns: Int = 0
    protected var rows: Int = 0

    protected var width: Int = 0
    protected var height: Int = 0

    private var useAuthAgent = HostConstants.AUTHAGENT_NO

    @Volatile
    private var agentLockPassphrase: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    constructor() : super()

    constructor(host: Host?, bridge: TerminalBridge?, manager: TerminalManager?) : super(host, bridge, manager)

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

    internal fun preferredHostKeyAlgorithms(currentHost: Host, supported: String): String {
        val known = manager?.hostRepository?.getHostKeyAlgorithmsForHostBlocking(currentHost.id).orEmpty().toSet()
        // Keep the library's security policy and relative preference within each group.
        // Other algorithms must remain enabled so authenticated host key updates can learn them.
        return supported.split(',').sortedBy { it !in known }.joinToString(",")
    }

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
                append("\n")
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
                    keySize,
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
                    md5 = md5,
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

                val result = bridge?.requestBooleanPrompt(
                    null,
                    manager?.res?.getString(R.string.prompt_continue_connecting) ?: "",
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
                    key.encoded,
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

    internal open suspend fun authenticate(): AuthResult? {
        credentialMethods.clear()
        if (host?.username.isNullOrEmpty()) {
            val username = requestLoginPrompt(
                null,
                manager?.res?.getString(R.string.prompt_username),
                false,
            )
            if (username.isEmpty()) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_fail))
                loginCancelled = true
                throw LoginCancelled()
            }
            host = host?.copy(username = username)
        }

        val currentHost = host ?: return null
        val authBannerSourceName = currentHost.authBannerSourceName()
        var keyboardInteractiveStarted = false

        val loadedKeys = manager?.loadedKeypairs?.entries?.sortedBy { it.key }?.map { it.key to it.value }.orEmpty()
        val result = try {
            client?.authenticate(
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
                            loadedKeys.forEach { entry ->
                                val keyPair = entry.second.pair ?: return@forEach
                                try {
                                    if (preparePublicKeyAuthentication(client, keyPair)) {
                                        keys.add(SshSigning.encodePublicKey(keyPair))
                                    }
                                } catch (e: Exception) {
                                    Timber.d(e, "Could not encode public key: ${entry.first}")
                                }
                            }
                        } else {
                            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_specific))
                            val pubkey = manager?.pubkeyRepository?.getByIdBlocking(pubkeyId)
                            if (pubkey == null) {
                                bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_invalid))
                            } else {
                                val pair = getAuthenticationKey(pubkey)
                                if (pair != null) {
                                    try {
                                        if (preparePublicKeyAuthentication(client, pair)) {
                                            keys.add(SshSigning.encodePublicKey(pair))
                                        }
                                    } catch (e: Exception) {
                                        Timber.d(e, "Could not encode public key: ${pubkey.nickname}")
                                    }
                                }
                            }
                        }

                        return keys
                    }

                    override suspend fun onSignatureRequest(key: AuthPublicKey, dataToSign: ByteArray): ByteArray? {
                        val keyPair = confirmedAuthenticationKey(client, key) ?: return null
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
                        prompts: List<KeyboardInteractiveCallback.Prompt>,
                    ): List<String>? {
                        if (!keyboardInteractiveStarted) {
                            keyboardInteractiveStarted = true
                            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_ki))
                        }
                        return prompts.map { prompt ->
                            credentialMethods.add("keyboard-interactive")
                            val isPassword = !prompt.echo

                            if (isPassword && currentHost.id !in savedPasswordsTried) {
                                val savedPassword = manager?.securePasswordStorage?.getPassword(currentHost.id)
                                if (savedPassword != null) {
                                    savedPasswordsTried.add(currentHost.id)
                                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_saved_password))
                                    return@map savedPassword
                                }
                            }

                            requestLoginPrompt(instruction, prompt.text, isPassword)
                        }
                    }

                    override suspend fun onPasswordNeeded(): String? {
                        credentialMethods.add("password")
                        val savedPassword = manager?.securePasswordStorage?.getPassword(currentHost.id)
                        if (savedPassword != null && currentHost.id !in savedPasswordsTried) {
                            savedPasswordsTried.add(currentHost.id)
                            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_saved_password))
                            return savedPassword
                        }

                        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pass))
                        return requestLoginPrompt(
                            null,
                            manager?.res?.getString(R.string.prompt_password),
                            true,
                        )
                    }

                    override suspend fun onBanner(message: String) {
                        handleAuthBanner(authBannerSourceName, message, null)
                    }
                },
            )
        } finally {
            bridge?.dismissAuthBannersFrom(authBannerSourceName)
        }

        currentCoroutineContext().ensureActive()
        if (loginCancelled) throw LoginCancelled()
        if (result is AuthResult.Success) {
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_success))
        }
        outputAuthResult(result)
        return result
    }

    private fun outputAuthResult(result: AuthResult?) {
        when (result) {
            is AuthResult.Error -> bridge?.outputLine(result.message)

            is AuthResult.Failure -> {
                if (result.allowedMethods.isEmpty()) {
                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_fail))
                } else {
                    Timber.d("Authentication failed; allowed methods: ${result.allowedMethods}")
                }
            }

            else -> Unit
        }
    }

    private suspend fun confirmedAuthenticationKey(authClient: SshClient?, key: AuthPublicKey): KeyPair? {
        authClient ?: return null
        val entry = manager?.loadedKeypairs?.entries?.firstOrNull { entry ->
            val pair = entry.value.pair ?: return@firstOrNull false
            runCatching { SshSigning.encodePublicKey(pair).publicKeyBlob.contentEquals(key.publicKeyBlob) }
                .getOrDefault(false)
        } ?: return null
        val holder = entry.value
        if (holder.pubkey?.confirmation == true) {
            val approved = keyUseDecisions.getOrPut(authClient to holder) {
                bridge?.promptManager?.requestBooleanPrompt(
                    null,
                    manager?.res?.getString(R.string.prompt_allow_agent_to_use_key, entry.key).orEmpty(),
                ) ?: false
            }
            if (!approved) return null
        }
        // A key can be unloaded while the user is deciding whether to use it.
        if (manager?.loadedKeypairs?.get(entry.key) !== holder) return null
        return holder.pair
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

    private fun getAuthenticationKey(pubkey: Pubkey): KeyPair? = try {
        getOrUnlockKey(pubkey)
    } catch (e: LoginCancelled) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Unable to unlock public key '%s'", pubkey.nickname)
        bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_pubkey_fail, pubkey.nickname))
        null
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
                true,
            )

            if (password == null) {
                throw LoginCancelled()
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

    protected open suspend fun finishConnection() {
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
            if (useAuthAgent != HostConstants.AUTHAGENT_NO) {
                client?.enableAgentForwarding(ConnectBotAgentProvider())
            }

            val openedSession = client?.openSession()
            if (openedSession == null) {
                bridge?.outputLine(manager?.res?.getString(R.string.terminal_session_failed))
                onDisconnect(DisconnectReason.IO_ERROR)
                return
            }
            session = openedSession
            outputDrained = CompletableDeferred()
            val failure = when {
                !openedSession.requestPty(getEmulation() ?: "xterm", columns, rows, width, height) -> R.string.terminal_pty_rejected
                !openedSession.requestShell() -> R.string.terminal_shell_rejected
                else -> null
            }
            if (failure != null) {
                bridge?.outputLine(manager?.res?.getString(failure))
                openedSession.close()
                session = null
                outputDrained?.complete(Unit)
                onDisconnect(DisconnectReason.IO_ERROR)
                return
            }

            sessionOpen = true

            bridge?.onConnected()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            outputDrained?.complete(Unit)
            Timber.e(e, "Problem while trying to create PTY in finishConnection()")
            onDisconnect()
        }
    }

    private suspend fun connectToJumpHost(jumpHost: Host, destination: Host, jumpTransportFactory: TransportFactory?, dispatchers: CoroutineDispatchers): TransportFactory? {
        bridge?.outputLine(manager?.res?.getString(R.string.terminal_connecting_via_jump, jumpHost.nickname))

        val policy = resolveCryptoPolicy(jumpHost, dispatchers) ?: return null
        val config = SshClientConfig {
            // Interactive host-key approval and slow servers must not have a fixed deadline.
            handshakeTimeoutMs = 0L
            cryptoPolicy(policy)
            algorithmWarningHandler = { recordPolicyWarnings(jumpHost, listOf(it)) }
            if (jumpTransportFactory != null) {
                this.transportFactory = jumpTransportFactory
            } else {
                this.host = jumpHost.hostname
                this.port = jumpHost.port
            }
            this.hostKeyAlgorithms = policyHostKeyOrder(jumpHost, policy)
            this.hostKeyVerifier = ConnectBotHostKeyVerifier(jumpHost)
            this.enableCompression = jumpHost.compression
            this.ipVersion = parseIpVersion(jumpHost.ipVersion, jumpHost.hostname)
        }

        jumpFailureReason = DisconnectReason.IO_ERROR
        val jumpClient = SshClient(config)
        policyClients[jumpClient] = jumpHost
        synchronized(jumpClients) { jumpClients.add(jumpClient) }
        val connectResult = jumpClient.connect()
        if (connectResult !is ConnectResult.Success) {
            Timber.e("Failed to connect to jump host: ${jumpHost.nickname}")
            bridge?.outputLine(
                manager?.res?.getString(
                    R.string.terminal_jump_failed,
                    jumpHost.nickname,
                    describeConnectResult(connectResult),
                ),
            )
            return null
        }
        updateLocalIpAddress(jumpClient)

        bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_connected, jumpHost.nickname))

        if (!authenticateJumpHost(jumpClient, jumpHost)) {
            bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_auth_failed, jumpHost.nickname))
            synchronized(jumpClients) { jumpClients.remove(jumpClient) }
            jumpClient.disconnect()
            return null
        }

        bridge?.outputLine(manager?.res?.getString(R.string.terminal_jump_authenticated, jumpHost.nickname))

        return jumpClient.openDirectTcpipTransport(destination.hostname, destination.port)
    }

    private fun preparePublicKeyAuthentication(client: SshClient?, pair: KeyPair): Boolean {
        if (pair.public !is RSAPublicKey || pair.private.encoded != null) return true
        return client != null && RsaSignatureAlgorithmPolicy.prepareForAuthentication(client)
    }

    internal suspend fun authenticateJumpHost(jumpClient: SshClient, jumpHost: Host): Boolean {
        val authBannerSourceName = jumpHost.authBannerSourceName()
        try {
            val username = jumpHost.username.ifEmpty {
                requestLoginPrompt(null, manager?.res?.getString(R.string.prompt_username), false)
            }
            repeat(AUTH_TRIES) { attempt ->
                val credentialMethods = mutableSetOf<String>()
                val result = jumpClient.authenticate(
                    username,
                    object : AuthHandler {
                        override suspend fun onPublicKeysNeeded(): List<AuthPublicKey> {
                            val pubkeyId = jumpHost.pubkeyId
                            if (pubkeyId == HostConstants.PUBKEYID_NEVER) return emptyList()

                            val keys = mutableListOf<AuthPublicKey>()
                            if (pubkeyId == HostConstants.PUBKEYID_ANY) {
                                manager?.loadedKeypairs?.entries?.forEach { entry ->
                                    val keyPair = entry.value.pair ?: return@forEach
                                    try {
                                        if (preparePublicKeyAuthentication(jumpClient, keyPair)) {
                                            keys.add(SshSigning.encodePublicKey(keyPair))
                                        }
                                    } catch (_: Exception) {
                                        Timber.d("Jump host: could not encode key ${entry.key}")
                                    }
                                }
                            } else {
                                val pubkey = manager?.pubkeyRepository?.getByIdBlocking(pubkeyId)
                                if (pubkey != null) {
                                    val pair = getAuthenticationKey(pubkey)
                                    if (pair != null) {
                                        try {
                                            if (preparePublicKeyAuthentication(jumpClient, pair)) {
                                                keys.add(SshSigning.encodePublicKey(pair))
                                            }
                                        } catch (_: Exception) {
                                            Timber.d("Jump host: could not encode specific key")
                                        }
                                    }
                                }
                            }
                            return keys
                        }

                        override suspend fun onSignatureRequest(key: AuthPublicKey, dataToSign: ByteArray): ByteArray? {
                            val keyPair = confirmedAuthenticationKey(jumpClient, key) ?: return null
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
                            prompts: List<KeyboardInteractiveCallback.Prompt>,
                        ): List<String>? = prompts.map { prompt ->
                            credentialMethods.add("keyboard-interactive")
                            val isPassword = !prompt.echo
                            if (isPassword && jumpHost.id !in savedPasswordsTried) {
                                val savedPassword = manager?.securePasswordStorage?.getPassword(jumpHost.id)
                                if (savedPassword != null) {
                                    savedPasswordsTried.add(jumpHost.id)
                                    bridge?.outputLine(manager?.res?.getString(R.string.terminal_auth_saved_password))
                                    return@map savedPassword
                                }
                            }
                            val promptPrefix = manager?.res?.getString(R.string.terminal_jump_prompt, jumpHost.nickname) ?: ""
                            requestLoginPrompt(
                                instruction,
                                "$promptPrefix ${prompt.text}",
                                isPassword,
                            )
                        }

                        override suspend fun onPasswordNeeded(): String? {
                            credentialMethods.add("password")
                            val savedPassword = manager?.securePasswordStorage?.getPassword(jumpHost.id)
                            if (savedPassword != null && jumpHost.id !in savedPasswordsTried) {
                                savedPasswordsTried.add(jumpHost.id)
                                return savedPassword
                            }

                            val passwordPrompt = manager?.res?.getString(R.string.terminal_jump_password, jumpHost.nickname)
                            return requestLoginPrompt(null, passwordPrompt, true)
                        }

                        override suspend fun onBanner(message: String) {
                            handleAuthBanner(authBannerSourceName, message, null)
                        }
                    },
                )

                capturePolicyWarnings(jumpClient, jumpHost)
                currentCoroutineContext().ensureActive()
                if (loginCancelled) throw LoginCancelled()
                jumpFailureReason = if (result is AuthResult.Failure) DisconnectReason.AUTH_FAIL else DisconnectReason.IO_ERROR
                outputAuthResult(result)
                when (result) {
                    is AuthResult.Success -> return true
                    is AuthResult.Error -> return false
                    is AuthResult.Failure -> if (result.allowedMethods.none { it in credentialMethods }) return false
                }
                if (attempt < AUTH_TRIES - 1) delay(1000)
            }
            return false
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            jumpFailureReason = if (e is LoginCancelled || loginCancelled) DisconnectReason.USER_REQUESTED else DisconnectReason.IO_ERROR
            Timber.e(e, "Error during jump host authentication")
            return false
        } finally {
            bridge?.dismissAuthBannersFrom(authBannerSourceName)
        }
    }

    override suspend fun connect(scope: CoroutineScope, dispatchers: CoroutineDispatchers, encoding: String) {
        locallyClosed = false
        policyDetails.clear()
        policyClients.clear()
        manager?.let { CryptoProviderState.ensureLoaded(it, dispatchers) }
        lastConnectionInfo = null
        connectionLossCause = null
        outputDrained = null
        savedPasswordsTried.clear()
        keyUseDecisions.clear()
        loginCancelled = false
        jumpFailureReason = DisconnectReason.IO_ERROR
        val currentHost = host ?: return

        val jumpHosts = try {
            resolveJumpHostChain(currentHost) { manager?.hostRepository?.findHostByIdBlocking(it) }
        } catch (e: JumpHostChainException) {
            bridge?.outputLine(manager?.res?.getString(e.messageResId))
            onDisconnect()
            return
        }
        var transportFactory: TransportFactory? = null
        for (index in jumpHosts.indices.reversed()) {
            val destination = if (index == 0) currentHost else jumpHosts[index - 1]
            transportFactory = connectToJumpHost(jumpHosts[index], destination, transportFactory, dispatchers) ?: run {
                close()
                if (bridge?.isDisconnected != true) onDisconnect(jumpFailureReason)
                return
            }
        }

        val policy = resolveCryptoPolicy(currentHost, dispatchers) ?: run {
            close()
            onDisconnect()
            return
        }
        val config = SshClientConfig {
            // Interactive host-key approval and slow servers must not have a fixed deadline.
            handshakeTimeoutMs = 0L
            cryptoPolicy(policy)
            algorithmWarningHandler = { recordPolicyWarnings(currentHost, listOf(it)) }
            if (transportFactory != null) {
                this.transportFactory = transportFactory
            } else {
                this.host = currentHost.hostname
                this.port = currentHost.port
            }
            this.hostKeyAlgorithms = policyHostKeyOrder(currentHost, policy)
            this.hostKeyVerifier = ConnectBotHostKeyVerifier(currentHost)
            this.enableCompression = compressionEnabled
            this.ipVersion = parseIpVersion(currentHost.ipVersion, currentHost.hostname)
            // The bridge owns teardown after the session's output has been read.
            // Mosh also needs its launcher channel to close without ending the handoff.
            this.autoDisconnectOnLastChannelClose = false
        }

        client = SshClient(config)
        client?.let { policyClients[it] = currentHost }

        try {
            when (val connectResult = client?.connect()) {
                is ConnectResult.Success -> {
                    lastConnectionInfo = client?.connectionInfo
                    updateLocalIpAddress(client)
                }

                else -> {
                    bridge?.outputLine(describeConnectResult(connectResult))
                    close()
                    onDisconnect()
                    return
                }
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

        this.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            client?.disconnectedFlow?.collect { reason ->
                handleConnectionLost(reason)
            }
        }

        authenticateConnection()
    }

    internal suspend fun authenticateConnection() {
        try {
            repeat(AUTH_TRIES) { attempt ->
                if (!connected) return
                val authenticatingClient = client
                val result = authenticate()
                authenticatingClient?.let { sshClient -> host?.let { capturePolicyWarnings(sshClient, it) } }
                currentCoroutineContext().ensureActive()
                when (result) {
                    is AuthResult.Success -> {
                        finishConnection()
                        return
                    }

                    is AuthResult.Error -> {
                        disconnectAuthentication(DisconnectReason.IO_ERROR)
                        return
                    }

                    is AuthResult.Failure -> {
                        if (result.allowedMethods.none { it in credentialMethods }) {
                            disconnectAuthentication(DisconnectReason.AUTH_FAIL)
                            return
                        }
                    }

                    null -> {
                        disconnectAuthentication(DisconnectReason.IO_ERROR)
                        return
                    }
                }
                if (attempt < AUTH_TRIES - 1) delay(1000)
            }
            disconnectAuthentication(DisconnectReason.AUTH_FAIL)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (bridge?.isDisconnected == true) {
                // Connection loss also cancels prompts. Keep its disconnect reason.
                close()
                return
            }
            if (e is LoginCancelled || loginCancelled) {
                disconnectAuthentication(DisconnectReason.USER_REQUESTED)
            } else {
                Timber.e(e, "Problem in SSH connection thread during authentication")
                disconnectAuthentication(DisconnectReason.IO_ERROR)
            }
        }
    }

    private suspend fun disconnectAuthentication(reason: DisconnectReason) {
        // A user-requested disconnect can destroy the bridge immediately.
        // Release SSH resources before handing that decision to the UI.
        close()
        onDisconnect(reason)
    }

    override suspend fun close() {
        if (bridge?.isInGracePeriod() == true) {
            Timber.d("Deferring SSH close - bridge in network grace period")
            return
        }

        getSshConnectionInfo()
        policyClients.forEach { (sshClient, currentHost) -> capturePolicyWarnings(sshClient, currentHost) }
        policyClients.clear()
        locallyClosed = true
        connected = false
        sessionOpen = false
        outputDrained?.complete(Unit)
        localIpAddress = null

        // Detach ownership before disconnecting: callbacks can re-enter close().
        val (closingSession, closingClient, closingJumps) = synchronized(jumpClients) {
            Triple(session, client, jumpClients.asReversed().toList()).also {
                session = null
                client = null
                jumpClients.clear()
            }
        }
        closingSession?.close()
        closingClient?.disconnect()
        closingJumps.forEach {
            try {
                it.disconnect()
            } catch (ignored: Exception) {
            }
        }
    }

    protected open fun onDisconnect(reason: DisconnectReason = DisconnectReason.IO_ERROR) {
        bridge?.dispatchDisconnect(reason)
    }

    @VisibleForTesting
    internal suspend fun handleConnectionLost(cause: Throwable?) {
        if (locallyClosed) return
        if (bridge?.isInGracePeriod() == true) {
            Timber.d("SSH connection lost during grace period (expected due to network loss)")
            return
        }
        Timber.d(cause, "SSH connection lost outside grace period - disconnecting")
        connectionLossCause = cause
        outputDrained?.let {
            // The relay owns EOF/error classification once it owns the output.
            it.await()
            return
        }
        val currentSession = session
        // The connection callback can precede the relay observing EOF. Preserve the
        // shell's exit status so a normal logout does not offer to reconnect.
        val reason = if (currentSession != null) {
            val sessionReason = getDisconnectReasonForClosedSession(currentSession)
            if (sessionReason == DisconnectReason.SESSION_EXIT || cause == null) {
                sessionReason
            } else {
                DisconnectReason.IO_ERROR
            }
        } else if (cause == null) {
            DisconnectReason.REMOTE_EOF
        } else {
            DisconnectReason.IO_ERROR
        }
        onDisconnect(reason)
    }

    @VisibleForTesting
    internal suspend fun getDisconnectReasonForClosedSession(session: SshSession): DisconnectReason {
        val exit = try {
            withTimeoutOrNull(250L) { session.exitInfo.await() }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            // A failed exit report must not prevent EOF from reaching the bridge.
            null
        }
        return when (exit) {
            is SessionExit.Status -> DisconnectReason.SESSION_EXIT
            else -> DisconnectReason.REMOTE_EOF
        }
    }

    @Throws(IOException::class)
    override suspend fun flush() {
        // New library handles flushing internally
    }

    override fun getSshConnectionInfo(): ConnectionInfo? {
        client?.connectionInfo?.let { lastConnectionInfo = it }
        return lastConnectionInfo
    }

    override fun getOutputStreams(): List<ReceiveChannel<ByteArray>>? {
        val currentSession = session ?: return null
        if (outputDrained == null) outputDrained = CompletableDeferred()
        return listOf(currentSession.stdout, currentSession.stderr)
    }

    override suspend fun onOutputComplete() {
        try {
            val currentSession = session ?: return
            val reason = getDisconnectReasonForClosedSession(currentSession)
            if (bridge == null) {
                close()
            } else {
                onDisconnect(if (reason == DisconnectReason.SESSION_EXIT || connectionLossCause == null) reason else DisconnectReason.IO_ERROR)
            }
        } finally {
            outputDrained?.complete(Unit)
        }
    }

    override fun onOutputFailure(cause: Exception) {
        onDisconnect(DisconnectReason.IO_ERROR)
        outputDrained?.complete(Unit)
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

        val currentSession = session ?: run {
            onDisconnect(DisconnectReason.REMOTE_EOF)
            throw IOException("Remote end closed connection")
        }
        val data = try {
            currentSession.read()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            onOutputFailure(e)
            throw IOException("Unable to read from SSH session", e)
        }
        if (data == null) {
            onOutputComplete()
            throw IOException("Remote end closed connection")
        }
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

    override fun getOptions(): Map<String, String> = mapOf("compression" to compressionEnabled.toString())

    override fun setOptions(options: Map<String, String>) {
        if (options.containsKey("compression")) {
            compressionEnabled = options["compression"]?.toBoolean() ?: false
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
                    client?.remotePortForward(
                        portForward.sourceAddr,
                        portForward.sourcePort,
                        portForward.destAddr ?: "",
                        portForward.destPort,
                    )

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

    override fun setInitialDimensions(columns: Int, rows: Int, width: Int, height: Int) {
        this.columns = columns
        this.rows = rows
        this.width = width
        this.height = height
    }

    override suspend fun resize(columns: Int, rows: Int, width: Int, height: Int) {
        setInitialDimensions(columns, rows, width, height)

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

    private fun promptForPubkeyUse(nickname: String): Boolean {
        val result = bridge?.requestBooleanPrompt(
            null,
            manager?.res?.getString(R.string.prompt_allow_agent_to_use_key, nickname) ?: "",
        )
        return result ?: false
    }

    inner class ConnectBotAgentProvider : AgentProvider {
        override suspend fun addIdentity(keyPair: KeyPair, comment: String, confirmUse: Boolean, lifetimeSeconds: Long?): AgentResult<Boolean> {
            // Do not silently accept a lifetime constraint the cache cannot enforce.
            if (agentLockPassphrase != null || lifetimeSeconds != null) return AgentResult.Success(false)
            val terminalManager = manager ?: return AgentResult.Success(false)
            val pubkey = Pubkey(
                id = UUID.randomUUID(),
                nickname = comment,
                type = "IMPORTED",
                privateKey = byteArrayOf(),
                publicKey = keyPair.public.encoded,
                encrypted = false,
                startup = false,
                confirmation = confirmUse,
                createdDate = System.currentTimeMillis(),
                storageType = KeyStorageType.EXPORTABLE,
                allowBackup = true,
                keystoreAlias = null,
            )
            terminalManager.addKey(pubkey, keyPair)
            return AgentResult.Success(terminalManager.getKey(comment) === keyPair)
        }

        override suspend fun removeIdentity(publicKeyBlob: ByteArray): AgentResult<Boolean> = AgentResult.Success(agentLockPassphrase == null && manager?.removeKey(publicKeyBlob) == true)

        override suspend fun removeAllIdentities(): AgentResult<Boolean> {
            if (agentLockPassphrase != null) return AgentResult.Success(false)
            val terminalManager = manager ?: return AgentResult.Success(false)
            terminalManager.loadedKeypairs.keys.toList().forEach { terminalManager.removeKey(it) }
            return AgentResult.Success(true)
        }

        override suspend fun isLocked(): Boolean = agentLockPassphrase != null

        override suspend fun lock(passphrase: String): AgentResult<Boolean> = synchronized(this@SSH) {
            if (agentLockPassphrase != null) {
                AgentResult.Success(false)
            } else {
                agentLockPassphrase = passphrase
                AgentResult.Success(true)
            }
        }

        override suspend fun unlock(passphrase: String): AgentResult<Boolean> = synchronized(this@SSH) {
            if (agentLockPassphrase == null || agentLockPassphrase != passphrase) {
                AgentResult.Success(false)
            } else {
                agentLockPassphrase = null
                AgentResult.Success(true)
            }
        }

        override suspend fun getIdentities(): AgentResult<List<org.connectbot.sshlib.AgentIdentity>> {
            if (agentLockPassphrase != null) return AgentResult.Success(emptyList())
            val identities = manager?.loadedKeypairs?.entries?.mapNotNull { entry ->
                val pair = entry.value.pair ?: return@mapNotNull null
                try {
                    val encoded = SshSigning.encodePublicKey(pair)
                    org.connectbot.sshlib.AgentIdentity(encoded.publicKeyBlob, entry.key)
                } catch (e: Exception) {
                    Timber.d(e, "Could not encode key for agent: ${entry.key}")
                    null
                }
            } ?: emptyList()
            return AgentResult.Success(identities)
        }

        override suspend fun signData(context: AgentSigningContext): AgentResult<ByteArray?> {
            if (agentLockPassphrase != null) return AgentResult.Success(null)

            val nickname = manager?.getKeyNickname(context.publicKeyBlob) ?: return AgentResult.Success(null)

            if (useAuthAgent == HostConstants.AUTHAGENT_NO) return AgentResult.Success(null)
            val holder = manager?.loadedKeypairs?.get(nickname)
            if (useAuthAgent == HostConstants.AUTHAGENT_CONFIRM || holder?.pubkey?.confirmation == true) {
                if (!promptForPubkeyUse(nickname)) return AgentResult.Success(null)
            }
            if (agentLockPassphrase != null) return AgentResult.Success(null)

            val keyPair = manager?.getKey(nickname) ?: return AgentResult.Success(null)
            val algo = determineAlgorithm(keyPair, context.flags)
            return try {
                AgentResult.Success(SshSigning.signWithKeyPair(algo, keyPair, context.dataToSign))
            } catch (e: Exception) {
                Timber.e(e, "Agent signing failed for key: $nickname")
                AgentResult.Failure("Agent signing failed for key: $nickname", e)
            }
        }
    }

    internal fun determineAlgorithm(keyPair: KeyPair, flags: Int): String = when {
        keyPair.public is RSAPublicKey -> {
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

    override fun getLocalIpAddress(): String? = localIpAddress

    @VisibleForTesting
    internal fun getKeyType(openSshKeyType: String): String? = SshKeyType.fromOpenSshType(openSshKeyType)?.storedName

    private fun describeConnectResult(result: ConnectResult?): String = when (result) {
        is ConnectResult.AlgorithmMismatch -> result.message

        is ConnectResult.HostKeyRejected -> manager?.res?.getString(R.string.terminal_failed).orEmpty()

        is ConnectResult.ProtocolError -> result.message

        is ConnectResult.TransportError ->
            result.cause.message
                ?: manager?.res?.getString(R.string.terminal_connection_failed).orEmpty()

        is ConnectResult.Success -> manager?.res?.getString(R.string.terminal_connected).orEmpty()

        null -> manager?.res?.getString(R.string.terminal_connection_failed).orEmpty()
    }

    private fun updateLocalIpAddress(sshClient: SshClient?) {
        localIpAddress = extractLocalIpAddress(sshClient) ?: localIpAddress
    }

    private fun extractLocalIpAddress(sshClient: SshClient?): String? {
        if (sshClient == null) return null

        return try {
            val transport = sshClient.readPrivateField("transport") ?: return null
            val transportSocket = transport.readPrivateField("socket") ?: return null
            val ktorSocket = transportSocket.readPrivateField("socket") ?: return null
            val localAddress = ktorSocket.javaClass.getMethod("getLocalAddress").invoke(ktorSocket)
            val javaAddress = localAddress.javaClass.getMethod("getAddress\$ktor_network").invoke(localAddress)
                as? java.net.InetSocketAddress
            javaAddress?.address?.hostAddress ?: javaAddress?.hostString
        } catch (e: ReflectiveOperationException) {
            Timber.d(e, "Could not determine SSH local IP address")
            null
        } catch (e: ClassCastException) {
            Timber.d(e, "Could not parse SSH local IP address")
            null
        }
    }

    private fun Any.readPrivateField(name: String): Any? = javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)

    /**
     * Returns the protocol name for this transport instance.
     * SSH returns "ssh", Mosh overrides to return "mosh".
     */
    open fun instanceProtocolName(): String = PROTOCOL

    companion object {
        internal fun getKeySizeFromAlgorithm(algorithm: String, keyBlob: ByteArray): Int = when {
            algorithm == "ssh-rsa" || algorithm.startsWith("rsa-sha2-") -> {
                rsaModulusBitLength(keyBlob)
            }

            algorithm == "ssh-dss" -> 1024

            algorithm == "ssh-ed25519" -> 256

            algorithm == "ecdsa-sha2-nistp256" -> 256

            algorithm == "ecdsa-sha2-nistp384" -> 384

            algorithm == "ecdsa-sha2-nistp521" -> 521

            else -> 0
        }

        private fun rsaModulusBitLength(keyBlob: ByteArray): Int {
            val buffer = ByteBuffer.wrap(keyBlob)
            fun readField(): ByteArray {
                require(buffer.remaining() >= Int.SIZE_BYTES)
                val length = buffer.int
                require(length in 0..buffer.remaining())
                return ByteArray(length).also { buffer.get(it) }
            }
            return try {
                require(String(readField(), StandardCharsets.US_ASCII) == "ssh-rsa")
                readField() // Public exponent.
                val modulus = readField()
                require(modulus.isNotEmpty() && !buffer.hasRemaining())
                BigInteger(1, modulus).bitLength()
            } catch (_: IllegalArgumentException) {
                0
            }
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

        protected val hostmask = Pattern.compile(
            "^(.+)@((?:[0-9a-z._-]+)|(?:\\[[a-f:0-9]+(?:%[-_.a-z0-9]+)?\\]))(?::(\\d+))?\$",
            Pattern.CASE_INSENSITIVE,
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
