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

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.transport.ExecChannel
import timber.log.Timber

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

    /** Receives raw `%output` bytes per pane; set by the pane terminal registry. */
    var paneOutputSink: ((sessionId: String, paneId: String, bytes: ByteArray) -> Unit)? = null

    private val clients = java.util.concurrent.ConcurrentHashMap<String, TmuxControlClient>()
    private val clientJobs = java.util.concurrent.ConcurrentHashMap<String, List<Job>>()
    private val clientsMutex = Mutex()

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
            _state.value = _state.value.copy(
                sessions = _state.value.sessions.map { it.copy(attachState = TmuxAttachState.DETACHED) },
            )
        }
    }

    /**
     * Tears everything down; the bridge is closing for good. Must not
     * suspend — best-effort synchronous close (the transport teardown kills
     * the channels regardless).
     */
    fun shutdown() {
        transportUp = false
        pendingReattach = null
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        clientJobs.values.flatMap { it }.forEach { it.cancel() }
        clientJobs.clear()
    }

    // ===== Probe & discovery =====

    private suspend fun probeAndDiscover() {
        _state.value = _state.value.copy(availability = TmuxAvailability.PROBING)
        val probe = runCatching { execRead(PROBE_COMMAND) }.getOrElse {
            Timber.d(it, "tmux probe failed")
            _state.value = _state.value.copy(availability = TmuxAvailability.UNAVAILABLE)
            return
        }
        val versionLine = probe.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        if (versionLine == null || versionLine == NO_TMUX_MARKER) {
            _state.value = _state.value.copy(availability = TmuxAvailability.UNAVAILABLE)
            return
        }
        val version = TmuxVersion.parse(versionLine)
        if (version == null) {
            Timber.d("Unparseable tmux version: %s", versionLine)
            _state.value = _state.value.copy(availability = TmuxAvailability.UNAVAILABLE)
            return
        }
        if (!version.isSupported) {
            _state.value = _state.value.copy(
                availability = TmuxAvailability.UNSUPPORTED_VERSION,
                version = version,
            )
            return
        }
        _state.value = _state.value.copy(version = version)
        refreshSessions()
    }

    /**
     * Re-lists sessions over a short-lived exec channel and merges with known
     * state (attached sessions keep their live window/pane tree).
     */
    suspend fun refreshSessions() {
        val listing = runCatching { execRead(LIST_SESSIONS_COMMAND) }.getOrElse {
            Timber.d(it, "tmux session discovery failed")
            _state.value = _state.value.copy(availability = TmuxAvailability.UNAVAILABLE)
            return
        }
        if (listing.lineSequence().firstOrNull()?.trim() == NO_SERVER_MARKER) {
            _state.value = _state.value.copy(
                availability = TmuxAvailability.READY,
                sessions = emptyList(),
                offerSession = true,
            )
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

        _state.value = _state.value.copy(
            availability = TmuxAvailability.READY,
            sessions = discovered,
            offerSession = discovered.isEmpty(),
        )

        discovered
            .filter { it.attachState == TmuxAttachState.DETACHED && it.snapshot == null }
            .forEach { session ->
                scope.launch(ioDispatcher) { fetchSnapshot(session.id) }
            }
    }

    private fun parseSessionLine(line: String): TmuxSessionInfo? {
        val parts = line.trimEnd().split('\t')
        if (parts.size < 3 || !parts[0].startsWith('$')) return null
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
        _state.value = _state.value.updateSession(sessionId) {
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
            if (clients.containsKey(sessionId)) return
        }
        val session = _state.value.session(sessionId) ?: throw IOException("unknown tmux session $sessionId")
        _state.value = _state.value.updateSession(sessionId) {
            it.copy(attachState = TmuxAttachState.ATTACHING)
        }

        val client = try {
            val channel = runInterruptible(ioDispatcher) {
                channelFactory.open("tmux -u -C attach-session -t '$sessionId'")
            }
            TmuxControlClient(channel, scope, ioDispatcher)
        } catch (e: Exception) {
            _state.value = _state.value.updateSession(sessionId) {
                it.copy(attachState = TmuxAttachState.DETACHED)
            }
            throw e
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

        _state.value = _state.value.updateSession(sessionId) {
            it.copy(attachState = TmuxAttachState.ATTACHED, snapshot = null)
        }

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
        _state.value = _state.value.updateSession(sessionId) {
            it.copy(attachState = TmuxAttachState.DETACHED)
        }
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
                    _state.value = _state.value.copy(
                        sessions = _state.value.sessions.filterNot { it.id == sessionId },
                    )
                    if (transportUp) refreshSessions()
                }

                else -> {
                    _state.value = _state.value.updateSession(sessionId) {
                        it.copy(attachState = TmuxAttachState.DETACHED)
                    }
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
            if (parts.size < 5 || !parts[0].startsWith('@')) return@mapNotNull null
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

        _state.value = _state.value.updateSession(sessionId) { session ->
            session.copy(
                windows = windows,
                activeWindowId = windows.find { it.active }?.id ?: windows.firstOrNull()?.id,
            )
        }
    }

    private fun parsePaneLine(line: String): Pair<String, TmuxPaneRef>? {
        val parts = line.split('\t')
        if (parts.size < 8 || !parts[0].startsWith('@') || !parts[1].startsWith('%')) return null
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

    /** Makes [target] current locally and mirrors the selection server-side. */
    suspend fun selectTarget(target: TmuxTarget) {
        setCurrentTarget(target)
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

    // ===== Notification handling =====

    private suspend fun handleNotification(sessionId: String, notification: TmuxNotification) {
        when (notification) {
            is TmuxNotification.Output ->
                paneOutputSink?.invoke(sessionId, notification.paneId, notification.bytes)

            is TmuxNotification.SessionsChanged,
            is TmuxNotification.UnlinkedWindowAdd,
            -> if (transportUp) refreshSessions()

            is TmuxNotification.WindowAdd -> {
                _state.value = reduceSession(_state.value, sessionId, notification)
                refreshWindows(sessionId)
            }

            else -> {
                _state.value = reduceSession(_state.value, sessionId, notification)
                syncTargetWithState(sessionId, notification)
            }
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
        private const val ATTACH_TIMEOUT_MS = 15_000L
        private const val DETACH_TIMEOUT_MS = 3_000L

        private const val PROBE_COMMAND =
            "command -v tmux >/dev/null 2>&1 && tmux -V || echo $NO_TMUX_MARKER"

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

        private fun renameWindow(state: TmuxHostState, windowId: String, name: String): TmuxHostState =
            state.copy(
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
