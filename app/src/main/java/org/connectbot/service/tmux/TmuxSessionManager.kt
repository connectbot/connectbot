/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package org.connectbot.service.tmux

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.service.meetsCompletionThreshold
import org.connectbot.transport.ExecChannel
import timber.log.Timber
import java.io.IOException

/** Opens one remote command channel; the SSH transport provides this. */
fun interface TmuxChannelFactory {
    @Throws(IOException::class)
    fun open(command: String): ExecChannel
}

/**
 * All tmux state and operations for one connected host.
 *
 * Owned by a TerminalBridge (composition — a tmux session is not a
 * connection). Probes for tmux after connect, discovers sessions, attaches
 * control-mode clients lazily (one [TmuxControlClient] per attached session),
 * applies notifications to an immutable [TmuxHostState] via a pure reducer,
 * and routes `%output` bytes to [paneOutputSink] (the per-pane terminal
 * registry).
 */
class TmuxSessionManager(
    private val channelFactory: TmuxChannelFactory,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _state = MutableStateFlow(TmuxHostState())
    val state: StateFlow<TmuxHostState> = _state.asStateFlow()

    private val _currentTarget = MutableStateFlow<TmuxTarget?>(null)

    /** The pane the user is looking at; drives reattach and persistence. */
    val currentTarget: StateFlow<TmuxTarget?> = _currentTarget.asStateFlow()

    /** Invoked (off the main thread) whenever [currentTarget] changes; the bridge persists it. */
    var onTargetChanged: ((TmuxTarget?) -> Unit)? = null

    /** Extra tap on raw `%output` bytes (tests); rendering goes to [paneRegistry]. */
    var paneOutputSink: ((sessionId: String, paneId: String, bytes: ByteArray) -> Unit)? = null

    /** Colors for newly created pane emulators; the bridge sets profile colors. */
    @Volatile
    var paneColors: TmuxPaneColors = TmuxPaneColors.DEFAULT

    /** Emulator factory for pane terminals; tests inject a fake (termlib is JNI-only). */
    @Volatile
    var paneEmulatorFactory: TmuxPaneEmulatorFactory = TmuxPaneEmulatorFactory.REAL

    /** Snapshot cadence for newly created pane emulators; existing panes retain theirs. */
    @Volatile
    var paneMinUpdateIntervalMs: Long = 0L

    /** Sticky-modifier pref for pane key handlers; the bridge sets it. */
    @Volatile
    var stickyModifierSetting: org.connectbot.service.StickyModifierSetting =
        org.connectbot.service.StickyModifierSetting.NONE

    /** Bell events from live panes: (sessionId, paneId). */
    private val _bellEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val bellEvents: SharedFlow<Pair<String, String>> = _bellEvents.asSharedFlow()

    /** New window-level bell/activity flags discovered by polling. */
    private val _alertEvents = MutableSharedFlow<TmuxAlert>(extraBufferCapacity = 32)
    val alertEvents: SharedFlow<TmuxAlert> = _alertEvents.asSharedFlow()

    /** Long-running command completions from live panes (OSC 133). */
    private val _commandCompletions = MutableSharedFlow<TmuxCommandCompletion>(extraBufferCapacity = 16)
    val commandCompletions: SharedFlow<TmuxCommandCompletion> = _commandCompletions.asSharedFlow()

    /** Minimum command duration in ms before a completion is reported; 0 = off. The bridge sets it. */
    @Volatile
    var completionThresholdMs: Long = 0L

    /**
     * Flag-poll cadence: the console sets ~10s while foregrounded; the
     * default matches the keepalive-ish background rhythm.
     */
    @Volatile
    var flagPollIntervalMs: Long = BACKGROUND_FLAG_POLL_MS

    private var flagPollJob: Job? = null

    /** (sessionId, windowId) pairs whose bell was already reported. */
    private val reportedBells = mutableSetOf<Pair<String, String>>()

    /** Live pane emulators, LRU-capped; eviction stops the stream on >=3.2. */
    val paneRegistry = TmuxPaneRegistry(
        onEvicted = { terminal -> onPaneEvicted(terminal) },
    )

    /** All state mutations go through CAS: concurrent coroutines must not lose updates. */
    private fun mutateState(transform: (TmuxHostState) -> TmuxHostState) = _state.update(transform)

    private fun mutateSession(sessionId: String, transform: (TmuxSessionInfo) -> TmuxSessionInfo) = _state.update { it.updateSession(sessionId, transform) }

    private val clients = java.util.concurrent.ConcurrentHashMap<String, TmuxControlClient>()
    private val clientJobs = java.util.concurrent.ConcurrentHashMap<String, List<Job>>()
    private val clientsMutex = Mutex()

    /** Sessions mid-attach; guarded by [clientsMutex] to bar duplicate attaches. */
    private val attachingSessions = mutableSetOf<String>()

    /** Where to reattach after the transport comes back. */
    @Volatile
    private var pendingReattach: TmuxTarget? = null

    @Volatile
    private var transportUp = false

    /**
     * Called by the bridge once the transport is connected and authenticated.
     * Probes for tmux, discovers sessions, and reattaches to [restoreTarget]
     * (or the target from before a reconnect) when it still exists.
     */
    fun onTransportConnected(restoreTarget: TmuxTarget? = null) {
        transportUp = true
        val target = pendingReattach ?: restoreTarget
        pendingReattach = null
        scope.launch(ioDispatcher) {
            probeAndDiscover()
            if (target != null && _state.value.session(target.sessionId) != null) {
                runCatching { attach(target.sessionId, target) }
                    .onFailure { Timber.w(it, "tmux reattach to ${target.sessionId} failed") }
            }
        }
    }

    /** Called by the bridge when the connection drops (channels are dead). */
    fun onTransportLost() {
        transportUp = false
        flagPollJob?.cancel()
        flagPollJob = null
        pendingReattach = _currentTarget.value
        scope.launch {
            val orphaned = clientsMutex.withLock {
                val entries = clients.toMap()
                clients.clear()
                clientJobs.values.flatten().forEach { it.cancel() }
                clientJobs.clear()
                entries
            }
            orphaned.values.forEach { runCatching { it.close() } }
            paneRegistry.clear()
            mutateState { state ->
                state.copy(sessions = state.sessions.map { it.copy(attachState = TmuxAttachState.DETACHED) })
            }
        }
    }

    /**
     * Tears everything down; the bridge is closing for good. Must not
     * suspend — best-effort synchronous close (the transport teardown kills
     * the channels regardless).
     */
    fun shutdown() {
        transportUp = false
        flagPollJob?.cancel()
        flagPollJob = null
        pendingReattach = null
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        clientJobs.values.flatMap { it }.forEach { it.cancel() }
        clientJobs.clear()
        paneRegistry.clear()
    }

    // ===== Probe & discovery =====

    private suspend fun probeAndDiscover() {
        mutateState { it.copy(availability = TmuxAvailability.PROBING) }
        val probe = runCatching { execRead(PROBE_COMMAND) }.getOrElse {
            Timber.d(it, "tmux probe failed")
            mutateState { state -> state.copy(availability = TmuxAvailability.UNAVAILABLE) }
            return
        }
        val versionLine = probe.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        if (versionLine == null || versionLine == NO_TMUX_MARKER) {
            mutateState { it.copy(availability = TmuxAvailability.UNAVAILABLE) }
            return
        }
        val version = TmuxVersion.parse(versionLine)
        if (version == null) {
            Timber.d("Unparseable tmux version: %s", versionLine)
            mutateState { it.copy(availability = TmuxAvailability.UNAVAILABLE) }
            return
        }
        if (!version.isSupported) {
            mutateState { it.copy(availability = TmuxAvailability.UNSUPPORTED_VERSION, version = version) }
            return
        }
        mutateState { it.copy(version = version) }
        refreshSessions()
    }

    /**
     * Re-lists sessions over a short-lived exec channel and merges with known
     * state (attached sessions keep their live window/pane tree).
     */
    suspend fun refreshSessions() {
        val listing = runCatching { execRead(LIST_SESSIONS_COMMAND) }.getOrElse {
            Timber.d(it, "tmux session discovery failed")
            mutateState { state -> state.copy(availability = TmuxAvailability.UNAVAILABLE) }
            return
        }
        if (listing.lineSequence().firstOrNull()?.trim() == NO_SERVER_MARKER) {
            mutateState {
                it.copy(
                    availability = TmuxAvailability.READY,
                    sessions = emptyList(),
                    offerSession = true,
                )
            }
            return
        }

        val known = _state.value.sessions.associateBy { it.id }
        val discovered = listing.lineSequence()
            .mapNotNull { parseSessionLine(it) }
            .map { parsed ->
                known[parsed.id]?.copy(
                    name = parsed.name,
                    attachedCount = parsed.attachedCount,
                ) ?: parsed
            }
            .toList()

        mutateState {
            it.copy(
                availability = TmuxAvailability.READY,
                sessions = discovered,
                offerSession = discovered.isEmpty(),
            )
        }

        discovered
            .filter { it.attachState == TmuxAttachState.DETACHED && it.snapshot == null }
            .forEach { session ->
                scope.launch(ioDispatcher) { fetchSnapshot(session.id) }
            }

        startFlagPolling()
    }

    // ===== Alert flag polling =====

    private fun startFlagPolling() {
        if (flagPollJob?.isActive == true) return
        flagPollJob = scope.launch(ioDispatcher) {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(flagPollIntervalMs)
                if (!transportUp || _state.value.availability != TmuxAvailability.READY) continue
                if (_state.value.sessions.isEmpty()) continue
                runCatching { pollWindowFlags() }
                    .onFailure { Timber.d(it, "tmux flag poll failed") }
            }
        }
    }

    /**
     * One cheap exec round-trip that fetches bell/activity flags for every
     * window on the server — the only way to see alerts in sessions we are
     * not attached to. New bells are emitted on [alertEvents] once.
     */
    internal suspend fun pollWindowFlags() {
        val listing = execRead(FLAG_POLL_COMMAND)
        if (listing.lineSequence().firstOrNull()?.trim() == NO_SERVER_MARKER) return

        data class Flags(val bell: Boolean, val activity: Boolean, val name: String)
        val flagsByWindow = mutableMapOf<Pair<String, String>, Flags>()
        listing.lineSequence().forEach { line ->
            val parts = line.trimEnd().split('\t')
            if (parts.size < 5 || !TmuxIds.isSession(parts[0]) || !TmuxIds.isWindow(parts[1])) return@forEach
            flagsByWindow[parts[0] to parts[1]] =
                Flags(bell = parts[2] == "1", activity = parts[3] == "1", name = parts[4])
        }
        if (flagsByWindow.isEmpty()) return

        mutateState { state ->
            state.copy(
                sessions = state.sessions.map { session ->
                    val sessionFlags = flagsByWindow.filterKeys { it.first == session.id }
                    if (sessionFlags.isEmpty()) return@map session.copy(bell = false, activity = false)
                    session.copy(
                        bell = sessionFlags.values.any { it.bell },
                        activity = sessionFlags.values.any { it.activity },
                        windows = session.windows.map { window ->
                            val flags = flagsByWindow[session.id to window.id]
                            if (flags != null) {
                                window.copy(bell = flags.bell, activity = flags.activity)
                            } else {
                                window
                            }
                        },
                    )
                },
            )
        }

        // Report each bell once until it clears server-side.
        val current = _state.value
        val viewed = _currentTarget.value
        flagsByWindow.forEach { (key, flags) ->
            val (sessionId, windowId) = key
            if (!flags.bell) {
                reportedBells.remove(key)
                return@forEach
            }
            if (viewed?.sessionId == sessionId && viewed.windowId == windowId) return@forEach
            if (!reportedBells.add(key)) return@forEach
            val sessionName = current.session(sessionId)?.name ?: sessionId
            _alertEvents.tryEmit(
                TmuxAlert(
                    sessionId = sessionId,
                    sessionName = sessionName,
                    windowId = windowId,
                    windowName = flags.name,
                ),
            )
        }
    }

    private fun parseSessionLine(line: String): TmuxSessionInfo? {
        val parts = line.trimEnd().split('\t')
        if (parts.size < 3 || !TmuxIds.isSession(parts[0])) return null
        return TmuxSessionInfo(
            id = parts[0],
            name = parts[1],
            attachedCount = parts[2].toIntOrNull() ?: 0,
        )
    }

    private suspend fun fetchSnapshot(sessionId: String) {
        val text = runCatching {
            execRead("tmux capture-pane -ep -t '$sessionId:' 2>/dev/null")
        }.getOrNull() ?: return
        if (text.isEmpty()) return
        mutateSession(sessionId) {
            if (it.attachState == TmuxAttachState.DETACHED) it.copy(snapshot = text.lines()) else it
        }
    }

    // ===== Attach / detach =====

    /**
     * Opens a control-mode channel to [sessionId] and loads its full
     * window/pane tree. No-op if already attached.
     * @param target window/pane to land on; defaults to the server-side active ones
     */
    suspend fun attach(sessionId: String, target: TmuxTarget? = null) {
        clientsMutex.withLock {
            // A concurrent attach may be past the containsKey check but not
            // yet registered in clients; the attaching set closes that gap.
            if (clients.containsKey(sessionId) || !attachingSessions.add(sessionId)) return
        }
        try {
            doAttach(sessionId, target)
        } finally {
            clientsMutex.withLock { attachingSessions.remove(sessionId) }
        }
    }

    private suspend fun doAttach(sessionId: String, target: TmuxTarget?) {
        val session = _state.value.session(sessionId) ?: throw IOException("unknown tmux session $sessionId")
        mutateSession(sessionId) { it.copy(attachState = TmuxAttachState.ATTACHING) }

        val client = try {
            val channel = runInterruptible(ioDispatcher) {
                channelFactory.open("tmux -u -C attach-session -t '$sessionId'")
            }
            TmuxControlClient(channel, scope, ioDispatcher)
        } catch (e: Exception) {
            mutateSession(sessionId) { it.copy(attachState = TmuxAttachState.DETACHED) }
            throw e
        }

        // Ask tmux (>=3.2) to pause flooding panes instead of stalling the
        // whole channel; paused panes are resumed and resynced on demand.
        if (_state.value.version?.supportsFlowControl == true) {
            runCatching { client.command("refresh-client -f pause-after=$PAUSE_AFTER_SECONDS") }
        }

        val notificationJob = scope.launch {
            client.notifications.collect { handleNotification(sessionId, it) }
        }
        val closedJob = scope.launch {
            val reason = runCatching { client.closed.await() }.getOrNull()
            onClientClosed(sessionId, reason)
        }
        clientsMutex.withLock {
            clients[sessionId] = client
            clientJobs[sessionId] = listOf(notificationJob, closedJob)
        }

        try {
            withTimeout(ATTACH_TIMEOUT_MS) { loadSessionTree(sessionId, client) }
        } catch (e: Exception) {
            detachInternal(sessionId, graceful = false)
            throw IOException("tmux attach to $sessionId failed: ${e.message}", e)
        }

        mutateSession(sessionId) { it.copy(attachState = TmuxAttachState.ATTACHED, snapshot = null) }

        val landed = resolveTarget(sessionId, target)
        if (landed != null) selectTarget(landed)
        Timber.d("tmux attached to %s (%s)", sessionId, session.name)
    }

    /** Detaches from [sessionId]; the server-side session keeps running. */
    suspend fun detach(sessionId: String) {
        detachInternal(sessionId, graceful = true)
    }

    private suspend fun detachInternal(sessionId: String, graceful: Boolean) {
        val (client, jobs) = clientsMutex.withLock {
            val c = clients.remove(sessionId) ?: return
            val j = clientJobs.remove(sessionId).orEmpty()
            c to j
        }
        if (graceful) {
            withTimeoutOrNull(DETACH_TIMEOUT_MS) {
                runCatching { client.command("detach-client") }
                client.closed.await()
            }
        }
        runCatching { client.close() }
        jobs.forEach { it.cancel() }
        paneRegistry.removeSession(sessionId)
        mutateSession(sessionId) { it.copy(attachState = TmuxAttachState.DETACHED) }
        if (transportUp) {
            scope.launch(ioDispatcher) { fetchSnapshot(sessionId) }
        }
    }

    private fun onClientClosed(sessionId: String, reason: String?) {
        Timber.d("tmux control channel for %s closed (%s)", sessionId, reason ?: "no reason")
        scope.launch {
            val removed = clientsMutex.withLock {
                clientJobs.remove(sessionId)?.forEach { it.cancel() }
                clients.remove(sessionId) != null
            }
            if (!removed) return@launch
            when (reason) {
                // Session or server was killed: it no longer exists.
                "killed", "server exited" -> {
                    paneRegistry.removeSession(sessionId)
                    mutateState { state ->
                        state.copy(sessions = state.sessions.filterNot { it.id == sessionId })
                    }
                    if (transportUp) refreshSessions()
                }

                else -> {
                    paneRegistry.removeSession(sessionId)
                    mutateSession(sessionId) { it.copy(attachState = TmuxAttachState.DETACHED) }
                    if (transportUp) scope.launch(ioDispatcher) { fetchSnapshot(sessionId) }
                }
            }
        }
    }

    private suspend fun loadSessionTree(sessionId: String, client: TmuxControlClient) {
        val windowsReply = client.command(
            "list-windows -t '$sessionId' -F '#{window_id}\t#{window_index}\t#{window_name}\t#{window_active}\t#{window_flags}'",
        )
        if (!windowsReply.ok) throw IOException("list-windows failed: ${windowsReply.text}")
        val panesReply = client.command(
            "list-panes -s -t '$sessionId' -F '#{window_id}\t#{pane_id}\t#{pane_index}\t#{pane_width}\t#{pane_height}\t#{pane_left}\t#{pane_top}\t#{pane_active}'",
        )
        if (!panesReply.ok) throw IOException("list-panes failed: ${panesReply.text}")

        val panesByWindow = panesReply.lines
            .mapNotNull { parsePaneLine(it) }
            .groupBy({ it.first }, { it.second })

        val windows = windowsReply.lines.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 5 || !TmuxIds.isWindow(parts[0])) return@mapNotNull null
            val panes = panesByWindow[parts[0]].orEmpty()
            TmuxWindow(
                id = parts[0],
                index = parts[1].toIntOrNull() ?: 0,
                name = parts[2],
                active = parts[3] == "1",
                bell = '!' in parts[4],
                activity = '#' in parts[4],
                panes = panes,
                activePaneId = panes.find { it.active }?.id ?: panes.firstOrNull()?.id,
            )
        }.sortedBy { it.index }

        mutateSession(sessionId) { session ->
            session.copy(
                windows = windows,
                activeWindowId = windows.find { it.active }?.id ?: windows.firstOrNull()?.id,
            )
        }
    }

    private fun parsePaneLine(line: String): Pair<String, TmuxPaneRef>? {
        val parts = line.split('\t')
        if (parts.size < 8 || !TmuxIds.isWindow(parts[0]) || !TmuxIds.isPane(parts[1])) return null
        return parts[0] to TmuxPaneRef(
            id = parts[1],
            index = parts[2].toIntOrNull() ?: 0,
            width = parts[3].toIntOrNull() ?: 80,
            height = parts[4].toIntOrNull() ?: 24,
            left = parts[5].toIntOrNull() ?: 0,
            top = parts[6].toIntOrNull() ?: 0,
            active = parts[7] == "1",
        )
    }

    // ===== Navigation =====

    /**
     * Makes [sessionId] the session the user is viewing: resolves its active
     * window/pane into a target, selects it, and returns it (or null when the
     * session has no usable target yet).
     */
    suspend fun selectSession(sessionId: String): TmuxTarget? {
        val current = _currentTarget.value
        val target = if (current?.sessionId == sessionId) {
            resolveTarget(sessionId, current)
        } else {
            resolveTarget(sessionId, null)
        } ?: return null
        selectTarget(target)
        return target
    }

    /** Makes [target] current locally and mirrors the selection server-side. */
    suspend fun selectTarget(target: TmuxTarget) {
        setCurrentTarget(target)
        clearWindowFlags(target.sessionId, target.windowId)
        val client = clientsMutex.withLock { clients[target.sessionId] } ?: return
        runCatching {
            client.command("select-window -t '${target.windowId}'")
            client.command("select-pane -t '${target.paneId}'")
        }.onFailure { Timber.d(it, "tmux select failed") }
    }

    private fun resolveTarget(sessionId: String, requested: TmuxTarget?): TmuxTarget? {
        val session = _state.value.session(sessionId) ?: return null
        val window = requested?.windowId?.let { id -> session.windows.find { it.id == id } }
            ?: session.activeWindow
            ?: return null
        val pane = requested?.paneId?.let { id -> window.panes.find { it.id == id } }
            ?: window.activePane
            ?: return null
        return TmuxTarget(sessionId, window.id, pane.id, session.name)
    }

    /** Viewing a window clears its badges (the server clears its own flags). */
    private fun clearWindowFlags(sessionId: String, windowId: String) {
        reportedBells.remove(sessionId to windowId)
        mutateSession(sessionId) { session ->
            val windows = session.windows.map { window ->
                if (window.id == windowId) window.copy(bell = false, activity = false) else window
            }
            session.copy(
                windows = windows,
                bell = windows.any { it.bell },
                activity = windows.any { it.activity },
            )
        }
    }

    private fun setCurrentTarget(target: TmuxTarget?) {
        if (_currentTarget.value == target) return
        _currentTarget.value = target
        onTargetChanged?.invoke(target)
    }

    // ===== Session lifecycle ops =====

    /** Creates a detached session and attaches to it (the offer flow / [+] tab). */
    suspend fun createSessionAndAttach(name: String) {
        execRead("tmux new-session -d -s '${escapeSingleQuotes(name)}' 2>&1")
        refreshSessions()
        val created = _state.value.sessions.find { it.name == name }
            ?: throw IOException("tmux session '$name' was not created")
        attach(created.id)
    }

    // ===== Management ops =====

    /**
     * Resizes the session server-side to the phone's grid (the per-session
     * "resize to my screen" action). Affects other attached clients; the UI
     * confirms when attachedCount > 1.
     */
    suspend fun resizeSessionToClient(sessionId: String, cols: Int, rows: Int) {
        if (cols <= 0 || rows <= 0) return
        val separator = if (_state.value.version?.atLeast(3, 0) == true) "," else "x"
        sessionCommand(sessionId, "refresh-client -C $cols$separator$rows")
    }

    /**
     * Runs one tmux command against [sessionId]: over its control client
     * when attached, else over a short-lived exec channel. Returns the
     * in-band reply when attached, null otherwise.
     */
    suspend fun sessionCommand(sessionId: String, command: String): TmuxReply? {
        val client = clients[sessionId]
        return if (client != null) {
            client.command(command)
        } else {
            // Our id quoting ('$n'/'@n') and quoteDouble name quoting are
            // shell-compatible (\$ and \` survive double quotes); only octal
            // control-byte escapes degrade, which is acceptable for names.
            execRead("tmux $command 2>&1")
            null
        }
    }

    /** Raw command for the palette; requires an attached session. */
    suspend fun rawCommand(sessionId: String, command: String): TmuxReply {
        val client = clients[sessionId] ?: throw TmuxChannelClosedException("session $sessionId is not attached")
        return client.command(command)
    }

    suspend fun renameSession(sessionId: String, name: String) {
        sessionCommand(sessionId, "rename-session -t '$sessionId' \"${TmuxInputEncoder.quoteDouble(name)}\"")
        refreshSessions()
    }

    /** Kills the session server-side; state updates via %exit / re-discovery. */
    suspend fun killSession(sessionId: String) {
        sessionCommand(sessionId, "kill-session -t '$sessionId'")
        if (clients[sessionId] == null && transportUp) refreshSessions()
    }

    suspend fun newWindow(sessionId: String) {
        sessionCommand(sessionId, "new-window -t '$sessionId'")
    }

    suspend fun splitPaneH(sessionId: String, paneId: String) {
        sessionCommand(sessionId, "split-window -h -t '$paneId'")
    }

    suspend fun splitPaneV(sessionId: String, paneId: String) {
        sessionCommand(sessionId, "split-window -v -t '$paneId'")
    }

    suspend fun zoomPane(sessionId: String, paneId: String) {
        sessionCommand(sessionId, "resize-pane -Z -t '$paneId'")
    }

    suspend fun killPane(sessionId: String, paneId: String) {
        sessionCommand(sessionId, "kill-pane -t '$paneId'")
    }

    suspend fun breakPane(sessionId: String, paneId: String) {
        sessionCommand(sessionId, "break-pane -t '$paneId'")
    }

    suspend fun renameWindow(sessionId: String, windowId: String, name: String) {
        sessionCommand(sessionId, "rename-window -t '$windowId' \"${TmuxInputEncoder.quoteDouble(name)}\"")
    }

    suspend fun killWindow(sessionId: String, windowId: String) {
        sessionCommand(sessionId, "kill-window -t '$windowId'")
    }

    /** Swaps two windows' positions (menu-based reorder). */
    suspend fun swapWindows(sessionId: String, windowId: String, otherWindowId: String) {
        sessionCommand(sessionId, "swap-window -d -s '$windowId' -t '$otherWindowId'")
        refreshWindows(sessionId)
    }

    // ===== Notification handling =====

    private suspend fun handleNotification(sessionId: String, notification: TmuxNotification) {
        when (notification) {
            is TmuxNotification.Output -> {
                paneRegistry.route(sessionId, notification)
                paneOutputSink?.invoke(sessionId, notification.paneId, notification.bytes)
            }

            // Refreshes issue commands whose replies arrive on this same
            // channel: never await them from the notification collector, or
            // a full buffer would deadlock the reader against the reply.
            is TmuxNotification.SessionsChanged,
            is TmuxNotification.UnlinkedWindowAdd,
            -> if (transportUp) scope.launch(ioDispatcher) { refreshSessions() }

            is TmuxNotification.WindowAdd -> {
                mutateState { reduceSession(it, sessionId, notification) }
                scope.launch(ioDispatcher) { refreshWindows(sessionId) }
            }

            is TmuxNotification.LayoutChange -> {
                mutateState { reduceSession(it, sessionId, notification) }
                resizeLivePanes(sessionId, notification.windowId)
                syncTargetWithState(sessionId, notification)
            }

            is TmuxNotification.Pause -> {
                mutateState { reduceSession(it, sessionId, notification) }
                resumePausedPane(sessionId, notification.paneId)
            }

            else -> {
                mutateState { reduceSession(it, sessionId, notification) }
                syncTargetWithState(sessionId, notification)
            }
        }
    }

    /**
     * tmux paused a flooding pane's stream. If we render it, continue the
     * stream and resync the emulator over the gap; otherwise leave it
     * paused — it costs nothing while unwatched.
     */
    private fun resumePausedPane(sessionId: String, paneId: String) {
        val terminal = paneRegistry.get(sessionId, paneId) ?: return
        val client = clients[sessionId] ?: return
        scope.launch(ioDispatcher) {
            runCatching {
                client.command("refresh-client -A '$paneId:continue'")
                terminal.resync()
                mutateState { reduceSession(it, sessionId, TmuxNotification.Continue(paneId)) }
            }.onFailure { Timber.d(it, "tmux pane resume failed for %s", paneId) }
        }
    }

    /** Pushes server-side pane sizes into any live emulators of a window. */
    private fun resizeLivePanes(sessionId: String, windowId: String) {
        val window = _state.value.session(sessionId)?.windows?.find { it.id == windowId } ?: return
        window.panes.forEach { pane ->
            paneRegistry.get(sessionId, pane.id)?.resize(rows = pane.height, cols = pane.width)
        }
    }

    // ===== Pane terminals =====

    /**
     * Returns the live terminal for [target]'s pane, creating and
     * backfilling one if needed (the session must be attached). On tmux
     * >=3.2 re-enables the pane's output stream in case a prior eviction
     * turned it off.
     */
    suspend fun acquirePaneTerminal(target: TmuxTarget): TmuxPaneTerminal? {
        val client = clients[target.sessionId] ?: return null
        val pane = _state.value.session(target.sessionId)
            ?.windows?.find { it.id == target.windowId }
            ?.panes?.find { it.id == target.paneId }
            ?: return null

        val (terminal, created) = paneRegistry.acquire(target.sessionId, target.paneId) {
            TmuxPaneTerminal(
                sessionId = target.sessionId,
                paneId = target.paneId,
                initialRows = pane.height,
                initialCols = pane.width,
                colors = paneColors,
                minUpdateIntervalMs = paneMinUpdateIntervalMs,
                scope = scope,
                sendCommand = { command -> client.command(command) },
                onBell = { sessionId, paneId -> onPaneBell(sessionId, paneId) },
                onCommandCompletion = { sessionId, paneId, durationMs, snippet ->
                    onPaneCommandCompleted(sessionId, paneId, durationMs, snippet)
                },
                emulatorFactory = paneEmulatorFactory,
            ).also { it.stickyModifierSetting = stickyModifierSetting }
        }
        if (created) {
            if (_state.value.version?.supportsFlowControl == true) {
                runCatching { client.command("refresh-client -A '${target.paneId}:on'") }
            }
            terminal.backfill()
        }
        return terminal
    }

    /** A live pane rang: badge its window unless the user is looking at it. */
    private fun onPaneBell(sessionId: String, paneId: String) {
        _bellEvents.tryEmit(sessionId to paneId)
        val viewed = _currentTarget.value
        if (viewed?.sessionId == sessionId && viewed.paneId == paneId) return
        mutateSession(sessionId) { session ->
            session.copy(
                bell = true,
                windows = session.windows.map { window ->
                    if (window.panes.any { it.id == paneId }) window.copy(bell = true) else window
                },
            )
        }
    }

    /**
     * A long-running command finished in a live pane: badge its window (via
     * the existing activity-flag plumbing) unless the user is looking at it,
     * and emit the completion for the console/notification layers.
     * Internal so tests can drive it with fabricated durations.
     */
    internal fun onPaneCommandCompleted(
        sessionId: String,
        paneId: String,
        durationMs: Long,
        snippet: String?,
    ) {
        if (!meetsCompletionThreshold(durationMs, completionThresholdMs)) return
        val session = _state.value.session(sessionId) ?: return
        val window = session.windows.find { w -> w.panes.any { it.id == paneId } } ?: return

        val viewed = _currentTarget.value
        val isViewed = viewed?.sessionId == sessionId && viewed.windowId == window.id
        if (!isViewed) {
            mutateSession(sessionId) { s ->
                s.copy(
                    activity = true,
                    windows = s.windows.map { w ->
                        if (w.id == window.id) w.copy(activity = true) else w
                    },
                )
            }
        }
        val completion = TmuxCommandCompletion(
            sessionId = sessionId,
            sessionName = session.name,
            windowId = window.id,
            windowName = window.name,
            paneId = paneId,
            durationMs = durationMs,
            snippet = snippet,
        )
        scope.launch { _commandCompletions.emit(completion) }
    }

    /** Stops an evicted pane's output stream server-side when supported. */
    private fun onPaneEvicted(terminal: TmuxPaneTerminal) {
        if (_state.value.version?.supportsFlowControl != true) return
        val client = clients[terminal.sessionId] ?: return
        scope.launch {
            runCatching { client.command("refresh-client -A '${terminal.paneId}:off'") }
        }
    }

    /** Re-dump one attached session's windows/panes (after %window-add etc.). */
    private suspend fun refreshWindows(sessionId: String) {
        val client = clientsMutex.withLock { clients[sessionId] } ?: return
        runCatching { loadSessionTree(sessionId, client) }
            .onFailure { Timber.d(it, "tmux window refresh failed") }
    }

    /** Keeps [currentTarget] valid when its window/pane disappears. */
    private fun syncTargetWithState(sessionId: String, notification: TmuxNotification) {
        val target = _currentTarget.value ?: return
        if (target.sessionId != sessionId) return
        when (notification) {
            is TmuxNotification.WindowClose,
            is TmuxNotification.LayoutChange,
            is TmuxNotification.Exit,
            -> {
                val resolved = resolveTarget(sessionId, target)
                if (resolved != target) setCurrentTarget(resolved)
            }

            else -> Unit
        }
    }

    // ===== Exec helpers =====

    private suspend fun execRead(command: String, timeoutMs: Long = EXEC_TIMEOUT_MS): String {
        val channel = runInterruptible(ioDispatcher) { channelFactory.open(command) }
        return try {
            withTimeout(timeoutMs) {
                runInterruptible(ioDispatcher) {
                    channel.stdout.readBytes().toString(Charsets.UTF_8)
                }
            }
        } finally {
            runCatching { channel.close() }
        }
    }

    companion object {
        private const val NO_TMUX_MARKER = "NO_TMUX"
        private const val NO_SERVER_MARKER = "NO_SERVER"

        private const val EXEC_TIMEOUT_MS = 10_000L
        private const val PAUSE_AFTER_SECONDS = 60
        private const val ATTACH_TIMEOUT_MS = 15_000L
        private const val DETACH_TIMEOUT_MS = 3_000L

        private const val PROBE_COMMAND =
            "command -v tmux >/dev/null 2>&1 && tmux -V || echo $NO_TMUX_MARKER"

        private const val BACKGROUND_FLAG_POLL_MS = 60_000L

        /** Foreground cadence the console requests while visible. */
        const val FOREGROUND_FLAG_POLL_MS = 10_000L

        private const val FLAG_POLL_COMMAND =
            "tmux list-windows -a -F '#{session_id}\t#{window_id}\t#{window_bell_flag}\t#{window_activity_flag}\t#{window_name}' 2>/dev/null || echo $NO_SERVER_MARKER"

        private const val LIST_SESSIONS_COMMAND =
            "tmux ls -F '#{session_id}\t#{session_name}\t#{session_attached}' 2>/dev/null || echo $NO_SERVER_MARKER"

        internal fun escapeSingleQuotes(value: String): String = value.replace("'", "'\\''")

        /**
         * Pure state transition for one control-mode notification on one
         * attached session. `%output`, `%window-add` refresh, and discovery
         * triggers are handled by the manager; everything else lands here.
         */
        internal fun reduceSession(
            state: TmuxHostState,
            sessionId: String,
            notification: TmuxNotification,
        ): TmuxHostState = when (notification) {
            is TmuxNotification.WindowAdd -> state.updateSession(sessionId) { session ->
                if (session.windows.any { it.id == notification.windowId }) {
                    session
                } else {
                    // Placeholder; the manager re-dumps the tree for details.
                    session.copy(
                        windows = session.windows +
                            TmuxWindow(id = notification.windowId, index = Int.MAX_VALUE, name = ""),
                    )
                }
            }

            is TmuxNotification.WindowClose -> removeWindow(state, notification.windowId)

            is TmuxNotification.UnlinkedWindowClose -> removeWindow(state, notification.windowId)

            is TmuxNotification.WindowRenamed -> renameWindow(state, notification.windowId, notification.name)

            is TmuxNotification.UnlinkedWindowRenamed -> renameWindow(state, notification.windowId, notification.name)

            is TmuxNotification.LayoutChange -> state.updateSession(sessionId) { session ->
                applyLayout(session, notification)
            }

            is TmuxNotification.SessionRenamed -> {
                val id = notification.sessionId ?: sessionId
                state.updateSession(id) { it.copy(name = notification.name) }
            }

            is TmuxNotification.SessionWindowChanged ->
                state.updateSession(notification.sessionId) { it.copy(activeWindowId = notification.windowId) }

            is TmuxNotification.WindowPaneChanged -> state.updateSession(sessionId) { session ->
                session.copy(
                    windows = session.windows.map { window ->
                        if (window.id == notification.windowId) {
                            window.copy(activePaneId = notification.paneId)
                        } else {
                            window
                        }
                    },
                )
            }

            is TmuxNotification.Pause -> setPanePaused(state, sessionId, notification.paneId, paused = true)

            is TmuxNotification.Continue -> setPanePaused(state, sessionId, notification.paneId, paused = false)

            is TmuxNotification.Exit -> state.updateSession(sessionId) {
                it.copy(attachState = TmuxAttachState.DETACHED)
            }

            else -> state
        }

        private fun removeWindow(state: TmuxHostState, windowId: String): TmuxHostState = state.copy(
            sessions = state.sessions.map { session ->
                val remaining = session.windows.filterNot { it.id == windowId }
                if (remaining.size == session.windows.size) {
                    session
                } else {
                    session.copy(
                        windows = remaining,
                        activeWindowId = if (session.activeWindowId == windowId) {
                            remaining.firstOrNull()?.id
                        } else {
                            session.activeWindowId
                        },
                    )
                }
            },
        )

        private fun renameWindow(state: TmuxHostState, windowId: String, name: String): TmuxHostState = state.copy(
            sessions = state.sessions.map { session ->
                session.copy(
                    windows = session.windows.map { window ->
                        if (window.id == windowId) window.copy(name = name) else window
                    },
                )
            },
        )

        private fun applyLayout(
            session: TmuxSessionInfo,
            notification: TmuxNotification.LayoutChange,
        ): TmuxSessionInfo {
            val geometry = TmuxLayoutParser.parse(notification.layout)
            if (geometry.isEmpty()) return session
            return session.copy(
                windows = session.windows.map { window ->
                    if (window.id != notification.windowId) return@map window
                    val existing = window.panes.associateBy { it.id }
                    val panes = geometry.mapIndexed { index, pane ->
                        existing[pane.paneId]?.copy(
                            width = pane.width,
                            height = pane.height,
                            left = pane.left,
                            top = pane.top,
                        ) ?: TmuxPaneRef(
                            id = pane.paneId,
                            index = index,
                            width = pane.width,
                            height = pane.height,
                            left = pane.left,
                            top = pane.top,
                        )
                    }
                    val flags = notification.flags
                    window.copy(
                        panes = panes,
                        activePaneId = window.activePaneId?.takeIf { id -> panes.any { it.id == id } }
                            ?: panes.firstOrNull()?.id,
                        bell = flags?.contains('!') ?: window.bell,
                        activity = flags?.contains('#') ?: window.activity,
                    )
                },
            )
        }

        private fun setPanePaused(
            state: TmuxHostState,
            sessionId: String,
            paneId: String,
            paused: Boolean,
        ): TmuxHostState = state.updateSession(sessionId) { session ->
            session.copy(
                windows = session.windows.map { window ->
                    window.copy(
                        panes = window.panes.map { pane ->
                            if (pane.id == paneId) pane.copy(paused = paused) else pane
                        },
                    )
                },
            )
        }
    }
}
