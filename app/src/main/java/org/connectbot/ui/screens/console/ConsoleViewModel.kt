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

package org.connectbot.ui.screens.console

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.connectbot.data.KeyboardLayoutRepository
import org.connectbot.data.entity.Host
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.keyboard.DefaultKeyboardLayouts
import org.connectbot.keyboard.KeyboardKeySize
import org.connectbot.keyboard.KeyboardLayoutSpec
import org.connectbot.service.DisconnectPolicy
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalManager
import org.connectbot.service.tmux.TmuxAttachState
import org.connectbot.service.tmux.TmuxPaneTerminal
import org.connectbot.service.tmux.TmuxSessionInfo
import org.connectbot.service.tmux.TmuxSessionManager
import org.connectbot.service.tmux.TmuxTarget
import org.connectbot.terminal.ProgressState
import org.connectbot.util.NotificationPermissionHelper
import org.connectbot.util.PreferenceConstants
import timber.log.Timber
import javax.inject.Inject

data class ConsoleUiState(
    val bridges: List<TerminalBridge> = emptyList(),
    val currentBridgeIndex: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    // Add a revision counter to force recomposition when bridge state changes
    val revision: Int = 0,
    // Progress state from OSC 9;4 escape sequences
    val progressState: ProgressState? = null,
    val progressValue: Int = 0,
    /** Host shell tabs plus tmux session tabs, grouped by host. */
    val tabs: List<ConsoleTab> = emptyList(),
    /** Key of the selected tab; null falls back to the current bridge's shell tab. */
    val currentTabKey: String? = null,
    /** Live pane terminal for the selected (attached) tmux tab. */
    val currentPaneTerminal: TmuxPaneTerminal? = null,
    /** Non-null when the "start a persistent session" offer applies to this host. */
    val tmuxOfferHostId: Long? = null,
    /** Command palette history for the current console (newest last). */
    val tmuxPaletteHistory: List<org.connectbot.ui.screens.console.tmux.TmuxPaletteEntry> = emptyList(),
) {
    val currentTab: ConsoleTab?
        get() = tabs.find { it.key == currentTabKey }
            ?: bridges.getOrNull(currentBridgeIndex)?.let { bridge ->
                tabs.find { it.key == ConsoleTab.hostKey(bridge.host.id) }
            }
}

