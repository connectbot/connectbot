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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.connectbot.data.EntityIds
import java.util.UUID

/** Rebuild all identity columns in one SQLite transaction; preserve mappings for external preferences. */
object UuidMigration : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA defer_foreign_keys = ON")
        db.execSQL("CREATE TABLE IF NOT EXISTS legacy_id_map (entity TEXT NOT NULL, old_id INTEGER NOT NULL, uuid TEXT NOT NULL, PRIMARY KEY(entity, old_id))")
        val tables = listOf("profiles", "hosts", "pubkeys", "port_forwards", "known_hosts", "color_schemes", "color_palette", "automation_actions")
        val mappings = mutableMapOf<String, MutableMap<Long, String>>()
        tables.filter { it != "automation_actions" }.forEach { table ->
            val map = mutableMapOf<Long, String>()
            db.query("SELECT id FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    val old = cursor.getLong(0)
                    val id = if (table == "profiles" && old == 1L) EntityIds.DEFAULT_PROFILE.toString() else UUID.randomUUID().toString()
                    map[old] = id
                    db.execSQL("INSERT INTO legacy_id_map VALUES (?, ?, ?)", arrayOf<Any>(table, old, id))
                }
            }
            mappings[table] = map
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS `hosts_uuid` (`id` TEXT NOT NULL, `repository_id` TEXT NOT NULL DEFAULT 'b6ba8cb6-02d4-492d-8de9-5747abbf3b21', `temporary` INTEGER NOT NULL DEFAULT 0, `nickname` TEXT NOT NULL, `protocol` TEXT NOT NULL, `username` TEXT NOT NULL, `hostname` TEXT NOT NULL, `port` INTEGER NOT NULL, `host_key_algo` TEXT, `last_connect` INTEGER NOT NULL, `color` TEXT, `use_keys` INTEGER NOT NULL, `use_auth_agent` TEXT, `post_login` TEXT, `pubkey_id` TEXT NOT NULL, `want_session` INTEGER NOT NULL, `compression` INTEGER NOT NULL, `stay_connected` INTEGER NOT NULL, `quick_disconnect` INTEGER NOT NULL, `scrollback_lines` INTEGER NOT NULL, `use_ctrl_alt_as_meta_key` INTEGER NOT NULL, `jump_host_id` TEXT, `profile_id` TEXT, `ip_version` TEXT NOT NULL DEFAULT 'IPV4_AND_IPV6', `mosh_port` INTEGER NOT NULL DEFAULT 0, `mosh_network_timeout` INTEGER, `mosh_server` TEXT, `locale` TEXT NOT NULL DEFAULT 'en_US.UTF-8', `telnet_echo_mode` TEXT NOT NULL DEFAULT 'AUTO', `telnet_negotiation_mode` TEXT NOT NULL DEFAULT 'INTERACTIVE', `telnet_nonbinary_policy` TEXT NOT NULL DEFAULT 'EIGHT_BIT_COMPATIBILITY', `telnet_newline_policy` TEXT NOT NULL DEFAULT 'LF_TO_CRLF', PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `pubkeys_uuid` (`id` TEXT NOT NULL, `repository_id` TEXT NOT NULL DEFAULT 'b6ba8cb6-02d4-492d-8de9-5747abbf3b21', `nickname` TEXT NOT NULL, `type` TEXT NOT NULL, `private_key` BLOB, `public_key` BLOB NOT NULL, `encrypted` INTEGER NOT NULL, `startup` INTEGER NOT NULL, `confirmation` INTEGER NOT NULL, `created_date` INTEGER NOT NULL, `storage_type` TEXT NOT NULL, `allow_backup` INTEGER NOT NULL, `keystore_alias` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `port_forwards_uuid` (`id` TEXT NOT NULL, `repository_id` TEXT NOT NULL DEFAULT 'b6ba8cb6-02d4-492d-8de9-5747abbf3b21', `host_id` TEXT NOT NULL, `nickname` TEXT NOT NULL, `type` TEXT NOT NULL, `source_addr` TEXT NOT NULL DEFAULT 'localhost', `source_port` INTEGER NOT NULL, `dest_addr` TEXT, `dest_port` INTEGER NOT NULL, `start_enabled` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`id`), FOREIGN KEY(`host_id`) REFERENCES `hosts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `known_hosts_uuid` (`id` TEXT NOT NULL, `repository_id` TEXT NOT NULL DEFAULT 'b6ba8cb6-02d4-492d-8de9-5747abbf3b21', `host_id` TEXT, `hostname` TEXT NOT NULL, `port` INTEGER NOT NULL, `host_key_algo` TEXT NOT NULL, `host_key` BLOB NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`host_id`) REFERENCES `hosts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `color_schemes_uuid` (`id` TEXT NOT NULL, `repository_id` TEXT NOT NULL DEFAULT 'b6ba8cb6-02d4-492d-8de9-5747abbf3b21', `name` TEXT NOT NULL, `is_built_in` INTEGER NOT NULL, `description` TEXT NOT NULL, `foreground` INTEGER NOT NULL, `background` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `color_palette_uuid` (`id` TEXT NOT NULL, `repository_id` TEXT NOT NULL DEFAULT 'b6ba8cb6-02d4-492d-8de9-5747abbf3b21', `scheme_id` TEXT NOT NULL, `color_index` INTEGER NOT NULL, `color` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`scheme_id`) REFERENCES `color_schemes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE TABLE IF NOT EXISTS `profiles_uuid` (`id` TEXT NOT NULL, `repository_id` TEXT NOT NULL DEFAULT 'b6ba8cb6-02d4-492d-8de9-5747abbf3b21', `name` TEXT NOT NULL, `icon_color` TEXT, `color_scheme_id` TEXT NOT NULL, `font_family` TEXT, `font_size` INTEGER NOT NULL DEFAULT 10, `del_key` TEXT NOT NULL DEFAULT 'del', `encoding` TEXT NOT NULL DEFAULT 'UTF-8', `emulation` TEXT NOT NULL DEFAULT 'xterm-256color', `inline_images` TEXT NOT NULL DEFAULT 'ask', `force_size_rows` INTEGER, `force_size_columns` INTEGER, `bold_as_bright` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `automation_actions_uuid` (`id` TEXT NOT NULL, `host_id` TEXT NOT NULL, `position` INTEGER NOT NULL, `type` TEXT NOT NULL, `text` TEXT NOT NULL, `regex` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL, `key` INTEGER NOT NULL, `modifiers` INTEGER NOT NULL, `forward_id` TEXT, `failure_policy` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`host_id`) REFERENCES `hosts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        val references = mapOf("host_id" to "hosts", "forward_id" to "port_forwards", "jump_host_id" to "hosts", "pubkey_id" to "pubkeys", "profile_id" to "profiles", "scheme_id" to "color_schemes", "color_scheme_id" to "color_schemes")
        tables.forEach { table ->
            db.query("SELECT * FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    val values = ContentValues()
                    cursor.columnNames.forEachIndexed { index, column ->
                        if (cursor.isNull(index)) {
                            values.putNull(column)
                        } else if (column == "id" && table != "automation_actions") {
                            values.put(column, mappings.getValue(table).getValue(cursor.getLong(index)))
                        } else if (column in references) {
                            val old = cursor.getLong(index)
                            val mapped = when {
                                column == "pubkey_id" && old < 0 -> EntityIds.legacyKey(old).toString()
                                column == "color_scheme_id" && old < 0 -> EntityIds.preset((-old - 1).toInt()).toString()
                                else -> mappings[references[column]]?.get(old)
                            }
                            if (mapped == null) {
                                when (column) {
                                    "pubkey_id" -> values.put(column, EntityIds.legacyKey(-1).toString())
                                    "color_scheme_id" -> values.put(column, EntityIds.DEFAULT_SCHEME.toString())
                                    else -> values.putNull(column)
                                }
                            } else {
                                values.put(column, mapped)
                            }
                        } else {
                            when (cursor.getType(index)) {
                                1 -> values.put(column, cursor.getLong(index))
                                2 -> values.put(column, cursor.getDouble(index))
                                4 -> values.put(column, cursor.getBlob(index))
                                else -> values.put(column, cursor.getString(index))
                            }
                        }
                    }
                    db.insert("${table}_uuid", 2, values)
                }
            }
        }
        listOf("automation_actions", "known_hosts", "port_forwards", "color_palette", "hosts", "profiles", "pubkeys", "color_schemes").forEach { db.execSQL("DROP TABLE `$it`") }
        tables.forEach { db.execSQL("ALTER TABLE `${it}_uuid` RENAME TO `$it`") }
        db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_actions_host_id ON automation_actions(host_id)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hosts_repository_id_nickname` ON `hosts` (`repository_id`, `nickname`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_hosts_protocol_username_hostname_port` ON `hosts` (`protocol`, `username`, `hostname`, `port`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pubkeys_repository_id_nickname` ON `pubkeys` (`repository_id`, `nickname`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pubkeys_storage_type` ON `pubkeys` (`storage_type`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pubkeys_allow_backup` ON `pubkeys` (`allow_backup`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_port_forwards_host_id` ON `port_forwards` (`host_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_hosts_host_id` ON `known_hosts` (`host_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_known_hosts_host_id_host_key` ON `known_hosts` (`host_id`, `host_key`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_color_schemes_repository_id_name` ON `color_schemes` (`repository_id`, `name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_color_palette_scheme_id` ON `color_palette` (`scheme_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_color_palette_scheme_id_color_index` ON `color_palette` (`scheme_id`, `color_index`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profiles_repository_id_name` ON `profiles` (`repository_id`, `name`)")
    }
}
