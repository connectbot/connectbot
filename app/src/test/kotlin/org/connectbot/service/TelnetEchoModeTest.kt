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

package org.connectbot.service

import org.connectbot.data.entity.TelnetEchoMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TelnetEchoModeTest(
    private val mode: TelnetEchoMode,
    private val expectedRemoteEcho: String,
    private val expectedLocalEcho: String,
) {
    @Test
    fun echoOverridesRemainSeparateFromNegotiationAndEscapeFramesAreHidden() {
        val rendered = StringBuilder()
        val input = TelnetInputDiscipline(mode, "UTF-8") { rendered.append(it.toString(Charsets.UTF_8)) }
        input.negotiatedEcho(false)
        input.echo("secret".toByteArray())
        assertEquals(expectedRemoteEcho, rendered.toString())
        rendered.clear()
        input.negotiatedEcho(true)
        input.echo("x\u001b[".toByteArray())
        input.echo("A\bY\r\n\t".toByteArray())
        assertEquals(expectedLocalEcho, rendered.toString())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "echo={0}")
        fun data(): List<Array<Any>> = listOf(
            arrayOf(TelnetEchoMode.AUTO, "", "x\b \bY\r\n\t"),
            arrayOf(TelnetEchoMode.ON, "secret", "x\b \bY\r\n\t"),
            arrayOf(TelnetEchoMode.OFF, "", ""),
        )
    }
}
