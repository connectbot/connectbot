package org.connectbot.util.keybar

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class MacroEscapeTest {

    // --- expand() ---

    @Test
    fun `empty input produces empty bytes`() {
        assertThat(MacroEscape.expand("")).isEmpty()
    }

    @Test
    fun `plain ASCII passes through`() {
        assertThat(MacroEscape.expand("sudo "))
            .isEqualTo("sudo ".toByteArray(Charsets.US_ASCII))
    }

    @Test
    fun `named escapes produce the right bytes`() {
        assertThat(MacroEscape.expand("\\a")).containsExactly(0x07)
        assertThat(MacroEscape.expand("\\b")).containsExactly(0x08)
        assertThat(MacroEscape.expand("\\e")).containsExactly(0x1B)
        assertThat(MacroEscape.expand("\\f")).containsExactly(0x0C)
        assertThat(MacroEscape.expand("\\n")).containsExactly(0x0A)
        assertThat(MacroEscape.expand("\\r")).containsExactly(0x0D)
        assertThat(MacroEscape.expand("\\t")).containsExactly(0x09)
        assertThat(MacroEscape.expand("\\v")).containsExactly(0x0B)
        assertThat(MacroEscape.expand("\\0")).containsExactly(0x00)
        assertThat(MacroEscape.expand("\\\\")).containsExactly(0x5C)
    }

    @Test
    fun `hex escapes produce the right byte including over 0x7F`() {
        assertThat(MacroEscape.expand("\\x00")).containsExactly(0x00)
        assertThat(MacroEscape.expand("\\x7f")).containsExactly(0x7F)
        // Regression guard: NOT String.toByteArray, which would mangle bytes >= 0x80
        assertThat(MacroEscape.expand("\\xff")).containsExactly(0xFF.toByte().toInt())
    }

    @Test
    fun `hex escapes are case-insensitive`() {
        assertThat(MacroEscape.expand("\\xAB")).containsExactly(0xAB.toByte().toInt())
        assertThat(MacroEscape.expand("\\xab")).containsExactly(0xAB.toByte().toInt())
    }

    @Test
    fun `interleaved escapes and text`() {
        // "a<TAB>b<LF>c"
        assertThat(MacroEscape.expand("a\\tb\\nc"))
            .containsExactly(0x61, 0x09, 0x62, 0x0A, 0x63)
    }

    @Test
    fun `real newlines produce LF bytes same as backslash-n`() {
        val withRealNewline = "a\nb"
        val withEscape = "a\\nb"
        assertThat(MacroEscape.expand(withRealNewline))
            .isEqualTo(MacroEscape.expand(withEscape))
    }

    @Test
    fun `non-ASCII characters encode as UTF-8`() {
        assertThat(MacroEscape.expand("é"))
            .isEqualTo("é".toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `double-backslash then x41 produces literal backslash x 4 1`() {
        // Parser greed regression: \\ consumes the backslash, then "x41" is plain text.
        assertThat(MacroEscape.expand("\\\\x41"))
            .containsExactly(0x5C, 0x78, 0x34, 0x31)
    }

    // --- validate() ---

    @Test
    fun `validate accepts plain text`() {
        assertThat(MacroEscape.validate("sudo apt update")).isInstanceOf(MacroEscape.ValidationResult.Ok::class.java)
    }

    @Test
    fun `validate accepts every named escape`() {
        for (esc in listOf("\\a", "\\b", "\\e", "\\f", "\\n", "\\r", "\\t", "\\v", "\\0", "\\\\")) {
            assertThat(MacroEscape.validate(esc))
                .describedAs(esc)
                .isInstanceOf(MacroEscape.ValidationResult.Ok::class.java)
        }
    }

    @Test
    fun `validate accepts hex escapes`() {
        assertThat(MacroEscape.validate("\\x00")).isInstanceOf(MacroEscape.ValidationResult.Ok::class.java)
        assertThat(MacroEscape.validate("\\xff")).isInstanceOf(MacroEscape.ValidationResult.Ok::class.java)
        assertThat(MacroEscape.validate("\\xAb")).isInstanceOf(MacroEscape.ValidationResult.Ok::class.java)
    }

    @Test
    fun `validate rejects unknown escapes`() {
        // "\z", "\1" (non-zero digit), "\X" (capital X), "\xZZ"
        for (bad in listOf("\\z", "\\1", "\\X", "\\xZZ")) {
            assertThat(MacroEscape.validate(bad))
                .describedAs(bad)
                .isInstanceOf(MacroEscape.ValidationResult.Invalid::class.java)
        }
    }

    @Test
    fun `validate rejects truncated escapes`() {
        for (bad in listOf("\\", "\\x", "\\xA")) {
            assertThat(MacroEscape.validate(bad))
                .describedAs(bad)
                .isInstanceOf(MacroEscape.ValidationResult.Invalid::class.java)
        }
    }
}
