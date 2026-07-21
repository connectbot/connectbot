/*
 * Mosh support for ConnectBot
 * Copyright 2012 Daniel Drown (transport layer)
 * ConnectBot integration by bqv (Tony O)
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

package org.mosh;

import java.io.FileDescriptor;

/**
 * JNI wrapper for mosh-client native process management.
 * This class provides methods to fork and manage the mosh-client subprocess.
 */
public class MoshClient {
    /**
     * File descriptor for the PTY connected to mosh-client.
     */
    private FileDescriptor clientFd;

    /**
     * Process ID of the forked mosh-client process.
     */
    private Long processId;

    /**
     * Fork and exec mosh-client with the given parameters.
     *
     * @param clientPath Absolute path to the downloaded mosh-client executable
     * @param ip The IP address of the mosh-server
     * @param port The UDP port of the mosh-server
     * @param key The MOSH_KEY for authentication
     * @param terminfo Path to the terminfo directory
     * @param locale The locale to use (e.g., "en_US.UTF-8")
     * @param row Initial PTY rows
     * @param col Initial PTY columns
     * @param xpixel Initial PTY width in pixels
     * @param ypixel Initial PTY height in pixels
     * @param processId A one-element array to receive the process ID
     * @return The file descriptor for the PTY, or null on failure
     */
    public static native FileDescriptor forkExec(String clientPath, String ip,
                                                  String port, String key,
                                                  String terminfo, String locale,
                                                  int row, int col,
                                                  int xpixel, int ypixel,
                                                  long[] processId);

    /**
     * Set the PTY window size.
     *
     * @param fd The file descriptor of the PTY
     * @param row Number of rows
     * @param col Number of columns
     * @param xpixel Width in pixels
     * @param ypixel Height in pixels
     */
    public static native void setPtyWindowSize(FileDescriptor fd, int row, int col,
                                                int xpixel, int ypixel);

    /**
     * Set an environment variable.
     *
     * @param name The name of the environment variable
     * @param value The value to set
     * @return 0 on success, -1 on failure
     */
    public static native int setenv(String name, String value);

    /**
     * Wait for the mosh-client process to exit.
     *
     * @param processId The process ID to wait for
     * @return The exit status of the process
     */
    public static native int waitFor(long processId);

    /**
     * Send a signal to the mosh-client process.
     *
     * @param processId The process ID
     * @param signal The signal number to send
     * @return 0 on success, -1 on failure
     */
    public static native int kill(long processId, int signal);

    static {
        System.loadLibrary("moshclient");
    }

    /**
     * Get the file descriptor for the mosh-client PTY.
     */
    public FileDescriptor getClientFd() {
        return clientFd;
    }

    /**
     * Set the file descriptor for the mosh-client PTY.
     */
    public void setClientFd(FileDescriptor fd) {
        this.clientFd = fd;
    }

    /**
     * Get the process ID of the mosh-client.
     */
    public Long getProcessId() {
        return processId;
    }

    /**
     * Set the process ID of the mosh-client.
     */
    public void setProcessId(Long pid) {
        this.processId = pid;
    }
}
