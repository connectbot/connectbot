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

package org.connectbot.data.migration

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.Cursor
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.DatabaseSchema
import org.connectbot.data.EntityIds
import org.connectbot.di.DatabaseModule
import org.connectbot.util.HostConstants
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.File
import java.util.UUID

/** Uses exported schemas for inputs, but explicit expectations for identity transformations. */
class UuidMigrationFixture(base: Context, private val readSchema: (Int) -> String) : AutoCloseable {
    companion object {
        const val LARGE_ID = 4_294_967_303L
        const val DATABASE_NAME = "connectbot.db"
        val tables = listOf("profiles", "hosts", "pubkeys", "port_forwards", "known_hosts", "color_schemes", "color_palette", "automation_actions")
        private val references = mapOf(
            "host_id" to "hosts",
            "jump_host_id" to "hosts",
            "profile_id" to "profiles",
            "pubkey_id" to "pubkeys",
            "scheme_id" to "color_schemes",
            "color_scheme_id" to "color_schemes",
            "forward_id" to "port_forwards",
        )
    }

    val context = IsolatedMigrationContext(base)
    private val databases = mutableListOf<ConnectBotDatabase>()
    lateinit var before: Map<String, List<Map<String, Any?>>>
        private set
    private var originalSchema: List<List<String?>> = emptyList()
    var version: Int = 14
        private set
    val preferences: SharedPreferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    val passwords: SharedPreferences get() = context.getSharedPreferences("secure_host_passwords", Context.MODE_PRIVATE)

