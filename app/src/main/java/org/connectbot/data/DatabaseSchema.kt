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
import org.json.JSONObject

/**
 * Parser for Room database schema JSON files.
 *
 * Room exports schema files to app/schemas/ during build. This class parses
 * those schema files to enable dynamic, schema-driven JSON export/import
 * that automatically adapts to database schema changes.
 */
class DatabaseSchema private constructor(private val schemaJson: JSONObject) {

    val version: Int = schemaJson.getJSONObject("database").getInt("version")

    val entities: Map<String, EntitySchema> by lazy {
        val database = schemaJson.getJSONObject("database")
        val entitiesArray = database.getJSONArray("entities")
        val result = mutableMapOf<String, EntitySchema>()

        for (i in 0 until entitiesArray.length()) {
            val entityJson = entitiesArray.getJSONObject(i)
            val entity = EntitySchema.fromJson(entityJson)
            result[entity.tableName] = entity
        }

        result
    }

    /**
     * Get entity schema by table name.
     */
    fun getEntity(tableName: String): EntitySchema? = entities[tableName]

    companion object {
        private const val EXPORT_SCHEMA_PATH = "export_schema.json"

        /**
         * Load the export schema from assets.
         *
         * The export schema is a filtered version of the Room schema containing
         * only the tables and fields needed for export/import. It is generated
         * at build time by the generateExportSchema Gradle task.
         *
         * @param context Android context for accessing assets
         * @return DatabaseSchema instance
         */
        fun load(context: Context): DatabaseSchema {
            val jsonString = context.assets.open(EXPORT_SCHEMA_PATH).bufferedReader().use { it.readText() }
            return DatabaseSchema(JSONObject(jsonString))
        }
    }
}

/**
 * Schema definition for a single database entity (table).
 */
data class EntitySchema(
    val tableName: String,
    val fields: List<FieldSchema>,
    val primaryKey: PrimaryKeySchema,
    val foreignKeys: List<ForeignKeySchema>,
    val uniqueIndices: List<UniqueIndexSchema>
) {
    /**
     * Get field schema by field path (Kotlin property name).
     */
    fun getField(fieldPath: String): FieldSchema? = fields.find { it.fieldPath == fieldPath }

    /**
     * Get the unique index columns for conflict detection (e.g., "nickname" for hosts).
     */
    fun getUniqueConstraintFields(): List<String> {
        return uniqueIndices.flatMap { it.columnNames }
    }

    companion object {
        fun fromJson(json: JSONObject): EntitySchema {
            val tableName = json.getString("tableName")

            // Parse fields
            val fieldsArray = json.getJSONArray("fields")
            val fields = mutableListOf<FieldSchema>()
            for (i in 0 until fieldsArray.length()) {
                fields.add(FieldSchema.fromJson(fieldsArray.getJSONObject(i)))
            }

            // Parse primary key
            val primaryKeyJson = json.getJSONObject("primaryKey")
            val primaryKey = PrimaryKeySchema(
                autoGenerate = primaryKeyJson.getBoolean("autoGenerate"),
                columnNames = primaryKeyJson.getJSONArray("columnNames").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            )

            // Parse foreign keys
            val foreignKeys = mutableListOf<ForeignKeySchema>()
            val foreignKeysArray = json.optJSONArray("foreignKeys")
            if (foreignKeysArray != null) {
                for (i in 0 until foreignKeysArray.length()) {
                    foreignKeys.add(ForeignKeySchema.fromJson(foreignKeysArray.getJSONObject(i)))
                }
            }

            // Parse unique indices
            val uniqueIndices = mutableListOf<UniqueIndexSchema>()
            val indicesArray = json.optJSONArray("indices")
            if (indicesArray != null) {
                for (i in 0 until indicesArray.length()) {
                    val indexJson = indicesArray.getJSONObject(i)
                    if (indexJson.optBoolean("unique", false)) {
                        uniqueIndices.add(UniqueIndexSchema.fromJson(indexJson))
                    }
                }
            }

            return EntitySchema(tableName, fields, primaryKey, foreignKeys, uniqueIndices)
        }
    }
}

/**
 * Schema definition for a single field (column).
 */
data class FieldSchema(
    val fieldPath: String,      // Kotlin property name (e.g., "jumpHostId")
    val columnName: String,     // Database column name (e.g., "jump_host_id")
    val affinity: String,       // SQLite type affinity (INTEGER, TEXT, BLOB, REAL)
    val notNull: Boolean,
    val excluded: Boolean       // True if field should be excluded from export (e.g., runtime state)
) {
    companion object {
        fun fromJson(json: JSONObject): FieldSchema {
            return FieldSchema(
                fieldPath = json.getString("fieldPath"),
                columnName = json.getString("columnName"),
                affinity = json.getString("affinity"),
                notNull = json.optBoolean("notNull", false),
                excluded = json.optBoolean("excluded", false)
            )
        }
    }
}

/**
 * Schema definition for a primary key.
 */
data class PrimaryKeySchema(
    val autoGenerate: Boolean,
    val columnNames: List<String>
)

/**
 * Schema definition for a foreign key relationship.
 */
data class ForeignKeySchema(
    val table: String,              // Referenced table name
    val columns: List<String>,      // Local column names
    val referencedColumns: List<String>,  // Referenced column names
    val onDelete: String
) {
    companion object {
        fun fromJson(json: JSONObject): ForeignKeySchema {
            return ForeignKeySchema(
                table = json.getString("table"),
                columns = json.getJSONArray("columns").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                referencedColumns = json.getJSONArray("referencedColumns").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                onDelete = json.getString("onDelete")
            )
        }
    }
}

/**
 * Schema definition for a unique index.
 */
data class UniqueIndexSchema(
    val name: String,
    val columnNames: List<String>
) {
    companion object {
        fun fromJson(json: JSONObject): UniqueIndexSchema {
            return UniqueIndexSchema(
                name = json.getString("name"),
                columnNames = json.getJSONArray("columnNames").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            )
        }
    }
}
