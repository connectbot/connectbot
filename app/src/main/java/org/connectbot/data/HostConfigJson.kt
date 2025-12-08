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
 * Data class representing a host configuration in JSON format for import/export.
 *
 * @property version Schema version for future compatibility (currently 1)
 * @property hosts List of host configurations
 */
data class HostConfigJson(
    val version: Int = CURRENT_VERSION,
    val hosts: List<HostJson>
) {
    companion object {
        const val CURRENT_VERSION = 1

        /**
         * Parse host configurations from JSON string.
         *
         * @param jsonString The JSON string to parse
         * @return HostConfigJson object
         * @throws org.json.JSONException if JSON is invalid
         * @throws IllegalArgumentException if schema is invalid
         */
        fun fromJson(jsonString: String): HostConfigJson {
            val json = JSONObject(jsonString)

            val version = json.optInt("version", 1)
            if (version > CURRENT_VERSION) {
                throw IllegalArgumentException("Unsupported version: $version (max supported: $CURRENT_VERSION)")
            }

            val hostsJson = json.getJSONArray("hosts")
            val hosts = mutableListOf<HostJson>()

            for (i in 0 until hostsJson.length()) {
                hosts.add(HostJson.fromJson(hostsJson.getJSONObject(i)))
            }

            return HostConfigJson(version, hosts)
        }

        /**
         * Create a HostConfigJson from a list of hosts and their port forwards.
         *
         * @param hosts List of Host entities
         * @param portForwardsMap Map of host ID to list of port forwards
         * @return HostConfigJson object
         */
        fun fromHosts(hosts: List<Host>, portForwardsMap: Map<Long, List<PortForward>>): HostConfigJson {
            val hostJsonList = hosts.map { host ->
                HostJson.fromHost(host, portForwardsMap[host.id] ?: emptyList())
            }
            return HostConfigJson(CURRENT_VERSION, hostJsonList)
        }
    }

    /**
     * Convert this configuration to JSON string.
     *
     * @param pretty If true, format JSON with indentation
     * @return JSON string
     */
    fun toJson(pretty: Boolean = true): String {
        val json = JSONObject()
        json.put("version", version)

        val hostsArray = JSONArray()
        hosts.forEach { host ->
            hostsArray.put(host.toJson())
        }
        json.put("hosts", hostsArray)

        return if (pretty) {
            json.toString(2)
        } else {
            json.toString()
        }
    }

    /**
     * Convert to list of Host entities.
     * Note: IDs will be set to 0 for new hosts.
     *
     * @return Pair of hosts list and map of host index to port forwards
     */
    fun toHosts(): Pair<List<Host>, Map<Int, List<PortForward>>> {
        val hostsList = mutableListOf<Host>()
        val portForwardsMap = mutableMapOf<Int, List<PortForward>>()

        hosts.forEachIndexed { index, hostJson ->
            val (host, portForwards) = hostJson.toHost()
            hostsList.add(host)
            if (portForwards.isNotEmpty()) {
                portForwardsMap[index] = portForwards
            }
        }

        return Pair(hostsList, portForwardsMap)
    }
}

/**
 * Data class representing a single host configuration in JSON format.
 */
