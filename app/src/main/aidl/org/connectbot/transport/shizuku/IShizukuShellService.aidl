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

package org.connectbot.transport.shizuku;

/**
 * Implemented by a Shizuku user service running outside the app sandbox with
 * the identity of the Shizuku server (the adb shell user, or root).
 */
interface IShizukuShellService {
    /** Reserved by Shizuku: called when the user service is being removed. */
    void destroy() = 16777114;

    /**
     * Starts an interactive shell on a new pseudo-terminal. Bytes read from
     * {@code input} are typed into the terminal and terminal output is written
     * to {@code output}; both pipes are created by the app. The shell is
     * terminated when {@code input} reaches end of file, and {@code output} is
     * closed when the shell exits.
     *
     * @return a session identifier for the other calls
     */
    int openShell(in ParcelFileDescriptor input, in ParcelFileDescriptor output) = 1;

    /** Updates the terminal window size of a session. */
    void resize(int session, int columns, int rows, int width, int height) = 2;

    /** Terminates the shell of a session. */
    void closeShell(int session) = 3;
}
