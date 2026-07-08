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

package org.connectbot.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Reusable command snippet entity.
 *
 * Snippets are saved commands that can be sent to a terminal session with a
 * tap. A snippet is either global (available on every host) or scoped to a
 * single host. Commands may contain `${variable}` placeholders that are
 * prompted for at run time.
 */
@Entity(
    tableName = "snippets",
    foreignKeys = [
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["host_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("host_id")],
)
data class Snippet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String = "",

    val command: String = "",

    /**
     * Comma-separated tags used to organize and filter snippets.
     */
    val tags: String = "",

    /**
     * Host this snippet is scoped to. A value of null means the snippet is
     * global and available on every host. Deleting the host demotes its
     * snippets to global rather than destroying them.
     */
    @ColumnInfo(name = "host_id")
    val hostId: Long? = null,
) {
    /**
     * Parsed tag list, with whitespace trimmed and empty entries removed.
     */
    val tagList: List<String>
        get() = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
