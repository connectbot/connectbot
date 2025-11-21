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
import android.os.Build
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import org.connectbot.R
import org.connectbot.TerminalView
import org.connectbot.data.entity.Host
import org.connectbot.service.TerminalBridge
import org.connectbot.ui.LocalTerminalManager
import org.connectbot.ui.components.InlinePrompt
import org.connectbot.ui.components.ResizeDialog
import org.connectbot.ui.components.TerminalKeyboard
import org.connectbot.ui.components.UrlScanDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConsoleScreen(
    hostId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToPortForwards: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val terminalManager = LocalTerminalManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val viewModel = remember(hostId) { ConsoleViewModel(terminalManager, hostId) }
    val uiState by viewModel.uiState.collectAsState()

    // Read preferences
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val keyboardAlwaysVisible = remember { prefs.getBoolean("alwaysvisible", false) }
    var fullscreen by remember { mutableStateOf(prefs.getBoolean("fullscreen", false)) }
    var titleBarHide by remember { mutableStateOf(prefs.getBoolean("titlebarhide", false)) }

    var showMenu by remember { mutableStateOf(false) }
    var showUrlScanDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(true) } // Start visible to show animation
    var hasPlayedKeyboardAnimation by remember { mutableStateOf(false) }
    var showTitleBar by remember { mutableStateOf(!titleBarHide) }
    var scannedUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    // Key the terminal view by currentBridgeIndex so it gets reset when switching tabs
    var currentTerminalView by remember(uiState.currentBridgeIndex) {
        mutableStateOf<TerminalView?>(
            null
        )
    }

    // One-time initialization
    LaunchedEffect(Unit) {
        // Disable StrictMode for terminal I/O (matches old ConsoleActivity behavior)
        // Terminal key input triggers immediate network writes which need to happen on main thread
        // for responsiveness. This is intentional behavior carried over from the original implementation.
        android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.LAX)

        // Set edge-to-edge mode once on first composition
        val activity = context as? Activity ?: return@LaunchedEffect
        val window = activity.window

        try {
            // Always use edge-to-edge mode and let Compose handle insets
            // This is the modern way to handle IME instead of deprecated SOFT_INPUT_ADJUST_RESIZE
            WindowCompat.setDecorFitsSystemWindows(window, false)
        } catch (e: IllegalArgumentException) {
            // Handle foldable device state issues
            android.util.Log.e(
                "ConsoleScreen",
                "Error setting edge-to-edge mode (foldable device?)",
                e
            )
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

    // Sync title bar visibility with keyboard visibility when titleBarHide is enabled
    LaunchedEffect(showKeyboard, titleBarHide) {
        if (titleBarHide && !showMenu) {
            // When keyboard hides, also hide the title bar (unless menu is open)
            showTitleBar = showKeyboard
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

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column {
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when {
                        uiState.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }

                        uiState.bridges.isNotEmpty() -> {
                            // Use HorizontalPager for swiping between terminals
                            val pagerState = rememberPagerState(
                                initialPage = uiState.currentBridgeIndex,
                                pageCount = { uiState.bridges.size }
                            )

                            // Sync pager state with ViewModel
                            LaunchedEffect(pagerState.currentPage) {
                                if (pagerState.currentPage != uiState.currentBridgeIndex) {
                                    viewModel.selectBridge(pagerState.currentPage)
                                }
                            }

                            // Sync ViewModel state with pager (for tab clicks)
                            LaunchedEffect(uiState.currentBridgeIndex) {
                                if (pagerState.currentPage != uiState.currentBridgeIndex) {
                                    pagerState.scrollToPage(uiState.currentBridgeIndex)
                                }
                            }

                            val contentPadding = if (titleBarHide) {
                                // When title bar is hidden, content can go to the edge, respecting safe areas.
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
                            } else {
                                // When title bar is visible, add its height to the top padding.
                                WindowInsets.safeDrawing.add(WindowInsets(top = 64.dp))
                                    .only(WindowInsetsSides.Vertical)
                            }

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
                                key = { uiState.bridges[it].hashCode() }
                            ) { page ->
                                val bridge = uiState.bridges[page]

                                Box(modifier = Modifier.fillMaxSize()) {
                                    // Terminal view fills entire space with insets padding
                                    // to avoid content being cut off by screen curves/notches
                                    TerminalViewWrapper(
                                        bridge = bridge,
                                        onViewCreated = { view ->
                                            // Only update currentTerminalView if this is the active page
                                            if (page == uiState.currentBridgeIndex) {
                                                currentTerminalView = view
                                            }
                                        },
                                        onTerminalTap = {
                                            // Show emulated keyboard when terminal is tapped (unless always visible)
                                            if (!keyboardAlwaysVisible) {
                                                showKeyboard = true
                                            }
                                            // Show title bar temporarily when terminal is tapped (if auto-hide enabled)
                                            if (titleBarHide) {
                                                showTitleBar = true
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .windowInsetsPadding(contentPadding)
                                    )

                                    // Request focus on terminal when it's the active page
                                    LaunchedEffect(
                                        page == uiState.currentBridgeIndex,
                                        currentTerminalView
                                    ) {
                                        if (page == uiState.currentBridgeIndex) {
                                            currentTerminalView?.post {
                                                currentTerminalView?.requestFocus()
                                            }
                                        }
                                    }

                                    // Only show keyboard and prompts for the current page
                                    if (page == uiState.currentBridgeIndex) {
                                        // Terminal keyboard overlay (doesn't resize terminal)
                                        // Must be BEFORE prompts so prompts appear on top
                                        // Fade in/out animation matches ConsoleActivity (100ms duration)
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = showKeyboard,
                                            enter = fadeIn(animationSpec = tween(durationMillis = 100)),
                                            exit = fadeOut(animationSpec = tween(durationMillis = 100)),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .windowInsetsPadding(WindowInsets.navigationBars)
                                                .windowInsetsPadding(WindowInsets.ime)
                                        ) {
                                            TerminalKeyboard(
                                                bridge = bridge,
                                                onHideKeyboard = {
                                                    // Auto-hide timer hides the TerminalKeyboard
                                                    if (!keyboardAlwaysVisible) {
                                                        showKeyboard = false
                                                    }
                                                    // Mark animation as played after first hide
                                                    hasPlayedKeyboardAnimation = true
                                                },
                                                onHideIme = {
                                                    // Special keys and hide button hide the IME
                                                    keyboardController?.hide()
                                                },
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
                                                // Return focus to terminal after prompt animation completes
                                                // This matches ConsoleActivity.updatePromptVisible() behavior
                                                currentTerminalView?.post {
                                                    currentTerminalView?.requestFocus()
                                                }
                                            },
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                        )
                                    }
                                }
                            }
                        }
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
                    currentTerminalView?.forceSize(width, height)
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
                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
                } else {
                    // Solid color when permanently visible
                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors()
                },
                actions = {
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
                            }
                        ) {
                            // Copy
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.console_menu_copy)) },
                                onClick = {
                                    showMenu = false
                                    currentTerminalView?.let { view ->
                                        if (view.hasSelection()) {
                                            view.copyCurrentSelectionToClipboard()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.console_copy_start),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                                enabled = currentBridge != null
                            )

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
                                enabled = currentBridge != null
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
                },
                modifier = Modifier.align(Alignment.TopCenter)
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

