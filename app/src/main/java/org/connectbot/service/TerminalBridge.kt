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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
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
import kotlinx.coroutines.launch

import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import org.connectbot.transport.AbsTransport
import org.connectbot.transport.SSH
import org.connectbot.transport.TransportFactory
import org.connectbot.util.HostConstants

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
    }

    private val transportOperations = Channel<TransportOperation>(Channel.UNLIMITED)

    var color: IntArray = IntArray(0)

    var defaultFg = HostConstants.DEFAULT_FG_COLOR
    var defaultBg = HostConstants.DEFAULT_BG_COLOR

    val manager: TerminalManager

    var host: Host

    /* package */ var transport: AbsTransport? = null

    val defaultPaint: Paint

    private var relay: Relay? = null

    private val emulation: String?
    private val scrollback: Int

    // Terminal emulator from ConnectBot Terminal library
    val terminalEmulator: TerminalEmulator

    /**
     * Callback invoked when text input dialog is requested (e.g., from camera button)
     */
    var onTextInputRequested: (() -> Unit)? = null

    private val _bellEvents = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 10)
    val bellEvents: SharedFlow<Unit> = _bellEvents.asSharedFlow()

    private var disconnected = false
    private var awaitingClose = false

    private var forcedSize = false

    // Network state tracking for grace period
    private data class NetworkState(
        val ipAddresses: Set<String>,
        val networkId: String
    )

    private var lastKnownNetworkState: NetworkState? = null
    private var networkGracePeriodJob: Job? = null
    private var inGracePeriod: Boolean = false

    private val keyListener: TerminalKeyListener

    var charWidth = -1
    var charHeight = -1
    private var charTop = -1

    private var fontSizeSp: Float = DEFAULT_FONT_SIZE_SP.toFloat()
    private val _fontSizeFlow = MutableStateFlow(-1f)
    val fontSizeFlow: StateFlow<Float> = _fontSizeFlow.asStateFlow()

    @Deprecated("Use fontSizeFlow instead")
    private val fontSizeChangedListeners: MutableList<FontSizeChangedListener>

    private val localOutput: MutableList<String>

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
    constructor(manager: TerminalManager, host: Host) {
        this.manager = manager
        this.host = host

        emulation = manager.getEmulation()
        scrollback = manager.getScrollback()

        // create our default paint
        defaultPaint = Paint()
        defaultPaint.isAntiAlias = true
        defaultPaint.typeface = Typeface.MONOSPACE
        defaultPaint.isFakeBoldText = true // more readable?

        localOutput = mutableListOf()

        fontSizeChangedListeners = mutableListOf()

        var hostFontSizeSp = host.fontSize
        if (hostFontSizeSp <= 0) {
            hostFontSizeSp = DEFAULT_FONT_SIZE_SP
        }
        setFontSize(hostFontSizeSp.toFloat())

        // Load color scheme from host configuration
        val schemeId = host.colorSchemeId
        val fullPalette = manager.colorRepository.getColorsForSchemeBlocking(schemeId)
        val defaults = manager.colorRepository.getDefaultColorsForSchemeBlocking(schemeId)
        defaultFg = defaults[0]
        defaultBg = defaults[1]

        // Initialize TerminalEmulator
        terminalEmulator = TerminalEmulatorFactory.create(
            initialRows = 24,  // Will be resized when view is attached
            initialCols = 80,
            defaultForeground = Color(defaultFg),
            defaultBackground = Color(defaultBg),
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
                    TransportOperation.SetDimensions(it.columns, it.rows, 0, 0)
                )
            },
            onClipboardCopy = { text ->
                // OSC 52 clipboard support - copy remote text to local clipboard
                Log.i(TAG, "OSC 52 clipboard copy: ${text.length} chars")
                val clipboard = manager.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
            }
        )

        // Apply color scheme to terminal emulator
        val ansiColors = fullPalette.sliceArray(0 until 16)
        val defaultFgColor = fullPalette[defaultFg]
        val defaultBgColor = fullPalette[defaultBg]
        terminalEmulator.applyColorScheme(ansiColors, defaultFgColor, defaultBgColor)

        keyListener = TerminalKeyListener(manager, this, host.encoding)

        // Start the transport operation processor to serialize all writes
        startTransportOperationProcessor()
    }

    /**
     * Processes transport operations serially to maintain strict ordering.
     * This ensures keyboard input and other writes happen in the correct order.
     */
    private fun startTransportOperationProcessor() {
        scope.launch(Dispatchers.IO) {
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
                                operation.height
                            )
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error processing transport operation", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error processing transport operation", e)
                }
            }
        }
    }

    /**
     * Spawn thread to open connection and start login process.
     */
    fun startConnection() {
        val newTransport = TransportFactory.getTransport(host.protocol)
        if (newTransport == null) {
            Log.i(TAG, "No transport found for ${host.protocol}")
            return
        }

        transport = newTransport
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

        scope.launch(Dispatchers.IO) {
            try {
                if (newTransport.canForwardPorts()) {
                    try {
                        for (portForward in manager.hostRepository.getPortForwardsForHost(host.id))
                            newTransport.addPortForward(portForward)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load port forwards for ${host.nickname}", e)
                        manager.reportError(
                            ServiceError.PortForwardLoadFailed(
                                hostNickname = host.nickname,
                                reason = e.message ?: "Failed to load port forwards"
                            )
                        )
                    }
                }
                newTransport.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed for ${host.nickname}", e)
                manager.reportError(
                    ServiceError.ConnectionFailed(
                        hostNickname = host.nickname,
                        hostname = host.hostname,
                        reason = e.message ?: "Connection failed"
                    )
                )
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
        keyListener.setCharset(encoding)
    }

    /**
     * Convenience method for writing text into the underlying terminal buffer.
     * Should never be called once the session is established.
     */
    fun outputLine(output: String?) {
        if (output == null) return

        if (transport?.isSessionOpen() == true) {
            Log.e(TAG, "Session established, cannot use outputLine!",
                    IOException("outputLine call traceback"))
        }

        synchronized(localOutput) {
            for (line in output.split("\n".toRegex())) {
                var processedLine = line
                if (processedLine.isNotEmpty() && processedLine[processedLine.length - 1] == '\r') {
                    processedLine = processedLine.substring(0, processedLine.length - 1)
                }

                val s = processedLine + "\r\n"

                localOutput.add(s)

                terminalEmulator.writeInput(s.encodeToByteArray())
            }
        }
    }

    /**
     * Inject a specific string into this terminal. Used for post-login strings
     * and pasting clipboard.
     */
    fun injectString(string: String?) {
        if (string == null || string.isEmpty())
            return

        transportOperations.trySend(
            TransportOperation.WriteData(string.toByteArray(charset(host.encoding)))
        )
    }

    /**
     * Request the parent ConsoleScreen to open the floating text input dialog.
     * Called from hardware camera button or other triggers.
     */
    fun requestOpenTextInput() {
        onTextInputRequested?.invoke()
    }

    /**
     * Internal method to request actual PTY terminal once we've finished
     * authentication. If called before authenticated, it will just fail.
     */
    fun onConnected() {
        disconnected = false

        // We no longer need our local output.
        localOutput.clear()

        // previously tried vt100 and xterm for emulation modes
        // "screen" works the best for color and escape codes
        // TODO(Terminal): send TERM variable in response to VT control code ENQ
//        (buffer as vt320).setAnswerBack(emulation)

        // TODO(Terminal): set whether backspace is del (for local echo?)
//        if (HostConstants.DELKEY_BACKSPACE == host.delKey)
//            (buffer as vt320).setBackspace(vt320.DELETE_IS_BACKSPACE)
//        else
//            (buffer as vt320).setBackspace(vt320.DELETE_IS_DEL)

        if (isSessionOpen) {
            // create thread to relay incoming connection data to buffer
            transport?.let { t ->
                relay = Relay(this, t, host.encoding)
                scope.launch {
                    relay?.start()
                }
            }
        }

        // force font-size to make sure we resizePTY as needed
        setFontSize(fontSizeSp)

        // finally send any post-login string, if requested
        injectString(host.postLogin)

        // Capture network state after successful connection
        captureNetworkState()
    }

    /**
     * @return whether a session is open or not
     */
    val isSessionOpen: Boolean
        get() {
            if (transport != null)
                return transport?.isSessionOpen() == true
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
     */
    fun dispatchDisconnect(immediate: Boolean) {
        // We don't need to do this multiple times.
        synchronized(this) {
            if (disconnected && !immediate)
                return

            disconnected = true
        }

        // Cancel any pending prompts
        promptManager.cancelPrompt()

        // disconnection request hangs if we havent really connected to a host yet
        // temporary fix is to just spawn disconnection into a thread
        scope.launch(Dispatchers.IO) {
            transport?.let {
                if (it.isConnected()) {
                    it.close()
                }
            }
        }

        if (immediate || (host.quickDisconnect && !host.stayConnected)) {
            awaitingClose = true
            triggerDisconnectListener()
        } else {
            run {
                val line = manager.res.getString(R.string.alert_disconnect_msg)
                outputLine("\r\n$line\r\n")
            }
            if (host.stayConnected) {
                manager.requestReconnect(this)
                return
            }
            scope.launch(Dispatchers.IO) {
                val result = requestBooleanPrompt(
                    message = manager.res.getString(R.string.prompt_host_disconnected),
                    instructions = null
                )
                if (result == null || result) {
                    awaitingClose = true
                    triggerDisconnectListener()
                }
            }
        }
    }

    /**
     * Tells the TerminalManager that we can be destroyed now.
     */
    private fun triggerDisconnectListener() {
        if (disconnectListeners.isEmpty()) {
            // Clean up even if no listeners
            cleanup()
            return
        }

        // The disconnect listener should be run on the main thread if possible.
        // CopyOnWriteArrayList is safe to iterate even if modified during iteration
        scope.launch(Dispatchers.Main) {
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

//        // refresh any bitmap with new font size
//        parent?.let { parentChanged(it) }

        for (ofscl in fontSizeChangedListeners) {
            ofscl.onFontSizeChanged(sizeSp)
        }

        // Create updated host with new fontSize
        host = host.withFontSize(sizeSp.toInt())

        if (host.id != 0L) {
            scope.launch(Dispatchers.IO) {
                try {
                    manager.hostRepository.saveHost(host)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save font size for ${host.nickname}", e)
                    manager.reportError(
                        ServiceError.HostSaveFailed(
                            hostNickname = host.nickname,
                            reason = "Failed to save font size: ${e.message}"
                        )
                    )
                }
            }
        }
    }

//    /**
//     * Something changed in our parent [TerminalView], maybe it's a new
//     * parent, or maybe it's an updated font size. We should recalculate
//     * terminal size information and request a PTY resize.
//     */
//    @Synchronized
//    fun parentChanged(parent: TerminalView) {
//        if (!manager.isResizeAllowed()) {
//            Log.d(TAG, "Resize is not allowed now")
//            return
//        }
//
//        this.parent = parent
//        val width = parent.width
//        val height = parent.height
//
//        // Something has gone wrong with our layout; we're 0 width or height!
//        if (width <= 0 || height <= 0)
//            return
//
//        val clipboard = parent.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//        keyListener.setClipboardManager(clipboard)
//
//        if (!forcedSize) {
//            // recalculate buffer size
//            val newColumns: Int
//            val newRows: Int
//
//            newColumns = width / charWidth
//            newRows = height / charHeight
//
//            columns = newColumns
//            rows = newRows
//            refreshOverlayFontSize()
//        }
//
//        // clear out any old buffer information
//        defaultPaint.color = android.graphics.Color.BLACK
//        canvas.drawPaint(defaultPaint)
//
//        // Stroke the border of the terminal if the size is being forced;
//        if (forcedSize) {
//            val borderX = (columns * charWidth) + 1
//            val borderY = (rows * charHeight) + 1
//
//            defaultPaint.color = android.graphics.Color.GRAY
//            defaultPaint.strokeWidth = 0.0f
//            if (width >= borderX)
//                canvas.drawLine(borderX.toFloat(), 0f, borderX.toFloat(), (borderY + 1).toFloat(), defaultPaint)
//            if (height >= borderY)
//                canvas.drawLine(0f, borderY.toFloat(), (borderX + 1).toFloat(), borderY.toFloat(), defaultPaint)
//        }
//
//        try {
//            transport?.setDimensions(columns, rows, width, height)
//        } catch (e: Exception) {
//            Log.e(TAG, "Problem while trying to resize screen or PTY", e)
//        }
//
//        // redraw local output if we don't have a session to receive our resize request
//        if (transport == null) {
//            // TODO(Terminal): write local output directly to display
////            synchronized(localOutput) {
////                (buffer as vt320).reset()
////
////                for (line in localOutput)
////                    (buffer as vt320).putString(line)
////            }
//        }
//
//        parent.notifyUser(String.format("%d x %d", columns, rows))
//
//        Log.i(TAG, String.format("parentChanged() now width=%d, height=%d", columns, rows))
//    }

    /**
     * Clean up resources when bridge is being destroyed.
     * Releases bitmap and clears parent reference to prevent memory leaks.
     */
    fun cleanup() {
        // Cancel grace period if active
        networkGracePeriodJob?.cancel()
        inGracePeriod = false

        transportOperations.close()
        scope.cancel()
    }

    /**
     * @return whether underlying transport can forward ports
     */
    fun canFowardPorts(): Boolean {
        return transport?.canForwardPorts() ?: false
    }

    /**
     * Adds the [PortForward] to the list.
     * @param portForward the port forward bean to add
     * @return true on successful addition
     */
    fun addPortForward(portForward: PortForward): Boolean {
        return transport?.addPortForward(portForward) ?: false
    }

    /**
     * Removes the [PortForward] from the list.
     * @param portForward the port forward bean to remove
     * @return true on successful removal
     */
    fun removePortForward(portForward: PortForward): Boolean {
        return transport?.removePortForward(portForward) ?: false
    }

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
                Log.i(TAG, "Attempt to enable port forward while not connected")
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
                Log.i(TAG, "Attempt to disable port forward while not connected")
                return false
            }
            it.disablePortForward(portForward)
        } ?: false
    }

    /**
     * @return whether the TerminalBridge should close
     */
    fun isAwaitingClose(): Boolean {
        return awaitingClose
    }

    /**
     * @return whether this connection had started and subsequently disconnected
     */
    val isDisconnected: Boolean
        get() = disconnected

    private object PatternHolder {
        val urlPattern: Pattern

        init {
            // based on http://www.ietf.org/rfc/rfc2396.txt
            val scheme = "[A-Za-z][-+.0-9A-Za-z]*"
            val unreserved = "[-._~0-9A-Za-z]"
            val pctEncoded = "%[0-9A-Fa-f]{2}"
            val subDelims = "[!${'$'}&'()*+,;:=]"
            val userinfo = "(?:$unreserved|$pctEncoded|$subDelims|:)*"
            val h16 = "[0-9A-Fa-f]{1,4}"
            val decOctet = "(?:[0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])"
            val ipv4address = "$decOctet\\.$decOctet\\.$decOctet\\.$decOctet"
            val ls32 = "(?:$h16:$h16|$ipv4address)"
            val ipv6address = "(?:(?:$h16){6}$ls32)"
            val ipvfuture = "v[0-9A-Fa-f]+.(?:$unreserved|$subDelims|:)+"
            val ipLiteral = "\\[(?:$ipv6address|$ipvfuture)\\]"
            val regName = "(?:$unreserved|$pctEncoded|$subDelims)*"
            val host = "(?:$ipLiteral|$ipv4address|$regName)"
            val port = "[0-9]*"
            val authority = "(?:$userinfo@)?$host(?::$port)?"
            val pchar = "(?:$unreserved|$pctEncoded|$subDelims|@)"
            val segment = "$pchar*"
            val pathAbempty = "(?:/$segment)*"
            val segmentNz = "$pchar+"
            val pathAbsolute = "/(?:$segmentNz(?:/$segment)*)?"
            val pathRootless = "$segmentNz(?:/$segment)*"
            val hierPart = "(?://$authority$pathAbempty|$pathAbsolute|$pathRootless)"
            val query = "(?:$pchar|/|\\?)*"
            val fragment = "(?:$pchar|/|\\?)*"
            val uriRegex = "$scheme:$hierPart(?:$query)?(?:#$fragment)?"
            urlPattern = Pattern.compile(uriRegex)
        }
    }

    /**
     * @return
     */
    fun scanForURLs(): List<String> {
        // buffer!! is intentional - buffer is initialized in constructor and must be non-null
        val urls = mutableListOf<String>()

        // TODO(Terminal): replace URL scanner
//        val visibleBuffer = CharArray(buffer!!.height * buffer!!.width)
//        for (l in 0 until buffer!!.height)
//            System.arraycopy(buffer!!.charArray[buffer!!.windowBase + l], 0,
//                    visibleBuffer, l * buffer!!.width, buffer!!.width)
//
//        val urlMatcher = PatternHolder.urlPattern.matcher(String(visibleBuffer))
//        while (urlMatcher.find())
//            urls.add(urlMatcher.group())

        return urls
    }

    /**
     * @return
     */
    fun isUsingNetwork(): Boolean {
        return transport?.usesNetwork() ?: false
    }

    /**
     * Capture current network state when connection established.
     * Called after successful SSH connection.
     */
    fun captureNetworkState() {
        if (!isUsingNetwork()) return

        val networkInfo = manager.connectivityMonitor.getCurrentNetworkInfo()
        if (networkInfo != null) {
            lastKnownNetworkState = NetworkState(
                ipAddresses = networkInfo.ipAddresses,
                networkId = networkInfo.networkId
            )
            Log.d(TAG, "Captured network state: ${networkInfo.ipAddresses.size} IPs")
        }
    }

    /**
     * Called by TerminalManager when network is lost.
     * Starts 60-second grace period instead of immediate disconnect.
     */
    fun onNetworkLost() {
        if (!isUsingNetwork() || disconnected) return

        // Cancel any existing grace period (rapid network changes)
        networkGracePeriodJob?.cancel()

        inGracePeriod = true

        // Show status message to user
        outputLine(manager.res.getString(R.string.network_lost_grace_period))

        // Start 60-second timer
        networkGracePeriodJob = scope.launch {
            delay(60_000) // 60 seconds

            // Grace period expired without network restoration
            inGracePeriod = false
            lastKnownNetworkState = null
            outputLine(manager.res.getString(R.string.network_grace_period_expired))

            // Trigger normal disconnect flow
            dispatchDisconnect(immediate = false)
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
            outputLine(manager.res.getString(R.string.network_restored_no_previous_state))
            lastKnownNetworkState = NetworkState(
                ipAddresses = newNetworkInfo.ipAddresses,
                networkId = newNetworkInfo.networkId
            )
            // Allow connection to continue
            return
        }

        // Check if ANY IP address matches (lenient - handles IPv4/v6 changes)
        val ipMatches = oldState.ipAddresses.intersect(newNetworkInfo.ipAddresses).isNotEmpty()

        if (ipMatches) {
            // Same IP - SSH session should still be alive, resume normally
            outputLine(manager.res.getString(R.string.network_restored_same_ip))
            lastKnownNetworkState = NetworkState(
                ipAddresses = newNetworkInfo.ipAddresses,
                networkId = newNetworkInfo.networkId
            )
            // No action needed - connection continues
        } else {
            // IP changed - TCP connection is broken, must reconnect
            outputLine(manager.res.getString(R.string.network_restored_ip_changed))
            lastKnownNetworkState = null
            dispatchDisconnect(immediate = false)
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

    companion object {
        const val TAG = "CB.TerminalBridge"

        private const val DEFAULT_FONT_SIZE_SP = 10
        private const val FONT_SIZE_STEP = 2
    }
}
