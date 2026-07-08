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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.connectbot.data.dao.KeyboardLayoutDao
import org.connectbot.data.entity.KeyboardLayout
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.keyboard.DefaultKeyboardLayouts
import org.connectbot.keyboard.KeyboardLayoutJson
import org.connectbot.keyboard.KeyboardLayoutSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user-defined keyboard layouts. Built-in layouts (negative IDs)
 * are virtual and resolved from [DefaultKeyboardLayouts]; only custom layouts
 * live in the database.
 */
@Singleton
class KeyboardLayoutRepository @Inject constructor(
    private val dao: KeyboardLayoutDao,
    private val dispatchers: CoroutineDispatchers,
) {
    fun observeAll(): Flow<List<KeyboardLayout>> = dao.observeAll()

    /**
     * Resolve the effective key layout for a stored id: null / the built-in IDs
     * map to their built-ins; a missing or undecodable custom row falls back to
     * the default.
     */
    fun observeResolvedSpec(layoutId: Long?): Flow<KeyboardLayoutSpec> {
        if (layoutId == null) return flowOf(DefaultKeyboardLayouts.default)
        DefaultKeyboardLayouts.byId(layoutId)?.let { return flowOf(it) }
        return dao.observeById(layoutId).map { entity ->
            entity?.keysJson?.let { KeyboardLayoutJson.decode(it) } ?: DefaultKeyboardLayouts.default
        }
    }

    /** One-shot resolution, for non-Flow callers. */
    suspend fun resolveSpec(layoutId: Long?): KeyboardLayoutSpec = withContext(dispatchers.io) {
        if (layoutId == null) return@withContext DefaultKeyboardLayouts.default
        DefaultKeyboardLayouts.byId(layoutId)?.let { return@withContext it }
        val entity = dao.getById(layoutId)
        entity?.keysJson?.let { KeyboardLayoutJson.decode(it) } ?: DefaultKeyboardLayouts.default
    }

    suspend fun getById(layoutId: Long): KeyboardLayout? = withContext(dispatchers.io) {
        dao.getById(layoutId)
    }

    suspend fun getAll(): List<KeyboardLayout> = withContext(dispatchers.io) {
        dao.getAll()
    }

    /** Create a new custom layout from a spec; returns the new row id. */
    suspend fun create(name: String, spec: KeyboardLayoutSpec): Long = withContext(dispatchers.io) {
        dao.insert(KeyboardLayout(name = name, keysJson = KeyboardLayoutJson.encode(spec)))
    }

    suspend fun updateSpec(layoutId: Long, spec: KeyboardLayoutSpec) = withContext(dispatchers.io) {
        val existing = dao.getById(layoutId) ?: return@withContext
        dao.update(existing.copy(keysJson = KeyboardLayoutJson.encode(spec)))
    }

    suspend fun rename(layoutId: Long, name: String) = withContext(dispatchers.io) {
        val existing = dao.getById(layoutId) ?: return@withContext
        dao.update(existing.copy(name = name))
    }

    /**
     * Delete a custom layout, first clearing any host overrides that point at it.
     * Returns true if a row was removed.
     */
    suspend fun delete(layoutId: Long): Boolean = withContext(dispatchers.io) {
        dao.clearHostReferences(layoutId)
        dao.deleteById(layoutId) > 0
    }

    suspend fun nameExists(name: String, excludeLayoutId: Long? = null): Boolean = withContext(dispatchers.io) {
        dao.nameExists(name, excludeLayoutId)
    }

    suspend fun countHostsUsing(layoutId: Long): Int = withContext(dispatchers.io) {
        dao.countHostsUsing(layoutId)
    }
}
