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

package org.connectbot.ui.components

import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.service.TerminalBridge
import org.connectbot.util.keybar.BuiltinKeyId
import org.connectbot.util.keybar.KeyBarUiVm
import org.connectbot.util.keybar.KeyEntry
import org.connectbot.util.keybar.MacroEscape
import timber.log.Timber

private const val UI_OPACITY = 0.5f

/**
 * Height of the virtual keyboard keys in dp.
 */
const val TERMINAL_KEYBOARD_HEIGHT_DP = 30

/**
 * Width of the virtual keyboard keys in dp.
 */
private const val TERMINAL_KEYBOARD_WIDTH_DP = 45

/**
 * Size of the content (icons and text) for the virtual keyboard keys in dp.
 */
private const val TERMINAL_KEYBOARD_CONTENT_SIZE_DP = 20

/**
 * Virtual keyboard with terminal special keys (Ctrl, Esc, arrows, function keys, etc.)
 * Positioned at bottom of console screen, horizontally scrollable
 * Auto-hide timer is managed by parent ConsoleScreen
 */
@Composable
fun TerminalKeyboard(
    bridge: TerminalBridge,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    onHideIme: () -> Unit = {},
    onShowIme: () -> Unit = {},
    onOpenTextInput: () -> Unit = {},
    onScrollInProgressChange: (Boolean) -> Unit = {},
    imeVisible: Boolean = false,
    playAnimation: Boolean = false,
    keyBarVm: KeyBarUiVm = hiltViewModel(),
) {
    val keyHandler = bridge.keyHandler
    val modifierState by keyHandler.modifierState.collectAsState()
    val config by keyBarVm.config.collectAsState()
    val bumpyArrows by keyBarVm.bumpyArrows.collectAsState()

    TerminalKeyboardContent(
        modifierState = modifierState,
        config = config,
        onBuiltinPress = { id ->
            keyHandler.sendBuiltin(id) { bytes -> bridge.sendBytes(bytes) }
            onInteraction()
        },
        onMacroPress = { macro ->
            runCatching { MacroEscape.expand(macro.text) }
                .onSuccess { bytes -> bridge.sendBytes(bytes) }
                .onFailure { e ->
                    Timber.w(e, "Failed to expand macro %s; ignoring tap", macro.label)
                }
            onInteraction()
        },
        onInteraction = onInteraction,
        onHideIme = onHideIme,
        onShowIme = onShowIme,
        onOpenTextInput = onOpenTextInput,
        onScrollInProgressChange = onScrollInProgressChange,
        imeVisible = imeVisible,
        playAnimation = playAnimation,
        bumpyArrows = bumpyArrows,
        modifier = modifier,
    )
}

/**
 * Stateless UI component for the terminal keyboard.
 * Separated from [TerminalKeyboard] to enable preview without TerminalBridge dependency.
 */
