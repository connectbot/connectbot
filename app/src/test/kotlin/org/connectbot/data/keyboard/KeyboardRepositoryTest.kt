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

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.connectbot.data.ColorSchemeRepository
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.HostConfigJson
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.BackupFilter
import org.connectbot.util.PreferenceConstants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class KeyboardRepositoryTest {
    private lateinit var db: ConnectBotDatabase
    private lateinit var repository: KeyboardRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        repository = KeyboardRepository(db, CoroutineDispatchers(Dispatchers.Default, Dispatchers.IO, Dispatchers.Main), ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("keyboard-test", Context.MODE_PRIVATE).also { it.edit().clear().commit() })
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun uuidIdentitySizesAndSpansSurviveRoundTrip() = runTest {
        val layout = KeyboardLayout(name = "Wide", buttonWidth = 60f, buttonHeight = 48f)
        val item = KeyboardItem(layoutId = layout.id, columnSpan = 2)
        repository.save(layout, listOf(item))
        repository.setDefault(layout.id)
        assertEquals(layout, db.keyboardDao().layouts().single())
        assertEquals(item, db.keyboardDao().items().single())
        assertEquals(layout, repository.configuration.first { it.layouts.isNotEmpty() }.layout(null))
        db.openHelper.readableDatabase.query("SELECT id, typeof(id) FROM keyboard_layouts").use {
            assertTrue(it.moveToFirst())
            assertEquals(layout.id.toString(), it.getString(0))
            assertEquals("text", it.getString(1))
        }
    }

    @Test fun resizingOneLayoutDoesNotChangeItemsOrOtherLayouts() = runTest {
        val first = KeyboardLayout(name = "First")
        val second = KeyboardLayout(name = "Second")
        val item = KeyboardItem(layoutId = first.id, columnSpan = 3)
        repository.save(first, listOf(item))
        repository.save(second, emptyList())
        repository.save(first.copy(buttonWidth = 72f, buttonHeight = 54f), listOf(item))
        assertEquals(item, db.keyboardDao().items().single())
        assertEquals(second, db.keyboardDao().layouts().first { it.id == second.id })
    }

    @Test fun duplicateChangesIdentityButSharesMacroAndDimensions() = runTest {
        val macro = KeyboardMacro(name = "Prefix", document = MacroFormat.encode(listOf(MacroAction.Text("x"))))
        repository.saveMacro(macro)
        val layout = KeyboardLayout(name = "Original", buttonWidth = 70f)
        val item = KeyboardItem(layoutId = layout.id, kind = "macro", target = "", macroId = macro.id)
        repository.save(layout, listOf(item))
        val duplicateId = repository.duplicate(layout, listOf(item))
        assertNotEquals(layout.id, duplicateId)
        val copy = db.keyboardDao().items().first { it.layoutId == duplicateId }
        assertNotEquals(item.id, copy.id)
        assertEquals(macro.id, copy.macroId)
        assertEquals(70f, db.keyboardDao().layouts().first { it.id == duplicateId }.buttonWidth)
        assertTrue(runCatching { repository.deleteMacro(macro.id) }.isFailure)
    }

    @Test fun deletingSelectedLayoutClearsProfileAndCreatesFallback() = runTest {
        val id = repository.create("Only")
        repository.setDefault(id)
        val profileId = db.profileDao().insert(Profile(name = "Profile", keyboardLayoutId = id))
        repository.deleteLayout(id)
        assertNull(db.profileDao().getById(profileId)!!.keyboardLayoutId)
        assertNotNull(db.keyboardDao().settings()!!.defaultLayoutId)
        assertEquals(1, db.keyboardDao().layouts().size)
    }

    @Test fun invalidGridCannotReplaceSavedConfiguration() = runTest {
        val layout = KeyboardLayout(name = "Valid")
        val item = KeyboardItem(layoutId = layout.id, visible = false)
        repository.save(layout, listOf(item))
        assertTrue(runCatching { repository.save(layout, listOf(item, item.copy(id = UUID.randomUUID()))) }.isFailure)
        assertEquals(listOf(item), db.keyboardDao().items())
        assertTrue(runCatching { repository.save(layout.copy(buttonHeight = Float.NaN), listOf(item)) }.isFailure)
    }

    @Test fun exportImportPreservesUuidReferencesAndLegacyNumericProfiles() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layout = KeyboardLayout(name = "Export", buttonWidth = 64f, buttonHeight = 52f)
        val macro = KeyboardMacro(name = "Macro", document = MacroFormat.encode(listOf(MacroAction.Key(key = "enter"))))
        repository.saveMacro(macro)
        val item = KeyboardItem(layoutId = layout.id, kind = "macro", macroId = macro.id)
        repository.save(layout, listOf(item))
        db.profileDao().insert(Profile(name = "Source", keyboardLayoutId = layout.id))
        val json = HostConfigJson.exportToJson(context, db).first
        val target = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        try {
            target.profileDao().insert(Profile(name = "Other"))
            HostConfigJson.importFromJson(context, target, json)
            HostConfigJson.importFromJson(context, target, json)
            assertEquals(listOf(layout), target.keyboardDao().layouts())
            assertEquals(listOf(item), target.keyboardDao().items())
            assertEquals(listOf(macro), target.keyboardDao().macros())
            val profile = target.profileDao().getAll().first { it.name == "Source" }
            assertEquals(layout.id, profile.keyboardLayoutId)
            assertNotEquals(1L, profile.id)
        } finally {
            target.close()
        }
    }

    @Test fun filteredBackupIncludesUuidConfigurationAndProfileReferences() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = repository.create("Backup")
        val futureMacro = KeyboardMacro(name = "Future", document = """{"version":2,"actions":[{"type":"future"}]}""")
        db.keyboardDao().saveMacro(futureMacro)
        db.keyboardDao().saveItems(listOf(KeyboardItem(layoutId = id, row = 1, kind = "macro", macroId = futureMacro.id)))
        repository.setDefault(id)
        val profileId = db.profileDao().insert(Profile(name = "Backup profile", keyboardLayoutId = id))
        val hosts = Mockito.mock(HostRepository::class.java)
        val colors = Mockito.mock(ColorSchemeRepository::class.java)
        val pubkeys = Mockito.mock(PubkeyRepository::class.java)
        Mockito.`when`(hosts.getHosts()).thenReturn(emptyList())
        Mockito.`when`(colors.getAllSchemes()).thenReturn(emptyList())
        val file = context.getDatabasePath("keyboard-filtered-backup")
        context.deleteDatabase(file.name)
        BackupFilter(context, hosts, colors, pubkeys, db).buildFilteredDatabase(file, false)
        val backup = Room.databaseBuilder(context, ConnectBotDatabase::class.java, file.name).build()
        try {
            assertEquals(db.keyboardDao().layouts(), backup.keyboardDao().layouts())
            assertEquals(db.keyboardDao().items().sortedBy { it.column }, backup.keyboardDao().items().sortedBy { it.column })
            assertEquals(listOf(futureMacro), backup.keyboardDao().macros())
            assertEquals(id, backup.keyboardDao().settings()!!.defaultLayoutId)
            assertEquals(id, backup.profileDao().getById(profileId)!!.keyboardLayoutId)
        } finally {
            backup.close()
            context.deleteDatabase(file.name)
        }
    }

    @Test fun legacyComposeVisibilityMigratesOnceIntoRoom() = runTest {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("keyboard-test", Context.MODE_PRIVATE)
        KeyboardDefaults.seed(db.openHelper.writableDatabase)
        prefs.edit().putBoolean(PreferenceConstants.IME_TOGGLE_KEY, false).commit()
        repository.configuration.first()
        val compose = db.keyboardDao().items().first { it.target == "toggle_compose" }
        assertTrue(!compose.visible)
        assertTrue(!prefs.contains(PreferenceConstants.IME_TOGGLE_KEY))
        db.keyboardDao().saveItems(listOf(compose.copy(visible = true)))
        repository.configuration.first()
        assertTrue(db.keyboardDao().items().first { it.target == "toggle_compose" }.visible)
    }
}
