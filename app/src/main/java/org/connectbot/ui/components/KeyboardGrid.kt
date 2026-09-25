/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.service.KeyboardActions
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.service.TerminalBridge
import org.connectbot.ui.screens.keyboard.KeyboardViewModel
import org.connectbot.util.PreferenceConstants
import java.util.UUID

@Composable
internal fun keyboardLabel(item: KeyboardItem, macros: List<KeyboardMacro>): String = if (item.kind == "macro") {
    macros.find { it.id == item.macroId }?.name ?: stringResource(R.string.keyboard_missing_macro)
} else {
    val resource = when (item.target) {
        "ctrl" -> R.string.keyboard_key_ctrl
        "alt" -> R.string.keyboard_key_alt
        "shift" -> R.string.keyboard_key_shift
        "escape" -> R.string.keyboard_key_escape
        "tab" -> R.string.keyboard_key_tab
        "enter" -> R.string.keyboard_key_enter
        "backspace" -> R.string.keyboard_key_backspace
        "delete" -> R.string.keyboard_key_delete
        "insert" -> R.string.keyboard_key_insert
        "arrow_up" -> R.string.keyboard_key_arrow_up
        "arrow_down" -> R.string.keyboard_key_arrow_down
        "arrow_left" -> R.string.keyboard_key_arrow_left
        "arrow_right" -> R.string.keyboard_key_arrow_right
        "home" -> R.string.keyboard_key_home
        "end" -> R.string.keyboard_key_end
        "page_up" -> R.string.keyboard_key_page_up
        "page_down" -> R.string.keyboard_key_page_down
        "text_input" -> R.string.keyboard_key_text_input
        "toggle_compose" -> R.string.keyboard_key_toggle_compose
        "toggle_ime" -> R.string.keyboard_key_toggle_ime
        "f1" -> R.string.keyboard_key_f1
        "f2" -> R.string.keyboard_key_f2
        "f3" -> R.string.keyboard_key_f3
        "f4" -> R.string.keyboard_key_f4
        "f5" -> R.string.keyboard_key_f5
        "f6" -> R.string.keyboard_key_f6
        "f7" -> R.string.keyboard_key_f7
        "f8" -> R.string.keyboard_key_f8
        "f9" -> R.string.keyboard_key_f9
        "f10" -> R.string.keyboard_key_f10
        "f11" -> R.string.keyboard_key_f11
        "f12" -> R.string.keyboard_key_f12
        else -> R.string.keyboard_unsupported
    }
    stringResource(resource)
}

@Composable
internal fun ConfigurableTerminalKeyboard(
    bridge: TerminalBridge,
    onInteraction: () -> Unit,
    onHideIme: () -> Unit,
    onShowIme: () -> Unit,
    onOpenTextInput: () -> Unit,
    onScrollInProgressChange: (Boolean) -> Unit,
    imeVisible: Boolean,
    playAnimation: Boolean,
    showImeToggleKey: Boolean,
    isComposeModeActive: Boolean,
    onToggleComposeMode: () -> Unit,
    onShortcutModifierChange: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KeyboardViewModel = hiltViewModel(),
) {
    val config by viewModel.configuration.collectAsState()
    val selection by remember(bridge) { viewModel.layoutSelection(bridge.host) }.collectAsState(null)
    val modifiers by bridge.keyHandler.modifierState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val bumpy = remember { PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PreferenceConstants.BUMPY_ARROWS, false) }
    val errorLabel = stringResource(R.string.keyboard_action_failed)
    LaunchedEffect(bridge) {
        bridge.keyboardErrors.collect { Toast.makeText(context, "$errorLabel: $it", Toast.LENGTH_LONG).show() }
    }
    val layout = config.layout(selection) ?: return
    val items = config.items.filter { it.layoutId == layout.id }
    if (runCatching { KeyboardDefaults.validate(layout, items) }.isFailure) {
        Text(stringResource(R.string.keyboard_unsupported), modifier = modifier)
        return
    }
    KeyboardGrid(
        layout = layout, items = items, macros = config.macros, modifierState = modifiers,
        unsupportedMacroIds = config.unsupportedMacroIds,
        imeVisible = imeVisible, composeActive = isComposeModeActive,
        modifier = modifier, playAnimation = playAnimation, onScrollInProgressChange = onScrollInProgressChange,
        onPress = { item ->
            onInteraction()
            when (item.kind) {
                "key" -> {
                    bridge.sendConfiguredKey(item.target)
                    if (bumpy && item.target.startsWith("arrow_")) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }

                "modifier" -> {
                    if (item.target in KeyboardDefaults.modifiers) bridge.keyHandler.metaPress(KeyboardActions.modifier(item.target), true)
                    onShortcutModifierChange()
                }

                "macro" -> config.macros.find { it.id == item.macroId }?.let { bridge.sendKeyboardMacro(it.document) }

                "app" -> when (item.target) {
                    "text_input" -> onOpenTextInput()
                    "toggle_compose" -> onToggleComposeMode()
                    "toggle_ime" -> if (imeVisible) onHideIme() else onShowIme()
                }
            }
        },
    )
}

