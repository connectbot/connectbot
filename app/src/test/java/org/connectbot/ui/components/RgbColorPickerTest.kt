package org.connectbot.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for RGB color picker hex parsing functionality.
 */
class RgbColorPickerTest {

    /**
     * Test helper to access the private parseHexColor function via reflection.
     */
    private fun parseHexColor(hex: String): Int? {
        // Use reflection to access the private parseHexColor function
        val method = Class.forName("org.connectbot.ui.components.RgbColorPickerDialogKt")
            .getDeclaredMethod("parseHexColor", String::class.java)
        method.isAccessible = true
        return method.invoke(null, hex) as? Int
    }

    @Test
    fun parseHexColor_ThreeDigits_ExpandsCorrectly() {
        // "abc" should expand to "aabbcc"
        val result = parseHexColor("abc")
        assertEquals(0xaabbcc, result)
    }

    @Test
    fun parseHexColor_ThreeDigits_AllSame_ExpandsCorrectly() {
        // "fff" should expand to "ffffff" (white)
        val result = parseHexColor("fff")
        assertEquals(0xffffff, result)
    }

    @Test
    fun parseHexColor_ThreeDigits_Zeros_ExpandsCorrectly() {
        // "000" should expand to "000000" (black)
        val result = parseHexColor("000")
        assertEquals(0x000000, result)
    }

    @Test
    fun parseHexColor_ThreeDigits_Red_ExpandsCorrectly() {
        // "f00" should expand to "ff0000" (red)
        val result = parseHexColor("f00")
        assertEquals(0xff0000, result)
    }

    @Test
    fun parseHexColor_ThreeDigits_Green_ExpandsCorrectly() {
        // "0f0" should expand to "00ff00" (green)
        val result = parseHexColor("0f0")
        assertEquals(0x00ff00, result)
    }

    @Test
    fun parseHexColor_ThreeDigits_Blue_ExpandsCorrectly() {
        // "00f" should expand to "0000ff" (blue)
        val result = parseHexColor("00f")
        assertEquals(0x0000ff, result)
    }

    @Test
    fun parseHexColor_ThreeDigits_Uppercase_ExpandsCorrectly() {
        // "ABC" should expand to "AABBCC"
        val result = parseHexColor("ABC")
        assertEquals(0xaabbcc, result)
    }

    @Test
    fun parseHexColor_ThreeDigits_Mixed_ExpandsCorrectly() {
        // "a5c" should expand to "aa55cc"
        val result = parseHexColor("a5c")
        assertEquals(0xaa55cc, result)
    }

    @Test
    fun parseHexColor_SixDigits_ParsesCorrectly() {
        val result = parseHexColor("aabbcc")
        assertEquals(0xaabbcc, result)
    }

    @Test
    fun parseHexColor_SixDigits_White_ParsesCorrectly() {
        val result = parseHexColor("ffffff")
        assertEquals(0xffffff, result)
    }

    @Test
    fun parseHexColor_SixDigits_Black_ParsesCorrectly() {
        val result = parseHexColor("000000")
        assertEquals(0x000000, result)
    }

    @Test
    fun parseHexColor_SixDigits_Red_ParsesCorrectly() {
        val result = parseHexColor("ff0000")
        assertEquals(0xff0000, result)
    }

    @Test
    fun parseHexColor_SixDigits_Green_ParsesCorrectly() {
        val result = parseHexColor("00ff00")
        assertEquals(0x00ff00, result)
    }

    @Test
    fun parseHexColor_SixDigits_Blue_ParsesCorrectly() {
        val result = parseHexColor("0000ff")
        assertEquals(0x0000ff, result)
    }

    @Test
    fun parseHexColor_SixDigits_Uppercase_ParsesCorrectly() {
        val result = parseHexColor("AABBCC")
        assertEquals(0xaabbcc, result)
    }

    @Test
    fun parseHexColor_SixDigits_MixedCase_ParsesCorrectly() {
        val result = parseHexColor("AaBbCc")
        assertEquals(0xaabbcc, result)
    }

    @Test
    fun parseHexColor_SixDigits_SolarizedBase03_ParsesCorrectly() {
        // Solarized Dark background
        val result = parseHexColor("002b36")
        assertEquals(0x002b36, result)
    }

    @Test
    fun parseHexColor_SixDigits_SolarizedBase0_ParsesCorrectly() {
        // Solarized Dark foreground
        val result = parseHexColor("839496")
        assertEquals(0x839496, result)
    }

    @Test
    fun parseHexColor_EmptyString_ReturnsNull() {
        val result = parseHexColor("")
        assertNull(result)
    }

    @Test
    fun parseHexColor_OneDigit_ReturnsNull() {
        val result = parseHexColor("a")
        assertNull(result)
    }

    @Test
    fun parseHexColor_TwoDigits_ReturnsNull() {
        val result = parseHexColor("ab")
        assertNull(result)
    }

    @Test
    fun parseHexColor_FourDigits_ReturnsNull() {
        val result = parseHexColor("abcd")
        assertNull(result)
    }

