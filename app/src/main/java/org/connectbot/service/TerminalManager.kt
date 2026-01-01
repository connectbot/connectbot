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

package org.connectbot.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import com.trilead.ssh2.crypto.PublicKeyUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.HostRepository
import org.connectbot.data.ProfileRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Pubkey
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.transport.TransportFactory
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.ProviderLoader
import org.connectbot.util.ProviderLoaderListener
import org.connectbot.util.PubkeyUtils
import timber.log.Timber
import java.io.IOException
import java.lang.ref.WeakReference
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Manager for SSH connections that runs as a service. This service holds a list
 * of currently connected SSH bridges that are ready for connection up to a GUI
 * if needed.
 */
@AndroidEntryPoint
class TerminalManager :
    Service(),
    BridgeDisconnectedListener,
    OnSharedPreferenceChangeListener,
    ProviderLoaderListener {

    private val _bridges = ArrayList<TerminalBridge>()
    private val _bridgesFlow = MutableStateFlow<List<TerminalBridge>>(emptyList())
    val bridgesFlow: StateFlow<List<TerminalBridge>> = _bridgesFlow.asStateFlow()

    @Deprecated("Use bridgesFlow instead", ReplaceWith("bridgesFlow.value"))
    val bridges: List<TerminalBridge>
        get() = _bridges.toList()

    // Maps for multi-session support: hostId -> list of bridges
    private val hostBridgesMap: MutableMap<Long, MutableList<WeakReference<TerminalBridge>>> = HashMap()

    // Quick lookup by session ID
    private val bridgesBySessionId: MutableMap<Long, WeakReference<TerminalBridge>> = HashMap()

    // Track last-used session per host for navigation
    private val lastUsedSessionByHost: MutableMap<Long, Long> = HashMap()

    // Session ID counter (starts at 1)
    private val nextSessionId = AtomicLong(1L)

    // Legacy maps for backwards compatibility (deprecated)
    @Deprecated("Use hostBridgesMap instead")
    private val hostBridgeMap: MutableMap<Host, WeakReference<TerminalBridge>> = HashMap()

    @Deprecated("Use getConnectedBridges instead")
    private val nicknameBridgeMap: MutableMap<String, WeakReference<TerminalBridge>> = HashMap()

    private val _disconnected = ArrayList<Host>()
    private val _disconnectedFlow = MutableStateFlow<List<Host>>(emptyList())
    val disconnectedFlow: StateFlow<List<Host>> = _disconnectedFlow.asStateFlow()

    private var disconnectListener: BridgeDisconnectedListener? = null

    /**
     * Report an error from the service layer to be propagated to the UI.
     * This method is thread-safe and can be called from any context.
     *
     * @param error The ServiceError to report
     */
    fun reportError(error: ServiceError) {
        scope.launch {
            _serviceErrors.emit(error)
        }
    }

    private val _hostStatusChanged = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 10)
    val hostStatusChangedFlow: SharedFlow<Unit> = _hostStatusChanged.asSharedFlow()

    private val _serviceErrors = MutableSharedFlow<ServiceError>(replay = 0, extraBufferCapacity = 10)
    val serviceErrors: SharedFlow<ServiceError> = _serviceErrors.asSharedFlow()

    private val _loadedKeysChanged = MutableSharedFlow<Set<String>>(replay = 1, extraBufferCapacity = 1)
    val loadedKeysChangedFlow: SharedFlow<Set<String>> = _loadedKeysChanged.asSharedFlow()

    internal val loadedKeypairs: MutableMap<String, KeyHolder> = ConcurrentHashMap()

    internal lateinit var res: Resources

    @Inject
    internal lateinit var hostRepository: HostRepository

    @Inject
    internal lateinit var colorRepository: ColorSchemeRepository

    @Inject
    internal lateinit var profileRepository: ProfileRepository

    @Inject
    internal lateinit var pubkeyRepository: PubkeyRepository

    @Inject
    internal lateinit var prefs: SharedPreferences

    @Inject
    internal lateinit var connectionNotifier: ConnectionNotifier

    @Inject
    internal lateinit var dispatchers: CoroutineDispatchers

    @Inject
    internal lateinit var securePasswordStorage: org.connectbot.util.SecurePasswordStorage

    private val binder: IBinder = TerminalBinder()

    internal lateinit var connectivityMonitor: ConnectivityMonitor

    private var mediaPlayer: MediaPlayer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var idleJob: Job? = null
    private val idleTimeout: Long = 300000 // 5 minutes

    private var vibrator: Vibrator? = null

    @Volatile
    private var wantKeyVibration = false

    private var wantBellVibration = false

    @Volatile
    private var isUiBound = false

    private var resizeAllowed = true

    private var savingKeys = false

    private val pendingReconnect: MutableList<WeakReference<TerminalBridge>> = ArrayList()

    private val nextTemporaryHostId = AtomicLong(-1L)

    internal var hardKeyboardHidden = false

    override fun onCreate() {
        super.onCreate()
        Timber.i("Starting service")

        prefs.registerOnSharedPreferenceChangeListener(this)

        res = resources

        // load all marked pubkeys into memory
        updateSavingKeys()
        scope.launch(dispatchers.io) {
            try {
                val pubkeys = pubkeyRepository.getStartupKeys()
                for (pubkey in pubkeys) {
                    try {
                        val pair = PubkeyUtils.convertToKeyPair(pubkey, null)
                        if (pair != null) {
                            addKey(pubkey, pair)
                        } else {
                            Timber.w(String.format("Failed to convert key '%s' to KeyPair", pubkey.nickname))
                            _serviceErrors.emit(
                                ServiceError.KeyLoadFailed(
                                    keyName = pubkey.nickname,
                                    reason = "Failed to convert key to KeyPair"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Problem adding key '%s' to in-memory cache", pubkey.nickname)
                        _serviceErrors.emit(
                            ServiceError.KeyLoadFailed(
                                keyName = pubkey.nickname,
                                reason = e.message ?: "Unknown error loading key"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load startup keys")
                _serviceErrors.emit(
                    ServiceError.KeyLoadFailed(
                        keyName = "startup keys",
                        reason = e.message ?: "Failed to retrieve keys from database"
                    )
                )
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ uses VibratorManager
            val manager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            // Pre-API 31 uses direct Vibrator service
            @Suppress("Deprecation")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

        wantKeyVibration = prefs.getBoolean(PreferenceConstants.BUMPY_ARROWS, true)

        wantBellVibration = prefs.getBoolean(PreferenceConstants.BELL_VIBRATE, true)
        enableMediaPlayer()

        hardKeyboardHidden = (
            res.configuration.hardKeyboardHidden ==
                Configuration.HARDKEYBOARDHIDDEN_YES
            )

        val lockingWifi = prefs.getBoolean(PreferenceConstants.WIFI_LOCK, true)

        connectivityMonitor = ConnectivityMonitor(this, lockingWifi)
        connectivityMonitor.init()

        ProviderLoader.load(this, this)
    }

    private fun updateSavingKeys() {
        savingKeys = prefs.getBoolean(PreferenceConstants.MEMKEYS, true)
    }

    override fun onDestroy() {
        Timber.i("Destroying service")

        scope.cancel()

        stopIdleTimer()

        disconnectAll(immediate = true, excludeLocal = false)

        connectionNotifier.hideRunningNotification(this)

        disableMediaPlayer()

        connectivityMonitor.cleanup()

        super.onDestroy()
    }

    /**
     * Generate a unique negative ID for a temporary host.
     * Temporary hosts use negative IDs to distinguish them from database hosts.
     */
    private fun generateTemporaryHostId(): Long = nextTemporaryHostId.getAndDecrement()

    /**
     * Disconnect all currently connected bridges.
     */
    fun disconnectAll(immediate: Boolean, excludeLocal: Boolean) {
        var tmpBridges: Array<TerminalBridge>? = null

        synchronized(_bridges) {
            if (_bridges.isNotEmpty()) {
                tmpBridges = _bridges.toTypedArray().clone()
            }
        }

        if (tmpBridges != null) {
            // disconnect and dispose of any existing bridges
            for (tmpBridge in tmpBridges) {
                if (excludeLocal && !tmpBridge.isUsingNetwork()) {
                    continue
                }
                tmpBridge.dispatchDisconnect(immediate)
            }
        }
    }

    /**
     * Generate a unique session ID for a new bridge.
     */
    private fun generateSessionId(): Long = nextSessionId.getAndIncrement()

    /**
     * Open a new SSH session using the given parameters.
     * Multiple sessions to the same host are now allowed.
     */
    private fun openConnection(host: Host): TerminalBridge {
        val sessionId = generateSessionId()
        val bridge = TerminalBridge(this, host, sessionId, dispatchers)
        bridge.setOnDisconnectedListener(this)
        bridge.startConnection()

        synchronized(_bridges) {
            _bridges.add(bridge)
            val wr = WeakReference(bridge)

            // Add to multi-session maps
            val hostBridges = hostBridgesMap.getOrPut(host.id) { mutableListOf() }
            hostBridges.add(wr)
            bridgesBySessionId[sessionId] = wr

            // Mark as last-used session for this host
            lastUsedSessionByHost[host.id] = sessionId

            // Legacy maps (for backwards compatibility)
            @Suppress("DEPRECATION")
            hostBridgeMap[bridge.host] = wr
            @Suppress("DEPRECATION")
            nicknameBridgeMap[bridge.host.nickname] = wr

            _bridgesFlow.value = _bridges.toList()
        }

        synchronized(_disconnected) {
            _disconnected.remove(bridge.host)
            _disconnectedFlow.value = _disconnected.toList()
        }

        if (bridge.isUsingNetwork()) {
            connectivityMonitor.incRef()
        }

        if (prefs.getBoolean(PreferenceConstants.CONNECTION_PERSIST, true)) {
            connectionNotifier.showRunningNotification(this)
        }

        // also update database with new connected time
        touchHost(host)

        notifyHostStatusChanged()

        return bridge
    }

    fun getScrollback(): Int {
        var scrollback = 140
        try {
            scrollback = Integer.parseInt(prefs.getString(PreferenceConstants.SCROLLBACK, "140")!!)
        } catch (_: Exception) {
        }
        return scrollback
    }

    /**
     * Open a new connection by reading parameters from the given URI. Follows
     * format specified by an individual transport.
     */
    suspend fun openConnection(uri: Uri): TerminalBridge {
        Timber.d("openConnection: uri=$uri, scheme=${uri.scheme}, fragment=${uri.fragment}")
        val scheme = uri.scheme
            ?: throw IllegalArgumentException("URI must contain a scheme (e.g., 'ssh://', 'telnet://'). URI: $uri")

        val host: Host = TransportFactory.findHost(hostRepository, uri)
            ?: TransportFactory.getTransport(scheme)?.createHost(uri)
            ?: throw IllegalArgumentException("No transport found for scheme '$scheme' in URI: $uri")

        // Assign unique negative ID to temporary hosts (id == 0)
        val finalHost = if (host.id == 0L) {
            host.copy(id = generateTemporaryHostId())
        } else {
            host
        }

        return openConnection(finalHost)
    }

    /**
     * Open a new connection for a host by its database ID.
     * Looks up the host from the repository and creates a connection.
     *
     * @param hostId the database ID of the host to connect to
     * @return TerminalBridge for the connection, or null if host not found
     */
    suspend fun openConnectionForHostId(hostId: Long): TerminalBridge? {
        val host = hostRepository.findHostById(hostId) ?: return null
        return openConnection(host)
    }

    /**
     * Update the last-connected value for the given nickname by passing through
     * to [HostRepository].
     */
    private fun touchHost(host: Host) {
        scope.launch(dispatchers.io) {
            hostRepository.touchHost(host)
        }
    }

    /**
     * Find a connected [TerminalBridge] with the given Host.
     *
     * @param host the Host to search for
     * @return TerminalBridge that uses the Host
     */
    fun getConnectedBridge(host: Host): TerminalBridge? {
        val wr = hostBridgeMap[host]
        return wr?.get()
    }

    /**
     * Find a connected [TerminalBridge] using its nickname.
     *
     * @param nickname
     * @return TerminalBridge that matches nickname
     */
    @Deprecated("Use getConnectedBridges(hostId) for multi-session support")
    fun getConnectedBridge(nickname: String?): TerminalBridge? {
        @Suppress("DEPRECATION")
        val wr = nicknameBridgeMap[nickname ?: return null]
        return wr?.get()
    }

    /**
     * Get all connected bridges for a given host ID.
     * Supports multiple sessions per host.
     *
     * @param hostId the database ID of the host
     * @return List of connected TerminalBridge objects for this host
     */
    fun getConnectedBridges(hostId: Long): List<TerminalBridge> {
        synchronized(_bridges) {
            return hostBridgesMap[hostId]?.mapNotNull { it.get() } ?: emptyList()
        }
    }

    /**
     * Get a bridge by its session ID.
     *
     * @param sessionId the unique session identifier
     * @return TerminalBridge with that session ID, or null if not found
     */
    fun getBridgeBySessionId(sessionId: Long): TerminalBridge? {
        synchronized(_bridges) {
            return bridgesBySessionId[sessionId]?.get()
        }
    }

    /**
     * Get the number of active sessions for a host.
     *
     * @param hostId the database ID of the host
     * @return number of active sessions
     */
    fun getSessionCount(hostId: Long): Int {
        synchronized(_bridges) {
            return hostBridgesMap[hostId]?.count { it.get() != null } ?: 0
        }
    }

    /**
     * Get the last-used bridge for a host.
     * Returns the most recently opened or interacted-with session.
     *
     * @param hostId the database ID of the host
     * @return the last-used TerminalBridge, or the first available if none tracked
     */
    fun getLastUsedBridge(hostId: Long): TerminalBridge? {
        synchronized(_bridges) {
            val lastSessionId = lastUsedSessionByHost[hostId]
            if (lastSessionId != null) {
                val bridge = bridgesBySessionId[lastSessionId]?.get()
                if (bridge != null) return bridge
            }
            // Fallback to first available session
            return hostBridgesMap[hostId]?.firstNotNullOfOrNull { it.get() }
        }
    }

    /**
     * Mark a session as recently used.
     * Called when user interacts with a session.
     *
     * @param sessionId the session to mark as used
     */
    fun markSessionUsed(sessionId: Long) {
        synchronized(_bridges) {
            val bridge = bridgesBySessionId[sessionId]?.get() ?: return
            lastUsedSessionByHost[bridge.host.id] = sessionId
        }
    }

    /**
     * Check if any sessions are connected for a host.
     *
     * @param hostId the database ID of the host
     * @return true if at least one session is connected
     */
    fun isHostConnected(hostId: Long): Boolean = getSessionCount(hostId) > 0

    /**
     * Called by child bridge when somehow it's been disconnected.
     */
    override fun onDisconnected(bridge: TerminalBridge) {
        var shouldHideRunningNotification = false
        Timber.d("Bridge Disconnected. Removing it. SessionId=${bridge.sessionId}")

        synchronized(_bridges) {
            // remove this bridge from our list
            _bridges.remove(bridge)

            // Remove from multi-session maps
            bridgesBySessionId.remove(bridge.sessionId)
            hostBridgesMap[bridge.host.id]?.removeIf { it.get() == bridge || it.get() == null }
            // Clean up empty lists
            if (hostBridgesMap[bridge.host.id]?.isEmpty() == true) {
                hostBridgesMap.remove(bridge.host.id)
            }

            // Update last-used if this was the last-used session
            if (lastUsedSessionByHost[bridge.host.id] == bridge.sessionId) {
                // Set to another session if one exists, or remove
                val remaining = hostBridgesMap[bridge.host.id]?.mapNotNull { it.get() }
                if (remaining.isNullOrEmpty()) {
                    lastUsedSessionByHost.remove(bridge.host.id)
                } else {
                    lastUsedSessionByHost[bridge.host.id] = remaining.last().sessionId
                }
            }

            // Legacy maps cleanup
            @Suppress("DEPRECATION")
            hostBridgeMap.remove(bridge.host)
            @Suppress("DEPRECATION")
            nicknameBridgeMap.remove(bridge.host.nickname)

            if (bridge.isUsingNetwork()) {
                connectivityMonitor.decRef()
            }

            if (_bridges.isEmpty() && pendingReconnect.isEmpty()) {
                shouldHideRunningNotification = true
            }

            // pass notification back up to gui
            disconnectListener?.onDisconnected(bridge)
            _bridgesFlow.value = _bridges.toList()
        }

        synchronized(_disconnected) {
            _disconnected.add(bridge.host)
            _disconnectedFlow.value = _disconnected.toList()
        }

        notifyHostStatusChanged()

        if (shouldHideRunningNotification) {
            connectionNotifier.hideRunningNotification(this)
        }
    }

    fun isKeyLoaded(nickname: String): Boolean = loadedKeypairs.containsKey(nickname)

    private fun emitLoadedKeysChanged() {
        scope.launch {
            _loadedKeysChanged.emit(loadedKeypairs.keys.toSet())
        }
    }

    fun addKey(pubkey: Pubkey, pair: KeyPair) {
        addKey(pubkey, pair, false)
    }

    fun addKey(pubkey: Pubkey, pair: KeyPair, force: Boolean) {
        if (!savingKeys && !force) {
            return
        }

        removeKey(pubkey.nickname)

        val sshPubKey = PublicKeyUtils.extractPublicKeyBlob(pair.public)

        val keyHolder = KeyHolder()
        keyHolder.pubkey = pubkey
        keyHolder.pair = pair
        keyHolder.openSSHPubkey = sshPubKey

        loadedKeypairs[pubkey.nickname] = keyHolder

        // Note: Pubkey entity doesn't have lifetime field yet
        // This functionality may need to be re-added if needed

        Timber.d(String.format("Added key '%s' to in-memory cache", pubkey.nickname))
        emitLoadedKeysChanged()
    }

    /**
     * Result of adding a biometric key to the cache.
     */
    sealed class BiometricKeyResult {
        data object Success : BiometricKeyResult()
        data class KeyInvalidated(val message: String) : BiometricKeyResult()
        data class Error(val message: String) : BiometricKeyResult()
    }

    /**
     * Add a biometric key from Android Keystore to in-memory cache.
     * The PrivateKey from Keystore is a proxy that delegates signing to secure hardware.
     * Since biometric auth was just completed, the 30-second signing window is active.
     * The key will be automatically removed when the auth window expires.
     *
     * @return BiometricKeyResult indicating success or failure with reason
     */
    fun addBiometricKey(pubkey: Pubkey, keystoreAlias: String, publicKey: java.security.PublicKey): BiometricKeyResult {
        removeKey(pubkey.nickname)

        // Get the private key reference from Keystore
        // This is a proxy object - actual signing is done in secure hardware
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val privateKey = keyStore.getKey(keystoreAlias, null) as? java.security.PrivateKey

        if (privateKey == null) {
            val message = "Failed to get private key from Keystore for alias: $keystoreAlias"
            Timber.e(message)
            return BiometricKeyResult.Error(message)
        }

        // Validate the key by trying to initialize a Signature
        // This will throw if the key is invalidated (e.g., new fingerprint enrolled)
        try {
            val algorithm = when (publicKey) {
                is java.security.interfaces.RSAPublicKey -> "SHA256withRSA"
                is java.security.interfaces.ECPublicKey -> "SHA256withECDSA"
                else -> null
            }
            if (algorithm != null) {
                val signature = java.security.Signature.getInstance(algorithm)
                signature.initSign(privateKey)
            }
        } catch (e: Exception) {
            val cause = e.cause ?: e
            val isKeyInvalidated = cause is KeyPermanentlyInvalidatedException ||
                cause is UserNotAuthenticatedException ||
                e is KeyPermanentlyInvalidatedException ||
                e is UserNotAuthenticatedException
            if (isKeyInvalidated) {
                val message = res?.getString(R.string.terminal_auth_biometric_invalidated, pubkey.nickname)
                    ?: "Biometric key '${pubkey.nickname}' has been invalidated. Please generate a new key."
                Timber.e(e, message)
                return BiometricKeyResult.KeyInvalidated(message)
            } else {
                val message = "Failed to validate biometric key '${pubkey.nickname}': ${e.message}"
                Timber.e(e, message)
                return BiometricKeyResult.Error(message)
            }
        }

        // Create a KeyPair that the SSH library can use
        val keyPair = KeyPair(publicKey, privateKey)

        // Extract OpenSSH format public key for SSH authentication
        val sshPubKey = PublicKeyUtils.extractPublicKeyBlob(keyPair.public)

        val keyHolder = KeyHolder()
        keyHolder.pubkey = pubkey
        keyHolder.pair = keyPair // KeyPair with Keystore-backed private key
        keyHolder.openSSHPubkey = sshPubKey
        keyHolder.keystoreAlias = keystoreAlias
        keyHolder.isBiometricKey = true

        // Schedule auto-expiry when biometric auth window closes (30 seconds)
        keyHolder.expiryJob = scope.launch {
            delay(BIOMETRIC_AUTH_VALIDITY_SECONDS * 1000L)
            Timber.d("Biometric auth window expired for key '${pubkey.nickname}', removing from cache")
            removeKey(pubkey.nickname)
        }

        loadedKeypairs[pubkey.nickname] = keyHolder

        Timber.d("Added biometric key '%s' to in-memory cache (expires in %d seconds)", pubkey.nickname, BIOMETRIC_AUTH_VALIDITY_SECONDS)
        emitLoadedKeysChanged()
        return BiometricKeyResult.Success
    }

    fun removeKey(nickname: String): Boolean {
        val keyHolder = loadedKeypairs.remove(nickname)
        if (keyHolder != null) {
            // Cancel any pending expiry job for biometric keys
            keyHolder.expiryJob?.cancel()
            Timber.d(String.format("Removed key '%s' from in-memory cache", nickname))
            emitLoadedKeysChanged()
            return true
        }
        return false
    }

    fun removeKey(publicKey: ByteArray): Boolean {
        var nickname: String? = null
        for ((key, value) in loadedKeypairs) {
            if (value.openSSHPubkey.contentEquals(publicKey)) {
                nickname = key
                break
            }
        }

        return if (nickname != null) {
            removeKey(nickname)
        } else {
            false
        }
    }

    fun getKey(nickname: String): KeyPair? = loadedKeypairs[nickname]?.pair

    fun getKeyNickname(publicKey: ByteArray): String? {
        for ((key, value) in loadedKeypairs) {
            if (value.openSSHPubkey.contentEquals(publicKey)) {
                return key
            }
        }
        return null
    }

    private fun stopWithDelay() {
        // TODO add in a way to check whether keys loaded are encrypted and only
        // set timer when we have an encrypted key loaded

        if (loadedKeypairs.isNotEmpty()) {
            synchronized(this) {
                idleJob?.cancel()
                idleJob = scope.launch {
                    delay(idleTimeout)
                    Timber.d("Stopping service after timeout of ~%d seconds", idleTimeout / 1000)
                    stopNow()
                }
            }
        } else {
            Timber.d("Stopping service immediately")
            stopSelf()
        }
    }

    fun stopNow() {
        val shouldStop =
            synchronized(_bridges) {
                _bridges.isEmpty()
            }

        if (shouldStop) {
            stopSelf()
        }
    }

    @Synchronized
    private fun stopIdleTimer() {
        idleJob?.cancel()
        idleJob = null
    }

    override fun onProviderLoaderSuccess() {
        Timber.d("Installed crypto provider successfully")
    }

    override fun onProviderLoaderError() {
        Timber.e("Failure while installing crypto provider")
    }

    inner class TerminalBinder : Binder() {
        fun getService(): TerminalManager = this@TerminalManager
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.i("Someone bound to TerminalManager with %s bridges active", bridgesFlow.value.size)
        isUiBound = true
        keepServiceAlive()
        setResizeAllowed(true)
        return binder
    }

    /**
     * Make sure we stay running to maintain the bridges. Later [.stopNow] should be called to stop the service.
     */
    private fun keepServiceAlive() {
        stopIdleTimer()
        startService(Intent(this, TerminalManager::class.java))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		/*
		 * We want this service to continue running until it is explicitly
		 * stopped, so return sticky.
		 */
        return START_STICKY
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        Timber.i("Someone rebound to TerminalManager with %d bridges active", bridgesFlow.value.size)
        isUiBound = true
        keepServiceAlive()
        setResizeAllowed(true)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Timber.i("Someone unbound from TerminalManager with %d bridges active", bridgesFlow.value.size)

        isUiBound = false
        setResizeAllowed(true)

        if (bridgesFlow.value.isEmpty()) {
            stopWithDelay()
        }

        return true
    }

    fun tryKeyVibrate() {
        if (wantKeyVibration) {
            vibrate()
        }
    }

    private fun vibrate() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // O = API 26
                val vibrationEffect = VibrationEffect.createOneShot(
                    VIBRATE_DURATION,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                it.vibrate(vibrationEffect)
            } else {
                // Deprecated path for compatibility with older APIs (pre-API 26)
                @Suppress("DEPRECATION")
                it.vibrate(VIBRATE_DURATION)
            }
        }
    }

    private fun enableMediaPlayer() {
        mediaPlayer = MediaPlayer()

        val volume = prefs.getFloat(
            PreferenceConstants.BELL_VOLUME,
            PreferenceConstants.DEFAULT_BELL_VOLUME
        )

        val audioAttributes = AudioAttributes.Builder()
            // Use USAGE_NOTIFICATION for sounds that signal an event
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            // Use CONTENT_TYPE_SONIFICATION for non-music/non-speech sounds (like notifications or alarms)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        mediaPlayer?.setAudioAttributes(audioAttributes)

        val file = res.openRawResourceFd(R.raw.bell)
        try {
            mediaPlayer!!.isLooping = false
            mediaPlayer!!.setDataSource(
                file.fileDescriptor,
                file
                    .startOffset,
                file.length
            )
            file.close()
            mediaPlayer!!.setVolume(volume, volume)
            mediaPlayer!!.prepare()
        } catch (e: IOException) {
            Timber.e(e, "Error setting up bell media player")
        }
    }

    private fun disableMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer!!.release()
            mediaPlayer = null
        }
    }

    fun playBeep() {
        if (mediaPlayer != null) {
            mediaPlayer!!.seekTo(0)
            mediaPlayer!!.start()
        }

        if (wantBellVibration) {
            vibrate()
        }
    }

    /**
     * Send system notification to user for a certain host. When user selects
     * the notification, it will bring them directly to the ConsoleActivity
     * displaying the host.
     *
     * @param host
     */
    fun sendActivityNotification(host: Host) {
        if (!isUiBound && prefs.getBoolean(PreferenceConstants.BELL_NOTIFICATION, false)) {
            connectionNotifier.showActivityNotification(this, host)
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?
    ) {
        if (PreferenceConstants.BELL == key) {
            val wantAudible = sharedPreferences.getBoolean(PreferenceConstants.BELL, true)
            if (wantAudible && mediaPlayer == null) {
                enableMediaPlayer()
            } else if (!wantAudible && mediaPlayer != null) {
                disableMediaPlayer()
            }
        } else if (PreferenceConstants.BELL_VOLUME == key) {
            if (mediaPlayer != null) {
                val volume = sharedPreferences.getFloat(
                    PreferenceConstants.BELL_VOLUME,
                    PreferenceConstants.DEFAULT_BELL_VOLUME
                )
                mediaPlayer!!.setVolume(volume, volume)
            }
        } else if (PreferenceConstants.BELL_VIBRATE == key) {
            wantBellVibration = sharedPreferences.getBoolean(PreferenceConstants.BELL_VIBRATE, true)
        } else if (PreferenceConstants.BUMPY_ARROWS == key) {
            wantKeyVibration = sharedPreferences.getBoolean(PreferenceConstants.BUMPY_ARROWS, true)
        } else if (PreferenceConstants.WIFI_LOCK == key) {
            val lockingWifi = prefs.getBoolean(PreferenceConstants.WIFI_LOCK, true)
            connectivityMonitor.setWantWifiLock(lockingWifi)
        } else if (PreferenceConstants.MEMKEYS == key) {
            updateSavingKeys()
        }
    }

    /**
     * Allow [TerminalBridge] to resize when the parent has changed.
     * @param resizeAllowed
     */
    fun setResizeAllowed(resizeAllowed: Boolean) {
        this.resizeAllowed = resizeAllowed
    }

    fun isResizeAllowed(): Boolean = resizeAllowed

    class KeyHolder {
        var pubkey: Pubkey? = null
        var pair: KeyPair? = null
        var openSSHPubkey: ByteArray? = null

        // For biometric keys stored in Android Keystore
        var keystoreAlias: String? = null
        var isBiometricKey: Boolean = false

        // Job to auto-expire biometric keys after auth window closes
        var expiryJob: Job? = null
    }

    /**
     * Called when connectivity to the network is lost.
     * Instead of immediate disconnect, starts grace period for all bridges.
     */
    fun onConnectivityLost() {
        Timber.d("Network lost - starting grace period for all network bridges")
        scope.launch(dispatchers.io) {
            synchronized(_bridges) {
                for (bridge in _bridges) {
                    if (bridge.isUsingNetwork()) {
                        bridge.onNetworkLost()
                    }
                }
            }
        }
    }

    /**
     * Called when connectivity to the network is restored.
     * Checks IP addresses and either resumes or reconnects bridges.
     */
    fun onConnectivityRestored() {
        Timber.d("Network restored - checking IP addresses for grace period bridges")
        scope.launch(dispatchers.io) {
            val newNetworkInfo = connectivityMonitor.getCurrentNetworkInfo()

            if (newNetworkInfo == null) {
                Timber.w("Network restored but no network info available")
                return@launch
            }

            // Notify bridges in grace period
            synchronized(_bridges) {
                for (bridge in _bridges) {
                    if (bridge.isInGracePeriod()) {
                        bridge.onNetworkRestored(newNetworkInfo)
                    }
                }
            }

            // Also handle normal pending reconnects (for already-disconnected bridges)
            reconnectPending()
        }
    }

    /**
     * Insert request into reconnect queue to be executed either immediately
     * or later when connectivity is restored depending on whether we're
     * currently connected.
     *
     * @param bridge the TerminalBridge to reconnect when possible
     */
    fun requestReconnect(bridge: TerminalBridge) {
        synchronized(pendingReconnect) {
            pendingReconnect.add(WeakReference(bridge))
            if (!bridge.isUsingNetwork() ||
                connectivityMonitor.getCurrentNetworkInfo()?.isConnected == true
            ) {
                reconnectPending()
            }
        }
    }

    /**
     * Reconnect all bridges that were pending a reconnect when connectivity
     * was lost.
     */
    private fun reconnectPending() {
        synchronized(pendingReconnect) {
            for (ref in pendingReconnect) {
                val bridge = ref.get() ?: continue
                bridge.startConnection()
            }
            pendingReconnect.clear()
        }
    }

    private fun notifyHostStatusChanged() {
        scope.launch {
            _hostStatusChanged.emit(Unit)
        }
    }

    companion object {
        const val TAG = "CB.TerminalManager"

        const val VIBRATE_DURATION: Long = 30

        // Must match AUTH_VALIDITY_DURATION_SECONDS in BiometricKeyManager
        const val BIOMETRIC_AUTH_VALIDITY_SECONDS = 30
    }
}
