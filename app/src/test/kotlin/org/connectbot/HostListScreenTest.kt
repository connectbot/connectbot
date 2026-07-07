/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package org.connectbot

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.ui.components.PortForwardQuickToggleTestTags
import org.connectbot.ui.screens.hostlist.ConnectionState
import org.connectbot.ui.screens.hostlist.HostListScreen
import org.connectbot.ui.screens.hostlist.HostListScreenContent
import org.connectbot.ui.screens.hostlist.HostListTestTags
import org.connectbot.ui.screens.hostlist.HostListUiState
import org.connectbot.ui.theme.ConnectBotTheme
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HostListScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltComponentActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun hostListScreen_displaysTitle() {
        val title = composeTestRule.activity.getString(R.string.app_name)

        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
    }

    @Test
    fun hostListScreen_hasAddButton() {
        val addHost = composeTestRule.activity.getString(R.string.hostpref_add_host)

        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(addHost, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun hostListScreen_menuOpensOnClick() {
        val moreOptions = composeTestRule.activity.getString(R.string.button_more_options)
        val managePubkeys = composeTestRule.activity.getString(R.string.list_menu_pubkeys)

        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(moreOptions)
            .performClick()

        composeTestRule
            .onNodeWithText(managePubkeys)
            .assertIsDisplayed()
    }

    @Test
    fun hostListScreen_addButtonCallsCallback() {
        var addHostCalled = false
        val addHost = composeTestRule.activity.getString(R.string.hostpref_add_host)

        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = { addHostCalled = true },
                    onNavigateToSettings = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(addHost, ignoreCase = true)
            .performClick()

        assert(addHostCalled)
    }

    @Test
    fun hostListScreen_showsSnackbar_whenShouldShowWarning() {
        val warning = composeTestRule.activity.getString(R.string.notification_permission_denied_snackbar)

        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                    shouldShowNotificationWarning = { true },
                    onNotificationSnackbarFinish = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(warning)
            .assertIsDisplayed()
    }

    @Test
    fun hostListScreen_snackbarSettingsAction_navigatesToSettingsHighlight() {
        var navigatedToSettingsHighlight = false
        val settings = composeTestRule.activity.getString(R.string.list_menu_settings)

        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToSettingsHighlightConnPersist = { navigatedToSettingsHighlight = true },
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                    shouldShowNotificationWarning = { true },
                    onNotificationSnackbarFinish = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(settings)
            .performClick()

        assertTrue(navigatedToSettingsHighlight)
    }

    @Test
    fun hostListScreen_noSnackbar_whenShouldNotShowWarning() {
        val warning = composeTestRule.activity.getString(R.string.notification_permission_denied_snackbar)

        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreen(
                    onNavigateToConsole = {},
                    onNavigateToEditHost = {},
                    onNavigateToSettings = {},
                    onNavigateToPubkeys = {},
                    onNavigateToPortForwards = {},
                    onNavigateToProfiles = {},
                    onNavigateToHelp = {},
                    shouldShowNotificationWarning = { false },
                    onNotificationSnackbarFinish = {},
                )
            }
        }

        composeTestRule
            .onAllNodes(androidx.compose.ui.test.hasText(warning))
            .fetchSemanticsNodes()
            .let { nodes -> assertTrue("Snackbar should not be shown", nodes.isEmpty()) }
    }

    @Test
    fun hostListScreenContent_emptyStateAddButtonNavigatesToEditor() {
        var editedHost: Host? = Host(nickname = "sentinel", hostname = "sentinel")

        setHostListContent(
            uiState = HostListUiState(hosts = emptyList()),
            onNavigateToEditHost = { editedHost = it },
        )

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.empty_hosts_message))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.hostpref_add_host))
            .performClick()

        assertTrue(editedHost == null)
    }

    @Test
    fun hostListScreenContent_topMenuInvokesActionsAndDisconnectAll() {
        var sortCalled = false
        var settingsCalled = false
        var profilesCalled = false
        var pubkeysCalled = false
        var exportCalled = false
        var importCalled = false
        var helpCalled = false
        var disconnectAllCalled = false

        setHostListContent(
            uiState = HostListUiState(sortedByColor = true),
            onToggleSortOrder = { sortCalled = true },
            onNavigateToSettings = { settingsCalled = true },
            onNavigateToProfiles = { profilesCalled = true },
            onNavigateToPubkeys = { pubkeysCalled = true },
            onExportHosts = { exportCalled = true },
            onImportHosts = { importCalled = true },
            onNavigateToHelp = { helpCalled = true },
            onDisconnectAll = { disconnectAllCalled = true },
        )

        openTopMenu()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_menu_sortname)).performClick()
        assertTrue(sortCalled)

        openTopMenu()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_menu_settings)).performClick()
        assertTrue(settingsCalled)

        openTopMenu()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.profile_list_title)).performClick()
        assertTrue(profilesCalled)

        openTopMenu()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_menu_pubkeys)).performClick()
        assertTrue(pubkeysCalled)

        openTopMenu()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_menu_export_hosts)).performClick()
        assertTrue(exportCalled)

        openTopMenu()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_menu_import_hosts)).performClick()
        assertTrue(importCalled)

        openTopMenu()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.title_help)).performClick()
        assertTrue(helpCalled)

        openTopMenu()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_menu_disconnect)).performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.disconnect_all_pos)).performClick()
        assertTrue(disconnectAllCalled)
    }

    @Test
    fun hostListScreenContent_hostRowsShowStateAndNavigate() {
        var navigatedHost: Host? = null
        val connected = testHost(id = 1L, nickname = "prod", protocol = "ssh", color = "#4CAF50")
        val disconnected = testHost(id = 2L, nickname = "legacy", protocol = "telnet", color = "#F44336")

        setHostListContent(
            uiState = HostListUiState(
                hosts = listOf(connected, disconnected),
                connectionStates = mapOf(
                    connected.id to ConnectionState.CONNECTED,
                    disconnected.id to ConnectionState.DISCONNECTED,
                ),
            ),
            onNavigateToConsole = { navigatedHost = it },
        )

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_connected))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.image_description_disconnected))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(HostListTestTags.itemRow(connected.id))
            .performClick()
        assertTrue(navigatedHost == connected)
    }

    @Test
    fun hostListScreenContent_makingShortcutSelectsHost() {
        var selectedShortcutHost: Host? = null
        val connected = testHost(id = 1L, nickname = "prod", protocol = "ssh", color = "#4CAF50")

        setHostListContent(
            uiState = HostListUiState(hosts = listOf(connected)),
            makingShortcut = true,
            onSelectShortcut = { selectedShortcutHost = it },
        )

        composeTestRule
            .onNodeWithTag(HostListTestTags.itemRow(connected.id))
            .performClick()
        assertTrue(selectedShortcutHost == connected)
    }

    @Test
    fun hostListScreenContent_hostMenuInvokesActionsAndDialogs() {
        var editedHost: Host? = null
        var portForwardHost: Host? = null
        var duplicatedHost: Host? = null
        var forgottenHost: Host? = null
        var disconnectedHost: Host? = null
        var deletedHost: Host? = null
        val host = testHost(id = 3L, nickname = "staging", protocol = "ssh")

        setHostListContent(
            uiState = HostListUiState(
                hosts = listOf(host),
                connectionStates = mapOf(host.id to ConnectionState.CONNECTED),
            ),
            onNavigateToEditHost = { editedHost = it },
            onNavigateToPortForwards = { portForwardHost = it },
            onDuplicateHost = { duplicatedHost = it },
            onForgetHostKeys = { forgottenHost = it },
            onDisconnectHost = { disconnectedHost = it },
            onDeleteHost = { deletedHost = it },
        )

        openHostMenu(host)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_host_edit)).performClick()
        assertTrue(editedHost == host)

        openHostMenu(host)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_host_portforwards)).performClick()
        assertTrue(portForwardHost == host)

        openHostMenu(host)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_host_duplicate)).performClick()
        assertTrue(duplicatedHost == host)

        openHostMenu(host)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_host_forget_keys)).performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.button_yes)).performClick()
        assertTrue(forgottenHost == host)

        openHostMenu(host)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_host_disconnect)).performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.button_yes)).performClick()
        assertTrue(disconnectedHost == host)

        openHostMenu(host)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.list_host_delete)).performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.button_yes)).performClick()
        assertTrue(deletedHost == host)
    }

    @Test
    fun hostListScreenContent_disconnectDisabledWhenHostNotConnected() {
        val host = testHost(id = 4L, nickname = "offline", protocol = "ssh", color = null)

        setHostListContent(
            uiState = HostListUiState(
                hosts = listOf(host),
                connectionStates = mapOf(host.id to ConnectionState.DISCONNECTED),
            ),
        )

        openHostMenu(host)
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.list_host_disconnect))
            .assertIsNotEnabled()
    }

    @Test
    fun hostListScreenContent_portForwardChipShowsActiveCounts() {
        val host = testHost(id = 5L, nickname = "tunnels", protocol = "ssh")

        setHostListContent(
            uiState = HostListUiState(
                hosts = listOf(host),
                connectionStates = mapOf(host.id to ConnectionState.CONNECTED),
                portForwards = mapOf(
                    host.id to listOf(
                        testPortForward(id = 1L, hostId = host.id, enabled = true),
                        testPortForward(id = 2L, hostId = host.id),
                    ),
                ),
            ),
        )

        composeTestRule
            .onNodeWithTag(HostListTestTags.itemPortForwardChip(host.id), useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_chip_active, 1, 2))
            .assertIsDisplayed()
    }

    @Test
    fun hostListScreenContent_chipHiddenWhenNoPortForwards() {
        val host = testHost(id = 6L, nickname = "plain", protocol = "ssh")

        setHostListContent(
            uiState = HostListUiState(hosts = listOf(host)),
        )

        composeTestRule
            .onNodeWithTag(HostListTestTags.itemPortForwardChip(host.id), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun hostListScreenContent_chipOpensQuickToggleSheetAndTogglesForward() {
        var toggledForward: PortForward? = null
        var toggledEnable: Boolean? = null
        val host = testHost(id = 7L, nickname = "sheet-host", protocol = "ssh")
        val forward = testPortForward(id = 11L, hostId = host.id, nickname = "MySQL Tunnel")

        setHostListContent(
            uiState = HostListUiState(
                hosts = listOf(host),
                connectionStates = mapOf(host.id to ConnectionState.CONNECTED),
                portForwards = mapOf(host.id to listOf(forward)),
            ),
            onTogglePortForward = { pf, enable ->
                toggledForward = pf
                toggledEnable = enable
            },
        )

        composeTestRule
            .onNodeWithTag(HostListTestTags.itemPortForwardChip(host.id), useUnmergedTree = true)
            .performClick()
        composeTestRule
            .onNodeWithText("MySQL Tunnel")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(PortForwardQuickToggleTestTags.switchRow(forward.id), useUnmergedTree = true)
            .performClick()
        assertTrue(toggledForward == forward)
        assertTrue(toggledEnable == true)
    }

    @Test
    fun hostListScreenContent_sheetSwitchDisabledAndManageNavigatesWhenDisconnected() {
        var managedHost: Host? = null
        val host = testHost(id = 8L, nickname = "offline-tunnels", protocol = "ssh")
        val forward = testPortForward(id = 12L, hostId = host.id)

        setHostListContent(
            uiState = HostListUiState(
                hosts = listOf(host),
                portForwards = mapOf(host.id to listOf(forward)),
            ),
            onNavigateToPortForwards = { managedHost = it },
        )

        composeTestRule
            .onNodeWithTag(HostListTestTags.itemPortForwardChip(host.id), useUnmergedTree = true)
            .performClick()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.portforward_quick_connect_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PortForwardQuickToggleTestTags.switchRow(forward.id), useUnmergedTree = true)
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag(PortForwardQuickToggleTestTags.MANAGE_BUTTON, useUnmergedTree = true)
            .performClick()
        assertTrue(managedHost == host)
    }

    private fun openTopMenu() {
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.button_more_options))
            .performClick()
    }

    private fun openHostMenu(host: Host) {
        composeTestRule
            .onNodeWithTag(HostListTestTags.itemMenuButton(host.id))
            .performClick()
    }

    private fun setHostListContent(
        uiState: HostListUiState = HostListUiState(),
        makingShortcut: Boolean = false,
        onNavigateToConsole: (Host) -> Unit = {},
        onSelectShortcut: (Host) -> Unit = {},
        onNavigateToEditHost: (Host?) -> Unit = {},
        onNavigateToSettings: () -> Unit = {},
        onNavigateToPubkeys: () -> Unit = {},
        onNavigateToPortForwards: (Host) -> Unit = {},
        onNavigateToProfiles: () -> Unit = {},
        onNavigateToHelp: () -> Unit = {},
        onToggleSortOrder: () -> Unit = {},
        onDeleteHost: (Host) -> Unit = {},
        onDuplicateHost: (Host) -> Unit = {},
        onForgetHostKeys: (Host) -> Unit = {},
        onDisconnectHost: (Host) -> Unit = {},
        onDisconnectAll: () -> Unit = {},
        onTogglePortForward: (PortForward, Boolean) -> Unit = { _, _ -> },
        onExportHosts: () -> Unit = {},
        onImportHosts: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            ConnectBotTheme {
                HostListScreenContent(
                    uiState = uiState,
                    makingShortcut = makingShortcut,
                    onNavigateToConsole = onNavigateToConsole,
                    onSelectShortcut = onSelectShortcut,
                    onNavigateToEditHost = onNavigateToEditHost,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToPubkeys = onNavigateToPubkeys,
                    onNavigateToPortForwards = onNavigateToPortForwards,
                    onNavigateToProfiles = onNavigateToProfiles,
                    onNavigateToHelp = onNavigateToHelp,
                    onToggleSortOrder = onToggleSortOrder,
                    onDeleteHost = onDeleteHost,
                    onDuplicateHost = onDuplicateHost,
                    onForgetHostKeys = onForgetHostKeys,
                    onDisconnectHost = onDisconnectHost,
                    onDisconnectAll = onDisconnectAll,
                    onTogglePortForward = onTogglePortForward,
                    onExportHosts = onExportHosts,
                    onImportHosts = onImportHosts,
                )
            }
        }
    }

    private fun testPortForward(
        id: Long,
        hostId: Long,
        nickname: String = "tunnel-$id",
        enabled: Boolean = false,
    ): PortForward = PortForward(
        id = id,
        hostId = hostId,
        nickname = nickname,
        type = "local",
        sourceAddr = "localhost",
        sourcePort = 8080,
        destAddr = "localhost",
        destPort = 80,
    ).apply { setEnabled(enabled) }

    private fun testHost(
        id: Long,
        nickname: String,
        protocol: String = "ssh",
        color: String? = "#2196F3",
    ): Host = Host(
        id = id,
        nickname = nickname,
        protocol = protocol,
        username = "user",
        hostname = "$nickname.example.com",
        port = if (protocol == "telnet") 23 else 22,
        color = color,
    )
}
