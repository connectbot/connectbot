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

package org.connectbot.transport

import org.connectbot.data.entity.Host
import org.connectbot.telnetlib.NewlinePolicy
import org.connectbot.telnetlib.NonBinaryPolicy
import org.connectbot.telnetlib.TelnetConfig
import org.connectbot.telnetlib.TelnetFailure
import org.connectbot.telnetlib.TelnetProfile
import java.io.IOException

internal fun telnetConfig(host: Host, emulation: String?, columns: Int, rows: Int): TelnetConfig = TelnetConfig(
    terminalTypes = listOf(emulation ?: "UNKNOWN"),
    columns = columns,
    rows = rows,
    profile = TelnetProfile.valueOf(host.telnetNegotiationMode.name),
    nonBinaryPolicy = NonBinaryPolicy.valueOf(host.telnetNonBinaryPolicy.name),
    newlinePolicy = NewlinePolicy.valueOf(host.telnetNewlinePolicy.name),
    consumeRecords = false,
    charset = null,
)

internal class TelnetOperationException(val failure: TelnetFailure) : IOException("${failure.code}: ${failure.message}")
