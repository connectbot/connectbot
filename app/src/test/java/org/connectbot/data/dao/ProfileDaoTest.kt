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

package org.connectbot.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.Profile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileDaoTest {

    private lateinit var database: ConnectBotDatabase
    private lateinit var profileDao: ProfileDao
    private lateinit var hostDao: HostDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profileDao = database.profileDao()
        hostDao = database.hostDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveProfile() = runTest {
        val profile = createTestProfile(name = "TestProfile")

        val id = profileDao.insert(profile)
        assertThat(id).isGreaterThan(0)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.name).isEqualTo("TestProfile")
        assertThat(retrieved?.fontSize).isEqualTo(10)
        assertThat(retrieved?.encoding).isEqualTo("UTF-8")
    }

    @Test
    fun observeAllProfiles() = runTest {
        val profile1 = createTestProfile(name = "Alpha")
        val profile2 = createTestProfile(name = "Beta")
        val profile3 = createTestProfile(name = "Gamma")

        profileDao.insert(profile1)
        profileDao.insert(profile2)
        profileDao.insert(profile3)

        val profiles = profileDao.observeAll().first()
        assertThat(profiles).hasSize(3)
        assertThat(profiles.map { it.name }).containsExactly("Alpha", "Beta", "Gamma")
    }

    @Test
    fun observeProfileById() = runTest {
        val profile = createTestProfile(name = "Observable")
        val id = profileDao.insert(profile)

        val observed = profileDao.observeById(id).first()
        assertThat(observed).isNotNull()
        assertThat(observed?.name).isEqualTo("Observable")
    }

    @Test
    fun getAllProfiles() = runTest {
        val profile1 = createTestProfile(name = "First")
        val profile2 = createTestProfile(name = "Second")

        profileDao.insert(profile1)
        profileDao.insert(profile2)

        val all = profileDao.getAll()
        assertThat(all).hasSize(2)
        assertThat(all.map { it.name }).containsExactly("First", "Second")
    }

    @Test
    fun getDefault() = runTest {
        // Insert the default profile
        val defaultProfile = Profile(
            id = 1,
            name = "Default"
        )
        profileDao.insert(defaultProfile)

        val retrieved = profileDao.getDefault()
        assertThat(retrieved).isNotNull()
        assertThat(retrieved?.id).isEqualTo(1)
        assertThat(retrieved?.name).isEqualTo("Default")
    }

    @Test
    fun updateProfile() = runTest {
        val profile = createTestProfile(name = "Original", fontSize = 10)
        val id = profileDao.insert(profile)

        val updated = profile.copy(id = id, name = "Modified", fontSize = 14)
        profileDao.update(updated)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.name).isEqualTo("Modified")
        assertThat(retrieved?.fontSize).isEqualTo(14)
    }

    @Test
    fun insertOrUpdateInsertsNewProfile() = runTest {
        val profile = createTestProfile(name = "NewProfile")
        val id = profileDao.insertOrUpdate(profile)

        assertThat(id).isGreaterThan(0)
        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.name).isEqualTo("NewProfile")
    }

    @Test
    fun insertOrUpdateUpdatesExistingProfile() = runTest {
        val profile = createTestProfile(name = "ExistingProfile")
        val id = profileDao.insert(profile)

        val updated = profile.copy(id = id, fontSize = 16)
        val returnedId = profileDao.insertOrUpdate(updated)

        assertThat(returnedId).isEqualTo(id)
        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.fontSize).isEqualTo(16)
    }

    @Test
    fun deleteProfile() = runTest {
        val profile = createTestProfile(name = "ToDelete")
        val id = profileDao.insert(profile)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved).isNotNull()

        profileDao.delete(retrieved!!)

        val afterDelete = profileDao.getById(id)
        assertThat(afterDelete).isNull()
    }

    @Test
    fun deleteProfileById() = runTest {
        // Insert a dummy profile first to ensure our test profile doesn't get ID 1
        val dummy = createTestProfile(name = "Dummy")
        profileDao.insert(dummy)

        val profile = createTestProfile(name = "ToDeleteById")
        val id = profileDao.insert(profile)

        val deletedCount = profileDao.deleteById(id)
        assertThat(deletedCount).isEqualTo(1)

        val afterDelete = profileDao.getById(id)
        assertThat(afterDelete).isNull()
    }

    @Test
    fun deleteByIdDoesNotDeleteDefaultProfile() = runTest {
        // Insert the default profile with id = 1
        val defaultProfile = Profile(id = 1, name = "Default")
        profileDao.insert(defaultProfile)

        val deletedCount = profileDao.deleteById(1)
        assertThat(deletedCount).isEqualTo(0)

        val retrieved = profileDao.getById(1)
        assertThat(retrieved).isNotNull()
    }

    @Test
    fun nameExistsReturnsTrueForExistingName() = runTest {
        val profile = createTestProfile(name = "ExistingName")
        profileDao.insert(profile)

        val exists = profileDao.nameExists("ExistingName")
        assertThat(exists).isTrue()
    }

    @Test
    fun nameExistsReturnsFalseForNonExistingName() = runTest {
        val exists = profileDao.nameExists("NonExistent")
        assertThat(exists).isFalse()
    }

    @Test
    fun nameExistsIsCaseInsensitive() = runTest {
        val profile = createTestProfile(name = "TestProfile")
        profileDao.insert(profile)

        assertThat(profileDao.nameExists("testprofile")).isTrue()
        assertThat(profileDao.nameExists("TESTPROFILE")).isTrue()
        assertThat(profileDao.nameExists("TeStPrOfIlE")).isTrue()
    }

    @Test
    fun nameExistsCanExcludeProfileId() = runTest {
        val profile = createTestProfile(name = "ProfileToRename")
        val id = profileDao.insert(profile)

        // Should return false when excluding the profile's own ID (allows renaming to same name)
        val existsExcludingSelf = profileDao.nameExists("ProfileToRename", id)
        assertThat(existsExcludingSelf).isFalse()

        // Should return true when not excluding
        val existsWithoutExclusion = profileDao.nameExists("ProfileToRename")
        assertThat(existsWithoutExclusion).isTrue()
    }

    @Test
    fun getHostsUsingProfileReturnsCorrectCount() = runTest {
        val profile = createTestProfile(name = "UsedProfile")
        val profileId = profileDao.insert(profile)

        // Create hosts using this profile
        val host1 = createTestHost(nickname = "host1", profileId = profileId)
        val host2 = createTestHost(nickname = "host2", profileId = profileId)
        val host3 = createTestHost(nickname = "host3", profileId = null)

        hostDao.insert(host1)
        hostDao.insert(host2)
        hostDao.insert(host3)

        val count = profileDao.getHostsUsingProfile(profileId)
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun getHostsUsingProfileReturnsZeroForUnusedProfile() = runTest {
        val profile = createTestProfile(name = "UnusedProfile")
        val profileId = profileDao.insert(profile)

        val count = profileDao.getHostsUsingProfile(profileId)
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun fontSizeStored() = runTest {
        val profile = createTestProfile(name = "font-test", fontSize = 14)
        val id = profileDao.insert(profile)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.fontSize).isEqualTo(14)
    }

    @Test
    fun encodingStored() = runTest {
        val profile = createTestProfile(name = "encoding-test", encoding = "ISO-8859-1")
        val id = profileDao.insert(profile)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.encoding).isEqualTo("ISO-8859-1")
    }

    @Test
    fun delKeyStored() = runTest {
        val profile = createTestProfile(name = "del-test", delKey = "backspace")
        val id = profileDao.insert(profile)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.delKey).isEqualTo("backspace")
    }

    @Test
    fun colorSchemeIdStored() = runTest {
        val profile = createTestProfile(name = "color-test", colorSchemeId = 5)
        val id = profileDao.insert(profile)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.colorSchemeId).isEqualTo(5)
    }

    @Test
    fun fontFamilyStored() = runTest {
        val profile = createTestProfile(name = "font-family-test", fontFamily = "Roboto Mono")
        val id = profileDao.insert(profile)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.fontFamily).isEqualTo("Roboto Mono")
    }

    @Test
    fun iconColorStored() = runTest {
        val profile = createTestProfile(name = "icon-test", iconColor = "#FF5722")
        val id = profileDao.insert(profile)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.iconColor).isEqualTo("#FF5722")
    }

    @Test
    fun emulationStored() = runTest {
        val profile = createTestProfile(name = "emulation-test", emulation = "xterm")
        val id = profileDao.insert(profile)

        val retrieved = profileDao.getById(id)
        assertThat(retrieved?.emulation).isEqualTo("xterm")
    }

    @Test
    fun profilesAreOrderedByName() = runTest {
        val profile1 = createTestProfile(name = "Zebra")
        val profile2 = createTestProfile(name = "Alpha")
        val profile3 = createTestProfile(name = "Beta")

        profileDao.insert(profile1)
        profileDao.insert(profile2)
        profileDao.insert(profile3)

        val profiles = profileDao.getAll()
        assertThat(profiles.map { it.name }).containsExactly("Alpha", "Beta", "Zebra")
    }

    private fun createTestProfile(
        name: String,
        iconColor: String? = null,
        colorSchemeId: Long = -1L,
        fontFamily: String? = null,
        fontSize: Int = 10,
        delKey: String = "del",
        encoding: String = "UTF-8",
        emulation: String = "xterm-256color"
    ): Profile {
        return Profile(
            name = name,
            iconColor = iconColor,
            colorSchemeId = colorSchemeId,
            fontFamily = fontFamily,
            fontSize = fontSize,
            delKey = delKey,
            encoding = encoding,
            emulation = emulation
        )
    }

    private fun createTestHost(
        nickname: String,
        profileId: Long?
    ): Host {
        return Host(
            nickname = nickname,
            protocol = "ssh",
            username = "user",
            hostname = "example.com",
            port = 22,
            profileId = profileId
        )
    }
}
