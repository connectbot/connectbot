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

import android.content.SharedPreferences
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
import androidx.compose.material.icons.filled.ContentPaste
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardItem
import org.connectbot.data.keyboard.KeyboardLayers
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.data.keyboard.KeyboardMacro
import org.connectbot.data.keyboard.MacroAction
import org.connectbot.data.keyboard.MacroFormat
import org.connectbot.service.KeyboardActions
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.service.TerminalBridge
import org.connectbot.terminal.TerminalKeyCatalog
import org.connectbot.ui.screens.keyboard.KeyboardViewModel
import org.connectbot.util.PreferenceConstants
import java.util.UUID

@Composable
internal fun keyboardLabel(item: KeyboardItem, macros: List<KeyboardMacro>): String = if (item.kind == "layer") {
    if (item.target == "next") stringResource(R.string.keyboard_fn) else stringResource(R.string.keyboard_layer_short, item.target.toIntOrNull() ?: 0)
} else if (item.kind == "macro" || (item.kind == "key" && item.macroId != null)) {
    macros.find { it.id == item.macroId }?.name ?: stringResource(R.string.keyboard_missing_macro)
} else if (TerminalKeyCatalog.byTarget(item.target)?.character != null || item.target.startsWith("keypad_")) {
    TerminalKeyCatalog.byTarget(item.target)?.displayName() ?: stringResource(R.string.keyboard_unsupported)
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
        "paste" -> R.string.keyboard_key_paste
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
    onPasteRequest: () -> Unit,
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
    val preferences = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    var bumpy by remember(preferences) { mutableStateOf(preferences.getBoolean(PreferenceConstants.BUMPY_ARROWS, true)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == PreferenceConstants.BUMPY_ARROWS || key == null) {
                bumpy = prefs.getBoolean(PreferenceConstants.BUMPY_ARROWS, true)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
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
        imeVisible = imeVisible, composeActive = isComposeModeActive, sessionKey = bridge,
        modifier = modifier, playAnimation = playAnimation, onScrollInProgressChange = onScrollInProgressChange,
        onPress = { item ->
            onInteraction()
            if (bumpy) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            when (item.kind) {
                "key" -> {
                    bridge.sendConfiguredKey(item.target, config.macros.find { it.id == item.macroId }?.document)
                }

                "modifier" -> {
                    if (item.target in KeyboardDefaults.modifiers) bridge.keyHandler.metaPress(KeyboardActions.modifier(item.target), true)
                    onShortcutModifierChange()
                }

                "macro" -> config.macros.find { it.id == item.macroId }?.let { bridge.sendKeyboardMacro(it.document) }

                "app" -> when (item.target) {
                    "paste" -> onPasteRequest()
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
    activeLayer: Int? = null,
    sessionKey: Any? = null,
    onMove: ((KeyboardItem, Int, Int) -> Unit)? = null,
    playAnimation: Boolean = false,
    onScrollInProgressChange: (Boolean) -> Unit = {},
) {
    var selectedLayer by remember(layout.id, sessionKey) { mutableIntStateOf(KeyboardLayers.initial(items)) }
    val populated = KeyboardLayers.populated(items)
    val layer = if (editing) activeLayer ?: 0 else selectedLayer.takeIf { it in populated } ?: KeyboardLayers.initial(items)
    val press: (KeyboardItem) -> Unit = { item ->
        if (!editing && item.kind == "layer") {
            val current = selectedLayer.takeIf { it in populated } ?: KeyboardLayers.initial(items)
            KeyboardLayers.destination(items, current, item.target)?.let { selectedLayer = it }
        }
        onPress(item)
    }
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val scrolling by rememberUpdatedState(onScrollInProgressChange)
    LaunchedEffect(horizontal.isScrollInProgress, vertical.isScrollInProgress) {
        scrolling(horizontal.isScrollInProgress || vertical.isScrollInProgress)
    }
    DisposableEffect(Unit) { onDispose { scrolling(false) } }
    LaunchedEffect(layout.id, playAnimation) {
        if (playAnimation && !layout.fitToWidth) {
            delay(100)
            horizontal.animateScrollTo(horizontal.maxValue)
            delay(300)
            horizontal.animateScrollTo(0)
        }
    }
    val visible = items.filter { it.layer == layer && (editing || it.visible) }
    val columns = (items.maxOfOrNull { it.column + it.columnSpan } ?: 0).coerceIn(0, 1000)
    val rows = (items.maxOfOrNull { it.row + it.rowSpan } ?: 0).coerceIn(0, 1000)
    val width = layout.buttonWidth.takeIf { it.isFinite() && it > 0 }?.coerceAtMost(1000f) ?: 45f
    val height = layout.buttonHeight.takeIf { it.isFinite() && it > 0 }?.coerceAtMost(1000f) ?: 30f
    Box(modifier = modifier.then(if (layout.fitToWidth) Modifier else Modifier.horizontalScroll(horizontal).verticalScroll(vertical))) {
        Layout(content = {
            visible.forEach { item ->
                key(item.id) {
                    val label = keyboardLabel(item, macros)
                    val active = if (item.kind == "layer" && (item.target == layer.toString() || (item.target == "next" && layer != KeyboardLayers.initial(items)))) {
                        ModifierLevel.LOCKED
                    } else {
                        when (item.target) {
                            "ctrl" -> modifierState?.ctrlState ?: ModifierLevel.OFF
                            "alt" -> modifierState?.altState ?: ModifierLevel.OFF
                            "shift" -> modifierState?.shiftState ?: ModifierLevel.OFF
                            "toggle_compose" -> if (composeActive) ModifierLevel.LOCKED else ModifierLevel.OFF
                            "toggle_ime" -> if (imeVisible) ModifierLevel.TRANSIENT else ModifierLevel.OFF
                            else -> ModifierLevel.OFF
                        }
                    }
                    val supported = when (item.kind) {
                        "key" ->
                            item.target in KeyboardDefaults.keys &&
                                (item.macroId == null || (item.macroId !in unsupportedMacroIds && macros.any { it.id == item.macroId }))

                        "modifier" -> item.target in KeyboardDefaults.modifiers

                        "app" -> item.target in KeyboardDefaults.controls

                        "layer" -> KeyboardLayers.destination(items, layer, item.target) != null

                        "macro" -> item.macroId !in unsupportedMacroIds && macros.any { it.id == item.macroId }

                        else -> false
                    }
                    val shortcut = macros.find { it.id == item.macroId }?.let { macro ->
                        runCatching { MacroFormat.decode(macro.document).singleOrNull() as? MacroAction.Key }.getOrNull()
                    }
                    val combination = shortcut?.key?.let { target ->
                        TerminalKeyCatalog.byTarget(target)?.let { key ->
                            (shortcut.modifiers.map { shortcutModifier -> keyboardLabel(KeyboardItem(layoutId = layout.id, kind = "modifier", target = shortcutModifier), emptyList()) } + key.displayName()).joinToString("+")
                        }
                    }
                    val description = combination?.let { stringResource(R.string.keyboard_shortcut_description, fullShortcutLabel(label, shortcut), it) }
                    GridButton(item, label, active, editing || supported, editing, press, onMove, shortcutDescription = description, layerNumber = layer)
                }
            }
        }) { measurables, constraints ->
            val fit = layout.fitToWidth && constraints.hasBoundedWidth
            val gridWidth = if (fit) constraints.maxWidth else (width.dp.roundToPx().coerceAtLeast(1) * columns).coerceAtMost(16000)
            val cellHeight = height.dp.roundToPx().coerceAtLeast(1).let {
                if (fit && constraints.hasBoundedHeight && rows > 0) minOf(it, constraints.maxHeight / rows) else it
            }
            fun x(column: Int): Int = if (columns == 0) 0 else (column.toLong() * gridWidth / columns).toInt()
            val placeables = measurables.zip(visible).map { (measurable, item) ->
                measurable.measure(
                    Constraints.fixed(
                        (x(item.column + item.columnSpan) - x(item.column)).coerceIn(0, 16000),
                        (cellHeight * item.rowSpan.coerceIn(1, 1000)).coerceAtMost(16000),
                    ),
                )
            }
            layout(gridWidth, (cellHeight * rows).coerceAtMost(16000)) {
                placeables.zip(visible).forEach { (placeable, item) -> placeable.place(x(item.column), item.row * cellHeight) }
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
    shortcutDescription: String? = null,
    layerNumber: Int = 0,
) {
    val currentPress by rememberUpdatedState(onPress)
    val currentMove by rememberUpdatedState(onMove)
    val scope = rememberCoroutineScope()
    val repeat = !editing && item.kind == "key" && item.target.startsWith("arrow_")
    val moveUp = stringResource(R.string.keyboard_move_up)
    val moveDown = stringResource(R.string.keyboard_move_down)
    val moveLeft = stringResource(R.string.keyboard_move_left)
    val moveRight = stringResource(R.string.keyboard_move_right)
    val description = shortcutDescription ?: if (item.kind == "layer") {
        if (item.target == "next") stringResource(R.string.keyboard_layer_next) else stringResource(R.string.keyboard_layer, item.target.toIntOrNull() ?: 0)
    } else {
        when (item.target) {
            "arrow_up" -> stringResource(R.string.image_description_up)
            "arrow_down" -> stringResource(R.string.image_description_down)
            "arrow_left" -> stringResource(R.string.image_description_left)
            "arrow_right" -> stringResource(R.string.image_description_right)
            "text_input" -> stringResource(R.string.terminal_keyboard_text_input_button)
            "toggle_ime" -> stringResource(if (active == ModifierLevel.OFF) R.string.image_description_show_keyboard else R.string.image_description_hide_keyboard)
            else -> label
        }
    }
    val layerState = if (item.kind == "layer") stringResource(R.string.keyboard_layer, layerNumber) else null
    val unsupported = stringResource(if (item.kind == "layer") R.string.keyboard_layer_empty else R.string.keyboard_unsupported)
    val level = when (active) {
        ModifierLevel.OFF -> stringResource(R.string.keyboard_off)
        ModifierLevel.TRANSIENT -> stringResource(R.string.keyboard_once)
        ModifierLevel.LOCKED -> stringResource(R.string.keyboard_locked)
    }
    var gestures = modifier.testTag("keyboard_button_${item.id}").semantics {
        contentDescription = description
        role = Role.Button
        if (item.kind == "modifier") stateDescription = level
        if (layerState != null) stateDescription = layerState
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
                "paste" -> Icons.Default.ContentPaste
                "text_input" -> Icons.Default.Edit
                "toggle_ime" -> if (active == ModifierLevel.OFF) Icons.Default.Keyboard else Icons.Default.KeyboardHide
                else -> null
            }
            if (icon == null) {
                FittedKeyboardLabel(if (item.kind == "app" && item.target == "toggle_compose") stringResource(R.string.button_key_ime) else label)
            } else {
                // Terminal directions retain their physical meaning in RTL layouts.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Measure at the user-selected font size, then compress only as much as the cell requires. */
@Composable
private fun FittedKeyboardLabel(label: String) {
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val measured = remember(label, style, measurer) { measurer.measure(label, style = style, softWrap = false, maxLines = 1) }
    Layout(content = { Text(label, style = style, softWrap = false, maxLines = 1) }) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints())
        val available = (constraints.maxWidth - 4.dp.roundToPx()).coerceAtLeast(0)
        val scaleX = if (measured.size.width == 0) 1f else minOf(1f, available.toFloat() / measured.size.width)
        val scaleY = if (placeable.height == 0) 1f else minOf(1f, constraints.maxHeight.toFloat() / placeable.height)
        val width = constraints.constrainWidth((placeable.width * scaleX).toInt())
        val height = constraints.constrainHeight((placeable.height * scaleY).toInt())
        layout(width, height) {
            placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                this.scaleX = scaleX
                this.scaleY = scaleY
            }
        }
    }
}

@Composable
private fun fullShortcutLabel(label: String, shortcut: MacroAction.Key?): String = when {
    shortcut?.key == "character_99" && shortcut.modifiers == listOf("ctrl") && label == stringResource(R.string.keyboard_action_stop) -> stringResource(R.string.keyboard_action_stop_full)
    shortcut?.key == "character_114" && shortcut.modifiers == listOf("ctrl") && label == stringResource(R.string.keyboard_action_history) -> stringResource(R.string.keyboard_action_history_full)
    shortcut?.key == "tab" && shortcut.modifiers == listOf("shift") && label == stringResource(R.string.keyboard_action_mode) -> stringResource(R.string.keyboard_action_mode_full)
    shortcut?.key == "character_106" && shortcut.modifiers == listOf("ctrl") && label == stringResource(R.string.keyboard_action_newline) -> stringResource(R.string.keyboard_action_newline_full)
    shortcut?.key == "character_111" && shortcut.modifiers == listOf("ctrl") && label == stringResource(R.string.keyboard_action_log) -> stringResource(R.string.keyboard_action_log_full)
    shortcut?.key == "character_103" && shortcut.modifiers == listOf("ctrl") && label == stringResource(R.string.keyboard_action_edit) -> stringResource(R.string.keyboard_action_edit_full)
    shortcut?.key == "character_111" && shortcut.modifiers == listOf("ctrl") && label == stringResource(R.string.keyboard_action_copy) -> stringResource(R.string.keyboard_action_copy_full)
    else -> label
}
