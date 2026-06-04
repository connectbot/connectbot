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
package org.connectbot.util.keybar

/**
 * Default key bar configuration written on first launch by
 * KeyBarConfigRepository when no KEY_BAR_CONFIG pref exists.
 */
fun defaultKeyBarConfig(): List<KeyEntry> = listOf(
    // Visible by default — matches the legacy hardcoded bar with Enter
    // inserted between the arrows and Home.
    KeyEntry.Builtin(BuiltinKeyId.CTRL, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.ESC, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.TAB, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.UP, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.DOWN, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.LEFT, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.RIGHT, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.ENTER, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.HOME, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.END, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.PG_UP, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.PG_DN, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F1, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F2, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F3, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F4, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F5, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F6, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F7, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F8, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F9, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F10, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F11, visible = true),
    KeyEntry.Builtin(BuiltinKeyId.F12, visible = true),
    // Hidden tail — power users can enable from the customize screen.
    KeyEntry.Builtin(BuiltinKeyId.ALT, visible = false),
    KeyEntry.Builtin(BuiltinKeyId.SHIFT, visible = false),
    KeyEntry.Builtin(BuiltinKeyId.BACKSPACE, visible = false),
    KeyEntry.Builtin(BuiltinKeyId.DELETE, visible = false),
    KeyEntry.Builtin(BuiltinKeyId.INSERT, visible = false),
)
