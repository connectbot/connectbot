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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.connectbot.R
import org.connectbot.ui.ScreenPreviews
import org.connectbot.ui.components.ColorPickerDialog
import org.connectbot.ui.theme.ConnectBotTheme

/**
 * Screen for editing terminal color scheme.
 * Allows setting foreground and background colors from the 256-color terminal palette.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSchemeManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel = remember { ColorsViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    var showForegroundPicker by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_colors)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onNavigateToSchemeManager) {
                        Text(stringResource(R.string.button_manage_schemes))
                    }
                    TextButton(onClick = { viewModel.resetToDefaults() }) {
                        Text(stringResource(R.string.menu_colors_reset))
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

                uiState.error != null -> {
                    Text(
                        text = stringResource(R.string.error_message, uiState.error ?: ""),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Color scheme selector
                        ColorSchemeSelector(
                            currentSchemeName = uiState.currentSchemeName,
                            availableSchemes = uiState.availableSchemes,
                            onSchemeSelected = { viewModel.switchToScheme(it.id) }
                        )

                        // Foreground color selector
                        ColorSelectorRow(
                            label = stringResource(
                                R.string.colors_fg_label,
                                uiState.foregroundColorIndex
                            ),
                            colorIndex = uiState.foregroundColorIndex,
                            palette = uiState.currentPalette,
                            onClick = { showForegroundPicker = true }
                        )

                        // Background color selector
                        ColorSelectorRow(
                            label = stringResource(
                                R.string.color_bg_label,
                                uiState.backgroundColorIndex
                            ),
                            colorIndex = uiState.backgroundColorIndex,
                            palette = uiState.currentPalette,
                            onClick = { showBackgroundPicker = true }
                        )

                        // Preview box
                        Text(
                            text = stringResource(R.string.title_color_picker),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    color = Color(uiState.currentPalette[uiState.backgroundColorIndex]),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "user@hostname:~$ ls -la",
                                    color = Color(uiState.currentPalette[uiState.foregroundColorIndex]),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "total 24",
                                    color = Color(uiState.currentPalette[uiState.foregroundColorIndex]),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "drwxr-xr-x  5 user  staff  160 Jan 15 10:30 .",
                                    color = Color(uiState.currentPalette[uiState.foregroundColorIndex]),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "drwxr-xr-x  3 user  staff   96 Jan 14 09:15 ..",
                                    color = Color(uiState.currentPalette[uiState.foregroundColorIndex]),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "-rw-r--r--  1 user  staff 1024 Jan 15 10:29 file.txt",
                                    color = Color(uiState.currentPalette[uiState.foregroundColorIndex]),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Color picker dialogs
    if (showForegroundPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.colors_fg_label, uiState.foregroundColorIndex),
            selectedColorIndex = uiState.foregroundColorIndex,
            palette = uiState.currentPalette,
            onColorSelected = { viewModel.updateForegroundColor(it) },
            onDismiss = { showForegroundPicker = false }
        )
    }

    if (showBackgroundPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.color_bg_label, uiState.backgroundColorIndex),
            selectedColorIndex = uiState.backgroundColorIndex,
            palette = uiState.currentPalette,
            onColorSelected = { viewModel.updateBackgroundColor(it) },
            onDismiss = { showBackgroundPicker = false }
        )
    }
}

/**
 * Row showing color name/index and a clickable color preview circle.
 */
@Composable
private fun ColorSelectorRow(
    label: String,
    colorIndex: Int,
    palette: IntArray,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = Color(palette[colorIndex]),
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
    }
}

/**
 * Dropdown selector for choosing color schemes.
 */
@Composable
private fun ColorSchemeSelector(
    currentSchemeName: String,
    availableSchemes: List<org.connectbot.data.entity.ColorScheme>,
    onSchemeSelected: (org.connectbot.data.entity.ColorScheme) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Color Scheme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { expanded = true }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentSchemeName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableSchemes.forEach { scheme ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = scheme.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (scheme.description.isNotEmpty()) {
                                Text(
                                    text = scheme.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onSchemeSelected(scheme)
                        expanded = false
                    }
                )
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun ColorsScreenPreview() {
    ConnectBotTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Color scheme selector preview
            Text("Color Scheme: Solarized Dark", style = MaterialTheme.typography.titleMedium)

            // Foreground color selector preview
            ColorSelectorRow(
                label = "Foreground Color: 7",
                colorIndex = 7,
                palette = IntArray(256) { it * 0x010101 },
                onClick = {}
            )

            // Background color selector preview
            ColorSelectorRow(
                label = "Background Color: 0",
                colorIndex = 0,
                palette = IntArray(256) { it * 0x010101 },
                onClick = {}
            )

            // Preview box
            Text(
                text = "Terminal Preview",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        color = Color.Black,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "user@hostname:~$ ls -la",
                        color = Color.LightGray,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "total 24",
                        color = Color.LightGray,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
