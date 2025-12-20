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

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.connectbot.R
import org.connectbot.service.TerminalBridge
import kotlin.math.roundToInt

private const val PREF_FLOATING_INPUT_X = "floating_input_x"
private const val PREF_FLOATING_INPUT_Y = "floating_input_y"
private const val DEFAULT_X_RATIO = 0.05f // 5% from left
private const val DEFAULT_Y_RATIO = 0.3f  // 30% from top

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

	// Window dimensions (90% of screen width)
	val windowWidthDp = configuration.screenWidthDp * 0.9f
	val windowWidthPx = with(density) { windowWidthDp.dp.toPx() }

	// Load saved position or use defaults
	val savedX = prefs.getFloat(PREF_FLOATING_INPUT_X, DEFAULT_X_RATIO)
	val savedY = prefs.getFloat(PREF_FLOATING_INPUT_Y, DEFAULT_Y_RATIO)

	// Current position in pixels
	var offsetX by remember { mutableFloatStateOf(screenWidthPx * savedX) }
	var offsetY by remember { mutableFloatStateOf(screenHeightPx * savedY) }

	// Text state and focus
	var text by remember { mutableStateOf(initialText) }
	val focusRequester = remember { FocusRequester() }

	// Request focus when shown
	LaunchedEffect(Unit) {
		focusRequester.requestFocus()
	}

	// Save position when dialog closes
	DisposableEffect(Unit) {
		onDispose {
			// Save position as ratio of screen size for orientation change support
			prefs.edit {
				putFloat(PREF_FLOATING_INPUT_X, offsetX / screenWidthPx)
				putFloat(PREF_FLOATING_INPUT_Y, offsetY / screenHeightPx)
			}
		}
	}

	// Send text helper function
	fun sendText() {
		if (text.isNotEmpty()) {
			bridge.injectString(text + "\n")
			onDismiss()
		}
	}

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(
			usePlatformDefaultWidth = false,
			decorFitsSystemWindows = false
		)
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.pointerInput(Unit) {
					// Dismiss when clicking outside
					detectDragGestures { _, _ -> }
				}
		) {
			Column(
				modifier = Modifier
					.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
					.width(windowWidthDp.dp)
					.background(
						MaterialTheme.colorScheme.surface,
						RoundedCornerShape(12.dp)
					)
			) {
				// Draggable header
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
									screenHeightPx - with(density) { 200.dp.toPx() }
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
						color = MaterialTheme.colorScheme.onPrimary
					)

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

				// TextField container
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(12.dp),
					verticalAlignment = Alignment.Bottom,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					// Compose TextField for full IME support
					TextField(
						value = text,
						onValueChange = { text = it },
						placeholder = {
							Text(stringResource(R.string.terminal_text_input_dialog_label))
						},
						keyboardOptions = KeyboardOptions(
							imeAction = ImeAction.Send
						),
						keyboardActions = KeyboardActions(
							onSend = { sendText() }
						),
						colors = TextFieldDefaults.colors(
							focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
							unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
							focusedIndicatorColor = Color.Transparent,
							unfocusedIndicatorColor = Color.Transparent
						),
						shape = RoundedCornerShape(8.dp),
						modifier = Modifier
							.weight(1f)
							.height(90.dp)
							.focusRequester(focusRequester)
					)

					// Send button
					FloatingActionButton(
						onClick = { sendText() },
						containerColor = MaterialTheme.colorScheme.primary,
						contentColor = MaterialTheme.colorScheme.onPrimary,
						modifier = Modifier.size(48.dp)
					) {
						Icon(
                            Icons.AutoMirrored.Filled.Send,
							contentDescription = stringResource(R.string.button_send)
						)
					}
				}
			}
		}
	}
}
