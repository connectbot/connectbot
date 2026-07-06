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

import com.trilead.ssh2.SFTPException
import com.trilead.ssh2.SFTPv3Client
import com.trilead.ssh2.sftp.ErrorCodes
import java.io.InputStream
import java.io.OutputStream

/**
 * [SftpChannel] implementation backed by the trilead-ssh2 [SFTPv3Client].
 */
class TrileadSftpChannel(private val client: SFTPv3Client) : SftpChannel {

    override fun canonicalPath(path: String): String = client.canonicalPath(path)

    override fun list(path: String): List<SftpFile> = client.ls(path)
        .filter { it.filename != "." && it.filename != ".." }
        .map { SftpFile.fromDirectoryEntry(path, it) }
        .sortedWith(SftpFile.BROWSER_ORDER)

    override fun stat(path: String): SftpFile? = try {
        SftpFile.fromAttributes(path, client.stat(path))
    } catch (e: SFTPException) {
        val notFound = e.serverErrorCode == ErrorCodes.SSH_FX_NO_SUCH_FILE ||
            e.serverErrorCode == ErrorCodes.SSH_FX_NO_SUCH_PATH
        if (notFound) null else throw e
    }

    override fun download(remotePath: String, sink: OutputStream, progressListener: SftpTransferProgressListener) {
        val handle = client.openFileRO(remotePath)
        try {
            val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
            var offset = 0L
            while (true) {
                val bytesRead = client.read(handle, offset, buffer, 0, buffer.size)
                if (bytesRead < 0) break
                sink.write(buffer, 0, bytesRead)
                offset += bytesRead
                if (!progressListener.onBytesTransferred(offset)) {
                    throw SftpTransferCancelledException()
                }
            }
            sink.flush()
        } finally {
            client.closeFile(handle)
        }
    }

    override fun upload(source: InputStream, remotePath: String, progressListener: SftpTransferProgressListener) {
        val handle = client.createFileTruncate(remotePath)
        try {
            val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
            var offset = 0L
            while (true) {
                val bytesRead = source.read(buffer)
                if (bytesRead < 0) break
                if (bytesRead == 0) continue
                client.write(handle, offset, buffer, 0, bytesRead)
                offset += bytesRead
                if (!progressListener.onBytesTransferred(offset)) {
                    throw SftpTransferCancelledException()
                }
            }
        } finally {
            client.closeFile(handle)
        }
    }

    override fun mkdir(path: String) {
        client.mkdir(path, DEFAULT_DIRECTORY_MODE)
    }

    override fun delete(path: String) {
        client.rm(path)
    }

    override fun rmdir(path: String) {
        client.rmdir(path)
    }

    override fun rename(fromPath: String, toPath: String) {
        client.mv(fromPath, toPath)
    }

    override fun close() {
        client.close()
    }

    companion object {
        /**
         * Maximum SFTP payload per read/write request supported by the
         * protocol implementation.
         */
        private const val TRANSFER_CHUNK_SIZE = 32768

        /** Octal 0755 permissions for newly created directories. */
        private const val DEFAULT_DIRECTORY_MODE = 493
    }
}
