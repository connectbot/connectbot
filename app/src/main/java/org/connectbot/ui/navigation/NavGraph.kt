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

package org.connectbot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import org.connectbot.data.entity.Host
import org.connectbot.ui.screens.colors.ColorSchemeManagerScreen
import org.connectbot.ui.screens.colors.ColorsScreen
import org.connectbot.ui.screens.colors.PaletteEditorScreen
import org.connectbot.ui.screens.console.ConsoleScreen
import org.connectbot.ui.screens.eula.EulaScreen
import org.connectbot.ui.screens.generatepubkey.GeneratePubkeyScreen
import org.connectbot.ui.screens.help.HelpScreen
import org.connectbot.ui.screens.hints.HintsScreen
import org.connectbot.ui.screens.hosteditor.HostEditorScreen
import org.connectbot.ui.screens.hostlist.HostListScreen
import org.connectbot.ui.screens.portforwardlist.PortForwardListScreen
import org.connectbot.ui.screens.profiles.ProfileEditorScreen
import org.connectbot.ui.screens.profiles.ProfileListScreen
import org.connectbot.ui.screens.pubkeyeditor.PubkeyEditorScreen
import org.connectbot.ui.screens.pubkeylist.PubkeyListScreen
import org.connectbot.ui.screens.settings.SettingsScreen

@Composable
fun ConnectBotNavHost(
    navController: NavHostController,
    startDestination: String = NavDestinations.HOST_LIST,
    makingShortcut: Boolean = false,
    onShortcutSelected: (Host) -> Unit = {},
    onNavigateToConsole: (Host) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(NavDestinations.HOST_LIST) {
            HostListScreen(
                makingShortcut = makingShortcut,
                onNavigateToConsole = onNavigateToConsole,
                onShortcutSelected = onShortcutSelected,
                onNavigateToEditHost = { host ->
                    if (host != null) {
                        navController.navigate("${NavDestinations.HOST_EDITOR}?${NavArgs.HOST_ID}=${host.id}")
                    } else {
                        navController.navigate(NavDestinations.HOST_EDITOR)
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(NavDestinations.SETTINGS)
                },
                onNavigateToPubkeys = {
                    navController.navigate(NavDestinations.PUBKEY_LIST)
                },
                onNavigateToPortForwards = { host ->
                    navController.navigate("${NavDestinations.PORT_FORWARD_LIST}/${host.id}")
                },
                onNavigateToColors = {
                    navController.navigate(NavDestinations.COLORS)
                },
                onNavigateToProfiles = {
                    navController.navigate(NavDestinations.PROFILES)
                },
                onNavigateToHelp = {
                    navController.navigate(NavDestinations.HELP)
                }
            )
        }

        composable(
            route = "${NavDestinations.CONSOLE}/{${NavArgs.HOST_ID}}",
            arguments = listOf(
                navArgument(NavArgs.HOST_ID) { type = NavType.LongType }
            )
        ) {
            ConsoleScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPortForwards = { hostIdForPortForwards ->
                    navController.navigate("${NavDestinations.PORT_FORWARD_LIST}/$hostIdForPortForwards")
                }
            )
        }

        composable(
            route = "${NavDestinations.HOST_EDITOR}?${NavArgs.HOST_ID}={${NavArgs.HOST_ID}}",
            arguments = listOf(
                navArgument(NavArgs.HOST_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            HostEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.PUBKEY_LIST) {
            PubkeyListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToGenerate = { navController.navigate(NavDestinations.GENERATE_PUBKEY) },
                onNavigateToEdit = { pubkey ->
                    navController.navigate("${NavDestinations.PUBKEY_EDITOR}/${pubkey.id}")
                }
            )
        }

        composable(NavDestinations.GENERATE_PUBKEY) {
            GeneratePubkeyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${NavDestinations.PUBKEY_EDITOR}/{${NavArgs.PUBKEY_ID}}",
            arguments = listOf(
                navArgument(NavArgs.PUBKEY_ID) { type = NavType.LongType }
            )
        ) {
            PubkeyEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${NavDestinations.PORT_FORWARD_LIST}/{${NavArgs.HOST_ID}}",
            arguments = listOf(
                navArgument(NavArgs.HOST_ID) { type = NavType.LongType }
            )
        ) {
            PortForwardListScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.COLORS) {
            ColorsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSchemeManager = { navController.navigate(NavDestinations.SCHEME_MANAGER) }
            )
        }

        composable(NavDestinations.SCHEME_MANAGER) {
            ColorSchemeManagerScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPaletteEditor = { schemeId ->
                    navController.navigate("${NavDestinations.PALETTE_EDITOR}/$schemeId")
                }
            )
        }

        composable(
            route = "${NavDestinations.PALETTE_EDITOR}/{${NavArgs.SCHEME_ID}}",
            arguments = listOf(
                navArgument(NavArgs.SCHEME_ID) { type = NavType.LongType }
            )
        ) {
            PaletteEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.PROFILES) {
            ProfileListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { profile ->
                    navController.navigate("${NavDestinations.PROFILE_EDITOR}/${profile.id}")
                }
            )
        }

        composable(
            route = "${NavDestinations.PROFILE_EDITOR}/{${NavArgs.PROFILE_ID}}",
            arguments = listOf(
                navArgument(NavArgs.PROFILE_ID) { type = NavType.LongType }
            )
        ) {
            ProfileEditorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.HELP) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHints = { navController.navigate(NavDestinations.HINTS) },
                onNavigateToEula = { navController.navigate(NavDestinations.EULA) }
            )
        }

        composable(NavDestinations.EULA) {
            EulaScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.HINTS) {
            HintsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
