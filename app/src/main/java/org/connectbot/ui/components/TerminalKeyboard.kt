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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.keyboard.KeyboardLayout
import org.connectbot.service.TerminalBridge
import org.connectbot.ui.theme.ConnectBotTheme

/** Configured terminal input surface. Its parent owns visibility and the auto-hide timer. */
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
    showImeToggleKey: Boolean = true,
    isComposeModeActive: Boolean = false,
    onToggleComposeMode: () -> Unit = {},
    onShortcutModifierChange: () -> Unit = {},
) {
    ConfigurableTerminalKeyboard(
        bridge = bridge, onInteraction = onInteraction, modifier = modifier,
        onHideIme = onHideIme, onShowIme = onShowIme, onOpenTextInput = onOpenTextInput,
        onScrollInProgressChange = onScrollInProgressChange, imeVisible = imeVisible,
        playAnimation = playAnimation, showImeToggleKey = showImeToggleKey,
        isComposeModeActive = isComposeModeActive, onToggleComposeMode = onToggleComposeMode,
        onShortcutModifierChange = onShortcutModifierChange,
    )
}

@Preview
@Composable
private fun TerminalKeyboardPreview() {
    val layout = KeyboardLayout(id = KeyboardDefaults.layoutId, name = "Default")
    ConnectBotTheme {
        KeyboardGrid(layout = layout, items = KeyboardDefaults.items(layout.id), macros = emptyList(), onPress = {})
    }
}
