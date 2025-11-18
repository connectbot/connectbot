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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import android.text.ClipboardManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

import java.io.IOException
import java.nio.charset.Charset
import java.util.regex.Pattern

import de.mud.terminal.VDUBuffer
import de.mud.terminal.VDUDisplay
import de.mud.terminal.vt320

import org.connectbot.R
import org.connectbot.TerminalView
import org.connectbot.bean.SelectionArea
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
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
class TerminalBridge : VDUDisplay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var displayDensity: Float = 0f
    private var systemFontScale: Float = 0f

    var color: IntArray = IntArray(0)

    var defaultFg = HostConstants.DEFAULT_FG_COLOR
    var defaultBg = HostConstants.DEFAULT_BG_COLOR

    protected val manager: TerminalManager?

    var host: Host

    /* package */ var transport: AbsTransport? = null

    val defaultPaint: Paint

    private var relay: Relay? = null

    private val emulation: String?
    private val scrollback: Int

    var bitmap: Bitmap? = null
    var buffer: VDUBuffer? = null

    private var parent: TerminalView? = null
    private val canvas = Canvas()

    private var disconnected = false
    private var awaitingClose = false

    private var forcedSize = false
    private var columns: Int = 0
    private var rows: Int = 0

    private val keyListener: TerminalKeyListener

    private var selectingForCopy = false
    private val selectionArea: SelectionArea

    var charWidth = -1
    var charHeight = -1
    private var charTop = -1

    private var fontSizeDp = -1f

    private val fontSizeChangedListeners: MutableList<FontSizeChangedListener>

    private val localOutput: MutableList<String>

    /**
     * Flag indicating if we should perform a full-screen redraw during our next
     * rendering pass.
     */
    private var fullRedraw = false

    val promptManager = PromptManager()

    private val disconnectListeners = mutableListOf<BridgeDisconnectedListener>()

    /**
     * Create a new terminal bridge suitable for unit testing.
     */
    constructor() {
        buffer = object : vt320() {
            override fun write(b: ByteArray) {}
            override fun write(b: Int) {}
            override fun sendTelnetCommand(cmd: Byte) {}
            override fun setWindowSize(c: Int, r: Int) {}
            override fun debug(s: String) {}
        }

        emulation = null
        manager = null

        displayDensity = 1f

        defaultPaint = Paint()

        selectionArea = SelectionArea()
        scrollback = 1

        localOutput = mutableListOf()

        fontSizeChangedListeners = mutableListOf()

        transport = null

        host = Host()

        keyListener = TerminalKeyListener(null, this, buffer!!, null)
    }

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

        refreshOverlayFontSize()

        localOutput = mutableListOf()

        fontSizeChangedListeners = mutableListOf()

        var hostFontSizeDp = host.fontSize
        if (hostFontSizeDp <= 0) {
            hostFontSizeDp = DEFAULT_FONT_SIZE_DP
        }
        setFontSize(hostFontSizeDp.toFloat())

        // create terminal buffer and handle outgoing data
        // this is probably status reply information
        buffer = object : vt320() {
            override fun debug(s: String) {
                Log.d(TAG, s)
            }

            override fun write(b: ByteArray) {
                try {
                    if (transport != null)
                        transport!!.write(b)
                } catch (e: IOException) {
                    Log.e(TAG, "Problem writing outgoing data in vt320() thread", e)
                }
            }

            override fun write(b: Int) {
                try {
                    if (transport != null)
                        transport!!.write(b)
                } catch (e: IOException) {
                    Log.e(TAG, "Problem writing outgoing data in vt320() thread", e)
                }
            }

            // We don't use telnet sequences.
            override fun sendTelnetCommand(cmd: Byte) {
            }

            // We don't want remote to resize our window.
            override fun setWindowSize(c: Int, r: Int) {
            }

            override fun beep() {
                if (parent?.isShown == true)
                    manager.playBeep()
                else
                    manager.sendActivityNotification(host)
            }
        }

        // Don't keep any scrollback if a session is not being opened.
        if (host.wantSession)
            buffer!!.setBufferSize(scrollback)
        else
            buffer!!.setBufferSize(0)

        resetColors()
        buffer!!.setDisplay(this)

        selectionArea = SelectionArea()

        keyListener = TerminalKeyListener(manager, this, buffer!!, host.encoding)
    }

    /**
     * Spawn thread to open connection and start login process.
     */
    fun startConnection() {
        transport = TransportFactory.getTransport(host.protocol)
        if (transport == null) {
            Log.i(TAG, "No transport found for ${host.protocol}")
            return
        }

        transport!!.bridge = this
        transport!!.manager = manager
        transport!!.host = host

        // TODO make this more abstract so we don't litter on AbsTransport
        if (transport is SSH) {
            (transport as SSH).setCompression(host.compression)
            host.useAuthAgent?.let { (transport as SSH).setUseAuthAgent(it) }
        }
        transport!!.setEmulation(emulation)

        outputLine(manager!!.res.getString(R.string.terminal_connecting, host.hostname, host.port, host.protocol))

        scope.launch(Dispatchers.IO) {
            try {
                if (transport!!.canForwardPorts()) {
                    try {
                        for (portForward in manager!!.hostRepository.getPortForwardsForHost(host.id))
                            transport!!.addPortForward(portForward)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load port forwards for ${host.nickname}", e)
                        manager?.reportError(
                            ServiceError.PortForwardLoadFailed(
                                hostNickname = host.nickname,
                                reason = e.message ?: "Failed to load port forwards"
                            )
                        )
                    }
                }
                transport!!.connect()
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed for ${host.nickname}", e)
                manager?.reportError(
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
        get() = relay!!.getCharset()!!

    /**
     * Sets the encoding used by the terminal. If the connection is live,
     * then the character set is changed for the next read.
     * @param encoding the canonical name of the character encoding
     */
    fun setCharset(encoding: String) {
        if (relay != null)
            relay!!.setCharset(encoding)
        keyListener.setCharset(encoding)
    }

    /**
     * Convenience method for writing text into the underlying terminal buffer.
     * Should never be called once the session is established.
     */
    fun outputLine(output: String?) {
        if (output == null) return
        
        if (transport != null && transport!!.isSessionOpen()) {
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

                (buffer as vt320).putString(s)

                // For accessibility
                val charArray = s.toCharArray()
                propagateConsoleText(charArray, charArray.size)
            }
        }
    }

    /**
     * Only intended for pre-Honeycomb devices.
     */
    fun setSelectingForCopy(selectingForCopy: Boolean) {
        this.selectingForCopy = selectingForCopy
    }

    /**
     * Only intended for pre-Honeycomb devices.
     */
    fun isSelectingForCopy(): Boolean {
        return selectingForCopy
    }

    /**
     * Only intended for pre-Honeycomb devices.
     */
    fun getSelectionArea(): SelectionArea {
        return selectionArea
    }

    fun copyCurrentSelection() {
        if (parent != null) {
            parent!!.copyCurrentSelectionToClipboard()
        }
    }

    /**
     * Inject a specific string into this terminal. Used for post-login strings
     * and pasting clipboard.
     */
    fun injectString(string: String?) {
        if (string == null || string.isEmpty())
            return

        scope.launch(Dispatchers.IO) {
            try {
                transport!!.write(string.toByteArray(charset(host.encoding)))
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't inject string to remote host: ", e)
            }
        }
    }

    /**
     * Internal method to request actual PTY terminal once we've finished
     * authentication. If called before authenticated, it will just fail.
     */
    fun onConnected() {
        disconnected = false

        (buffer as vt320).reset()

        // We no longer need our local output.
        localOutput.clear()

        // previously tried vt100 and xterm for emulation modes
        // "screen" works the best for color and escape codes
        (buffer as vt320).setAnswerBack(emulation)

        if (HostConstants.DELKEY_BACKSPACE == host.delKey)
            (buffer as vt320).setBackspace(vt320.DELETE_IS_BACKSPACE)
        else
            (buffer as vt320).setBackspace(vt320.DELETE_IS_DEL)

        if (isSessionOpen) {
            // create thread to relay incoming connection data to buffer
            relay = Relay(this, transport!!, buffer as vt320, host.encoding)
            scope.launch {
                relay?.start()
            }
        }

        // force font-size to make sure we resizePTY as needed
        setFontSize(fontSizeDp)

        // finally send any post-login string, if requested
        injectString(host.postLogin)
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
        synchronized(disconnectListeners) {
            disconnectListeners.clear()
            if (disconnectListener != null) {
                disconnectListeners.add(disconnectListener)
            }
        }
    }

    fun addOnDisconnectedListener(listener: BridgeDisconnectedListener?) {
        synchronized(disconnectListeners) {
            if (listener != null && !disconnectListeners.contains(listener)) {
                disconnectListeners.add(listener)
            }
        }
    }

    fun removeOnDisconnectedListener(listener: BridgeDisconnectedListener?) {
        synchronized(disconnectListeners) {
            disconnectListeners.remove(listener)
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
            if (transport != null && transport!!.isConnected())
                transport!!.close()
        }

        if (immediate || (host.quickDisconnect && !host.stayConnected)) {
            awaitingClose = true
            triggerDisconnectListener()
        } else {
            run {
                val line = manager!!.res.getString(R.string.alert_disconnect_msg)
                (buffer as vt320).putString("\r\n$line\r\n")
            }
            if (host.stayConnected) {
                manager?.requestReconnect(this)
                return
            }
            scope.launch(Dispatchers.IO) {
                val result = requestBooleanPrompt(
                    message = manager!!.res.getString(R.string.prompt_host_disconnected),
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
        val listenersCopy: List<BridgeDisconnectedListener>
        synchronized(disconnectListeners) {
            if (disconnectListeners.isEmpty()) {
                return
            }
            listenersCopy = ArrayList(disconnectListeners)
        }

        // The disconnect listener should be run on the main thread if possible.
        scope.launch(Dispatchers.Main) {
            for (listener in listenersCopy) {
                listener.onDisconnected(this@TerminalBridge)
            }
        }
    }

    @Synchronized
    fun tryKeyVibrate() {
        manager!!.tryKeyVibrate()
    }

    /**
     * Request a different font size. Will make call to parentChanged() to make
     * sure we resize PTY if needed.
     *
     * @param sizeDp Size of font in dp
     */
    private fun setFontSize(sizeDp: Float) {
        setFontSize(sizeDp, false)
    }

    /**
     * Request a different font size. Will make call to parentChanged() to make
     * sure we resize PTY if needed.
     *
     * @param sizeDp Size of font in dp
     * @param isForced whether the font size was forced
     */
    private fun setFontSize(sizeDp: Float, isForced: Boolean) {
        if (sizeDp <= 0.0) {
            return
        }

        val fontSizePx = (sizeDp * displayDensity * systemFontScale + 0.5f).toInt()

        defaultPaint.textSize = fontSizePx.toFloat()
        fontSizeDp = sizeDp

        // read new metrics to get exact pixel dimensions
        val fm = defaultPaint.fontMetrics
        charTop = Math.ceil(fm.top.toDouble()).toInt()

        val widths = FloatArray(1)
        defaultPaint.getTextWidths("X", widths)
        charWidth = Math.ceil(widths[0].toDouble()).toInt()
        charHeight = Math.ceil((fm.descent - fm.top).toDouble()).toInt()

        forcedSize = isForced

        // refresh any bitmap with new font size
        if (parent != null) {
            parentChanged(parent!!)
        }

        for (ofscl in fontSizeChangedListeners) {
            ofscl.onFontSizeChanged(sizeDp)
        }

        // Create updated host with new fontSize
        host = host.withFontSize(sizeDp.toInt())

        if (host.id != 0L) {
            scope.launch(Dispatchers.IO) {
                try {
                    manager!!.hostRepository.saveHost(host)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save font size for ${host.nickname}", e)
                    manager?.reportError(
                        ServiceError.HostSaveFailed(
                            hostNickname = host.nickname,
                            reason = "Failed to save font size: ${e.message}"
                        )
                    )
                }
            }
        }
    }

    /**
     * @return current text size in pixels
     */
    val textSizePx: Float
        get() = defaultPaint.textSize

    /**
     * Add an [FontSizeChangedListener] to the list of listeners for this
     * bridge.
     *
     * @param listener
     *            listener to add
     */
    fun addFontSizeChangedListener(listener: FontSizeChangedListener) {
        fontSizeChangedListeners.add(listener)
    }

    /**
     * Remove an [FontSizeChangedListener] from the list of listeners for
     * this bridge.
     *
     * @param listener
     */
    fun removeFontSizeChangedListener(listener: FontSizeChangedListener) {
        fontSizeChangedListeners.remove(listener)
    }

    /**
     * Something changed in our parent [TerminalView], maybe it's a new
     * parent, or maybe it's an updated font size. We should recalculate
     * terminal size information and request a PTY resize.
     */
    @Synchronized
    fun parentChanged(parent: TerminalView) {
        if (manager != null && !manager.isResizeAllowed()) {
            Log.d(TAG, "Resize is not allowed now")
            return
        }

        this.parent = parent
        val width = parent.width
        val height = parent.height

        // Something has gone wrong with our layout; we're 0 width or height!
        if (width <= 0 || height <= 0)
            return

        val clipboard = parent.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        keyListener.setClipboardManager(clipboard)

        if (!forcedSize) {
            // recalculate buffer size
            val newColumns: Int
            val newRows: Int

            newColumns = width / charWidth
            newRows = height / charHeight

            // If nothing has changed in the terminal dimensions and not an intial
            // draw then don't blow away scroll regions and such.
            if (newColumns == columns && newRows == rows)
                return

            columns = newColumns
            rows = newRows
            refreshOverlayFontSize()
        }

        // reallocate new bitmap if needed
        var newBitmap = (bitmap == null)
        if (bitmap != null)
            newBitmap = (bitmap!!.width != width || bitmap!!.height != height)

        if (newBitmap) {
            discardBitmap()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            canvas.setBitmap(bitmap)
        }

        // clear out any old buffer information
        defaultPaint.color = Color.BLACK
        canvas.drawPaint(defaultPaint)

        // Stroke the border of the terminal if the size is being forced;
        if (forcedSize) {
            val borderX = (columns * charWidth) + 1
            val borderY = (rows * charHeight) + 1

            defaultPaint.color = Color.GRAY
            defaultPaint.strokeWidth = 0.0f
            if (width >= borderX)
                canvas.drawLine(borderX.toFloat(), 0f, borderX.toFloat(), (borderY + 1).toFloat(), defaultPaint)
            if (height >= borderY)
                canvas.drawLine(0f, borderY.toFloat(), (borderX + 1).toFloat(), borderY.toFloat(), defaultPaint)
        }

        try {
            // request a terminal pty resize
            synchronized(buffer!!) {
                buffer!!.setScreenSize(columns, rows, true)
            }

            if (transport != null)
                transport!!.setDimensions(columns, rows, width, height)
        } catch (e: Exception) {
            Log.e(TAG, "Problem while trying to resize screen or PTY", e)
        }

        // redraw local output if we don't have a sesson to receive our resize request
        if (transport == null) {
            synchronized(localOutput) {
                (buffer as vt320).reset()

                for (line in localOutput)
                    (buffer as vt320).putString(line)
            }
        }

        // force full redraw with new buffer size
        fullRedraw = true
        redraw()

        parent.notifyUser(String.format("%d x %d", columns, rows))

        Log.i(TAG, String.format("parentChanged() now width=%d, height=%d", columns, rows))
    }

    /**
     * Somehow our parent [TerminalView] was destroyed. Now we don't need
     * to redraw anywhere, and we can recycle our internal bitmap.
     */
    @Synchronized
    fun parentDestroyed() {
        parent = null
        discardBitmap()
    }

    private fun discardBitmap() {
        if (bitmap != null)
            bitmap!!.recycle()
        bitmap = null
    }

    override fun setVDUBuffer(buffer: VDUBuffer) {
        this.buffer = buffer
    }

    override fun getVDUBuffer(): VDUBuffer? {
        return buffer
    }

    fun propagateConsoleText(rawText: CharArray, length: Int) {
        if (parent != null) {
            parent!!.propagateConsoleText(rawText, length)
        }
    }

    fun onDraw() {
        var fg: Int
        var bg: Int
        synchronized(buffer!!) {
            val entireDirty = buffer!!.update[0] || fullRedraw
            var isWideCharacter = false

            // walk through all lines in the buffer
            for (l in 0 until buffer!!.height) {

                // check if this line is dirty and needs to be repainted
                // also check for entire-buffer dirty flags
                if (!entireDirty && !buffer!!.update[l + 1]) continue

                // reset dirty flag for this line
                buffer!!.update[l + 1] = false

                // walk through all characters in this line
                var c = 0
                while (c < buffer!!.width) {
                    var addr = 0
                    val currAttr = buffer!!.charAttributes[buffer!!.windowBase + l][c]

                    run {
                        var fgcolor = defaultFg
                        var bgcolor = defaultBg

                        // check if foreground color attribute is set
                        if ((currAttr and VDUBuffer.COLOR_FG) != 0L)
                            fgcolor = ((currAttr and VDUBuffer.COLOR_FG) shr VDUBuffer.COLOR_FG_SHIFT).toInt() - 1

                        if (fgcolor < 8 && (currAttr and VDUBuffer.BOLD) != 0L)
                            fg = color[fgcolor + 8]
                        else if (fgcolor < 256)
                            fg = color[fgcolor]
                        else
                            fg = 0xff000000.toInt() or (fgcolor - 256)

                        // check if background color attribute is set
                        if ((currAttr and VDUBuffer.COLOR_BG) != 0L)
                            bgcolor = ((currAttr and VDUBuffer.COLOR_BG) shr VDUBuffer.COLOR_BG_SHIFT).toInt() - 1

                        if (bgcolor < 256)
                            bg = color[bgcolor]
                        else
                            bg = 0xff000000.toInt() or (bgcolor - 256)
                    }

                    // support character inversion by swapping background and foreground color
                    var finalFg = fg
                    var finalBg = bg
                    if ((currAttr and VDUBuffer.INVERT) != 0L) {
                        val swapc = finalBg
                        finalBg = finalFg
                        finalFg = swapc
                    }

                    // set underlined attributes if requested
                    defaultPaint.isUnderlineText = (currAttr and VDUBuffer.UNDERLINE) != 0L

                    isWideCharacter = (currAttr and VDUBuffer.FULLWIDTH) != 0L

                    if (isWideCharacter)
                        addr++
                    else {
                        // determine the amount of continuous characters with the same settings and print them all at once
                        while (c + addr < buffer!!.width
                                && buffer!!.charAttributes[buffer!!.windowBase + l][c + addr] == currAttr) {
                            addr++
                        }
                    }

                    // Save the current clip region
                    canvas.save()

                    // clear this dirty area with background color
                    defaultPaint.color = finalBg
                    if (isWideCharacter) {
                        canvas.clipRect(c * charWidth,
                                l * charHeight,
                                (c + 2) * charWidth,
                                (l + 1) * charHeight)
                    } else {
                        canvas.clipRect(c * charWidth,
                                l * charHeight,
                                (c + addr) * charWidth,
                                (l + 1) * charHeight)
                    }
                    canvas.drawPaint(defaultPaint)

                    // write the text string starting at 'c' for 'addr' number of characters
                    defaultPaint.color = finalFg
                    if ((currAttr and VDUBuffer.INVISIBLE) == 0L)
                        canvas.drawText(buffer!!.charArray[buffer!!.windowBase + l], c,
                            addr, (c * charWidth).toFloat(), ((l * charHeight) - charTop).toFloat(),
                            defaultPaint)

                    // Restore the previous clip region
                    canvas.restore()

                    // advance to the next text block with different characteristics
                    c += addr - 1
                    if (isWideCharacter)
                        c++
                    c++
                }
            }

            // reset entire-buffer flags
            buffer!!.update[0] = false
        }
        fullRedraw = false
    }

    override fun redraw() {
        if (parent != null)
            parent!!.postInvalidate()
    }

    // We don't have a scroll bar.
    override fun updateScrollBar() {
    }

    /**
     * Resize terminal to fit [rows]x[cols] in screen of size [width]x[height]
     *
     * @param rows desired number of text rows
     * @param cols desired numbor of text colums
     * @param width width of screen in pixels
     * @param height height of screen in pixels
     */
    @Synchronized
    fun resizeComputed(cols: Int, rows: Int, width: Int, height: Int) {
        var sizeDp = 8.0f
        var step = 8.0f
        val limit = 0.125f

        var direction: Int

        while (fontSizeCompare(sizeDp, cols, rows, width, height).also { direction = it } < 0)
            sizeDp += step

        if (direction == 0) {
            Log.d("fontsize", String.format("Found match at %f", sizeDp))
            return
        }

        step /= 2.0f
        sizeDp -= step

        while (fontSizeCompare(sizeDp, cols, rows, width, height).also { direction = it } != 0
                && step >= limit) {
            step /= 2.0f
            if (direction > 0) {
                sizeDp -= step
            } else {
                sizeDp += step
            }
        }

        if (direction > 0)
            sizeDp -= step

        this.columns = cols
        this.rows = rows
        setFontSize(sizeDp, true)
    }

    private fun fontSizeCompare(sizeDp: Float, cols: Int, rows: Int, width: Int, height: Int): Int {
        // read new metrics to get exact pixel dimensions
        defaultPaint.textSize = (sizeDp * displayDensity * systemFontScale + 0.5f)
        val fm = defaultPaint.fontMetrics

        val widths = FloatArray(1)
        defaultPaint.getTextWidths("X", widths)
        val termWidth = widths[0].toInt() * cols
        val termHeight = Math.ceil((fm.descent - fm.top).toDouble()).toInt() * rows

        Log.d("fontsize", String.format("font size %fdp resulted in %d x %d", sizeDp, termWidth, termHeight))

        // Check to see if it fits in resolution specified.
        if (termWidth > width || termHeight > height)
            return 1

        if (termWidth == width || termHeight == height)
            return 0

        return -1
    }

    internal fun refreshOverlayFontSize() {
        val newDensity = manager!!.resources.displayMetrics.density
        val newFontScale = Settings.System.getFloat(manager.contentResolver,
                Settings.System.FONT_SCALE, 1.0f)
        if (newDensity != displayDensity || newFontScale != systemFontScale) {
            displayDensity = newDensity
            systemFontScale = newFontScale
            defaultPaint.textSize = (fontSizeDp * displayDensity * systemFontScale + 0.5f)
            setFontSize(fontSizeDp)
        }
    }

    /**
     * @return whether underlying transport can forward ports
     */
    fun canFowardPorts(): Boolean {
        return transport!!.canForwardPorts()
    }

    /**
     * Adds the [PortForward] to the list.
     * @param portForward the port forward bean to add
     * @return true on successful addition
     */
    fun addPortForward(portForward: PortForward): Boolean {
        return transport!!.addPortForward(portForward)
    }

    /**
     * Removes the [PortForward] from the list.
     * @param portForward the port forward bean to remove
     * @return true on successful removal
     */
    fun removePortForward(portForward: PortForward): Boolean {
        return transport!!.removePortForward(portForward)
    }

    /**
     * @return the list of port forwards
     */
    val portForwards: List<PortForward>
        get() = transport!!.getPortForwards().orEmpty()

    /**
     * Enables a port forward member. After calling this method, the port forward should
     * be operational.
     * @param portForward member of our current port forwards list to enable
     * @return true on successful port forward setup
     */
    fun enablePortForward(portForward: PortForward): Boolean {
        if (!transport!!.isConnected()) {
            Log.i(TAG, "Attempt to enable port forward while not connected")
            return false
        }

        return transport!!.enablePortForward(portForward)
    }

    /**
     * Disables a port forward member. After calling this method, the port forward should
     * be non-functioning.
     * @param portForward member of our current port forwards list to enable
     * @return true on successful port forward tear-down
     */
    fun disablePortForward(portForward: PortForward): Boolean {
        if (!transport!!.isConnected()) {
            Log.i(TAG, "Attempt to disable port forward while not connected")
            return false
        }

        return transport!!.disablePortForward(portForward)
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

    /* (non-Javadoc)
     * @see de.mud.terminal.VDUDisplay#setColor(byte, byte, byte, byte)
     */
    override fun setColor(index: Int, red: Int, green: Int, blue: Int) {
        // Don't allow the system colors to be overwritten for now. May violate specs.
        if (index < color.size && index >= 16)
            color[index] = 0xff000000.toInt() or (red shl 16) or (green shl 8) or blue
    }

    override fun resetColors() {
        scope.launch(Dispatchers.IO) {
            try {
                val defaults = manager!!.colorRepository.getSchemeDefaults(-1)
                defaultFg = defaults.first
                defaultBg = defaults.second

                color = manager.colorRepository.getSchemeColors(-1)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reset colors", e)
                manager?.reportError(
                    ServiceError.ColorSchemeLoadFailed(
                        reason = e.message ?: "Failed to load color scheme"
                    )
                )
            }
        }
    }

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
        val urls = mutableListOf<String>()

        val visibleBuffer = CharArray(buffer!!.height * buffer!!.width)
        for (l in 0 until buffer!!.height)
            System.arraycopy(buffer!!.charArray[buffer!!.windowBase + l], 0,
                    visibleBuffer, l * buffer!!.width, buffer!!.width)

        val urlMatcher = PatternHolder.urlPattern.matcher(String(visibleBuffer))
        while (urlMatcher.find())
            urls.add(urlMatcher.group())

        return urls
    }

    /**
     * @return
     */
    fun isUsingNetwork(): Boolean {
        return transport!!.usesNetwork()
    }

    /**
     * @return
     */
    val keyHandler: TerminalKeyListener
        get() = keyListener

    /**
     *
     */
    fun resetScrollPosition() {
        // if we're in scrollback, scroll to bottom of window on input
        if (buffer!!.windowBase != buffer!!.screenBase)
            buffer!!.setWindowBase(buffer!!.screenBase)
    }

    /**
     * Convenience function to increase the font size by a given step.
     */
    fun increaseFontSize() {
        setFontSize(fontSizeDp + FONT_SIZE_STEP)
    }

    /**
     * Convenience function to decrease the font size by a given step.
     */
    fun decreaseFontSize() {
        setFontSize(fontSizeDp - FONT_SIZE_STEP)
    }

    /**
     * Cancel the coroutine scope and clean up any running coroutines.
     */
    fun cancelScope() {
        scope.cancel()
    }

    companion object {
        const val TAG = "CB.TerminalBridge"

        private const val DEFAULT_FONT_SIZE_DP = 10
        private const val FONT_SIZE_STEP = 2
    }
}
