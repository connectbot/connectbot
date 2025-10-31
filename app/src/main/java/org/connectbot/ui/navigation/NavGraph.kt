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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.ui.screens.console.ConsoleScreen
import org.connectbot.ui.screens.eula.EulaScreen
import org.connectbot.ui.screens.generatepubkey.GeneratePubkeyScreen
import org.connectbot.ui.screens.help.HelpScreen
import org.connectbot.ui.screens.hints.HintsScreen
import org.connectbot.ui.screens.hosteditor.HostEditorScreen
import org.connectbot.ui.screens.hostlist.HostListScreen
import org.connectbot.ui.screens.portforwardlist.PortForwardListScreen
import org.connectbot.ui.screens.pubkeyeditor.PubkeyEditorScreen
import org.connectbot.ui.screens.pubkeylist.PubkeyListScreen
import org.connectbot.ui.screens.settings.SettingsScreen
import org.connectbot.ui.screens.colors.ColorsScreen
import org.connectbot.ui.screens.colors.ColorsViewModel
import org.connectbot.ui.screens.colors.ColorSchemeManagerScreen
import org.connectbot.ui.screens.colors.ColorSchemeManagerViewModel
import org.connectbot.ui.screens.colors.PaletteEditorScreen
import org.connectbot.ui.screens.colors.PaletteEditorViewModel
import org.connectbot.util.HostDatabase

@Composable
fun ConnectBotNavHost(
    navController: NavHostController,
    startDestination: String = NavDestinations.HOST_LIST,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(NavDestinations.HOST_LIST) {
            HostListScreen(
                onNavigateToConsole = { host ->
                    navController.navigate("${NavDestinations.CONSOLE}/${host.id}")
                },
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
        ) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getLong(NavArgs.HOST_ID) ?: -1L
            ConsoleScreen(
                hostId = hostId,
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
        ) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getLong(NavArgs.HOST_ID) ?: -1L
            HostEditorScreen(
                hostId = hostId,
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
        ) { backStackEntry ->
            val pubkeyId = backStackEntry.arguments?.getLong(NavArgs.PUBKEY_ID) ?: -1L
            PubkeyEditorScreen(
                pubkeyId = pubkeyId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${NavDestinations.PORT_FORWARD_LIST}/{${NavArgs.HOST_ID}}",
            arguments = listOf(
                navArgument(NavArgs.HOST_ID) { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getLong(NavArgs.HOST_ID) ?: -1L
            PortForwardListScreen(
                hostId = hostId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavDestinations.COLORS) {
            val context = LocalContext.current
            val viewModel = remember { ColorsViewModel(context) }
            ColorsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSchemeManager = { navController.navigate(NavDestinations.SCHEME_MANAGER) }
            )
        }

        composable(NavDestinations.SCHEME_MANAGER) {
            val context = LocalContext.current
            val viewModel = remember { ColorSchemeManagerViewModel(context) }
            val repository = remember { ColorSchemeRepository.get(context) }
            val scope = rememberCoroutineScope()

            // Track which scheme is being exported
            var exportingSchemeId by remember { mutableIntStateOf(-1) }

            // Export launcher - creates a new JSON file
            val exportLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                uri?.let { fileUri ->
                    scope.launch {
                        try {
                            val schemeJson = repository.exportScheme(exportingSchemeId)
                            context.contentResolver.openOutputStream(fileUri)?.use { output ->
                                output.write(schemeJson.toJson().toByteArray())
                            }
                            Toast.makeText(
                                context,
                                context.getString(org.connectbot.R.string.message_export_success, schemeJson.name),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(org.connectbot.R.string.error_export_failed, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            // Import launcher - selects an existing JSON file
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let { fileUri ->
                    scope.launch {
                        try {
                            val jsonString = context.contentResolver.openInputStream(fileUri)?.use { input ->
                                input.bufferedReader().readText()
                            } ?: return@launch

                            val schemeId = repository.importScheme(jsonString, allowOverwrite = false)
                            val schemes = repository.getAllSchemes()
                            val importedScheme = schemes.find { it.id == schemeId }

                            // Refresh the list to show the imported scheme
                            viewModel.refresh()

                            Toast.makeText(
                                context,
                                context.getString(
                                    org.connectbot.R.string.message_import_success,
                                    importedScheme?.name ?: "scheme"
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: org.json.JSONException) {
                            Toast.makeText(
                                context,
                                context.getString(org.connectbot.R.string.error_invalid_json),
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(org.connectbot.R.string.error_import_failed, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            ColorSchemeManagerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPaletteEditor = { schemeId ->
                    navController.navigate("${NavDestinations.PALETTE_EDITOR}/$schemeId")
                },
                onExportScheme = { schemeId ->
                    exportingSchemeId = schemeId
                    scope.launch {
                        try {
                            val schemes = repository.getAllSchemes()
                            val scheme = schemes.find { it.id == schemeId }
                            val fileName = "${scheme?.name?.replace(" ", "_") ?: "scheme"}.json"
                            exportLauncher.launch(fileName)
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                context.getString(org.connectbot.R.string.error_export_failed, e.message),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                onImportScheme = {
                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                }
            )
        }

        composable(
            route = "${NavDestinations.PALETTE_EDITOR}/{${NavArgs.SCHEME_ID}}",
            arguments = listOf(
                navArgument(NavArgs.SCHEME_ID) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val schemeId = backStackEntry.arguments?.getInt(NavArgs.SCHEME_ID) ?: 0
            val context = LocalContext.current
            val viewModel = remember {
                PaletteEditorViewModel(context, schemeId)
            }
            PaletteEditorScreen(
                viewModel = viewModel,
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
