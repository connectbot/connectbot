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
import org.connectbot.terminal.VTermKey
import org.connectbot.util.TerminalKeyModifiers
import java.util.UUID

enum class AutomationActionType {
    WAIT_FOR_TEXT,
    SEND_TEXT,
    SEND_KEY,
    DELAY,
    DISCONNECT,
    ENABLE_FORWARD,
    DISABLE_FORWARD,
}

enum class AutomationFailurePolicy { STOP, CONTINUE }

/** Identity is independent of both device-local host IDs and sequence position. */
@Entity(
    tableName = "automation_actions",
    foreignKeys = [
        ForeignKey(
            entity = Host::class,
            parentColumns = ["id"],
            childColumns = ["host_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("host_id")],
)
data class AutomationAction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "host_id") val hostId: Long,
    val position: Int = 0,
    val type: AutomationActionType = AutomationActionType.SEND_TEXT,
    val text: String = "",
    val regex: Boolean = false,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 30_000,
    val key: Int = VTermKey.ENTER,
    val modifiers: Int = TerminalKeyModifiers.NONE,
    // Intentionally not a foreign key: deleting a forward must not delete an action.
    @ColumnInfo(name = "forward_id") val forwardId: Long? = null,
    @ColumnInfo(name = "failure_policy") val failurePolicy: AutomationFailurePolicy = AutomationFailurePolicy.STOP,
)
