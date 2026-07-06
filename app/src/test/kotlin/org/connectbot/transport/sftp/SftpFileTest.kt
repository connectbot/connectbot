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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpFileTest {

    private fun directoryEntry(name: String, permissions: Int, size: Long? = null, mtime: Long? = null): SFTPv3DirectoryEntry {
        val entry = SFTPv3DirectoryEntry()
        entry.filename = name
        entry.attributes = SFTPv3FileAttributes().apply {
            this.permissions = permissions
            this.size = size
            this.mtime = mtime
        }
        return entry
    }

    @Test
    fun fromDirectoryEntry_mapsRegularFile() {
        val entry = directoryEntry("notes.txt", REGULAR_FILE_MODE, size = 2048L, mtime = 1700000000L)

        val file = SftpFile.fromDirectoryEntry("/home/user", entry)

        assertEquals("notes.txt", file.name)
        assertEquals("/home/user/notes.txt", file.path)
        assertFalse(file.isDirectory)
        assertFalse(file.isSymlink)
        assertEquals(2048L, file.size)
        assertEquals(1700000000000L, file.modifiedTimeMillis)
    }

    @Test
    fun fromDirectoryEntry_mapsDirectory() {
        val entry = directoryEntry("docs", DIRECTORY_MODE)

        val file = SftpFile.fromDirectoryEntry("/home/user", entry)

        assertTrue(file.isDirectory)
        assertFalse(file.isSymlink)
        assertEquals("/home/user/docs", file.path)
    }

    @Test
    fun fromDirectoryEntry_mapsSymlink() {
        val entry = directoryEntry("link", SYMLINK_MODE)

        val file = SftpFile.fromDirectoryEntry("/home/user", entry)

        assertTrue(file.isSymlink)
        assertFalse(file.isDirectory)
    }

    @Test
    fun fromAttributes_withNullAttributes_mapsUnknowns() {
        val file = SftpFile.fromAttributes("/tmp/mystery", null)

        assertEquals("mystery", file.name)
        assertFalse(file.isDirectory)
        assertNull(file.size)
        assertNull(file.modifiedTimeMillis)
    }

    @Test
    fun browserOrder_sortsDirectoriesFirstThenCaseInsensitiveNames() {
        val entries = listOf(
            sftpFile("zebra.txt", isDirectory = false),
            sftpFile("Apple", isDirectory = true),
            sftpFile("banana.txt", isDirectory = false),
            sftpFile("cherry", isDirectory = true),
            sftpFile("Aardvark.txt", isDirectory = false),
        )

        val sorted = entries.sortedWith(SftpFile.BROWSER_ORDER)

        assertEquals(
            listOf("Apple", "cherry", "Aardvark.txt", "banana.txt", "zebra.txt"),
            sorted.map { it.name },
        )
    }

    private fun sftpFile(name: String, isDirectory: Boolean): SftpFile = SftpFile(
        name = name,
        path = "/home/user/$name",
        isDirectory = isDirectory,
        isSymlink = false,
        size = null,
        modifiedTimeMillis = null,
    )

    companion object {
        // POSIX file mode bits: S_IFDIR | 0755, S_IFREG | 0644, S_IFLNK | 0777
        private const val DIRECTORY_MODE = 16877
        private const val REGULAR_FILE_MODE = 33188
        private const val SYMLINK_MODE = 41471
    }
}
