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

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

/**
 * Serializes / deserializes [KeyEntry] lists for the
 * KEY_BAR_CONFIG SharedPreferences value.
 *
 * Forward-compat: unknown entry types and unknown builtin ids
 * are silently dropped on decode so a downgrade from a future
 * version doesn't crash.
 */
object KeyBarConfigJson {

    fun encode(entries: List<KeyEntry>): String {
        val arr = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            when (entry) {
                is KeyEntry.Builtin -> {
                    obj.put("t", "b")
                    obj.put("id", entry.id.name)
                    if (!entry.visible) obj.put("v", false)
                }
                is KeyEntry.Macro -> {
                    obj.put("t", "m")
                    obj.put("l", entry.label)
                    obj.put("x", entry.text)
                    obj.put("u", entry.id)
                    if (!entry.visible) obj.put("v", false)
                }
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decode(json: String): List<KeyEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val entry = decodeEntry(obj) ?: continue
                    add(entry)
                }
            }
        } catch (e: JSONException) {
            Timber.w(e, "Malformed key bar config; returning empty list")
            emptyList()
        }
    }

    private fun decodeEntry(obj: JSONObject): KeyEntry? = when (obj.optString("t")) {
        "b" -> {
            val idName = obj.optString("id")
            val id = enumValues<BuiltinKeyId>().firstOrNull { it.name == idName }
            id?.let { KeyEntry.Builtin(it, visible = obj.optBoolean("v", true)) }
        }
        "m" -> {
            val storedId = obj.optString("u").ifEmpty { java.util.UUID.randomUUID().toString() }
            KeyEntry.Macro(
                label = obj.optString("l"),
                text = obj.optString("x"),
                id = storedId,
                visible = obj.optBoolean("v", true),
            )
        }
        else -> null
    }
}
