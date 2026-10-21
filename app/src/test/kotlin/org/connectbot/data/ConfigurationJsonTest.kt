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

package org.connectbot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConfigurationJsonTest {
    @Test fun acceptsCommentsSingleQuotesUnquotedKeysAndTrailingCommas() {
        val root = ConfigurationJson.objectFrom(
            """
            { /* comment */ text: 'https://host/a,/*b*/', values: [0x10, +2,], }
            """.trimIndent(),
        )
        assertEquals("https://host/a,/*b*/", root.getString("text"))
        assertEquals(16, root.getJSONArray("values").getInt(0))
        assertEquals(2, root.getJSONArray("values").getInt(1))
    }

    @Test fun json5StringEscapesPreserveControlCharacters() {
        val root = ConfigurationJson.objectFrom("""{text: '\v\x41', empty: '', missing: null}""")
        assertEquals("\u000bA", root.getString("text"))
        assertEquals("", root.getString("empty"))
        assertTrue(root.isNull("missing"))
    }

    @Test fun rejectsDuplicateKeysAndTrailingDocuments() {
        assertTrue(runCatching { ConfigurationJson.objectFrom("{a:1,a:2}") }.isFailure)
        assertTrue(runCatching { ConfigurationJson.objectFrom("{} {}") }.isFailure)
    }
}
