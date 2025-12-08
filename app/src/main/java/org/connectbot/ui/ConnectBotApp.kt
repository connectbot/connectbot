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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import org.connectbot.service.TerminalManager
import org.connectbot.ui.navigation.ConnectBotNavHost
import org.connectbot.ui.navigation.NavDestinations
import org.connectbot.ui.theme.ConnectBotTheme

val LocalTerminalManager = compositionLocalOf<TerminalManager?> {
    null
}

@Composable
fun ConnectBotApp(
    appUiState: AppUiState,
    onRetryMigration: () -> Unit,
    onNavigationReady: (NavController) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectBotTheme {
        when (appUiState) {
            is AppUiState.Loading -> {
                LoadingScreen(modifier = modifier)
            }

            is AppUiState.MigrationInProgress -> {
                MigrationScreen(
                    uiState = MigrationUiState.InProgress(appUiState.state),
                    onRetry = onRetryMigration,
                    modifier = modifier
                )
            }

            is AppUiState.MigrationFailed -> {
                MigrationScreen(
                    uiState = MigrationUiState.Failed(
                        appUiState.error,
                        appUiState.debugLog
                    ),
                    onRetry = onRetryMigration,
                    modifier = modifier
                )
            }

            is AppUiState.Ready -> {
                val navController = rememberNavController()

                LaunchedEffect(navController) {
                    onNavigationReady(navController)
                }

                CompositionLocalProvider(LocalTerminalManager provides appUiState.terminalManager) {
                    ConnectBotNavHost(
                        navController = navController,
                        startDestination = NavDestinations.HOST_LIST,
                        modifier = modifier
                    )
                }
            }
        }
    }
}
