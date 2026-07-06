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

package org.connectbot.transport.sftp

/**
 * Helpers for manipulating absolute POSIX-style remote paths. Paths handled
 * here are expected to come from the server via canonicalization, so they are
 * absolute and use '/' separators.
 */
object SftpPaths {
    private const val SEPARATOR = '/'
    private const val ROOT = "/"

    /**
     * Joins a directory path and a child name into a single path.
     */
    fun join(parent: String, name: String): String = if (parent.endsWith(SEPARATOR)) {
        "$parent$name"
    } else {
        "$parent$SEPARATOR$name"
    }

    /**
     * Returns the parent directory of an absolute path, or null when the path
     * is the root (or is not absolute and the parent cannot be determined).
     */
    fun parent(path: String): String? {
        if (!path.startsWith(SEPARATOR)) return null

        val trimmed = path.trimEnd(SEPARATOR)
        if (trimmed.isEmpty()) return null

        val parent = trimmed.substringBeforeLast(SEPARATOR)
        return parent.ifEmpty { ROOT }
    }

    /**
     * Returns the last component of a path, e.g. "file.txt" for "/tmp/file.txt".
     */
    fun name(path: String): String {
        val trimmed = path.trimEnd(SEPARATOR)
        if (trimmed.isEmpty()) return ROOT
        return trimmed.substringAfterLast(SEPARATOR)
    }
}
