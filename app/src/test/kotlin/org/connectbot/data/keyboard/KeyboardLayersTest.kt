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

package org.connectbot.data.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayersTest {
    private val layout = KeyboardLayout(name = "Layers")
    private fun key(layer: Int) = KeyboardItem(layoutId = layout.id, layer = layer)
    private fun switch(layer: Int, target: String) = KeyboardItem(layoutId = layout.id, layer = layer, column = 1, kind = "layer", target = target)

    @Test fun cyclingSkipsHiddenEmptyAndSwitchOnlyLayersAndWraps() {
        val items = listOf(key(0), key(3), key(1).copy(visible = false), switch(2, "next"))
        assertEquals(listOf(0, 3), KeyboardLayers.populated(items))
        assertEquals(3, KeyboardLayers.destination(items, 0, "next"))
        assertEquals(0, KeyboardLayers.destination(items, 3, "next"))
        assertEquals(3, KeyboardLayers.destination(items, 0, "3"))
        assertNull(KeyboardLayers.destination(items, 0, "1"))
        assertNull(KeyboardLayers.destination(items, 0, "4"))
        assertNull(KeyboardLayers.destination(items, 0, "5"))
    }

    @Test fun sameLayerSelectionAndSingleLayerCyclingAreNoOps() {
        val items = listOf(key(2))
        assertEquals(2, KeyboardLayers.initial(items))
        assertEquals(2, KeyboardLayers.destination(items, 2, "2"))
        assertEquals(2, KeyboardLayers.destination(items, 2, "next"))
        assertNull(KeyboardLayers.destination(emptyList(), 0, "next"))
    }

    @Test fun directDestinationsNeedARouteBackIncludingIntermediateLayers() {
        val items = listOf(key(0), switch(0, "4"), key(4), switch(4, "2"), key(2), switch(2, "0"))
        KeyboardDefaults.validate(layout, items)
        assertTrue(runCatching { KeyboardDefaults.validate(layout, items.filterNot { it.layer == 2 && it.kind == "layer" }) }.isFailure)
    }

    @Test fun selfTargetDoesNotProvideAReturnRoute() {
        val items = listOf(key(0), switch(0, "3"), key(3), switch(3, "3"))
        assertTrue(runCatching { KeyboardDefaults.validate(layout, items) }.isFailure)
    }

    @Test fun cellsCanOverlapAcrossLayersButNotWithinOneLayer() {
        KeyboardDefaults.validate(layout, listOf(key(0), key(4)))
        assertTrue(runCatching { KeyboardDefaults.validate(layout, listOf(key(0), key(0))) }.isFailure)
        assertTrue(runCatching { KeyboardDefaults.validate(layout, listOf(key(5))) }.isFailure)
        assertTrue(runCatching { KeyboardDefaults.validate(layout, listOf(key(-1))) }.isFailure)
        assertTrue(runCatching { KeyboardDefaults.validate(layout, listOf(key(0), switch(0, "5"))) }.isFailure)
    }
}
