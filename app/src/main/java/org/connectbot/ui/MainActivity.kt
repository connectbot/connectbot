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
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.fido2.Fido2Manager
import org.connectbot.service.TerminalManager
import org.connectbot.ui.components.DisconnectAllDialog
import org.connectbot.ui.navigation.NavDestinations
import org.connectbot.ui.theme.ConnectBotTheme
import org.connectbot.util.IconStyle
import org.connectbot.util.NotificationPermissionHelper
import org.connectbot.util.PreferenceConstants
import org.connectbot.util.ShortcutIconGenerator
import timber.log.Timber
import javax.inject.Inject

// TODO: Move back to ComponentActivity when https://issuetracker.google.com/issues/178855209 is fixed.
//       FragmentActivity subclass is required for BiometricPrompt to find the FragmentManager
//       from Compose context.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    companion object {
        private const val STATE_SELECTED_URI = "selectedUri"
        const val DISCONNECT_ACTION = "org.connectbot.action.DISCONNECT"
    }

    @Inject
    lateinit var fido2Manager: Fido2Manager

    internal lateinit var appViewModel: AppViewModel
    private var bound = false
    private var requestedUri: Uri? by mutableStateOf(null)
    private var pendingHostConnection: Host? by mutableStateOf(null)
    internal var makingShortcut by mutableStateOf(false)
    private var showDisconnectAllDialog by mutableStateOf(false)

    // NFC foreground dispatch for FIDO2 security keys
    private var nfcAdapter: NfcAdapter? = null
    private var nfcPendingIntent: PendingIntent? = null
    private var nfcIntentFilters: Array<IntentFilter>? = null
    private var nfcTechLists: Array<Array<String>>? = null
    private var nfcForegroundDispatchEnabled by mutableStateOf(false)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Check the actual permission status instead of relying on the launcher result.
        // If user went to settings and granted permission, the result will be false but
        // the actual permission may be granted.
        val actuallyGranted = NotificationPermissionHelper.isNotificationPermissionGranted(this)

        appViewModel.onNotificationPermissionResult(actuallyGranted)?.let { uri ->
            requestedUri = uri
        }
        // Connections do not require notification permission, so a denied permission
        // does not block any pending host connection. Navigation to, and clearing of,
        // any pendingHostConnection are handled unconditionally by the LaunchedEffect
        // in the composable UI that observes this state.
        if (!actuallyGranted && pendingHostConnection != null) {
            Timber.d("Permission denied; proceeding with any pending host connection")
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

        // Set up NFC foreground dispatch for FIDO2 security keys
        setupNfcForegroundDispatch()

        setContent {
            val appUiState by appViewModel.uiState.collectAsState()
            val pendingDisconnectAll by appViewModel.pendingDisconnectAll.collectAsState()
            val isAuthenticated by appViewModel.isAuthenticated.collectAsState()
            val authOnLaunchEnabled = appViewModel.authOnLaunchEnabled
            val navController = rememberNavController()
            val context = LocalContext.current
            var showPermissionRationale by remember { mutableStateOf(false) }

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

            // Re-check permission status when activity resumes (e.g., user grants/revokes in Settings)
            ObservePermissionOnResume { isGranted ->
                if (pendingHostConnection != null) {
                    // Permission state changed and we have a pending connection
                    appViewModel.onNotificationPermissionResult(isGranted)
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

            // Navigate to console when pending host connection is set
            LaunchedEffect(pendingHostConnection, appUiState) {
                if (appUiState is AppUiState.Ready) {
                    pendingHostConnection?.let { host ->
                        Timber.d("Navigating to console for pending host: ${host.nickname}")
                        pendingHostConnection = null
                        navController.navigate("${NavDestinations.CONSOLE}/${host.id}")
                    }
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
                            // Also handle pending host connection
                            pendingHostConnection?.let { host ->
                                navController.navigate("${NavDestinations.CONSOLE}/${host.id}")
                                pendingHostConnection = null
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

            // Callback to check permission before navigating to console
            val onNavigateToConsole: (Host) -> Unit = { host ->
                Timber.d("onNavigateToConsole called for host: ${host.nickname}")

                // Check if connection persistence is enabled
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val persistConnections = prefs.getBoolean(PreferenceConstants.CONNECTION_PERSIST, true)

                if (!persistConnections || NotificationPermissionHelper.isNotificationPermissionGranted(context)) {
                    // Either persistence is disabled (no permission needed) or permission granted, navigate immediately
                    navController.navigate("${NavDestinations.CONSOLE}/${host.id}")
                } else {
                    // Persistence is enabled but no permission - need to request permission
                    Timber.d("Requesting notification permission before connection")
                    pendingHostConnection = host
                    val shouldShowRationale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        this@MainActivity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        false
                    }
                    appViewModel.requestNotificationPermission(shouldShowRationale)
                }
            }

            ConnectBotApp(
                appUiState = appUiState,
                navController = navController,
                makingShortcut = makingShortcut,
                authRequired = authOnLaunchEnabled,
                isAuthenticated = isAuthenticated,
                onAuthenticationSuccess = { appViewModel.onAuthenticationSuccess() },
                onRetryMigration = { appViewModel.retryMigration() },
                onSelectShortcut = { host, color, iconStyle ->
                    createShortcutAndFinish(host, color, iconStyle)
                },
                onNavigateToConsole = onNavigateToConsole
            )
        }
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableNfcForegroundDispatch()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Check for NFC tag
        if (handleNfcIntent(intent)) {
            return
        }

        handleIntent(intent)

        intent.data?.let { uri ->
            requestedUri = uri
        }
    }

    private fun handleNfcIntent(intent: Intent): Boolean {
        if (intent.action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            intent.action != NfcAdapter.ACTION_TAG_DISCOVERED) {
            return false
        }

        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag != null) {
            Timber.d("NFC tag discovered, connecting to FIDO2 device")
            lifecycleScope.launch {
                fido2Manager.connectToNfcTag(tag)
            }
            return true
        }
        return false
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == DISCONNECT_ACTION) {
            Timber.d("handleIntent: DISCONNECT_ACTION, showing disconnect dialog")
            showDisconnectAllDialog = true
        }
    }

    private fun setupNfcForegroundDispatch() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Timber.d("NFC not available on this device")
            return
        }

        // Create pending intent for foreground dispatch
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        nfcPendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Filter for IsoDep (ISO 14443-4) which is used by FIDO2 NFC
        val techFilter = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        nfcIntentFilters = arrayOf(techFilter)
        nfcTechLists = arrayOf(arrayOf(IsoDep::class.java.name))
    }

    private fun enableNfcForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        val pendingIntent = nfcPendingIntent ?: return

        try {
            adapter.enableForegroundDispatch(
                this,
                pendingIntent,
                nfcIntentFilters,
                nfcTechLists
            )
            nfcForegroundDispatchEnabled = true
            Timber.d("NFC foreground dispatch enabled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable NFC foreground dispatch")
        }
    }

    private fun disableNfcForegroundDispatch() {
        if (!nfcForegroundDispatchEnabled) return
        val adapter = nfcAdapter ?: return

        try {
            adapter.disableForegroundDispatch(this)
            nfcForegroundDispatchEnabled = false
            Timber.d("NFC foreground dispatch disabled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to disable NFC foreground dispatch")
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

    private fun createShortcutAndFinish(host: Host, color: String?, iconStyle: IconStyle) {
        val uri = host.getUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val icon = ShortcutIconGenerator.generateShortcutIcon(this, color, iconStyle)

        val shortcut = ShortcutInfoCompat.Builder(this, "host-${host.id}")
            .setShortLabel(host.nickname)
            .setLongLabel(host.nickname)
            .setIcon(icon)
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
        title = { Text(stringResource(R.string.notification_permission_title)) },
        text = { Text(stringResource(R.string.notification_permission_message)) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.grant_permission))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.connect_anyway))
            }
        }
    )
}