/** Coordinates are physical grid cells, not layout-direction-relative terminal directions. */
@Composable
internal fun KeyboardGrid(
    layout: KeyboardLayout,
    items: List<KeyboardItem>,
    macros: List<KeyboardMacro>,
    onPress: (KeyboardItem) -> Unit,
    modifier: Modifier = Modifier,
    unsupportedMacroIds: Set<UUID> = emptySet(),
    modifierState: ModifierState? = null,
    imeVisible: Boolean = false,
    composeActive: Boolean = false,
    editing: Boolean = false,
    onMove: ((KeyboardItem, Int, Int) -> Unit)? = null,
    playAnimation: Boolean = false,
    onScrollInProgressChange: (Boolean) -> Unit = {},
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val scrolling by rememberUpdatedState(onScrollInProgressChange)
    LaunchedEffect(horizontal.isScrollInProgress, vertical.isScrollInProgress) {
        scrolling(horizontal.isScrollInProgress || vertical.isScrollInProgress)
    }
    DisposableEffect(Unit) { onDispose { scrolling(false) } }
    LaunchedEffect(layout.id, playAnimation) {
        if (playAnimation) {
            delay(100)
            horizontal.animateScrollTo(horizontal.maxValue)
            delay(300)
            horizontal.animateScrollTo(0)
        }
    }
    val visible = items.filter { editing || it.visible }
    val columns = (items.maxOfOrNull { it.column + it.columnSpan } ?: 0).coerceIn(0, 1000)
    val rows = (items.maxOfOrNull { it.row + it.rowSpan } ?: 0).coerceIn(0, 1000)
    val width = layout.buttonWidth.takeIf { it.isFinite() && it > 0 }?.coerceAtMost(1000f) ?: 45f
    val height = layout.buttonHeight.takeIf { it.isFinite() && it > 0 }?.coerceAtMost(1000f) ?: 30f
    Box(modifier = modifier.horizontalScroll(horizontal).verticalScroll(vertical)) {
        Layout(content = {
            visible.forEach { item ->
                key(item.id) {
                    val label = keyboardLabel(item, macros)
                    val active = when (item.target) {
                        "ctrl" -> modifierState?.ctrlState ?: ModifierLevel.OFF
                        "alt" -> modifierState?.altState ?: ModifierLevel.OFF
                        "shift" -> modifierState?.shiftState ?: ModifierLevel.OFF
                        "toggle_compose" -> if (composeActive) ModifierLevel.LOCKED else ModifierLevel.OFF
                        "toggle_ime" -> if (imeVisible) ModifierLevel.TRANSIENT else ModifierLevel.OFF
                        else -> ModifierLevel.OFF
                    }
                    val supported = when (item.kind) {
                        "key" -> item.target in KeyboardDefaults.keys
                        "modifier" -> item.target in KeyboardDefaults.modifiers
                        "app" -> item.target in KeyboardDefaults.controls
                        "macro" -> item.macroId !in unsupportedMacroIds && macros.any { it.id == item.macroId }
                        else -> false
                    }
                    GridButton(item, label, active, editing || supported, editing, onPress, onMove)
                }
            }
        }) { measurables, _ ->
            val cellWidth = width.dp.roundToPx().coerceAtLeast(1)
            val cellHeight = height.dp.roundToPx().coerceAtLeast(1)
            val placeables = measurables.zip(visible).map { (measurable, item) ->
                measurable.measure(Constraints.fixed((cellWidth * item.columnSpan.coerceIn(1, 1000)).coerceAtMost(16000), (cellHeight * item.rowSpan.coerceIn(1, 1000)).coerceAtMost(16000)))
            }
            layout((cellWidth * columns).coerceAtMost(16000), (cellHeight * rows).coerceAtMost(16000)) {
                placeables.zip(visible).forEach { (placeable, item) -> placeable.place(item.column * cellWidth, item.row * cellHeight) }
            }
        }
    }
}

