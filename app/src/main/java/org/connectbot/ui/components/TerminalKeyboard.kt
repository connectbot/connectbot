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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalKeyListener
import org.connectbot.terminal.VTermKey

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
    playAnimation: Boolean = false
) {
    val keyHandler = bridge.keyHandler
    val modifierState by keyHandler.modifierState.collectAsState()

    TerminalKeyboardContent(
        modifierState = modifierState,
        onCtrlPress = {
            keyHandler.metaPress(TerminalKeyListener.OUR_CTRL_ON, true)
            onInteraction()
        },
        onEscPress = {
            keyHandler.sendEscape()
            onInteraction()
        },
        onTabPress = {
            keyHandler.sendTab()
            onInteraction()
        },
        onKeyPress = { key ->
            keyHandler.sendPressedKey(key)
            onInteraction()
        },
        onInteraction = onInteraction,
        onHideIme = onHideIme,
        onShowIme = onShowIme,
        onOpenTextInput = onOpenTextInput,
        onScrollInProgressChange = onScrollInProgressChange,
        imeVisible = imeVisible,
        playAnimation = playAnimation,
        modifier = modifier
    )
}

/**
 * Stateless UI component for the terminal keyboard.
 * Separated from [TerminalKeyboard] to enable preview without TerminalBridge dependency.
 */
@Composable
private fun TerminalKeyboardContent(
    modifierState: ModifierState,
    onCtrlPress: () -> Unit,
    onEscPress: () -> Unit,
    onTabPress: () -> Unit,
    onKeyPress: (Int) -> Unit,
    onInteraction: () -> Unit,
    onHideIme: () -> Unit,
    onShowIme: () -> Unit,
    onOpenTextInput: () -> Unit,
    onScrollInProgressChange: (Boolean) -> Unit,
    imeVisible: Boolean,
    playAnimation: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val currentOnScrollInProgressChange by rememberUpdatedState(onScrollInProgressChange)

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
                animationSpec = tween(durationMillis = 500)
            )

            // Then scroll back to the left
            delay(300)
            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(durationMillis = 500)
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
                    }
                )
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TERMINAL_KEYBOARD_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scrollable key buttons
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.Start // No spacing between keys
            ) {
                // Ctrl key (sticky modifier)
                ModifierKeyButton(
                    text = stringResource(R.string.button_key_ctrl),
                    contentDescription = stringResource(R.string.image_description_toggle_control_character),
                    modifierLevel = modifierState.ctrlState,
                    onClick = onCtrlPress
                )

                // Esc key
                KeyButton(
                    text = stringResource(R.string.button_key_esc),
                    contentDescription = stringResource(R.string.image_description_send_escape_character),
                    onClick = onEscPress
                )

                // Tab key
                KeyButton(
                    text = "⇥", // Tab symbol
                    contentDescription = stringResource(R.string.image_description_send_tab_character),
                    onClick = onTabPress
                )

                // Arrow keys (repeatable)
                RepeatableKeyButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.image_description_up),
                    onPress = { onKeyPress(VTermKey.UP) }
                )

                RepeatableKeyButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.image_description_down),
                    onPress = { onKeyPress(VTermKey.DOWN) }
                )

                RepeatableKeyButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.image_description_left),
                    onPress = { onKeyPress(VTermKey.LEFT) }
                )

                RepeatableKeyButton(
                    icon = Icons.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.image_description_right),
                    onPress = { onKeyPress(VTermKey.RIGHT) }
                )

                // Home/End
                KeyButton(
                    text = stringResource(R.string.button_key_home),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.HOME) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_end),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.END) }
                )

                // Page Up/Down
                KeyButton(
                    text = stringResource(R.string.button_key_pgup),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.PAGEUP) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_pgdn),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.PAGEDOWN) }
                )

                // Function keys F1-F12
                KeyButton(
                    text = stringResource(R.string.button_key_f1),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_1) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f2),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_2) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f3),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_3) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f4),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_4) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f5),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_5) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f6),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_6) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f7),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_7) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f8),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_8) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f9),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_9) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f10),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_10) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f11),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_11) }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f12),
                    contentDescription = null,
                    onClick = { onKeyPress(VTermKey.FUNCTION_12) }
                )
            }

            // Text input button (always visible on right)
            Surface(
                onClick = {
                    onOpenTextInput()
                    onInteraction()
                },
                modifier = Modifier.size(
                    width = TERMINAL_KEYBOARD_WIDTH_DP.dp,
                    height = TERMINAL_KEYBOARD_HEIGHT_DP.dp
                ),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.terminal_keyboard_text_input_button),
                        modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp)
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
                    height = TERMINAL_KEYBOARD_HEIGHT_DP.dp
                ),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        if (imeVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                        contentDescription = stringResource(
                            if (imeVisible) {
                                R.string.image_description_hide_keyboard
                            } else {
                                R.string.image_description_show_keyboard
                            }
                        ),
                        modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp)
                    )
                }
            }
        }
    }
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
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val surfaceModifier = modifier
        .size(width = TERMINAL_KEYBOARD_WIDTH_DP.dp, height = TERMINAL_KEYBOARD_HEIGHT_DP.dp)

    val content: @Composable () -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.height(TERMINAL_KEYBOARD_CONTENT_SIZE_DP.dp)
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
            content = content
        )
    } else {
        Surface(
            modifier = surfaceModifier,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = backgroundColor,
            content = content
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
    modifier: Modifier = Modifier
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

                    // Start a job that handles initial delay, first press, and repeat
                    val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
                    repeatJob = coroutineScope.launch {
                        // Delay before first press to allow scroll gestures to steal touch
                        delay(tapTimeout)
                        if (!isPressed) return@launch

                        // First press after initial tap delay
                        onPress()

                        // Wait before starting repeat
                        delay(500 - tapTimeout)
                        while (isPressed) {
                            onPress()
                            delay(50) // Repeat interval
                        }
                    }

                    // Wait for release
                    tryAwaitRelease()
                    isPressed = false
                    repeatJob?.cancel()
                }
            )
        },
        backgroundColor = backgroundColor
    )
}

@Composable
private fun ModifierKeyButton(
    text: String,
    contentDescription: String?,
    modifierLevel: ModifierLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        tint = textColor
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
                shiftState = ModifierLevel.OFF
            ),
            onCtrlPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false
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
                shiftState = ModifierLevel.OFF
            ),
            onCtrlPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false
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
                shiftState = ModifierLevel.OFF
            ),
            onCtrlPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false
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
                shiftState = ModifierLevel.OFF
            ),
            onCtrlPress = {},
            onEscPress = {},
            onTabPress = {},
            onKeyPress = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onScrollInProgressChange = {},
            imeVisible = true,
            playAnimation = false
        )
    }
}