@Composable
private fun TerminalViewWrapper(
    bridge: TerminalBridge,
    onViewCreated: (TerminalView) -> Unit = {},
    onTerminalTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Key the AndroidView by bridge hash to force recreation when bridge changes
    key(bridge.hashCode()) {
        AndroidView(
            factory = { context ->
                // Create TerminalView first so we can reference it in container
                var terminalView: TerminalView? = null

                // Create a simple FrameLayout that handles clicks for tap-to-show keyboard
                // TerminalView's GestureDetector will call performClick() on this parent
                val container = object : android.widget.FrameLayout(context) {
                    override fun performClick(): Boolean {
                        onTerminalTap()
                        // Request focus and show IME
                        terminalView?.let { view ->
                            view.requestFocus()
                            val imm =
                                context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.showSoftInput(
                                view,
                                InputMethodManager.SHOW_IMPLICIT
                            )
                        }
                        return super.performClick()
                    }
                }

                // Create TerminalView with the container as parent
                terminalView = TerminalView(context, bridge, container).apply {
                    // Disable default focus highlight (green outline)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        defaultFocusHighlightEnabled = false
                    }
                    onViewCreated(this)
                }

                // Add TerminalView to container
                container.addView(
                    terminalView, android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                container
            },
            update = { container ->
                // Get the TerminalView from the container
                val view = container.getChildAt(0) as? TerminalView
                view?.let {
                    onViewCreated(it)
                    it.requestFocus()
                }
            },
            modifier = modifier
        )
    }
}