@Composable
private fun TerminalKeyboardContent(
    modifierState: ModifierState,
    config: List<KeyEntry>,
    onBuiltinPress: (BuiltinKeyId) -> Unit,
    onMacroPress: (KeyEntry.Macro) -> Unit,
    onInteraction: () -> Unit,
    onHideIme: () -> Unit,
    onShowIme: () -> Unit,
    onOpenTextInput: () -> Unit,
    onScrollInProgressChange: (Boolean) -> Unit,
    imeVisible: Boolean,
    playAnimation: Boolean,
    bumpyArrows: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val currentOnScrollInProgressChange by rememberUpdatedState(onScrollInProgressChange)
    val view = LocalView.current

    if (bumpyArrows) {
        view.isHapticFeedbackEnabled = true
    }

    // Notify parent when scroll state changes
    LaunchedEffect(scrollState.isScrollInProgress) {
        currentOnScrollInProgressChange(scrollState.isScrollInProgress)
    }

    // Auto-scroll animation on first appearance (only if playAnimation is true)
    LaunchedEffect(playAnimation) {
        if (playAnimation) {
            // Wait a moment for layout to complete
            delay(100)

            // Scroll all the way to the right to show all keys
            scrollState.animateScrollTo(
                value = scrollState.maxValue,
                animationSpec = tween(durationMillis = 500),
            )

            // Then scroll back to the left
            delay(300)
            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(durationMillis = 500),
            )
        }
    }

    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                // Reset timer on any touch interaction
                detectTapGestures(
                    onPress = {
                        onInteraction()
                        tryAwaitRelease()
                    },
                )
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
        tonalElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TERMINAL_KEYBOARD_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Scrollable key buttons
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.Start, // No spacing between keys
            ) {
                config.forEach { entry ->
                    when (entry) {
                        is KeyEntry.Builtin -> {
                            if (!entry.visible) return@forEach
                            BarBuiltinButton(
                                id = entry.id,
                                modifierState = modifierState,
                                bumpyArrows = bumpyArrows,
                                view = view,
                                onClick = { onBuiltinPress(entry.id) },
                            )
                        }
                        is KeyEntry.Macro -> {
                            BarMacroButton(
                                entry = entry,
                                onClick = { onMacroPress(entry) },
                            )
                        }
                    }
                }
            }

            // Text input button (always visible on right)
            Surface(
                onClick = {
                    onOpenTextInput()
                    onInteraction()
                },
                modifier = Modifier.size(
                    width = TERMINAL_KEYBOARD_WIDTH_DP.dp,
                    height = TERMINAL_KEYBOARD_HEIGHT_DP.dp,
                ),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.terminal_keyboard_text_input_button),
                        modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp),
                    )
                }
            }

            // Keyboard toggle button (always visible on right)
            Surface(
                onClick = {
                    if (imeVisible) {
                        onHideIme()
                    } else {
                        onShowIme()
                    }
                    onInteraction()
                },
                modifier = Modifier.size(
                    width = TERMINAL_KEYBOARD_WIDTH_DP.dp,
                    height = TERMINAL_KEYBOARD_HEIGHT_DP.dp,
                ),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        if (imeVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                        contentDescription = stringResource(
                            if (imeVisible) {
                                R.string.image_description_hide_keyboard
                            } else {
                                R.string.image_description_show_keyboard
                            },
                        ),
                        modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp),
                    )
                }
            }
        }
    }
}

/**
 * Renders one built-in key for the on-screen bar. Dispatches to the
 * appropriate visual primitive (ModifierKeyButton, RepeatableKeyButton,
 * KeyButton) based on the [BuiltinKeyId] and supplies the right
 * label, contentDescription, and modifier-state slice.
 */