    fun create(version: Int = 14, populated: Boolean = true, healthy: Boolean = true, edit: (SupportSQLiteDatabase) -> Unit = {}) {
        this.version = version
        val entities = JSONObject(readSchema(version)).getJSONObject("database").getJSONArray("entities")
        val schemas = (0 until entities.length()).map { entities.getJSONObject(it) }.associateBy { it.getString("tableName") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DATABASE_NAME).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)
                override fun onCreate(db: SupportSQLiteDatabase) {
                    schemas.forEach { (table, schema) ->
                        db.execSQL(schema.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = schema.getJSONArray("indices")
                        for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture must not upgrade databases")
            }).build(),
        )
        helper.use {
            val db = it.writableDatabase
            if (populated) {
                listOf("pubkeys", "color_schemes", "profiles", "hosts", "port_forwards", "known_hosts", "color_palette", "automation_actions").forEach { table ->
                    schemas[table]?.let { schema -> seed(db, table, schema) }
                }
            }
            edit(db)
            before = snapshot(db::query, schemas.keys.toList())
            originalSchema = schemaSnapshot(db::query)
            if (healthy) assertHealthy(db)
        }
    }

    private fun seed(db: SupportSQLiteDatabase, table: String, schema: JSONObject) {
        val count = if (table == "hosts") {
            4
        } else if (table == "color_schemes") {
            2
        } else {
            3
        }
        repeat(count) { i ->
            val id = if (i == 0) 1L else LARGE_ID + i - 1
            val boolean = i % 2
            val specific = mutableMapOf<String, Any?>(
                "id" to id, "nickname" to "$table-$i 世界", "name" to "$table-$i 世界",
                "host_id" to if (i == 2) 1L else LARGE_ID,
                "scheme_id" to if (i == 0) 1L else LARGE_ID,
                "color_scheme_id" to when (i) {
                    0 -> -1L
                    1 -> LARGE_ID
                    else -> -2L
                },
                "pubkey_id" to when (i) {
                    0 -> LARGE_ID
                    1 -> -1L
                    2 -> -2L
                    else -> 1L
                },
                "profile_id" to when (i) {
                    0 -> 1L
                    3 -> null
                    else -> LARGE_ID
                },
                "jump_host_id" to if (i == 1 || i == 2) 1L else null,
                "protocol" to "ssh", "hostname" to "host-$i.example", "port" to 2222L + i,
                "username" to "user-$i 世界", "last_connect" to 1_780_000_000_000L + i,
                "created_date" to 1_770_000_000_000L + i,
                "use_keys" to boolean, "want_session" to boolean, "compression" to boolean,
                "stay_connected" to boolean, "quick_disconnect" to boolean, "use_ctrl_alt_as_meta_key" to boolean,
                "use_auth_agent" to if (i == 0) null else "confirm", "host_key_algo" to "ssh-ed25519",
                "post_login" to if (i % 2 == 0) null else "echo '世界'\r\n\u0003\u0000",
                "font_size" to 17L + i, "encoding" to "UTF-8", "del_key" to "del", "emulation" to "xterm-256color",
                "icon_color" to if (i == 0) null else "#12ab34",
                "font_family" to if (i == 0) null else "monospace", "inline_images" to "ask", "bold_as_bright" to boolean,
                "force_size_rows" to if (i == 0) null else 30L + i, "force_size_columns" to if (i == 0) null else 100L + i,
                "scrollback_lines" to 731L + i, "ip_version" to "IPV6_ONLY", "mosh_port" to 60001L + i,
                "mosh_server" to if (i == 0) null else "custom-mosh-$i", "mosh_network_timeout" to if (i == 0) null else 150L + i,
                "locale" to "de_DE.UTF-8", "telnet_echo_mode" to "ON", "telnet_negotiation_mode" to "INTERACTIVE",
                "telnet_nonbinary_policy" to "EIGHT_BIT_COMPATIBILITY", "telnet_newline_policy" to "LF_TO_CRLF",
                "private_key" to if (i == 2) null else byteArrayOf(0, 1, -1, 4, i.toByte()),
                "public_key" to byteArrayOf(0, -128, 127, i.toByte()), "host_key" to byteArrayOf(0, -1, 13, i.toByte()),
                "storage_type" to if (i == 2) "ANDROID_KEYSTORE" else "EXPORTABLE",
                "keystore_alias" to if (i == 2) "test-alias-世界" else null,
                "encrypted" to boolean, "startup" to boolean, "confirmation" to boolean, "allow_backup" to boolean,
                "is_built_in" to 0L, "description" to "description $i\n世界", "foreground" to -123456L, "background" to -654321L,
                "color_index" to i.toLong(), "color" to -16711936L + i,
                "source_port" to 8000L + i, "source_addr" to "127.0.0.${i + 1}",
                "dest_addr" to if (i == 2) null else "dest-$i.example", "dest_port" to 9000L + i, "start_enabled" to boolean,
                "position" to if (i == 0) 1L else 0L, "text" to " \t世界\r\n\u0000$i", "regex" to boolean,
                "duration_ms" to 45_678L + i, "key" to 1L, "modifiers" to i.toLong(),
                "forward_id" to if (i == 0) LARGE_ID else null, "failure_policy" to if (i == 0) "CONTINUE" else "STOP",
            )
            specific["type"] = when (table) {
                "automation_actions" -> if (i == 0) "ENABLE_FORWARD" else "SEND_TEXT"
                "port_forwards" -> if (i == 2) "dynamic4" else "local"
                else -> "ssh-ed25519"
            }
            if (table == "known_hosts" && i == 1) specific["host_id"] = null
            if (table == "automation_actions") specific["id"] = UUID(0, i + 1L).toString()
            val values = ContentValues()
            val fields = schema.getJSONArray("fields")
            for (j in 0 until fields.length()) {
                val field = fields.getJSONObject(j)
                val column = field.getString("columnName")
                check(specific.containsKey(column)) { "Add an explicit fixture value for $table.$column" }
                when (val value = specific[column]) {
                    null -> values.putNull(column)
                    is ByteArray -> values.put(column, value)
                    is Number -> values.put(column, value.toLong())
                    else -> values.put(column, value.toString())
                }
            }
            check(db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values) != -1L)
        }
    }

    fun open(production: Boolean = false, migration: Migration = UuidMigration): ConnectBotDatabase {
        val database = if (production) {
            DatabaseModule.provideConnectBotDatabase(context)
        } else {
            Room.databaseBuilder(context, ConnectBotDatabase::class.java, DATABASE_NAME)
                .addMigrations(ConnectBotDatabase.MIGRATION_4_5, ConnectBotDatabase.MIGRATION_10_11, migration).build()
        }
        databases.add(database)
        return database
    }

    fun verifyPreservation(database: ConnectBotDatabase) {
        val db = database.openHelper.writableDatabase
        val currentVersion = DatabaseSchema.load(context).version
        assertEquals(currentVersion, db.version)
        assertHealthy(db)
        val after = snapshot(db::query)
        val mappings = mappings(db)
        before.forEach { (table, rows) ->
            assertEquals("row count: $table", rows.size, after.getValue(table).size)
            rows.forEach { old ->
                val id = if (table == "automation_actions") old.getValue("id") else mappings.getValue(table to (old.getValue("id") as Long))
                val actual = after.getValue(table).single { it["id"] == id }
                old.forEach { (column, value) ->
                    if (version < 5 && table == "hosts" && column in setOf("color_scheme_id", "font_size", "del_key", "encoding")) return@forEach
                    val expected = when {
                        column == "id" -> id
                        column == "post_login" && version <= 10 -> null
                        value != null && column in references -> reference(column, value as Long, mappings)
                        else -> value
                    }
                    assertEquals("v$version $table[$id].$column", expected, actual[column])
                }
                if (table != "automation_actions") assertEquals(EntityIds.LOCAL_TEXT, actual["repository_id"])
                if (table == "hosts") {
                    assertEquals(0L, actual["temporary"])
                    if (version < 5) {
                        val profile = after.getValue("profiles").single { it["id"] == actual["profile_id"] }
                        for (column in listOf("color_scheme_id", "font_size", "del_key", "encoding")) {
                            val expected = if (column == "color_scheme_id") reference(column, old[column] as Long, mappings) else old[column]
                            assertEquals("legacy host settings: $column", expected, profile[column])
                        }
                    }
                }
            }
            if (table != "automation_actions") {
                assertEquals(rows.map { it.getValue("id") }.toSet(), mappings.keys.filter { it.first == table }.map { it.second }.toSet())
            }
        }
        val currentSchema = JSONObject(readSchema(currentVersion)).getJSONObject("database").getJSONArray("entities")
        for (i in 0 until currentSchema.length()) {
            val entity = currentSchema.getJSONObject(i)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            // Check defaults for columns added by the intermediate migrations as well.
            val oldColumns = before[table]?.firstOrNull()?.keys ?: continue
            for (j in 0 until fields.length()) {
                val field = fields.getJSONObject(j)
                val column = field.getString("columnName")
                if (column !in oldColumns && field.has("defaultValue")) {
                    val raw = field.getString("defaultValue")
                    val expected = if (field.getString("affinity") == "INTEGER") raw.toLong() else raw.removeSurrounding("'")
                    after.getValue(table).forEach { assertEquals("default $table.$column", expected, it[column]) }
                }
            }
        }
        val mappedIds = mappings.values
        assertEquals(mappedIds.size, mappedIds.toSet().size)
        mappedIds.forEach { assertEquals(it, UUID.fromString(it).toString()) }
        assertEquals(after.filterKeys { it != "automation_actions" }.values.sumOf { it.size }, mappings.size)
        if (before["profiles"]?.any { it["id"] == 1L } == true) assertEquals(EntityIds.DEFAULT_PROFILE.toString(), mappings["profiles" to 1L])
        if (version <= 10) {
            val texts = before.getValue("hosts").filter { !it["post_login"].let { text -> text == null || text == "" } }
            assertEquals(texts.size, after.getValue("automation_actions").size)
            texts.forEach { old ->
                val action = after.getValue("automation_actions").single { it["host_id"] == mappings["hosts" to (old.getValue("id") as Long)] }
                assertEquals(old["post_login"], action["text"])
                assertEquals("SEND_TEXT", action["type"])
                assertEquals(4, UUID.fromString(action["id"] as String).version())
            }
        }
    }

    private fun reference(column: String, old: Long, mappings: Map<Pair<String, Long>, String>): String? = when {
        column == "pubkey_id" && old == -1L -> HostConstants.PUBKEYID_ANY.toString()
        column == "pubkey_id" && old == -2L -> HostConstants.PUBKEYID_NEVER.toString()
        column == "color_scheme_id" && old < 0 -> EntityIds.preset((-old - 1).toInt()).toString()
        column == "pubkey_id" -> mappings["pubkeys" to old] ?: HostConstants.PUBKEYID_ANY.toString()
        column == "color_scheme_id" -> mappings["color_schemes" to old] ?: EntityIds.DEFAULT_SCHEME.toString()
        else -> mappings[references.getValue(column) to old]
    }

    fun mappings(db: SupportSQLiteDatabase): Map<Pair<String, Long>, String> = db.query("SELECT entity,old_id,uuid FROM legacy_id_map").use { cursor ->
        buildMap { while (cursor.moveToNext()) put(cursor.getString(0) to cursor.getLong(1), cursor.getString(2)) }
    }

    fun assertHealthy(db: SupportSQLiteDatabase) {
        // foreign_keys is connection-local. Pin checks to the writer that enforces constraints.
        val ownsTransaction = !db.inTransaction()
        if (ownsTransaction) db.beginTransaction()
        try {
            db.query("PRAGMA foreign_keys").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse("Foreign key violation", it.moveToFirst()) }
            db.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst())
                assertEquals("ok", it.getString(0))
            }
        } finally {
            if (ownsTransaction) db.endTransaction()
        }
    }

    suspend fun verifyDaoReads(database: ConnectBotDatabase) {
        val hosts = database.hostDao().getAll()
        assertEquals(4, hosts.size)
        assertEquals(3, database.pubkeyDao().getAll().size)
        assertEquals(3, database.profileDao().getAll().size)
        assertEquals(3, database.knownHostDao().getAll().size)
        assertEquals(3, hosts.sumOf { database.portForwardDao().getByHost(it.id).size })
        assertEquals(3, hosts.sumOf { database.automationActionDao().getByHost(it.id).size })
        val schemes = database.colorSchemeDao().getAll()
        assertEquals(2, schemes.size)
        assertEquals(3, schemes.sumOf { database.colorSchemeDao().getColors(it.id).size })
    }

    fun verifyReopen(production: Boolean = false) {
        val first = open(production)
        verifyPreservation(first)
        val rows = snapshot(first.openHelper.readableDatabase::query)
        val ids = mappings(first.openHelper.readableDatabase)
        first.close()
        val second = open(production)
        verifyPreservation(second)
        assertEquals(rows, snapshot(second.openHelper.readableDatabase::query))
        assertEquals(ids, mappings(second.openHelper.readableDatabase))
    }

    fun verifyCascades() {
        val database = open(production = true)
        verifyPreservation(database)
        val db = database.openHelper.writableDatabase
        val ids = mappings(db)
        val host = ids.getValue("hosts" to LARGE_ID)
        db.execSQL("DELETE FROM hosts WHERE id=?", arrayOf(host))
        for (table in listOf("port_forwards", "known_hosts", "automation_actions")) {
            db.query("SELECT COUNT(*) FROM $table WHERE host_id=?", arrayOf(host)).use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
        assertEquals(3, snapshot(db::query).getValue("hosts").size)
        val scheme = ids.getValue("color_schemes" to LARGE_ID)
        db.execSQL("DELETE FROM color_schemes WHERE id=?", arrayOf(scheme))
        db.query("SELECT COUNT(*) FROM color_palette WHERE scheme_id=?", arrayOf(scheme)).use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        assertHealthy(db)
    }

    enum class FailurePoint { COPY, DROP, RENAME }

    fun verifyRollback(point: FailurePoint) {
        var injected = false
        val failing = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                UuidMigration.migrate(object : SupportSQLiteDatabase by db {
                    override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long {
                        val result = db.insert(table, conflictAlgorithm, values)
                        if (point == FailurePoint.COPY && table == "hosts_uuid") {
                            injected = true
                            error("Injected failure during $point")
                        }
                        return result
                    }
                    override fun execSQL(sql: String) {
                        db.execSQL(sql)
                        if ((point == FailurePoint.DROP && sql == "DROP TABLE `hosts`") ||
                            (point == FailurePoint.RENAME && sql == "ALTER TABLE `hosts_uuid` RENAME TO `hosts`")
                        ) {
                            injected = true
                            error("Injected failure during $point")
                        }
                    }
                })
            }
        }
        val database = open(migration = failing)
        try {
            database.openHelper.writableDatabase
            fail("Migration should fail at $point")
        } catch (expected: IllegalStateException) {
            assertTrue("Expected injected failure, got $expected", injected)
        } finally {
            database.close()
        }
        verifyOriginal()
        verifyReopen()
    }

    fun verifyOriginal() {
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { raw ->
            assertEquals(version, raw.version)
            assertEquals(originalSchema, schemaSnapshot { raw.rawQuery(it, null) })
            assertEquals(before, snapshot({ raw.rawQuery(it, null) }, before.keys.toList()))
            raw.rawQuery("SELECT name FROM sqlite_master WHERE name='legacy_id_map' OR name LIKE '%_uuid'", null).use { assertFalse(it.moveToFirst()) }
        }
    }

    private fun schemaSnapshot(query: (String) -> Cursor): List<List<String?>> = query("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name").use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
    }

    fun snapshot(query: (String) -> Cursor, names: List<String> = tables): Map<String, List<Map<String, Any?>>> = names.associateWith { table ->
        query("SELECT * FROM `$table` ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        cursor.columnNames.mapIndexed { i, column ->
                            column to when (cursor.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> null
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                                Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i).toList()
                                else -> cursor.getString(i)
                            }
                        }.toMap(),
                    )
                }
            }
        }
    }

    override fun close() {
        databases.forEach { it.close() }
        context.cleanUp()
    }
}

/** Run the production builder without sharing its fixed database/preference names with other tests. */
class IsolatedMigrationContext(base: Context) : ContextWrapper(base) {
    private val prefix = "uuid-test-${UUID.randomUUID()}-"
    private val preferenceNames = mutableSetOf<String>()
    override fun getApplicationContext(): Context = this
    override fun getDatabasePath(name: String): File = super.getDatabasePath(prefix + name)
    override fun deleteDatabase(name: String): Boolean = super.deleteDatabase(prefix + name)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase = super.openOrCreateDatabase(prefix + name, mode, factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase = super.openOrCreateDatabase(prefix + name, mode, factory, errorHandler)
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        preferenceNames.add(name)
        return super.getSharedPreferences(prefix + name, mode)
    }
    fun cleanUp() {
        preferenceNames.forEach { super.getSharedPreferences(prefix + it, MODE_PRIVATE).edit().clear().commit() }
        deleteDatabase(UuidMigrationFixture.DATABASE_NAME)
    }
}
