/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Network
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.tmux.TmuxPaneColors
import org.connectbot.service.tmux.TmuxSessionManager
import org.connectbot.service.tmux.TmuxTarget
import org.connectbot.terminal.DelKeyMode
import org.connectbot.terminal.ProgressState
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.terminal.UrlScanScope
import org.connectbot.transport.AbsTransport
import org.connectbot.transport.SSH
import org.connectbot.transport.TransportFactory
import org.connectbot.util.HostConstants
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.commandOutputSnippet
import org.connectbot.util.ProfileStartup
import timber.log.Timber
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class AuthBanner(
    val id: Long,
    val sourceName: String,
    val message: String,
    val urls: List<String>,
    val languageTag: String?,
)

internal class AuthBannerQueue {
    private val authBannerIds = AtomicLong()
    private val _authBanners = MutableStateFlow<List<AuthBanner>>(emptyList())
    val authBanners: StateFlow<List<AuthBanner>> = _authBanners.asStateFlow()

    fun enqueue(
        sourceName: String,
        message: String,
        urls: List<String>,
        languageTag: String?,
    ) {
        if (urls.isEmpty()) return

        val banner = AuthBanner(
            id = authBannerIds.incrementAndGet(),
            sourceName = sourceName,
            message = message,
            urls = urls,
            languageTag = languageTag,
        )
        _authBanners.update { list ->
            if (list.any { it.sourceName == sourceName && it.message == message }) {
                list
            } else {
                (list + banner).takeLast(MAX_AUTH_BANNERS)
            }
        }
    }

    fun dismiss(id: Long) {
        _authBanners.update { list -> list.filterNot { it.id == id } }
    }

    fun dismissFrom(sourceName: String) {
        _authBanners.update { list -> list.filterNot { it.sourceName == sourceName } }
    }

    private companion object {
        private const val MAX_AUTH_BANNERS = 10
    }
}

/**
 * Provides a bridge between a MUD terminal buffer and a possible TerminalView.
 * This separation allows us to keep the TerminalBridge running in a background
 * service. A TerminalView shares down a bitmap that we can use for rendering
 * when available.
 *
 * This class also provides SSH hostkey verification prompting, and password
 * prompting.
 */
