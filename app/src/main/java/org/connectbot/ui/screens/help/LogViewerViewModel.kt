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

package org.connectbot.ui.screens.help

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.connectbot.R
import org.connectbot.data.LogRepository
import org.connectbot.di.CoroutineDispatchers
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class LogViewerUiState(
    val logs: String = "",
)

enum class TombstoneSaveResult {
    SAVED,
    UNAVAILABLE,
    FAILED,
}

@HiltViewModel
class LogViewerViewModel @Inject constructor(
    private val logRepository: LogRepository,
    @ApplicationContext private val context: Context,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    suspend fun loadLogs() = withContext(dispatchers.io) {
        val logs = logRepository.getLogs()
        val exits = loadExitHistory()
        _uiState.value = LogViewerUiState(logs = listOf(logs, exits).filter { it.isNotBlank() }.joinToString("\n\n"))
    }

    private fun loadExitHistory(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        val header = context.getString(R.string.logs_exit_history)
        val details = try {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val exits = activityManager?.getHistoricalProcessExitReasons(context.packageName, 0, 10)
            if (exits == null) {
                context.getString(R.string.logs_exit_history_unavailable)
            } else if (exits.isEmpty()) {
                context.getString(R.string.logs_exit_history_empty)
            } else {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                exits.sortedByDescending { it.timestamp }.take(10).joinToString("\n\n") { exit ->
                    context.getString(
                        R.string.logs_exit_details,
                        dateFormat.format(Date(exit.timestamp)),
                        exit.processName.orEmpty(),
                        exit.pid,
                        exitReasonName(exit.reason),
                        exit.reason,
                        exit.status,
                        exit.description ?: context.getString(R.string.logs_exit_description_unavailable),
                        exit.pss,
                        exit.rss,
                    )
                }
            }
        } catch (_: SecurityException) {
            context.getString(R.string.logs_exit_history_unavailable)
        }
        return "$header\n$details"
    }

    private fun exitReasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "UNKNOWN"
    }

    /** Copies the newest retained native tombstone without decoding or caching its contents. */
    suspend fun saveTombstone(uri: Uri): TombstoneSaveResult = withContext(dispatchers.io) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@withContext TombstoneSaveResult.UNAVAILABLE

        try {
            val activityManager = context.getSystemService(ActivityManager::class.java)
                ?: return@withContext TombstoneSaveResult.UNAVAILABLE
            val crashes = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 0)
                .filter { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE }
                .sortedByDescending { it.timestamp }
            for (crash in crashes) {
                // Android may have already removed the trace from its circular buffer.
                val input = crash.traceInputStream ?: continue
                input.use {
                    val output = context.contentResolver.openOutputStream(uri, "wt")
                        ?: return@withContext TombstoneSaveResult.FAILED
                    output.use { input.copyTo(it) }
                }
                return@withContext TombstoneSaveResult.SAVED
            }
            TombstoneSaveResult.UNAVAILABLE
        } catch (_: IOException) {
            TombstoneSaveResult.FAILED
        } catch (_: SecurityException) {
            TombstoneSaveResult.FAILED
        }
    }
}