data class HostJson(
    val nickname: String,
    val protocol: String,
    val username: String,
    val hostname: String,
    val port: Int,
    val color: String?,
    val useKeys: Boolean,
    val useAuthAgent: String?,
    val postLogin: String?,
    val pubkeyId: Long,
    val wantSession: Boolean,
    val compression: Boolean,
    val encoding: String,
    val stayConnected: Boolean,
    val quickDisconnect: Boolean,
    val fontSize: Int,
    val colorSchemeId: Long,
    val delKey: String,
    val scrollbackLines: Int,
    val useCtrlAltAsMetaKey: Boolean,
    val portForwards: List<PortForwardJson>
) {
    companion object {
        /**
         * Parse a host from JSON object.
         */
        fun fromJson(json: JSONObject): HostJson {
            val portForwardsJson = json.optJSONArray("portForwards")
            val portForwards = mutableListOf<PortForwardJson>()
            if (portForwardsJson != null) {
                for (i in 0 until portForwardsJson.length()) {
                    portForwards.add(PortForwardJson.fromJson(portForwardsJson.getJSONObject(i)))
                }
            }

            return HostJson(
                nickname = json.getString("nickname"),
                protocol = json.optString("protocol", "ssh"),
                username = json.optString("username", ""),
                hostname = json.optString("hostname", ""),
                port = json.optInt("port", 22),
                color = json.optString("color", "").ifEmpty { null },
                useKeys = json.optBoolean("useKeys", true),
                useAuthAgent = json.optString("useAuthAgent", "no").ifEmpty { null },
                postLogin = json.optString("postLogin", "").ifEmpty { null },
                pubkeyId = json.optLong("pubkeyId", -1L),
                wantSession = json.optBoolean("wantSession", true),
                compression = json.optBoolean("compression", false),
                encoding = json.optString("encoding", "UTF-8"),
                stayConnected = json.optBoolean("stayConnected", false),
                quickDisconnect = json.optBoolean("quickDisconnect", false),
                fontSize = json.optInt("fontSize", 10),
                colorSchemeId = json.optLong("colorSchemeId", 1L),
                delKey = json.optString("delKey", "DEL"),
                scrollbackLines = json.optInt("scrollbackLines", 140),
                useCtrlAltAsMetaKey = json.optBoolean("useCtrlAltAsMetaKey", false),
                portForwards = portForwards
            )
        }

        /**
         * Create a HostJson from a Host entity and its port forwards.
         */
        fun fromHost(host: Host, portForwards: List<PortForward>): HostJson {
            return HostJson(
                nickname = host.nickname,
                protocol = host.protocol,
                username = host.username,
                hostname = host.hostname,
                port = host.port,
                color = host.color,
                useKeys = host.useKeys,
                useAuthAgent = host.useAuthAgent,
                postLogin = host.postLogin,
                pubkeyId = host.pubkeyId,
                wantSession = host.wantSession,
                compression = host.compression,
                encoding = host.encoding,
                stayConnected = host.stayConnected,
                quickDisconnect = host.quickDisconnect,
                fontSize = host.fontSize,
                colorSchemeId = host.colorSchemeId,
                delKey = host.delKey,
                scrollbackLines = host.scrollbackLines,
                useCtrlAltAsMetaKey = host.useCtrlAltAsMetaKey,
                portForwards = portForwards.map { PortForwardJson.fromPortForward(it) }
            )
        }
    }

    /**
     * Convert to JSON object.
     */
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("nickname", nickname)
        json.put("protocol", protocol)
        json.put("username", username)
        json.put("hostname", hostname)
        json.put("port", port)
        color?.let { json.put("color", it) }
        json.put("useKeys", useKeys)
        useAuthAgent?.let { json.put("useAuthAgent", it) }
        postLogin?.let { json.put("postLogin", it) }
        json.put("pubkeyId", pubkeyId)
        json.put("wantSession", wantSession)
        json.put("compression", compression)
        json.put("encoding", encoding)
        json.put("stayConnected", stayConnected)
        json.put("quickDisconnect", quickDisconnect)
        json.put("fontSize", fontSize)
        json.put("colorSchemeId", colorSchemeId)
        json.put("delKey", delKey)
        json.put("scrollbackLines", scrollbackLines)
        json.put("useCtrlAltAsMetaKey", useCtrlAltAsMetaKey)

        if (portForwards.isNotEmpty()) {
            val pfArray = JSONArray()
            portForwards.forEach { pf ->
                pfArray.put(pf.toJson())
            }
            json.put("portForwards", pfArray)
        }

        return json
    }

    /**
     * Convert to Host entity and list of PortForward entities.
     * Note: Host ID will be 0 (for insert), port forward hostId will be 0 (to be set after host insert).
     */
    fun toHost(): Pair<Host, List<PortForward>> {
        val host = Host(
            id = 0,
            nickname = nickname,
            protocol = protocol,
            username = username,
            hostname = hostname,
            port = port,
            color = color,
            useKeys = useKeys,
            useAuthAgent = useAuthAgent,
            postLogin = postLogin,
            pubkeyId = pubkeyId,
            wantSession = wantSession,
            compression = compression,
            encoding = encoding,
            stayConnected = stayConnected,
            quickDisconnect = quickDisconnect,
            fontSize = fontSize,
            colorSchemeId = colorSchemeId,
            delKey = delKey,
            scrollbackLines = scrollbackLines,
            useCtrlAltAsMetaKey = useCtrlAltAsMetaKey
        )

        val pfs = portForwards.map { it.toPortForward(0) }

        return Pair(host, pfs)
    }
}

/**
 * Data class representing a port forward configuration in JSON format.
 */
data class PortForwardJson(
    val nickname: String,
    val type: String,
    val sourcePort: Int,
    val destAddr: String?,
    val destPort: Int
) {
    companion object {
        /**
         * Parse a port forward from JSON object.
         */
        fun fromJson(json: JSONObject): PortForwardJson {
            return PortForwardJson(
                nickname = json.getString("nickname"),
                type = json.getString("type"),
                sourcePort = json.getInt("sourcePort"),
                destAddr = json.optString("destAddr", "").ifEmpty { null },
                destPort = json.optInt("destPort", 0)
            )
        }

        /**
         * Create a PortForwardJson from a PortForward entity.
         */
        fun fromPortForward(pf: PortForward): PortForwardJson {
            return PortForwardJson(
                nickname = pf.nickname,
                type = pf.type,
                sourcePort = pf.sourcePort,
                destAddr = pf.destAddr,
                destPort = pf.destPort
            )
        }
    }

    /**
     * Convert to JSON object.
     */
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("nickname", nickname)
        json.put("type", type)
        json.put("sourcePort", sourcePort)
        destAddr?.let { json.put("destAddr", it) }
        json.put("destPort", destPort)
        return json
    }

    /**
     * Convert to PortForward entity.
     *
     * @param hostId The host ID to associate with this port forward
     */
    fun toPortForward(hostId: Long): PortForward {
        return PortForward(
            id = 0,
            hostId = hostId,
            nickname = nickname,
            type = type,
            sourcePort = sourcePort,
            destAddr = destAddr,
            destPort = destPort
        )
    }
}
