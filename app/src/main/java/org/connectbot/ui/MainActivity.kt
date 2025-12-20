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

package org.connectbot.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import timber.log.Timber
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.service.TerminalManager
import org.connectbot.ui.components.DisconnectAllDialog
import org.connectbot.ui.navigation.NavDestinations
import org.connectbot.ui.theme.ConnectBotTheme

// TODO: Move back to ComponentActivity when https://issuetracker.google.com/issues/178855209 is fixed.
//       FragmentActivity is required for BiometricPrompt to find the FragmentManager from Compose context.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    companion object {
        private const val TAG = "CB.MainActivity"
        private const val STATE_SELECTED_URI = "selectedUri"
        const val DISCONNECT_ACTION = "org.connectbot.action.DISCONNECT"
    }

    internal lateinit var appViewModel: AppViewModel
    private var bound = false
    private var requestedUri: Uri? by mutableStateOf(null)
    internal var makingShortcut by mutableStateOf(false)
    private var showDisconnectAllDialog by mutableStateOf(false)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        appViewModel.onNotificationPermissionResult(isGranted)?.let { uri ->
            requestedUri = uri
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TerminalManager.TerminalBinder
            val manager = binder?.getService()
            Timber.d("onServiceConnected: manager=$manager")
            appViewModel.setTerminalManager(manager)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("onServiceDisconnected")
            appViewModel.setTerminalManager(null)
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        appViewModel = ViewModelProvider(this)[AppViewModel::class.java]

        if (savedInstanceState == null) {
            requestedUri = intent?.data
            makingShortcut = Intent.ACTION_CREATE_SHORTCUT == intent?.action ||
                             Intent.ACTION_PICK == intent?.action
            Timber.d("onCreate: requestedUri=$requestedUri, makingShortcut=$makingShortcut")
            handleIntent(intent)
        } else {
            savedInstanceState.getString(STATE_SELECTED_URI)?.let {
                requestedUri = it.toUri()
            }
        }

        val serviceIntent = Intent(this, TerminalManager::class.java)
        bindService(serviceIntent, connection, BIND_AUTO_CREATE)

        setContent {
            val appUiState by appViewModel.uiState.collectAsState()
            val pendingDisconnectAll by appViewModel.pendingDisconnectAll.collectAsState()
            val navController = rememberNavController()
            val context = LocalContext.current
            var showPermissionRationale by mutableStateOf(false)

            LaunchedEffect(Unit) {
                appViewModel.showPermissionRationale.collect {
                    showPermissionRationale = true
                }
            }

            LaunchedEffect(Unit) {
                appViewModel.requestPermission.collect {
                    Timber.d("Received requestPermission event, SDK_INT=${Build.VERSION.SDK_INT}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Timber.d("Launching permission request")
                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Timber.d("Skipping permission request, SDK < TIRAMISU")
                    }
                }
            }

            // Request notification permission on app startup
            LaunchedEffect(appUiState) {
                if (appUiState is AppUiState.Ready) {
                    Timber.d("App is ready, requesting initial notification permission")
                    val shouldShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        this@MainActivity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        false
                    }
                    appViewModel.requestInitialNotificationPermissionIfNeeded(context, shouldShowRationale)
                }
            }

            LaunchedEffect(requestedUri, navController, appUiState) {
                Timber.d("LaunchedEffect: requestedUri=$requestedUri, appUiState=$appUiState")
                if (appUiState is AppUiState.Ready) {
                    requestedUri?.let { uri ->
                        Timber.d("Processing URI: $uri")
                        navController.let { controller ->
                            val shouldShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                this@MainActivity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                false
                            }

                            Timber.d("shouldShowRationale=$shouldShowRationale")
                            if (appViewModel.checkAndRequestNotificationPermission(context, uri, shouldShowRationale)) {
                                Timber.d("Permission check passed, handling connection")
                                handleConnectionUri(uri, controller)
                                requestedUri = null
                            } else {
                                Timber.d("Permission check blocked, waiting for permission")
                            }
                        }
                    }
                }
            }

            LaunchedEffect(pendingDisconnectAll, appUiState) {
                appViewModel.executePendingDisconnectAllIfReady()
            }

            LaunchedEffect(Unit) {
                appViewModel.finishActivity.collect {
                    if (context is Activity) {
                        context.finish()
                    }
                }
            }

            if (showDisconnectAllDialog) {
                ConnectBotTheme {
                    DisconnectAllDialog(
                        onDismiss = {
                            Timber.d("User cancelled disconnectAll")
                            showDisconnectAllDialog = false
                        },
                        onConfirm = {
                            Timber.d("User confirmed disconnectAll")
                            showDisconnectAllDialog = false
                            appViewModel.setPendingDisconnectAll(true)
                        }
                    )
                }
            }

            if (showPermissionRationale) {
                ConnectBotTheme {
                    NotificationPermissionRationaleDialog(
                        onDismiss = {
                            Timber.d("User dismissed permission rationale, proceeding anyway")
                            showPermissionRationale = false
                            // User chose "Continue anyway" - proceed without permission
                            appViewModel.pendingConnectionUri.value?.let { uri ->
                                requestedUri = uri
                                appViewModel.clearPendingConnectionUri()
                            }
                        },
                        onAllow = {
                            Timber.d("User chose to allow permission from rationale")
                            showPermissionRationale = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }

            ConnectBotApp(
                appUiState = appUiState,
                navController = navController,
                makingShortcut = makingShortcut,
                onRetryMigration = { appViewModel.retryMigration() },
                onShortcutSelected = { host ->
                    createShortcutAndFinish(host)
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleIntent(intent)

        intent.data?.let { uri ->
            requestedUri = uri
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == DISCONNECT_ACTION) {
            Timber.d("handleIntent: DISCONNECT_ACTION, showing disconnect dialog")
            showDisconnectAllDialog = true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        requestedUri?.let {
            outState.putString(STATE_SELECTED_URI, it.toString())
        }
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }

    private fun handleConnectionUri(uri: Uri, controller: NavController) {
        Timber.d("handleConnectionUri: uri=$uri, fragment=${uri.fragment}")
        val state = appViewModel.uiState.value
        if (state !is AppUiState.Ready) {
            Timber.d("handleConnectionUri: state not ready, current state=$state")
            return
        }

        val manager = state.terminalManager

        lifecycleScope.launch {
            try {
                val nickname = uri.fragment ?: uri.authority
                Timber.d("handleConnectionUri: nickname=$nickname")
                var bridge = manager.getConnectedBridge(nickname)

                if (bridge == null) {
                    Timber.d("Creating new connection for URI: $uri with nickname: $nickname")
                    bridge = manager.openConnection(uri)
                }

                controller.navigate("${NavDestinations.CONSOLE}/${bridge.host.id}") {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                Timber.e(e, "Error handling connection URI: $uri")
            }
        }
    }

    private fun createShortcutAndFinish(host: Host) {
        val uri = host.getUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val shortcut = ShortcutInfoCompat.Builder(this, "host-${host.id}")
            .setShortLabel(host.nickname)
            .setLongLabel(host.nickname)
            .setIcon(IconCompat.createWithResource(this, R.mipmap.icon))
            .setIntent(intent)
            .build()

        val result = ShortcutManagerCompat.createShortcutResultIntent(this, shortcut)
        setResult(RESULT_OK, result)
        finish()
    }
}

@Composable
private fun NotificationPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onAllow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.allow)) },
        text = { Text(stringResource(R.string.notification_requirement_explanation)) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.continue_anyway))
            }
        }
    )
}
