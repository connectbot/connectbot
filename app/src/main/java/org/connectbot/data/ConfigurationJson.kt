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

package org.connectbot.data

import de.marhali.json5.Json5
import de.marhali.json5.Json5Element
import de.marhali.json5.config.Json5Options
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Accept JSON5 at configuration boundaries while keeping stored and exported JSON canonical. */
object ConfigurationJson {
    private val json5 = Json5(Json5Options.builder().build())

    fun objectFrom(source: String): JSONObject = try {
        val element = requireNotNull(json5.parse(source)) { "Empty JSON document" }
        convert(element) as? JSONObject ?: throw JSONException("Expected an object")
    } catch (e: JSONException) {
        throw e
    } catch (e: Exception) {
        throw JSONException(e.message ?: "Invalid JSON document").apply { initCause(e) }
    }
    private fun convert(element: Json5Element): Any = when {
        element.isJson5Null -> JSONObject.NULL

        element.isJson5Object -> JSONObject().apply {
            element.asJson5Object.entrySet().forEach { (name, value) -> put(name, convert(value)) }
        }

        element.isJson5Array -> JSONArray().apply { element.asJson5Array.forEach { put(convert(it)) } }

        else -> element.asJson5Primitive.let { primitive ->
            when {
                primitive.isBoolean -> primitive.asBoolean
                primitive.isNumber -> JSONObject("{\"value\":${primitive.asNumber}}").get("value")
                else -> primitive.asString
            }
        }
    }
}
