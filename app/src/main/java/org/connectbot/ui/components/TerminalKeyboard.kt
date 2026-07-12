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

@file:Suppress("ktlint:compose:compositionlocal-allowlist")

package org.connectbot.ui.components

import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.connectbot.R
import org.connectbot.keyboard.DefaultKeyboardLayouts
import org.connectbot.keyboard.KeyIconCatalog
import org.connectbot.keyboard.KeySpec
import org.connectbot.keyboard.KeyboardKeySize
import org.connectbot.keyboard.KeyboardLayoutSpec
import org.connectbot.keyboard.ModifierKey
import org.connectbot.keyboard.SpecialKey
import org.connectbot.keyboard.TmuxAction
import org.connectbot.keyboard.dispatchKeySpec
import org.connectbot.keyboard.stringResId
import org.connectbot.service.ModifierLevel
import org.connectbot.service.ModifierState
import org.connectbot.service.TerminalKeyListener
import org.connectbot.util.PreferenceConstants

private const val UI_OPACITY = 0.5f

/** The active key size, provided by [TerminalKeyboardContent] to nested key composables. */
private val LocalKeyboardKeySize = staticCompositionLocalOf { KeyboardKeySize.MEDIUM }

/**
 * Total height of the keys bar for a layout with [rowCount] rows at [keySize].
 */
fun terminalKeyboardHeightDp(rowCount: Int, keySize: KeyboardKeySize): Int = rowCount.coerceAtLeast(1) * keySize.keyHeightDp

/**
 * Virtual keyboard with a customizable set of terminal special keys.
 * Positioned at the bottom of the console screen; each row scrolls horizontally.
 * The auto-hide timer is managed by the parent ConsoleScreen.
 *
 * @param keyHandler modifier-aware key path for the terminal that currently has
 *   focus (host shell or tmux pane).
 * @param injectText pane-aware text path for custom text keys.
 * @param layout the ordered rows of keys to render.
 */
@Composable
fun TerminalKeyboard(
    keyHandler: TerminalKeyListener,
    injectText: (String) -> Unit,
    layout: KeyboardLayoutSpec,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: KeyboardKeySize = KeyboardKeySize.MEDIUM,
    onHideIme: () -> Unit = {},
    onShowIme: () -> Unit = {},
    onOpenTextInput: () -> Unit = {},
    onOpenSnippets: () -> Unit = {},
    onTmuxAction: ((TmuxAction) -> Unit)? = null,
    onLongPress: () -> Unit = {},
    onScrollInProgressChange: (Boolean) -> Unit = {},
    imeVisible: Boolean = false,
    playAnimation: Boolean = false,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val modifierState by keyHandler.modifierState.collectAsState()
    val bumpyArrows by remember {
        mutableStateOf(prefs.getBoolean(PreferenceConstants.BUMPY_ARROWS, false))
    }

    TerminalKeyboardContent(
        layout = layout,
        keySize = keySize,
        modifierState = modifierState,
        onKeyAction = { spec ->
            dispatchKeySpec(
                spec = spec,
                keyHandler = keyHandler,
                onTmuxAction = onTmuxAction,
                injectText = injectText,
            )
            onInteraction()
        },
        onInteraction = onInteraction,
        onHideIme = onHideIme,
        onShowIme = onShowIme,
        onOpenTextInput = onOpenTextInput,
        onOpenSnippets = onOpenSnippets,
        onLongPress = onLongPress,
        onScrollInProgressChange = onScrollInProgressChange,
        imeVisible = imeVisible,
        playAnimation = playAnimation,
        bumpyArrows = bumpyArrows,
        modifier = modifier,
    )
}

/**
 * Stateless UI component for the terminal keyboard.
 * Separated from [TerminalKeyboard] so it can be previewed and tested without a
 * TerminalBridge; [onKeyAction] receives the pressed [KeySpec].
 */
