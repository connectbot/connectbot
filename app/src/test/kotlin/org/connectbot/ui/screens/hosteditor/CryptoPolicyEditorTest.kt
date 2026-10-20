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

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import org.connectbot.data.entity.HostCryptoConfig
import org.connectbot.sshlib.AlgorithmCategory
import org.connectbot.sshlib.SshAlgorithm
import org.connectbot.sshlib.SshCryptoPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CryptoPolicyEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val config = mutableStateOf(HostCryptoConfig(preset = "CUSTOM"))

    private fun show(
        names: List<String> = listOf("curve25519-sha256"),
        resolve: suspend (HostCryptoConfig) -> SshCryptoPolicy? = { null },
        minimumRsa: Int? = null,
        minimumDh: Int? = null,
    ) {
        compose.setContent {
            MaterialTheme {
                CryptoPolicyEditor(config.value, mapOf(AlgorithmCategory.KEX to names.map { SshAlgorithm(it, true, true) }), mapOf(AlgorithmCategory.KEX to names), { config.value = it }, resolve, minimumRsa, minimumDh)
            }
        }
    }

    @Test
    fun customExposesSizesAndCategoriesWhilePresetsHideThem() {
        show()
        compose.onNodeWithText("Minimum RSA bits").assertIsDisplayed()
        compose.onNodeWithText("Cryptographic policy").performClick()
        compose.onNodeWithText("Require post-quantum key exchange").assertIsDisplayed()
        compose.onNodeWithText("Future").performClick()
        compose.onNodeWithText("Minimum RSA bits").assertDoesNotExist()
        assertEquals("FUTURE", config.value.preset)
    }

    @Test
    fun freeTextPreservesUnknownEntries() {
        config.value = config.value.copy(kex = "+future-algorithm@example.com")
        show()
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithText("Algorithm expression").performTextReplacement("^curve25519-sha256,+another@example.com")
        compose.onNodeWithText("Apply").performClick()
        assertEquals("^curve25519-sha256,+another@example.com", config.value.kex)
    }

    @Test
    fun invalidExpressionsCannotBeApplied() {
        config.value = config.value.copy(kex = "+curve25519-sha256")
        show()
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithText("Algorithm expression").performTextReplacement("+curve25519-sha256,-curve25519-sha256")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
    }

    @Test
    fun switchingModesRetainsTheDeltaDraft() {
        config.value = config.value.copy(kex = "+future@example.com")
        show()
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithText("Selection mode").performClick()
        compose.onNodeWithText("Use only these").performClick()
        compose.onNodeWithText("Algorithm expression").performTextReplacement("curve25519-sha256")
        compose.onNodeWithText("Selection mode").performClick()
        compose.onNodeWithText("Modify Default").performClick()
        compose.onNodeWithText("Algorithm expression").assertTextContains("+future@example.com")
        compose.onNodeWithText("Apply").performClick()
        assertEquals("+future@example.com", config.value.kex)
    }

    @Test
    @Config(qualifiers = "w480dp-h1000dp")
    fun stateChoicesStayUnderTheRowUntilSelectionSettles() {
        config.value = config.value.copy(kex = "-unavailable")
        show(listOf("first", "second"))
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithTag("algorithm-list").performScrollToNode(hasContentDescription("Change state for second"))
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Prefer first").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change state for second").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Prefer first").performClick()
        compose.mainClock.advanceTimeBy(800)
        fun secondIsBelowFirst(): Boolean = compose.onNodeWithTag("algorithm-row-second").fetchSemanticsNode().boundsInRoot.top >
            compose.onNodeWithTag("algorithm-row-first").fetchSemanticsNode().boundsInRoot.top
        assertTrue(secondIsBelowFirst())
        // A new choice restarts the idle delay, even if it returns to the previous state.
        compose.onNodeWithContentDescription("Append").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription("Prefer first").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(800)
        assertTrue(secondIsBelowFirst())
        val startingTop = compose.onNodeWithTag("algorithm-row-second").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(750)
        val movingTop = compose.onNodeWithTag("algorithm-row-second").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(1000)
        val settledTop = compose.onNodeWithTag("algorithm-row-second").fetchSemanticsNode().boundsInRoot.top
        assertTrue(movingTop < startingTop)
        assertTrue(movingTop > settledTop)
        assertFalse(secondIsBelowFirst())
        compose.onNodeWithContentDescription("Prefer first").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Apply").performClick()
        assertEquals("-unavailable,^second", config.value.kex)
    }

    @Test
    fun algorithmNameAndStatusBoxToggleTheSameChoices() {
        config.value = config.value.copy(kex = "+curve25519-sha256")
        show()
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithText("curve25519-sha256").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Prefer first").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change state for curve25519-sha256").performClick()
        compose.onNodeWithContentDescription("Prefer first").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change state for curve25519-sha256").performClick()
        compose.onNodeWithContentDescription("Prefer first").assertIsDisplayed()
        compose.onNodeWithText("curve25519-sha256").performClick()
        compose.onNodeWithContentDescription("Prefer first").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w480dp-h1000dp")
    fun dragOnlyEnablesTargetsWithinItsGroup() {
        config.value = config.value.copy(kex = "^first,^second,+last")
        show(listOf("first", "second", "inherited", "last"))
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithTag("algorithm-list").performScrollToNode(hasContentDescription("Change state for last"))
        compose.waitForIdle()
        val first = compose.onNodeWithTag("algorithm-drag-first")
        val last = compose.onNodeWithTag("algorithm-drag-last")
        val distance = last.fetchSemanticsNode().boundsInRoot.center.y - first.fetchSemanticsNode().boundsInRoot.center.y
        compose.mainClock.autoAdvance = false
        first.performTouchInput {
            down(center)
            moveBy(Offset(0f, 20f), delayMillis = 100)
        }
        compose.mainClock.advanceTimeBy(100)
        last.assertIsNotEnabled()
        compose.onNodeWithTag("algorithm-drag-second").assertIsEnabled()
        first.performTouchInput { moveBy(Offset(0f, distance - 20f), delayMillis = 200) }
        compose.mainClock.advanceTimeBy(300)
        last.assertIsNotEnabled()
        first.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000)
        last.assertIsEnabled()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Apply").performClick()
        val rules = algorithmRules(config.value.kex.orEmpty())
        assertEquals(setOf("first", "second"), rules.filter { it.operation == '^' }.map { it.name }.toSet())
        assertEquals(listOf(AlgorithmRule('+', "last")), rules.filter { it.operation == '+' })
    }

    @Test
    fun allPresetsExplainTheirRequirementsAndRetainCustomSettings() {
        config.value = config.value.copy(kex = "^curve25519-sha256", minimumRsaBits = 4096)
        show(resolve = { it.resolve() })
        listOf(
            "Default" to "Modern algorithms and key-size requirements maintained by the SSH library.",
            "FIPS algorithms" to "Restricts algorithm choices; does not establish FIPS validation.",
            "Legacy" to "Allows older algorithms and smaller RSA and DH keys for compatibility. Modern algorithms remain preferred.",
            "Future" to "Requires post-quantum hybrid key exchange, 256-bit encryption keys, and stronger RSA keys.",
            "Custom" to "Customize Default. Algorithm selections and key-size minimums are independent.",
        ).forEach { (name, summary) ->
            compose.onNodeWithText("Cryptographic policy").performClick()
            compose.onNodeWithText(name).performClick()
            compose.onNodeWithText(summary).assertIsDisplayed()
            assertEquals("^curve25519-sha256", config.value.kex)
            assertEquals(4096, config.value.minimumRsaBits)
        }
    }

    @Test
    fun keySizeFieldsRetainInvalidInputAndCanReturnToInheritedLimits() {
        show(minimumRsa = 2048, minimumDh = 2048)
        val rsa = compose.onNodeWithText("Minimum RSA bits")
        val dh = compose.onNodeWithText("Minimum DH bits")
        rsa.performTextReplacement("4096")
        assertEquals(4096, config.value.minimumRsaBits)
        rsa.performTextReplacement("100")
        compose.onNodeWithText("Enter at least 1024 bits. DH must not exceed 8192 bits.").assertIsDisplayed()
        rsa.performTextReplacement("99999999999999999999")
        assertEquals(-1, config.value.minimumRsaBits)
        rsa.performTextReplacement("")
        assertEquals(null, config.value.minimumRsaBits)
        dh.performTextReplacement("8193")
        compose.onNodeWithText("Enter at least 1024 bits. DH must not exceed 8192 bits.").assertIsDisplayed()
        dh.performTextReplacement("8192")
        assertEquals(8192, config.value.minimumDhBits)
        dh.performTextReplacement("")
        assertEquals(null, config.value.minimumDhBits)
    }

    @Test
    fun inheritedAlgorithmsCanBeViewedAndAppliedWithoutStoringACatalog() {
        show()
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithText("Show effective preference order").performClick()
        compose.onNodeWithText("curve25519-sha256").assertIsDisplayed()
        compose.onNodeWithText("Hide effective preference order").performClick()
        compose.onNodeWithText("curve25519-sha256").assertDoesNotExist()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(null, config.value.kex)
    }

    @Test
    fun exactSelectionCheckboxesAddAndRemoveAlgorithms() {
        config.value = config.value.copy(kex = "first")
        show(listOf("first", "second"))
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithContentDescription("first").performScrollTo().performClick()
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithContentDescription("second").performScrollTo().performClick()
        compose.onNodeWithText("Apply").assertIsEnabled().performClick()
        assertEquals("second", config.value.kex)
    }

    @Test
    fun switchingToExactSeedsDefaultsAndRetainsTheExactDraft() {
        show(listOf("first", "second"))
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithText("Selection mode").performClick()
        compose.onNodeWithText("Use only these").performClick()
        compose.onNodeWithText("Algorithm expression").assertTextContains("first,second")
        compose.onNodeWithText("Algorithm expression").performTextReplacement("second")
        compose.onNodeWithText("Selection mode").performClick()
        compose.onNodeWithText("Modify Default").performClick()
        compose.onNodeWithText("Algorithm expression").performTextReplacement("+unknown")
        compose.onNodeWithText("Selection mode").performClick()
        compose.onNodeWithText("Use only these").performClick()
        compose.onNodeWithText("Algorithm expression").assertTextContains("second")
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(null, config.value.kex)
    }

    @Test
    fun deltaSelectionCanExcludeAndThenInheritAnAlgorithm() {
        config.value = config.value.copy(kex = "^curve25519-sha256")
        show()
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithContentDescription("Change state for curve25519-sha256").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Exclude").performClick()
        compose.onNodeWithText("Algorithm expression").assertTextContains("-curve25519-sha256")
        compose.onNodeWithContentDescription("Follow Default").performScrollTo().performClick()
        compose.onNodeWithText("Algorithm expression").assertTextContains("")
        compose.onNodeWithText("Apply").performClick()
        assertEquals(null, config.value.kex)
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun accessibilityMovesReorderWithinTheGroupAndHandleItsEdges() {
        config.value = config.value.copy(kex = "first,second")
        show(listOf("first", "second"))
        compose.onNodeWithText("Key exchange algorithms").performClick()
        val first = compose.onNodeWithTag("algorithm-row-first")
        first.performScrollTo().performCustomAccessibilityActionWithLabel("Move up")
        compose.onNodeWithText("Algorithm expression").assertTextContains("first,second")
        first.performCustomAccessibilityActionWithLabel("Move down")
        compose.onNodeWithText("Algorithm expression").assertTextContains("second,first")
        first.performCustomAccessibilityActionWithLabel("Move down")
        compose.onNodeWithText("Algorithm expression").assertTextContains("second,first")
        first.performCustomAccessibilityActionWithLabel("Move up")
        compose.onNodeWithText("Apply").performClick()
        assertEquals("first,second", config.value.kex)
    }

    @Test
    fun eachCategoryCanBeEditedWithoutChangingTheOthers() {
        show()
        listOf(
            "Cipher algorithms" to AlgorithmCategory.CIPHER,
            "Message authentication algorithms" to AlgorithmCategory.MAC,
            "Host key algorithms" to AlgorithmCategory.HOST_KEY,
            "Public-key authentication algorithms" to AlgorithmCategory.PUBKEY,
        ).forEach { (title, category) ->
            compose.onNodeWithText(title).performClick()
            compose.onNodeWithText("Selection mode").performClick()
            compose.onNodeWithText("Modify Default").performClick()
            compose.onNodeWithText("Algorithm expression").performTextReplacement("+future@example.com")
            compose.onNodeWithText("Apply").performClick()
            assertEquals("+future@example.com", config.value.expression(category))
            assertEquals(null, config.value.kex)
        }
    }

    @Test
    fun resolverFailuresStillAllowEditingAndPreservingUnknownAlgorithms() {
        config.value = config.value.copy(kex = "+unknown")
        show(resolve = { throw IllegalArgumentException("Provider unavailable") })
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithText("Show effective preference order").performClick()
        compose.onNodeWithText("Hide effective preference order").assertIsDisplayed()
        compose.onNodeWithText("Apply").assertIsEnabled().performClick()
        assertEquals("+unknown", config.value.kex)
    }

    @Test
    fun modeRejectsExpressionsOfTheWrongKind() {
        config.value = config.value.copy(kex = "+first")
        show(listOf("first"))
        compose.onNodeWithText("Key exchange algorithms").performClick()
        compose.onNodeWithText("Algorithm expression").performTextReplacement("first")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Selection mode").performClick()
        compose.onNodeWithText("Use only these").performClick()
        compose.onNodeWithText("Algorithm expression").performTextReplacement("+first")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
    }
}
