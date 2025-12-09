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

import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON serialization handler for host configurations.
 *
 * This class uses reflection-based serialization to automatically adapt to
 * Room entity schema changes without requiring manual updates to the JSON format.
 *
 * Only true runtime state fields are excluded:
 * - Host: lastConnect (runtime state), hostKeyAlgo (negotiated at connection)
 *
 * All IDs are preserved in the export and remapped during import to handle conflicts.
 */
object HostConfigJson {
    const val CURRENT_VERSION = 1

    // Only exclude true runtime state - IDs are kept for relationship mapping
    private val HOST_EXCLUDED_FIELDS = setOf("lastConnect", "hostKeyAlgo")

    // No fields excluded for PortForward - all are needed including id and hostId
    private val PORT_FORWARD_EXCLUDED_FIELDS = emptySet<String>()

    // Default values for excluded fields during deserialization
    private val HOST_DEFAULTS = mapOf<String, Any?>(
        "lastConnect" to 0L,
        "hostKeyAlgo" to null
    )

    private val hostSerializer = EntityJsonSerializer.forEntity<Host>(
        excludedProperties = HOST_EXCLUDED_FIELDS,
        propertyDefaults = HOST_DEFAULTS
    )

    private val portForwardSerializer = EntityJsonSerializer.forEntity<PortForward>(
        excludedProperties = PORT_FORWARD_EXCLUDED_FIELDS,
        propertyDefaults = emptyMap()
    )

    /**
     * Result of parsing host configurations from JSON.
     *
     * @property hosts List of Host entities with original IDs from export
     * @property portForwards List of PortForward entities with original IDs from export
     */
    data class ParseResult(
        val hosts: List<Host>,
        val portForwards: List<PortForward>
    )

    /**
     * Parse host configurations from JSON string.
     *
     * @param jsonString The JSON string to parse
     * @return ParseResult containing hosts and port forwards with their original IDs
     * @throws org.json.JSONException if JSON is invalid
     * @throws IllegalArgumentException if schema is invalid
     */
    fun fromJson(jsonString: String): ParseResult {
        val json = JSONObject(jsonString)

        val version = json.optInt("version", 1)
        if (version > CURRENT_VERSION) {
            throw IllegalArgumentException("Unsupported version: $version (max supported: $CURRENT_VERSION)")
        }

        val hostsJson = json.getJSONArray("hosts")
        val hosts = mutableListOf<Host>()
        val portForwards = mutableListOf<PortForward>()

        for (i in 0 until hostsJson.length()) {
            val hostJson = hostsJson.getJSONObject(i)

            // Deserialize host with original ID
            val host = hostSerializer.fromJson(hostJson)
            hosts.add(host)

            // Deserialize port forwards if present
            val portForwardsJson = hostJson.optJSONArray("portForwards")
            if (portForwardsJson != null) {
                for (j in 0 until portForwardsJson.length()) {
                    val pf = portForwardSerializer.fromJson(portForwardsJson.getJSONObject(j))
                    portForwards.add(pf)
                }
            }
        }

        return ParseResult(hosts, portForwards)
    }

    /**
     * Convert hosts and port forwards to JSON string.
     *
     * @param hosts List of Host entities
     * @param portForwardsMap Map of host ID to list of port forwards
     * @param pretty If true, format JSON with indentation
     * @return JSON string
     */
    fun toJson(
        hosts: List<Host>,
        portForwardsMap: Map<Long, List<PortForward>>,
        pretty: Boolean = true
    ): String {
        val json = JSONObject()
        json.put("version", CURRENT_VERSION)

        val hostsArray = JSONArray()
        hosts.forEach { host ->
            val hostJson = hostSerializer.toJson(host)

            // Add port forwards if present
            val portForwards = portForwardsMap[host.id]
            if (!portForwards.isNullOrEmpty()) {
                val pfArray = JSONArray()
                portForwards.forEach { pf ->
                    pfArray.put(portForwardSerializer.toJson(pf))
                }
                hostJson.put("portForwards", pfArray)
            }

            hostsArray.put(hostJson)
        }
        json.put("hosts", hostsArray)

        return if (pretty) {
            json.toString(2)
        } else {
            json.toString()
        }
    }
}
