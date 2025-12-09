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
import android.content.Context
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.connectbot.service.TerminalManager
import org.connectbot.ui.navigation.NavDestinations
import androidx.core.net.toUri

// TODO: Move back to ComponentActivity when https://issuetracker.google.com/issues/178855209 is fixed.
//       FragmentActivity is required for BiometricPrompt to find the FragmentManager from Compose context.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    companion object {
        private const val TAG = "CB.MainActivity"
        private const val STATE_SELECTED_URI = "selectedUri"
        const val DISCONNECT_ACTION = "org.connectbot.action.DISCONNECT"
    }

    private lateinit var appViewModel: AppViewModel
    private var bound = false
    private var requestedUri: Uri? by mutableStateOf(null)
    private var navController: NavController? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TerminalManager.TerminalBinder
            val manager = binder?.getService()
            appViewModel.setTerminalManager(manager)
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
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
            handleIntent(intent)
        } else {
            savedInstanceState.getString(STATE_SELECTED_URI)?.let {
                requestedUri = it.toUri()
            }
        }

        val serviceIntent = Intent(this, TerminalManager::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            val appUiState by appViewModel.uiState.collectAsState()

            LaunchedEffect(requestedUri, navController) {
                requestedUri?.let { uri ->
                    navController?.let { controller ->
                        handleConnectionUri(uri, controller)
                        requestedUri = null
                    }
                }
            }

            ConnectBotApp(
                appUiState = appUiState,
                onRetryMigration = { appViewModel.retryMigration() },
                onNavigationReady = { controller ->
                    navController = controller
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
                state.terminalManager.disconnectAll(false, false)
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
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }

    private fun handleConnectionUri(uri: Uri, controller: NavController) {
        val state = appViewModel.uiState.value
        if (state !is AppUiState.Ready) return

        val manager = state.terminalManager

        lifecycleScope.launch {
            try {
                val requestedNickname = uri.fragment
                var bridge = manager.getConnectedBridge(requestedNickname)

                if (requestedNickname != null && bridge == null) {
                    Log.d(
                        TAG,
                        "Creating new connection for URI: $uri with nickname: $requestedNickname"
                    )
                    bridge = manager.openConnection(uri)
                }

                if (bridge != null) {
                    controller.navigate("${NavDestinations.CONSOLE}/${bridge.host.id}") {
                        launchSingleTop = true
                    }
                } else {
                    Log.w(TAG, "Could not create bridge for URI: $uri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling connection URI: $uri", e)
            }
        }
    }
}