@Composable
private fun BarBuiltinButton(
    id: BuiltinKeyId,
    modifierState: ModifierState,
    bumpyArrows: Boolean,
    view: android.view.View,
    onClick: () -> Unit,
) {
    when (id) {
        BuiltinKeyId.CTRL -> ModifierKeyButton(
            text = stringResource(R.string.button_key_ctrl),
            contentDescription = stringResource(R.string.image_description_toggle_control_character),
            modifierLevel = modifierState.ctrlState,
            onClick = onClick,
        )
        BuiltinKeyId.ALT -> ModifierKeyButton(
            text = stringResource(R.string.button_key_alt),
            contentDescription = stringResource(R.string.image_description_send_alt_character),
            modifierLevel = modifierState.altState,
            onClick = onClick,
        )
        BuiltinKeyId.SHIFT -> ModifierKeyButton(
            text = stringResource(R.string.button_key_shift),
            contentDescription = stringResource(R.string.image_description_send_shift_character),
            modifierLevel = modifierState.shiftState,
            onClick = onClick,
        )

        BuiltinKeyId.ESC -> KeyButton(
            text = stringResource(R.string.button_key_esc),
            contentDescription = stringResource(R.string.image_description_send_escape_character),
            onClick = onClick,
        )
        BuiltinKeyId.TAB -> KeyButton(
            text = "⇥",
            contentDescription = stringResource(R.string.image_description_send_tab_character),
            onClick = onClick,
        )
        BuiltinKeyId.ENTER -> KeyButton(
            text = stringResource(R.string.button_key_enter),
            contentDescription = null,
            onClick = onClick,
        )
        BuiltinKeyId.BACKSPACE -> KeyButton(
            text = stringResource(R.string.button_key_backspace),
            contentDescription = null,
            onClick = onClick,
        )
        BuiltinKeyId.DELETE -> KeyButton(
            text = stringResource(R.string.button_key_delete),
            contentDescription = null,
            onClick = onClick,
        )
        BuiltinKeyId.INSERT -> KeyButton(
            text = stringResource(R.string.button_key_insert),
            contentDescription = null,
            onClick = onClick,
        )

        BuiltinKeyId.UP -> RepeatableKeyButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = stringResource(R.string.image_description_up),
            onPress = { onClick(); maybeBump(bumpyArrows, view) },
        )
        BuiltinKeyId.DOWN -> RepeatableKeyButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.image_description_down),
            onPress = { onClick(); maybeBump(bumpyArrows, view) },
        )
        BuiltinKeyId.LEFT -> RepeatableKeyButton(
            icon = Icons.Default.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.image_description_left),
            onPress = { onClick(); maybeBump(bumpyArrows, view) },
        )
        BuiltinKeyId.RIGHT -> RepeatableKeyButton(
            icon = Icons.Default.KeyboardArrowRight,
            contentDescription = stringResource(R.string.image_description_right),
            onPress = { onClick(); maybeBump(bumpyArrows, view) },
        )

        BuiltinKeyId.HOME -> KeyButton(
            text = stringResource(R.string.button_key_home),
            contentDescription = null,
            onClick = onClick,
        )
        BuiltinKeyId.END -> KeyButton(
            text = stringResource(R.string.button_key_end),
            contentDescription = null,
            onClick = onClick,
        )
        BuiltinKeyId.PG_UP -> KeyButton(
            text = stringResource(R.string.button_key_pgup),
            contentDescription = null,
            onClick = onClick,
        )
        BuiltinKeyId.PG_DN -> KeyButton(
            text = stringResource(R.string.button_key_pgdn),
            contentDescription = null,
            onClick = onClick,
        )

        BuiltinKeyId.F1 -> KeyButton(text = stringResource(R.string.button_key_f1), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F2 -> KeyButton(text = stringResource(R.string.button_key_f2), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F3 -> KeyButton(text = stringResource(R.string.button_key_f3), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F4 -> KeyButton(text = stringResource(R.string.button_key_f4), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F5 -> KeyButton(text = stringResource(R.string.button_key_f5), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F6 -> KeyButton(text = stringResource(R.string.button_key_f6), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F7 -> KeyButton(text = stringResource(R.string.button_key_f7), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F8 -> KeyButton(text = stringResource(R.string.button_key_f8), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F9 -> KeyButton(text = stringResource(R.string.button_key_f9), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F10 -> KeyButton(text = stringResource(R.string.button_key_f10), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F11 -> KeyButton(text = stringResource(R.string.button_key_f11), contentDescription = null, onClick = onClick)
        BuiltinKeyId.F12 -> KeyButton(text = stringResource(R.string.button_key_f12), contentDescription = null, onClick = onClick)
    }
}

@Composable
private fun BarMacroButton(
    entry: KeyEntry.Macro,
    onClick: () -> Unit,
) {
    KeyButton(
        text = entry.label,
        contentDescription = entry.label,  // label is the user-chosen description
        onClick = onClick,
    )
}

private fun maybeBump(enabled: Boolean, view: android.view.View) {
    if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

/**
 * A button for single-press keys (Ctrl, Esc, Tab, Home, End, PgUp, PgDn, F1-F12)
 * Styled to match the old keyboard layout: rectangular 45dp × 30dp with border
 */
@Composable
private fun KeyButton(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val surfaceModifier = modifier
        .size(width = TERMINAL_KEYBOARD_WIDTH_DP.dp, height = TERMINAL_KEYBOARD_HEIGHT_DP.dp)

    val content: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp),
                )
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = backgroundColor,
            content = content,
        )
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = backgroundColor,
            content = content,
        )
    }
}

/**
 * A button for repeatable keys (arrow keys)
 * Starts repeating after initial delay when held down
 * Styled to match the old keyboard layout: rectangular 45dp × 30dp with border
 */
@Composable
private fun RepeatableKeyButton(
    icon: ImageVector,
    contentDescription: String?,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    // Cleanup on unmount
    DisposableEffect(Unit) {
        onDispose {
            repeatJob?.cancel()
        }
    }

    val backgroundColor =
        if (isPressed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = UI_OPACITY)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY)
        }

    KeyButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = null,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    var sentPress = false

                    // Start a job that handles initial delay, first press, and repeat
                    val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
                    repeatJob = coroutineScope.launch {
                        // Delay before first press to allow scroll gestures to steal touch
                        delay(tapTimeout)
                        if (!isPressed) return@launch

                        // First press after initial tap delay
                        sentPress = true
                        onPress()

                        // Wait before starting repeat
                        delay(500 - tapTimeout)
                        while (isPressed) {
                            sentPress = true
                            onPress()
                            delay(50) // Repeat interval
                        }
                    }

                    // Wait for release - returns true if normal release, false if gesture stolen
                    val released = tryAwaitRelease()
                    isPressed = false

                    if (released && !sentPress) {
                        // User released but key hasn't been sent yet (quick tap) - send it now
                        repeatJob?.cancel()
                        onPress()
                    } else {
                        repeatJob?.cancel()
                    }
                },
            )
        },
        backgroundColor = backgroundColor,
    )
}

