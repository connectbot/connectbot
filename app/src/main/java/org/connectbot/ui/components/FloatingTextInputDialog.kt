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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.connectbot.R
import org.connectbot.service.TerminalBridge
import kotlin.math.roundToInt

private const val NEWLINE_SYMBOL = "↩"
private const val TAB_SYMBOL = "⇥"

private object SpecialCharVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text

        // Build transformed string and a map from original index to transformed index
        val transformedBuilder = StringBuilder()
        val originalToTransformed = IntArray(original.length + 1)
        for (i in original.indices) {
            originalToTransformed[i] = transformedBuilder.length
            when (original[i]) {
                '\n' -> transformedBuilder.append("$NEWLINE_SYMBOL\n")
                '\t' -> transformedBuilder.append(TAB_SYMBOL)
                else -> transformedBuilder.append(original[i])
            }
        }
        originalToTransformed[original.length] = transformedBuilder.length
        val transformed = transformedBuilder.toString()

        // Build reverse map from transformed index to original index
        val transformedToOriginal = IntArray(transformed.length + 1)
        for (i in original.indices) {
            val tStart = originalToTransformed[i]
            val tEnd = originalToTransformed[i + 1]
            for (t in tStart until tEnd) {
                transformedToOriginal[t] = i
            }
        }
        transformedToOriginal[transformed.length] = original.length

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, original.length)]

            override fun transformedToOriginal(offset: Int): Int = transformedToOriginal[offset.coerceIn(0, transformed.length)]
        }

        return TransformedText(AnnotatedString(transformed), offsetMapping)
    }
}

private const val PREF_FLOATING_INPUT_X = "floating_input_x"
private const val PREF_FLOATING_INPUT_Y = "floating_input_y"
private const val PREF_FLOATING_INPUT_WIDTH = "floating_input_width"
private const val PREF_FLOATING_INPUT_HEIGHT = "floating_input_height"
private const val PREF_FLOATING_INPUT_AUTO_ENTER = "floating_input_auto_enter"
private const val DEFAULT_X_RATIO = 0.05f
private const val DEFAULT_Y_RATIO = 0.3f
private const val DEFAULT_WIDTH_RATIO = 0.9f
private const val DEFAULT_HEIGHT_RATIO = 0.25f
private const val MIN_WIDTH_DP = 200f
private const val MIN_HEIGHT_DP = 80f

/**
 * Floating, draggable text input dialog with Compose TextField for full IME support.
 * Features:
 * - Draggable window that can be positioned anywhere
 * - Full IME support with swipe typing, voice input, predictions
 * - Persistent positioning saved in SharedPreferences
 * - Material Design 3 styling with blue accent
 * - Full text selection support
 */
@Composable
fun FloatingTextInputDialog(
    bridge: TerminalBridge,
    initialText: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Calculate screen dimensions
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val minWidthPx = with(density) { MIN_WIDTH_DP.dp.toPx() }
    val minHeightPx = with(density) { MIN_HEIGHT_DP.dp.toPx() }

    // Load saved position/size or use defaults
    val savedX = prefs.getFloat(PREF_FLOATING_INPUT_X, DEFAULT_X_RATIO)
    val savedY = prefs.getFloat(PREF_FLOATING_INPUT_Y, DEFAULT_Y_RATIO)
    val savedWidth = prefs.getFloat(PREF_FLOATING_INPUT_WIDTH, DEFAULT_WIDTH_RATIO)
    val savedHeight = prefs.getFloat(PREF_FLOATING_INPUT_HEIGHT, DEFAULT_HEIGHT_RATIO)

    // Current position and size in pixels
    var offsetX by remember { mutableFloatStateOf(screenWidthPx * savedX) }
    var offsetY by remember { mutableFloatStateOf(screenHeightPx * savedY) }
    var windowWidthPx by remember { mutableFloatStateOf(screenWidthPx * savedWidth) }
    var windowHeightPx by remember { mutableFloatStateOf(screenHeightPx * savedHeight) }

    // Auto-Enter toggle: when on, Send appends a real Enter (CR) and closes the dialog
    var autoEnter by remember {
        mutableStateOf(prefs.getBoolean(PREF_FLOATING_INPUT_AUTO_ENTER, true))
    }

    // Text state and focus
    var text by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    // Request focus when shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler { onDismiss() }

    // Save position and size when dialog closes
    DisposableEffect(Unit) {
        onDispose {
            prefs.edit {
                putFloat(PREF_FLOATING_INPUT_X, offsetX / screenWidthPx)
                putFloat(PREF_FLOATING_INPUT_Y, offsetY / screenHeightPx)
                putFloat(PREF_FLOATING_INPUT_WIDTH, windowWidthPx / screenWidthPx)
                putFloat(PREF_FLOATING_INPUT_HEIGHT, windowHeightPx / screenHeightPx)
                putBoolean(PREF_FLOATING_INPUT_AUTO_ENTER, autoEnter)
            }
        }
    }

    // Send text helper function
    fun sendText() {
        if (text.isNotEmpty()) {
            if (autoEnter) {
                bridge.injectString(text + "\r")
                text = ""
                onDismiss()
                return
            }
            bridge.injectString(text)
            text = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Dismiss on tap outside the dialog. The Column below consumes its own
            // gestures, so taps inside the dialog never reach this clickable.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            // Eat drags in the empty modal area so they don't reach the terminal.
            .pointerInput(Unit) {
                detectDragGestures { _, _ -> }
            }
    ) {
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .width(with(density) { windowWidthPx.toDp() })
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(12.dp)
                )
                // Consume taps inside the dialog so the outer click-to-dismiss doesn't fire.
                .pointerInput(Unit) {
                    detectTapGestures { /* swallow */ }
                }
        ) {
                // Draggable header with send and close buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(
                                    0f,
                                    screenWidthPx - windowWidthPx
                                )
                                offsetY = (offsetY + dragAmount.y).coerceIn(
                                    0f,
                                    screenHeightPx - windowHeightPx
                                )
                            }
                        }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.terminal_text_input_dialog_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { autoEnter = !autoEnter },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardReturn,
                            contentDescription = stringResource(R.string.terminal_text_input_auto_enter),
                            tint = if (autoEnter) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.button_close),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // TextField with send button and resize handle to the right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { (windowHeightPx - 36.dp.toPx()).coerceAtLeast(minHeightPx).toDp() })
                ) {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = {
                            Text(stringResource(R.string.terminal_text_input_dialog_label))
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text
                        ),
                        visualTransformation = SpecialCharVisualTransformation,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(bottomStart = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .focusRequester(focusRequester)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(bottomEnd = 12.dp)
                            ),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { sendText() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.button_send),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        windowWidthPx = (windowWidthPx + dragAmount.x).coerceIn(
                                            minWidthPx,
                                            screenWidthPx - offsetX
                                        )
                                        windowHeightPx = (windowHeightPx + dragAmount.y).coerceIn(
                                            minHeightPx,
                                            screenHeightPx - offsetY
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.OpenInFull,
                                contentDescription = stringResource(R.string.terminal_text_input_resize_handle),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
