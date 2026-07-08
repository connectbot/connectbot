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

package org.connectbot.keyboard

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Serializes a [KeyboardLayoutSpec] to/from JSON for storage in the
 * `keyboard_layouts.keys_json` column, using [org.json] to match the rest of
 * the app (no extra serialization dependency).
 */
object KeyboardLayoutJson {
    const val FORMAT_VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_ROWS = "rows"
    private const val KEY_TYPE = "type"
    private const val KEY_LABEL = "label"
    private const val KEY_ICON = "icon"

    private const val TYPE_SPECIAL = "special"
    private const val TYPE_MODIFIER = "modifier"
    private const val TYPE_TEXT = "text"
    private const val TYPE_COMBO = "combo"
    private const val TYPE_FN = "fn"

    fun encode(spec: KeyboardLayoutSpec): String {
        val rows = JSONArray()
        for (row in spec.rows) {
            val rowArray = JSONArray()
            for (key in row) {
                rowArray.put(encodeKey(key))
            }
            rows.put(rowArray)
        }
        return JSONObject()
            .put(KEY_VERSION, FORMAT_VERSION)
            .put(KEY_ROWS, rows)
            .toString()
    }

    /**
     * Decode a layout, or null if the JSON is malformed or has no usable rows.
     * Rows beyond [KeyboardLayoutSpec.MAX_ROWS] are dropped: the keys bar and
     * the editor only support that many.
     */
    fun decode(json: String): KeyboardLayoutSpec? {
        val obj = try {
            JSONObject(json)
        } catch (_: JSONException) {
            return null
        }
        val rowsArray = obj.optJSONArray(KEY_ROWS) ?: return null
        val rows = mutableListOf<List<KeySpec>>()
        for (r in 0 until rowsArray.length()) {
            if (rows.size == KeyboardLayoutSpec.MAX_ROWS) break
            val rowArray = rowsArray.optJSONArray(r) ?: continue
            val keys = mutableListOf<KeySpec>()
            for (k in 0 until rowArray.length()) {
                val keyObj = rowArray.optJSONObject(k) ?: continue
                decodeKey(keyObj)?.let { keys.add(it) }
            }
            rows.add(keys)
        }
        if (rows.isEmpty()) return null
        return KeyboardLayoutSpec(rows)
    }

    private fun encodeKey(key: KeySpec): JSONObject {
        val obj = JSONObject()
        when (key) {
            is KeySpec.Special -> obj.put(KEY_TYPE, TYPE_SPECIAL).put("key", key.key.name)

            is KeySpec.Modifier -> obj.put(KEY_TYPE, TYPE_MODIFIER).put("mod", key.mod.name)

            is KeySpec.Text -> obj.put(KEY_TYPE, TYPE_TEXT)
                .put("text", key.text)
                .put("sendEnter", key.sendEnter)

            is KeySpec.Combo -> {
                obj.put(KEY_TYPE, TYPE_COMBO)
                    .put("ctrl", key.ctrl)
                    .put("alt", key.alt)
                    .put("shift", key.shift)
                key.ch?.let { obj.put("ch", it.toString()) }
                key.special?.let { obj.put("special", it.name) }
            }

            is KeySpec.FnGrid -> obj.put(KEY_TYPE, TYPE_FN)
        }
        key.label?.let { obj.put(KEY_LABEL, it) }
        key.icon?.let { obj.put(KEY_ICON, it) }
        return obj
    }

    private fun decodeKey(obj: JSONObject): KeySpec? {
        val label = if (obj.isNull(KEY_LABEL)) null else obj.optString(KEY_LABEL)
        val icon = if (obj.isNull(KEY_ICON)) null else obj.optString(KEY_ICON)
        return when (obj.optString(KEY_TYPE)) {
            TYPE_SPECIAL -> parseSpecial(obj.optString("key"))
                ?.let { KeySpec.Special(it, label, icon) }

            TYPE_MODIFIER -> parseModifier(obj.optString("mod"))
                ?.let { KeySpec.Modifier(it, label, icon) }

            TYPE_TEXT -> KeySpec.Text(
                text = obj.optString("text"),
                sendEnter = obj.optBoolean("sendEnter", false),
                label = label,
                icon = icon,
            )

            TYPE_COMBO -> KeySpec.Combo(
                ctrl = obj.optBoolean("ctrl", false),
                alt = obj.optBoolean("alt", false),
                shift = obj.optBoolean("shift", false),
                ch = obj.optString("ch").takeIf { it.isNotEmpty() }?.first(),
                special = obj.optString("special").takeIf { it.isNotEmpty() }?.let { parseSpecial(it) },
                label = label,
                icon = icon,
            )

            TYPE_FN -> KeySpec.FnGrid(label, icon)

            // Unknown key types (e.g. from a newer version) are skipped.
            else -> null
        }
    }

    private fun parseSpecial(name: String): SpecialKey? = SpecialKey.entries.firstOrNull { it.name == name }

    private fun parseModifier(name: String): ModifierKey? = ModifierKey.entries.firstOrNull { it.name == name }
}
