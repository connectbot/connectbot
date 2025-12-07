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

package org.connectbot.ui.screens.console

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.terminal.Terminal
import org.connectbot.ui.LoadingScreen
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.components.FloatingTextInputDialog
import org.connectbot.ui.components.InlinePrompt
import org.connectbot.ui.components.ResizeDialog
import org.connectbot.ui.components.TERMINAL_KEYBOARD_HEIGHT_DP
import org.connectbot.ui.components.TerminalKeyboard
import org.connectbot.ui.components.UrlScanDialog

/**
 * Check if a hardware keyboard is currently attached to the device.
 * Detects QWERTY and 12-key hardware keyboards, including Bluetooth keyboards.
 */
@Composable
private fun rememberHasHardwareKeyboard(): Boolean {
	val context = LocalContext.current
	val configuration = LocalConfiguration.current

	return remember(configuration) {
		val keyboardType = configuration.keyboard
		keyboardType == android.content.res.Configuration.KEYBOARD_QWERTY ||
				keyboardType == android.content.res.Configuration.KEYBOARD_12KEY
	}
}

/**
 * Height of the title bar in dp.
 */
private const val TITLE_BAR_HEIGHT_DP = 64

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(
    hostId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToPortForwards: (Long) -> Unit,
) {
    val context = LocalContext.current
    val terminalManager = LocalTerminalManager.current
    val viewModel = remember(hostId) { ConsoleViewModel(terminalManager, hostId) }
    val uiState by viewModel.uiState.collectAsState()

    // Read preferences
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val keyboardAlwaysVisible = remember { prefs.getBoolean("alwaysvisible", false) }
    var fullscreen by remember { mutableStateOf(prefs.getBoolean("fullscreen", false)) }
    var titleBarHide by remember { mutableStateOf(prefs.getBoolean("titlebarhide", false)) }

    // Keyboard state
    val hasHardwareKeyboard = rememberHasHardwareKeyboard()
    var showSoftwareKeyboard by remember { mutableStateOf(!hasHardwareKeyboard) }

    val termFocusRequester = remember { FocusRequester() }

    var forceSize: Pair<Int, Int>? by remember { mutableStateOf(null) }

    var showMenu by remember { mutableStateOf(false) }
    var showUrlScanDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }
    var showExtraKeyboard by remember { mutableStateOf(true) } // Start visible to show animation
    var hasPlayedKeyboardAnimation by remember { mutableStateOf(false) }
    var showTitleBar by remember { mutableStateOf(!titleBarHide) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var scannedUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var imeVisible by remember { mutableStateOf(false) }
    var isFirstLoad by remember(uiState.currentBridgeIndex) { mutableStateOf(true) }

    // One-time initialization
    LaunchedEffect(Unit) {
        // Disable StrictMode for terminal I/O (matches old ConsoleActivity behavior)
        // Terminal key input triggers immediate network writes which need to happen on main thread
        // for responsiveness. This is intentional behavior carried over from the original implementation.
        android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.LAX)
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
            android.util.Log.e(
                "ConsoleScreen",
                "Error setting fullscreen mode (foldable device?)",
                e
            )
        }
    }

    // Navigate back if all bridges are closed (after initial loading)
    LaunchedEffect(uiState.bridges.size, uiState.isLoading) {
        if (uiState.bridges.isEmpty() && !uiState.isLoading) {
            onNavigateBack()
        }
    }

    // Track actual IME visibility using WindowInsets to detect user dismissing with back button
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeHeight = with(density) { imeInsets.getBottom(density).toDp() }
    val systemImeVisible = imeHeight > 0.dp

    // Sync our state when user dismisses IME externally (back button)
    LaunchedEffect(systemImeVisible) {
        // If system says IME is hidden but we think we should show it, update our state
        if (!systemImeVisible && showSoftwareKeyboard) {
            showSoftwareKeyboard = false
        }
        imeVisible = systemImeVisible
    }

    // Unified auto-hide timer for both keyboard and title bar
    LaunchedEffect(lastInteractionTime, keyboardAlwaysVisible, titleBarHide) {
        // Only run the timer if there's something to auto-hide
        if (!keyboardAlwaysVisible || titleBarHide) {
            delay(3000)
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

    val currentBridge = uiState.bridges.getOrNull(uiState.currentBridgeIndex)
    // These values are computed from bridge state and will recompute when uiState.revision changes
    val sessionOpen = currentBridge?.isSessionOpen == true
    val disconnected = currentBridge?.isDisconnected == true
    val canForwardPorts = currentBridge?.canFowardPorts() == true
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when there's an error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true
            )
        }
    }

    fun handleTerminalInteraction() {
        // Show emulated keyboard when terminal is tapped (unless always visible)
        if (!keyboardAlwaysVisible) {
            showExtraKeyboard = true
        }
        // Show title bar temporarily when terminal is tapped (if auto-hide enabled)
        if (titleBarHide) {
            showTitleBar = true
        }
        // Reset the unified timer
        lastInteractionTime = System.currentTimeMillis()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .union(WindowInsets.imeAnimationTarget),
    ) { innerPadding ->
        // Show tabs if multiple terminals
        if (uiState.bridges.size > 1) {
            PrimaryTabRow(
                selectedTabIndex = uiState.currentBridgeIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                uiState.bridges.forEachIndexed { index, bridge ->
                    Tab(
                        selected = index == uiState.currentBridgeIndex,
                        onClick = { viewModel.selectBridge(index) },
                        text = {
                            Text(
                                bridge.host.nickname,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }

        // Terminal content with keyboard overlay
        // This Box is transparent to accessibility - it's just for layout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.imeAnimationTarget)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingScreen(modifier = Modifier.fillMaxSize())
                }

                uiState.bridges.isNotEmpty() -> {
                    // TODO(Terminal): Re-implement support for switching between terminals
                    // For now, just show the current bridge directly without HorizontalPager
                    // to avoid accessibility issues. Maybe a tab strip across the top for
                    // small screen devices and a list of hosts on the left for large screen.

                    val bridge = uiState.bridges[uiState.currentBridgeIndex]

                    // Terminal view fills entire space with insets padding
                    // to avoid content being cut off by screen curves/notches
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = if (!titleBarHide) TITLE_BAR_HEIGHT_DP.dp else 0.dp,
                                bottom = if (keyboardAlwaysVisible) TERMINAL_KEYBOARD_HEIGHT_DP.dp else 0.dp
                            )
                    ) {
                        val typeface = Typeface.MONOSPACE
                        // TODO: Make this configurable via downloading fonts!
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                            Typeface.CustomFallbackBuilder(
//                                FontFamily.Builder(
//                                    Font.Builder(context.assets, "fonts/PowerlineExtraSymbols.ttf").build()
//                                ).build()
//                            ).setSystemFallback("monospace").build()
//                        } else {
//                            Typeface.MONOSPACE
//                        }

                        Terminal(
                            terminalEmulator = bridge.terminalEmulator,
                            modifier = Modifier.fillMaxSize(),
                            typeface = typeface,
                            initialFontSize = bridge.fontSizeFlow.value.sp,
                            keyboardEnabled = true,
                            showSoftKeyboard = showSoftwareKeyboard,
                            focusRequester = termFocusRequester,
                            forcedSize = forceSize,
                            modifierManager = bridge.keyHandler,
                            onTerminalTap = { handleTerminalInteraction() },
                            onImeVisibilityChanged = { visible ->
                                imeVisible = visible
                            },
                        )

                        // Set up text input request callback from bridge (for camera button)
                        SideEffect {
                            bridge.onTextInputRequested = {
                                showTextInputDialog = true
                            }
                        }

                        // Terminal keyboard overlay (doesn't resize terminal)
                        // Must be BEFORE prompts so prompts appear on top
                        // Fade in/out animation matches ConsoleActivity (100ms duration)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showExtraKeyboard,
                            enter = fadeIn(animationSpec = tween(durationMillis = 100)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 100)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                        ) {
                            TerminalKeyboard(
                                bridge = bridge,
                                onInteraction = { handleTerminalInteraction() },
                                onHideIme = {
                                    showSoftwareKeyboard = false
                                },
                                onShowIme = {
                                    showSoftwareKeyboard = true
                                },
                                onOpenTextInput = {
                                    // Open floating text input dialog
                                    showTextInputDialog = true
                                },
                                imeVisible = imeVisible,
                                playAnimation = !hasPlayedKeyboardAnimation
                            )
                        }

                        // Show inline prompts from the current bridge (non-modal at bottom)
                        // Must be AFTER keyboard so prompts appear on top (z-order)
                        val promptState by bridge.promptManager.promptState.collectAsState()

                        InlinePrompt(
                            promptRequest = promptState,
                            onResponse = { response ->
                                bridge.promptManager.respond(response)
                            },
                            onCancel = {
                                bridge.promptManager.cancelPrompt()
                            },
                            onDismissed = {
                                termFocusRequester.requestFocus()
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        // Dialogs
        if (showUrlScanDialog) {
            UrlScanDialog(
                urls = scannedUrls,
                onDismiss = { showUrlScanDialog = false },
                onUrlClick = { url ->
                    // Open URL in browser
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        url.toUri()
                    )
                    context.startActivity(intent)
                }
            )
        }

        if (showResizeDialog && currentBridge != null) {
            ResizeDialog(
                currentBridge = currentBridge,
                onDismiss = { showResizeDialog = false },
                onResize = { width, height ->
                    // Resize the terminal emulator
                    forceSize = Pair(height, width)
                }
            )
        }

        if (showDisconnectDialog && currentBridge != null) {
            HostDisconnectDialog(
                host = currentBridge.host,
                onDismiss = { showDisconnectDialog = false },
                onConfirm = {
                    currentBridge.dispatchDisconnect(true)
                }
            )
        }

        if (showTextInputDialog && currentBridge != null) {
            // TODO: Get selected text from TerminalEmulator when selection is implemented
            val selectedText = ""

            FloatingTextInputDialog(
                bridge = currentBridge,
                initialText = selectedText,
                onDismiss = {
                    showTextInputDialog = false
                    termFocusRequester.requestFocus()
                }
            )
        }

        // Overlay TopAppBar - always visible when titleBarHide is false,
        // or temporarily visible when titleBarHide is true and showTitleBar is true
        if (!titleBarHide || showTitleBar) {
            TopAppBar(
                title = {
                    Text(
                        currentBridge?.host?.nickname
                            ?: stringResource(R.string.console_default_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.button_back)
                        )
                    }
                },
                colors = if (titleBarHide) {
                    // Translucent overlay when auto-hide is enabled
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    )
                } else {
                    // Solid color when permanently visible
                    TopAppBarDefaults.topAppBarColors()
                },
                actions = {
                    // Text Input button
                    IconButton(
                        onClick = { showTextInputDialog = true },
                        enabled = currentBridge != null
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.console_menu_text_input)
                        )
                    }

                    // Paste button - always visible
                    IconButton(
                        onClick = {
                            currentBridge?.let { bridge ->
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip =
                                    clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                bridge.injectString(clip)
                            }
                        },
                        enabled = currentBridge != null
                    ) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.console_menu_paste)
                        )
                    }

                    // More menu
                    Box {
                        IconButton(onClick = {
                            // Refresh menu state to update enabled/disabled items
                            viewModel.refreshMenuState()
                            showMenu = true
                        }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.button_more_options)
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
                            }
                        ) {
                            // Disconnect/Close
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (!sessionOpen && disconnected)
                                            stringResource(R.string.console_menu_close)
                                        else
                                            stringResource(R.string.list_host_disconnect)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDisconnectDialog = true
                                },
                                enabled = currentBridge != null,
                                leadingIcon = {
                                    Icon(Icons.Default.LinkOff, null)
                                }
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
                                enabled = currentBridge != null
                            )

                            // Resize
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.console_menu_resize)) },
                                onClick = {
                                    showMenu = false
                                    showResizeDialog = true
                                },
                                enabled = sessionOpen
                            )

                            // Port Forwards (if available)
                            if (canForwardPorts) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_menu_portforwards)) },
                                    onClick = {
                                        showMenu = false
                                        currentBridge.host?.id?.let {
                                            onNavigateToPortForwards(
                                                it
                                            )
                                        }
                                    },
                                    enabled = sessionOpen
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
                                    androidx.compose.material3.Checkbox(
                                        checked = fullscreen,
                                        onCheckedChange = null
                                    )
                                }
                            )

                            // Title bar auto-hide toggle
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.pref_titlebarhide_title)) },
                                onClick = {
                                    titleBarHide = !titleBarHide
                                    prefs.edit { putBoolean("titlebarhide", titleBarHide) }
                                },
                                trailingIcon = {
                                    androidx.compose.material3.Checkbox(
                                        checked = titleBarHide,
                                        onCheckedChange = null
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun HostDisconnectDialog(
    host: Host,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(stringResource(R.string.disconnect_host_alert, host.nickname))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.button_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_no))
            }
        }
    )
}
