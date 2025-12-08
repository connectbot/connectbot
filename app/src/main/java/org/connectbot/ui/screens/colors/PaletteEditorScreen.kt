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

package org.connectbot.ui.screens.colors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.connectbot.R
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.components.RgbColorPickerDialog
import org.connectbot.ui.theme.ConnectBotTheme

/**
 * Screen for editing the full 256-color palette of a color scheme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteEditorScreen(
    schemeId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember {
        PaletteEditorViewModel(context, schemeId)
    }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when there's an error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.title_palette_editor))
                        Text(
                            text = uiState.schemeName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.showResetAllDialog() }) {
                        Text(stringResource(R.string.button_reset_all_colors))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ANSI Colors (0-15)
                        item {
                            ColorSection(
                                title = stringResource(R.string.section_ansi_colors),
                                colorIndices = 0..15,
                                palette = uiState.palette,
                                onColorClick = { viewModel.editColor(it) },
                                onColorReset = { viewModel.resetColor(it) }
                            )
                        }

                        // RGB Cube (16-231)
                        item {
                            ColorSection(
                                title = stringResource(R.string.section_rgb_cube),
                                colorIndices = 16..231,
                                palette = uiState.palette,
                                onColorClick = { viewModel.editColor(it) },
                                onColorReset = { viewModel.resetColor(it) },
                                columns = 18 // 6x6x6 cube displayed as 18 columns
                            )
                        }

                        // Grayscale (232-255)
                        item {
                            ColorSection(
                                title = stringResource(R.string.section_grayscale),
                                colorIndices = 232..255,
                                palette = uiState.palette,
                                onColorClick = { viewModel.editColor(it) },
                                onColorReset = { viewModel.resetColor(it) },
                                columns = 12
                            )
                        }
                    }
                }
            }
        }
    }

    // Color editor dialog
    if (uiState.editingColorIndex != null) {
        val colorIndex = uiState.editingColorIndex!!
        RgbColorPickerDialog(
            title = stringResource(R.string.dialog_title_edit_color, colorIndex),
            initialColor = uiState.palette[colorIndex],
            onColorSelected = { newColor ->
                viewModel.updateColor(colorIndex, newColor)
            },
            onDismiss = { viewModel.closeColorEditor() }
        )
    }

    // Reset all confirmation dialog
    if (uiState.showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideResetAllDialog() },
            title = { Text(stringResource(R.string.dialog_title_reset_all)) },
            text = { Text(stringResource(R.string.dialog_message_reset_all)) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetAllColors() }) {
                    Text(
                        stringResource(R.string.button_reset_all_colors),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideResetAllDialog() }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }
}

/**
 * A section showing a group of colors (e.g., ANSI colors, RGB cube, grayscale).
 */
@Composable
private fun ColorSection(
    title: String,
    colorIndices: IntRange,
    palette: IntArray,
    onColorClick: (Int) -> Unit,
    onColorReset: (Int) -> Unit,
    columns: Int = 16,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // Custom grid layout using Column and Row
        val colorList = colorIndices.toList()
        val rows = colorList.chunked(columns)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            rows.forEach { rowColors ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowColors.forEach { colorIndex ->
                        ColorCell(
                            colorIndex = colorIndex,
                            color = palette[colorIndex],
                            onClick = { onColorClick(colorIndex) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Add empty spaces to fill the last row if needed
                    repeat(columns - rowColors.size) {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Individual color cell in the palette grid.
 */
@Composable
private fun ColorCell(
    colorIndex: Int,
    color: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                color = Color(color),
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Show index for ANSI colors (0-15) for easier identification
        if (colorIndex < 16) {
            Text(
                text = colorIndex.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (colorIndex == 0 || colorIndex in 1..6) {
                    Color.White
                } else {
                    Color.Black
                }
            )
        }
    }
}

@ScreenPreviews
@Composable
private fun PaletteEditorScreenPreview() {
    ConnectBotTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "256-Color Palette Editor",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "ANSI Colors (0-15)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            // Preview of ANSI colors grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0..7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(
                                color = Color(i * 36, i * 36, i * 36),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            Text(
                text = "System Colors (16-255)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            // Preview of system colors grid (sample)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 0..7) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(
                                color = Color(i * 32, 128, i * 32),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}
