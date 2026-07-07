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

package org.connectbot.service

import android.content.SharedPreferences
import org.connectbot.util.PreferenceConstants

/**
 * A long-running command that finished on a host shell (termlib's
 * OSC 133 `onCommandFinished`), with the output tail captured at completion
 * time.
 */
data class HostCommandCompletion(val durationMs: Long, val snippet: String?)

/**
 * The user's completion-notification threshold in milliseconds, or 0 when the
 * feature is off. The preference stores seconds as a string; "0" disables it.
 */
fun completionThresholdMs(prefs: SharedPreferences): Long {
    val seconds = prefs.getString(
        PreferenceConstants.COMMAND_COMPLETION_NOTIFY,
        PreferenceConstants.DEFAULT_COMMAND_COMPLETION_NOTIFY,
    )?.toLongOrNull() ?: 0L
    return seconds.coerceAtLeast(0) * 1000
}

/**
 * Whether a finished command's duration reaches the notification threshold.
 * False when the feature is off ([thresholdMs] == 0) or the duration is
 * unknown (termlib reports -1 when it saw no command-start mark).
 */
fun meetsCompletionThreshold(durationMs: Long, thresholdMs: Long): Boolean =
    thresholdMs > 0 && durationMs >= thresholdMs
