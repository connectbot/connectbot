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

package org.connectbot.data.entity

/** Per-host Telnet compatibility settings; names are persisted in Room and backups. */
enum class TelnetEchoMode { AUTO, ON, OFF }
enum class TelnetNegotiationMode { INTERACTIVE, PASSIVE }
enum class TelnetNonBinaryPolicy { EIGHT_BIT_COMPATIBILITY, STRICT_ASCII }
enum class TelnetNewlinePolicy { LF_TO_CRLF, PRESERVE_LF }
