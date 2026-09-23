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

package org.connectbot.service.automation

import com.google.re2j.PatternSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationRegexTest {
    @Test
    fun findsPromptWithinOutputAndReturnsConsumptionBoundary() {
        assertEquals(15, AutomationRegex("user@host:.*[$] ").matchEnd("Hi\nuser@host:$ next"))
    }

    @Test
    fun incompletePromptDoesNotMatch() {
        assertNull(AutomationRegex("Password: ").matchEnd("Password:"))
    }

    @Test(expected = PatternSyntaxException::class)
    fun invalidExpressionIsRejected() {
        AutomationRegex("[")
    }

    @Test(timeout = 2000)
    fun nestedQuantifiersDoNotBacktrackExponentially() {
        assertNull(AutomationRegex("(a+)+$").matchEnd("a".repeat(65_535) + "!"))
    }
}
