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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.connectbot.data.HostRepository
import org.connectbot.service.TerminalManager
import org.connectbot.transport.TransportFactory
import org.connectbot.ui.navigation.ConnectBotNavHost
import org.connectbot.ui.navigation.NavDestinations
import org.connectbot.ui.theme.ConnectBotTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "CB.MainActivity"
        private const val STATE_SELECTED_URI = "selectedUri"
        const val DISCONNECT_ACTION = "org.connectbot.action.DISCONNECT"
    }

    private var terminalManager: TerminalManager? by mutableStateOf(null)
    private var bound = false
    private var requestedUri: Uri? = null
    private var navController: NavController? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TerminalManager.TerminalBinder
            terminalManager = binder?.service
            bound = true

            requestedUri?.let { uri ->
                handleConnectionUri(uri)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            terminalManager = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            requestedUri = intent?.data
            handleIntent(intent)
        } else {
            savedInstanceState.getString(STATE_SELECTED_URI)?.let {
                requestedUri = Uri.parse(it)
            }
        }

        val serviceIntent = Intent(this, TerminalManager::class.java)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            val migrationViewModel: MigrationViewModel = viewModel()
            val migrationUiState by migrationViewModel.uiState.collectAsState()

            ConnectBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (migrationUiState) {
                        is MigrationUiState.Completed -> {
                            if (terminalManager == null) {
                                // Show a loading indicator or waiting screen
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                    Text("Connecting to terminal manager…")
                                }
                            } else {
                                // Migration complete or not needed, show normal UI
                                val controller = rememberNavController()
                                navController = controller

                                CompositionLocalProvider(LocalTerminalManager provides terminalManager) {
                                    ConnectBotNavHost(
                                        navController = controller,
                                        startDestination = NavDestinations.HOST_LIST
                                    )
                                }
                            }
                        }

                        else -> {
                            // Show migration screen
                            MigrationScreen(
                                uiState = migrationUiState,
                                onRetry = { migrationViewModel.retryMigration() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleIntent(intent)

        intent.data?.let { uri ->
            requestedUri = uri
            if (bound) {
                handleConnectionUri(uri)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == DISCONNECT_ACTION) {
            terminalManager?.let { manager ->
                manager.disconnectAll(false, false)
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

    private fun handleConnectionUri(uri: Uri) {
        val manager = terminalManager ?: return
        val controller = navController ?: return

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
                    val hostRepository = HostRepository.get(this@MainActivity)
                    val host = TransportFactory.findHost(hostRepository, uri)
                    if (host != null) {
                        controller.navigate("${NavDestinations.CONSOLE}/${host.id}") {
                            launchSingleTop = true
                        }
                    } else {
                        Log.w(TAG, "Could not find host for URI: $uri")
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