    @Test
    fun parseHexColor_FiveDigits_ReturnsNull() {
        val result = parseHexColor("abcde")
        assertNull(result)
    }

    @Test
    fun parseHexColor_SevenDigits_ReturnsNull() {
        val result = parseHexColor("abcdefg")
        assertNull(result)
    }

    @Test
    fun parseHexColor_InvalidCharacters_ReturnsNull() {
        val result = parseHexColor("gggggg")
        assertNull(result)
    }

    @Test
    fun parseHexColor_WithHashPrefix_ShouldBeStrippedByCaller() {
        // The parseHexColor function doesn't handle #, it should be stripped before calling
        // This tests that # in the input returns null (invalid hex character)
        val result = parseHexColor("#aabbcc")
        assertNull(result)
    }

    @Test
    fun parseHexColor_SpecialCharacters_ReturnsNull() {
        val result = parseHexColor("ab cd!")
        assertNull(result)
    }

    /**
     * Test RGB component extraction from ARGB int.
     */
    @Test
    fun extractRgbComponents_FromColor() {
        val color = 0xFF839496.toInt() // Solarized base0

        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF

        assertEquals(0x83, red)
        assertEquals(0x94, green)
        assertEquals(0x96, blue)
    }

    @Test
    fun extractRgbComponents_FromWhite() {
        val color = 0xFFFFFFFF.toInt()

        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF

        assertEquals(0xFF, red)
        assertEquals(0xFF, green)
        assertEquals(0xFF, blue)
    }

    @Test
    fun extractRgbComponents_FromBlack() {
        val color = 0xFF000000.toInt()

        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF

        assertEquals(0x00, red)
        assertEquals(0x00, green)
        assertEquals(0x00, blue)
    }

    @Test
    fun extractRgbComponents_FromRed() {
        val color = 0xFFFF0000.toInt()

        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF

        assertEquals(0xFF, red)
        assertEquals(0x00, green)
        assertEquals(0x00, blue)
    }

    /**
     * Test RGB component reconstruction to ARGB int.
     */
    @Test
    fun reconstructColor_FromRgbComponents() {
        val red = 0x83
        val green = 0x94
        val blue = 0x96

        val color = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

        assertEquals(0xFF839496.toInt(), color)
    }

    @Test
    fun reconstructColor_FromMaxValues() {
        val red = 0xFF
        val green = 0xFF
        val blue = 0xFF

        val color = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

        assertEquals(0xFFFFFFFF.toInt(), color)
    }

    @Test
    fun reconstructColor_FromMinValues() {
        val red = 0x00
        val green = 0x00
        val blue = 0x00

        val color = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

        assertEquals(0xFF000000.toInt(), color)
    }

    /**
     * Test hex formatting.
     */
    @Test
    fun formatHex_SixDigits() {
        val color = 0xFF839496.toInt()
        val hex = String.format("%06X", color and 0xFFFFFF)
        assertEquals("839496", hex)
    }

    @Test
    fun formatHex_LeadingZeros() {
        val color = 0xFF002b36.toInt()
        val hex = String.format("%06X", color and 0xFFFFFF)
        assertEquals("002B36", hex)
    }

    @Test
    fun formatHex_AllZeros() {
        val color = 0xFF000000.toInt()
        val hex = String.format("%06X", color and 0xFFFFFF)
        assertEquals("000000", hex)
    }

    @Test
    fun formatHex_AllFs() {
        val color = 0xFFFFFFFF.toInt()
        val hex = String.format("%06X", color and 0xFFFFFF)
        assertEquals("FFFFFF", hex)
    }

    /**
     * Test round-trip conversions.
     */
    @Test
    fun roundTrip_ThreeDigitHex() {
        // Parse "abc" -> 0xaabbcc -> format back to "AABBCC"
        val parsed = parseHexColor("abc")!!
        val formatted = String.format("%06X", parsed and 0xFFFFFF)
        assertEquals("AABBCC", formatted)
    }

    @Test
    fun roundTrip_SixDigitHex() {
        // Parse "839496" -> 0x839496 -> format back to "839496"
        val parsed = parseHexColor("839496")!!
        val formatted = String.format("%06X", parsed and 0xFFFFFF)
        assertEquals("839496", formatted)
    }

    @Test
    fun roundTrip_RgbToHexToRgb() {
        // Start with RGB values
        val originalRed = 131
        val originalGreen = 148
        val originalBlue = 150

        // Convert to color int
        val color = (0xFF shl 24) or (originalRed shl 16) or (originalGreen shl 8) or originalBlue

        // Format as hex
        val hex = String.format("%06X", color and 0xFFFFFF)
        assertEquals("839496", hex)

        // Parse back
        val parsed = parseHexColor(hex.lowercase())!!

        // Extract RGB
        val red = (parsed shr 16) and 0xFF
        val green = (parsed shr 8) and 0xFF
        val blue = parsed and 0xFF

        assertEquals(originalRed, red)
        assertEquals(originalGreen, green)
        assertEquals(originalBlue, blue)
    }
}
