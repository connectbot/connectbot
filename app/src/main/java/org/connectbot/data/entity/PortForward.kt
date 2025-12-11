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
 * SSH port forwarding rule entity.
 * Supports local, remote, and dynamic (SOCKS) port forwarding.
 */
@Entity(
    tableName = "port_forwards",
    foreignKeys = [
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["host_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("host_id")]
)
data class PortForward(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "host_id")
    val hostId: Long,

    val nickname: String,

    val type: String,

    @ColumnInfo(name = "source_port")
    val sourcePort: Int,

    @ColumnInfo(name = "dest_addr")
    val destAddr: String?,

    @ColumnInfo(name = "dest_port")
    val destPort: Int
) {
    // Transient fields (not stored in database)
    @Transient
    private var enabled: Boolean = false

    @Transient
    private var identifier: Any? = null

    /**
     * Get a human-readable description of this port forward (Java interop helper).
     */
    fun getDescription(): String {
        return "$nickname ($type)"
    }

    /**
     * Check if this port forward is currently enabled (Java interop helper).
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Set whether this port forward is enabled (Java interop helper).
     */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /**
     * Get the identifier object (DynamicPortForwarder or LocalPortForwarder).
     */
    fun getIdentifier(): Any? = identifier

    /**
     * Set the identifier object (Java interop helper).
     */
    fun setIdentifier(value: Any?) {
        identifier = value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PortForward

        if (id != other.id) return false
        if (hostId != other.hostId) return false
        if (nickname != other.nickname) return false
        if (type != other.type) return false
        if (sourcePort != other.sourcePort) return false
        if (destAddr != other.destAddr) return false
        if (destPort != other.destPort) return false
        if (enabled != other.enabled) return false
        if (identifier != other.identifier) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + hostId.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + sourcePort
        result = 31 * result + (destAddr?.hashCode() ?: 0)
        result = 31 * result + destPort
        result = 31 * result + enabled.hashCode()
        result = 31 * result + (identifier?.hashCode() ?: 0)
        return result
    }
}
