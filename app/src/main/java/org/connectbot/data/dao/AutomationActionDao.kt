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

package org.connectbot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.connectbot.data.entity.AutomationAction
import java.util.UUID

@Dao
interface AutomationActionDao {
    @Query("SELECT * FROM automation_actions WHERE host_id = :hostId ORDER BY position, id")
    fun observeByHost(hostId: Long): Flow<List<AutomationAction>>

    @Query("SELECT * FROM automation_actions WHERE host_id = :hostId ORDER BY position, id")
    suspend fun getByHost(hostId: Long): List<AutomationAction>

    @Insert
    suspend fun insert(actions: List<AutomationAction>)

    @Query("DELETE FROM automation_actions WHERE host_id = :hostId")
    suspend fun deleteByHost(hostId: Long)

    @Transaction
    suspend fun replace(hostId: Long, actions: List<AutomationAction>) {
        require(actions.all { it.hostId == hostId && UUID.fromString(it.id).toString() == it.id })
        require(actions.map { it.id }.distinct().size == actions.size)
        deleteByHost(hostId)
        insert(actions.mapIndexed { position, action -> action.copy(position = position) })
    }
}
