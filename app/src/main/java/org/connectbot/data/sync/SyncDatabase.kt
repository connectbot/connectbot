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

package org.connectbot.data.sync

import androidx.sqlite.db.SupportSQLiteDatabase
import org.connectbot.data.EntityIds
import org.connectbot.util.HostConstants

/** Durable protocol state. These tables contain no passwords or access tokens. */
object SyncDatabase {
    val tables = listOf("hosts", "profiles", "color_schemes", "color_palette", "port_forwards", "known_hosts", "settings")

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_control (id INTEGER PRIMARY KEY CHECK(id=1), applying INTEGER NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO sync_control VALUES(1,0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_repositories (id TEXT PRIMARY KEY NOT NULL, remote_id TEXT, server TEXT, account_id TEXT, name TEXT NOT NULL, owner_type TEXT NOT NULL, role TEXT NOT NULL, cursor INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("INSERT OR IGNORE INTO sync_repositories(id,name,owner_type,role) VALUES(?, 'Local', 'local', 'admin')", arrayOf(EntityIds.LOCAL_TEXT))
        db.execSQL("CREATE TABLE IF NOT EXISTS settings (id TEXT PRIMARY KEY NOT NULL, repository_id TEXT NOT NULL, key TEXT NOT NULL, value TEXT NOT NULL, UNIQUE(repository_id,key))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_outbox (repository_id TEXT NOT NULL, kind TEXT NOT NULL, object_id TEXT NOT NULL, operation TEXT NOT NULL, generation INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(repository_id,kind,object_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_versions (repository_id TEXT NOT NULL, kind TEXT NOT NULL, object_id TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(repository_id,kind,object_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_batches (id TEXT PRIMARY KEY NOT NULL, repository_id TEXT NOT NULL, request TEXT NOT NULL, generations TEXT NOT NULL, sources TEXT NOT NULL DEFAULT '[]', transfer INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_host_redirects (old_id TEXT PRIMARY KEY NOT NULL, new_id TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_conflicts (repository_id TEXT PRIMARY KEY NOT NULL, detail TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_certificates (id TEXT PRIMARY KEY NOT NULL, repository_id TEXT NOT NULL, key_fingerprint TEXT NOT NULL, state TEXT NOT NULL, response TEXT)")
        (tables + "pubkeys").forEach { table ->
            val localFields = setOf("id", "repository_id", "last_connect", "pubkey_id", "temporary") + if (table == "hosts") setOf("host_key_algo") else emptySet()
            val portableColumns = db.query("PRAGMA table_info(`$table`)").use { c -> buildList { while (c.moveToNext()) if (c.getString(1) !in localFields) add(c.getString(1)) } }
            listOf("INSERT", "UPDATE", "DELETE").forEach { event ->
                val changed = if (event == "UPDATE") " AND (" + portableColumns.joinToString(" OR ") { "OLD.`$it` IS NOT NEW.`$it`" } + ")" else ""
                val row = if (event == "DELETE") "OLD" else "NEW"
                val operation = if (event == "DELETE") "delete" else "put"
                if (event == "UPDATE") {
                    db.execSQL(
                        """CREATE TRIGGER IF NOT EXISTS sync_${table}_owner BEFORE UPDATE ON `$table`
                    WHEN (SELECT applying FROM sync_control WHERE id=1)=0 AND OLD.repository_id!=NEW.repository_id BEGIN
                    SELECT RAISE(ABORT, 'Use Move to group to change repository'); END""",
                    )
                }
                db.execSQL(
                    """CREATE TRIGGER IF NOT EXISTS sync_${table}_${event.lowercase()} BEFORE $event ON `$table`
                    WHEN (SELECT applying FROM sync_control WHERE id=1)=0 $changed BEGIN
                    SELECT CASE WHEN EXISTS(SELECT 1 FROM sync_repositories WHERE id=$row.repository_id AND role='reader') THEN RAISE(ABORT, 'Repository is read-only') END;
                    UPDATE sync_outbox SET operation='$operation',generation=generation+1 WHERE repository_id=$row.repository_id AND kind='$table' AND object_id=$row.id;
                    INSERT INTO sync_outbox(repository_id,kind,object_id,operation) SELECT $row.repository_id,'$table',$row.id,'$operation' WHERE NOT EXISTS(SELECT 1 FROM sync_outbox WHERE repository_id=$row.repository_id AND kind='$table' AND object_id=$row.id);
                    END""",
                )
            }
        }
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS sync_host_references BEFORE DELETE ON hosts BEGIN
            UPDATE hosts SET jump_host_id=NULL WHERE jump_host_id=OLD.id;
            END""",
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS sync_profile_references BEFORE DELETE ON profiles BEGIN
            UPDATE hosts SET profile_id=NULL WHERE profile_id=OLD.id;
            END""",
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS sync_scheme_references BEFORE DELETE ON color_schemes BEGIN
            UPDATE profiles SET color_scheme_id='${EntityIds.DEFAULT_SCHEME}' WHERE color_scheme_id=OLD.id;
            END""",
        )
        db.execSQL(
            """CREATE TRIGGER IF NOT EXISTS sync_key_references BEFORE DELETE ON pubkeys BEGIN
            UPDATE hosts SET pubkey_id='${HostConstants.PUBKEYID_ANY}' WHERE pubkey_id=OLD.id;
            END""",
        )
    }
}
