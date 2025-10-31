/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

import org.connectbot.util.Colors
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ColorSchemePresets.
 */
class ColorSchemePresetsTest {

    @Test
    fun solarizedDark_HasCorrectMetadata() {
        val scheme = ColorSchemePresets.solarizedDark

        assertEquals("Solarized Dark", scheme.name)
        assertEquals("Precision colors for machines and people", scheme.description)
        assertEquals(12, scheme.defaultFg) // base0
        assertEquals(8, scheme.defaultBg)  // base03
    }

    @Test
    fun solarizedLight_HasCorrectMetadata() {
        val scheme = ColorSchemePresets.solarizedLight

        assertEquals("Solarized Light", scheme.name)
        assertEquals("Solarized with light background", scheme.description)
        assertEquals(10, scheme.defaultFg) // base01
        assertEquals(15, scheme.defaultBg) // base3
    }

    @Test
    fun solarizedDark_HasCorrectColors() {
        val scheme = ColorSchemePresets.solarizedDark

        // Check a few key colors
        assertEquals(0xff073642.toInt(), scheme.colors[0])  // black: base02
        assertEquals(0xffdc322f.toInt(), scheme.colors[1])  // red
        assertEquals(0xff002b36.toInt(), scheme.colors[8])  // bright black: base03
        assertEquals(0xff839496.toInt(), scheme.colors[12]) // bright blue: base0
    }

    @Test
    fun dracula_HasCorrectMetadata() {
        val scheme = ColorSchemePresets.dracula

        assertEquals("Dracula", scheme.name)
        assertEquals("A dark theme for the terminal", scheme.description)
        assertEquals(7, scheme.defaultFg)
        assertEquals(0, scheme.defaultBg)
    }

    @Test
    fun nord_HasCorrectMetadata() {
        val scheme = ColorSchemePresets.nord

        assertEquals("Nord", scheme.name)
        assertEquals("An arctic, north-bluish color palette", scheme.description)
        assertEquals(7, scheme.defaultFg)
        assertEquals(0, scheme.defaultBg)
    }

    @Test
    fun gruvboxDark_HasCorrectMetadata() {
        val scheme = ColorSchemePresets.gruvboxDark

        assertEquals("Gruvbox Dark", scheme.name)
        assertEquals("Retro groove color scheme", scheme.description)
        assertEquals(15, scheme.defaultFg)
        assertEquals(0, scheme.defaultBg)
    }

    @Test
    fun monokai_HasCorrectMetadata() {
        val scheme = ColorSchemePresets.monokai

        assertEquals("Monokai", scheme.name)
        assertEquals("Sublime Text inspired theme", scheme.description)
        assertEquals(7, scheme.defaultFg)
        assertEquals(0, scheme.defaultBg)
    }

    @Test
    fun tomorrowNight_HasCorrectMetadata() {
        val scheme = ColorSchemePresets.tomorrowNight

        assertEquals("Tomorrow Night", scheme.name)
        assertEquals("A clean and comfortable dark theme", scheme.description)
        assertEquals(7, scheme.defaultFg)
        assertEquals(0, scheme.defaultBg)
    }

    @Test
    fun builtInSchemes_Contains7Schemes() {
        assertEquals("Should have 7 built-in schemes", 7, ColorSchemePresets.builtInSchemes.size)
    }

    @Test
    fun builtInSchemes_ContainsAllSchemes() {
        val schemes = ColorSchemePresets.builtInSchemes

        assertTrue("Should contain Solarized Dark", schemes.contains(ColorSchemePresets.solarizedDark))
        assertTrue("Should contain Solarized Light", schemes.contains(ColorSchemePresets.solarizedLight))
        assertTrue("Should contain Dracula", schemes.contains(ColorSchemePresets.dracula))
        assertTrue("Should contain Nord", schemes.contains(ColorSchemePresets.nord))
        assertTrue("Should contain Gruvbox Dark", schemes.contains(ColorSchemePresets.gruvboxDark))
        assertTrue("Should contain Monokai", schemes.contains(ColorSchemePresets.monokai))
        assertTrue("Should contain Tomorrow Night", schemes.contains(ColorSchemePresets.tomorrowNight))
    }

