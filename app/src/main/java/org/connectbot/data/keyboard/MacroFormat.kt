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

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

sealed interface MacroAction {
    data class Text(val text: String) : MacroAction
    data class Key(val key: String? = null, val character: String? = null, val modifiers: List<String> = emptyList()) : MacroAction
    data class Bytes(val hex: String) : MacroAction
}

/** Explicit v1 wire contract. Neither Kotlin type names nor editor source are persisted. */
object MacroFormat {
    const val MAX_ACTIONS = 256
    const val MAX_DOCUMENT_LENGTH = 65536
    private val bareCharacter = Regex("[a-zA-Z0-9]")
    private val hexBytes = Regex("(?:[0-9a-fA-F]{2})+")

    private fun JSONObject.requireString(name: String): String = get(name) as? String ?: error("$name must be a string")

    fun encode(actions: List<MacroAction>): String {
        validate(actions)
        val result = JSONObject().put("version", 1).put(
            "actions",
            JSONArray().apply {
                actions.forEach { action ->
                    put(
                        when (action) {
                            is MacroAction.Text -> JSONObject().put("type", "text").put("text", action.text)

                            is MacroAction.Bytes -> JSONObject().put("type", "bytes").put("hex", action.hex.lowercase())

                            is MacroAction.Key -> JSONObject().put("type", "key").apply {
                                action.key?.let { put("key", it) }
                                action.character?.let { put("character", it) }
                                put("modifiers", JSONArray(KeyboardDefaults.modifiers.filter { it in action.modifiers }))
                            }
                        },
                    )
                }
            },
        ).toString()
        require(result.length <= MAX_DOCUMENT_LENGTH) { "Macro is too large" }
        return result
    }

    fun decode(document: String): List<MacroAction> {
        require(document.length <= MAX_DOCUMENT_LENGTH) { "Macro is too large" }
        val root = JSONObject(document)
        require(root.get("version") == 1) { "Unsupported macro version" }
        require(root.keys().asSequence().toSet() == setOf("version", "actions")) { "Unsupported macro fields" }
        val array = root.getJSONArray("actions")
        require(array.length() in 1..MAX_ACTIONS) { "A macro needs 1–256 steps" }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val allowed = when (item.requireString("type")) {
                "text" -> setOf("type", "text")
                "bytes" -> setOf("type", "hex")
                "key" -> setOf("type", "key", "character", "modifiers")
                else -> error("Unsupported macro action")
            }
            require(item.keys().asSequence().all { it in allowed }) { "Unsupported action fields" }
            when (item.requireString("type")) {
                "text" -> MacroAction.Text(item.requireString("text"))

                "bytes" -> MacroAction.Bytes(item.requireString("hex").lowercase())

                else -> MacroAction.Key(
                    key = if (item.has("key")) item.requireString("key") else null,
                    character = if (item.has("character")) item.requireString("character") else null,
                    modifiers = item.getJSONArray("modifiers").let { mods -> (0 until mods.length()).map { (mods.get(it) as? String ?: error("Modifiers must be strings")) } },
                )
            }
        }.also(::validate)
    }

    fun validate(actions: List<MacroAction>) {
        require(actions.size in 1..MAX_ACTIONS) { "A macro needs 1–256 steps" }
        actions.forEach { action ->
            when (action) {
                is MacroAction.Text -> require(action.text.isNotEmpty()) { "Text must not be empty" }

                is MacroAction.Bytes -> require(hexBytes.matches(action.hex)) { "Bytes must be pairs of hexadecimal digits" }

                is MacroAction.Key -> {
                    require((action.key == null) != (action.character == null)) { "Choose a key or a character" }
                    require(action.key == null || action.key in KeyboardDefaults.keys) { "Unknown key" }
                    action.character?.let {
                        require(it.codePointCount(0, it.length) == 1 && it.codePointAt(0) !in 0xD800..0xDFFF) { "Choose one Unicode character" }
                    }
                    require(action.modifiers.distinct().size == action.modifiers.size && action.modifiers.all { it in KeyboardDefaults.modifiers }) { "Unknown or duplicate modifier" }
                }
            }
        }
    }

    fun parse(source: String): List<MacroAction> {
        require(source.length <= MAX_DOCUMENT_LENGTH) { "Macro is too large" }
        return source.lineSequence().mapIndexedNotNull { index, original ->
            val line = original.trim()
            if (line.isEmpty()) return@mapIndexedNotNull null
            try {
                when {
                    line.startsWith("text ") -> MacroAction.Text(quoted(line.removePrefix("text ").trim()))

                    line.startsWith("bytes ") -> {
                        val bytes = line.removePrefix("bytes ").trim().split(Regex("\\s+"))
                        require(bytes.all { it.length == 2 }) { "Use two hexadecimal digits per byte" }
                        MacroAction.Bytes(bytes.joinToString("").lowercase())
                    }

                    line.startsWith("key ") -> {
                        var value = line.removePrefix("key ").trim()
                        val mods = mutableListOf<String>()
                        while (!value.startsWith('"') && '+' in value) {
                            mods.add(value.substringBefore('+'))
                            value = value.substringAfter('+')
                        }
                        when {
                            value.startsWith('"') -> MacroAction.Key(character = quoted(value), modifiers = mods)
                            bareCharacter.matches(value) -> MacroAction.Key(character = value, modifiers = mods)
                            else -> MacroAction.Key(key = value, modifiers = mods)
                        }
                    }

                    else -> error("Expected text, key, or bytes")
                }.also { validate(listOf(it)) }
            } catch (e: Exception) {
                throw IllegalArgumentException("Line ${index + 1}, column ${original.indexOfFirst { !it.isWhitespace() } + 1}: ${e.message}", e)
            }
        }.toList().also { encode(it) }
    }

    private fun quoted(value: String): String {
        require(value.startsWith('"')) { "Use a JSON-quoted string" }
        val tokener = JSONTokener(value)
        val result = tokener.nextValue()
        require(result is String && tokener.nextClean() == '\u0000') { "Expected one quoted string" }
        return result
    }

    fun format(actions: List<MacroAction>): String = actions.joinToString("\n") { action ->
        when (action) {
            is MacroAction.Text -> "text ${JSONObject.quote(action.text)}"

            is MacroAction.Bytes -> "bytes ${action.hex.lowercase().chunked(2).joinToString(" ")}"

            is MacroAction.Key -> "key " + KeyboardDefaults.modifiers.filter { it in action.modifiers }.joinToString("", postfix = "") { "$it+" } +
                (action.key ?: action.character!!.let { if (bareCharacter.matches(it)) it else JSONObject.quote(it) })
        }
    }
}