@Composable
private fun GridButton(
    item: KeyboardItem,
    label: String,
    active: ModifierLevel,
    enabled: Boolean,
    editing: Boolean,
    onPress: (KeyboardItem) -> Unit,
    onMove: ((KeyboardItem, Int, Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val currentPress by rememberUpdatedState(onPress)
    val currentMove by rememberUpdatedState(onMove)
    val scope = rememberCoroutineScope()
    val repeat = !editing && item.kind == "key" && item.target.startsWith("arrow_")
    val moveUp = stringResource(R.string.keyboard_move_up)
    val moveDown = stringResource(R.string.keyboard_move_down)
    val moveLeft = stringResource(R.string.keyboard_move_left)
    val moveRight = stringResource(R.string.keyboard_move_right)
    val description = when (item.target) {
        "arrow_up" -> stringResource(R.string.image_description_up)
        "arrow_down" -> stringResource(R.string.image_description_down)
        "arrow_left" -> stringResource(R.string.image_description_left)
        "arrow_right" -> stringResource(R.string.image_description_right)
        "text_input" -> stringResource(R.string.terminal_keyboard_text_input_button)
        "toggle_ime" -> stringResource(if (active == ModifierLevel.OFF) R.string.image_description_show_keyboard else R.string.image_description_hide_keyboard)
        else -> label
    }
    val unsupported = stringResource(R.string.keyboard_unsupported)
    val level = when (active) {
        ModifierLevel.OFF -> stringResource(R.string.keyboard_off)
        ModifierLevel.TRANSIENT -> stringResource(R.string.keyboard_once)
        ModifierLevel.LOCKED -> stringResource(R.string.keyboard_locked)
    }
    var gestures = modifier.testTag("keyboard_button_${item.id}").semantics {
        contentDescription = description
        role = Role.Button
        if (item.kind == "modifier") stateDescription = level
        if (!enabled) stateDescription = unsupported
        if (enabled) {
            onClick {
                currentPress(item)
                true
            }
        } else {
            disabled()
        }
        if (editing) {
            customActions = listOf(
                CustomAccessibilityAction(moveUp) {
                    currentMove?.invoke(item, item.row - 1, item.column)
                    true
                },
                CustomAccessibilityAction(moveDown) {
                    currentMove?.invoke(item, item.row + 1, item.column)
                    true
                },
                CustomAccessibilityAction(moveLeft) {
                    currentMove?.invoke(item, item.row, item.column - 1)
                    true
                },
                CustomAccessibilityAction(moveRight) {
                    currentMove?.invoke(item, item.row, item.column + 1)
                    true
                },
            )
        }
    }
    if (enabled) {
        gestures = gestures.pointerInput(item.id, repeat) {
            detectTapGestures(onPress = {
                if (!repeat) {
                    if (tryAwaitRelease()) currentPress(item)
                } else {
                    var sent = false
                    val job = scope.launch {
                        delay(ViewConfiguration.getTapTimeout().toLong())
                        sent = true
                        currentPress(item)
                        delay(400)
                        while (true) {
                            currentPress(item)
                            delay(50)
                        }
                    }
                    try {
                        if (tryAwaitRelease() && !sent) currentPress(item)
                    } finally {
                        job.cancel()
                    }
                }
            })
        }
    }
    Surface(
        modifier = gestures,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = when (active) {
            ModifierLevel.OFF -> MaterialTheme.colorScheme.surface.copy(alpha = if (enabled && item.visible) 0.85f else 0.35f)
            ModifierLevel.TRANSIENT -> MaterialTheme.colorScheme.primaryContainer
            ModifierLevel.LOCKED -> MaterialTheme.colorScheme.primary
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            val icon = when (item.target.takeIf { item.kind != "macro" }) {
                "arrow_up" -> Icons.Default.KeyboardArrowUp
                "arrow_down" -> Icons.Default.KeyboardArrowDown
                "arrow_left" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
                "arrow_right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                "text_input" -> Icons.Default.Edit
                "toggle_ime" -> if (active == ModifierLevel.OFF) Icons.Default.Keyboard else Icons.Default.KeyboardHide
                else -> null
            }
            if (icon == null) {
                Text(if (item.kind == "app" && item.target == "toggle_compose") stringResource(R.string.button_key_ime) else label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            } else {
                // Terminal directions retain their physical meaning in RTL layouts.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
