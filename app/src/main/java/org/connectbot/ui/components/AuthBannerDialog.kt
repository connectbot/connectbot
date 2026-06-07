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

package org.connectbot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.service.AuthBanner
import org.connectbot.ui.screens.console.ConsoleTestTags
import org.connectbot.ui.theme.ConnectBotTheme

/**
 * A dialog that displays an authentication banner from the server.
 * Banners often contain important information or links for authentication.
 *
 * @param banner The [AuthBanner] to display.
 * @param onDismiss Callback when the dialog is dismissed or closed.
 * @param modifier Optional [Modifier] for the dialog window.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthBannerDialog(
    banner: AuthBanner,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        AuthBannerDialogContent(
            banner = banner,
            onDismiss = onDismiss,
        )
    }
}

/**
 * The content of the [AuthBannerDialog]. Extracted for easier previewing
 * and to avoid issues with dialog windows in some environments.
 */
@Composable
fun AuthBannerDialogContent(
    banner: AuthBanner,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val annotatedMessage = remember(banner.message, banner.urls, linkStyle) {
        buildAnnotatedString {
            var startIndex = 0
            banner.urls.forEach { url ->
                val urlIndex = banner.message.indexOf(url, startIndex)
                if (urlIndex >= 0) {
                    append(banner.message.substring(startIndex, urlIndex))
                    withLink(LinkAnnotation.Url(url)) {
                        withStyle(linkStyle) {
                            append(url)
                        }
                    }
                    startIndex = urlIndex + url.length
                }
            }
            append(banner.message.substring(startIndex))
        }
    }

    Surface(
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_banner_title, banner.sourceName),
                style = MaterialTheme.typography.titleMedium,
                color = AlertDialogDefaults.titleContentColor,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = annotatedMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = AlertDialogDefaults.textContentColor,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .testTag(ConsoleTestTags.AUTH_BANNER_MESSAGE),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.button_close))
                }
            }
        }
    }
}

@Preview
@Composable
private fun AuthBannerDialogPreview() {
    ConnectBotTheme {
        AuthBannerDialogContent(
            banner = AuthBanner(
                id = 1L,
                sourceName = "exampleuser@ssh.example.com:2222",
                message = "Welcome to example.com. Please visit this URL to authenticate: https://gateway.example.com/auth/892ea3hfeed for more information.",
                urls = listOf("https://gateway.example.com/auth/892ea3hfeed"),
                languageTag = "en",
            ),
            onDismiss = {},
        )
    }
}