@Composable
private fun ModifierKeyButton(
    text: String,
    contentDescription: String?,
    modifierLevel: ModifierLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = when (modifierLevel) {
        ModifierLevel.OFF -> MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY)
        ModifierLevel.TRANSIENT -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ModifierLevel.LOCKED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    }

    val textColor = when (modifierLevel) {
        ModifierLevel.OFF -> MaterialTheme.colorScheme.onSurface
        ModifierLevel.TRANSIENT -> MaterialTheme.colorScheme.onPrimaryContainer
        ModifierLevel.LOCKED -> MaterialTheme.colorScheme.onPrimary
    }

    KeyButton(
        text = text,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        backgroundColor = backgroundColor,
        tint = textColor,
    )
}

@Preview(name = "Terminal Keyboard - Default State", showBackground = true)
@Composable
private fun TerminalKeyboardPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            config = listOf(
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.ESC, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.TAB, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.UP, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.DOWN, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.LEFT, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.RIGHT, visible = true),
            ),
            onBuiltinPress = {},
            onMacroPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}

@Preview(name = "Terminal Keyboard - Ctrl Pressed", showBackground = true)
@Composable
private fun TerminalKeyboardCtrlPressedPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.TRANSIENT,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            config = listOf(
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.ESC, visible = true),
            ),
            onBuiltinPress = {},
            onMacroPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}

@Preview(name = "Terminal Keyboard - Ctrl Locked", showBackground = true)
@Composable
private fun TerminalKeyboardCtrlLockedPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.LOCKED,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            config = listOf(
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.ESC, visible = true),
            ),
            onBuiltinPress = {},
            onMacroPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}

@Preview(name = "Terminal Keyboard - IME Visible", showBackground = true)
@Composable
private fun TerminalKeyboardImeVisiblePreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            config = listOf(
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
                org.connectbot.util.keybar.KeyEntry.Builtin(BuiltinKeyId.ESC, visible = true),
            ),
            onBuiltinPress = {},
            onMacroPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = true,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}
