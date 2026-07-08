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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.service.AuthBanner
import org.connectbot.service.DisconnectReason
import org.connectbot.service.PromptRequest
import org.connectbot.service.TerminalBridge
import org.connectbot.service.tmux.TmuxAttachState
import org.connectbot.service.tmux.TmuxPaneTerminal
import org.connectbot.terminal.ComposeController
import org.connectbot.terminal.ProgressState
import org.connectbot.terminal.SelectionController
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.VTermKey
import org.connectbot.ui.LoadingScreen
import org.connectbot.ui.LocalEinkMode
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.common.parseHostColor
import org.connectbot.ui.components.AuthBannerDialog
import org.connectbot.ui.components.FloatingTextInputDialog
import org.connectbot.ui.components.InlinePrompt
import org.connectbot.ui.components.ResizeDialog
import org.connectbot.ui.components.SnippetPickerSheet
import org.connectbot.ui.components.TERMINAL_KEYBOARD_HEIGHT_DP
import org.connectbot.ui.components.TerminalKeyboard
import org.connectbot.ui.components.UrlScanDialog
import org.connectbot.ui.screens.console.tmux.PaneDotsIndicator
import org.connectbot.ui.screens.console.tmux.TmuxActionMenuDialog
import org.connectbot.ui.screens.console.tmux.TmuxCommandPaletteSheet
import org.connectbot.ui.screens.console.tmux.TmuxConfirmDialog
import org.connectbot.ui.screens.console.tmux.TmuxKillConfirmDialog
import org.connectbot.ui.screens.console.tmux.TmuxOfferBanner
import org.connectbot.ui.screens.console.tmux.TmuxRenameDialog
import org.connectbot.ui.screens.console.tmux.TmuxSnapshotPage
import org.connectbot.ui.screens.console.tmux.TmuxWindowStrip
import org.connectbot.ui.screens.console.tmux.tmuxSwipeNavigation
import org.connectbot.ui.theme.terminal
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.TerminalSessionReader
import org.connectbot.util.TerminalTextUtils
import org.connectbot.util.UrlUtils
import org.connectbot.util.rememberTerminalTypefaceResultFromStoredValue
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max

/**
 * Check if a hardware keyboard is currently attached to the device.
 * Detects QWERTY and 12-key hardware keyboards, including Bluetooth keyboards.
 */
@Composable
private fun rememberHasHardwareKeyboard(): Boolean {
    val configuration = LocalConfiguration.current

    return remember(configuration) {
        val keyboardType = configuration.keyboard
        keyboardType == android.content.res.Configuration.KEYBOARD_QWERTY ||
            keyboardType == android.content.res.Configuration.KEYBOARD_12KEY
    }
}

@VisibleForTesting
const val AUTO_HIDE_DELAY_MS = 3000L

internal object ConsoleTestTags {
    const val AUTH_BANNER_MESSAGE = "auth_banner_message"
}

internal fun handleConsoleShortcut(
    keyEvent: KeyEvent,
    volumeKeysChangeFontSize: Boolean,
    copySelection: () -> Unit,
    pasteClipboardContents: () -> Unit,
    increaseFontSize: () -> Unit,
    decreaseFontSize: () -> Unit,
    /** Active tmux tab + pref: volume keys switch panes instead of font size. */
    tmuxPaneNavigation: Boolean = false,
    nextPane: () -> Unit = {},
    previousPane: () -> Unit = {},
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false

    return when {
        // Volume keys on a tmux tab: pane navigation (preference-gated)
        tmuxPaneNavigation && keyEvent.key == Key.VolumeUp -> {
            nextPane()
            true
        }

        tmuxPaneNavigation && keyEvent.key == Key.VolumeDown -> {
            previousPane()
            true
        }

        // Ctrl+Shift+C: copy selection
        keyEvent.key == Key.C && keyEvent.isCtrlPressed && keyEvent.isShiftPressed -> {
            copySelection()
            true
        }

        // Ctrl+Shift+V: paste clipboard content
        keyEvent.key == Key.V && keyEvent.isCtrlPressed && keyEvent.isShiftPressed -> {
            pasteClipboardContents()
            true
        }

        // Ctrl+Shift+= (Ctrl++): increase font size
        keyEvent.isCtrlPressed && keyEvent.isShiftPressed && keyEvent.key == Key.Equals -> {
            increaseFontSize()
            true
        }

        // Ctrl+Shift+-: decrease font size
        keyEvent.isCtrlPressed && keyEvent.isShiftPressed && keyEvent.key == Key.Minus -> {
            decreaseFontSize()
            true
        }

        // Volume keys: change font size
        volumeKeysChangeFontSize && keyEvent.key == Key.VolumeUp -> {
            increaseFontSize()
            true
        }

        volumeKeysChangeFontSize && keyEvent.key == Key.VolumeDown -> {
            decreaseFontSize()
            true
        }

        else -> false
    }
}

@VisibleForTesting
internal fun sessionSwipeTarget(
    currentIndex: Int,
    sessionCount: Int,
    dragX: Float,
    dragY: Float,
    viewportWidth: Int,
    touchSlop: Float,
    selectionActive: Boolean = false,
): Int? {
    if (selectionActive || sessionCount < 2 || viewportWidth <= 0) {
        return null
    }

    val minimumSwipeDistance = max(touchSlop * 3f, viewportWidth * 0.18f)
    if (abs(dragX) < minimumSwipeDistance || abs(dragX) < abs(dragY) * 1.5f) {
        return null
    }

    val targetIndex = if (dragX < 0f) {
        (currentIndex + 1).coerceAtMost(sessionCount - 1)
    } else {
        (currentIndex - 1).coerceAtLeast(0)
    }

    return targetIndex.takeIf { it != currentIndex }
}

/**
 * Fraction of the viewport width, measured from the left edge, where the
 * page up/down gesture is recognized (per the preference description).
 */
private const val PAGE_GESTURE_REGION_FRACTION = 1f / 3f

/**
 * Decides whether an accumulated vertical drag should send a page key.
 *
 * Dragging up advances the content (Page Down); dragging down goes back
 * (Page Up), matching the pre-Compose gesture behavior.
 *
 * @return [VTermKey.PAGEUP], [VTermKey.PAGEDOWN], or null if the drag has
 *   not yet covered a page distance.
 */
@VisibleForTesting
internal fun pageKeyForDrag(
    accumulatedDragY: Float,
    viewportHeight: Int,
    touchSlop: Float,
    selectionActive: Boolean = false,
): Int? {
    if (selectionActive || viewportHeight <= 0) {
        return null
    }

    val pageDistance = max(touchSlop * 4f, viewportHeight / 8f)
    return when {
        accumulatedDragY <= -pageDistance -> VTermKey.PAGEDOWN
        accumulatedDragY >= pageDistance -> VTermKey.PAGEUP
        else -> null
    }
}

@VisibleForTesting
internal fun shouldShowSoftwareKeyboardForSessionOpen(
    previousBridgeId: Long?,
    previousSessionOpen: Boolean,
    currentBridgeId: Long?,
    sessionOpen: Boolean,
    hasHardwareKeyboard: Boolean,
): Boolean = currentBridgeId != null &&
    currentBridgeId == previousBridgeId &&
    sessionOpen &&
    !previousSessionOpen &&
    !hasHardwareKeyboard

@VisibleForTesting
internal fun shouldPreserveSoftwareKeyboardForBridgeChange(
    previousBridgeId: Long?,
    currentBridgeId: Long?,
    showSoftwareKeyboard: Boolean,
    hasHardwareKeyboard: Boolean,
): Boolean = previousBridgeId != null &&
    currentBridgeId != null &&
    previousBridgeId != currentBridgeId &&
    showSoftwareKeyboard &&
    !hasHardwareKeyboard

