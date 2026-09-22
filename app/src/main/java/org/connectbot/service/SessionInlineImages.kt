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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.connectbot.terminal.InlineImages

/** Keeps image consent local to one terminal session, including concurrent requests. */
internal class SessionInlineImages(private val confirm: suspend () -> Boolean) {
    private val mutex = Mutex()
    private var allowed: Boolean? = null

    fun policy(setting: String): InlineImages = when (setting) {
        "off" -> InlineImages.Off

        "on" -> InlineImages.On()

        else -> InlineImages.Ask {
            mutex.withLock {
                allowed ?: confirm().also { allowed = it }
            }
        }
    }
}
