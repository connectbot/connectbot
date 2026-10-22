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

package org.connectbot.data.keyboard

import android.content.SharedPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.EntityIds
import org.connectbot.data.JsonImportReader
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.util.PreferenceConstants
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyboardRepository @Inject constructor(private val database: ConnectBotDatabase, private val dispatchers: CoroutineDispatchers, private val preferences: SharedPreferences) {
    private val dao get() = database.keyboardDao()
    private val legacyMigrationMutex = Mutex()
    private val sessionSelections = java.util.WeakHashMap<Any, MutableStateFlow<UUID?>>()

    @Synchronized
    fun sessionSelection(session: Any): MutableStateFlow<UUID?> = sessionSelections.getOrPut(session) { MutableStateFlow(null) }

    fun macroUsage(id: UUID) = dao.observeMacroUsage(id)
    fun layoutUsage(id: UUID) = dao.observeLayoutUsage(id)

    suspend fun canEditHost(id: UUID): Boolean = withContext(dispatchers.io) {
        val host = database.hostDao().getById(id) ?: return@withContext false
        if (host.repositoryId == EntityIds.LOCAL) return@withContext true
        database.openHelper.readableDatabase.query("SELECT role FROM sync_repositories WHERE id=?", arrayOf(host.repositoryId.toString())).use {
            it.moveToFirst() && it.getString(0) != "reader"
        }
    }

    suspend fun setHostLayout(hostId: UUID, layoutId: UUID?, draft: KeyboardDraft? = null) = withContext(dispatchers.io) {
        database.withTransaction {
            require(canEditHost(hostId)) { "This host is read-only" }
            require(draft == null || draft.layout.id == layoutId)
            draft?.let { saveDraft(it) }
            check(dao.setHostLayout(hostId, layoutId) == 1) { "Host no longer exists" }
        }
    }

    suspend fun <T> commitDrafts(drafts: List<KeyboardDraft>, macros: List<KeyboardMacro> = emptyList(), commit: suspend () -> T): T = withContext(dispatchers.io) {
        database.withTransaction {
            macros.forEach { saveMacro(it) }
            drafts.forEach { saveDraft(it) }
            commit()
        }
    }
    val configuration = combine(dao.observeLayouts(), dao.observeItems(), dao.observeMacros(), dao.observeSettings()) { layouts, items, macros, settings ->
        KeyboardConfiguration(layouts, items, macros, settings)
    }.map { configuration ->
        configuration.copy(unsupportedMacroIds = configuration.macros.filter { runCatching { MacroFormat.decode(it.document) }.isFailure }.map { it.id }.toSet())
    }.onStart { migrateLegacyComposeVisibility() }.flowOn(dispatchers.default)

    private suspend fun migrateLegacyComposeVisibility() = withContext(dispatchers.io) {
        legacyMigrationMutex.withLock {
            if (preferences.contains(PreferenceConstants.IME_TOGGLE_KEY)) {
                val visible = preferences.getBoolean(PreferenceConstants.IME_TOGGLE_KEY, true)
                database.withTransaction {
                    val item = dao.items().find { it.layoutId == KeyboardDefaults.layoutId && it.kind == "app" && it.target == "toggle_compose" }
                    if (item != null) dao.saveItems(listOf(item.copy(visible = visible)))
                }
                preferences.edit().remove(PreferenceConstants.IME_TOGGLE_KEY).commit()
            }
        }
    }

    suspend fun save(layout: KeyboardLayout, items: List<KeyboardItem>, macros: List<KeyboardMacro> = emptyList()) = withContext(dispatchers.io) {
        KeyboardDefaults.validate(layout, items)
        macros.forEach { macro ->
            require(macro.name.isNotBlank()) { "A macro needs a name" }
            MacroFormat.decode(macro.document)
        }
        database.withTransaction {
            dao.saveLayout(layout)
            macros.forEach { dao.saveMacro(it) }
            dao.deleteItems(layout.id)
            dao.saveItems(items)
        }
    }

    suspend fun saveDraft(draft: KeyboardDraft) = save(draft.layout, draft.items, draft.macros.filter { macro -> draft.items.any { it.macroId == macro.id } })

    suspend fun create(name: String): UUID {
        val layout = KeyboardLayout(name = name)
        save(layout, KeyboardDefaults.items(layout.id))
        return layout.id
    }

    suspend fun duplicate(layout: KeyboardLayout, items: List<KeyboardItem>): UUID {
        val copy = layout.copy(id = UUID.randomUUID())
        save(copy, items.map { it.copy(id = UUID.randomUUID(), layoutId = copy.id) })
        return copy.id
    }

    suspend fun setDefault(id: UUID) = withContext(dispatchers.io) { dao.saveSettings(KeyboardSettings(defaultLayoutId = id)) }

    suspend fun deleteLayout(id: UUID) = withContext(dispatchers.io) {
        database.withTransaction {
            dao.deleteLayout(id)
            var remaining = dao.layouts()
            if (remaining.isEmpty()) {
                val layout = KeyboardLayout(id = KeyboardDefaults.layoutId, name = "Classic CB")
                dao.saveLayout(layout)
                dao.saveItems(KeyboardDefaults.items(layout.id))
                remaining = listOf(layout)
            }
            if (dao.settings()?.defaultLayoutId == null) dao.saveSettings(KeyboardSettings(defaultLayoutId = remaining.first().id))
        }
    }

    suspend fun saveMacro(macro: KeyboardMacro) = withContext(dispatchers.io) {
        require(macro.name.isNotBlank()) { "A macro needs a name" }
        val shortcut = MacroFormat.decode(macro.document).singleOrNull() as? MacroAction.Key
        database.withTransaction {
            dao.saveMacro(macro)
            // Labelled shortcuts also reference this library. If a shared edit changes
            // their action, run the edited macro rather than the obsolete key target.
            val changed = dao.items().filter { it.macroId == macro.id && it.kind == "key" && it.target != shortcut?.key }
            if (changed.isNotEmpty()) dao.saveItems(changed.map { it.copy(kind = "macro", target = "") })
        }
    }

    suspend fun readMacro(open: () -> InputStream?): KeyboardMacro = withContext(dispatchers.io) {
        val source = requireNotNull(open()) { "Unable to open macro file" }.use(JsonImportReader::read)
        MacroFileFormat.decode(source)
    }

    suspend fun importMacro(open: () -> InputStream?) {
        saveMacro(readMacro(open))
    }

    suspend fun exportMacro(id: UUID, open: () -> OutputStream?) = withContext(dispatchers.io) {
        val macro = requireNotNull(dao.macros().find { it.id == id }) { "Macro no longer exists" }
        val source = MacroFileFormat.encode(macro)
        requireNotNull(open()) { "Unable to create macro file" }.use { it.write(source.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun deleteMacro(id: UUID) = withContext(dispatchers.io) {
        database.withTransaction {
            require(dao.items().none { it.macroId == id }) { "Remove or reassign buttons using this macro first" }
            require(database.automationActionDao().macroUsageCount(id) == 0) { "Remove or reassign post-login actions using this macro first" }
            dao.deleteMacro(id)
        }
    }
}