private fun Modifier.sessionSwipeNavigation(
    currentIndex: Int,
    sessionCount: Int,
    selectionActive: Boolean,
    onSwipeToSession: (Int) -> Unit,
    onInteraction: () -> Unit,
): Modifier = pointerInput(currentIndex, sessionCount, selectionActive) {
    if (selectionActive) {
        return@pointerInput
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val pointerId = down.id
        var dragX = 0f
        var dragY = 0f
        var horizontalSwipeLocked = false
        var verticalGestureLocked = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                break
            }

            val delta = change.positionChange()
            dragX += delta.x
            dragY += delta.y

            if (!horizontalSwipeLocked && !verticalGestureLocked) {
                val absX = abs(dragX)
                val absY = abs(dragY)
                if (absX > viewConfiguration.touchSlop && absX > absY * 1.5f) {
                    horizontalSwipeLocked = true
                } else if (absY > viewConfiguration.touchSlop && absY > absX) {
                    verticalGestureLocked = true
                }
            }

            if (horizontalSwipeLocked) {
                change.consume()
            }
        }

        if (horizontalSwipeLocked) {
            sessionSwipeTarget(
                currentIndex = currentIndex,
                sessionCount = sessionCount,
                dragX = dragX,
                dragY = dragY,
                viewportWidth = size.width,
                touchSlop = viewConfiguration.touchSlop,
                selectionActive = selectionActive,
            )?.let { target ->
                onInteraction()
                onSwipeToSession(target)
            }
        }
    }
}

/**
 * Sends page up/down keys for vertical swipes starting in the left third of
 * the terminal, as described by the "Page up/down gesture" preference.
 * Locked vertical drags are consumed so the scrollback does not also move.
 */
private fun Modifier.pageUpDownGesture(
    selectionActive: Boolean,
    onPageKey: (Int) -> Unit,
): Modifier = pointerInput(selectionActive) {
    if (selectionActive) {
        return@pointerInput
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x > size.width * PAGE_GESTURE_REGION_FRACTION) {
            return@awaitEachGesture
        }

        val pointerId = down.id
        var dragX = 0f
        var dragY = 0f
        var accumulatedY = 0f
        var pageGestureLocked = false
        var otherGestureLocked = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                break
            }

            val delta = change.positionChange()
            dragX += delta.x
            dragY += delta.y

            if (!pageGestureLocked && !otherGestureLocked) {
                val absX = abs(dragX)
                val absY = abs(dragY)
                if (absY > viewConfiguration.touchSlop && absY > absX) {
                    pageGestureLocked = true
                    accumulatedY = dragY
                } else if (absX > viewConfiguration.touchSlop && absX > absY) {
                    otherGestureLocked = true
                }
            } else if (pageGestureLocked) {
                accumulatedY += delta.y
            }

            if (pageGestureLocked) {
                change.consume()
                pageKeyForDrag(
                    accumulatedDragY = accumulatedY,
                    viewportHeight = size.height,
                    touchSlop = viewConfiguration.touchSlop,
                )?.let { key ->
                    onPageKey(key)
                    accumulatedY = 0f
                }
            }
        }
    }
}

/**
 * Human-readable explanation for why the session ended, shown on the
 * disconnect overlay. Returns null for reasons that need no explanation
 * (the user asked for the disconnect, or the cause is unknown).
 */
@Composable
private fun disconnectReasonText(reason: DisconnectReason): String? = when (reason) {
    DisconnectReason.REMOTE_EOF -> stringResource(R.string.disconnect_reason_remote_eof)
    DisconnectReason.IO_ERROR -> stringResource(R.string.disconnect_reason_io_error)
    DisconnectReason.NETWORK_LOST -> stringResource(R.string.disconnect_reason_network_lost)
    DisconnectReason.HOST_UNRESOLVED -> stringResource(R.string.disconnect_reason_host_unresolved)
    DisconnectReason.AUTH_FAIL -> stringResource(R.string.disconnect_reason_auth_fail)
    DisconnectReason.USER_REQUESTED, DisconnectReason.UNKNOWN -> null
}

