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

package org.connectbot.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SSH known host key entity for host key verification.
 */
@Entity(
    tableName = "known_hosts",
    foreignKeys = [
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["host_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("host_id"),
        Index(value = ["hostname", "port"], unique = true)
    ]
)
data class KnownHost(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "host_id")
    val hostId: Long?,

    val hostname: String,

    val port: Int,

    @ColumnInfo(name = "host_key_algo")
    val hostKeyAlgo: String,

    @ColumnInfo(name = "host_key", typeAffinity = ColumnInfo.BLOB)
    val hostKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KnownHost

        if (id != other.id) return false
        if (hostId != other.hostId) return false
        if (hostname != other.hostname) return false
        if (port != other.port) return false
        if (hostKeyAlgo != other.hostKeyAlgo) return false
        if (!hostKey.contentEquals(other.hostKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (hostId?.hashCode() ?: 0)
        result = 31 * result + hostname.hashCode()
        result = 31 * result + port
        result = 31 * result + hostKeyAlgo.hashCode()
        result = 31 * result + hostKey.contentHashCode()
        return result
    }
}
