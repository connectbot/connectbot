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

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.entity.Host
import org.connectbot.service.TerminalManager
import org.connectbot.ui.navigation.NavDestinations

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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TerminalManager.TerminalBinder
            val manager = binder?.getService()
            Log.d(TAG, "onServiceConnected: manager=$manager")
            appViewModel.setTerminalManager(manager)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
            appViewModel.setTerminalManager(null)
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            val state = appViewModel.uiState.value
            state !is AppUiState.Ready
        }

        appViewModel = ViewModelProvider(this)[AppViewModel::class.java]

        if (savedInstanceState == null) {
            requestedUri = intent?.data
            makingShortcut = Intent.ACTION_CREATE_SHORTCUT == intent?.action ||
                             Intent.ACTION_PICK == intent?.action
            Log.d(TAG, "onCreate: requestedUri=$requestedUri, makingShortcut=$makingShortcut")
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
            val navController = rememberNavController()

            LaunchedEffect(requestedUri, navController, appUiState) {
                if (appUiState is AppUiState.Ready) {
                    requestedUri?.let { uri ->
                        navController.let { controller ->
                            handleConnectionUri(uri, controller)
                            requestedUri = null
                        }
                    }
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
            val state = appViewModel.uiState.value
            if (state is AppUiState.Ready) {
                state.terminalManager.disconnectAll(immediate = false, excludeLocal = false)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        requestedUri?.let {
            outState.putString(STATE_SELECTED_URI, it.toString())
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }

    private fun handleConnectionUri(uri: Uri, controller: NavController) {
        Log.d(TAG, "handleConnectionUri: uri=$uri, fragment=${uri.fragment}")
        val state = appViewModel.uiState.value
        if (state !is AppUiState.Ready) {
            Log.d(TAG, "handleConnectionUri: state not ready, current state=$state")
            return
        }

        val manager = state.terminalManager

        lifecycleScope.launch {
            try {
                val nickname = uri.fragment ?: uri.authority
                Log.d(TAG, "handleConnectionUri: nickname=$nickname")
                var bridge = manager.getConnectedBridge(nickname)

                if (bridge == null) {
                    Log.d(TAG, "Creating new connection for URI: $uri with nickname: $nickname")
                    bridge = manager.openConnection(uri)
                }

                controller.navigate("${NavDestinations.CONSOLE}/${bridge.host.id}") {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling connection URI: $uri", e)
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
