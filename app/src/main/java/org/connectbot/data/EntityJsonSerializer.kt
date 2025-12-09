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

import org.json.JSONObject
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Reflection-based JSON serializer for Room entity data classes.
 *
 * This serializer automatically discovers properties from entity classes using Kotlin reflection,
 * allowing the JSON export/import to automatically adapt to schema changes without manual updates.
 *
 * @param T The entity type to serialize
 * @param excludedProperties Set of property names to exclude from serialization (e.g., "id", "lastConnect")
 * @param propertyDefaults Optional map of property names to their default values for deserialization.
 *                         If not provided, defaults from the data class constructor are used.
 */
class EntityJsonSerializer<T : Any>(
    private val entityClass: KClass<T>,
    private val excludedProperties: Set<String> = emptySet(),
    private val propertyDefaults: Map<String, Any?> = emptyMap()
) {
    private val constructor = entityClass.primaryConstructor
        ?: throw IllegalArgumentException("Entity class must have a primary constructor")

    private val properties: List<KProperty1<T, *>> = entityClass.memberProperties
        .filter { it.name !in excludedProperties }
        .toList()

    private val constructorParams: Map<String, KParameter> = constructor.parameters
        .associateBy { it.name ?: "" }

    /**
     * Serialize an entity instance to a JSONObject.
     *
     * @param entity The entity to serialize
     * @return JSONObject containing all non-excluded properties
     */
    fun toJson(entity: T): JSONObject {
        val json = JSONObject()

        for (property in properties) {
            val value = property.get(entity)
            // Only include non-null values, or include if the property is not nullable
            if (value != null) {
                json.put(property.name, value)
            }
        }

        return json
    }

    /**
     * Deserialize a JSONObject to an entity instance.
     *
     * For properties not present in the JSON:
     * - Uses the value from propertyDefaults if provided
     * - Otherwise uses the default value from the data class constructor
     *
     * @param json The JSONObject to deserialize
     * @return A new entity instance
     */
    fun fromJson(json: JSONObject): T {
        val args = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            val paramName = param.name ?: continue

            // Skip excluded properties - they'll use constructor defaults
            if (paramName in excludedProperties) {
                if (param.isOptional) {
                    continue // Use constructor default
                } else {
                    // Required parameter that's excluded - use propertyDefaults
                    args[param] = propertyDefaults[paramName]
                }
                continue
            }

            when {
                json.has(paramName) -> {
                    args[param] = getJsonValue(json, paramName, param)
                }
                propertyDefaults.containsKey(paramName) -> {
                    args[param] = propertyDefaults[paramName]
                }
                param.isOptional -> {
                    // Use constructor default - don't add to args
                }
                else -> {
                    throw IllegalArgumentException("Required property '$paramName' not found in JSON")
                }
            }
        }

        return constructor.callBy(args)
    }

    /**
     * Get a value from JSON and convert it to the appropriate Kotlin type.
     */
    private fun getJsonValue(json: JSONObject, key: String, param: KParameter): Any? {
        if (json.isNull(key)) {
            return null
        }

        val classifier = param.type.classifier as? KClass<*>
        return when (classifier) {
            String::class -> {
                val value = json.optString(key, "")
                if (value.isEmpty() && param.type.isMarkedNullable) null else value
            }
            Int::class -> json.optInt(key, 0)
            Long::class -> json.optLong(key, 0L)
            Boolean::class -> json.optBoolean(key, false)
            Double::class -> json.optDouble(key, 0.0)
            Float::class -> json.optDouble(key, 0.0).toFloat()
            else -> json.opt(key)
        }
    }

    companion object {
        /**
         * Create a serializer for Host entities.
         * Excludes: id (auto-generated), lastConnect (runtime state), hostKeyAlgo (negotiated)
         */
        inline fun <reified T : Any> forEntity(
            excludedProperties: Set<String> = emptySet(),
            propertyDefaults: Map<String, Any?> = emptyMap()
        ): EntityJsonSerializer<T> {
            return EntityJsonSerializer(
                entityClass = T::class,
                excludedProperties = excludedProperties,
                propertyDefaults = propertyDefaults
            )
        }
    }
}
