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

package org.connectbot.ui.screens.hints

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.theme.ConnectBotTheme

data class Hint(
    val title: String,
    val description: String
)

private val hints = listOf(
    Hint(
        title = "Quick Connect",
        description = "Tap the + button on the main screen to quickly connect to a host using a URI like ssh://user@hostname:port"
    ),
    Hint(
        title = "Volume Keys",
        description = "Use volume up/down to send special keys. Volume Up = Ctrl, Volume Down = Tab"
    ),
    Hint(
        title = "Scroll Back",
        description = "Swipe up/down on the screen to scroll through terminal history"
    ),
    Hint(
        title = "Multiple Connections",
        description = "Swipe left/right to switch between multiple active terminal sessions"
    ),
    Hint(
        title = "Copy/Paste",
        description = "Long-press on the terminal to select text, then use the standard Android copy/paste"
    ),
    Hint(
        title = "Port Forwarding",
        description = "Set up SSH port forwarding in the host editor for secure tunneling"
    ),
    Hint(
        title = "Public Keys",
        description = "Generate and manage SSH keys in the Manage Keys section for password-less authentication"
    ),
    Hint(
        title = "Color Schemes",
        description = "Customize terminal colors for each host in the host editor"
    ),
    Hint(
        title = "Keep Alive",
        description = "Enable 'Keep screen awake' in settings to prevent the screen from timing out during long sessions"
    ),
    Hint(
        title = "Disconnect All",
        description = "Use the menu on the main screen to quickly disconnect all active connections"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HintsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hints)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Here are some tips to help you get the most out of ConnectBot:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            items(hints) { hint ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = hint.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = hint.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun HintsScreenPreview() {
    ConnectBotTheme {
        HintsScreen(
            onNavigateBack = {}
        )
    }
}
