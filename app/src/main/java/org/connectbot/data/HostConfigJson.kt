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
 * Excluded fields:
 * - Host: id (auto-generated), lastConnect (runtime state), hostKeyAlgo (negotiated at connection)
 * - PortForward: id (auto-generated), hostId (relationship, set during import)
 */
object HostConfigJson {
    const val CURRENT_VERSION = 1

    // Fields to exclude from Host serialization
    private val HOST_EXCLUDED_FIELDS = setOf("id", "lastConnect", "hostKeyAlgo")

    // Fields to exclude from PortForward serialization
    private val PORT_FORWARD_EXCLUDED_FIELDS = setOf("id", "hostId")

    // Default values for excluded required fields during deserialization
    private val HOST_DEFAULTS = mapOf<String, Any?>(
        "id" to 0L,
        "lastConnect" to 0L,
        "hostKeyAlgo" to null
    )

    private val PORT_FORWARD_DEFAULTS = mapOf<String, Any?>(
        "id" to 0L,
        "hostId" to 0L
    )

    private val hostSerializer = EntityJsonSerializer.forEntity<Host>(
        excludedProperties = HOST_EXCLUDED_FIELDS,
        propertyDefaults = HOST_DEFAULTS
    )

    private val portForwardSerializer = EntityJsonSerializer.forEntity<PortForward>(
        excludedProperties = PORT_FORWARD_EXCLUDED_FIELDS,
        propertyDefaults = PORT_FORWARD_DEFAULTS
    )

    /**
     * Parse host configurations from JSON string.
     *
     * @param jsonString The JSON string to parse
     * @return Pair of hosts list and map of host index to port forwards
     * @throws org.json.JSONException if JSON is invalid
     * @throws IllegalArgumentException if schema is invalid
     */
    fun fromJson(jsonString: String): Pair<List<Host>, Map<Int, List<PortForward>>> {
        val json = JSONObject(jsonString)

        val version = json.optInt("version", 1)
        if (version > CURRENT_VERSION) {
            throw IllegalArgumentException("Unsupported version: $version (max supported: $CURRENT_VERSION)")
        }

        val hostsJson = json.getJSONArray("hosts")
        val hosts = mutableListOf<Host>()
        val portForwardsMap = mutableMapOf<Int, List<PortForward>>()

        for (i in 0 until hostsJson.length()) {
            val hostJson = hostsJson.getJSONObject(i)

            // Deserialize host
            val host = hostSerializer.fromJson(hostJson)
            hosts.add(host)

            // Deserialize port forwards if present
            val portForwardsJson = hostJson.optJSONArray("portForwards")
            if (portForwardsJson != null && portForwardsJson.length() > 0) {
                val portForwards = mutableListOf<PortForward>()
                for (j in 0 until portForwardsJson.length()) {
                    val pf = portForwardSerializer.fromJson(portForwardsJson.getJSONObject(j))
                    portForwards.add(pf)
                }
                portForwardsMap[i] = portForwards
            }
        }

        return Pair(hosts, portForwardsMap)
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
