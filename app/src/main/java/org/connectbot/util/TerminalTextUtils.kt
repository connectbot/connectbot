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

package org.connectbot.util

object TerminalTextUtils {
    /**
     * Normalizes line breaks in text that is sent to the terminal as if the
     * user had typed it, such as clipboard pastes and the floating text input.
     *
     * Pressing Enter in a terminal sends a carriage return, so CRLF and LF
     * both become CR, matching what xterm and other terminal emulators send
     * when pasting. Programs running in raw mode (menus, editors, REPLs)
     * ignore bare line feeds, which made multi-line pastes run together.
     *
     * @param text The raw text to send to the terminal.
     * @return The text with CRLF and LF line breaks replaced by CR.
     */
    fun normalizeLineBreaks(text: String): String = text.replace("\r\n", "\r").replace('\n', '\r')
}
