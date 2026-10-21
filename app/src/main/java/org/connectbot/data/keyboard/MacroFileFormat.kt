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

import org.connectbot.data.ConfigurationJson
import org.connectbot.data.JsonImportReader
import org.json.JSONObject

/** Portable single-macro file. Import allocates a new identity and never overwrites another macro. */
object MacroFileFormat {
    fun encode(macro: KeyboardMacro): String {
        require(macro.name.isNotBlank()) { "Invalid macro name" }
        return JSONObject(MacroFormat.encode(MacroFormat.decode(macro.document))).put("name", macro.name).toString(2)
    }

    fun decode(source: String): KeyboardMacro {
        require(source.length <= JsonImportReader.MAX_BYTES) { "Macro file is too large" }
        val root = ConfigurationJson.objectFrom(source)
        require(root.keys().asSequence().toSet() == setOf("version", "name", "actions")) { "Unsupported macro file fields" }
        val name = root.get("name") as? String ?: error("A macro needs a name")
        require(name.isNotBlank()) { "Invalid macro name" }
        root.remove("name")
        return KeyboardMacro(name = name, document = MacroFormat.encode(MacroFormat.decode(root.toString())))
    }
}
