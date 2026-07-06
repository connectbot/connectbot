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

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Receives transfer progress updates for uploads and downloads.
 */
fun interface SftpTransferProgressListener {
    /**
     * Called after each chunk is transferred.
     *
     * @param bytesTransferred total number of bytes transferred so far
     * @return true to continue the transfer, false to abort it
     */
    fun onBytesTransferred(bytesTransferred: Long): Boolean
}

/**
 * Thrown when a transfer is aborted because the progress listener asked to stop.
 */
class SftpTransferCancelledException : IOException("Transfer cancelled")

/**
 * A file-transfer session running over an established transport connection.
 * All methods perform blocking network I/O and must be called off the main
 * thread. Implementations are not required to be thread-safe; callers must
 * serialize access.
 */
interface SftpChannel : Closeable {
    /**
     * Resolves a path (e.g. "." or one containing "..") into an absolute,
     * canonical remote path.
     */
    @Throws(IOException::class)
    fun canonicalPath(path: String): String

    /**
     * Lists the contents of a remote directory, excluding the "." and ".."
     * entries, sorted for display.
     */
    @Throws(IOException::class)
    fun list(path: String): List<SftpFile>

    /**
     * Stats a remote path, following symlinks.
     *
     * @return the file information, or null when the path does not exist
     */
    @Throws(IOException::class)
    fun stat(path: String): SftpFile?

    /**
     * Downloads a remote file into [sink].
     *
     * @throws SftpTransferCancelledException when [progressListener] aborts the transfer
     */
    @Throws(IOException::class)
    fun download(remotePath: String, sink: OutputStream, progressListener: SftpTransferProgressListener)

    /**
     * Uploads the contents of [source] to a remote file, creating or
     * truncating it.
     *
     * @throws SftpTransferCancelledException when [progressListener] aborts the transfer
     */
    @Throws(IOException::class)
    fun upload(source: InputStream, remotePath: String, progressListener: SftpTransferProgressListener)

    /**
     * Creates a remote directory.
     */
    @Throws(IOException::class)
    fun mkdir(path: String)

    /**
     * Removes a remote file. Use [rmdir] for directories.
     */
    @Throws(IOException::class)
    fun delete(path: String)

    /**
     * Removes an empty remote directory.
     */
    @Throws(IOException::class)
    fun rmdir(path: String)

    /**
     * Renames or moves a remote file or directory.
     */
    @Throws(IOException::class)
    fun rename(fromPath: String, toPath: String)
}
