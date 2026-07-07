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

import com.trilead.ssh2.SFTPv3DirectoryEntry
import com.trilead.ssh2.SFTPv3FileAttributes

/**
 * A single entry in a remote directory listing, decoupled from the
 * underlying SSH library types so UI and tests don't depend on them.
 */
data class SftpFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val size: Long?,
    val modifiedTimeMillis: Long?,
) {
    companion object {
        /**
         * Directories first, then case-insensitive by name.
         */
        val BROWSER_ORDER: Comparator<SftpFile> = compareByDescending<SftpFile> { it.isDirectory }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }

        fun fromDirectoryEntry(parentPath: String, entry: SFTPv3DirectoryEntry): SftpFile = fromAttributes(SftpPaths.join(parentPath, entry.filename), entry.attributes)

        fun fromAttributes(path: String, attributes: SFTPv3FileAttributes?): SftpFile = SftpFile(
            name = SftpPaths.name(path),
            path = path,
            isDirectory = attributes?.isDirectory == true,
            isSymlink = attributes?.isSymlink == true,
            size = attributes?.size,
            modifiedTimeMillis = attributes?.mtime?.let { it * MILLIS_PER_SECOND },
        )

        private const val MILLIS_PER_SECOND = 1000L
    }
}
