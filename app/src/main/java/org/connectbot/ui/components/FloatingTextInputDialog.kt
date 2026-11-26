/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.connectbot.R
import org.connectbot.service.TerminalBridge
import org.connectbot.terminal.VTermKey
import org.json.JSONArray

// Prompts get their own file so BackupAgent, which uploads the default preferences, never sees them
private const val PROMPT_PREFS = "text_input_prompts"
private const val PREF_DRAFT = "draft"
private const val PREF_HISTORY = "history"
private const val HISTORY_SIZE = 30

/**
 * Text input panel docked at the bottom of the console, with Compose TextField for full IME support.
 * The caller sizes it and pushes the terminal up above it; dragging past the top of the draft (or the
 * header) reports through [onResize], so the panel shrinks and the real terminal slides back into view.
 * Features:
 * - Opens tall for comfortable composing; drag it down to see the terminal above
 * - Full IME support with swipe typing, voice input, predictions
 * - Full text selection support
 * - Opens empty; the draft is saved on every edit and moves into history on the next open
 * - History of sent, cleared and left-over prompts to flip through, so no text is ever lost
 */
@Composable
fun FloatingTextInputDialog(
    bridge: TerminalBridge,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    /** Grows (positive) or shrinks (negative) the panel by a dragged distance in px; returns the part used. */
    onResize: (Float) -> Float = { 0f },
) {
    val context = LocalContext.current

    // Text state and focus
    val prompts = remember { context.getSharedPreferences(PROMPT_PREFS, Context.MODE_PRIVATE) }
    var text by remember { mutableStateOf(initialText) }
    var history by remember {
        // Each open starts empty; what was left in the draft (sent, or closed by accident) is one "older" tap away
        val loaded = addToHistory(parseHistory(prompts.getString(PREF_HISTORY, null)), prompts.getString(PREF_DRAFT, "").orEmpty())
        prompts.edit {
            putString(PREF_HISTORY, JSONArray(loaded).toString())
            remove(PREF_DRAFT)
        }
        mutableStateOf(loaded)
    }

    // -1 shows the draft, otherwise history[pos]. Editing always turns the text into the draft.
    var pos by remember { mutableIntStateOf(-1) }
    val focusRequester = remember { FocusRequester() }

    // Request focus when shown
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun savedDraft() = prompts.getString(PREF_DRAFT, "").orEmpty()

    fun keep(value: String) {
        history = addToHistory(history, value)
        prompts.edit { putString(PREF_HISTORY, JSONArray(history).toString()) }
    }

    fun setDraft(value: String) {
        // Leaving an old prompt by editing it replaces the draft, so keep the draft first
        if (pos >= 0) keep(savedDraft())
        pos = -1
        text = value
        prompts.edit { putString(PREF_DRAFT, value) }
    }

    fun show(newPos: Int) {
        pos = newPos
        text = if (newPos < 0) savedDraft() else history[newPos]
    }

    fun clear() {
        keep(text) // one "older" tap brings it back
        setDraft("")
    }

    // Resets on every open: sending and closing is the usual case
    var mode by remember { mutableStateOf(SendMode.SEND_AND_CLOSE) }

    // Paste (bracketed when the remote app enables it) so inner newlines don't submit, then press
    // Enter once to submit unless only pasting. Kept text moves to history on the next open.
    fun sendText() {
        val body = text.trimEnd('\n')
        if (body.isEmpty()) return
        bridge.terminalEmulator.pasteText(body)
        if (mode.enter) bridge.terminalEmulator.dispatchKey(0, VTermKey.ENTER)
        keep(text)
        if (pos >= 0) pos = 0 // the sent prompt moved to the top
        if (mode.close) onDismiss()
    }

    BackHandler(onBack = onDismiss)

    // Dragging down past the draft's top shrinks the panel (the terminal above slides into view); dragging up grows it
    val resize = remember(onResize) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = if (available.y < 0) Offset(0f, -onResize(-available.y)) else Offset.Zero

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = if (available.y > 0) Offset(0f, -onResize(-available.y)) else Offset.Zero
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .nestedScroll(resize),
    ) {
        // Grab bar: shows the panel can be dragged (down to reveal the terminal above, up to grow again)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .draggable(rememberDraggableState { delta -> onResize(-delta) }, Orientation.Vertical)
                .padding(top = 8.dp, bottom = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f), RoundedCornerShape(2.dp)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .draggable(rememberDraggableState { delta -> onResize(-delta) }, Orientation.Vertical)
                .height(48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(onClickLabel = stringResource(R.string.terminal_text_input_mode_change), role = Role.Button) {
                            mode = SendMode.entries[(mode.ordinal + 1) % SendMode.entries.size]
                        },
                ) {
                    Text(
                        text = stringResource(mode.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                    )
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(start = 4.dp).size(16.dp),
                    )
                }
            }

            HeaderButton(Icons.Default.KeyboardArrowUp, R.string.terminal_text_input_older, enabled = pos < history.lastIndex) { show(pos + 1) }
            HeaderButton(Icons.Default.KeyboardArrowDown, R.string.terminal_text_input_newer, enabled = pos >= 0) { show(pos - 1) }
            HeaderButton(Icons.Default.Delete, R.string.terminal_text_input_clear, enabled = text.isNotEmpty(), onClick = ::clear)
            HeaderButton(Icons.Default.Close, R.string.button_close, onClick = onDismiss)
        }

        // The draft fills the panel; Send sits bottom right, next to the thumb. The draft scrolls even when
        // short, so a drag on it always reaches the panel resize above.
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val draftHeight = maxHeight
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TextField(
                        value = text,
                        onValueChange = ::setDraft,
                        placeholder = {
                            Text(stringResource(R.string.terminal_text_input_dialog_label))
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                        ),
                        visualTransformation = SpecialCharVisualTransformation,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        shape = RectangleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = draftHeight)
                            .focusRequester(focusRequester),
                    )
                }
            }

            IconButton(
                onClick = { sendText() },
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .size(48.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.button_send),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, @StringRes description: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(24.dp)) {
        Icon(
            icon,
            contentDescription = stringResource(description),
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Newest first, without duplicates; blank text isn't worth keeping. */
private fun addToHistory(history: List<String>, text: String): List<String> = if (text.isBlank()) history else (listOf(text) + history.filter { it != text }).take(HISTORY_SIZE)

private fun parseHistory(json: String?): List<String> = json?.let {
    runCatching { JSONArray(it).let { array -> List(array.length(), array::getString) } }.getOrNull()
}.orEmpty()

private enum class SendMode(@StringRes val label: Int, val enter: Boolean, val close: Boolean) {
    SEND_AND_CLOSE(R.string.terminal_text_input_mode_send_close, enter = true, close = true),
    SEND(R.string.terminal_text_input_mode_send, enter = true, close = false),
    PASTE(R.string.terminal_text_input_mode_paste, enter = false, close = true),
}