@Composable
private fun ConsoleTerminalPage(
    bridge: TerminalBridge,
    isActive: Boolean,
    keyboardAlwaysVisible: Boolean,
    showSoftwareKeyboard: Boolean,
    forceSize: Pair<Int, Int>?,
    termFocusRequester: FocusRequester,
    showExtraKeyboard: Boolean,
    hasPlayedKeyboardAnimation: Boolean,
    imeVisible: Boolean,
    handleTerminalInteraction: () -> Unit,
    onShowSoftwareKeyboardChange: (Boolean) -> Unit,
    onImeVisibilityChange: (Boolean) -> Unit,
    onTextInputRequest: () -> Unit,
    onSnippetsRequest: () -> Unit,
    onDisconnectRequest: () -> Unit,
    onKeyboardScrollInProgressChange: (Boolean) -> Unit,
    onSelectionControllerChange: (SelectionController) -> Unit,
    onOpenUrl: (String) -> Unit,
    onPasteRequest: () -> Unit,
    onInterceptKey: (KeyEvent) -> Boolean,
    onReconnect: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    terminalModifier: Modifier = Modifier,
    /** When set, the page renders this tmux pane instead of the host shell. */
    tmuxPane: TmuxPaneTerminal? = null,
    /** Server-side pane grid (rows, cols); termlib fits the font to it. */
    tmuxForcedSize: Pair<Int, Int>? = null,
) {
    Box(modifier = modifier) {
        val fontResult = rememberTerminalTypefaceResultFromStoredValue(bridge.fontFamily)
        val coroutineScope = rememberCoroutineScope()
        val fontSize by bridge.fontSizeFlow.collectAsState()
        val delKeyMode by bridge.delKeyModeFlow.collectAsState()

        LaunchedEffect(fontResult.loadFailed, fontResult.isLoading) {
            if (fontResult.loadFailed && !fontResult.isLoading) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Failed to load font '${fontResult.requestedFontName}'. Using system default.",
                    )
                }
            }
        }

        val tmuxEmulator = tmuxPane?.emulator
        var composeController by remember { mutableStateOf<ComposeController?>(null) }

        // Per-host keyboard suggestions (issue 2015): activate the IME compose
        // mode once the terminal provides its controller.
        LaunchedEffect(composeController, bridge.host.keyboardSuggestions) {
            val controller = composeController ?: return@LaunchedEffect
            if (bridge.host.keyboardSuggestions && !controller.isComposeModeActive) {
                controller.startComposeMode()
            }
        }

        Terminal(
            terminalEmulator = tmuxEmulator ?: bridge.terminalEmulator,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = if (keyboardAlwaysVisible) TERMINAL_KEYBOARD_HEIGHT_DP.dp else 0.dp,
                )
                .then(terminalModifier)
                .testTag("terminal"),
            typeface = fontResult.typeface,
            initialFontSize = fontSize.sp,
            keyboardEnabled = true,
            showSoftKeyboard = showSoftwareKeyboard && isActive,
            focusRequester = termFocusRequester,
            forcedSize = if (tmuxPane != null) tmuxForcedSize else forceSize,
            modifierManager = if (tmuxEmulator != null) tmuxPane.keyHandler else bridge.keyHandler,
            onSelectionControllerAvailable = { controller ->
                if (isActive) {
                    onSelectionControllerChange(controller)
                }
            },
            onComposeControllerAvailable = { controller ->
                composeController = controller
            },
            onTerminalTap = { handleTerminalInteraction() },
            onImeVisibilityChanged = { visible ->
                if (isActive) {
                    onImeVisibilityChange(visible)
                }
            },
            onHyperlinkClick = onOpenUrl,
            delKeyMode = delKeyMode,
            onPasteRequest = onPasteRequest,
            onInterceptKey = onInterceptKey,
        )

        SideEffect {
            bridge.onTextInputRequest = onTextInputRequest
        }

        val einkMode = LocalEinkMode.current
        if (isActive) {
            AnimatedVisibility(
                visible = showExtraKeyboard,
                enter = if (einkMode) EnterTransition.None else fadeIn(animationSpec = tween(durationMillis = 100)),
                exit = if (einkMode) ExitTransition.None else fadeOut(animationSpec = tween(durationMillis = 100)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .testTag("terminal_keyboard"),
            ) {
                TerminalKeyboard(
                    bridge = bridge,
                    onInteraction = { handleTerminalInteraction() },
                    onHideIme = {
                        onShowSoftwareKeyboardChange(false)
                    },
                    onShowIme = {
                        onShowSoftwareKeyboardChange(true)
                    },
                    onOpenTextInput = onTextInputRequest,
                    onOpenSnippets = onSnippetsRequest,
                    onScrollInProgressChange = onKeyboardScrollInProgressChange,
                    imeVisible = imeVisible,
                    playAnimation = !hasPlayedKeyboardAnimation && !einkMode,
                )
            }

            val promptState by bridge.promptManager.promptState.collectAsState()

            InlinePrompt(
                promptRequest = promptState,
                onResponse = { response ->
                    bridge.promptManager.respond(response)
                },
                onCancel = {
                    bridge.promptManager.cancelPrompt()
                },
                onDismiss = {
                    termFocusRequester.requestFocus()
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            ConnectionStatusOverlays(
                isConnecting = bridge.isConnecting,
                isDisconnected = bridge.isDisconnected,
                hasPrompt = promptState != null,
                hostNickname = bridge.host.nickname,
                reconnectAttempts = bridge.autoReconnectAttempts,
                disconnectReason = bridge.disconnectReason,
                onClose = onDisconnectRequest,
                onReconnect = onReconnect,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * Bottom-anchored overlays that keep the user informed about the session's
 * connection state: a progress banner while (re)connecting and a disconnect
 * banner with the reason plus close/reconnect actions once the session has
 * dropped. Returning to a session must never show a silent blank terminal.
 */
@Composable
internal fun ConnectionStatusOverlays(
    isConnecting: Boolean,
    isDisconnected: Boolean,
    hasPrompt: Boolean,
    hostNickname: String,
    reconnectAttempts: Int,
    disconnectReason: DisconnectReason,
    onClose: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val einkMode = LocalEinkMode.current
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = isConnecting && !hasPrompt,
            enter = if (einkMode) EnterTransition.None else slideInVertically(initialOffsetY = { it }),
            exit = if (einkMode) ExitTransition.None else slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val terminalColors = MaterialTheme.colorScheme.terminal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(terminalColors.overlayBackground)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = if (reconnectAttempts > 0) {
                        stringResource(R.string.console_reconnecting_status, hostNickname, reconnectAttempts)
                    } else {
                        stringResource(R.string.console_connecting_status, hostNickname)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = terminalColors.overlayText,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = isDisconnected && !isConnecting && !hasPrompt,
            enter = if (einkMode) EnterTransition.None else slideInVertically(initialOffsetY = { it }),
            exit = if (einkMode) ExitTransition.None else slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val terminalColors = MaterialTheme.colorScheme.terminal
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(terminalColors.overlayBackground)
                    .then(
                        // Hard border stands in for the translucent edge on e-ink
                        if (einkMode) Modifier.border(1.dp, terminalColors.overlayText) else Modifier,
                    )
                    .padding(16.dp),
            ) {
                // A remote EOF means the session ended cleanly (e.g. the user
                // exited the shell with Ctrl+D), so present a calm "session
                // ended" state with Close as the primary action instead of an
                // alarming connection-lost banner pushing a reconnect.
                // https://github.com/connectbot/connectbot/issues/2214
                val cleanClose = disconnectReason == DisconnectReason.REMOTE_EOF
                Text(
                    text = stringResource(
                        if (cleanClose) R.string.console_session_ended else R.string.alert_disconnect_msg,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = terminalColors.overlayText,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                disconnectReasonText(disconnectReason)?.let { reasonText ->
                    Text(
                        text = reasonText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = terminalColors.overlayText,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (cleanClose) {
                        TextButton(onClick = onReconnect) {
                            Text(
                                stringResource(R.string.console_menu_reconnect),
                                color = terminalColors.overlayText,
                            )
                        }
                        Button(
                            onClick = onClose,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(stringResource(R.string.console_menu_close))
                        }
                    } else {
                        TextButton(onClick = onClose) {
                            Text(
                                stringResource(R.string.console_menu_close),
                                color = terminalColors.overlayText,
                            )
                        }
                        Button(
                            onClick = onReconnect,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(stringResource(R.string.console_menu_reconnect))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPortForwards: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSftp: (Long) -> Unit = {},
    viewModel: ConsoleViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val terminalManager = LocalTerminalManager.current
    val einkMode = LocalEinkMode.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Capture latest callback for use in effects
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnNavigateToSettings by rememberUpdatedState(onNavigateToSettings)

    LaunchedEffect(terminalManager) {
        terminalManager?.let { viewModel.setTerminalManager(it) }
    }

    // Reconnect a session that dropped in the background when the console
    // comes back to the foreground, matching pre-rewrite behavior.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onConsoleResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Read preferences
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val keyboardAlwaysVisible = remember { prefs.getBoolean(PreferenceConstants.KEY_ALWAYS_VISIBLE, false) }
    val swipeSessionsEnabled = remember {
        prefs.getBoolean(PreferenceConstants.SWIPE_SESSIONS, false)
    }
    val pgUpDnGestureEnabled = remember {
        prefs.getBoolean(PreferenceConstants.PG_UPDN_GESTURE, false)
    }
    var fullscreen by remember { mutableStateOf(prefs.getBoolean(PreferenceConstants.FULLSCREEN, false)) }
    var titleBarHide by remember { mutableStateOf(prefs.getBoolean(PreferenceConstants.TITLEBARHIDE, false)) }
    val volumeKeysChangeFontSize = remember { prefs.getBoolean(PreferenceConstants.VOLUME_FONT, true) }
    val volumeKeysSwitchTmuxPanes = remember {
        prefs.getBoolean(
            PreferenceConstants.VOLUME_TMUX_PANES,
            PreferenceConstants.VOLUME_TMUX_PANES_DEFAULT,
        )
    }
    val keepScreenAwake = remember { prefs.getBoolean(PreferenceConstants.KEEP_ALIVE, true) }

    // Keyboard state
    val hasHardwareKeyboard = rememberHasHardwareKeyboard()
    var showSoftwareKeyboard by remember { mutableStateOf(!hasHardwareKeyboard) }

    var rotation by remember(hasHardwareKeyboard) {
        val prefValue = prefs.getString(PreferenceConstants.ROTATION, PreferenceConstants.ROTATION_DEFAULT)
        mutableStateOf(
            if (prefValue == PreferenceConstants.ROTATION_DEFAULT) {
                if (hasHardwareKeyboard) {
                    PreferenceConstants.ROTATION_LANDSCAPE
                } else {
                    PreferenceConstants.ROTATION_PORTRAIT
                }
            } else {
                prefValue
            },
        )
    }

    val termFocusRequester = remember { FocusRequester() }

    var forceSize: Pair<Int, Int>? by remember { mutableStateOf(null) }

    var showMenu by remember { mutableStateOf(false) }
    var showUrlScanDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showSessionPickerDialog by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var tmuxMenuTabKey by remember { mutableStateOf<String?>(null) }
    var tmuxWindowMenuId by remember { mutableStateOf<String?>(null) }
    var tmuxRenameSessionTab by remember { mutableStateOf<ConsoleTab.TmuxSession?>(null) }
    var tmuxRenameWindowId by remember { mutableStateOf<String?>(null) }
    var tmuxKillSessionTab by remember { mutableStateOf<ConsoleTab.TmuxSession?>(null) }
    var tmuxResizeTab by remember { mutableStateOf<ConsoleTab.TmuxSession?>(null) }
    var tmuxKillWindowId by remember { mutableStateOf<String?>(null) }
    var showTmuxPalette by remember { mutableStateOf(false) }
    var showSnippetPicker by remember { mutableStateOf(false) }
    var showExtraKeyboard by remember { mutableStateOf(true) } // Start visible to show animation
    var hasPlayedKeyboardAnimation by remember { mutableStateOf(false) }
    var showTitleBar by remember { mutableStateOf(!titleBarHide) }
    // Non-state holder for auto-hide job to avoid unnecessary recompositions
    val autoHideJobRef = remember {
        object {
            var job: Job? = null
        }
    }
    var scannedUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectionController by remember { mutableStateOf<SelectionController?>(null) }
    var imeVisible by remember { mutableStateOf(false) }
    var keyboardScrollInProgress by remember { mutableStateOf(false) }
    var previousBridgeIdForImeState by remember { mutableStateOf<Long?>(null) }
    var ignoreImeHiddenForBridgeId by remember { mutableStateOf<Long?>(null) }
    var previousBridgeIdForSessionOpen by remember { mutableStateOf<Long?>(null) }
    var previousSessionOpen by remember { mutableStateOf(false) }

    val currentBridge = uiState.bridges
        .getOrNull(uiState.currentBridgeIndex)
    val currentBridgeId = currentBridge?.host?.id

    // Surround color for everything behind and around the terminal
    // (including the area under the transparent status bar): match the
    // active scheme's background so the surround never clashes with it.
    val consoleSurroundColor = currentBridge?.let { Color(it.defaultBackgroundColor) }
        ?: if (einkMode) Color.White else Color.Black

    // Get current prompt state to check if biometric prompt is active
    val promptState by currentBridge?.promptManager?.promptState?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val authBanners by currentBridge?.authBanners?.collectAsState()
        ?: remember { mutableStateOf(emptyList()) }
    val currentAuthBanner = authBanners.firstOrNull()
    var wasBiometricPromptActive by remember { mutableStateOf(false) }
    val isBiometricPromptActive = promptState is PromptRequest.BiometricPrompt

    // Check if any modal (menu or dialog) is currently active
    val anyModalActive = showMenu || showUrlScanDialog || showResizeDialog ||
        showDisconnectDialog || showTextInputDialog || showSnippetPicker ||
        isBiometricPromptActive || currentAuthBanner != null

    /**
     * Unified interaction handler for terminal and keyboard.
     * Manages visibility of the extra keyboard and title bar based on preferences.
     *
     * Intent Matrix:
     * | keyboardAlwaysVisible | titleBarHide | keyboardScrollInProgress | anyModalActive | isTerminalTap | Action                     |
     * |-----------------------|--------------|--------------------------|----------------|---------------|----------------------------|
     * | false                 | any          | false                    | false          | any           | Show KB, Start/Reset Timer |
     * | any                   | true         | false                    | false          | true          | Show TB, Start/Reset Timer |
     * | true                  | false        | any                      | any            | any           | Ensure Both Shown, No Timer|
     * | any                   | any          | true                     | any            | any           | Show KB, Cancel Timer      |
     * | any                   | any          | any                      | true           | any           | Cancel Timer               |
     *
     * @param isTerminalTap Whether this call was triggered by a terminal tap or title bar action.
     * @param isInteraction Whether this call was triggered by a user interaction (tap, key press, scroll).
     *                      If false, only the timer is managed without forcing visibility to true.
     */
    fun handleTerminalInteraction(isTerminalTap: Boolean = false, isInteraction: Boolean = true) {
        autoHideJobRef.job?.cancel()

        if (isInteraction || keyboardScrollInProgress) {
            // Show emulated keyboard on any interaction or while scrolling (unless always visible)
            if (!keyboardAlwaysVisible) {
                showExtraKeyboard = true
            }
            // Show title bar temporarily ONLY when terminal is tapped (if auto-hide enabled)
            if (titleBarHide && isTerminalTap) {
                showTitleBar = true
            }
        }

        // Ensure they are shown if they should be permanent
        if (keyboardAlwaysVisible) showExtraKeyboard = true
        if (!titleBarHide) showTitleBar = true

        // Only start the auto-hide timer if we are not actively scrolling,
        // no modal is active, and at least one element is configured to auto-hide.
        if (!keyboardScrollInProgress && !anyModalActive && (!keyboardAlwaysVisible || titleBarHide)) {
            autoHideJobRef.job = coroutineScope.launch {
                delay(AUTO_HIDE_DELAY_MS)
                // Accessing Compose State within coroutine to avoid stale closures
                // Hide keyboard if not always visible
                if (!keyboardAlwaysVisible) {
                    showExtraKeyboard = false
                }
                // Hide title bar if auto-hide is enabled
                if (titleBarHide) {
                    showTitleBar = false
                }
                // Mark animation as played after first timeout
                hasPlayedKeyboardAnimation = true
            }
        }
    }

    fun selectBridgePreservingKeyboard(index: Int) {
        val targetBridgeId = uiState.bridges.getOrNull(index)?.host?.id
        if (
            shouldPreserveSoftwareKeyboardForBridgeChange(
                previousBridgeId = currentBridgeId,
                currentBridgeId = targetBridgeId,
                showSoftwareKeyboard = showSoftwareKeyboard,
                hasHardwareKeyboard = hasHardwareKeyboard,
            )
        ) {
            ignoreImeHiddenForBridgeId = targetBridgeId
        }
        viewModel.selectBridge(index)
    }

    // Sync our state when user dismisses modals or prompts
    LaunchedEffect(anyModalActive) {
        if (!anyModalActive) {
            // When modals are dismissed, restart the auto-hide timer
            handleTerminalInteraction(isInteraction = false)
        }
    }

    // Apply fullscreen mode and display cutout settings
    LaunchedEffect(fullscreen) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window

        try {
            if (fullscreen) {
                // Enable fullscreen mode - hide system bars
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // Disable fullscreen mode - show system bars
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        } catch (e: IllegalArgumentException) {
            // Handle foldable device state issues
            Timber.e(e, "Error setting fullscreen mode (foldable device?)")
        }
    }

    // While the title bar is hidden the transparent status bar sits directly
    // on the terminal, so it needs icons that contrast with the terminal
    // background (light icons on dark schemes, dark icons on light ones)
    // regardless of the app theme; restore the theme's appearance once the
    // title bar returns.
    val rootView = LocalView.current
    val titleBarVisible = !titleBarHide || showTitleBar
    DisposableEffect(rootView, titleBarVisible, consoleSurroundColor) {
        val window = (rootView.context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val previousLightStatusBars = controller?.isAppearanceLightStatusBars
        if (controller != null && !titleBarVisible) {
            controller.isAppearanceLightStatusBars = consoleSurroundColor.luminance() > 0.5f
        }
        onDispose {
            if (controller != null && previousLightStatusBars != null) {
                controller.isAppearanceLightStatusBars = previousLightStatusBars
            }
        }
    }

    // Navigate back if all bridges are closed (after initial loading)
    LaunchedEffect(uiState.bridges.size, uiState.isLoading) {
        if (uiState.bridges.isEmpty() && !uiState.isLoading) {
            currentOnNavigateBack()
        }
    }

    // Request focus on terminal when screen appears (e.g., returning from navigation)
    LaunchedEffect(Unit) {
        termFocusRequester.requestFocus()
        // Initial auto-hide timer start (without forcing show)
        handleTerminalInteraction(isInteraction = false)
    }

    // Track actual IME visibility using WindowInsets to detect user dismissing with back button
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeHeight = with(density) { imeInsets.getBottom(density).toDp() }
    val systemImeVisible = imeHeight > 0.dp
    var hasImeBeenVisible by remember { mutableStateOf(false) }
    val latestCurrentBridgeId by rememberUpdatedState(currentBridgeId)

    // Sync our state when user dismisses IME externally (back button)
    LaunchedEffect(systemImeVisible) {
        if (systemImeVisible) {
            hasImeBeenVisible = true
            ignoreImeHiddenForBridgeId = null
        }
        // Only sync to hidden state after IME has been visible at least once.
        // This prevents canceling the keyboard before it has a chance to show.
        if (hasImeBeenVisible && !systemImeVisible && showSoftwareKeyboard) {
            if (ignoreImeHiddenForBridgeId == latestCurrentBridgeId) {
                termFocusRequester.requestFocus()
            } else {
                showSoftwareKeyboard = false
            }
        }
        imeVisible = systemImeVisible
    }

    // Show software keyboard after biometric prompt completes (unless hardware keyboard is connected)
    LaunchedEffect(isBiometricPromptActive) {
        if (wasBiometricPromptActive && !isBiometricPromptActive && !hasHardwareKeyboard) {
            showSoftwareKeyboard = true
        }
        wasBiometricPromptActive = isBiometricPromptActive
    }

    val hasMultipleSessions = uiState.bridges.size > 1 && !uiState.isLoading
    val swipeBetweenSessions = swipeSessionsEnabled && hasMultipleSessions
    val terminalSelectionActive = selectionController?.isSelectionActive == true
    // These values are computed from bridge state and will recompute when uiState.revision changes
    val sessionOpen = currentBridge?.isSessionOpen == true
    val disconnected = currentBridge?.isDisconnected == true
    val canForwardPorts = currentBridge?.canFowardPorts() == true
    val canTransferFiles = currentBridge?.canTransferFiles() == true
    val snackbarHostState = remember { SnackbarHostState() }

    val isConnectionActive = currentBridge != null && !disconnected
    val keepScreenOn = keepScreenAwake && isConnectionActive

    LaunchedEffect(currentBridgeId) {
        if (
            shouldPreserveSoftwareKeyboardForBridgeChange(
                previousBridgeId = previousBridgeIdForImeState,
                currentBridgeId = currentBridgeId,
                showSoftwareKeyboard = showSoftwareKeyboard,
                hasHardwareKeyboard = hasHardwareKeyboard,
            )
        ) {
            ignoreImeHiddenForBridgeId = currentBridgeId
        }
        previousBridgeIdForImeState = currentBridgeId
    }

    // Show software keyboard when the current session transitions to open,
    // while preserving the user's current keyboard state across bridge switches.
    LaunchedEffect(currentBridgeId, sessionOpen, hasHardwareKeyboard) {
        if (
            shouldShowSoftwareKeyboardForSessionOpen(
                previousBridgeId = previousBridgeIdForSessionOpen,
                previousSessionOpen = previousSessionOpen,
                currentBridgeId = currentBridgeId,
                sessionOpen = sessionOpen,
                hasHardwareKeyboard = hasHardwareKeyboard,
            )
        ) {
            showSoftwareKeyboard = true
        }
        previousBridgeIdForSessionOpen = currentBridgeId
        previousSessionOpen = sessionOpen
    }

    // Reset selection controller when bridge changes
    LaunchedEffect(currentBridge) {
        selectionController = null
        if (currentBridge != null) {
            termFocusRequester.requestFocus()
        }
    }

    // Initialize forceSize from profile when bridge changes
    LaunchedEffect(currentBridge) {
        currentBridge?.let { bridge ->
            val rows = bridge.profileForceSizeRows
            val cols = bridge.profileForceSizeColumns
            if (rows != null && cols != null) {
                forceSize = Pair(rows, cols)
            } else {
                forceSize = null
            }
        }
    }

    // Show snackbar for network status messages
    LaunchedEffect(Unit) {
        viewModel.networkStatusMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Show snackbar on each open when connections won't persist in background
    val notificationWarningMessage = stringResource(R.string.notification_permission_console_warning)
    val settingsLabel = stringResource(R.string.list_menu_settings)
    LaunchedEffect(Unit) {
        if (viewModel.shouldShowNotificationWarning()) {
            val result = snackbarHostState.showSnackbar(
                message = notificationWarningMessage,
                actionLabel = settingsLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                currentOnNavigateToSettings()
            }
        }
    }

    // Show snackbar when there's an error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true,
            )
        }
    }

    val noUrlHandlerMessage = stringResource(R.string.console_url_no_handler)

    val urlNotSupportedMessage = stringResource(R.string.console_url_not_supported)

    fun openUrl(url: String) {
        UrlUtils.openUrl(context, url).onFailure { e ->
            coroutineScope.launch {
                val message = if (e is ActivityNotFoundException) {
                    noUrlHandlerMessage
                } else {
                    urlNotSupportedMessage
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    var titleBarHeight by remember { mutableStateOf(0.dp) }

    fun pasteClipboardContents() {
        currentBridge?.let { bridge ->
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()

            if (!clip.isNullOrBlank()) {
                val normalized = TerminalTextUtils.normalizeLineBreaks(clip)
                val paneTerminal = uiState.currentPaneTerminal
                if (paneTerminal != null && uiState.currentTab is ConsoleTab.TmuxSession) {
                    paneTerminal.paste(normalized)
                } else {
                    bridge.injectString(normalized)
                }
            }
        }
    }

    val sessionCopiedMessage = stringResource(R.string.console_session_copied)

    fun copySessionToClipboard() {
        val bridge = currentBridge ?: return
        val sessionText = TerminalTextUtils.buildSessionText(
            TerminalSessionReader.readSessionLines(bridge.terminalEmulator),
        )
        if (sessionText.isEmpty()) return

        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(bridge.host.nickname, sessionText),
        )
        // Android 13+ shows its own confirmation UI when the clipboard changes
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(sessionCopiedMessage)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
            .fillMaxSize()
            .then(if (keepScreenOn) Modifier.keepScreenOn() else Modifier),
        // The console is a terminal screen: keep everything behind and around
        // the terminal (including the area under the transparent status bar)
        // matched to the terminal's own background instead of the theme.
        containerColor = consoleSurroundColor,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .union(WindowInsets.imeAnimationTarget),
    ) { innerPadding ->
        val handleShortcut: (KeyEvent) -> Boolean = { keyEvent ->
            handleConsoleShortcut(
                keyEvent = keyEvent,
                volumeKeysChangeFontSize = volumeKeysChangeFontSize,
                copySelection = { selectionController?.copySelection() },
                pasteClipboardContents = { pasteClipboardContents() },
                increaseFontSize = { currentBridge?.increaseFontSize() },
                decreaseFontSize = { currentBridge?.decreaseFontSize() },
                tmuxPaneNavigation = volumeKeysSwitchTmuxPanes &&
                    uiState.currentTab is ConsoleTab.TmuxSession &&
                    uiState.currentPaneTerminal != null,
                nextPane = { viewModel.selectPane(1) },
                previousPane = { viewModel.selectPane(-1) },
            )
        }

        // Terminal content with keyboard overlay
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    top = if (!titleBarHide) titleBarHeight else innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                )
                .windowInsetsPadding(WindowInsets.imeAnimationTarget)
                .onPreviewKeyEvent(handleShortcut),
        ) {
            when {
                uiState.isLoading -> {
                    LoadingScreen(modifier = Modifier.fillMaxSize())
                }

                uiState.bridges.isNotEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        val bridge = uiState.bridges[uiState.currentBridgeIndex]
                        val pageGestureModifier = if (pgUpDnGestureEnabled) {
                            Modifier.pageUpDownGesture(
                                selectionActive = terminalSelectionActive,
                                onPageKey = { key ->
                                    bridge.keyHandler.sendPressedKey(key)
                                    handleTerminalInteraction(isInteraction = false)
                                },
                            )
                        } else {
                            Modifier
                        }
                        val swipeModifier = if (swipeBetweenSessions) {
                            Modifier.sessionSwipeNavigation(
                                currentIndex = uiState.currentBridgeIndex,
                                sessionCount = uiState.bridges.size,
                                selectionActive = terminalSelectionActive,
                                onSwipeToSession = { index -> selectBridgePreservingKeyboard(index) },
                                onInteraction = { handleTerminalInteraction(isInteraction = false) },
                            )
                        } else {
                            Modifier
                        }
                        val terminalModifier = pageGestureModifier.then(swipeModifier)

                        val tmuxTab = uiState.currentTab as? ConsoleTab.TmuxSession
                        val tmuxHostState = tmuxTab?.bridge?.tmux?.state?.collectAsState()?.value
                        val tmuxSession = tmuxHostState?.sessions?.find { it.id == tmuxTab.sessionId }
                        val paneTerminal = uiState.currentPaneTerminal

                        if (tmuxTab != null &&
                            (tmuxSession?.attachState != TmuxAttachState.ATTACHED || paneTerminal?.emulator == null)
                        ) {
                            // Detached or still attaching: dimmed snapshot face.
                            TmuxSnapshotPage(
                                sessionName = tmuxTab.sessionName,
                                snapshot = tmuxSession?.snapshot,
                                isAttaching = tmuxSession?.attachState == TmuxAttachState.ATTACHING,
                                onAttach = { viewModel.selectTab(tmuxTab.key) },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (tmuxTab != null && paneTerminal != null) {
                            val tmuxTarget = tmuxTab.bridge.tmux?.currentTarget?.collectAsState()?.value
                            val tmuxWindow = tmuxSession?.windows?.find { it.id == tmuxTarget?.windowId }
                            val pane = tmuxWindow?.panes?.find { it.id == paneTerminal.paneId }
                            val swipeDensity = LocalDensity.current.density
                            val tmuxSwipeModifier = Modifier.tmuxSwipeNavigation(
                                selectionActive = terminalSelectionActive,
                                onSwipePane = { direction -> viewModel.selectPane(direction) },
                                onSwipeWindow = { direction -> viewModel.stepWindow(direction) },
                                onInteraction = { handleTerminalInteraction(isInteraction = false) },
                                density = swipeDensity,
                            )
                            key(tmuxTab.key) {
                                ConsoleTerminalPage(
                                    bridge = bridge,
                                    isActive = true,
                                    keyboardAlwaysVisible = keyboardAlwaysVisible,
                                    showSoftwareKeyboard = showSoftwareKeyboard,
                                    forceSize = forceSize,
                                    termFocusRequester = termFocusRequester,
                                    showExtraKeyboard = showExtraKeyboard,
                                    hasPlayedKeyboardAnimation = hasPlayedKeyboardAnimation,
                                    imeVisible = imeVisible,
                                    handleTerminalInteraction = { handleTerminalInteraction(isTerminalTap = true) },
                                    onShowSoftwareKeyboardChange = { showSoftwareKeyboard = it },
                                    onImeVisibilityChange = { imeVisible = it },
                                    onTextInputRequest = { showTextInputDialog = true },
                                    onSnippetsRequest = { showSnippetPicker = true },
                                    onDisconnectRequest = {
                                        bridge.dispatchDisconnect(DisconnectReason.USER_REQUESTED)
                                    },
                                    onKeyboardScrollInProgressChange = { inProgress ->
                                        keyboardScrollInProgress = inProgress
                                        handleTerminalInteraction()
                                    },
                                    onSelectionControllerChange = { selectionController = it },
                                    onOpenUrl = ::openUrl,
                                    onPasteRequest = ::pasteClipboardContents,
                                    onInterceptKey = handleShortcut,
                                    onReconnect = { viewModel.reconnect(bridge) },
                                    snackbarHostState = snackbarHostState,
                                    modifier = Modifier.fillMaxSize(),
                                    terminalModifier = pageGestureModifier.then(tmuxSwipeModifier),
                                    tmuxPane = paneTerminal,
                                    tmuxForcedSize = pane?.let { Pair(it.height, it.width) },
                                )
                            }
                            val tmuxPanes = tmuxWindow?.panes
                            if (tmuxPanes != null && tmuxPanes.size > 1) {
                                PaneDotsIndicator(
                                    count = tmuxPanes.size,
                                    selectedIndex = tmuxPanes
                                        .indexOfFirst { it.id == paneTerminal.paneId }
                                        .coerceAtLeast(0),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp),
                                )
                            }
                        } else {
                            key(bridge.host.id) {
                                ConsoleTerminalPage(
                                    bridge = bridge,
                                    isActive = true,
                                    keyboardAlwaysVisible = keyboardAlwaysVisible,
                                    showSoftwareKeyboard = showSoftwareKeyboard,
                                    forceSize = forceSize,
                                    termFocusRequester = termFocusRequester,
                                    showExtraKeyboard = showExtraKeyboard,
                                    hasPlayedKeyboardAnimation = hasPlayedKeyboardAnimation,
                                    imeVisible = imeVisible,
                                    handleTerminalInteraction = { handleTerminalInteraction(isTerminalTap = true) },
                                    onShowSoftwareKeyboardChange = { showSoftwareKeyboard = it },
                                    onImeVisibilityChange = { imeVisible = it },
                                    onTextInputRequest = { showTextInputDialog = true },
                                    onSnippetsRequest = { showSnippetPicker = true },
                                    onDisconnectRequest = {
                                        bridge.dispatchDisconnect(DisconnectReason.USER_REQUESTED)
                                    },
                                    onKeyboardScrollInProgressChange = { inProgress ->
                                        keyboardScrollInProgress = inProgress
                                        handleTerminalInteraction()
                                    },
                                    onSelectionControllerChange = { selectionController = it },
                                    onOpenUrl = ::openUrl,
                                    onPasteRequest = ::pasteClipboardContents,
                                    onInterceptKey = handleShortcut,
                                    onReconnect = { viewModel.reconnect(bridge) },
                                    snackbarHostState = snackbarHostState,
                                    modifier = Modifier.fillMaxSize(),
                                    terminalModifier = terminalModifier,
                                )
                            }
                        }

                        // One-tap persistent-session offer for this host
                        if (tmuxTab == null && uiState.tmuxOfferHostId == bridge.host.id) {
                            TmuxOfferBanner(
                                onStart = { viewModel.startTmuxSession() },
                                onDismiss = { viewModel.dismissTmuxOffer() },
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    }
                }
            }
        }

        // tmux management dialogs
        (tmuxMenuTabKey?.let { key -> uiState.tabs.find { it.key == key } } as? ConsoleTab.TmuxSession)?.let { menuTab ->
            TmuxActionMenuDialog(
                title = menuTab.sessionName,
                actions = buildList {
                    add(stringResource(R.string.tmux_menu_rename) to { tmuxRenameSessionTab = menuTab })
                    if (menuTab.attachState == TmuxAttachState.ATTACHED) {
                        add(stringResource(R.string.tmux_menu_detach) to { viewModel.detachTmuxTab(menuTab) })
                        add(
                            stringResource(R.string.tmux_menu_resize) to {
                                val attachedElsewhere = menuTab.bridge.tmux?.state?.value
                                    ?.session(menuTab.sessionId)?.attachedCount ?: 0
                                if (attachedElsewhere > 1) {
                                    tmuxResizeTab = menuTab
                                } else {
                                    viewModel.resizeTmuxSessionToScreen(menuTab)
                                }
                            },
                        )
                    }
                    add(stringResource(R.string.tmux_menu_kill_session) to { tmuxKillSessionTab = menuTab })
                },
                onDismiss = { tmuxMenuTabKey = null },
            )
        }

        tmuxWindowMenuId?.let { windowId ->
            val windowName = (uiState.currentTab as? ConsoleTab.TmuxSession)
                ?.bridge?.tmux?.state?.value
                ?.session((uiState.currentTab as ConsoleTab.TmuxSession).sessionId)
                ?.windows?.find { it.id == windowId }?.name ?: windowId
            TmuxActionMenuDialog(
                title = windowName,
                actions = listOf(
                    stringResource(R.string.tmux_menu_rename) to { tmuxRenameWindowId = windowId },
                    stringResource(R.string.tmux_menu_move_left) to { viewModel.moveTmuxWindow(windowId, -1) },
                    stringResource(R.string.tmux_menu_move_right) to { viewModel.moveTmuxWindow(windowId, 1) },
                    stringResource(R.string.tmux_menu_kill_window) to { tmuxKillWindowId = windowId },
                ),
                onDismiss = { tmuxWindowMenuId = null },
            )
        }

        tmuxRenameSessionTab?.let { renameTab ->
            TmuxRenameDialog(
                title = stringResource(R.string.tmux_rename_session_title),
                initialName = renameTab.sessionName,
                onConfirm = { name ->
                    viewModel.renameTmuxSession(renameTab, name)
                    tmuxRenameSessionTab = null
                },
                onDismiss = { tmuxRenameSessionTab = null },
            )
        }

        tmuxRenameWindowId?.let { windowId ->
            TmuxRenameDialog(
                title = stringResource(R.string.tmux_rename_window_title),
                initialName = "",
                onConfirm = { name ->
                    viewModel.renameTmuxWindow(windowId, name)
                    tmuxRenameWindowId = null
                },
                onDismiss = { tmuxRenameWindowId = null },
            )
        }

        tmuxResizeTab?.let { resizeTab ->
            TmuxConfirmDialog(
                title = stringResource(R.string.tmux_menu_resize),
                message = stringResource(R.string.tmux_resize_confirm_message),
                confirmLabel = stringResource(R.string.tmux_menu_resize),
                onConfirm = {
                    viewModel.resizeTmuxSessionToScreen(resizeTab)
                    tmuxResizeTab = null
                },
                onDismiss = { tmuxResizeTab = null },
            )
        }

        tmuxKillSessionTab?.let { killTab ->
            TmuxKillConfirmDialog(
                message = stringResource(R.string.tmux_kill_session_message, killTab.sessionName),
                onConfirm = {
                    viewModel.killTmuxSession(killTab)
                    tmuxKillSessionTab = null
                },
                onDismiss = { tmuxKillSessionTab = null },
            )
        }

        tmuxKillWindowId?.let { windowId ->
            TmuxKillConfirmDialog(
                message = stringResource(R.string.tmux_kill_window_message),
                onConfirm = {
                    viewModel.killTmuxWindow(windowId)
                    tmuxKillWindowId = null
                },
                onDismiss = { tmuxKillWindowId = null },
            )
        }

        if (showTmuxPalette) {
            TmuxCommandPaletteSheet(
                history = uiState.tmuxPaletteHistory,
                onRunCommand = { viewModel.runTmuxCommand(it) },
                onDismiss = { showTmuxPalette = false },
            )
        }

        // Dialogs
        if (showUrlScanDialog) {
            UrlScanDialog(
                urls = scannedUrls,
                onDismiss = { showUrlScanDialog = false },
                onUrlClick = { url ->
                    openUrl(url)
                },
            )
        }

        currentAuthBanner?.let { banner ->
            AuthBannerDialog(
                banner = banner,
                onDismiss = {
                    currentBridge?.dismissAuthBanner(banner.id)
                },
            )
        }

        if (showResizeDialog && currentBridge != null) {
            ResizeDialog(
                currentBridge = currentBridge,
                isForced = forceSize != null,
                onDismiss = { showResizeDialog = false },
                onResize = { width, height ->
                    // Resize the terminal emulator
                    forceSize = Pair(height, width)
                },
                onDisableForceSize = {
                    // Disable force size for this session
                    forceSize = null
                },
            )
        }

        if (showDisconnectDialog && currentBridge != null) {
            HostDisconnectDialog(
                host = currentBridge.host,
                onDismiss = { showDisconnectDialog = false },
                onConfirm = {
                    showDisconnectDialog = false
                    currentBridge.dispatchDisconnect(DisconnectReason.USER_REQUESTED)
                },
            )
        }

        if (showSessionPickerDialog && hasMultipleSessions) {
            SessionPickerDialog(
                bridges = uiState.bridges,
                currentBridgeIndex = uiState.currentBridgeIndex,
                onDismiss = { showSessionPickerDialog = false },
                onSelectBridge = { index ->
                    showSessionPickerDialog = false
                    selectBridgePreservingKeyboard(index)
                },
            )
        }

        if (showSnippetPicker && currentBridge != null) {
            SnippetPickerSheet(
                hostId = currentBridge.host.id,
                onSend = { text ->
                    currentBridge.injectString(TerminalTextUtils.normalizeLineBreaks(text))
                },
                onDismiss = {
                    showSnippetPicker = false
                    termFocusRequester.requestFocus()
                },
            )
        }

        if (showTextInputDialog && promptState == null && currentBridge != null) {
            // TODO: Get selected text from TerminalEmulator when selection is implemented
            val selectedText = ""

            FloatingTextInputDialog(
                bridge = currentBridge,
                initialText = selectedText,
                onDismiss = {
                    showTextInputDialog = false
                    termFocusRequester.requestFocus()
                },
            )
        }

        // Overlay TopAppBar - always visible when titleBarHide is false,
        // or temporarily visible when titleBarHide is true and showTitleBar is true
        if (!titleBarHide || showTitleBar) {
            val density = LocalDensity.current
            Column(
                modifier = Modifier.onSizeChanged {
                    titleBarHeight = with(density) { it.height.toDp() }
                },
            ) {
                TopAppBar(
                    title = {
                        Text(
                            currentBridge?.host?.nickname
                                ?: stringResource(R.string.console_default_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.testTag("top_app_bar"),
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                stringResource(R.string.button_back),
                            )
                        }
                    },
                    colors = if (titleBarHide && !einkMode) {
                        // Translucent overlay when auto-hide is enabled
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        )
                    } else {
                        // Solid color when permanently visible
                        TopAppBarDefaults.topAppBarColors()
                    },
                    actions = {
                        if (hasMultipleSessions) {
                            IconButton(onClick = { showSessionPickerDialog = true }) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = stringResource(R.string.console_switch_session),
                                )
                            }
                        }

                        // Text Input button
                        IconButton(
                            onClick = { showTextInputDialog = true },
                            enabled = currentBridge != null,
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.console_menu_text_input),
                            )
                        }

                        // Paste button - always visible
                        IconButton(
                            onClick = {
                                pasteClipboardContents()
                            },
                            enabled = currentBridge != null,
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.console_menu_paste),
                            )
                        }

                        // More menu
                        Box {
                            IconButton(
                                onClick = {
                                    // Refresh menu state to update enabled/disabled items
                                    viewModel.refreshMenuState()
                                    showMenu = true
                                },
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.button_more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = {
                                    showMenu = false
                                    // Hide title bar again after closing menu if auto-hide is enabled
                                    if (titleBarHide) {
                                        showTitleBar = false
                                    }
                                    termFocusRequester.requestFocus()
                                },
                            ) {
                                // Reconnect (shown only when disconnected)
                                if (disconnected) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.console_menu_reconnect)) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.reconnect(currentBridge)
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                        },
                                    )
                                }

                                if (hasMultipleSessions) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.console_previous_session)) },
                                        onClick = {
                                            showMenu = false
                                            selectBridgePreservingKeyboard(uiState.currentBridgeIndex - 1)
                                        },
                                        enabled = uiState.currentBridgeIndex > 0,
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.console_next_session)) },
                                        onClick = {
                                            showMenu = false
                                            selectBridgePreservingKeyboard(uiState.currentBridgeIndex + 1)
                                        },
                                        enabled = uiState.currentBridgeIndex < uiState.bridges.lastIndex,
                                    )
                                }

                                // Disconnect/Close
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (!sessionOpen && disconnected) {
                                                stringResource(R.string.console_menu_close)
                                            } else {
                                                stringResource(R.string.list_host_disconnect)
                                            },
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDisconnectDialog = true
                                    },
                                    enabled = currentBridge != null,
                                    leadingIcon = {
                                        Icon(Icons.Default.LinkOff, null)
                                    },
                                )

                                // URL Scan
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_menu_urlscan)) },
                                    onClick = {
                                        showMenu = false
                                        currentBridge?.let { bridge ->
                                            scannedUrls = bridge.scanForURLs()
                                            showUrlScanDialog = true
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Link, contentDescription = null)
                                    },
                                    enabled = currentBridge != null,
                                )

                                // Copy entire session (screen plus scrollback)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_menu_copy_session)) },
                                    onClick = {
                                        showMenu = false
                                        copySessionToClipboard()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    },
                                    enabled = currentBridge != null,
                                )

                                // Resize
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_menu_resize)) },
                                    onClick = {
                                        showMenu = false
                                        showResizeDialog = true
                                    },
                                    enabled = sessionOpen,
                                )

                                // tmux command palette (attached tmux tab only)
                                if (uiState.currentTab is ConsoleTab.TmuxSession &&
                                    uiState.currentPaneTerminal != null
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tmux_menu_command)) },
                                        onClick = {
                                            showMenu = false
                                            showTmuxPalette = true
                                        },
                                    )

                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.tmux_menu_load_earlier)) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.loadEarlierTmuxHistory()
                                        },
                                    )
                                }

                                // Port Forwards (if available)
                                if (canForwardPorts) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.console_menu_portforwards)) },
                                        onClick = {
                                            showMenu = false
                                            currentBridge.host.id.let {
                                                onNavigateToPortForwards(
                                                    it,
                                                )
                                            }
                                        },
                                        enabled = sessionOpen,
                                    )
                                }

                                // SFTP file browser (if available)
                                if (canTransferFiles) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.console_menu_files)) },
                                        onClick = {
                                            showMenu = false
                                            currentBridge.host.id.let { onNavigateToSftp(it) }
                                        },
                                        enabled = sessionOpen,
                                    )
                                }

                                // Fullscreen toggle
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.pref_fullscreen_title)) },
                                    onClick = {
                                        fullscreen = !fullscreen
                                        prefs.edit { putBoolean("fullscreen", fullscreen) }
                                    },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = fullscreen,
                                            onCheckedChange = null,
                                        )
                                    },
                                )

                                // Title bar auto-hide toggle
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.pref_titlebarhide_title)) },
                                    onClick = {
                                        titleBarHide = !titleBarHide
                                        prefs.edit { putBoolean("titlebarhide", titleBarHide) }
                                        handleTerminalInteraction(isTerminalTap = true)
                                    },
                                    trailingIcon = {
                                        Checkbox(
                                            checked = titleBarHide,
                                            onCheckedChange = null,
                                        )
                                    },
                                )
                            }
                        }
                    },
                )

                // Persistent tab strip: host shells and their tmux sessions
                if (uiState.tabs.size > 1) {
                    SessionTabStrip(
                        tabs = uiState.tabs.map { tab ->
                            when (tab) {
                                is ConsoleTab.HostShell -> SessionTabData(
                                    key = tab.key,
                                    nickname = tab.bridge.host.nickname,
                                    color = tab.bridge.host.color,
                                    isDisconnected = tab.bridge.isDisconnected,
                                )

                                is ConsoleTab.TmuxSession -> SessionTabData(
                                    key = tab.key,
                                    nickname = tab.sessionName,
                                    color = tab.bridge.host.color,
                                    isDisconnected = tab.attachState == TmuxAttachState.DETACHED,
                                    isTmux = true,
                                    isAttaching = tab.attachState == TmuxAttachState.ATTACHING,
                                    bellBadge = tab.bellBadge,
                                    activityBadge = tab.activityBadge,
                                )
                            }
                        },
                        selectedKey = uiState.currentTab?.key,
                        onSelectTab = { key ->
                            val tappedBridge = uiState.tabs.find { it.key == key }?.bridge
                            val index = tappedBridge
                                ?.let { b -> uiState.bridges.indexOfFirst { it === b } } ?: -1
                            if (index != -1 && index != uiState.currentBridgeIndex) {
                                selectBridgePreservingKeyboard(index)
                            }
                            viewModel.selectTab(key)
                            handleTerminalInteraction(isInteraction = false)
                        },
                        onLongPressTab = { key ->
                            if (uiState.tabs.find { it.key == key } is ConsoleTab.TmuxSession) {
                                tmuxMenuTabKey = key
                            }
                        },
                        containerColor = if (titleBarHide) {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    )
                }

                // Window strip for the active tmux session (auto-hides with chrome)
                (uiState.currentTab as? ConsoleTab.TmuxSession)?.let { stripTab ->
                    val stripHostState = stripTab.bridge.tmux?.state?.collectAsState()?.value
                    val stripSession = stripHostState?.sessions?.find { it.id == stripTab.sessionId }
                    val stripTarget = stripTab.bridge.tmux?.currentTarget?.collectAsState()?.value
                    if (stripSession != null &&
                        stripSession.attachState == TmuxAttachState.ATTACHED &&
                        stripSession.windows.isNotEmpty()
                    ) {
                        TmuxWindowStrip(
                            windows = stripSession.windows,
                            activeWindowId = stripTarget
                                ?.takeIf { it.sessionId == stripTab.sessionId }?.windowId
                                ?: stripSession.activeWindowId,
                            onSelectWindow = { windowId ->
                                viewModel.selectWindow(windowId)
                                handleTerminalInteraction(isInteraction = false)
                            },
                            onLongPressWindow = { windowId -> tmuxWindowMenuId = windowId },
                            onNewWindow = { viewModel.newTmuxWindow() },
                            accentColor = parseHostColor(stripTab.bridge.host.color),
                        )
                    }
                }
            }

            // Progress indicator for OSC 9;4 progress reporting
            val progressState = uiState.progressState
            if (progressState != null && progressState != ProgressState.HIDDEN) {
                val progressColor = when (progressState) {
                    ProgressState.ERROR -> MaterialTheme.colorScheme.error
                    ProgressState.WARNING -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }

                if (progressState == ProgressState.INDETERMINATE) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = titleBarHeight),
                        color = progressColor,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { uiState.progressValue / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = titleBarHeight),
                        color = progressColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun HostDisconnectDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(stringResource(R.string.disconnect_host_alert, host.nickname))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(stringResource(R.string.button_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_no))
            }
        },
    )
}

@Composable
private fun SessionPickerDialog(
    bridges: List<TerminalBridge>,
    currentBridgeIndex: Int,
    onDismiss: () -> Unit,
    onSelectBridge: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.console_switch_session))
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(
                    count = bridges.size,
                    key = { index -> bridges[index].host.id },
                ) { index ->
                    val bridge = bridges[index]
                    TextButton(
                        onClick = { onSelectBridge(index) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (index == currentBridgeIndex) {
                                "\u2022 ${bridge.host.nickname}"
                            } else {
                                bridge.host.nickname
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
    )
}
