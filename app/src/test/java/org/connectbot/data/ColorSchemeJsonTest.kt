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
import org.json.JSONException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for ColorSchemeJson import/export functionality.
 */
@RunWith(RobolectricTestRunner::class)
class ColorSchemeJsonTest {

    @Test
    fun fromPalette_CreatesValidJson() {
        val palette = Colors.defaults
        val json = ColorSchemeJson.fromPalette("Test Scheme", "A test", palette)

        assertEquals("Test Scheme", json.name)
        assertEquals("A test", json.description)
        assertEquals(1, json.version)
        assertEquals(256, json.colors.size)
    }

    @Test
    fun toJson_ProducesValidJsonString() {
        val palette = IntArray(256) { 0xFF000000.toInt() or (it shl 16) }
        val json = ColorSchemeJson.fromPalette("Test", "Desc", palette)

        val jsonString = json.toJson()

        assertTrue(jsonString.contains("\"name\"") && jsonString.contains("\"Test\""))
        assertTrue(jsonString.contains("\"description\"") && jsonString.contains("\"Desc\""))
        assertTrue(jsonString.contains("\"version\"") && jsonString.contains("1"))
        assertTrue(jsonString.contains("\"colors\""))
    }

    @Test
    fun toJson_PrettyFormat_HasIndentation() {
        val palette = Colors.defaults
        val json = ColorSchemeJson.fromPalette("Test", "", palette)

        val pretty = json.toJson(pretty = true)
        val compact = json.toJson(pretty = false)

        assertTrue(pretty.length > compact.length)
        assertTrue(pretty.contains("\n"))
    }

    @Test
    fun fromJson_ParsesValidJson() {
        val jsonString = """
            {
                "name": "My Scheme",
                "description": "Custom colors",
                "version": 1,
                "colors": {
                    "0": "#000000",
                    "1": "#FF0000",
                    "255": "#FFFFFF"
                }
            }
        """.trimIndent()

        val json = ColorSchemeJson.fromJson(jsonString)

        assertEquals("My Scheme", json.name)
        assertEquals("Custom colors", json.description)
        assertEquals(1, json.version)
        assertEquals(3, json.colors.size)
        assertEquals("#000000", json.colors[0])
        assertEquals("#FF0000", json.colors[1])
        assertEquals("#FFFFFF", json.colors[255])
    }

    @Test
    fun fromJson_HandlesOptionalDescription() {
        val jsonString = """
            {
                "name": "Minimal",
                "version": 1,
                "colors": {
                    "0": "#000000"
                }
            }
        """.trimIndent()

        val json = ColorSchemeJson.fromJson(jsonString)

        assertEquals("Minimal", json.name)
        assertEquals("", json.description)
    }

    @Test
    fun fromJson_HandlesOptionalVersion() {
        val jsonString = """
            {
                "name": "No Version",
                "colors": {
                    "0": "#000000"
                }
            }
        """.trimIndent()

        val json = ColorSchemeJson.fromJson(jsonString)

        assertEquals(1, json.version) // Defaults to 1
    }

    @Test
    fun fromJson_ThreeDigitHex_Expands() {
        val jsonString = """
            {
                "name": "Short",
                "colors": {
                    "0": "#abc",
                    "1": "#f00"
                }
            }
        """.trimIndent()

        val json = ColorSchemeJson.fromJson(jsonString)

        assertEquals("#abc", json.colors[0])
        assertEquals("#f00", json.colors[1])
    }

    @Test
    fun fromJson_HexWithoutHash_Accepted() {
        val jsonString = """
            {
                "name": "No Hash",
                "colors": {
                    "0": "000000",
                    "1": "ffffff"
                }
            }
        """.trimIndent()

        val json = ColorSchemeJson.fromJson(jsonString)

        assertEquals("000000", json.colors[0])
        assertEquals("ffffff", json.colors[1])
    }

    @Test(expected = JSONException::class)
    fun fromJson_InvalidJson_ThrowsException() {
        ColorSchemeJson.fromJson("{ invalid json")
    }

    @Test(expected = JSONException::class)
    fun fromJson_MissingName_ThrowsException() {
        val jsonString = """
            {
                "description": "No name",
                "colors": {}
            }
        """.trimIndent()

        ColorSchemeJson.fromJson(jsonString)
    }

    @Test(expected = JSONException::class)
    fun fromJson_MissingColors_ThrowsException() {
        val jsonString = """
            {
                "name": "No colors"
            }
        """.trimIndent()

        ColorSchemeJson.fromJson(jsonString)
    }

    @Test
    fun fromJson_InvalidColorIndex_ThrowsException() {
        val jsonString = """
            {
                "name": "Bad Index",
                "colors": {
                    "abc": "#000000"
                }
            }
        """.trimIndent()

        try {
            ColorSchemeJson.fromJson(jsonString)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid color index"))
        }
    }

