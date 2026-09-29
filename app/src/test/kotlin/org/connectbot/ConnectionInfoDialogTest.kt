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

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.connectbot.data.entity.Host
import org.connectbot.sshlib.ConnectionInfo
import org.connectbot.ui.components.ConnectionInfoDialog
import org.connectbot.ui.screens.console.ConnectionDetails
import org.connectbot.ui.screens.console.ConnectionStatus
import org.connectbot.ui.screens.console.PortForwardDetails
import org.connectbot.util.HostConstants
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectionInfoDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val ssh = ConnectionInfo("mlkem768x25519-sha256", "ssh-ed25519", "chacha20-poly1305@openssh.com", "aes256-ctr", null, "hmac-sha2-256")
    private val details = ConnectionDetails(Host(hostname = "server.example", username = "user"), ConnectionStatus.CONNECTED, "192.0.2.1", ssh)

    private fun show(details: ConnectionDetails, onDismiss: () -> Unit = {}) {
        compose.setContent { MaterialTheme { ConnectionInfoDialog(details, onDismiss) } }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("connection-info-fields").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test
    fun showsHostAndNegotiatedAlgorithmsWithIntegratedMac() {
        show(details)
        compose.onNodeWithText("server.example").assertIsDisplayed()
        scrollTo("mlkem768x25519-sha256")
        scrollTo("chacha20-poly1305@openssh.com")
        scrollTo("Integrated with encryption")
        scrollTo("hmac-sha2-256")
        scrollTo("Post-quantum key exchange")
        compose.onNodeWithText("Yes").assertIsDisplayed()
    }

    @Test
    fun missingNegotiation_isExplained() {
        show(details.copy(ssh = null))
        scrollTo("SSH negotiation details are not available.")
    }

    @Test
    fun moshDetails_areLabeledAsSshBootstrap() {
        show(details.copy(host = details.host.copy(protocol = "mosh")))
        compose.onNodeWithText("SSH port").assertIsDisplayed()
        scrollTo("SSH bootstrap details")
    }

    @Test
    fun nonSshConnection_showsConnectionWithoutNegotiationSection() {
        show(details.copy(host = details.host.copy(protocol = "telnet"), ssh = null))
        compose.onNodeWithText("TELNET").assertIsDisplayed()
        compose.onNodeWithText("SSH negotiation details").assertDoesNotExist()
    }

    @Test
    fun disconnectedConnection_showsStatusAndRetainedDetails() {
        show(details.copy(status = ConnectionStatus.DISCONNECTED))
        scrollTo("Disconnected")
        scrollTo("mlkem768x25519-sha256")
    }

    @Test
    fun closeDismissesPanel() {
        var dismissed = false
        show(details) { dismissed = true }
        compose.onNodeWithText("Close").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun remoteForward_showsAssignedPortSeparatelyFromConfiguredSource() {
        val forward = PortForwardDetails("Database tunnel", HostConstants.PORTFORWARD_REMOTE, "localhost", 0, "database.example", 5432, 41234, true)
        show(details.copy(portForwards = listOf(forward)))
        scrollTo("Port forwards")
        scrollTo("Database tunnel")
        scrollTo("Remote")
        scrollTo("localhost:0")
        scrollTo("41234")
        scrollTo("Active")
        scrollTo("database.example:5432")
    }

    @Test
    fun dynamicForwardWithoutListener_showsInactiveAndNotBound() {
        val forward = PortForwardDetails("SOCKS proxy", HostConstants.PORTFORWARD_DYNAMIC5, "localhost", 1080, null, 0, null, false)
        show(details.copy(portForwards = listOf(forward)))
        scrollTo("SOCKS proxy")
        scrollTo("Dynamic (SOCKS)")
        scrollTo("Not bound")
        scrollTo("Inactive")
        compose.onNodeWithText("Destination").assertDoesNotExist()
    }
}
