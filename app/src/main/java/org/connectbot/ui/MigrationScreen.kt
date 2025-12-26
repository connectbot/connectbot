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

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.migration.MigrationState
import org.connectbot.data.migration.MigrationStatus
import org.connectbot.ui.theme.ConnectBotTheme

/**
 * Full-screen UI shown during database migration.
 */
@Composable
fun MigrationScreen(
    uiState: MigrationUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background // Surface uses this by default
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (uiState) {
                is MigrationUiState.Checking -> {
                    CheckingMigrationContent()
                }

                is MigrationUiState.InProgress -> {
                    MigrationInProgressContent(state = uiState.state)
                }

                is MigrationUiState.Failed -> {
                    MigrationFailedContent(
                        error = uiState.error,
                        debugLog = uiState.debugLog,
                        onRetry = onRetry
                    )
                }

                is MigrationUiState.Completed -> {
                    // This state is handled by MainActivity - screen is hidden
                }
            }
        }
    }
}

@Composable
private fun CheckingMigrationContent() {
    CircularProgressIndicator(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = "Checking database migration status"
            }
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.migration_checking),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun MigrationInProgressContent(state: MigrationState) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.migration_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = state.currentStep,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.status == MigrationStatus.IN_PROGRESS) {
            Spacer(modifier = Modifier.height(24.dp))

            // Show migration statistics
            if (state.hostsMigrated > 0 || state.pubkeysMigrated > 0) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.migration_stats_hosts, state.hostsMigrated),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.migration_stats_pubkeys, state.pubkeysMigrated),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (state.portForwardsMigrated > 0) {
                        Text(
                            text = stringResource(
                                R.string.migration_stats_port_forwards,
                                state.portForwardsMigrated
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (state.knownHostsMigrated > 0) {
                        Text(
                            text = stringResource(
                                R.string.migration_stats_known_hosts,
                                state.knownHostsMigrated
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Show warnings if any
            if (state.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = stringResource(R.string.migration_warnings_title),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.migration_warnings_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        state.warnings.forEach { warning ->
                            Text(
                                text = "• $warning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val warningsText = state.warnings.joinToString("\n") { "• $it" }
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText("warnings", warningsText).toClipEntry()
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.migration_copy_warnings))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.migration_warning),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MigrationFailedContent(
    error: String,
    debugLog: List<String>,
    onRetry: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.migration_failed_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.migration_failed_message, error),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        if (debugLog.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.migration_debug_log_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    debugLog.forEach { logEntry ->
                        Text(
                            text = logEntry,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val logText = debugLog.joinToString("\n")
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipData.newPlainText("debug_log", logText).toClipEntry()
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.migration_copy_debug_log))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRetry) {
            Text(stringResource(R.string.migration_retry))
        }

        Spacer(modifier = Modifier.height(16.dp))

        val annotatedString = buildAnnotatedString {
            val url = "https://github.com/connectbot/connectbot/issues"
			val fullText = stringResource(id = R.string.migration_failed_help)
			val startIndex = fullText.indexOf($$"%1$s")
			if (startIndex != -1) {
				append(fullText.take(startIndex))
                withLink(LinkAnnotation.Url(url)) { append(url) }
				append(fullText.substring(startIndex + $$"%1$s".length))
			} else {
				append(fullText)
			}
		}
        Text(
            text = annotatedString,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@ScreenPreviews
@Composable
private fun MigrationScreenCheckingPreview() {
    ConnectBotTheme {
        MigrationScreen(
            uiState = MigrationUiState.Checking,
            onRetry = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun MigrationScreenInProgressPreview() {
    ConnectBotTheme {
        MigrationScreen(
            uiState = MigrationUiState.InProgress(
                state = MigrationState(
                    status = MigrationStatus.IN_PROGRESS,
                    progress = 0.65f,
                    currentStep = "Migrating hosts...",
                    hostsMigrated = 12,
                    pubkeysMigrated = 5,
                    portForwardsMigrated = 8,
                    knownHostsMigrated = 25,
                    warnings = listOf(
                        "Found 2 duplicate host nickname(s): server, backup. Appending suffixes to make them unique.",
                        "Found 1 duplicate SSH key nickname(s): myKey. Appending suffixes to make them unique."
                    )
                )
            ),
            onRetry = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun MigrationScreenInProgressEmptyPreview() {
    ConnectBotTheme {
        MigrationScreen(
            uiState = MigrationUiState.InProgress(
                state = MigrationState(
                    status = MigrationStatus.IN_PROGRESS,
                    progress = 0.15f,
                    currentStep = "Starting migration...",
                    hostsMigrated = 0,
                    pubkeysMigrated = 0,
                    portForwardsMigrated = 0,
                    knownHostsMigrated = 0
                )
            ),
            onRetry = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun MigrationScreenFailedPreview() {
    ConnectBotTheme {
        MigrationScreen(
            uiState = MigrationUiState.Failed(
                error = "Database corruption detected",
                debugLog = listOf(
                    "Starting database migration",
                    "Step 1: Reading legacy databases",
                    "Legacy data read: 10 hosts, 5 pubkeys, 3 port forwards, 15 known hosts, 2 color schemes",
                    "Step 2: Validating data",
                    "WARNING: Found 1 duplicate host nickname(s): server. Appending suffixes to make them unique.",
                    "Step 3: Transforming data to Room entities",
                    "Step 4: Writing to new database",
                    "ERROR: Migration failed: Database corruption detected"
                )
            ),
            onRetry = {}
        )
    }
}