@Composable
internal fun TerminalKeyboardContent(
    layout: KeyboardLayoutSpec,
    modifierState: ModifierState,
    onKeyAction: (KeySpec) -> Unit,
    onInteraction: () -> Unit,
    onHideIme: () -> Unit,
    onShowIme: () -> Unit,
    onOpenTextInput: () -> Unit,
    onOpenSnippets: () -> Unit,
    onLongPress: () -> Unit,
    onScrollInProgressChange: (Boolean) -> Unit,
    imeVisible: Boolean,
    playAnimation: Boolean,
    bumpyArrows: Boolean,
    modifier: Modifier = Modifier,
    keySize: KeyboardKeySize = KeyboardKeySize.MEDIUM,
) {
    val currentOnScrollInProgressChange by rememberUpdatedState(onScrollInProgressChange)
    val view = LocalView.current

    // At most two rows are supported; states are created unconditionally so the
    // number of remembered slots never changes between recompositions.
    val scrollState0 = rememberScrollState()
    val scrollState1 = rememberScrollState()
    val scrollStates = listOf(scrollState0, scrollState1)

    var showFnPopup by remember { mutableStateOf(false) }

    if (bumpyArrows) {
        view.isHapticFeedbackEnabled = true
    }

    // Notify parent when any row is scrolling.
    LaunchedEffect(scrollState0.isScrollInProgress, scrollState1.isScrollInProgress) {
        currentOnScrollInProgressChange(
            scrollState0.isScrollInProgress || scrollState1.isScrollInProgress,
        )
    }

    // Auto-scroll animation on first appearance (only if playAnimation is true).
    LaunchedEffect(playAnimation) {
        if (playAnimation) {
            delay(100)
            scrollState0.animateScrollTo(
                value = scrollState0.maxValue,
                animationSpec = tween(durationMillis = 500),
            )
            delay(300)
            scrollState0.animateScrollTo(
                value = 0,
                animationSpec = tween(durationMillis = 500),
            )
        }
    }

    val onKeyActionWithFn: (KeySpec) -> Unit = { spec ->
        if (spec is KeySpec.FnGrid) {
            showFnPopup = !showFnPopup
            onInteraction()
        } else {
            onKeyAction(spec)
        }
    }

    CompositionLocalProvider(LocalKeyboardKeySize provides keySize) {
        Surface(
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongPress() },
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
                    .height(terminalKeyboardHeightDp(layout.rows.size, keySize).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Scrollable rows of keys.
                Column(modifier = Modifier.weight(1f)) {
                    layout.rows.forEachIndexed { rowIndex, keys ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(scrollStates.getOrElse(rowIndex) { scrollState0 }),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            keys.forEach { key ->
                                TerminalKey(
                                    spec = key,
                                    modifierState = modifierState,
                                    bumpyArrows = bumpyArrows,
                                    onKeyAction = onKeyActionWithFn,
                                )
                            }
                        }
                    }
                }

                // Pinned right-side buttons, spanning the full bar height.
                PinnedBarButton(
                    icon = Icons.Default.Code,
                    contentDescription = stringResource(R.string.terminal_keyboard_snippets_button),
                    onClick = {
                        onOpenSnippets()
                        onInteraction()
                    },
                )
                PinnedBarButton(
                    icon = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.terminal_keyboard_text_input_button),
                    onClick = {
                        onOpenTextInput()
                        onInteraction()
                    },
                )
                PinnedBarButton(
                    icon = if (imeVisible) Icons.Default.KeyboardHide else Icons.Default.Keyboard,
                    contentDescription = stringResource(
                        if (imeVisible) {
                            R.string.image_description_hide_keyboard
                        } else {
                            R.string.image_description_show_keyboard
                        },
                    ),
                    onClick = {
                        if (imeVisible) onHideIme() else onShowIme()
                        onInteraction()
                    },
                )
            }
        }

        if (showFnPopup) {
            FnGridPopup(
                onDismiss = { showFnPopup = false },
                onFunctionKey = { key ->
                    onKeyAction(KeySpec.Special(key))
                    showFnPopup = false
                },
            )
        }
    }
}

/** The twelve function keys, laid out 4 columns × 3 rows in the Fn popup. */
private val FUNCTION_KEYS = listOf(
    SpecialKey.F1, SpecialKey.F2, SpecialKey.F3, SpecialKey.F4,
    SpecialKey.F5, SpecialKey.F6, SpecialKey.F7, SpecialKey.F8,
    SpecialKey.F9, SpecialKey.F10, SpecialKey.F11, SpecialKey.F12,
)

