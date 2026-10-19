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

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Profile
import org.connectbot.data.entity.Pubkey
import org.connectbot.data.sync.SyncDatabase
import org.connectbot.di.CoroutineDispatchers
import org.connectbot.transport.TransportFactory
import org.connectbot.util.HostConstants
import org.connectbot.util.SecurePasswordStorage
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UuidRepositoryTest {
    private lateinit var database: ConnectBotDatabase
    private lateinit var hosts: HostRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var schemes: ColorSchemeRepository
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java).allowMainThreadQueries().build()
        hosts = HostRepository(context, database, database.hostDao(), database.portForwardDao(), database.knownHostDao(), mock(SecurePasswordStorage::class.java), dispatchers)
        profiles = ProfileRepository(database.profileDao(), dispatchers)
        schemes = ColorSchemeRepository(database.colorSchemeDao(), dispatchers)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun profileCreationAndDuplicationPreserveSettingsWithFreshIdentities() = runBlocking {
        val source = Profile(name = "Source", fontSize = 23, encoding = "ISO-8859-1", boldAsBright = false)
        profiles.save(source)
        val created = profiles.create("Based on source", source.id)
        val duplicate = profiles.duplicate(source.id, "Duplicate")
        assertEquals(source.copy(id = created, name = "Based on source"), profiles.getById(created))
        assertEquals(source.copy(id = duplicate, name = "Duplicate"), profiles.getById(duplicate))
        assertEquals(3, setOf(source.id, created, duplicate).size)
        assertEquals(source, profiles.getById(source.id))
        assertEquals(source, profiles.observeById(source.id).first())
        for (id in listOf(profiles.create("Default settings"), profiles.create("Missing base", UUID.randomUUID()), profiles.duplicate(UUID.randomUUID(), "Missing duplicate"))) {
            val saved = profiles.getById(id)!!
            assertEquals(Profile.createDefault().copy(id = id, name = saved.name), saved)
        }
    }

    @Test
    fun profileUpdatesDeletionAndBlockingFallbacksUseTheUuid() = runBlocking {
        val original = Profile(name = "Original")
        profiles.save(original)
        val updated = original.copy(name = "Updated")
        assertEquals(original.id, profiles.save(updated))
        assertEquals(updated, profiles.getByIdBlocking(original.id))
        assertEquals(updated, profiles.getByIdOrDefaultBlocking(original.id))
        assertTrue(profiles.nameExists("Updated"))
        assertFalse(profiles.nameExists("Updated", original.id))
        val host = hosts.saveHost(Host(nickname = "Uses profile", profileId = original.id))
        assertEquals(1, profiles.getHostsUsingProfile(original.id))
        assertEquals(host.profileId, original.id)
        assertEquals(EntityIds.DEFAULT_PROFILE, profiles.getByIdOrDefaultBlocking(null).id)
        assertEquals(EntityIds.DEFAULT_PROFILE, profiles.getByIdOrDefaultBlocking(UUID.randomUUID()).id)
        assertTrue(profiles.delete(original.id))
        assertFalse(profiles.delete(original.id))
        assertNull(profiles.getById(original.id))
    }

    @Test
    fun hostRedirectLookupHandlesMissingTableMissingMappingAndDeletedTargets() = runBlocking {
        val source = UUID.randomUUID()
        assertNull(hosts.findHostById(source))
        SyncDatabase.install(database.openHelper.writableDatabase)
        assertNull(hosts.findHostById(source))
        val target = hosts.saveHost(Host(nickname = "Moved host"))
        database.openHelper.writableDatabase.execSQL("INSERT INTO sync_host_redirects(old_id,new_id) VALUES(?,?)", arrayOf(source.toString(), target.id.toString()))
        assertEquals(target, hosts.findHostByIdBlocking(source))
        assertEquals(target, hosts.findHostById(target.id))
        database.hostDao().delete(target)
        assertNull(hosts.findHostById(source))
    }

    @Test
    fun hostSelectionDisambiguatesNicknameByHostnameAndUsername() = runBlocking {
        val first = hosts.saveHost(Host(nickname = "Same", hostname = "first", username = "alice"))
        val second = hosts.saveHost(Host(nickname = "Other", hostname = "second", username = "bob"))
        for (criteria in listOf(mapOf("nickname" to "Same"), mapOf("nickname" to "Same", "hostname" to "first"), mapOf("nickname" to "Same", "username" to "alice"), mapOf("nickname" to "Same", "hostname" to "first", "username" to "alice"))) {
            assertEquals(first, hosts.findHost(criteria))
        }
        for (criteria in listOf(mapOf("nickname" to "Same", "hostname" to "wrong"), mapOf("nickname" to "Same", "username" to "wrong"), mapOf("nickname" to "missing"))) {
            assertNull(hosts.findHost(criteria))
        }
        assertEquals(second, hosts.findHost(mapOf("nickname" to "Same", "protocol" to "ssh", "hostname" to "second", "username" to "bob", "port" to "22")))
    }

    @Test
    fun uuidHostLinksPreferIdentityAndFallBackToConnectionDetails() = runBlocking {
        val selected = hosts.saveHost(Host(nickname = "Selected", hostname = "selected.example", username = "alice"))
        val fallback = hosts.saveHost(Host(nickname = "Fallback", hostname = "fallback.example", username = "bob"))
        assertEquals(selected, TransportFactory.findHost(hosts, Uri.parse("ssh://bob@fallback.example:22?hostId=${selected.id}")))
        for (id in listOf(UUID.randomUUID().toString(), "invalid")) {
            assertEquals(fallback, TransportFactory.findHost(hosts, Uri.parse("ssh://bob@fallback.example:22?hostId=$id")))
        }
        assertEquals(fallback, TransportFactory.findHost(hosts, Uri.parse("ssh://bob@fallback.example:22")))
    }

    @Test
    fun missingSchemeRejectsPaletteWritesWithoutCreatingOrphans() = runBlocking {
        val missing = UUID.randomUUID()
        try {
            schemes.setColorForScheme(missing, 3, 123)
            fail("A palette entry must reference an existing scheme")
        } catch (expected: SQLiteConstraintException) {
            assertTrue(database.colorSchemeDao().getColors(missing).isEmpty())
        }
    }

    @Test
    fun hostAndPortForwardUpdatesPreserveIdentitiesAndRepositoryOwnership() = runBlocking {
        val host = Host(nickname = "Temporary", isTemporary = true)
        val saved = hosts.saveHost(host)
        assertFalse(saved.isTemporary)
        assertEquals(host.id, saved.id)
        hosts.touchHost(host)
        assertEquals(0L, hosts.findHostById(host.id)!!.lastConnect)
        hosts.touchHost(saved)
        assertTrue(hosts.findHostById(host.id)!!.lastConnect > 0)
        val renamed = saved.copy(nickname = "Saved")
        hosts.saveHost(renamed)
        assertEquals(renamed, hosts.observeHost(host.id).first())
        val forward = PortForward(hostId = host.id, nickname = "Forward", type = "local", sourcePort = 8080, destAddr = "localhost", destPort = 80, repositoryId = UUID.randomUUID())
        val inserted = hosts.savePortForward(forward)
        assertEquals(EntityIds.LOCAL, inserted.repositoryId)
        assertEquals(inserted, hosts.getPortForwardById(inserted.id))
        assertEquals(listOf(inserted), hosts.observePortForwardsForHost(host.id).first())
        val updated = inserted.copy(destPort = 443)
        assertEquals(updated, hosts.savePortForward(updated))
        assertEquals(updated, hosts.getPortForwardById(inserted.id))
        hosts.deletePortForward(updated)
        assertNull(hosts.getPortForwardById(inserted.id))
        try {
            hosts.savePortForward(forward.copy(hostId = UUID.randomUUID()))
            fail("A missing parent must reject the forward")
        } catch (expected: IllegalStateException) {
            assertEquals("Host no longer exists", expected.message)
        }
    }

    @Test
    fun knownHostOwnershipFallsBackToLocalForMissingOrReadOnlyRepositories() = runBlocking {
        SyncDatabase.install(database.openHelper.writableDatabase)
        for (role in listOf("editor", "reader", "missing")) {
            val repository = UUID.randomUUID()
            val host = hosts.saveHost(Host(nickname = role))
            if (role != "missing") {
                database.openHelper.writableDatabase.execSQL("INSERT INTO sync_repositories(id,name,owner_type,role) VALUES(?,?,?,?)", arrayOf(repository.toString(), role, "group", role))
            }
            val ownedHost = host.copy(repositoryId = repository)
            database.openHelper.writableDatabase.execSQL("UPDATE sync_control SET applying=1")
            database.hostDao().update(ownedHost)
            database.openHelper.writableDatabase.execSQL("UPDATE sync_control SET applying=0")
            hosts.saveKnownHostBlocking(ownedHost, "example", 22, "ssh-ed25519", byteArrayOf(1))
            val known = hosts.getKnownHostsForHostBlocking(host.id).single()
            assertEquals(if (role == "editor") repository else EntityIds.LOCAL, known.repositoryId)
        }
        val temporary = Host(nickname = "temporary", isTemporary = true)
        hosts.saveKnownHost(temporary, "example", 22, "ssh-ed25519", byteArrayOf(1))
        assertTrue(hosts.getKnownHostsForHost(temporary.id).isEmpty())
    }

    @Test
    fun pubkeySaveUpdateLookupAndObservationPreserveUuid() = runBlocking {
        val repository = PubkeyRepository(database.pubkeyDao())
        val key = Pubkey(nickname = "Key", type = "ssh-ed25519", privateKey = byteArrayOf(1), publicKey = byteArrayOf(2), encrypted = false, startup = false, confirmation = false, createdDate = 0)
        assertEquals(key, repository.save(key))
        assertEquals(key, repository.getById(key.id))
        assertEquals(key, repository.getByIdBlocking(key.id))
        assertEquals(key, repository.observePubkey(key.id).first())
        val updated = key.copy(nickname = "Renamed")
        assertEquals(updated, repository.save(updated))
        assertEquals(updated, repository.getById(key.id))
    }

    @Test
    fun colorDaoUpsertsPreservePaletteIdentityAndRepository() = runBlocking {
        val dao = database.colorSchemeDao()
        val scheme = ColorScheme(name = "Custom", isBuiltIn = false)
        assertEquals(scheme.id, dao.insert(scheme))
        val renamed = scheme.copy(name = "Renamed")
        assertEquals(scheme.id, dao.insertOrUpdate(renamed))
        assertEquals(renamed, dao.getById(scheme.id))
        val color = ColorPalette(schemeId = scheme.id, colorIndex = 3, color = 123)
        assertEquals(color.id, dao.insertColor(color))
        val replacement = color.copy(id = UUID.randomUUID(), repositoryId = UUID.randomUUID(), color = 456)
        assertEquals(color.id, dao.insertOrUpdateColor(replacement))
        assertEquals(color.copy(color = 456), dao.getColor(scheme.id, 3))
        val other = ColorPalette(schemeId = scheme.id, colorIndex = 4, color = 789)
        assertEquals(other.id, dao.insertOrUpdateColor(other))
        assertEquals(other, dao.getColor(scheme.id, 4))
    }

    @Test
    fun customSchemePaletteChangesExportAndImportRetainUuidSettings() = runBlocking {
        val preset = EntityIds.preset(1)
        assertEquals(preset, schemes.observeScheme(preset).first()!!.id)
        assertArrayEquals(ColorSchemePresets.builtInSchemes[1].colors, schemes.observeSchemeColors(preset).first())
        val defaults = ColorSchemePresets.builtInSchemes[1]
        assertEquals(defaults.defaultFg to defaults.defaultBg, schemes.observeSchemeDefaults(preset).first())
        val id = schemes.createCustomScheme("Custom", basedOnSchemeId = preset)
        schemes.setColorForScheme(id, 3, 0x123456)
        assertEquals(0x123456, database.colorSchemeDao().getColor(id, 3)!!.color)
        schemes.setDefaultColorsForScheme(id, 2, 4)
        assertEquals(2 to 4, schemes.observeSchemeDefaults(id).first())
        assertEquals(0x123456, schemes.observeSchemeColors(id).first()[3])
        val duplicate = schemes.duplicateScheme(id, "Copy")
        assertArrayEquals(schemes.getSchemeColors(id), schemes.getSchemeColors(duplicate))
        assertEquals("Custom", schemes.observeScheme(id).first()!!.name)
        val exported = schemes.exportScheme(id)
        assertEquals("Custom", exported.name)
        val imported = schemes.importScheme(exported.toJson())
        assertEquals("Custom (1)", database.colorSchemeDao().getById(imported)!!.name)
        assertArrayEquals(exported.toPalette(), schemes.getSchemeColors(imported))
        assertFalse(schemes.schemeNameExists(defaults.name, preset))
        assertTrue(schemes.schemeNameExists(defaults.name))
        assertTrue(schemes.schemeNameExists(defaults.name, id))
        schemes.resetSchemeToDefaults(preset)
        assertArrayEquals(defaults.colors, schemes.getSchemeColors(preset))
        assertNull(schemes.observeScheme(UUID.randomUUID()).first())
    }
}