    @Test
    fun getFullPalette_Returns256Colors() {
        val scheme = ColorSchemePresets.solarizedDark
        val palette = scheme.getFullPalette()

        assertEquals("Should have 256 colors", 256, palette.size)
    }

    @Test
    fun getFullPalette_UsesDefaultsForUnspecified() {
        val scheme = ColorSchemePresets.solarizedDark
        val palette = scheme.getFullPalette()

        // Solarized only defines first 16 colors
        // Colors 16-255 should come from Colors.defaults
        for (i in 16 until 256) {
            assertEquals(
                "Color $i should match Colors.defaults",
                Colors.defaults[i],
                palette[i]
            )
        }
    }

    @Test
    fun getFullPalette_OverridesSpecifiedColors() {
        val scheme = ColorSchemePresets.solarizedDark
        val palette = scheme.getFullPalette()

        // First 16 colors should be from Solarized, not defaults
        for (i in 0..15) {
            val expectedColor = scheme.colors[i]
            assertNotNull("Color $i should be defined in scheme", expectedColor)
            assertEquals("Color $i should match scheme definition", expectedColor, palette[i])
        }
    }

    @Test
    fun getFullPalette_DoesNotModifyOriginal() {
        val scheme = ColorSchemePresets.solarizedDark
        val palette1 = scheme.getFullPalette()
        val palette2 = scheme.getFullPalette()

        // Modify palette1
        palette1[0] = 0xFFFFFFFF.toInt()

        // palette2 should not be affected
        assertNotEquals("Palettes should be independent", palette1[0], palette2[0])
    }

    @Test
    fun presetScheme_ValidColorIndices() {
        // Ensure all color indices are valid (0-255)
        ColorSchemePresets.builtInSchemes.forEach { scheme ->
            scheme.colors.keys.forEach { index ->
                assertTrue(
                    "Color index $index in ${scheme.name} should be 0-255",
                    index in 0..255
                )
            }
        }
    }

    @Test
    fun presetScheme_ValidDefaultIndices() {
        // Ensure default FG/BG indices are valid (0-255)
        ColorSchemePresets.builtInSchemes.forEach { scheme ->
            assertTrue(
                "${scheme.name} defaultFg should be 0-255",
                scheme.defaultFg in 0..255
            )
            assertTrue(
                "${scheme.name} defaultBg should be 0-255",
                scheme.defaultBg in 0..255
            )
        }
    }

    @Test
    fun presetScheme_ColorsAreArgb() {
        // Ensure all colors have alpha channel set (0xFF)
        ColorSchemePresets.builtInSchemes.forEach { scheme ->
            scheme.colors.values.forEach { color ->
                val alpha = (color ushr 24) and 0xFF
                assertEquals(
                    "Color in ${scheme.name} should have full alpha (0xFF)",
                    0xFF,
                    alpha
                )
            }
        }
    }

    @Test
    fun solarizedDark_And_SolarizedLight_ShareSameColors() {
        // Solarized Dark and Light share the same color values
        // They differ only in which colors are used for FG/BG

        val dark = ColorSchemePresets.solarizedDark
        val light = ColorSchemePresets.solarizedLight

        // All color values should be the same
        dark.colors.keys.forEach { index ->
            assertEquals(
                "Color $index should be same in both variants",
                dark.colors[index],
                light.colors[index]
            )
        }
    }

    @Test
    fun solarizedDark_And_SolarizedLight_DifferentDefaults() {
        // Solarized Dark and Light should use different FG/BG indices

        val dark = ColorSchemePresets.solarizedDark
        val light = ColorSchemePresets.solarizedLight

        assertNotEquals("Should have different FG", dark.defaultFg, light.defaultFg)
        assertNotEquals("Should have different BG", dark.defaultBg, light.defaultBg)
    }
}
