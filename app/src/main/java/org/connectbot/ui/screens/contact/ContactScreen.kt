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

package org.connectbot.ui.screens.contact

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.ui.PreviewScreen
import org.connectbot.ui.theme.ConnectBotTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_contact)) },
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
                Text(
                    text = stringResource(R.string.help_section_contact),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                ContactLinkItem(
                    label = stringResource(R.string.help_website),
                    url = stringResource(R.string.help_website_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }

            item {
                ContactLinkItem(
                    label = stringResource(R.string.help_github),
                    url = stringResource(R.string.help_github_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }

            item {
                ContactLinkItem(
                    label = stringResource(R.string.help_report_bug),
                    url = stringResource(R.string.help_report_bug_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.help_section_donate),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)
                )
            }

            item {
                ContactLinkItem(
                    label = stringResource(R.string.help_donate_github),
                    url = stringResource(R.string.help_donate_github_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }

            item {
                ContactLinkItem(
                    label = stringResource(R.string.help_donate_coffee),
                    url = stringResource(R.string.help_donate_coffee_url),
                    onClick = { uriHandler.openUri(it) }
                )
            }
        }
    }
}

@Composable
private fun ContactLinkItem(
    label: String,
    url: String,
    onClick: (String) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium
            )
        },
        supportingContent = {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable { onClick(url) }
    )
    HorizontalDivider()
}

@PreviewScreen
@Composable
private fun ContactScreenPreview() {
    ConnectBotTheme {
        ContactScreen(
            onNavigateBack = {}
        )
    }
}