@Suppress("DEPRECATION") // for ClipboardManager
class TerminalBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private sealed class TransportOperation {
        data class WriteData(val data: ByteArray) : TransportOperation()
        data class SetDimensions(val columns: Int, val rows: Int, val width: Int, val height: Int) : TransportOperation()
        data object Flush : TransportOperation()
    }

    private val transportOperations = Channel<TransportOperation>(Channel.UNLIMITED)

    var color: IntArray = IntArray(0)

    var defaultFg = HostConstants.DEFAULT_FG_COLOR
    var defaultBg = HostConstants.DEFAULT_BG_COLOR

    // Store color scheme info for reapplication
    private var currentColorSchemeId: Long = -1L
    private var fullColorPalette: IntArray = IntArray(0)

    /** Resolved ARGB color of the terminal's default background. */
    val defaultBackgroundColor: Int
        get() = fullColorPalette.getOrNull(defaultBg) ?: 0xff000000.toInt()

    // Profile observation
    private var currentProfileId: Long? = null
    private var profileObservationJob: Job? = null

    val manager: TerminalManager

    var host: Host

    private val dispatchers: CoroutineDispatchers

    /* package */
    var transport: AbsTransport? = null

    val defaultPaint: Paint

    private var relay: Relay? = null

    /**
     * Native tmux integration for this host, created after the first
     * successful connect when the transport supports exec channels and the
     * host has not disabled tmux. Survives reconnects (it holds the
     * reattach target); torn down in [cleanup].
     */
    var tmux: TmuxSessionManager? = null
        private set

    private val emulation: String?
    private val scrollback: Int
    private var einkMode: Boolean
    private val encoding: String

    /** Font family from profile for terminal display */
    val fontFamily: String?

    /** Force size rows from profile (null = auto-size) */
    var profileForceSizeRows: Int? = null
        private set

    /** Force size columns from profile (null = auto-size) */
    var profileForceSizeColumns: Int? = null
        private set

    /** Startup command from profile, run when a connection is established */
    var profileStartupCommand: String? = null
        private set

    /** How the profile startup command is run (see [Profile.STARTUP_MODE_INJECT]) */
    var profileStartupCommandMode: String = Profile.STARTUP_MODE_INJECT
        private set

    /** Environment variables from profile (one KEY=VALUE per line) */
    var profileEnvironmentVariables: String? = null
        private set

    /** DEL key mode from profile */
    private val _delKeyModeFlow = MutableStateFlow<DelKeyMode>(DelKeyMode.Delete)
    val delKeyModeFlow: StateFlow<DelKeyMode> = _delKeyModeFlow.asStateFlow()

    // Terminal emulator from ConnectBot Terminal library
    val terminalEmulator: TerminalEmulator

    /**
     * Callback invoked to request the text input dialog (e.g., from camera button)
     */
    var onTextInputRequest: (() -> Unit)? = null

    private val _bellEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 10)
    val bellEvents: SharedFlow<Unit> = _bellEvents.asSharedFlow()

    // OSC 133 shell integration: long-running command completions
    private val _commandCompletions = MutableSharedFlow<HostCommandCompletion>(replay = 0, extraBufferCapacity = 10)
    val commandCompletions: SharedFlow<HostCommandCompletion> = _commandCompletions.asSharedFlow()

    private val _networkStatusMessages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 10)
    val networkStatusMessages: SharedFlow<String> = _networkStatusMessages.asSharedFlow()

    private val authBannerQueue = AuthBannerQueue()
    val authBanners: StateFlow<List<AuthBanner>> = authBannerQueue.authBanners

    // Progress state for OSC 9;4 progress reporting
    data class ProgressInfo(val state: ProgressState, val progress: Int)
    private val _progressState = MutableStateFlow<ProgressInfo?>(null)
    val progressState: StateFlow<ProgressInfo?> = _progressState.asStateFlow()

    var disconnected = false
        private set
    var connecting = false
        private set
    var disconnectReason: DisconnectReason = DisconnectReason.UNKNOWN
        private set
    private var awaitingClose = false

    private val reconnectAttemptCounter = AtomicInteger(0)

    /**
     * Consecutive automatic reconnect cycles since the last successful
     * connection. Used by [TerminalManager] to back off between retries.
     */
    val autoReconnectAttempts: Int
        get() = reconnectAttemptCounter.get()

    /**
     * Forget accumulated reconnect failures so the next attempt runs
     * immediately, e.g. when the user explicitly asks to reconnect.
     */
    fun resetAutoReconnectBackoff() {
        reconnectAttemptCounter.set(0)
    }

    private var forcedSize = false

    // Network state tracking for grace period
    private data class NetworkState(
        val ipAddresses: Set<String>,
        val networkId: String,
    )

    private var lastKnownNetworkState: NetworkState? = null
    private var networkGracePeriodJob: Job? = null
    private var inGracePeriod: Boolean = false

    private var keepaliveJob: Job? = null

    private val keyListener: TerminalKeyListener

    var charWidth = -1
    var charHeight = -1
    private var charTop = -1

    private var fontSizeSp: Float = DEFAULT_FONT_SIZE_SP.toFloat()
    private val _fontSizeFlow = MutableStateFlow(-1f)
    val fontSizeFlow: StateFlow<Float> = _fontSizeFlow.asStateFlow()

    @Deprecated("Use fontSizeFlow instead")
    private val fontSizeChangedListeners: MutableList<FontSizeChangedListener>

    /**
     * Flag indicating if we should perform a full-screen redraw during our next
     * rendering pass.
     */
    private var fullRedraw = false

    val promptManager = PromptManager()

    private val disconnectListeners = CopyOnWriteArrayList<BridgeDisconnectedListener>()

    /**
     * Create new terminal bridge with following parameters. We will immediately
     * launch thread to start SSH connection and handle any hostkey verification
     * and password authentication.
     */
    constructor(manager: TerminalManager, host: Host, dispatchers: CoroutineDispatchers) {
        this.manager = manager
        this.host = host
        this.dispatchers = dispatchers

        // Load profile for this host (always returns a profile, defaulting to Default profile)
        val profile = manager.profileRepository.getByIdOrDefaultBlocking(host.profileId)
        currentProfileId = host.profileId

        emulation = profile.emulation
        scrollback = manager.getScrollback()
        einkMode = manager.prefs.getBoolean(PreferenceConstants.EINK_MODE, false)

        // create our default paint; e-ink panels smear soft antialiased edges
        // and fake-bold strokes, so both stay off there
        defaultPaint = Paint()
        defaultPaint.isAntiAlias = !einkMode
        defaultPaint.typeface = Typeface.MONOSPACE
        defaultPaint.isFakeBoldText = !einkMode // more readable?

        fontSizeChangedListeners = mutableListOf()

        // Store encoding and font family from profile for later use
        encoding = profile.encoding
        fontFamily = profile.fontFamily

        // Store force size from profile
        profileForceSizeRows = profile.forceSizeRows
        profileForceSizeColumns = profile.forceSizeColumns

        // Store startup command and environment variables from profile
        profileStartupCommand = profile.startupCommand
        profileStartupCommandMode = profile.startupCommandMode
        profileEnvironmentVariables = profile.environmentVariables

        // Set DEL key mode from profile
        _delKeyModeFlow.value = delKeyModeFromProfile(profile)

        // Use settings from profile
        val initialFontSize = if (profile.fontSize > 0) profile.fontSize else defaultFontSizeSp
        setFontSize(initialFontSize.toFloat())

        // Load color scheme from profile
        currentColorSchemeId = profile.colorSchemeId
        fullColorPalette = manager.colorRepository.getColorsForSchemeBlocking(profile.colorSchemeId)
        val defaults = manager.colorRepository.getDefaultColorsForSchemeBlocking(profile.colorSchemeId)
        defaultFg = defaults[0]
        defaultBg = defaults[1]

        // Get actual RGB colors for the default foreground/background indices
        val defaultFgColor = fullColorPalette[defaultFg]
        val defaultBgColor = fullColorPalette[defaultBg]

        // Initialize TerminalEmulator with colors from scheme
        // Note: We pass the actual RGB colors (not indices) wrapped in Color objects
        terminalEmulator = TerminalEmulatorFactory.create(
            initialRows = 24, // Will be resized when view is attached
            initialCols = 80,
            defaultForeground = Color(defaultFgColor),
            defaultBackground = Color(defaultBgColor),
            onKeyboardInput = { data ->
                transportOperations.trySend(TransportOperation.WriteData(data))
            },
            onBell = {
                scope.launch {
                    _bellEvents.emit(Unit)
                }
                manager.sendActivityNotification(host)
            },
            onResize = {
                transportOperations.trySend(
                    TransportOperation.SetDimensions(it.columns, it.rows, 0, 0),
                )
            },
            onClipboardCopy = { text ->
                // OSC 52 clipboard support - copy remote text to local clipboard
                Timber.i("OSC 52 clipboard copy: ${text.length} chars")
                val clipboard = manager.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
            },
            onProgressChange = { state, progress ->
                // OSC 9;4 progress reporting - update progress state
                Timber.d("OSC 9;4 progress: state=$state, progress=$progress")
                _progressState.value = ProgressInfo(state, progress)
            },
            onCommandFinished = { durationMs ->
                if (meetsCompletionThreshold(durationMs, completionThresholdMs(manager.prefs))) {
                    val snippet = commandOutputSnippet(terminalEmulator)
                    scope.launch {
                        _commandCompletions.emit(HostCommandCompletion(durationMs, snippet))
                    }
                    // Backgrounded case; gated on !isUiBound inside the manager.
                    // Foreground routing happens via the flow in ConsoleViewModel.
                    manager.sendCommandCompletionNotification(host, durationMs, snippet)
                }
            },
        )

        // Apply color scheme to terminal emulator
        val ansiColors = fullColorPalette.sliceArray(0 until 16)
        terminalEmulator.applyColorScheme(ansiColors, defaultFgColor, defaultBgColor)

        val stickyModifierSetting = when (
            manager.prefs.getString(PreferenceConstants.STICKY_MODIFIERS, PreferenceConstants.NO)
        ) {
            PreferenceConstants.ALT -> StickyModifierSetting.ALT
            PreferenceConstants.YES -> StickyModifierSetting.ALL
            else -> StickyModifierSetting.NONE
        }
        keyListener = TerminalKeyListener(TerminalEmulatorKeyDispatcher(terminalEmulator), stickyModifierSetting)

        // Start the transport operation processor to serialize all writes
        startTransportOperationProcessor()

        // Start observing profile changes for live updates
        startProfileObservation()
    }

    /**
     * Processes transport operations serially to maintain strict ordering.
     * This ensures keyboard input and other writes happen in the correct order.
     */
    private fun startTransportOperationProcessor() {
        scope.launch(dispatchers.io) {
            for (operation in transportOperations) {
                try {
                    when (operation) {
                        is TransportOperation.WriteData -> {
                            transport?.write(operation.data)
                        }

                        is TransportOperation.SetDimensions -> {
                            transport?.setDimensions(
                                operation.columns,
                                operation.rows,
                                operation.width,
                                operation.height,
                            )
                        }

                        is TransportOperation.Flush -> {
                            transport?.flush()
                        }
                    }
                } catch (e: IOException) {
                    Timber.e(e, "Error processing transport operation")
                } catch (e: Exception) {
                    Timber.e(e, "Unexpected error processing transport operation")
                }
            }
        }
    }

    /**
     * Start observing profile changes to apply live updates.
     * Observes both profile attribute changes and host profile_id changes.
     */
    private fun startProfileObservation() {
        profileObservationJob = scope.launch {
            // For temporary hosts (negative IDs), only observe the current profile
            if (host.id <= 0) {
                currentProfileId?.let { profileId ->
                    manager.profileRepository.observeById(profileId)
                        .filterNotNull()
                        .collectLatest { profile ->
                            Timber.d("Profile ${profile.id} changed, applying updates")
                            applyProfileSettings(profile)
                        }
                }
                return@launch
            }

            // For saved hosts, observe the host to detect profile_id changes
            // and switch profile observation when it changes
            manager.hostRepository.observeHost(host.id)
                .filterNotNull()
                .map { it.profileId }
                .distinctUntilChanged()
                .collectLatest { newProfileId ->
                    if (newProfileId != currentProfileId) {
                        Timber.d("Host profile changed from $currentProfileId to $newProfileId")
                        currentProfileId = newProfileId
                    }

                    // Observe the current profile for attribute changes
                    val profileToObserve = newProfileId ?: 1L // Default to profile 1 if null
                    manager.profileRepository.observeById(profileToObserve)
                        .filterNotNull()
                        .collectLatest { profile ->
                            Timber.d("Profile ${profile.id} changed, applying updates")
                            applyProfileSettings(profile)
                        }
                }
        }
    }

    /**
     * Apply changes when the host's profile_id changes.
     */
    private suspend fun applyProfileChanges(newProfileId: Long?) {
        val profile = if (newProfileId != null) {
            manager.profileRepository.getById(newProfileId)
        } else {
            null
        } ?: manager.profileRepository.getDefault()

        applyProfileSettings(profile)
    }

    /**
     * Apply profile settings to the terminal.
     */
    private fun applyProfileSettings(profile: org.connectbot.data.entity.Profile) {
        // Apply font size
        val newFontSize = if (profile.fontSize > 0) profile.fontSize else defaultFontSizeSp
        if (newFontSize.toFloat() != fontSizeSp) {
            setFontSize(newFontSize.toFloat())
        }

        // Apply color scheme if changed
        if (profile.colorSchemeId != currentColorSchemeId) {
            currentColorSchemeId = profile.colorSchemeId
            fullColorPalette = manager.colorRepository.getColorsForSchemeBlocking(profile.colorSchemeId)
            val defaults = manager.colorRepository.getDefaultColorsForSchemeBlocking(profile.colorSchemeId)
            defaultFg = defaults[0]
            defaultBg = defaults[1]

            // Apply to terminal emulator
            val defaultFgColor = fullColorPalette[defaultFg]
            val defaultBgColor = fullColorPalette[defaultBg]
            val ansiColors = fullColorPalette.sliceArray(0 until 16)
            terminalEmulator.applyColorScheme(ansiColors, defaultFgColor, defaultBgColor)
        }

        // Update force size from profile
        profileForceSizeRows = profile.forceSizeRows
        profileForceSizeColumns = profile.forceSizeColumns

        // Update startup command and environment variables from profile;
        // these take effect on the next connection
        profileStartupCommand = profile.startupCommand
        profileStartupCommandMode = profile.startupCommandMode
        profileEnvironmentVariables = profile.environmentVariables

        // Update DEL key mode from profile
        _delKeyModeFlow.value = delKeyModeFromProfile(profile)

        // Note: encoding and fontFamily changes require reconnection to take effect
        // as they are deeply integrated into the terminal initialization
    }

    /**
     * Spawn thread to open connection and start login process.
     *
     * Safe to call repeatedly (manual reconnect, connectivity-restored events,
     * scheduled retries): a call is ignored while another attempt is in
     * flight, while the transport is still connected, or once the bridge is
     * being torn down.
     */
    fun startConnection() {
        val newTransport = TransportFactory.getTransport(host.protocol)
        if (newTransport == null) {
            Timber.w("No transport found for ${host.protocol}")
            return
        }

        synchronized(this) {
            if (awaitingClose) {
                Timber.d("Skipping connection attempt for ${host.nickname}: bridge awaiting close")
                return
            }
            if (connecting) {
                Timber.d("Skipping connection attempt for ${host.nickname}: attempt already in progress")
                return
            }
            if (!disconnected && transport?.isConnected() == true) {
                Timber.d("Skipping connection attempt for ${host.nickname}: already connected")
                return
            }
            connecting = true
            // Reset per-attempt state so that a failed attempt goes back
            // through dispatchDisconnect (instead of being swallowed by its
            // reentrancy guard) and can schedule another retry. Swapping the
            // transport under the same lock lets dispatchDisconnect discard
            // stale events from the previous transport atomically.
            disconnected = false
            disconnectReason = DisconnectReason.UNKNOWN
            transport = newTransport
        }

        newTransport.bridge = this
        newTransport.manager = manager
        newTransport.host = host

        // TODO make this more abstract so we don't litter on AbsTransport
        if (newTransport is SSH) {
            newTransport.setCompression(host.compression)
            host.useAuthAgent?.let { newTransport.setUseAuthAgent(it) }
        }
        newTransport.setEmulation(emulation)

        outputLine(manager.res.getString(R.string.terminal_connecting, host.hostname, host.port, host.protocol))

        manager.notifyBridgeStateChanged()

        scope.launch(dispatchers.io) {
            try {
                if (newTransport.canForwardPorts()) {
                    try {
                        for (portForward in manager.hostRepository.getPortForwardsForHost(host.id)) {
                            newTransport.addPortForward(portForward)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load port forwards for ${host.nickname}")
                        manager.reportError(
                            ServiceError.PortForwardLoadFailed(
                                hostNickname = host.nickname,
                                reason = e.message ?: "Failed to load port forwards",
                            ),
                        )
                    }
                }
                Timber.i("Starting connection to ${host.nickname}")
                newTransport.connect()
            } catch (e: Exception) {
                Timber.e(e, "Connection failed for ${host.nickname}")
                manager.reportError(
                    ServiceError.ConnectionFailed(
                        hostNickname = host.nickname,
                        hostname = host.hostname,
                        reason = e.message ?: "Connection failed",
                    ),
                )
                // Route the failure through the disconnect policy so the
                // bridge doesn't stay stuck in the connecting state and a
                // stay-connected host schedules another retry.
                dispatchDisconnect(DisconnectReason.IO_ERROR, newTransport)
            }
        }
    }

    /**
     * @return charset in use by bridge
     */
    val charset: Charset
        get() = relay?.getCharset() ?: Charsets.UTF_8

    /**
     * Sets the encoding used by the terminal. If the connection is live,
     * then the character set is changed for the next read.
     * @param encoding the canonical name of the character encoding
     */
    fun setCharset(encoding: String) {
        relay?.setCharset(encoding)
    }

    /**
     * Convenience method for writing text into the underlying terminal buffer.
     * Should never be called once the session is established.
     */
    fun outputLine(output: String?) {
        if (output == null) return

        if (transport?.isSessionOpen() == true) {
            Timber.e(
                "Session established, cannot use outputLine!",
                IOException("outputLine call traceback"),
            )
        }

        for (line in output.split("\n".toRegex())) {
            var processedLine = line
            if (processedLine.isNotEmpty() && processedLine[processedLine.length - 1] == '\r') {
                processedLine = processedLine.substring(0, processedLine.length - 1)
            }

            terminalEmulator.writeInput((processedLine + "\r\n").encodeToByteArray())
        }
    }

    /**
     * Inject a specific string into this terminal. Used for post-login strings
     * and pasting clipboard.
     */
    fun injectString(string: String?) {
        if (string == null || string.isEmpty()) {
            return
        }

        transportOperations.trySend(
            TransportOperation.WriteData(string.toByteArray(charset(encoding))),
        )
    }

    /**
     * Reply to a received ENQ (0x05) control code with this terminal's
     * answerback string. VT terminals identify themselves this way; we answer
     * with the emulation (TERM) name, matching classic ConnectBot behavior.
     */
    fun sendAnswerback() {
        injectString(emulation)
    }

    /**
     * Enqueue a single byte for transport write. Goes through the same serialized
     * channel as [injectString] so keyboard input cannot interleave with paste data.
     */
    fun sendByte(c: Int) {
        transportOperations.trySend(
            TransportOperation.WriteData(byteArrayOf(c.toByte())),
        )
    }

    /**
     * Enqueue a byte array for transport write. Serialized with all other
     * transport operations (paste, keyboard, resize, flush).
     */
    fun sendBytes(data: ByteArray) {
        if (data.isEmpty()) return
        transportOperations.trySend(TransportOperation.WriteData(data))
    }

    /**
     * Enqueue a flush after any pending writes. Serialized with writes so the
     * flush happens only after preceding bytes have been sent to the transport.
     */
    fun requestFlush() {
        transportOperations.trySend(TransportOperation.Flush)
    }

    /**
     * Request the parent ConsoleScreen to open the floating text input dialog.
     * Called from hardware camera button or other triggers.
     */
    fun requestOpenTextInput() {
        onTextInputRequest?.invoke()
    }

    /**
     * Internal method to request actual PTY terminal once we've finished
     * authentication. If called before authenticated, it will just fail.
     */
    fun onConnected() {
        disconnected = false
        connecting = false
        reconnectAttemptCounter.set(0)

        // Record the connected time only now that the connection actually
        // succeeded, so a doomed attempt never shows as "recently connected".
        manager.touchHost(host)

        // ENQ answerback is handled by Relay (which replies with the emulation
        // name), and DEL-vs-backspace is handled by termlib's KeyboardHandler
        // via delKeyModeFlow.

        if (isSessionOpen) {
            // create thread to relay incoming connection data to buffer
            transport?.let { t ->
                relay = Relay(this, t, dispatchers, encoding)
                scope.launch {
                    relay?.start()
                }
            }
        }

        // force font-size to make sure we resizePTY as needed
        setFontSize(fontSizeSp)

        // send environment variables and the profile startup command, unless
        // the transport already ran them as the session exec command
        if (transport?.executedStartupCommand != true) {
            injectString(
                ProfileStartup.buildInjectString(profileEnvironmentVariables, profileStartupCommand),
            )
        }

        // finally send any post-login string, if requested
        injectString(host.postLogin)

        maybeStartTmux()

        // Capture network state after successful connection
        captureNetworkState()

        startKeepaliveMonitor()

        // Notify manager so the UI recomposes with updated connection state
        manager.notifyBridgeStateChanged()
    }

    /**
     * Starts the periodic liveness probe for this connection so a link that
     * dies silently in the background (NAT/firewall idle drop) is detected
     * promptly and routed through [dispatchDisconnect] instead of lingering
     * until the next read fails hours later.
     */
    private fun startKeepaliveMonitor() {
        keepaliveJob?.cancel()

        val intervalMs = manager.keepaliveIntervalMs()
        // Capture the transport this monitor watches: dispatching with it as
        // the source lets dispatchDisconnect discard the event if a newer
        // connection attempt has already replaced this transport.
        val monitoredTransport = transport ?: return
        if (intervalMs <= 0 || !monitoredTransport.supportsKeepalive()) {
            return
        }

        keepaliveJob = scope.launch {
            KeepaliveMonitor(
                intervalMs = intervalMs,
                isEligible = {
                    !disconnected && !inGracePeriod && monitoredTransport.isConnected()
                },
                sendKeepalive = {
                    runInterruptible(dispatchers.io) { monitoredTransport.sendKeepalive() }
                },
                onDead = {
                    Timber.w("Keepalive failed for ${host.nickname}, treating connection as dead")
                    dispatchDisconnect(DisconnectReason.IO_ERROR, monitoredTransport)
                },
            ).run()
        }
    }

    /**
     * Starts (or resumes, after a reconnect) tmux integration when the
     * transport supports exec channels, the host wants a session, and tmux
     * mode is not OFF for this host.
     */
    private fun maybeStartTmux() {
        if (transport?.canOpenExecChannels() != true) return
        if (!host.wantSession || host.tmuxMode == Host.TMUX_MODE_OFF) return

        val sessionManager = tmux ?: TmuxSessionManager(
            // Resolve the transport at call time: reconnects swap it out.
            channelFactory = { command ->
                (transport ?: throw IOException("transport is gone")).openExecChannel(command)
            },
            scope = scope,
            ioDispatcher = dispatchers.io,
        ).also { created ->
            created.onTargetChanged = { target ->
                scope.launch(dispatchers.io) {
                    runCatching {
                        manager.hostRepository.updateTmuxLastTarget(host.id, target?.encode())
                    }
                }
            }
            created.completionThresholdMs = completionThresholdMs(manager.prefs)
            scope.launch {
                created.commandCompletions.collect { event ->
                    // Backgrounded case; gated on !isUiBound inside the manager.
                    manager.sendTmuxCommandCompletionNotification(host, event)
                }
            }
            tmux = created
        }
        if (fullColorPalette.size >= 16) {
            sessionManager.paneColors = TmuxPaneColors(
                defaultForeground = Color(fullColorPalette[defaultFg]),
                defaultBackground = Color(fullColorPalette[defaultBg]),
                ansiColors = fullColorPalette.sliceArray(0 until 16),
            )
        }
        sessionManager.onTransportConnected(TmuxTarget.decode(host.tmuxLastTarget))
    }

    /**
     * @return whether a session is open or not
     */
    val isSessionOpen: Boolean
        get() {
            if (transport != null) {
                return transport?.isSessionOpen() == true
            }
            return false
        }

    fun setOnDisconnectedListener(disconnectListener: BridgeDisconnectedListener?) {
        disconnectListeners.clear()
        if (disconnectListener != null) {
            disconnectListeners.add(disconnectListener)
        }
    }

    /**
     * Force disconnection of this terminal bridge.
     *
     * [DisconnectReason.USER_REQUESTED] always takes the immediate-close path,
     * even if the bridge already reached the disconnected state — this is how
     * the "Close" button on the reconnect overlay tears down a session that an
     * IO_ERROR already marked disconnected.
     *
     * @param source the transport reporting the disconnect, if any. Events
     *   from a transport that a newer connection attempt has already replaced
     *   are ignored, so a stale transport being torn down cannot kill the new
     *   connection.
     */
    fun dispatchDisconnect(reason: DisconnectReason, source: AbsTransport? = null) {
        val transportToClose: AbsTransport?

        // We don't need to do this multiple times.
        synchronized(this) {
            if (source != null && transport !== source) {
                Timber.d("Ignoring disconnect from stale transport for ${host.nickname}")
                return
            }
            if (disconnected && reason != DisconnectReason.USER_REQUESTED) {
                return
            }

            disconnected = true
            connecting = false
            if (disconnectReason == DisconnectReason.UNKNOWN) {
                disconnectReason = reason
            }

            // Capture the transport belonging to this session under the same
            // lock: a reconnect attempt may replace [transport] before the
            // asynchronous close below runs, and closing the replacement
            // would tear down the new connection.
            transportToClose = transport
        }

        // The connection is going away one way or another; stop probing it.
        keepaliveJob?.cancel()
        keepaliveJob = null

        // Cancel any pending prompts
        promptManager.cancelPrompt()

        // tmux control channels died with the connection; remember where we
        // were so a reconnect can silently reattach
        tmux?.onTransportLost()

        // disconnection request hangs if we havent really connected to a host yet
        // temporary fix is to just spawn disconnection into a thread
        scope.launch(dispatchers.io) {
            transportToClose?.let {
                if (it.isConnected()) {
                    it.close()
                }
            }
        }

        val action = DisconnectPolicy.decide(
            reason = reason,
            quickDisconnect = host.quickDisconnect,
            stayConnected = host.stayConnected,
            reconnectAttempts = autoReconnectAttempts,
            maxReconnectAttempts = manager.reconnectMaxAttempts(),
        )
        when (action) {
            is DisconnectAction.CloseImmediately -> {
                awaitingClose = true
                triggerDisconnectListener()
            }

            is DisconnectAction.AutoReconnect -> {
                reconnectAttemptCounter.incrementAndGet()
                manager.requestReconnect(this, userInitiated = false)
                manager.notifyBridgeStateChanged()
            }

            is DisconnectAction.GiveUpReconnect -> {
                outputLine(manager.res.getString(R.string.terminal_reconnect_gave_up, autoReconnectAttempts))
                manager.notifyBridgeStateChanged()
            }

            is DisconnectAction.ShowReconnectOverlay -> {
                manager.notifyBridgeStateChanged()
            }
        }
    }

    /**
     * Tells the TerminalManager that we can be destroyed now.
     */
    private fun triggerDisconnectListener() {
        Timber.i("Triggering disconnect for ${host.nickname}")
        if (disconnectListeners.isEmpty()) {
            // Clean up even if no listeners
            cleanup()
            return
        }

        // The disconnect listener should be run on the main thread if possible.
        // CopyOnWriteArrayList is safe to iterate even if modified during iteration
        scope.launch(dispatchers.main) {
            for (listener in disconnectListeners) {
                listener.onDisconnected(this@TerminalBridge)
            }
            // Clean up after notifying all listeners
            cleanup()
        }
    }

    @Synchronized
    fun tryKeyVibrate() {
        manager.tryKeyVibrate()
    }

    /**
     * Request a different font size. Will make call to parentChanged() to make
     * sure we resize PTY if needed.
     *
     * @param sizeSp Size of font in sp
     */
    private fun setFontSize(sizeDp: Float) {
        setFontSize(sizeDp, false)
    }

    /**
     * Request a different font size. Will make call to parentChanged() to make
     * sure we resize PTY if needed.
     *
     * @param sizeSp Size of font in sp
     * @param isForced whether the font size was forced
     */
    private fun setFontSize(sizeSp: Float, isForced: Boolean) {
        if (sizeSp <= 0.0) {
            return
        }

        defaultPaint.textSize = sizeSp
        fontSizeSp = sizeSp
        _fontSizeFlow.value = sizeSp

        // read new metrics to get exact pixel dimensions
        val fm = defaultPaint.fontMetrics
        charTop = Math.ceil(fm.top.toDouble()).toInt()

        val widths = FloatArray(1)
        defaultPaint.getTextWidths("X", widths)
        charWidth = Math.ceil(widths[0].toDouble()).toInt()
        charHeight = Math.ceil((fm.descent - fm.top).toDouble()).toInt()

        forcedSize = isForced

        for (ofscl in fontSizeChangedListeners) {
            ofscl.onFontSizeChanged(sizeSp)
        }
        // Note: Font size is now stored in profiles, not hosts.
        // Runtime font size changes are session-only and not persisted.
    }

    /**
     * Clean up resources when bridge is being destroyed.
     * Releases bitmap and clears parent reference to prevent memory leaks.
     */
    fun cleanup() {
        // Cancel grace period if active
        networkGracePeriodJob?.cancel()
        inGracePeriod = false

        keepaliveJob?.cancel()
        keepaliveJob = null

        tmux?.shutdown()
        tmux = null

        profileObservationJob?.cancel()
        transportOperations.close()
        scope.cancel()
    }

    /**
     * @return whether underlying transport can forward ports
     */
    fun canFowardPorts(): Boolean = transport?.canForwardPorts() ?: false

    /**
     * @return whether underlying transport can transfer files
     */
    fun canTransferFiles(): Boolean = transport?.canTransferFiles() ?: false

    /**
     * Adds the [PortForward] to the list.
     * @param portForward the port forward bean to add
     * @return true on successful addition
     */
    fun addPortForward(portForward: PortForward): Boolean = transport?.addPortForward(portForward) ?: false

    /**
     * Removes the [PortForward] from the list.
     * @param portForward the port forward bean to remove
     * @return true on successful removal
     */
    fun removePortForward(portForward: PortForward): Boolean = transport?.removePortForward(portForward) ?: false

    /**
     * @return the list of port forwards
     */
    val portForwards: List<PortForward>
        get() = transport?.getPortForwards().orEmpty()

    /**
     * Enables a port forward member. After calling this method, the port forward should
     * be operational.
     * @param portForward member of our current port forwards list to enable
     * @return true on successful port forward setup
     */
    fun enablePortForward(portForward: PortForward): Boolean {
        return transport?.let {
            if (!it.isConnected()) {
                Timber.i("Attempt to enable port forward while not connected")
                return false
            }
            it.enablePortForward(portForward)
        } ?: false
    }

    /**
     * Disables a port forward member. After calling this method, the port forward should
     * be non-functioning.
     * @param portForward member of our current port forwards list to enable
     * @return true on successful port forward tear-down
     */
    fun disablePortForward(portForward: PortForward): Boolean {
        return transport?.let {
            if (!it.isConnected()) {
                Timber.i("Attempt to disable port forward while not connected")
                return false
            }
            it.disablePortForward(portForward)
        } ?: false
    }

    /**
     * @return whether the TerminalBridge should close
     */
    fun isAwaitingClose(): Boolean = awaitingClose

    /**
     * @return whether this connection had started and subsequently disconnected
     */
    val isDisconnected: Boolean
        get() = disconnected

    val isConnecting: Boolean
        get() = connecting

    fun scanForURLs(): List<String> = terminalEmulator.getUrls(UrlScanScope.CurrentView).map { it.url }

    fun enqueueAuthBanner(sourceName: String, message: String, urls: List<String>, languageTag: String?) = authBannerQueue.enqueue(sourceName, message, urls, languageTag)

    fun dismissAuthBanner(id: Long) = authBannerQueue.dismiss(id)

    fun dismissAuthBannersFrom(sourceName: String) = authBannerQueue.dismissFrom(sourceName)

    /**
     * @return
     */
    fun isUsingNetwork(): Boolean = transport?.usesNetwork() ?: false

    /**
     * Capture current network state when connection established.
     * Called after successful SSH connection.
     */
    fun captureNetworkState() {
        if (!isUsingNetwork()) return

        val localIp = transport?.getLocalIpAddress()
        val networkInfo = manager.connectivityMonitor.getCurrentNetworkInfo()

        lastKnownNetworkState = when {
            localIp != null -> {
                NetworkState(
                    ipAddresses = setOf(localIp),
                    networkId = networkInfo?.networkId ?: "",
                )
            }

            networkInfo != null -> {
                NetworkState(
                    ipAddresses = networkInfo.ipAddresses,
                    networkId = networkInfo.networkId,
                )
            }

            else -> null
        }
        Timber.d("Captured network state: localIp=$localIp, netId=${lastKnownNetworkState?.networkId}")
    }

    /**
     * Called by TerminalManager when network is lost.
     * Starts 60-second grace period instead of immediate disconnect.
     */
    fun onNetworkLost(network: Network, lostIpAddresses: Set<String>) {
        if (!isUsingNetwork() || disconnected) return

        val state = lastKnownNetworkState ?: return

        // Check if we are using this network or one of these IP addresses
        val isAffected = if (state.ipAddresses.isNotEmpty() && lostIpAddresses.isNotEmpty()) {
            state.ipAddresses.intersect(lostIpAddresses).isNotEmpty()
        } else {
            state.networkId == network.toString()
        }

        if (!isAffected) {
            Timber.d("Network $network lost but bridge not affected")
            return
        }

        // Cancel any existing grace period (rapid network changes)
        networkGracePeriodJob?.cancel()

        inGracePeriod = true

        // Show status message to user
        scope.launch { _networkStatusMessages.emit(manager.res.getString(R.string.network_lost_grace_period)) }

        // Start 60-second timer
        networkGracePeriodJob = scope.launch {
            delay(60_000) // 60 seconds

            // Grace period expired without network restoration
            inGracePeriod = false
            lastKnownNetworkState = null
            Timber.i("Network grace period expired")
            _networkStatusMessages.emit(manager.res.getString(R.string.network_grace_period_expired))

            // Trigger normal disconnect flow
            dispatchDisconnect(DisconnectReason.NETWORK_LOST)
        }
    }

    /**
     * Called by TerminalManager when network is restored.
     * Checks if IP address changed to decide reconnect vs resume.
     */
    fun onNetworkRestored(newNetworkInfo: ConnectivityMonitor.NetworkInfo) {
        if (!inGracePeriod) return

        // Cancel grace period timer
        networkGracePeriodJob?.cancel()
        inGracePeriod = false

        val oldState = lastKnownNetworkState

        if (oldState == null) {
            // No previous state - treat as new connection
            scope.launch { _networkStatusMessages.emit(manager.res.getString(R.string.network_restored_no_previous_state)) }
            lastKnownNetworkState = NetworkState(
                ipAddresses = newNetworkInfo.ipAddresses,
                networkId = newNetworkInfo.networkId,
            )
            // Allow connection to continue
            return
        }

        // Check if ANY IP address matches (lenient - handles IPv4/v6 changes)
        val ipMatches = oldState.ipAddresses.intersect(newNetworkInfo.ipAddresses).isNotEmpty()

        if (ipMatches) {
            // Same IP - SSH session should still be alive, resume normally
            scope.launch { _networkStatusMessages.emit(manager.res.getString(R.string.network_restored_same_ip)) }
            lastKnownNetworkState = NetworkState(
                ipAddresses = newNetworkInfo.ipAddresses,
                networkId = newNetworkInfo.networkId,
            )
            // No action needed - connection continues
        } else {
            // IP changed - TCP connection is broken, must reconnect
            scope.launch { _networkStatusMessages.emit(manager.res.getString(R.string.network_restored_ip_changed)) }
            lastKnownNetworkState = null
            dispatchDisconnect(DisconnectReason.NETWORK_LOST)
        }
    }

    /**
     * @return whether bridge is in network grace period
     */
    fun isInGracePeriod(): Boolean = inGracePeriod

    /**
     * @return
     */
    val keyHandler: TerminalKeyListener
        get() = keyListener

    /**
     * Convenience function to increase the font size by a given step.
     */
    fun increaseFontSize() {
        setFontSize(fontSizeSp + FONT_SIZE_STEP, false)
    }

    /**
     * Convenience function to decrease the font size by a given step.
     */
    fun decreaseFontSize() {
        setFontSize(fontSizeSp - FONT_SIZE_STEP, false)
    }

    /** E-ink users sit closer to lower-resolution panels, so default larger. */
    private val defaultFontSizeSp: Int
        get() = if (einkMode) EINK_DEFAULT_FONT_SIZE_SP else DEFAULT_FONT_SIZE_SP

    /**
     * Applies an e-ink mode preference change to this live session so font
     * rendering updates without requiring a reconnect.
     */
    fun setEinkMode(enabled: Boolean) {
        if (einkMode == enabled) {
            return
        }
        einkMode = enabled
        defaultPaint.isAntiAlias = !enabled
        defaultPaint.isFakeBoldText = !enabled
        // Re-measure with the updated paint flags
        setFontSize(fontSizeSp)
    }

    companion object {
        const val TAG = "CB.TerminalBridge"

        private const val DEFAULT_FONT_SIZE_SP = 10
        private const val EINK_DEFAULT_FONT_SIZE_SP = 14
        private const val FONT_SIZE_STEP = 2
    }
}

private fun delKeyModeFromProfile(profile: Profile): DelKeyMode = if (profile.delKey == HostConstants.DELKEY_BACKSPACE) {
    DelKeyMode.Backspace
} else {
    DelKeyMode.Delete
}
