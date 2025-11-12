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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mud.terminal.vt320
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.service.TerminalBridge
import org.connectbot.service.TerminalKeyListener

/**
 * Virtual keyboard with terminal special keys (Ctrl, Esc, arrows, function keys, etc.)
 * Positioned at bottom of console screen, horizontally scrollable
 * Auto-hides after 3 seconds of inactivity with fade animation
 */
@Composable
fun TerminalKeyboard(
    bridge: TerminalBridge,
    onHideKeyboard: () -> Unit,
    onHideIme: () -> Unit = {},
    playAnimation: Boolean = false,
    modifier: Modifier = Modifier
) {
    val keyHandler = bridge.keyHandler
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val scrollState = rememberScrollState()

    // Auto-hide after 3 seconds (matching ConsoleActivity.KEYBOARD_DISPLAY_TIME)
    LaunchedEffect(lastInteractionTime) {
        delay(3000)
        onHideKeyboard()
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

    // Function to reset the auto-hide timer
    val resetTimer = {
        lastInteractionTime = System.currentTimeMillis()
    }

    Surface(
        modifier = modifier
            .pointerInput(Unit) {
                // Reset timer on any touch interaction
                detectTapGestures(
                    onPress = {
                        resetTimer()
                        tryAwaitRelease()
                    }
                )
            },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp), // Match key height for compact layout
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
                KeyButton(
                    text = stringResource(R.string.button_key_ctrl),
                    contentDescription = stringResource(R.string.image_description_toggle_control_character),
                    onClick = {
                        keyHandler.metaPress(TerminalKeyListener.OUR_CTRL_ON, true)
                        resetTimer()
                    }
                )

                // Esc key
                KeyButton(
                    text = stringResource(R.string.button_key_esc),
                    contentDescription = stringResource(R.string.image_description_send_escape_character),
                    onClick = {
                        keyHandler.sendEscape()
                        resetTimer()
                    }
                )

                // Tab key
                KeyButton(
                    text = "⇥", // Tab symbol
                    contentDescription = stringResource(R.string.image_description_send_tab_character),
                    onClick = {
                        keyHandler.sendTab()
                        resetTimer()
                    }
                )

                // Arrow keys (repeatable)
                RepeatableKeyButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.image_description_up),
                    onPress = {
                        keyHandler.sendPressedKey(vt320.KEY_UP)
                        resetTimer()
                    }
                )

                RepeatableKeyButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.image_description_down),
                    onPress = {
                        keyHandler.sendPressedKey(vt320.KEY_DOWN)
                        resetTimer()
                    }
                )

                RepeatableKeyButton(
                    icon = Icons.Default.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.image_description_left),
                    onPress = {
                        keyHandler.sendPressedKey(vt320.KEY_LEFT)
                        resetTimer()
                    }
                )

                RepeatableKeyButton(
                    icon = Icons.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.image_description_right),
                    onPress = {
                        keyHandler.sendPressedKey(vt320.KEY_RIGHT)
                        resetTimer()
                    }
                )

                // Home/End
                KeyButton(
                    text = stringResource(R.string.button_key_home),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_HOME)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_end),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_END)
                        resetTimer()
                    }
                )

                // Page Up/Down
                KeyButton(
                    text = stringResource(R.string.button_key_pgup),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_PAGE_UP)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_pgdn),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_PAGE_DOWN)
                        resetTimer()
                    }
                )

                // Function keys F1-F12
                KeyButton(
                    text = stringResource(R.string.button_key_f1),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F1)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f2),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F2)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f3),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F3)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f4),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F4)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f5),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F5)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f6),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F6)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f7),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F7)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f8),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F8)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f9),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F9)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f10),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F10)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f11),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F11)
                        resetTimer()
                    }
                )

                KeyButton(
                    text = stringResource(R.string.button_key_f12),
                    contentDescription = null,
                    onClick = {
                        keyHandler.sendPressedKey(vt320.KEY_F12)
                        resetTimer()
                    }
                )
            }

            // Keyboard hide button (always visible on right)
            Surface(
                onClick = {
                    onHideIme()
                    resetTimer()
                },
                modifier = Modifier.size(width = 45.dp, height = 30.dp),
                shape = androidx.compose.ui.graphics.RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.KeyboardHide,
                        contentDescription = stringResource(R.string.image_description_hide_keyboard),
                        modifier = Modifier.size(20.dp)
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
    text: String,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width = 45.dp, height = 30.dp),
        shape = androidx.compose.ui.graphics.RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                style = MaterialTheme.typography.labelSmall
            )
        }
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

    Surface(
        modifier = modifier
            .size(width = 45.dp, height = 30.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        // Single press
                        onPress()

                        // Start repeat after initial delay
                        repeatJob = coroutineScope.launch {
                            delay(500) // Initial delay before repeat
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
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = if (isPressed) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
