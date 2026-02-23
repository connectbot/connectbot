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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.connectbot.R
import org.connectbot.ui.PreviewScreen
import org.connectbot.ui.components.ColorPickerDialog
import org.connectbot.ui.components.RgbColorPickerDialog

/**
 * Screen for editing the full 256-color palette of a color scheme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: PaletteEditorViewModel = hiltViewModel(),
    onNavigateToDuplicate: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentOnNavigateToDuplicate by rememberUpdatedState(onNavigateToDuplicate)
    LaunchedEffect(Unit) {
        viewModel.navigateToDuplicate.collect { newSchemeId ->
            currentOnNavigateToDuplicate(newSchemeId)
        }
    }

    PaletteEditorScreenContent(
        onNavigateBack = onNavigateBack,
        onClearError = viewModel::clearError,
        onShowResetAllDialog = viewModel::showResetAllDialog,
        onHideResetAllDialog = viewModel::hideResetAllDialog,
        onEditColor = { color ->
            viewModel.editColor(color)
        },
        onUpdateColor = { colorIndex, newColor ->
            viewModel.updateColor(colorIndex, newColor)
        },
        onResetAllColors = viewModel::resetAllColors,
        onCloseColorEditor = viewModel::closeColorEditor,
        onUpdateForegroundColor = viewModel::updateForegroundColor,
        onUpdateBackgroundColor = viewModel::updateBackgroundColor,
        onUpdateName = viewModel::updateName,
        onUpdateDescription = viewModel::updateDescription,
        onSaveNameAndDescription = viewModel::saveNameAndDescription,
        onShowDuplicateDialog = viewModel::showDuplicateDialog,
        onHideDuplicateDialog = viewModel::hideDuplicateDialog,
        onDuplicateScheme = viewModel::duplicateScheme,
        uiState = uiState,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteEditorScreenContent(
    onNavigateBack: () -> Unit,
    onClearError: () -> Unit,
    onShowResetAllDialog: () -> Unit,
    onHideResetAllDialog: () -> Unit,
    onEditColor: (Int) -> Unit,
    onUpdateColor: (Int, Int) -> Unit,
    onResetAllColors: () -> Unit,
    onCloseColorEditor: () -> Unit,
    onUpdateForegroundColor: (Int) -> Unit,
    onUpdateBackgroundColor: (Int) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateDescription: (String) -> Unit,
    onSaveNameAndDescription: () -> Unit,
    onShowDuplicateDialog: () -> Unit,
    onHideDuplicateDialog: () -> Unit,
    onDuplicateScheme: (String) -> Unit,
    uiState: PaletteEditorUiState,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    var showForegroundPicker by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    val currentOnClearError by rememberUpdatedState(onClearError)

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true
            )
            currentOnClearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_palette_editor)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_navigate_up)
                        )
                    }
                },
                actions = {
                    if (!uiState.isBuiltIn) {
                        TextButton(onClick = { onShowResetAllDialog() }) {
                            Text(stringResource(R.string.button_reset_all_colors))
                        }
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            SchemeInfoSection(
                                schemeName = uiState.schemeName,
                                schemeDescription = uiState.schemeDescription,
                                isBuiltIn = uiState.isBuiltIn,
                                onUpdateName = onUpdateName,
                                onUpdateDescription = onUpdateDescription,
                                onSave = onSaveNameAndDescription,
                                onDuplicateClick = onShowDuplicateDialog
                            )
                        }

                        item {
                            ForegroundBackgroundSection(
                                foregroundColorIndex = uiState.foregroundColorIndex,
                                backgroundColorIndex = uiState.backgroundColorIndex,
                                palette = uiState.palette,
                                isBuiltIn = uiState.isBuiltIn,
                                onForegroundClick = { showForegroundPicker = true },
                                onBackgroundClick = { showBackgroundPicker = true }
                            )
                        }

                        item {
                            ColorSection(
                                title = stringResource(R.string.section_ansi_colors),
                                colorIndices = 0..15,
                                palette = uiState.palette,
                                onColorClick = { if (!uiState.isBuiltIn) onEditColor(it) }
                            )
                        }

                        item {
                            TerminalPreview(
                                foregroundColorIndex = uiState.foregroundColorIndex,
                                backgroundColorIndex = uiState.backgroundColorIndex,
                                palette = uiState.palette
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.editingColorIndex != null) {
        val colorIndex = uiState.editingColorIndex
        RgbColorPickerDialog(
            title = stringResource(R.string.dialog_title_edit_color, colorIndex),
            initialColor = uiState.palette[colorIndex],
            onSelectColor = { newColor ->
                onUpdateColor(colorIndex, newColor)
            },
            onDismiss = { onCloseColorEditor() }
        )
    }

    if (showForegroundPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.label_foreground_color),
            selectedColorIndex = uiState.foregroundColorIndex,
            palette = uiState.palette,
            onSelectColor = { colorIndex ->
                onUpdateForegroundColor(colorIndex)
                showForegroundPicker = false
            },
            onDismiss = { showForegroundPicker = false }
        )
    }

    if (showBackgroundPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.label_background_color),
            selectedColorIndex = uiState.backgroundColorIndex,
            palette = uiState.palette,
            onSelectColor = { colorIndex ->
                onUpdateBackgroundColor(colorIndex)
                showBackgroundPicker = false
            },
            onDismiss = { showBackgroundPicker = false }
        )
    }

    if (uiState.showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { onHideResetAllDialog() },
            title = { Text(stringResource(R.string.dialog_title_reset_all)) },
            text = { Text(stringResource(R.string.dialog_message_reset_all)) },
            confirmButton = {
                TextButton(onClick = { onResetAllColors() }) {
                    Text(
                        stringResource(R.string.button_reset_all_colors),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onHideResetAllDialog() }) {
                    Text(stringResource(R.string.button_cancel))
                }
            }
        )
    }

    if (uiState.showDuplicateDialog) {
        DuplicateSchemeDialog(
            baseName = uiState.schemeName,
            onConfirm = onDuplicateScheme,
            onDismiss = onHideDuplicateDialog
        )
    }
}

@Composable
private fun SchemeInfoSection(
    schemeName: String,
    schemeDescription: String,
    isBuiltIn: Boolean,
    onUpdateName: (String) -> Unit,
    onUpdateDescription: (String) -> Unit,
    onSave: () -> Unit,
    onDuplicateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isBuiltIn) {
            Text(
                text = schemeName,
                style = MaterialTheme.typography.titleLarge
            )
            if (schemeDescription.isNotEmpty()) {
                Text(
                    text = schemeDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onDuplicateClick) {
                Text(stringResource(R.string.button_duplicate_scheme))
            }
        } else {
            OutlinedTextField(
                value = schemeName,
                onValueChange = onUpdateName,
                label = { Text(stringResource(R.string.label_scheme_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { onSave() }),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = schemeDescription,
                onValueChange = onUpdateDescription,
                label = { Text(stringResource(R.string.label_scheme_description)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ForegroundBackgroundSection(
    foregroundColorIndex: Int,
    backgroundColorIndex: Int,
    palette: IntArray,
    isBuiltIn: Boolean,
    onForegroundClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.section_fg_bg_colors),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FgBgColorCell(
                label = stringResource(R.string.label_foreground_color),
                colorIndex = foregroundColorIndex,
                palette = palette,
                enabled = !isBuiltIn,
                onClick = onForegroundClick
            )
            FgBgColorCell(
                label = stringResource(R.string.label_background_color),
                colorIndex = backgroundColorIndex,
                palette = palette,
                enabled = !isBuiltIn,
                onClick = onBackgroundClick
            )
        }
    }
}

@Composable
private fun FgBgColorCell(
    label: String,
    colorIndex: Int,
    palette: IntArray,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cellModifier = if (enabled) {
        modifier.width(80.dp).clickable(onClick = onClick)
    } else {
        modifier.width(80.dp)
    }

    Column(
        modifier = cellModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    color = Color(palette[colorIndex]),
                    shape = RoundedCornerShape(4.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TerminalPreview(
    foregroundColorIndex: Int,
    backgroundColorIndex: Int,
    palette: IntArray,
    modifier: Modifier = Modifier
) {
    // ANSI color roles used in the preview
    fun c(index: Int) = SpanStyle(color = Color(palette[index]))
    val fg = c(foregroundColorIndex)
    val red = c(1) // errors
    val green = c(2) // success, username
    val yellow = c(3) // warnings, modified files
    val blue = c(4) // directories (standard)
    val cyan = c(6) // hostname, symlinks
    val brightBlack = c(8) // dim text
    val brightGreen = c(10) // new/added files

    val lines: List<AnnotatedString> = listOf(
        // Prompt: user@host:~/projects$  ls -la
        buildAnnotatedString {
            withStyle(green) { append("user") }
            withStyle(fg) { append("@") }
            withStyle(cyan) { append("hostname") }
            withStyle(fg) { append(":") }
            withStyle(blue) { append("~/projects") }
            withStyle(fg) { append("\$ ") }
            withStyle(fg) { append("ls -la") }
        },
        // Directory entry in bright blue
        buildAnnotatedString {
            withStyle(fg) { append("drwxr-xr-x  ") }
            withStyle(brightGreen) { append("src") }
            withStyle(brightBlack) { append("/") }
        },
        // File entry in default fg
        buildAnnotatedString {
            withStyle(fg) { append("-rw-r--r--  README.md") }
        },
        // Prompt: git status
        buildAnnotatedString {
            withStyle(green) { append("user") }
            withStyle(fg) { append("@") }
            withStyle(cyan) { append("hostname") }
            withStyle(fg) { append(":") }
            withStyle(blue) { append("~/projects") }
            withStyle(fg) { append("\$ ") }
            withStyle(fg) { append("git status") }
        },
        // git modified file in yellow
        buildAnnotatedString {
            withStyle(fg) { append("  modified:   ") }
            withStyle(yellow) { append("src/main.kt") }
        },
        // git new file in green
        buildAnnotatedString {
            withStyle(fg) { append("  new file:   ") }
            withStyle(brightGreen) { append("src/util.kt") }
        },
        // Error output in red
        buildAnnotatedString {
            withStyle(red) { append("error: ") }
            withStyle(fg) { append("file not found: missing.txt") }
        }
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.section_color_preview),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color(palette[backgroundColorIndex]),
                    shape = RoundedCornerShape(8.dp)
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
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
    modifier: Modifier = Modifier,
    columns: Int = 8
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

@Composable
private fun DuplicateSchemeDialog(
    baseName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("Copy of $baseName") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_new_scheme)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_scheme_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}

@PreviewScreen
@Composable
private fun PaletteEditorScreenPreview() {
    PaletteEditorScreenContent(
        onNavigateBack = {},
        uiState = PaletteEditorUiState(),
        snackbarHostState = SnackbarHostState(),
        onClearError = {},
        onShowResetAllDialog = {},
        onHideResetAllDialog = {},
        onEditColor = {},
        onUpdateColor = { _, _ -> },
        onResetAllColors = {},
        onCloseColorEditor = {},
        onUpdateForegroundColor = {},
        onUpdateBackgroundColor = {},
        onUpdateName = {},
        onUpdateDescription = {},
        onSaveNameAndDescription = {},
        onShowDuplicateDialog = {},
        onHideDuplicateDialog = {},
        onDuplicateScheme = {}
    )
}
