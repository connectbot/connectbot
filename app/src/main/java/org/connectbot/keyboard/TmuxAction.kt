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

package org.connectbot.keyboard

import androidx.annotation.StringRes
import org.connectbot.R

enum class TmuxAction {
    SPLIT_H,
    SPLIT_V,
    ZOOM,
    KILL_PANE,
    BREAK_PANE,
    NEW_WINDOW,
    NEXT_WINDOW,
    PREV_WINDOW,
    NEXT_PANE,
    PREV_PANE,
    COPY_MODE,
    PALETTE,
    PREFIX,
    OPEN_DRAWER,
}

@get:StringRes
val TmuxAction.stringResId: Int
    get() = when (this) {
        TmuxAction.SPLIT_H -> R.string.tmux_action_split_h
        TmuxAction.SPLIT_V -> R.string.tmux_action_split_v
        TmuxAction.ZOOM -> R.string.tmux_action_zoom
        TmuxAction.KILL_PANE -> R.string.tmux_action_kill_pane
        TmuxAction.BREAK_PANE -> R.string.tmux_action_break_pane
        TmuxAction.NEW_WINDOW -> R.string.tmux_action_new_window
        TmuxAction.NEXT_WINDOW -> R.string.tmux_action_next_window
        TmuxAction.PREV_WINDOW -> R.string.tmux_action_prev_window
        TmuxAction.NEXT_PANE -> R.string.tmux_action_next_pane
        TmuxAction.PREV_PANE -> R.string.tmux_action_prev_pane
        TmuxAction.COPY_MODE -> R.string.tmux_action_copy_mode
        TmuxAction.PALETTE -> R.string.tmux_action_palette
        TmuxAction.PREFIX -> R.string.tmux_action_prefix
        TmuxAction.OPEN_DRAWER -> R.string.tmux_action_open_drawer
    }
