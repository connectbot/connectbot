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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
	val bridges: ArrayList<TerminalBridge>
		get() = _bridges
	var mHostBridgeMap: MutableMap<Host, WeakReference<TerminalBridge>> = HashMap()
	var mNicknameBridgeMap: MutableMap<String, WeakReference<TerminalBridge>> = HashMap()

	val disconnected: MutableList<Host> = ArrayList()

	var disconnectListener: BridgeDisconnectedListener? = null

	private val hostStatusChangedListeners = ArrayList<OnHostStatusChangedListener>()

	var loadedKeypairs: MutableMap<String, KeyHolder> = HashMap()

	lateinit var res: Resources

	internal lateinit var hostRepository: HostRepository
	internal lateinit var colorRepository: ColorSchemeRepository
	internal var pubkeyRepository: PubkeyRepository? = null

	lateinit var prefs: SharedPreferences

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

	private var resizeAllowed = true

	private var savingKeys = false

	val mPendingReconnect: MutableList<WeakReference<TerminalBridge>> = ArrayList()

	var hardKeyboardHidden = false

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
		val pubkeys = pubkeyRepository!!.getStartupKeysBlocking()

		for (pubkey in pubkeys) {
			try {
				val pair = PubkeyUtils.convertToKeyPair(pubkey, null)
				addKey(pubkey, pair)
			} catch (e: Exception) {
				Log.d(TAG, String.format("Problem adding key '%s' to in-memory cache", pubkey.nickname), e)
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
			mHostBridgeMap[bridge.host] = wr
			mNicknameBridgeMap[bridge.host.nickname] = wr
		}

		synchronized(disconnected) {
			disconnected.remove(bridge.host)
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
	fun openConnection(uri: Uri): TerminalBridge {
        val host: Host = (TransportFactory.findHost(hostRepository, uri) ?: TransportFactory.getTransport(uri.scheme!!)?.createHost(uri))!!
		return openConnection(host)
	}

	/**
	 * Open a new connection for a host by its database ID.
	 * Looks up the host from the repository and creates a connection.
	 *
	 * @param hostId the database ID of the host to connect to
	 * @return TerminalBridge for the connection, or null if host not found
	 */
	fun openConnectionForHostId(hostId: Long): TerminalBridge? {
		val host = hostRepository.findHostByIdBlocking(hostId) ?: return null
		return openConnection(host)
	}

	/**
	 * Update the last-connected value for the given nickname by passing through
	 * to [HostRepository].
	 */
	private fun touchHost(host: Host) {
		hostRepository.touchHostBlocking(host)
	}

	/**
	 * Find a connected [TerminalBridge] with the given Host.
	 *
	 * @param host the Host to search for
	 * @return TerminalBridge that uses the Host
	 */
	fun getConnectedBridge(host: Host): TerminalBridge? {
		val wr = mHostBridgeMap[host]
		return wr?.get()
	}

	/**
	 * Find a connected [TerminalBridge] using its nickname.
	 *
	 * @param nickname
	 * @return TerminalBridge that matches nickname
	 */
	fun getConnectedBridge(nickname: String?): TerminalBridge? {
		if (nickname == null) {
			return null
		}
		val wr = mNicknameBridgeMap[nickname]
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

			mHostBridgeMap.remove(bridge.host)
			mNicknameBridgeMap.remove(bridge.host.nickname)

			if (bridge.isUsingNetwork()) {
				connectivityManager.decRef()
			}

			if (bridges.isEmpty() && mPendingReconnect.isEmpty()) {
				shouldHideRunningNotification = true
			}

			// pass notification back up to gui
			if (disconnectListener != null)
				disconnectListener!!.onDisconnected(bridge)
		}

		synchronized(disconnected) {
			disconnected.add(bridge.host)
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
		return if (loadedKeypairs.containsKey(nickname)) {
			val keyHolder = loadedKeypairs[nickname]
			keyHolder!!.pair
		} else
			null
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
		keepServiceAlive()
		setResizeAllowed(true)
	}

	override fun onUnbind(intent: Intent): Boolean {
		Log.i(TAG, "Someone unbound from TerminalManager with " + bridges.size + " bridges active")

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
            val vibrationEffect = VibrationEffect.createOneShot(VIBRATE_DURATION, VibrationEffect.DEFAULT_AMPLITUDE)
            it.vibrate(vibrationEffect)
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
		if (!prefs.getBoolean(PreferenceConstants.BELL_NOTIFICATION, false))
			return

		ConnectionNotifier.instance.showActivityNotification(this, host)
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
		synchronized(mPendingReconnect) {
			mPendingReconnect.add(WeakReference(bridge))
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
		synchronized(mPendingReconnect) {
			for (ref in mPendingReconnect) {
                val bridge = ref.get() ?: continue
                bridge.startConnection()
			}
			mPendingReconnect.clear()
		}
	}

	/**
	 * Register a `listener` that wants to know when a host's status materially changes.
	 * @see .hostStatusChangedListeners
	 */
	fun registerOnHostStatusChangedListener(listener: OnHostStatusChangedListener) {
		if (!hostStatusChangedListeners.contains(listener)) {
			hostStatusChangedListeners.add(listener)
		}
	}

	/**
	 * Unregister a `listener` that wants to know when a host's status materially changes.
	 * @see .hostStatusChangedListeners
	 */
	fun unregisterOnHostStatusChangedListener(listener: OnHostStatusChangedListener) {
		hostStatusChangedListeners.remove(listener)
	}

	private fun notifyHostStatusChanged() {
		for (listener in hostStatusChangedListeners) {
			listener.onHostStatusChanged()
		}
	}

	companion object {
		const val TAG = "CB.TerminalManager"

		const val VIBRATE_DURATION: Long = 30
	}
}
