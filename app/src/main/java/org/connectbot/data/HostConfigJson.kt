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
import androidx.room.RoomDatabase
import org.json.JSONObject

/**
 * Result of exporting host configurations.
 *
 * @param hostCount Number of hosts exported
 * @param profileCount Number of profiles exported
 */
data class ExportCounts(
    val hostCount: Int,
    val profileCount: Int
)

/**
 * Result of importing host configurations.
 *
 * @param hostsImported Number of hosts newly inserted
 * @param hostsSkipped Number of hosts skipped (already existed)
 * @param profilesImported Number of profiles newly inserted
 * @param profilesSkipped Number of profiles skipped (already existed)
 */
data class ImportCounts(
    val hostsImported: Int,
    val hostsSkipped: Int,
    val profilesImported: Int,
    val profilesSkipped: Int
)

/**
 * Configuration for host configuration export/import.
 *
 * This is a thin wrapper around SchemaBasedExporter that specifies
 * which tables to export for host configurations. All serialization
 * logic is handled generically by SchemaBasedExporter using the
 * Room database schema.
 *
 * Tables exported (in order for foreign key resolution):
 * 1. profiles - Terminal profile configurations
 * 2. hosts - Main host configurations (references profiles)
 * 3. port_forwards - Port forwarding rules (references hosts)
 */
object HostConfigJson {
    /**
     * Tables to export for host configuration, in order.
     * Parent tables must come before child tables for foreign key resolution.
     *
     * Note: Excluded fields (runtime state like last_connect, host_key_algo) are
     * configured in the generateExportSchema Gradle task and marked in the schema.
     */
    val EXPORT_TABLES = listOf("profiles", "hosts", "port_forwards")

    /**
     * Export host configurations to JSON.
     *
     * @param context Android context for loading schema
     * @param database The Room database instance
     * @param pretty Whether to format JSON with indentation
     * @return Pair of JSON string and export counts (hosts and profiles)
     */
    fun exportToJson(context: Context, database: RoomDatabase, pretty: Boolean = true): Pair<String, ExportCounts> {
        val schema = DatabaseSchema.load(context)
        val exporter = SchemaBasedExporter(database, schema)
        val json = exporter.exportToJson(EXPORT_TABLES, pretty)

        // Parse the JSON to count hosts and profiles
        val jsonObj = JSONObject(json)
        val hostCount = jsonObj.optJSONArray("hosts")?.length() ?: 0
        val profileCount = jsonObj.optJSONArray("profiles")?.length() ?: 0

        return Pair(json, ExportCounts(hostCount, profileCount))
    }

    /**
     * Import host configurations from JSON.
     *
     * @param context Android context for loading schema
     * @param database The Room database instance
     * @param jsonString JSON string containing host configurations
     * @return Import counts for hosts and profiles
     */
    fun importFromJson(context: Context, database: RoomDatabase, jsonString: String): ImportCounts {
        val schema = DatabaseSchema.load(context)
        val exporter = SchemaBasedExporter(database, schema)
        val results = exporter.importFromJson(jsonString, EXPORT_TABLES)

        val hostCounts = results["hosts"] ?: Pair(0, 0)
        val profileCounts = results["profiles"] ?: Pair(0, 0)

        return ImportCounts(
            hostsImported = hostCounts.first,
            hostsSkipped = hostCounts.second,
            profilesImported = profileCounts.first,
            profilesSkipped = profileCounts.second
        )
    }
}
