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

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import androidx.room.RoomDatabase
import org.json.JSONArray
import org.json.JSONObject

/**
 * Schema-driven database exporter/importer.
 *
 * Uses the Room schema JSON to generically export and import database tables
 * without any hardcoded entity knowledge. All table names, field names, types,
 * relationships, and excluded fields are read from the schema.
 *
 * Fields marked as "excluded" in the schema are:
 * - Omitted from JSON export
 * - Given default values during import (for NOT NULL fields)
 *
 * @param database The Room database instance
 * @param schema The parsed database schema (filtered export schema with excluded field markers)
 */
class SchemaBasedExporter(
    private val database: RoomDatabase,
    private val schema: DatabaseSchema
) {

    /**
     * Export specified tables to JSON.
     *
     * @param tableNames List of table names to export
     * @param pretty Whether to format JSON with indentation
     * @return JSON string containing all table data
     */
    fun exportToJson(tableNames: List<String>, pretty: Boolean = true): String {
        val json = JSONObject()
        json.put("version", schema.version)

        val db = database.openHelper.readableDatabase

        for (tableName in tableNames) {
            val entitySchema = schema.getEntity(tableName) ?: continue
            val rows = JSONArray()

            // Build column list excluding fields marked as excluded in schema
            val columns = entitySchema.fields
                .filter { !it.excluded }
                .map { it.columnName }

            val cursor = db.query(
                "SELECT ${columns.joinToString(", ")} FROM $tableName"
            )

            cursor.use {
                while (it.moveToNext()) {
                    val row = cursorToJson(it, entitySchema)
                    rows.put(row)
                }
            }

            json.put(tableName, rows)
        }

        return if (pretty) json.toString(2) else json.toString()
    }

    /**
     * Import data from JSON into database tables.
     *
     * @param jsonString JSON string containing table data
     * @param tableNames List of table names to import (in order - parent tables first)
     * @return Map of table name to Pair of (inserted count, updated count)
     */
    fun importFromJson(jsonString: String, tableNames: List<String>): Map<String, Pair<Int, Int>> {
        val json = JSONObject(jsonString)
        val version = json.optInt("version", 1)

        if (version > schema.version) {
            throw IllegalArgumentException(
                "Unsupported schema version: $version (max supported: ${schema.version})"
            )
        }

        val db = database.openHelper.writableDatabase
        val results = mutableMapOf<String, Pair<Int, Int>>()

        // Track ID mappings for foreign key remapping: tableName -> (oldId -> newId)
        val idMappings = mutableMapOf<String, MutableMap<Long, Long>>()

        // Process tables in order (parent tables first for foreign key resolution)
        for (tableName in tableNames) {
            val entitySchema = schema.getEntity(tableName) ?: continue
            val rows = json.optJSONArray(tableName) ?: continue
            val idMapping = mutableMapOf<Long, Long>()
            idMappings[tableName] = idMapping

            var insertedCount = 0
            var updatedCount = 0

            // Find unique constraint for conflict detection
            val uniqueFields = entitySchema.uniqueIndices
                .firstOrNull()
                ?.columnNames
                ?: listOf("id")

            // Find foreign keys that need remapping
            val foreignKeys = entitySchema.foreignKeys

            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val oldId = row.optLong("id", 0)

                // Remap foreign key values using previously imported ID mappings
                val remappedRow = remapForeignKeys(row, foreignKeys, idMappings, entitySchema)

                // Check for existing row by unique constraint
                val existingId = findExistingId(db, tableName, remappedRow, uniqueFields, entitySchema)

                val newId = if (existingId != null) {
                    // Update existing row
                    updateRow(db, tableName, existingId, remappedRow, entitySchema)
                    updatedCount++
                    existingId
                } else {
                    // Insert new row
                    val id = insertRow(db, tableName, remappedRow, entitySchema)
                    insertedCount++
                    id
                }

                idMapping[oldId] = newId
            }

            // Second pass: update self-referencing foreign keys
            updateSelfReferences(db, tableName, entitySchema, idMapping, rows)

            results[tableName] = Pair(insertedCount, updatedCount)
        }

        // Notify Room's InvalidationTracker that tables have changed
        // This triggers Flow updates for any observers
        database.invalidationTracker.refreshVersionsAsync()

        return results
    }

    /**
     * Convert a cursor row to JSON using schema field definitions.
     */
    private fun cursorToJson(cursor: Cursor, entitySchema: EntitySchema): JSONObject {
        val json = JSONObject()

        for (field in entitySchema.fields) {
            if (field.excluded) continue

            val columnIndex = cursor.getColumnIndex(field.columnName)
            if (columnIndex < 0 || cursor.isNull(columnIndex)) continue

            val value: Any = when (field.affinity) {
                "INTEGER" -> cursor.getLong(columnIndex)
                "TEXT" -> cursor.getString(columnIndex)
                "REAL" -> cursor.getDouble(columnIndex)
                "BLOB" -> Base64.encodeToString(cursor.getBlob(columnIndex), Base64.NO_WRAP)
                else -> cursor.getString(columnIndex)
            }

            // Use fieldPath (Kotlin property name) as JSON key for consistency
            json.put(field.fieldPath, value)
        }

        return json
    }

    /**
     * Remap foreign key values using ID mappings from previously imported tables.
     */
    private fun remapForeignKeys(
        row: JSONObject,
        foreignKeys: List<ForeignKeySchema>,
        idMappings: Map<String, Map<Long, Long>>,
        entitySchema: EntitySchema
    ): JSONObject {
        val remapped = JSONObject(row.toString())

        for (fk in foreignKeys) {
            val referencedMapping = idMappings[fk.table] ?: continue

            for ((localCol, _) in fk.columns.zip(fk.referencedColumns)) {
                val field = entitySchema.fields.find { it.columnName == localCol } ?: continue
                val fieldPath = field.fieldPath

                if (remapped.has(fieldPath)) {
                    val oldValue = remapped.optLong(fieldPath, 0)
                    val newValue = referencedMapping[oldValue]
                    if (newValue != null) {
                        remapped.put(fieldPath, newValue)
                    } else {
                        // Foreign key references non-imported row, set to null
                        remapped.put(fieldPath, JSONObject.NULL)
                    }
                }
            }
        }

        return remapped
    }

    /**
     * Find existing row ID by unique constraint.
     */
    private fun findExistingId(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        row: JSONObject,
        uniqueFields: List<String>,
        entitySchema: EntitySchema
    ): Long? {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        for (columnName in uniqueFields) {
            val field = entitySchema.fields.find { it.columnName == columnName } ?: continue
            val value = row.opt(field.fieldPath) ?: continue

            conditions.add("$columnName = ?")
            args.add(value.toString())
        }

        if (conditions.isEmpty()) return null

        val cursor = db.query(
            "SELECT id FROM $tableName WHERE ${conditions.joinToString(" AND ")}",
            args.toTypedArray()
        )

        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    /**
     * Insert a new row into the database.
     */
    private fun insertRow(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        row: JSONObject,
        entitySchema: EntitySchema
    ): Long {
        val values = jsonToContentValues(row, entitySchema, excludeId = true)
        return db.insert(tableName, 0, values)
    }

    /**
     * Update an existing row in the database.
     */
    private fun updateRow(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        id: Long,
        row: JSONObject,
        entitySchema: EntitySchema
    ) {
        val values = jsonToContentValues(row, entitySchema, excludeId = true)
        db.update(tableName, 0, values, "id = ?", arrayOf(id.toString()))
    }

    /**
     * Update self-referencing foreign keys after all rows are imported.
     */
    private fun updateSelfReferences(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
        entitySchema: EntitySchema,
        idMapping: Map<Long, Long>,
        originalRows: JSONArray
    ) {
        // Find self-referencing fields (foreign keys that reference the same table)
        val selfRefFields = entitySchema.fields.filter { field ->
            entitySchema.foreignKeys.none { fk ->
                fk.columns.contains(field.columnName)
            } && field.fieldPath.endsWith("Id") && field.fieldPath != "id" &&
                field.affinity == "INTEGER"
        }

        // Also check for explicit self-referencing foreign keys
        val explicitSelfRefs = entitySchema.foreignKeys
            .filter { it.table == tableName }
            .flatMap { fk ->
                fk.columns.mapNotNull { col ->
                    entitySchema.fields.find { it.columnName == col }
                }
            }

        val allSelfRefFields = (selfRefFields + explicitSelfRefs).distinctBy { it.fieldPath }

        if (allSelfRefFields.isEmpty()) return

        for (i in 0 until originalRows.length()) {
            val row = originalRows.getJSONObject(i)
            val oldId = row.optLong("id", 0)
            val newId = idMapping[oldId] ?: continue

            for (field in allSelfRefFields) {
                if (!row.has(field.fieldPath)) continue
                val oldRefId = row.optLong(field.fieldPath, 0)
                if (oldRefId == 0L) continue

                val newRefId = idMapping[oldRefId] ?: continue

                db.execSQL(
                    "UPDATE $tableName SET ${field.columnName} = ? WHERE id = ?",
                    arrayOf(newRefId, newId)
                )
            }
        }
    }

    /**
     * Convert JSON object to ContentValues for database insertion.
     */
    private fun jsonToContentValues(
        row: JSONObject,
        entitySchema: EntitySchema,
        excludeId: Boolean
    ): ContentValues {
        val values = ContentValues()

        for (field in entitySchema.fields) {
            if (excludeId && field.columnName == "id") continue

            // For excluded fields, provide default values if NOT NULL
            if (field.excluded) {
                if (field.notNull) {
                    when (field.affinity) {
                        "INTEGER" -> values.put(field.columnName, 0L)
                        "TEXT" -> values.put(field.columnName, "")
                        "REAL" -> values.put(field.columnName, 0.0)
                        "BLOB" -> values.put(field.columnName, ByteArray(0))
                    }
                }
                continue
            }

            if (!row.has(field.fieldPath)) continue

            if (row.isNull(field.fieldPath)) {
                values.putNull(field.columnName)
                continue
            }

            when (field.affinity) {
                "INTEGER" -> values.put(field.columnName, row.getLong(field.fieldPath))
                "TEXT" -> values.put(field.columnName, row.getString(field.fieldPath))
                "REAL" -> values.put(field.columnName, row.getDouble(field.fieldPath))
                "BLOB" -> {
                    val base64 = row.getString(field.fieldPath)
                    values.put(field.columnName, Base64.decode(base64, Base64.NO_WRAP))
                }
            }
        }

        return values
    }
}