@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val dispatchers: CoroutineDispatchers,
    private val prefs: SharedPreferences,
    private val keyboardLayoutRepository: KeyboardLayoutRepository,
    private val notificationPermissionHelper: NotificationPermissionHelper,
) : ViewModel() {

    /**
     * Resolve the effective keys-bar layout for a host: the per-host override,
     * or the global default preference (tracked live, so changing the default
     * updates any open console).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun keyboardLayoutFlow(host: Host): Flow<KeyboardLayoutSpec> {
        host.keyboardLayoutId?.let { return keyboardLayoutRepository.observeResolvedSpec(it) }
        return prefChanges(PreferenceConstants.KEYBOARD_LAYOUT_ID).flatMapLatest {
            keyboardLayoutRepository.observeResolvedSpec(
                prefs.getLong(PreferenceConstants.KEYBOARD_LAYOUT_ID, DefaultKeyboardLayouts.DEFAULT_ID),
            )
        }
    }

    /** Global key-size preference for the keys bar, tracked live. */
    val keyboardKeySize: StateFlow<KeyboardKeySize> by lazy {
        prefChanges(PreferenceConstants.KEYBOARD_KEY_SIZE)
            .map { readKeyboardKeySize() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), readKeyboardKeySize())
    }

    private fun readKeyboardKeySize(): KeyboardKeySize = KeyboardKeySize.fromPreferenceValue(
        prefs.getString(PreferenceConstants.KEYBOARD_KEY_SIZE, PreferenceConstants.KEYBOARD_KEY_SIZE_DEFAULT),
    )

    /** Emits once immediately, then whenever the preference [key] changes. */
    private fun prefChanges(key: String): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == key) trySend(Unit)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(Unit)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private val hostId: Long = savedStateHandle.get<Long>("hostId") ?: -1L

    /** "sessionId|windowId" from a tmux bell notification deep link. */
    private var pendingTmuxNavigation: Pair<String, String>? =
        savedStateHandle.get<String>("tmux")?.split('|')
            ?.takeIf { it.size == 2 && it[0].startsWith('$') && it[1].startsWith('@') }
            ?.let { it[0] to it[1] }
    private var terminalManager: TerminalManager? = null
    private var pendingInitialHostId: Long? = hostId.takeIf { it != -1L }
    private var selectedHostId: Long? = null

    private val bellJobs = mutableMapOf<Long, Job>()
    private val progressJobs = mutableMapOf<Long, Job>()
    private val networkStatusJobs = mutableMapOf<Long, Job>()
    private val tmuxJobs = mutableMapOf<Long, Job>()
    private val tmuxAlertJobs = mutableMapOf<Long, Job>()
    private val dismissedTmuxOffers = mutableSetOf<Long>()
    private var paneTerminalJob: Job? = null

    private val _uiState = MutableStateFlow(ConsoleUiState())
    val uiState: StateFlow<ConsoleUiState> = _uiState.asStateFlow()

    private val _networkStatusMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val networkStatusMessages: SharedFlow<String> = _networkStatusMessages.asSharedFlow()

    fun shouldShowNotificationWarning(): Boolean {
        if (!prefs.contains(PreferenceConstants.NOTIFICATION_PERMISSION_DENIED)) return false
        val connPersist = prefs.getBoolean(PreferenceConstants.CONNECTION_PERSIST, true)
        return !connPersist || !notificationPermissionHelper.isGranted()
    }

    fun setTerminalManager(manager: TerminalManager) {
        if (terminalManager != manager) {
            terminalManager = manager

            viewModelScope.launch {
                manager.bridgesFlow.collect { bridges ->
                    updateBridges(bridges)
                    syncBridgeBellSubscriptions(bridges)
                    syncBridgeProgressSubscriptions(bridges)
                    syncBridgeNetworkStatusSubscriptions(bridges)
                    syncBridgeTmuxSubscriptions(bridges)
                }
            }

            viewModelScope.launch {
                manager.hostStatusChangedFlow.collect {
                    _uiState.update { it.copy(revision = it.revision + 1) }
                    // tmux managers are created on connect; catch late arrivals.
                    syncBridgeTmuxSubscriptions(_uiState.value.bridges)
                }
            }

            if (hostId != -1L) {
                viewModelScope.launch {
                    ensureBridgeExists()
                }
            }
        }
    }

    private fun syncBridgeBellSubscriptions(bridges: List<TerminalBridge>) {
        syncBridgeJobs(bridges, bellJobs) { bridge ->
            viewModelScope.launch {
                bridge.bellEvents.collect {
                    val currentBridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex)

                    if (currentBridge == bridge) {
                        terminalManager?.playBeep()
                    } else {
                        terminalManager?.sendActivityNotification(bridge.host)
                    }
                }
            }
        }
    }

    private fun syncBridgeProgressSubscriptions(bridges: List<TerminalBridge>) {
        syncBridgeJobs(bridges, progressJobs) { bridge ->
            viewModelScope.launch {
                bridge.progressState.collect { progressInfo ->
                    val currentBridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex)
                    if (currentBridge == bridge) {
                        updateProgressState(progressInfo)
                    }
                }
            }
        }
    }

    private fun syncBridgeNetworkStatusSubscriptions(bridges: List<TerminalBridge>) {
        syncBridgeJobs(bridges, networkStatusJobs) { bridge ->
            viewModelScope.launch {
                bridge.networkStatusMessages.collect { message ->
                    val currentBridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex)
                    if (currentBridge == bridge) {
                        _networkStatusMessages.emit(message)
                    }
                }
            }
        }
    }

    private fun syncBridgeJobs(
        bridges: List<TerminalBridge>,
        jobs: MutableMap<Long, Job>,
        createJob: (TerminalBridge) -> Job,
    ) {
        val activeHostIds = bridges.map { it.host.id }.toSet()

        val removedIds = jobs.keys.filter { it !in activeHostIds }
        removedIds.forEach { hostId ->
            jobs.remove(hostId)?.cancel()
        }

        bridges.forEach { bridge ->
            jobs.getOrPut(bridge.host.id) {
                createJob(bridge)
            }
        }
    }

    private fun updateProgressState(progressInfo: TerminalBridge.ProgressInfo?) {
        _uiState.update {
            if (progressInfo == null || progressInfo.state == ProgressState.HIDDEN) {
                it.copy(progressState = null, progressValue = 0)
            } else {
                it.copy(
                    progressState = progressInfo.state,
                    progressValue = progressInfo.progress,
                )
            }
        }
    }

    private fun updateCurrentBridgeProgress(
        bridges: List<TerminalBridge> = _uiState.value.bridges,
        currentIndex: Int = _uiState.value.currentBridgeIndex,
    ) {
        val progressInfo = bridges.getOrNull(currentIndex)?.progressState?.value
        updateProgressState(progressInfo)
    }

    private fun findBridgeIndex(bridges: List<TerminalBridge>, bridgeHostId: Long?): Int {
        if (bridgeHostId == null) {
            return -1
        }

        return bridges.indexOfFirst { bridge ->
            bridge.host.id == bridgeHostId
        }
    }

    private fun stepBridgeSelection(offset: Int) {
        val bridges = _uiState.value.bridges
        if (bridges.isEmpty()) {
            return
        }

        val newIndex = (_uiState.value.currentBridgeIndex + offset).coerceIn(0, bridges.lastIndex)
        if (newIndex != _uiState.value.currentBridgeIndex) {
            selectBridge(newIndex)
        }
    }

    fun selectNextBridge() {
        stepBridgeSelection(1)
    }

    fun selectPreviousBridge() {
        stepBridgeSelection(-1)
    }

    private fun handleInitialSelectionError(message: String) {
        pendingInitialHostId = null
        _uiState.update {
            it.copy(
                isLoading = false,
                error = message,
            )
        }
    }

    private suspend fun ensureBridgeExists() {
        withContext(dispatchers.io) {
            try {
                val allBridges = terminalManager?.bridgesFlow?.value ?: emptyList()
                val existingBridge = allBridges.find { bridge ->
                    bridge.host.id == hostId
                }

                if (existingBridge == null) {
                    if (hostId < 0L) {
                        handleInitialSelectionError("Temporary connection not found")
                    } else {
                        val bridge = terminalManager?.openConnectionForHostId(hostId)
                        if (bridge == null) {
                            handleInitialSelectionError("Failed to open connection: host not found")
                        }
                    }
                } else {
                    maybeReconnectOnOpen(existingBridge)
                }
            } catch (e: Exception) {
                handleInitialSelectionError(e.message ?: "Failed to create connection")
            }
        }
    }

    // ===== tmux tabs =====

    /**
     * Subscribes to each bridge's tmux state once its manager exists (it is
     * created on connect, after the bridge first appears). Safe to call
     * repeatedly.
     */
    private fun syncBridgeTmuxSubscriptions(bridges: List<TerminalBridge>) {
        val activeHostIds = bridges.map { it.host.id }.toSet()
        tmuxJobs.keys.filter { it !in activeHostIds }.forEach { hostId ->
            tmuxJobs.remove(hostId)?.cancel()
        }
        tmuxAlertJobs.keys.filter { it !in activeHostIds }.forEach { hostId ->
            tmuxAlertJobs.remove(hostId)?.cancel()
        }
        bridges.forEach { bridge ->
            val tmux = bridge.tmux ?: return@forEach
            // Console is visible while this VM lives: poll flags eagerly.
            tmux.flagPollIntervalMs = TmuxSessionManager.FOREGROUND_FLAG_POLL_MS
            tmuxJobs.getOrPut(bridge.host.id) {
                viewModelScope.launch {
                    tmux.state.collect { rebuildTabs() }
                }
            }
            tmuxAlertJobs.getOrPut(bridge.host.id) {
                viewModelScope.launch {
                    launch {
                        tmux.alertEvents.collect { alert -> onTmuxAlert(bridge, alert) }
                    }
                    launch {
                        tmux.bellEvents.collect { (sessionId, paneId) ->
                            onTmuxPaneBell(bridge, sessionId, paneId)
                        }
                    }
                }
            }
        }
        rebuildTabs()
    }

    /** A bell surfaced by flag polling (usually a session we're not viewing). */
    private fun onTmuxAlert(bridge: TerminalBridge, alert: org.connectbot.service.tmux.TmuxAlert) {
        val current = _uiState.value.currentTab
        val viewingThatSession = current is ConsoleTab.TmuxSession &&
            current.bridge === bridge && current.sessionId == alert.sessionId
        if (viewingThatSession) {
            terminalManager?.playBeep()
        } else {
            terminalManager?.sendTmuxActivityNotification(
                bridge.host,
                "${alert.sessionId}|${alert.windowId}",
                "${alert.sessionName}:${alert.windowName}",
            )
        }
    }

    /** A live pane rang (attached session with a live emulator). */
    private fun onTmuxPaneBell(bridge: TerminalBridge, sessionId: String, paneId: String) {
        val current = _uiState.value.currentTab
        val viewingThatSession = current is ConsoleTab.TmuxSession &&
            current.bridge === bridge && current.sessionId == sessionId
        if (viewingThatSession) {
            terminalManager?.playBeep()
            return
        }
        val tmuxState = bridge.tmux?.state?.value
        val session = tmuxState?.session(sessionId)
        val window = session?.windows?.find { w -> w.panes.any { it.id == paneId } }
        terminalManager?.sendTmuxActivityNotification(
            bridge.host,
            "$sessionId|${window?.id ?: ""}",
            "${session?.name ?: sessionId}:${window?.name ?: ""}",
        )
    }

    private fun rebuildTabs() {
        _uiState.update { state ->
            val tabs = state.bridges.flatMap { bridge ->
                val sessionTabs = bridge.tmux?.state?.value?.sessions.orEmpty().map { session ->
                    ConsoleTab.TmuxSession(
                        bridge = bridge,
                        sessionId = session.id,
                        sessionName = session.name,
                        attachState = session.attachState,
                        bellBadge = session.bell || session.windows.any { it.bell },
                        activityBadge = session.activity || session.windows.any { it.activity },
                    )
                }
                listOf(ConsoleTab.HostShell(bridge)) + sessionTabs
            }
            val currentKey = state.currentTabKey
                ?.takeIf { key -> tabs.any { it.key == key } }
                ?: state.bridges.getOrNull(state.currentBridgeIndex)
                    ?.let { ConsoleTab.hostKey(it.host.id) }
            state.copy(
                tabs = tabs,
                currentTabKey = currentKey,
                tmuxOfferHostId = computeTmuxOffer(state.bridges, state.currentBridgeIndex),
            )
        }
        refreshPaneTerminal()
        consumePendingTmuxNavigation()
    }

    /** Deep link from a bell notification: land on the session and window. */
    private fun consumePendingTmuxNavigation() {
        val (sessionId, windowId) = pendingTmuxNavigation ?: return
        val bridge = _uiState.value.bridges.find { it.host.id == hostId } ?: return
        val key = ConsoleTab.tmuxKey(bridge.host.id, sessionId)
        if (_uiState.value.tabs.none { it.key == key }) return
        pendingTmuxNavigation = null
        selectTab(key)
        viewModelScope.launch(dispatchers.io) {
            // Window selection needs the attach dump; selectTab attaches lazily.
            val tmux = bridge.tmux ?: return@launch
            withTimeoutOrNull(10_000) {
                tmux.state.first { state ->
                    state.session(sessionId)?.windows?.any { it.id == windowId } == true
                }
            } ?: return@launch
            selectWindow(windowId)
        }
    }

    private fun computeTmuxOffer(bridges: List<TerminalBridge>, currentIndex: Int): Long? {
        val bridge = bridges.getOrNull(currentIndex) ?: return null
        val host = bridge.host
        if (host.id in dismissedTmuxOffers || host.tmuxOfferDismissed) return null
        val tmuxState = bridge.tmux?.state?.value ?: return null
        return host.id.takeIf { tmuxState.offerSession }
    }

    /** Selects a tab by key; lazily attaches detached tmux sessions. */
    fun selectTab(key: String) {
        val tab = _uiState.value.tabs.find { it.key == key } ?: return
        val bridgeIndex = _uiState.value.bridges.indexOfFirst { it === tab.bridge }
        if (bridgeIndex != -1) {
            selectedHostId = tab.bridge.host.id
        }
        _uiState.update {
            it.copy(
                currentTabKey = key,
                currentBridgeIndex = if (bridgeIndex != -1) bridgeIndex else it.currentBridgeIndex,
            )
        }
        updateCurrentBridgeProgress()

        if (tab is ConsoleTab.TmuxSession) {
            val tmux = tab.bridge.tmux ?: return
            viewModelScope.launch(dispatchers.io) {
                try {
                    if (tab.attachState == TmuxAttachState.DETACHED) {
                        tmux.attach(tab.sessionId)
                    } else {
                        tmux.selectSession(tab.sessionId)
                    }
                } catch (e: Exception) {
                    _networkStatusMessages.emit(e.message ?: "tmux attach failed")
                }
                refreshPaneTerminal()
            }
        } else {
            _uiState.update { it.copy(currentPaneTerminal = null) }
        }
    }

    /**
     * Resolves the live pane terminal for the selected tmux tab (creating and
     * backfilling on first view) into [ConsoleUiState.currentPaneTerminal].
     */
    private fun refreshPaneTerminal() {
        // Callers run on arbitrary dispatchers; paneTerminalJob bookkeeping is
        // serialized on the main dispatcher so cancellations are never lost.
        viewModelScope.launch(dispatchers.main) {
            val tab = _uiState.value.currentTab
            if (tab !is ConsoleTab.TmuxSession) {
                if (_uiState.value.currentPaneTerminal != null) {
                    _uiState.update { it.copy(currentPaneTerminal = null) }
                }
                return@launch
            }
            val tmux = tab.bridge.tmux ?: return@launch
            paneTerminalJob?.cancel()
            paneTerminalJob = launch(dispatchers.io) {
                val target = tmux.currentTarget.value
                    ?.takeIf { it.sessionId == tab.sessionId }
                    ?: tmux.selectSession(tab.sessionId)
                    ?: return@launch
                val terminal = runCatching { tmux.acquirePaneTerminal(target) }.getOrNull()
                _uiState.update { state ->
                    if (state.currentTab?.key == tab.key) {
                        state.copy(currentPaneTerminal = terminal)
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** The offer banner action: create a persistent session and land in it. */
    fun startTmuxSession() {
        val bridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex) ?: return
        val tmux = bridge.tmux ?: return
        viewModelScope.launch(dispatchers.io) {
            try {
                tmux.createSessionAndAttach(DEFAULT_TMUX_SESSION_NAME)
                val sessionId = tmux.state.value.sessions
                    .find { it.name == DEFAULT_TMUX_SESSION_NAME }?.id
                if (sessionId != null) {
                    selectTab(ConsoleTab.tmuxKey(bridge.host.id, sessionId))
                }
            } catch (e: Exception) {
                _networkStatusMessages.emit(e.message ?: "Failed to start tmux session")
            }
        }
    }

    /** Dismisses the offer; [permanent] persists it on the host row. */
    fun dismissTmuxOffer(permanent: Boolean = false) {
        val bridge = _uiState.value.bridges.getOrNull(_uiState.value.currentBridgeIndex) ?: return
        dismissedTmuxOffers.add(bridge.host.id)
        _uiState.update { it.copy(tmuxOfferHostId = null) }
        if (permanent && bridge.host.id > 0L) {
            val updated = bridge.host.copy(tmuxOfferDismissed = true)
            viewModelScope.launch(dispatchers.io) {
                runCatching { terminalManager?.hostRepository?.saveHost(updated) }
            }
        }
    }

    // ===== tmux management (menus, palette) =====

    fun renameTmuxSession(tab: ConsoleTab.TmuxSession, name: String) {
        val tmux = tab.bridge.tmux ?: return
        viewModelScope.launch(dispatchers.io) {
            runCatching { tmux.renameSession(tab.sessionId, name) }
        }
    }

    fun killTmuxSession(tab: ConsoleTab.TmuxSession) {
        val tmux = tab.bridge.tmux ?: return
        if (_uiState.value.currentTabKey == tab.key) {
            selectTab(ConsoleTab.hostKey(tab.bridge.host.id))
        }
        viewModelScope.launch(dispatchers.io) {
            runCatching { tmux.killSession(tab.sessionId) }
        }
    }

    fun newTmuxWindow() {
        val tab = _uiState.value.currentTab as? ConsoleTab.TmuxSession ?: return
        val tmux = tab.bridge.tmux ?: return
        viewModelScope.launch(dispatchers.io) {
            runCatching { tmux.newWindow(tab.sessionId) }
        }
    }

    fun renameTmuxWindow(windowId: String, name: String) {
        val tab = _uiState.value.currentTab as? ConsoleTab.TmuxSession ?: return
        val tmux = tab.bridge.tmux ?: return
        viewModelScope.launch(dispatchers.io) {
            runCatching { tmux.renameWindow(tab.sessionId, windowId, name) }
        }
    }

    fun killTmuxWindow(windowId: String) {
        val tab = _uiState.value.currentTab as? ConsoleTab.TmuxSession ?: return
        val tmux = tab.bridge.tmux ?: return
        viewModelScope.launch(dispatchers.io) {
            runCatching { tmux.killWindow(tab.sessionId, windowId) }
        }
    }

    /** Swaps a window with its neighbor [offset] positions away (reorder). */
    fun moveTmuxWindow(windowId: String, offset: Int) {
        val context = currentTmuxContext() ?: return
        val (tmux, _, session) = context
        val index = session.windows.indexOfFirst { it.id == windowId }
        val other = session.windows.getOrNull(index + offset) ?: return
        viewModelScope.launch(dispatchers.io) {
            runCatching { tmux.swapWindows(session.id, windowId, other.id) }
        }
    }

    /**
     * Resizes the tmux session to this phone's grid (approximated by the
     * host shell emulator's current dimensions at the profile font size).
     */
    fun resizeTmuxSessionToScreen(tab: ConsoleTab.TmuxSession) {
        val tmux = tab.bridge.tmux ?: return
        val dimensions = tab.bridge.terminalEmulator.dimensions
        viewModelScope.launch(dispatchers.io) {
            runCatching {
                tmux.resizeSessionToClient(tab.sessionId, dimensions.columns, dimensions.rows)
            }
        }
    }

    /** Deepens the viewed pane's local scrollback from server history. */
    fun loadEarlierTmuxHistory() {
        val terminal = _uiState.value.currentPaneTerminal ?: return
        viewModelScope.launch(dispatchers.io) {
            val grew = runCatching { terminal.loadEarlier() }.getOrDefault(false)
            if (!grew) {
                _networkStatusMessages.emit(HISTORY_LIMIT_MESSAGE)
            }
        }
    }

    /** Runs a raw tmux command from the palette against the viewed session. */
    fun runTmuxCommand(command: String) {
        val tab = _uiState.value.currentTab as? ConsoleTab.TmuxSession ?: return
        val tmux = tab.bridge.tmux ?: return
        viewModelScope.launch(dispatchers.io) {
            val entry = runCatching { tmux.rawCommand(tab.sessionId, command) }
                .fold(
                    onSuccess = { reply ->
                        org.connectbot.ui.screens.console.tmux.TmuxPaletteEntry(
                            command = command,
                            output = reply.text,
                            isError = !reply.ok,
                        )
                    },
                    onFailure = { e ->
                        org.connectbot.ui.screens.console.tmux.TmuxPaletteEntry(
                            command = command,
                            output = e.message ?: "command failed",
                            isError = true,
                        )
                    },
                )
            _uiState.update {
                it.copy(tmuxPaletteHistory = (it.tmuxPaletteHistory + entry).takeLast(PALETTE_HISTORY_LIMIT))
            }
        }
    }

    // ===== tmux navigation (window strip, swipes, volume keys) =====

    /** Cycles panes of the viewed window by [offset], wrapping around. */
    fun selectPane(offset: Int) {
        val context = currentTmuxContext() ?: return
        val (_, target, session) = context
        val window = session.windows.find { it.id == target.windowId } ?: return
        if (window.panes.size < 2) {
            // Single-pane window: hardware navigation falls through to windows.
            stepWindow(offset)
            return
        }
        val index = window.panes.indexOfFirst { it.id == target.paneId }
        if (index == -1) return
        val next = window.panes[(index + offset).mod(window.panes.size)]
        moveTarget(target.copy(paneId = next.id))
    }

    /** Cycles windows of the viewed session by [offset], wrapping around. */
    fun stepWindow(offset: Int) {
        val context = currentTmuxContext() ?: return
        val (_, target, session) = context
        if (session.windows.size < 2) return
        val index = session.windows.indexOfFirst { it.id == target.windowId }
        if (index == -1) return
        selectWindow(session.windows[(index + offset).mod(session.windows.size)].id)
    }

    /** Jumps to a window (window strip tap), landing on its active pane. */
    fun selectWindow(windowId: String) {
        val context = currentTmuxContext() ?: return
        val (_, target, session) = context
        val window = session.windows.find { it.id == windowId } ?: return
        val pane = window.activePane ?: return
        moveTarget(target.copy(windowId = window.id, paneId = pane.id))
    }

    private fun currentTmuxContext(): Triple<TmuxSessionManager, TmuxTarget, TmuxSessionInfo>? {
        val tab = _uiState.value.currentTab as? ConsoleTab.TmuxSession ?: return null
        val tmux = tab.bridge.tmux ?: return null
        val target = tmux.currentTarget.value?.takeIf { it.sessionId == tab.sessionId } ?: return null
        val session = tmux.state.value.session(tab.sessionId) ?: return null
        return Triple(tmux, target, session)
    }

    private fun moveTarget(target: TmuxTarget) {
        val tab = _uiState.value.currentTab as? ConsoleTab.TmuxSession ?: return
        val tmux = tab.bridge.tmux ?: return
        viewModelScope.launch(dispatchers.io) {
            tmux.selectTarget(target)
            refreshPaneTerminal()
        }
    }

    /** Closing a tmux tab detaches only; the server-side session survives. */
    fun detachTmuxTab(tab: ConsoleTab.TmuxSession) {
        val tmux = tab.bridge.tmux ?: return
        viewModelScope.launch(dispatchers.io) {
            runCatching { tmux.detach(tab.sessionId) }
        }
        if (_uiState.value.currentTabKey == tab.key) {
            selectTab(ConsoleTab.hostKey(tab.bridge.host.id))
        }
    }

    private fun updateBridges(allBridges: List<TerminalBridge>) {
        val currentState = _uiState.value
        val selectedIndex = findBridgeIndex(allBridges, selectedHostId)
        val requestedIndex = findBridgeIndex(allBridges, pendingInitialHostId)
        val requestedBridgeAvailable = requestedIndex != -1

        val newIndex = when {
            requestedBridgeAvailable -> requestedIndex
            selectedIndex != -1 -> selectedIndex
            currentState.currentBridgeIndex >= allBridges.size -> (allBridges.size - 1).coerceAtLeast(0)
            else -> currentState.currentBridgeIndex
        }

        if (requestedBridgeAvailable) {
            pendingInitialHostId = null
            selectedHostId = allBridges.getOrNull(newIndex)?.host?.id
        } else if (pendingInitialHostId == null) {
            selectedHostId = allBridges.getOrNull(newIndex)?.host?.id
        }

        val waitingForRequestedHost = pendingInitialHostId != null

        _uiState.update {
            it.copy(
                bridges = allBridges,
                currentBridgeIndex = newIndex,
                isLoading = waitingForRequestedHost,
                error = null,
            )
        }

        updateCurrentBridgeProgress(allBridges, newIndex)
        rebuildTabs()
    }

    fun selectBridge(index: Int) {
        if (index in _uiState.value.bridges.indices) {
            val bridge = _uiState.value.bridges[index]
            selectedHostId = bridge.host.id
            _uiState.update {
                it.copy(
                    currentBridgeIndex = index,
                    currentTabKey = ConsoleTab.hostKey(bridge.host.id),
                    currentPaneTerminal = null,
                    tmuxOfferHostId = computeTmuxOffer(it.bridges, index),
                )
            }
            updateCurrentBridgeProgress()
        }
    }

    /**
     * Refresh the UI state to trigger recomposition.
     * This is needed because bridge state (isSessionOpen, isDisconnected, etc.)
     * changes asynchronously but doesn't trigger Compose recomposition automatically.
     */
    fun refreshMenuState() {
        _uiState.update { it.copy(revision = it.revision + 1) }
    }

    /**
     * Request a reconnection for the given bridge.
     */
    fun reconnect(bridge: TerminalBridge) {
        terminalManager?.requestReconnect(bridge)
        _uiState.update { it.copy(revision = it.revision + 1) }
    }

    /**
     * Called when the console returns to the foreground so a session that
     * dropped in the background reconnects without requiring the user to
     * find the reconnect menu, matching pre-rewrite behavior.
     */
    fun onConsoleResumed() {
        val state = _uiState.value
        val bridge = state.bridges.getOrNull(state.currentBridgeIndex) ?: return
        maybeReconnectOnOpen(bridge)
    }

    private fun maybeReconnectOnOpen(bridge: TerminalBridge) {
        val shouldReconnect = DisconnectPolicy.shouldReconnectOnOpen(
            isDisconnected = bridge.isDisconnected,
            isConnecting = bridge.isConnecting,
            awaitingClose = bridge.isAwaitingClose(),
            reason = bridge.disconnectReason,
        )
        if (!shouldReconnect) {
            return
        }
        Timber.i("Reconnecting ${bridge.host.nickname} on open")
        terminalManager?.requestReconnect(bridge, userInitiated = true)
        _uiState.update { it.copy(revision = it.revision + 1) }
    }

    companion object {
        const val DEFAULT_TMUX_SESSION_NAME = "connectbot"
        private const val PALETTE_HISTORY_LIMIT = 50
        private const val HISTORY_LIMIT_MESSAGE = "History limit reached"
    }
}
