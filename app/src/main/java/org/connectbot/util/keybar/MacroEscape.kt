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
package org.connectbot.util.keybar

import java.io.ByteArrayOutputStream

/**
 * Parses a macro payload according to a small C-style escape grammar.
 *
 * Recognised escapes:
 *   \a \b \e \f \n \r \t \v \0 \\   -> the corresponding byte
 *   \xHH (case-insensitive)         -> the byte 0xHH
 *
 * Anything else is a syntax error (`validate` returns Invalid;
 * `expand` throws IllegalArgumentException — the editor validates
 * before saving, so `expand` should never see invalid input in
 * production).
 *
 * Plain ASCII passes through. Non-ASCII characters are encoded
 * as UTF-8. Real newlines (`\n` in the source string) emit byte
 * 0x0A, identical to the `\n` escape.
 */
object MacroEscape {

    sealed class ValidationResult {
        data object Ok : ValidationResult()
        data class Invalid(val position: Int, val reason: String) : ValidationResult()
    }

    fun expand(text: String): ByteArray {
        val out = ByteArrayOutputStream(text.length)
        parse(
            text,
            onByte = { b -> out.write(b.toInt() and 0xFF) },
            onText = { s -> out.write(s.toByteArray(Charsets.UTF_8)) },
            onError = { pos, reason ->
                throw IllegalArgumentException("Invalid escape at position $pos: $reason")
            },
        )
        return out.toByteArray()
    }

    /**
     * Validates the macro grammar without writing any bytes.
     *
     * Returns [ValidationResult.Invalid] at the position of the
     * first malformed escape, or [ValidationResult.Ok] if the
     * entire string parses. Later errors are not reported — the
     * editor's UX is to flag the first issue, let the user fix it,
     * and re-validate.
     */
    fun validate(text: String): ValidationResult {
        var result: ValidationResult = ValidationResult.Ok
        parse(
            text,
            onByte = { _ -> },
            onText = { _ -> },
            onError = { pos, reason ->
                result = ValidationResult.Invalid(pos, reason)
            },
        )
        return result
    }

    private inline fun parse(
        text: String,
        crossinline onByte: (Byte) -> Unit,
        crossinline onText: (String) -> Unit,
        crossinline onError: (Int, String) -> Unit,
    ) {
        var i = 0
        val buf = StringBuilder()
        while (i < text.length) {
            val c = text[i]
            if (c != '\\') {
                buf.append(c)
                i++
                continue
            }
            if (buf.isNotEmpty()) {
                onText(buf.toString())
                buf.setLength(0)
            }
            if (i + 1 >= text.length) {
                onError(i, "trailing backslash")
                return
            }
            when (val n = text[i + 1]) {
                'a' -> { onByte(0x07); i += 2 }
                'b' -> { onByte(0x08); i += 2 }
                'e' -> { onByte(0x1B); i += 2 }
                'f' -> { onByte(0x0C); i += 2 }
                'n' -> { onByte(0x0A); i += 2 }
                'r' -> { onByte(0x0D); i += 2 }
                't' -> { onByte(0x09); i += 2 }
                'v' -> { onByte(0x0B); i += 2 }
                '0' -> { onByte(0x00); i += 2 }
                '\\' -> { onByte(0x5C); i += 2 }
                'x' -> {
                    if (i + 3 >= text.length) {
                        onError(i, "\\x needs two hex digits")
                        return
                    }
                    val h1 = text[i + 2]
                    val h2 = text[i + 3]
                    if (!h1.isHexDigit() || !h2.isHexDigit()) {
                        onError(i, "\\x needs two hex digits")
                        return
                    }
                    val value = (digit(h1) shl 4) or digit(h2)
                    onByte(value.toByte())
                    i += 4
                }
                else -> {
                    onError(i, "unknown escape \\$n")
                    return
                }
            }
        }
        if (buf.isNotEmpty()) {
            onText(buf.toString())
        }
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun digit(c: Char): Int = when (c) {
        in '0'..'9' -> c.code - '0'.code
        in 'a'..'f' -> 10 + c.code - 'a'.code
        in 'A'..'F' -> 10 + c.code - 'A'.code
        else -> error("not a hex digit: $c")
    }
}
