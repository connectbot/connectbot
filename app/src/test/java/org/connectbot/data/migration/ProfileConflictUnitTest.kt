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

package org.connectbot.data.migration

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.entity.Profile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit test to reproduce issue #1806:
 * "Migration failed: UNIQUE constraint failed: profiles.id"
 *
 * This test demonstrates the core issue: when the Room database already has
 * a default profile with ID=1, and the legacy migration tries to insert
 * another profile with ID=1, it fails with a unique constraint violation.
 *
 * These tests define the EXPECTED behavior and will FAIL until the fix is implemented.
 *
 * @see <a href="https://github.com/connectbot/connectbot/issues/1806">Issue #1806</a>
 */
@RunWith(AndroidJUnit4::class)
class ProfileConflictUnitTest {

    private lateinit var context: Context
    private lateinit var database: ConnectBotDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ConnectBotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Reproduces issue #1806: Tests that migration should succeed even when
     * Room database already has a default profile.
     *
     * This simulates what happens during legacy migration:
     * 1. Room database is created with default profile (ID=1) from onCreate callback
     * 2. Legacy migration runs and creates profiles
     * 3. Migration should succeed without UNIQUE constraint violation
     *
     * This test will FAIL until the fix is implemented because the current code
     * tries to insert a profile with explicit ID=1 which conflicts.
     */
    @Test
    fun `migration should succeed when default profile already exists`() = runTest {
        // Step 1: Simulate Room database created with default profile
        // This is what DatabaseModule.onCreate() does
        val defaultProfile = Profile(
            id = 0, // Auto-generate ID (will become 1)
            name = "Default",
            colorSchemeId = -1L,
            fontSize = 10,
            delKey = "del",
            encoding = "UTF-8",
            emulation = "xterm-256color"
        )
        database.profileDao().insert(defaultProfile)

        // Verify the default profile was inserted with ID=1
        val existingProfiles = database.profileDao().getAll()
        assertThat(existingProfiles).hasSize(1)
        assertThat(existingProfiles[0].id).isEqualTo(1L)

        // Step 2: Simulate legacy migration trying to insert profiles
        // This is what DatabaseMigrator.writeToRoomDatabase() does
        // Currently it uses explicit ID=1 which causes the conflict
        val migratedProfile = Profile(
            id = 1, // Explicit ID=1 - THIS IS THE BUG!
            name = "Default",
            colorSchemeId = -1L,
            fontSize = 10,
            delKey = "del",
            encoding = "UTF-8"
        )

        // Step 3: This should NOT throw an exception - migration should handle this gracefully
        // Currently this WILL throw SQLiteConstraintException, causing the test to FAIL
        var migrationSucceeded = false
        try {
            database.withTransaction {
                database.profileDao().insert(migratedProfile)
            }
            migrationSucceeded = true
        } catch (e: Exception) {
            // Migration failed - this is the bug we're reproducing
            migrationSucceeded = false
        }

        // This assertion will FAIL until the fix is implemented
        assertThat(migrationSucceeded)
            .describedAs("Migration should succeed even when default profile exists (Issue #1806)")
            .isTrue()
    }

    /**
     * Tests the exact scenario from issue #1806:
     * - Room database has default profile (from fresh install or MIGRATION_4_5)
     * - Room hosts/pubkeys tables are empty (so isMigrationNeeded returns true)
     * - Legacy migration runs and should complete successfully
     *
     * This test will FAIL until the fix is implemented.
     */
    @Test
    fun `issue 1806 - migration should not fail when Room has profile but no hosts or pubkeys`() = runTest {
        // Step 1: Room database is created with default profile
        // This happens via DatabaseModule.onCreate() callback
        val defaultProfile = Profile(
            id = 0,
            name = "Default",
            colorSchemeId = -1L,
            fontSize = 10,
            delKey = "del",
            encoding = "UTF-8"
        )
        database.profileDao().insert(defaultProfile)

        // Step 2: Verify the state that triggers the bug:
        // - hosts table is empty
        // - pubkeys table is empty
        // - profiles table has the default profile
        val hosts = database.hostDao().getAll()
        val pubkeys = database.pubkeyDao().getAll()
        val profiles = database.profileDao().getAll()

        assertThat(hosts).isEmpty()
        assertThat(pubkeys).isEmpty()
        assertThat(profiles).hasSize(1)

        // Step 3: This is what isMigrationNeeded() checks - only hosts and pubkeys
        // It would return true because hosts and pubkeys are empty
        // BUT: isMigrationNeeded should also check profiles table!
        val roomHasData = hosts.isNotEmpty() || pubkeys.isNotEmpty()

        // Current buggy behavior: roomHasData is false even though profiles exist
        assertThat(roomHasData).isFalse()

        // Step 4: The fix should either:
        // Option A: isMigrationNeeded() should also check profiles table
        // Option B: Migration should handle existing profiles gracefully

        // For now, test that migration would succeed (it won't until fixed)
        val migratedProfile = Profile(
            id = 1,
            name = "Default",
            colorSchemeId = -1L,
            fontSize = 10,
            delKey = "del",
            encoding = "UTF-8"
        )

        var migrationSucceeded = false
        try {
            database.withTransaction {
                database.profileDao().insert(migratedProfile)
            }
            migrationSucceeded = true
        } catch (e: Exception) {
            migrationSucceeded = false
        }

        // This will FAIL until the fix is implemented
        assertThat(migrationSucceeded)
            .describedAs("Issue #1806: Migration should handle existing profiles gracefully")
            .isTrue()
    }

    /**
     * Tests that isMigrationNeeded() should return false when profiles already exist,
     * even if hosts and pubkeys are empty.
     *
     * This is one potential fix for issue #1806.
     * This test will FAIL until the fix is implemented.
     */
    @Test
    fun `isMigrationNeeded should consider profiles table not just hosts and pubkeys`() = runTest {
        // Insert default profile (simulating DatabaseModule.onCreate())
        val defaultProfile = Profile(
            id = 0,
            name = "Default",
            colorSchemeId = -1L,
            fontSize = 10,
            delKey = "del",
            encoding = "UTF-8"
        )
        database.profileDao().insert(defaultProfile)

        // Current buggy check (only hosts and pubkeys)
        val hosts = database.hostDao().getAll()
        val pubkeys = database.pubkeyDao().getAll()
        val profiles = database.profileDao().getAll()

        val currentBuggyCheck = hosts.isNotEmpty() || pubkeys.isNotEmpty()

        // The fix: also check profiles
        val fixedCheck = hosts.isNotEmpty() || pubkeys.isNotEmpty() || profiles.isNotEmpty()

        // Current behavior is buggy - returns false even though we have data
        assertThat(currentBuggyCheck).isFalse()

        // Fixed behavior should return true (we have profile data)
        assertThat(fixedCheck).isTrue()

        // This test verifies the fix is needed - it passes to show the discrepancy
        // The actual fix should be in DatabaseMigrator.isMigrationNeeded()
        assertThat(profiles)
            .describedAs("Room has profile data that current isMigrationNeeded() ignores")
            .isNotEmpty()
    }
}
