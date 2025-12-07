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
import android.content.Context
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
import androidx.preference.PreferenceManager
import android.util.Log

import java.io.IOException
import java.lang.ref.WeakReference
import java.security.KeyPair
import java.util.Arrays
import java.util.concurrent.atomic.AtomicLong

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

import org.connectbot.R
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Pubkey
import org.connectbot.transport.TransportFactory
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.ProviderLoader
import org.connectbot.util.ProviderLoaderListener
import org.connectbot.util.PubkeyUtils

/**
 * Manager for SSH connections that runs as a service. This service holds a list
 * of currently connected SSH bridges that are ready for connection up to a GUI
 * if needed.
 */
class TerminalManager : Service(), BridgeDisconnectedListener, OnSharedPreferenceChangeListener, ProviderLoaderListener {

	private val _bridges = ArrayList<TerminalBridge>()
	private val _bridgesFlow = MutableStateFlow<List<TerminalBridge>>(emptyList())
	val bridgesFlow: StateFlow<List<TerminalBridge>> = _bridgesFlow.asStateFlow()

	@Deprecated("Use bridgesFlow instead", ReplaceWith("bridgesFlow.value"))
	val bridges: List<TerminalBridge>
		get() = _bridges.toList()

	private val hostBridgeMap: MutableMap<Host, WeakReference<TerminalBridge>> = HashMap()
	private val nicknameBridgeMap: MutableMap<String, WeakReference<TerminalBridge>> = HashMap()

	private val _disconnected = ArrayList<Host>()
	private val _disconnectedFlow = MutableStateFlow<List<Host>>(emptyList())
	val disconnectedFlow: StateFlow<List<Host>> = _disconnectedFlow.asStateFlow()

	@Deprecated("Use disconnectedFlow instead", ReplaceWith("disconnectedFlow.value"))
	val disconnected: List<Host>
		get() = _disconnected.toList()

	private var disconnectListener: BridgeDisconnectedListener? = null

	fun setDisconnectListener(listener: BridgeDisconnectedListener?) {
		disconnectListener = listener
	}

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

	internal val loadedKeypairs: MutableMap<String, KeyHolder> = HashMap()

	internal lateinit var res: Resources

	internal lateinit var hostRepository: HostRepository
	internal lateinit var colorRepository: ColorSchemeRepository
	internal var pubkeyRepository: PubkeyRepository? = null

	internal lateinit var prefs: SharedPreferences

	private val binder: IBinder = TerminalBinder()

	private lateinit var connectivityManager: ConnectivityReceiver

	private var mediaPlayer: MediaPlayer? = null

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private var idleJob: Job? = null
	private val IDLE_TIMEOUT: Long = 300000 // 5 minutes

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
		Log.i(TAG, "Starting service")

		prefs = PreferenceManager.getDefaultSharedPreferences(this)
		prefs.registerOnSharedPreferenceChangeListener(this)

		res = resources

		hostRepository = HostRepository.get(this)
		colorRepository = ColorSchemeRepository.get(this)
		pubkeyRepository = PubkeyRepository.get(this)

