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

package org.connectbot.logging

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class LocalTree : Timber.Tree() {
    companion object {
        private const val BUFFER_SIZE = 1000
        val logBuffer = ArrayDeque<String>(BUFFER_SIZE)
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?
    ) {
        if (priority == Log.VERBOSE || priority == Log.DEBUG) {
            return
        }

        val timestamp = dateFormat.format(Date())
        val finalMessage = buildString {
            append(timestamp)
            append(" ")
            if (tag != null) {
                append(tag)
                append(": ")
            }
            append(message)
            if (t != null) {
                append("\n")
                append(Log.getStackTraceString(t))
            }
        }

        synchronized(logBuffer) {
            while (logBuffer.size >= BUFFER_SIZE) {
                logBuffer.removeFirst()
            }
            logBuffer.addLast(finalMessage)
        }
    }
}
