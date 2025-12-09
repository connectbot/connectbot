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

import android.content.Context
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.json.JSONArray
import org.json.JSONObject

/**
 * Schema-driven JSON serialization handler for host configurations.
 *
 * This class reads the Room database schema at runtime to dynamically
 * serialize and deserialize entities, automatically adapting to schema changes.
 *
 * The JSON structure mirrors the database schema:
 * - Table names are used as JSON keys (from schema)
 * - Field names are used as JSON property names (from schema's fieldPath)
 * - Foreign key relationships are used for ID remapping (from schema)
 *
 * Only runtime state fields are excluded (lastConnect, hostKeyAlgo).
 */
class HostConfigJson private constructor(
    private val schema: DatabaseSchema
) {
    // Fields to exclude from serialization (runtime state, not user configuration)
    private val excludedFields = setOf("lastConnect", "hostKeyAlgo")

    // Tables to export for host configuration
    private val exportTables = listOf("hosts", "port_forwards")

    private val hostSerializer = EntityJsonSerializer.forEntity<Host>(
        excludedProperties = excludedFields,
        propertyDefaults = mapOf("lastConnect" to 0L, "hostKeyAlgo" to null)
    )

    private val portForwardSerializer = EntityJsonSerializer.forEntity<PortForward>(
        excludedProperties = emptySet(),
        propertyDefaults = emptyMap()
    )

    /**
     * Parse host configurations from JSON string.
     *
     * @param jsonString The JSON string to parse
     * @return ParseResult containing hosts and port forwards with their original IDs
     * @throws org.json.JSONException if JSON is invalid
     * @throws IllegalArgumentException if schema version is incompatible
     */
    fun fromJson(jsonString: String): ParseResult {
        val json = JSONObject(jsonString)

        val version = json.optInt("version", 1)
        if (version > schema.version) {
            throw IllegalArgumentException(
                "Unsupported schema version: $version (max supported: ${schema.version})"
            )
        }

        val hosts = mutableListOf<Host>()
        val portForwards = mutableListOf<PortForward>()

        // Get table names from schema
        val hostsTableName = schema.getEntity("hosts")?.tableName ?: "hosts"
        val portForwardsTableName = schema.getEntity("port_forwards")?.tableName ?: "port_forwards"

        // Parse hosts
        val hostsArray = json.optJSONArray(hostsTableName)
        if (hostsArray != null) {
            for (i in 0 until hostsArray.length()) {
                hosts.add(hostSerializer.fromJson(hostsArray.getJSONObject(i)))
            }
        }

        // Parse port forwards
        val portForwardsArray = json.optJSONArray(portForwardsTableName)
        if (portForwardsArray != null) {
            for (i in 0 until portForwardsArray.length()) {
                portForwards.add(portForwardSerializer.fromJson(portForwardsArray.getJSONObject(i)))
            }
        }

        return ParseResult(hosts, portForwards)
    }

    /**
     * Convert hosts and port forwards to JSON string.
     *
     * @param hosts List of Host entities
     * @param portForwards List of PortForward entities
     * @param pretty If true, format JSON with indentation
     * @return JSON string with schema-driven structure
     */
    fun toJson(
        hosts: List<Host>,
        portForwards: List<PortForward>,
        pretty: Boolean = true
    ): String {
        val json = JSONObject()
        json.put("version", schema.version)

        // Get table names from schema
        val hostsTableName = schema.getEntity("hosts")?.tableName ?: "hosts"
        val portForwardsTableName = schema.getEntity("port_forwards")?.tableName ?: "port_forwards"

        // Serialize hosts
        val hostsArray = JSONArray()
        hosts.forEach { host ->
            hostsArray.put(hostSerializer.toJson(host))
        }
        json.put(hostsTableName, hostsArray)

        // Serialize port forwards
        val portForwardsArray = JSONArray()
        portForwards.forEach { pf ->
            portForwardsArray.put(portForwardSerializer.toJson(pf))
        }
        json.put(portForwardsTableName, portForwardsArray)

        return if (pretty) {
            json.toString(2)
        } else {
            json.toString()
        }
    }

    /**
     * Get foreign key relationships from schema for ID remapping.
     *
     * @return Map of (tableName, columnName) -> (referencedTable, referencedColumn)
     */
    fun getForeignKeyRelationships(): Map<Pair<String, String>, Pair<String, String>> {
        val relationships = mutableMapOf<Pair<String, String>, Pair<String, String>>()

        for (tableName in exportTables) {
            val entity = schema.getEntity(tableName) ?: continue
            for (fk in entity.foreignKeys) {
                // Map local column to referenced table.column
                fk.columns.zip(fk.referencedColumns).forEach { (localCol, refCol) ->
                    // Find the fieldPath for this column
                    val field = entity.fields.find { it.columnName == localCol }
                    if (field != null) {
                        relationships[tableName to field.fieldPath] = fk.table to refCol
                    }
                }
            }
        }

        // Also handle self-referencing foreign keys (like jumpHostId -> hosts.id)
        val hostsEntity = schema.getEntity("hosts")
        if (hostsEntity != null) {
            // Find fields that reference the hosts table (self-reference)
            for (field in hostsEntity.fields) {
                if (field.fieldPath.endsWith("HostId") && field.fieldPath != "id") {
                    relationships["hosts" to field.fieldPath] = "hosts" to "id"
                }
            }
        }

        return relationships
    }

    /**
     * Get unique constraint fields for a table from schema.
     *
     * @param tableName The table name
     * @return List of field paths that form unique constraints
     */
    fun getUniqueConstraintFields(tableName: String): List<String> {
        val entity = schema.getEntity(tableName) ?: return emptyList()
        // Convert column names to field paths
        return entity.uniqueIndices.flatMap { index ->
            index.columnNames.mapNotNull { colName ->
                entity.fields.find { it.columnName == colName }?.fieldPath
            }
        }
    }

    /**
     * Result of parsing host configurations from JSON.
     */
    data class ParseResult(
        val hosts: List<Host>,
        val portForwards: List<PortForward>
    )

    companion object {
        @Volatile
        private var instance: HostConfigJson? = null

        /**
         * Get the singleton instance, loading schema from assets.
         *
         * @param context Android context for accessing assets
         * @return HostConfigJson instance
         */
        fun getInstance(context: Context): HostConfigJson {
            return instance ?: synchronized(this) {
                instance ?: HostConfigJson(DatabaseSchema.load(context)).also {
                    instance = it
                }
            }
        }

        /**
         * Clear the singleton instance (for testing).
         */
        @androidx.annotation.VisibleForTesting
        fun clearInstance() {
            instance = null
        }
    }
}
