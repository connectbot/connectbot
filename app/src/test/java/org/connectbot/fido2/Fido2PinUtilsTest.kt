/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root, Jeffrey Sharkey
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

package org.connectbot.fido2

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class Fido2PinUtilsTest {

    @Test
    fun `normalizePin returns ASCII digits unchanged`() {
        assertThat(normalizePin("1234")).isEqualTo("1234".toCharArray())
    }

    @Test
    fun `normalizePin returns ASCII text unchanged`() {
        assertThat(normalizePin("hello")).isEqualTo("hello".toCharArray())
    }

    @Test
    fun `normalizePin normalizes NFD to NFC`() {
        // é as e + combining acute accent (NFD: U+0065 U+0301) -> NFC: U+00E9
        val nfdPin = "e\u0301"
        val result = normalizePin(nfdPin)
        assertThat(result).isEqualTo(charArrayOf('\u00E9'))
    }

    @Test
    fun `normalizePin keeps already-NFC input unchanged`() {
        // é as precomposed (NFC: U+00E9)
        val nfcPin = "\u00E9"
        val result = normalizePin(nfcPin)
        assertThat(result).isEqualTo(charArrayOf('\u00E9'))
    }

    @Test
    fun `normalizePin handles mixed ASCII and Unicode`() {
        // "café" with NFD é -> should normalize to NFC
        val nfdPin = "cafe\u0301"
        val result = normalizePin(nfdPin)
        assertThat(result).isEqualTo("caf\u00E9".toCharArray())
    }

    @Test
    fun `normalizePin handles multiple combining characters`() {
        // ö as o + combining diaeresis (NFD: U+006F U+0308) -> NFC: U+00F6
        val nfdPin = "o\u0308"
        val result = normalizePin(nfdPin)
        assertThat(result).isEqualTo(charArrayOf('\u00F6'))
    }

    @Test
    fun `normalizePin handles CJK characters`() {
        val cjkPin = "\u4F60\u597D" // 你好
        val result = normalizePin(cjkPin)
        assertThat(result).isEqualTo(cjkPin.toCharArray())
    }

    @Test
    fun `normalizePin handles emoji`() {
        val emojiPin = "\uD83D\uDD11" // 🔑
        val result = normalizePin(emojiPin)
        assertThat(result).isEqualTo(emojiPin.toCharArray())
    }

    @Test
    fun `normalizePin handles empty string`() {
        assertThat(normalizePin("")).isEqualTo(charArrayOf())
    }

    @Test
    fun `normalizePin normalizes Angstrom sign to A-ring`() {
        // Å (Angstrom U+212B) -> NFC: Å (U+00C5)
        val angstromPin = "\u212B"
        val result = normalizePin(angstromPin)
        assertThat(result).isEqualTo(charArrayOf('\u00C5'))
    }
}