    @Test
    fun fromJson_IndexOutOfRange_ThrowsException() {
        val jsonString = """
            {
                "name": "Out of range",
                "colors": {
                    "256": "#000000"
                }
            }
        """.trimIndent()

        try {
            ColorSchemeJson.fromJson(jsonString)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("out of range"))
        }
    }

    @Test
    fun fromJson_NegativeIndex_ThrowsException() {
        val jsonString = """
            {
                "name": "Negative",
                "colors": {
                    "-1": "#000000"
                }
            }
        """.trimIndent()

        try {
            ColorSchemeJson.fromJson(jsonString)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("out of range"))
        }
    }

    @Test
    fun fromJson_InvalidHexColor_ThrowsException() {
        val jsonString = """
            {
                "name": "Bad color",
                "colors": {
                    "0": "gggggg"
                }
            }
        """.trimIndent()

        try {
            ColorSchemeJson.fromJson(jsonString)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid hex color"))
        }
    }

    @Test
    fun fromJson_WrongLengthHex_ThrowsException() {
        val jsonString = """
            {
                "name": "Wrong length",
                "colors": {
                    "0": "#1234"
                }
            }
        """.trimIndent()

        try {
            ColorSchemeJson.fromJson(jsonString)
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Invalid hex color"))
        }
    }

    @Test
    fun toPalette_ConvertsToIntArray() {
        val jsonString = """
            {
                "name": "Test",
                "colors": {
                    "0": "#000000",
                    "1": "#FF0000",
                    "2": "#00FF00",
                    "3": "#0000FF"
                }
            }
        """.trimIndent()

        val json = ColorSchemeJson.fromJson(jsonString)
        val palette = json.toPalette()

        assertEquals(256, palette.size)
        assertEquals(0xFF000000.toInt(), palette[0])
        assertEquals(0xFFFF0000.toInt(), palette[1])
        assertEquals(0xFF00FF00.toInt(), palette[2])
        assertEquals(0xFF0000FF.toInt(), palette[3])
    }

    @Test
    fun toPalette_ThreeDigitHex_ExpandsCorrectly() {
        val jsonString = """
            {
                "name": "Short Hex",
                "colors": {
                    "0": "#abc",
                    "1": "#f00"
                }
            }
        """.trimIndent()

        val json = ColorSchemeJson.fromJson(jsonString)
        val palette = json.toPalette()

        assertEquals(0xFFAABBCC.toInt(), palette[0])
        assertEquals(0xFFFF0000.toInt(), palette[1])
    }

    @Test
    fun roundTrip_PreservesColors() {
        val originalPalette = Colors.defaults
        val json = ColorSchemeJson.fromPalette("Round Trip", "Test", originalPalette)

        val jsonString = json.toJson()
        val parsed = ColorSchemeJson.fromJson(jsonString)
        val resultPalette = parsed.toPalette()

        assertArrayEquals(originalPalette, resultPalette)
    }

    @Test
    fun roundTrip_PreservesMetadata() {
        val palette = Colors.defaults
        val json = ColorSchemeJson.fromPalette("My Custom Scheme", "A beautiful scheme", palette)

        val jsonString = json.toJson()
        val parsed = ColorSchemeJson.fromJson(jsonString)

        assertEquals("My Custom Scheme", parsed.name)
        assertEquals("A beautiful scheme", parsed.description)
        assertEquals(1, parsed.version)
    }

    @Test
    fun roundTrip_SolarizedDark() {
        // Test with Solarized Dark colors
        val palette = IntArray(256)
        palette[0] = 0xFF073642.toInt() // base02
        palette[1] = 0xFFDC322F.toInt() // red
        palette[8] = 0xFF002B36.toInt() // base03
        palette[12] = 0xFF839496.toInt() // base0

        val json = ColorSchemeJson.fromPalette("Solarized Dark", "Precision colors", palette)
        val jsonString = json.toJson()
        val parsed = ColorSchemeJson.fromJson(jsonString)
        val result = parsed.toPalette()

        assertEquals(palette[0], result[0])
        assertEquals(palette[1], result[1])
        assertEquals(palette[8], result[8])
        assertEquals(palette[12], result[12])
    }

    @Test
    fun export_AllColors_CreatesCompleteMap() {
        val palette = IntArray(256) { index ->
            0xFF000000.toInt() or (index shl 16) or (index shl 8) or index
        }

        val json = ColorSchemeJson.fromPalette("Full", "", palette)

        assertEquals(256, json.colors.size)
        for (i in 0..255) {
            assertNotNull("Color $i should exist", json.colors[i])
        }
    }

    @Test
    fun import_PartialColors_OnlyAffectsSpecifiedIndices() {
        val jsonString = """
            {
                "name": "Partial",
                "colors": {
                    "0": "#FF0000",
                    "15": "#00FF00",
                    "255": "#0000FF"
                }
            }
        """.trimIndent()

        val json = ColorSchemeJson.fromJson(jsonString)
        val palette = json.toPalette()

        // Only specified colors should be set
        assertEquals(0xFFFF0000.toInt(), palette[0])
        assertEquals(0xFF00FF00.toInt(), palette[15])
        assertEquals(0xFF0000FF.toInt(), palette[255])

        // Unspecified colors should be 0 (default IntArray value)
        assertEquals(0, palette[1])
        assertEquals(0, palette[100])
        assertEquals(0, palette[254])
    }
}
