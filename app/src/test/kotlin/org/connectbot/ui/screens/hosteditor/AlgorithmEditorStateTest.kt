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

package org.connectbot.ui.screens.hosteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlgorithmEditorStateTest {
    @Test
    fun changingStateReplacesThePreviousOperation() {
        assertEquals("-ssh-rsa,^ssh-ed25519", setAlgorithmRule("+ssh-ed25519,-ssh-rsa", "ssh-ed25519", '^'))
        assertEquals("-ssh-rsa", setAlgorithmRule("+ssh-ed25519,-ssh-rsa", "ssh-ed25519", null))
    }

    @Test
    fun draggingPreservesOtherGroupsAndDoesNotCrossGroups() {
        val expression = "^first,^second,-removed,+last"
        assertEquals("^second,^first,-removed,+last", moveAlgorithmRule(expression, "first", "second"))
        assertEquals(expression, moveAlgorithmRule(expression, "first", "last"))
        assertEquals("second,first", moveAlgorithmRule("first,second", "first", "second"))
    }

    @Test
    fun blankExactDraftCanSelectItsFirstAlgorithm() {
        assertEquals("ssh-ed25519", setAlgorithmRule("", "ssh-ed25519", '='))
        assertEquals("", setAlgorithmRule("ssh-ed25519", "ssh-ed25519", null))
    }

    @Test
    fun convertingToExactPreservesUnavailableEntriesInTheirIntendedPositions() {
        val rules = algorithmRules("^unavailable-first,+unavailable-last,-second")
        assertEquals(listOf("unavailable-first", "first", "unavailable-last"), intendedAlgorithmOrder(listOf("first", "second"), rules))
    }

    @Test
    fun displayOrderKeepsEachOverrideGroupInPreferenceOrder() {
        assertEquals(
            listOf("preferred", "inherited", "appended", "excluded"),
            algorithmDisplayOrder(listOf("inherited", "excluded", "appended"), algorithmRules("+appended,-excluded,^preferred")),
        )
    }

    @Test
    fun emptyAndWhitespaceExpressionsHaveNoRules() {
        assertEquals(emptyList<AlgorithmRule>(), algorithmRules(""))
        assertEquals(emptyList<AlgorithmRule>(), algorithmRules("  "))
        assertEquals(listOf(AlgorithmRule('^', "first"), AlgorithmRule('-', "second")), algorithmRules(" ^first , -second "))
    }

    @Test
    fun missingDragEndpointsLeaveTheExpressionUnchanged() {
        val expression = "^first,^second"
        assertEquals(expression, moveAlgorithmRule(expression, "absent", "first"))
        assertEquals(expression, moveAlgorithmRule(expression, "first", "absent"))
        assertEquals(expression, moveAlgorithmRule(expression, "first", "first"))
    }

    @Test
    fun exactOrderOverridesDefaultsWhileEmptyRulesInheritThem() {
        val defaults = listOf("first", "second")
        assertEquals(defaults, intendedAlgorithmOrder(defaults, emptyList()))
        assertEquals(listOf("unavailable", "second"), intendedAlgorithmOrder(defaults, algorithmRules("unavailable,second")))
        assertEquals(listOf("second", "first"), algorithmDisplayOrder(defaults, algorithmRules("second,first")))
    }
}