@Composable
private fun FnGridPopup(
    onDismiss: () -> Unit,
    onFunctionKey: (SpecialKey) -> Unit,
) {
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier.testTag("fn_popup"),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 8.dp,
        ) {
            Column {
                FUNCTION_KEYS.chunked(4).forEach { rowKeys ->
                    Row {
                        rowKeys.forEach { key ->
                            KeyButton(
                                text = defaultSpecialLabel(key),
                                contentDescription = null,
                                onClick = { onFunctionKey(key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a single key from the layout, choosing the right button style
 * (modifier / repeatable / plain) for the spec.
 */
@Composable
private fun TerminalKey(
    spec: KeySpec,
    modifierState: ModifierState,
    bumpyArrows: Boolean,
    onKeyAction: (KeySpec) -> Unit,
) {
    val view = LocalView.current
    val customIcon = KeyIconCatalog.iconFor(spec.icon)

    when (spec) {
        is KeySpec.Modifier -> ModifierKeyButton(
            text = spec.label ?: defaultModifierLabel(spec.mod),
            contentDescription = modifierContentDescription(spec.mod),
            modifierLevel = modifierState.levelFor(spec.mod),
            onClick = { onKeyAction(spec) },
        )

        is KeySpec.Special -> {
            val arrowIcon = arrowIconFor(spec.key)
            if (spec.key.repeatable && spec.label == null && (customIcon ?: arrowIcon) != null) {
                RepeatableKeyButton(
                    icon = customIcon ?: arrowIcon!!,
                    contentDescription = specialContentDescription(spec.key),
                    onPress = {
                        onKeyAction(spec)
                        if (bumpyArrows) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    },
                )
            } else {
                // Icon-only keys need a content description for screen readers;
                // keys drawn with text are announced through their label.
                KeyButton(
                    text = if (customIcon != null) null else spec.label ?: defaultSpecialLabel(spec.key),
                    icon = customIcon,
                    contentDescription = specialContentDescription(spec.key)
                        ?: iconOnlyContentDescription(customIcon, spec.label ?: defaultSpecialLabel(spec.key)),
                    onClick = { onKeyAction(spec) },
                )
            }
        }

        is KeySpec.Text -> KeyButton(
            text = if (customIcon != null) null else spec.label ?: spec.text,
            icon = customIcon,
            contentDescription = iconOnlyContentDescription(customIcon, spec.label ?: spec.text),
            onClick = { onKeyAction(spec) },
        )

        is KeySpec.Combo -> KeyButton(
            text = if (customIcon != null) null else spec.label ?: defaultComboLabel(spec),
            icon = customIcon,
            contentDescription = iconOnlyContentDescription(customIcon, spec.label ?: defaultComboLabel(spec)),
            onClick = { onKeyAction(spec) },
        )

        is KeySpec.FnGrid -> KeyButton(
            text = if (customIcon != null) null else spec.label ?: stringResource(R.string.button_key_fn),
            icon = customIcon,
            contentDescription = iconOnlyContentDescription(
                customIcon,
                spec.label ?: stringResource(R.string.button_key_fn),
            ),
            onClick = { onKeyAction(spec) },
        )

        is KeySpec.Tmux -> {
            val label = spec.label ?: stringResource(spec.action.stringResId)
            KeyButton(
                text = if (customIcon != null) null else label,
                icon = customIcon,
                contentDescription = iconOnlyContentDescription(customIcon, label),
                onClick = { onKeyAction(spec) },
            )
        }
    }
}

/** Content description for a key rendered as an icon: its label, or null when text is shown. */
private fun iconOnlyContentDescription(icon: ImageVector?, label: String): String? = if (icon != null) label else null

private fun ModifierState.levelFor(mod: ModifierKey): ModifierLevel = when (mod) {
    ModifierKey.CTRL -> ctrlState
    ModifierKey.ALT -> altState
    ModifierKey.SHIFT -> shiftState
}

private fun arrowIconFor(key: SpecialKey): ImageVector? = when (key) {
    SpecialKey.UP -> Icons.Default.KeyboardArrowUp
    SpecialKey.DOWN -> Icons.Default.KeyboardArrowDown
    SpecialKey.LEFT -> Icons.Default.KeyboardArrowLeft
    SpecialKey.RIGHT -> Icons.Default.KeyboardArrowRight
    else -> null
}

@Composable
private fun defaultModifierLabel(mod: ModifierKey): String = when (mod) {
    ModifierKey.CTRL -> stringResource(R.string.button_key_ctrl)
    ModifierKey.ALT -> stringResource(R.string.button_key_alt)
    ModifierKey.SHIFT -> stringResource(R.string.button_key_shift)
}

@Composable
private fun modifierContentDescription(mod: ModifierKey): String? = when (mod) {
    ModifierKey.CTRL -> stringResource(R.string.image_description_toggle_control_character)
    else -> null
}

@Composable
private fun defaultSpecialLabel(key: SpecialKey): String = when (key) {
    SpecialKey.ESC -> stringResource(R.string.button_key_esc)
    SpecialKey.TAB -> "⇥"
    SpecialKey.ENTER -> stringResource(R.string.button_key_enter)
    SpecialKey.UP -> "↑"
    SpecialKey.DOWN -> "↓"
    SpecialKey.LEFT -> "←"
    SpecialKey.RIGHT -> "→"
    SpecialKey.HOME -> stringResource(R.string.button_key_home)
    SpecialKey.END -> stringResource(R.string.button_key_end)
    SpecialKey.PGUP -> stringResource(R.string.button_key_pgup)
    SpecialKey.PGDN -> stringResource(R.string.button_key_pgdn)
    SpecialKey.INS -> stringResource(R.string.button_key_ins)
    SpecialKey.DEL -> stringResource(R.string.button_key_del)
    SpecialKey.F1 -> stringResource(R.string.button_key_f1)
    SpecialKey.F2 -> stringResource(R.string.button_key_f2)
    SpecialKey.F3 -> stringResource(R.string.button_key_f3)
    SpecialKey.F4 -> stringResource(R.string.button_key_f4)
    SpecialKey.F5 -> stringResource(R.string.button_key_f5)
    SpecialKey.F6 -> stringResource(R.string.button_key_f6)
    SpecialKey.F7 -> stringResource(R.string.button_key_f7)
    SpecialKey.F8 -> stringResource(R.string.button_key_f8)
    SpecialKey.F9 -> stringResource(R.string.button_key_f9)
    SpecialKey.F10 -> stringResource(R.string.button_key_f10)
    SpecialKey.F11 -> stringResource(R.string.button_key_f11)
    SpecialKey.F12 -> stringResource(R.string.button_key_f12)
}

@Composable
private fun specialContentDescription(key: SpecialKey): String? = when (key) {
    SpecialKey.ESC -> stringResource(R.string.image_description_send_escape_character)
    SpecialKey.TAB -> stringResource(R.string.image_description_send_tab_character)
    SpecialKey.UP -> stringResource(R.string.image_description_up)
    SpecialKey.DOWN -> stringResource(R.string.image_description_down)
    SpecialKey.LEFT -> stringResource(R.string.image_description_left)
    SpecialKey.RIGHT -> stringResource(R.string.image_description_right)
    else -> null
}

/** Compact default label for a combo key, e.g. Ctrl+C -> "⌃C". */
private fun defaultComboLabel(spec: KeySpec.Combo): String = buildString {
    if (spec.ctrl) append("⌃")
    if (spec.alt) append("⌥")
    if (spec.shift) append("⇧")
    append(spec.ch?.uppercaseChar()?.toString() ?: spec.special?.name.orEmpty())
}

@Composable
private fun PinnedBarButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val keySize = LocalKeyboardKeySize.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(keySize.keyWidthDp.dp)
            .fillMaxHeight(),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface.copy(alpha = UI_OPACITY),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.height(keySize.contentDp.dp),
            )
        }
    }
}

/**
 * A button for single-press keys.
 * Styled to match the old keyboard layout: rectangular 45dp × 30dp with border.
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
    val keySize = LocalKeyboardKeySize.current
    val surfaceModifier = modifier
        .size(width = keySize.keyWidthDp.dp, height = keySize.keyHeightDp.dp)
        .let {
            if (contentDescription != null && text != null) {
                it.semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                }
            } else {
                it
            }
        }

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
                    modifier = Modifier.height(keySize.contentDp.dp),
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
 * A button for repeatable keys (arrow keys).
 * Starts repeating after an initial delay when held down.
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

                    val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
                    repeatJob = coroutineScope.launch {
                        // Delay before first press to allow scroll gestures to steal touch.
                        delay(tapTimeout)
                        if (!isPressed) return@launch

                        sentPress = true
                        onPress()

                        delay(500 - tapTimeout)
                        while (isPressed) {
                            sentPress = true
                            onPress()
                            delay(50) // Repeat interval
                        }
                    }

                    val released = tryAwaitRelease()
                    isPressed = false

                    if (released && !sentPress) {
                        // Quick tap released before the repeat job fired — send once now.
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

@Preview(name = "Terminal Keyboard - Default", showBackground = true)
@Composable
private fun TerminalKeyboardPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            layout = DefaultKeyboardLayouts.default,
            modifierState = ModifierState(
                ctrlState = ModifierLevel.OFF,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onKeyAction = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onOpenSnippets = {},
            onLongPress = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}

@Preview(name = "Terminal Keyboard - Classic, Ctrl Locked", showBackground = true)
@Composable
private fun TerminalKeyboardClassicPreview() {
    MaterialTheme {
        TerminalKeyboardContent(
            layout = DefaultKeyboardLayouts.classic,
            modifierState = ModifierState(
                ctrlState = ModifierLevel.LOCKED,
                altState = ModifierLevel.OFF,
                shiftState = ModifierLevel.OFF,
            ),
            onKeyAction = {},
            onInteraction = {},
            onHideIme = {},
            onShowIme = {},
            onOpenTextInput = {},
            onOpenSnippets = {},
            onLongPress = {},
            onScrollInProgressChange = {},
            imeVisible = false,
            playAnimation = false,
            bumpyArrows = false,
        )
    }
}