		// load all marked pubkeys into memory
		updateSavingKeys()
		scope.launch(Dispatchers.IO) {
			try {
				val pubkeys = pubkeyRepository!!.getStartupKeys()
				for (pubkey in pubkeys) {
					try {
						val pair = PubkeyUtils.convertToKeyPair(pubkey, null)
						if (pair != null) {
							addKey(pubkey, pair)
						} else {
							Log.w(TAG, String.format("Failed to convert key '%s' to KeyPair", pubkey.nickname))
							_serviceErrors.emit(
								ServiceError.KeyLoadFailed(
									keyName = pubkey.nickname,
									reason = "Failed to convert key to KeyPair"
								)
							)
						}
					} catch (e: Exception) {
						Log.w(TAG, String.format("Problem adding key '%s' to in-memory cache", pubkey.nickname), e)
						_serviceErrors.emit(
							ServiceError.KeyLoadFailed(
								keyName = pubkey.nickname,
								reason = e.message ?: "Unknown error loading key"
							)
						)
					}
				}
			} catch (e: Exception) {
				Log.e(TAG, "Failed to load startup keys", e)
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
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            // Pre-API 31 uses direct Vibrator service
            @Suppress("Deprecation")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        wantKeyVibration = prefs.getBoolean(PreferenceConstants.BUMPY_ARROWS, true)

		wantBellVibration = prefs.getBoolean(PreferenceConstants.BELL_VIBRATE, true)
		enableMediaPlayer()

		hardKeyboardHidden = (res.configuration.hardKeyboardHidden ==
			Configuration.HARDKEYBOARDHIDDEN_YES)

		val lockingWifi = prefs.getBoolean(PreferenceConstants.WIFI_LOCK, true)

		connectivityManager = ConnectivityReceiver(this, lockingWifi)

		ProviderLoader.load(this, this)
	}

	private fun updateSavingKeys() {
		savingKeys = prefs.getBoolean(PreferenceConstants.MEMKEYS, true)
	}

	override fun onDestroy() {
		Log.i(TAG, "Destroying service")

		disconnectAll(true, false)

		pubkeyRepository = null

		stopIdleTimer()
		scope.cancel()

		connectivityManager.cleanup()

		ConnectionNotifier.instance.hideRunningNotification(this)

		disableMediaPlayer()
	}

	/**
	 * Generate a unique negative ID for a temporary host.
	 * Temporary hosts use negative IDs to distinguish them from database hosts.
	 */
	private fun generateTemporaryHostId(): Long {
		return nextTemporaryHostId.getAndDecrement()
	}

	/**
	 * Disconnect all currently connected bridges.
	 */
	fun disconnectAll(immediate: Boolean, excludeLocal: Boolean) {
		var tmpBridges: Array<TerminalBridge>? = null

		synchronized(_bridges) {
			if (bridges.size > 0) {
				tmpBridges = bridges.toTypedArray()
			}
		}

		if (tmpBridges != null) {
			// disconnect and dispose of any existing bridges
			for (tmpBridge in tmpBridges) {
				if (excludeLocal && !tmpBridge.isUsingNetwork())
					continue
				tmpBridge.dispatchDisconnect(immediate)
			}
		}
	}

	/**
	 * Open a new SSH session using the given parameters.
	 */
	private fun openConnection(host: Host): TerminalBridge {
		// throw exception if terminal already open
		if (getConnectedBridge(host) != null) {
			throw IllegalArgumentException("Connection already open for that nickname")
		}

		val bridge = TerminalBridge(this, host)
		bridge.setOnDisconnectedListener(this)
		bridge.startConnection()

		synchronized(_bridges) {
			_bridges.add(bridge)
			val wr = WeakReference(bridge)
			hostBridgeMap[bridge.host] = wr
			nicknameBridgeMap[bridge.host.nickname] = wr
			_bridgesFlow.value = _bridges.toList()
		}

		synchronized(_disconnected) {
			_disconnected.remove(bridge.host)
			_disconnectedFlow.value = _disconnected.toList()
		}

		if (bridge.isUsingNetwork()) {
			connectivityManager.incRef()
		}

		if (prefs.getBoolean(PreferenceConstants.CONNECTION_PERSIST, true)) {
			ConnectionNotifier.instance.showRunningNotification(this)
		}

		// also update database with new connected time
		touchHost(host)

		notifyHostStatusChanged()

		return bridge
	}

	fun getEmulation(): String {
		return prefs.getString(PreferenceConstants.EMULATION, "xterm-256color")!!
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
		scope.launch(Dispatchers.IO) {
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
	fun getConnectedBridge(nickname: String?): TerminalBridge? {
        val wr = nicknameBridgeMap[nickname ?: return null]
        return wr?.get()
	}

	/**
	 * Called by child bridge when somehow it's been disconnected.
	 */
	override fun onDisconnected(bridge: TerminalBridge) {
		var shouldHideRunningNotification = false
		Log.d(TAG, "Bridge Disconnected. Removing it.")

		synchronized(_bridges) {
			// remove this bridge from our list
			_bridges.remove(bridge)

			hostBridgeMap.remove(bridge.host)
			nicknameBridgeMap.remove(bridge.host.nickname)

			if (bridge.isUsingNetwork()) {
				connectivityManager.decRef()
			}

			if (bridges.isEmpty() && pendingReconnect.isEmpty()) {
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
			ConnectionNotifier.instance.hideRunningNotification(this)
		}
	}

	fun isKeyLoaded(nickname: String): Boolean {
		return loadedKeypairs.containsKey(nickname)
	}

	fun addKey(pubkey: Pubkey, pair: KeyPair) {
		addKey(pubkey, pair, false)
	}

	fun addKey(pubkey: Pubkey, pair: KeyPair, force: Boolean) {
		if (!savingKeys && !force)
			return

		removeKey(pubkey.nickname)

		val sshPubKey = PubkeyUtils.extractOpenSSHPublic(pair)

		val keyHolder = KeyHolder()
		keyHolder.pubkey = pubkey
		keyHolder.pair = pair
		keyHolder.openSSHPubkey = sshPubKey

		loadedKeypairs[pubkey.nickname] = keyHolder

		// Note: Pubkey entity doesn't have lifetime field yet
		// This functionality may need to be re-added if needed

		Log.d(TAG, String.format("Added key '%s' to in-memory cache", pubkey.nickname))
	}

	/**
	 * Add a biometric key from Android Keystore to in-memory cache.
	 * The PrivateKey from Keystore is a proxy that delegates signing to secure hardware.
	 * Since biometric auth was just completed, the 30-second signing window is active.
	 */
	fun addBiometricKey(pubkey: Pubkey, keystoreAlias: String, publicKey: java.security.PublicKey) {
		removeKey(pubkey.nickname)

		// Get the private key reference from Keystore
		// This is a proxy object - actual signing is done in secure hardware
		val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore")
		keyStore.load(null)
		val privateKey = keyStore.getKey(keystoreAlias, null) as? java.security.PrivateKey

		if (privateKey == null) {
			Log.e(TAG, "Failed to get private key from Keystore for alias: $keystoreAlias")
			return
		}

		// Create a KeyPair that the SSH library can use
		val keyPair = KeyPair(publicKey, privateKey)

		// Extract OpenSSH format public key for SSH authentication
		val sshPubKey = extractOpenSSHPublicKey(publicKey)

		val keyHolder = KeyHolder()
		keyHolder.pubkey = pubkey
		keyHolder.pair = keyPair // KeyPair with Keystore-backed private key
		keyHolder.openSSHPubkey = sshPubKey
		keyHolder.keystoreAlias = keystoreAlias
		keyHolder.isBiometricKey = true

		loadedKeypairs[pubkey.nickname] = keyHolder

		Log.d(TAG, String.format("Added biometric key '%s' to in-memory cache", pubkey.nickname))
	}

	/**
	 * Extract OpenSSH format public key from a PublicKey.
	 */
	private fun extractOpenSSHPublicKey(publicKey: java.security.PublicKey): ByteArray? {
		return try {
			when (publicKey) {
				is java.security.interfaces.RSAPublicKey -> {
					com.trilead.ssh2.signature.RSASHA1Verify.get().encodePublicKey(publicKey)
				}
				is java.security.interfaces.ECPublicKey -> {
					com.trilead.ssh2.signature.ECDSASHA2Verify.getVerifierForKey(publicKey).encodePublicKey(publicKey)
				}
				else -> {
					Log.w(TAG, "Unsupported public key type: ${publicKey.algorithm}")
					null
				}
			}
		} catch (e: Exception) {
			Log.e(TAG, "Failed to extract OpenSSH public key", e)
			null
		}
	}

	fun removeKey(nickname: String): Boolean {
		Log.d(TAG, String.format("Removed key '%s' to in-memory cache", nickname))
		return loadedKeypairs.remove(nickname) != null
	}

	fun removeKey(publicKey: ByteArray): Boolean {
		var nickname: String? = null
		for ((key, value) in loadedKeypairs) {
			if (Arrays.equals(value.openSSHPubkey, publicKey)) {
				nickname = key
				break
			}
		}

		return if (nickname != null) {
			Log.d(TAG, String.format("Removed key '%s' to in-memory cache", nickname))
			removeKey(nickname)
		} else
			false
	}

	fun getKey(nickname: String): KeyPair? {
		return loadedKeypairs[nickname]?.pair
	}

	fun getKeyNickname(publicKey: ByteArray): String? {
		for ((key, value) in loadedKeypairs) {
			if (Arrays.equals(value.openSSHPubkey, publicKey))
				return key
		}
		return null
	}

	private fun stopWithDelay() {
		// TODO add in a way to check whether keys loaded are encrypted and only
		// set timer when we have an encrypted key loaded

		if (loadedKeypairs.size > 0) {
			synchronized(this) {
				idleJob?.cancel()
				idleJob = scope.launch {
					delay(IDLE_TIMEOUT)
					Log.d(TAG, String.format("Stopping service after timeout of ~%d seconds", IDLE_TIMEOUT / 1000))
					stopNow()
				}
			}
		} else {
			Log.d(TAG, "Stopping service immediately")
			stopSelf()
		}
	}

	protected fun stopNow() {
		if (bridges.size == 0) {
			stopSelf()
		}
	}

	@Synchronized
	private fun stopIdleTimer() {
		idleJob?.cancel()
		idleJob = null
	}
//
//	fun getBridges(): ArrayList<TerminalBridge> {
//		return bridges
//	}

	override fun onProviderLoaderSuccess() {
		Log.d(TAG, "Installed crypto provider successfully")
	}

	override fun onProviderLoaderError() {
		Log.e(TAG, "Failure while installing crypto provider")
	}

	inner class TerminalBinder : Binder() {
		fun getService(): TerminalManager {
			return this@TerminalManager
		}
	}

	override fun onBind(intent: Intent): IBinder {
		Log.i(TAG, "Someone bound to TerminalManager with " + bridges.size + " bridges active")
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
		Log.i(TAG, "Someone rebound to TerminalManager with " + bridges.size + " bridges active")
		isUiBound = true
		keepServiceAlive()
		setResizeAllowed(true)
	}

	override fun onUnbind(intent: Intent): Boolean {
		Log.i(TAG, "Someone unbound from TerminalManager with " + bridges.size + " bridges active")

		isUiBound = false
		setResizeAllowed(true)

		if (bridges.isEmpty()) {
			stopWithDelay()
		}

		return true
	}

	fun tryKeyVibrate() {
		if (wantKeyVibration)
			vibrate()
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

		val volume = prefs.getFloat(PreferenceConstants.BELL_VOLUME,
				PreferenceConstants.DEFAULT_BELL_VOLUME)

        val audioAttributes = AudioAttributes.Builder()
            // Use USAGE_NOTIFICATION for sounds that signal an event
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            // Use CONTENT_TYPE_SONIFICATION for non-music/non-speech sounds (like notifications or alarms)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        mediaPlayer?.setAudioAttributes(audioAttributes)

		val file = res.openRawResourceFd(R.raw.bell)
		try {
			mediaPlayer!!.setLooping(false)
			mediaPlayer!!.setDataSource(file.fileDescriptor, file
					.startOffset, file.length)
			file.close()
			mediaPlayer!!.setVolume(volume, volume)
			mediaPlayer!!.prepare()
		} catch (e: IOException) {
			Log.e(TAG, "Error setting up bell media player", e)
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

		if (wantBellVibration)
			vibrate()
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
			ConnectionNotifier.instance.showActivityNotification(this, host)
		}
	}

	override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
			key: String?) {
		if (PreferenceConstants.BELL == key) {
			val wantAudible = sharedPreferences.getBoolean(
					PreferenceConstants.BELL, true)
			if (wantAudible && mediaPlayer == null)
				enableMediaPlayer()
			else if (!wantAudible && mediaPlayer != null)
				disableMediaPlayer()
		} else if (PreferenceConstants.BELL_VOLUME == key) {
			if (mediaPlayer != null) {
				val volume = sharedPreferences.getFloat(
						PreferenceConstants.BELL_VOLUME,
						PreferenceConstants.DEFAULT_BELL_VOLUME)
				mediaPlayer!!.setVolume(volume, volume)
			}
		} else if (PreferenceConstants.BELL_VIBRATE == key) {
			wantBellVibration = sharedPreferences.getBoolean(
					PreferenceConstants.BELL_VIBRATE, true)
		} else if (PreferenceConstants.BUMPY_ARROWS == key) {
			wantKeyVibration = sharedPreferences.getBoolean(
					PreferenceConstants.BUMPY_ARROWS, true)
		} else if (PreferenceConstants.WIFI_LOCK == key) {
			val lockingWifi = prefs.getBoolean(PreferenceConstants.WIFI_LOCK, true)
			connectivityManager.setWantWifiLock(lockingWifi)
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

	fun isResizeAllowed(): Boolean {
		return resizeAllowed
	}

	class KeyHolder {
		var pubkey: Pubkey? = null
		var pair: KeyPair? = null
		var openSSHPubkey: ByteArray? = null
		// For biometric keys stored in Android Keystore
		var keystoreAlias: String? = null
		var isBiometricKey: Boolean = false
	}

	/**
	 * Called when connectivity to the network is lost and it doesn't appear
	 * we'll be getting a different connection any time soon.
	 */
	fun onConnectivityLost() {
		scope.launch(Dispatchers.IO) {
			disconnectAll(false, true)
		}
	}

	/**
	 * Called when connectivity to the network is restored.
	 */
	fun onConnectivityRestored() {
		scope.launch(Dispatchers.IO) {
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
					connectivityManager.isConnected()) {
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
	}
}
