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
import org.connectbot.data.DatabaseSchema
import org.connectbot.data.HostConfigJson
import org.connectbot.data.HostRepository
import org.connectbot.data.PubkeyRepository
import org.connectbot.data.SchemaBasedExporter
import org.connectbot.data.entity.Profile
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.service.BackupFilter
import org.connectbot.util.PreferenceConstants
import org.json.JSONObject
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

    @Test fun sharedMacroEditsUpdateLabelledShortcutButtonsAsWell() = runTest {
        val layout = KeyboardLayout(name = "Shortcuts")
        val macro = KeyboardMacro(name = "Enter", document = MacroFormat.encode(listOf(MacroAction.Key(key = "enter"))))
        val button = KeyboardItem(layoutId = layout.id, target = "enter", macroId = macro.id)
        repository.save(layout, listOf(button), listOf(macro))
        val modified = macro.copy(document = MacroFormat.encode(listOf(MacroAction.Key(key = "enter", modifiers = listOf("ctrl")))))
        repository.saveMacro(modified)
        assertEquals("key", db.keyboardDao().items().single().kind)
        val general = macro.copy(name = "Deploy", document = MacroFormat.encode(listOf(MacroAction.Text("deploy"), MacroAction.Key(key = "enter"))))
        repository.saveMacro(general)
        val updated = db.keyboardDao().items().single()
        assertEquals(button.id, updated.id)
        assertEquals("macro", updated.kind)
        assertEquals("", updated.target)
        assertEquals(macro.id, updated.macroId)
        assertEquals(general, db.keyboardDao().macros().single())
    }

    @Test fun hostOverrideCommitsWithDraftAndRejectsMissingHostWithoutOrphans() = runTest {
        val host = org.connectbot.data.entity.Host(nickname = "Override", hostname = "host")
        db.hostDao().insert(host)
        val layout = KeyboardLayout(name = "Private")
        val draft = KeyboardDraft(layout, KeyboardDefaults.items(layout.id))
        repository.setHostLayout(host.id, layout.id, draft)
        assertEquals(layout.id, db.hostDao().getById(host.id)!!.keyboardLayoutId)
        assertEquals(layout, db.keyboardDao().layouts().single())
        val orphan = draft.duplicate("Must not remain")
        assertTrue(runCatching { repository.setHostLayout(UUID.randomUUID(), orphan.layout.id, orphan) }.isFailure)
        assertEquals(listOf(layout), db.keyboardDao().layouts())
        repository.deleteLayout(layout.id)
        assertNull(db.hostDao().getById(host.id)!!.keyboardLayoutId)
    }

    @Test fun parentFailureRollsBackLayoutMacroHostAndAutomationTogether() = runTest {
        val layout = KeyboardLayout(name = "Pending")
        val macro = KeyboardMacro(name = "Pending", document = MacroFormat.encode(listOf(MacroAction.Text("run"))))
        val draft = KeyboardDraft(layout, listOf(KeyboardItem(layoutId = layout.id, macroId = macro.id)), listOf(macro))
        assertTrue(
            runCatching {
                repository.commitDrafts(listOf(draft)) {
                    val host = org.connectbot.data.entity.Host(nickname = "Pending", keyboardLayoutId = layout.id)
                    db.hostDao().insert(host)
                    db.automationActionDao().replace(host.id, listOf(org.connectbot.data.entity.AutomationAction(hostId = host.id, macroId = macro.id)))
                    error("Parent save failed")
                }
            }.isFailure,
        )
        assertTrue(db.keyboardDao().layouts().isEmpty())
        assertTrue(db.keyboardDao().macros().isEmpty())
        assertTrue(db.hostDao().getAll().isEmpty())
    }

    @Test fun copyRetainsSharedMacrosAndReportsBothKindsOfUsage() = runTest {
        val macro = KeyboardMacro(name = "Shared", document = MacroFormat.encode(listOf(MacroAction.Text("run"))))
        repository.saveMacro(macro)
        val layout = KeyboardLayout(name = "Base")
        val item = KeyboardItem(layoutId = layout.id, kind = "macro", macroId = macro.id)
        repository.save(layout, listOf(item))
        val copy = KeyboardDraft(layout, listOf(item)).duplicate("Custom")
        assertNotEquals(layout.id, copy.layout.id)
        assertNotEquals(item.id, copy.items.single().id)
        assertEquals(macro.id, copy.items.single().macroId)
        assertTrue(copy.macros.isEmpty())
        val host = org.connectbot.data.entity.Host(nickname = "Automation")
        db.hostDao().insert(host)
        db.automationActionDao().replace(host.id, listOf(org.connectbot.data.entity.AutomationAction(hostId = host.id, macroId = macro.id)))
        assertEquals(setOf("key", "automation"), repository.macroUsage(macro.id).first().map { it.kind }.toSet())
    }

    @Test fun sessionSelectionIsIndependentForEveryConnection() {
        val first = Any()
        val second = Any()
        val id = UUID.randomUUID()
        repository.sessionSelection(first).value = id
        assertEquals(id, repository.sessionSelection(first).value)
        assertNull(repository.sessionSelection(second).value)
        repository.sessionSelection(first).value = null
        assertNull(repository.sessionSelection(first).value)
    }

    @Test fun sharedMacroReferencesAndJson5BackupSurviveRestore() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val macro = KeyboardMacro(name = "Shared", document = MacroFormat.encode(listOf(MacroAction.Text("run"))))
        repository.saveMacro(macro)
        val layout = KeyboardLayout(name = "Shared")
        val button = KeyboardItem(layoutId = layout.id, kind = "macro", macroId = macro.id)
        repository.save(layout, listOf(button))
        val host = org.connectbot.data.entity.Host(nickname = "Shared", hostname = "host", protocol = "ssh", keyboardLayoutId = layout.id)
        db.hostDao().insert(host)
        val action = org.connectbot.data.entity.AutomationAction(hostId = host.id, type = org.connectbot.data.entity.AutomationActionType.RUN_MACRO, macroId = macro.id)
        db.automationActionDao().replace(host.id, listOf(action))
        assertTrue(runCatching { repository.deleteMacro(macro.id) }.isFailure)
        val json = HostConfigJson.exportToJson(context, db).first
        val target = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        try {
            HostConfigJson.importFromJson(context, target, "/* JSON5 backup */" + json.dropLast(1) + ",}")
            val restoredHost = target.hostDao().getAll().single()
            assertEquals(layout.id, restoredHost.keyboardLayoutId)
            assertEquals(macro.id, target.keyboardDao().items().single().macroId)
            assertEquals(macro, target.keyboardDao().macros().single())
            assertEquals(macro.id, target.automationActionDao().getByHost(restoredHost.id).single().macroId)
        } finally {
            target.close()
        }
        db.keyboardDao().deleteItems(layout.id)
        assertTrue(runCatching { repository.deleteMacro(macro.id) }.isFailure)
        db.automationActionDao().deleteByHost(host.id)
        repository.deleteMacro(macro.id)
        assertTrue(db.keyboardDao().macros().isEmpty())
    }

    @Test fun individualMacroImportExportDoesNotOverwriteExistingIdentity() = runTest {
        val original = KeyboardMacro(name = "Portable", document = MacroFormat.encode(listOf(MacroAction.Text("hello"))))
        repository.saveMacro(original)
        val output = java.io.ByteArrayOutputStream()
        repository.exportMacro(original.id) { output }
        repository.importMacro { java.io.ByteArrayInputStream(output.toByteArray()) }
        val macros = db.keyboardDao().macros()
        assertEquals(2, macros.size)
        assertEquals(2, macros.map { it.id }.distinct().size)
        assertTrue(macros.all { it.name == original.name && it.document == original.document })
    }

    @Test fun labelledKeyCombinationIsSavedWithLayout() = runTest {
        val layout = KeyboardLayout(name = "Shortcuts")
        val action = MacroAction.Key(key = "character_97", modifiers = listOf("ctrl"))
        val macro = KeyboardMacro(name = "Select all", document = MacroFormat.encode(listOf(action)))
        val button = KeyboardItem(layoutId = layout.id, kind = "key", target = "character_97", macroId = macro.id)
        repository.save(layout, listOf(button), listOf(macro))
        val restored = db.keyboardDao().macros().single()
        assertEquals("Select all", restored.name)
        assertEquals(listOf(action), MacroFormat.decode(restored.document))
        assertEquals(button, db.keyboardDao().items().single())
    }

    @Test fun rejectsInvalidButtonAppearance() = runTest {
        val layout = KeyboardLayout(name = "Invalid appearance")
        val item = KeyboardItem(layoutId = layout.id)
        listOf(
            item.copy(backgroundOpacity = Float.NaN),
            item.copy(backgroundOpacity = -0.1f),
            item.copy(backgroundOpacity = 1.1f),
            item.copy(outlineWidth = Float.POSITIVE_INFINITY),
            item.copy(outlineWidth = -1f),
            item.copy(outlineWidth = 11f),
        ).forEach { invalid -> assertTrue(runCatching { repository.save(layout, listOf(invalid)) }.isFailure) }
        assertTrue(db.keyboardDao().items().isEmpty())
    }

    @Test fun uuidIdentitySizesAndSpansSurviveRoundTrip() = runTest {
        val layout = KeyboardLayout(name = "Wide", buttonWidth = 60f, buttonHeight = 48f)
        val item = KeyboardItem(layoutId = layout.id, columnSpan = 2, backgroundColor = 0xFF123456.toInt(), backgroundOpacity = 0.4f, textColor = 0xFFFFFFFF.toInt(), outlineColor = 0xFF00FF00.toInt(), outlineWidth = 2.5f, repeatOnHold = true, hapticFeedback = false, pinned = true)
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

    @Test fun changedKeyboardRecordsWithExistingUuidsAreSkipped() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KeyboardDefaults.seed(db.openHelper.writableDatabase)
        val layout = db.keyboardDao().layouts().single()
        val item = db.keyboardDao().items().first()
        val macro = KeyboardMacro(name = "Original", document = MacroFormat.encode(listOf(MacroAction.Text("original"))))
        db.keyboardDao().saveMacro(macro)
        val target = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        try {
            KeyboardDefaults.seed(target.openHelper.writableDatabase)
            target.keyboardDao().saveMacro(macro)
            db.keyboardDao().saveLayout(layout.copy(name = "Edited", buttonWidth = 64f))
            db.keyboardDao().saveItems(listOf(item.copy(visible = false, columnSpan = 2)))
            db.keyboardDao().saveMacro(macro.copy(name = "Edited", document = MacroFormat.encode(listOf(MacroAction.Text("edited")))))
            db.profileDao().insert(Profile(name = "Imported", keyboardLayoutId = layout.id))
            val json = HostConfigJson.exportToJson(context, db).first
            repeat(2) { HostConfigJson.importFromJson(context, target, json) }
            assertEquals(listOf(layout), target.keyboardDao().layouts())
            assertEquals(item, target.keyboardDao().items().first { it.id == item.id })
            assertEquals(listOf(macro), target.keyboardDao().macros())
            assertEquals(layout.id, target.profileDao().getAll().single().keyboardLayoutId)
            assertEquals(KeyboardDefaults.layoutId, target.keyboardDao().settings()!!.defaultLayoutId)
        } finally {
            target.close()
        }
    }

    @Test fun identicalKeyboardContentsWithDistinctUuidsKeepTheirIdentities() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layout = KeyboardLayout(name = "Same")
        val macro = KeyboardMacro(name = "Same", document = MacroFormat.encode(listOf(MacroAction.Text("x"))))
        val item = KeyboardItem(layoutId = layout.id, kind = "macro", macroId = macro.id)
        db.keyboardDao().saveLayout(layout)
        db.keyboardDao().saveMacro(macro)
        db.keyboardDao().saveItems(listOf(item))
        val json = HostConfigJson.exportToJson(context, db).first
        val target = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        try {
            val otherLayout = layout.copy(id = UUID.randomUUID())
            val otherMacro = macro.copy(id = UUID.randomUUID())
            target.keyboardDao().saveLayout(otherLayout)
            target.keyboardDao().saveMacro(otherMacro)
            HostConfigJson.importFromJson(context, target, json)
            assertEquals(setOf(layout, otherLayout), target.keyboardDao().layouts().toSet())
            assertEquals(setOf(macro, otherMacro), target.keyboardDao().macros().toSet())
            assertEquals(listOf(item), target.keyboardDao().items())
            // Equal contents are not identity, including for buttons in the same layout.
            target.keyboardDao().saveItems(listOf(item.copy(id = UUID.randomUUID())))
            HostConfigJson.importFromJson(context, target, json)
            assertEquals(2, target.keyboardDao().items().size)
        } finally {
            target.close()
        }
    }

    @Test fun malformedKeyboardUuidIsRejectedEvenForAnExistingRecord() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val layout = KeyboardLayout(id = UUID.fromString("abcdefab-1234-4123-8123-123456789abc"), name = "Invalid import")
        db.keyboardDao().saveLayout(layout)
        val exporter = SchemaBasedExporter(db, DatabaseSchema.load(context))
        val original = JSONObject(HostConfigJson.exportToJson(context, db).first)
        listOf("invalid", layout.id.toString().uppercase()).forEach { invalid ->
            val json = JSONObject(original.toString())
            json.getJSONArray("keyboard_layouts").getJSONObject(0).put("id", invalid)
            assertTrue(runCatching { exporter.importFromJson(json.toString(), HostConfigJson.EXPORT_TABLES) }.isFailure)
        }
        assertEquals(listOf(layout), db.keyboardDao().layouts())
    }

    @Test fun defaultsOmitOptionalTextInputWithoutChangingOtherButtonUuids() {
        val items = KeyboardDefaults.items(KeyboardDefaults.layoutId)
        assertTrue(items.none { it.target == "text_input" })
        assertTrue("text_input" in KeyboardDefaults.controls)
        val scrolling = items.filterNot { it.pinned }
        assertEquals(scrolling.indices.toList(), scrolling.map { it.column })
        assertEquals(listOf(0, 1), items.filter { it.pinned }.map { it.column })
        listOf("toggle_compose" to 25, "toggle_ime" to 26).forEach { (target, identityIndex) ->
            val expected = UUID.nameUUIDFromBytes("${KeyboardDefaults.layoutId}/$identityIndex".toByteArray(Charsets.UTF_8))
            assertEquals(expected, items.single { it.target == target }.id)
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

    @Test fun fittedLayeredPresetSurvivesSaveDuplicationAndExport() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val draft = KeyboardPresets.create(KeyboardPreset.SCRIBE, "My Scribe", context::getString)
        val items = draft.items.map { item ->
            val styled = item.copy(backgroundColor = 0xFF123456.toInt(), backgroundOpacity = 0.4f, textColor = 0xFFFFFFFF.toInt(), outlineColor = 0xFF00FF00.toInt(), outlineWidth = 0f, repeatOnHold = false, hapticFeedback = true, pinned = item.target == "escape")
            if (styled.kind == "layer") styled.copy(target = if (styled.layer == 0) "1" else "0") else styled
        }
        repository.save(draft.layout, items, draft.macros)
        repository.setDefault(draft.layout.id)
        val copyId = repository.duplicate(draft.layout, items)
        val copy = db.keyboardDao().layouts().single { it.id == copyId }
        assertEquals(draft.layout.copy(id = copyId), copy)
        assertEquals(draft.layout.id, db.keyboardDao().settings()!!.defaultLayoutId)
        assertEquals(items.groupingBy { it.layer }.eachCount(), db.keyboardDao().items().filter { it.layoutId == copyId }.groupingBy { it.layer }.eachCount())
        val json = HostConfigJson.exportToJson(context, db).first
        val target = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        try {
            HostConfigJson.importFromJson(context, target, json)
            assertEquals(db.keyboardDao().layouts().toSet(), target.keyboardDao().layouts().toSet())
            assertEquals(db.keyboardDao().items().toSet(), target.keyboardDao().items().toSet())
            assertEquals(draft.macros.toSet(), target.keyboardDao().macros().toSet())
            val older = JSONObject(json)
            val layouts = older.getJSONArray("keyboard_layouts")
            for (index in 0 until layouts.length()) {
                layouts.getJSONObject(index).remove("fitToWidth")
                layouts.getJSONObject(index).remove("presetKey")
            }
            val buttons = older.getJSONArray("keyboard_items")
            for (index in 0 until buttons.length()) {
                val button = buttons.getJSONObject(index)
                button.remove("layer")
                listOf("backgroundColor", "backgroundOpacity", "textColor", "outlineColor", "outlineWidth", "repeatOnHold", "hapticFeedback", "pinned").forEach { button.remove(it) }
            }
            // Older exports can omit these fields; SQLite column defaults preserve compatibility.
            target.clearAllTables()
            HostConfigJson.importFromJson(context, target, older.toString())
            assertTrue(target.keyboardDao().layouts().all { !it.fitToWidth && it.presetKey == null })
            assertTrue(target.keyboardDao().items().all { it.layer == 0 && it.backgroundColor == null && it.backgroundOpacity == null && it.textColor == null && it.outlineColor == null && it.outlineWidth == null && it.repeatOnHold == null && it.hapticFeedback == null && !it.pinned })
        } finally {
            target.close()
        }
    }
}
