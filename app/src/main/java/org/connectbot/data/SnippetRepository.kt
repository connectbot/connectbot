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

package org.connectbot.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.connectbot.data.dao.SnippetDao
import org.connectbot.data.entity.Snippet
import org.connectbot.di.CoroutineDispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing command snippets.
 * Snippets are reusable commands that can be sent to a terminal with a tap,
 * either globally available or scoped to a single host.
 *
 * @param snippetDao The DAO for accessing snippet data
 */
@Singleton
class SnippetRepository @Inject constructor(
    private val snippetDao: SnippetDao,
    private val dispatchers: CoroutineDispatchers,
) {
    /**
     * Observe all snippets.
     */
    fun observeAll(): Flow<List<Snippet>> = snippetDao.observeAll()

    /**
     * Observe the snippets available for a host (global + host-scoped).
     */
    fun observeForHost(hostId: Long): Flow<List<Snippet>> = snippetDao.observeForHost(hostId)

    /**
     * Get a snippet by ID.
     */
    suspend fun getById(snippetId: Long): Snippet? = withContext(dispatchers.io) {
        snippetDao.getById(snippetId)
    }

    /**
     * Save a snippet (insert or update).
     *
     * @return The ID of the saved snippet
     */
    suspend fun save(snippet: Snippet): Long = withContext(dispatchers.io) {
        if (snippet.id == 0L) {
            snippetDao.insert(snippet)
        } else {
            snippetDao.update(snippet)
            snippet.id
        }
    }

    /**
     * Delete a snippet by ID.
     *
     * @return true if deleted, false if not found
     */
    suspend fun delete(snippetId: Long): Boolean = withContext(dispatchers.io) {
        snippetDao.deleteById(snippetId) > 0
    }
}
