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

package org.connectbot.transport

import java.io.FileDescriptor

/** Keeps resizes received during process creation until the PTY is available. */
internal class MoshPtyWindow(private val resize: (FileDescriptor, Size) -> Unit) {
    data class Size(val columns: Int = 80, val rows: Int = 24, val width: Int = 0, val height: Int = 0)

    private var size = Size()
    private var descriptor: FileDescriptor? = null

    @Synchronized
    fun snapshot(): Size = size

    @Synchronized
    fun update(columns: Int, rows: Int, width: Int, height: Int) {
        if (columns <= 0 || rows <= 0) return
        size = Size(columns, rows, width.coerceAtLeast(0), height.coerceAtLeast(0))
        descriptor?.let { resize(it, size) }
    }

    @Synchronized
    fun attach(fd: FileDescriptor) {
        descriptor = fd
        // Replay the latest size, which may differ from the fork's snapshot.
        resize(fd, size)
    }

    @Synchronized
    fun detach() {
        descriptor = null
    }
}
