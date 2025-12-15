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

package org.connectbot.sftp

import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.SFTPv3DirectoryEntry
import com.trilead.ssh2.SFTPv3FileAttributes
import com.trilead.ssh2.SFTPv3FileHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Kotlin-friendly wrapper around SFTPv3Client providing suspend functions for all operations.
 * All I/O operations are run on Dispatchers.IO.
 */
class SftpOperations(private val client: SFTPv3Client) {

    companion object {
        private const val BUFFER_SIZE = 32768 // 32KB - max size for SFTP read/write
    }

    /**
     * List directory contents.
     *
     * @param path The directory path to list
     * @return List of directory entries
     */
    suspend fun listDirectory(path: String): List<SFTPv3DirectoryEntry> = withContext(Dispatchers.IO) {
        @Suppress("UNCHECKED_CAST")
        client.ls(path) as List<SFTPv3DirectoryEntry>
    }

    /**
     * Get file/directory attributes.
     *
     * @param path The path to get attributes for
     * @return The file attributes
     */
    suspend fun stat(path: String): SFTPv3FileAttributes = withContext(Dispatchers.IO) {
        client.stat(path)
    }

    /**
     * Get file/directory attributes without following symlinks.
     *
     * @param path The path to get attributes for
     * @return The file attributes
     */
    suspend fun lstat(path: String): SFTPv3FileAttributes = withContext(Dispatchers.IO) {
        client.lstat(path)
    }

    /**
     * Create a directory.
     *
     * @param path The path of the directory to create
     * @param permissions POSIX permissions (default 0755)
     */
    suspend fun mkdir(path: String, permissions: Int = 493) = withContext(Dispatchers.IO) { // 0755 octal = 493 decimal
        client.mkdir(path, permissions)
    }

    /**
     * Remove an empty directory.
     *
     * @param path The path of the directory to remove
     */
    suspend fun rmdir(path: String) = withContext(Dispatchers.IO) {
        client.rmdir(path)
    }

    /**
     * Remove a file.
     *
     * @param path The path of the file to remove
     */
    suspend fun rm(path: String) = withContext(Dispatchers.IO) {
        client.rm(path)
    }

    /**
     * Rename/move a file or directory.
     *
     * @param oldPath The current path
     * @param newPath The new path
     */
    suspend fun mv(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        client.mv(oldPath, newPath)
    }

    /**
     * Get the canonical (absolute) path.
     *
     * @param path The path to resolve
     * @return The canonical path
     */
    suspend fun canonicalPath(path: String): String = withContext(Dispatchers.IO) {
        client.canonicalPath(path)
    }

    /**
     * Read the target of a symbolic link.
     *
     * @param path The path of the symlink
     * @return The target path
     */
    suspend fun readLink(path: String): String = withContext(Dispatchers.IO) {
        client.readLink(path)
    }

    /**
     * Download a file from the remote server.
     *
     * @param remotePath The path of the file to download
     * @param outputStream The stream to write the file contents to
     * @param progressCallback Called with (bytesTransferred, totalBytes) during transfer
     * @throws IOException if the transfer fails
     */
    suspend fun downloadFile(
        remotePath: String,
        outputStream: OutputStream,
        progressCallback: (Long, Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val attrs = client.stat(remotePath)
        val totalSize = attrs.size ?: 0L

        val handle = client.openFileRO(remotePath)
        try {
            val buffer = ByteArray(BUFFER_SIZE)
            var offset = 0L

            while (isActive) {
                val bytesRead = client.read(handle, offset, buffer, 0, buffer.size)
                if (bytesRead <= 0) break

                outputStream.write(buffer, 0, bytesRead)
                offset += bytesRead
                progressCallback(offset, totalSize)
            }

            outputStream.flush()
        } finally {
            try {
                client.closeFile(handle)
            } catch (e: IOException) {
                // Ignore close errors
            }
        }
    }

    /**
     * Upload a file to the remote server.
     *
     * @param inputStream The stream to read the file contents from
     * @param remotePath The path to write the file to
     * @param totalSize The total size of the file (for progress reporting)
     * @param progressCallback Called with (bytesTransferred, totalBytes) during transfer
     * @throws IOException if the transfer fails
     */
    suspend fun uploadFile(
        inputStream: InputStream,
        remotePath: String,
        totalSize: Long,
        progressCallback: (Long, Long) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val handle = client.createFileTruncate(remotePath)
        try {
            val buffer = ByteArray(BUFFER_SIZE)
            var offset = 0L

            while (isActive) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead <= 0) break

                client.write(handle, offset, buffer, 0, bytesRead)
                offset += bytesRead
                progressCallback(offset, totalSize)
            }
        } finally {
            try {
                client.closeFile(handle)
            } catch (e: IOException) {
                // Ignore close errors
            }
        }
    }

    /**
     * Close the SFTP client.
     */
    fun close() {
        client.close()
    }
}
