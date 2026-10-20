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

package org.connectbot.data.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import java.io.File

@RunWith(ParameterizedRobolectricTestRunner::class)
class UuidHistoricalMigrationTest(private val version: Int) {
    companion object {
        @JvmStatic
        @Parameters(name = "v{0} to current")
        fun versions(): List<Array<Int>> = (1..14).map { arrayOf(it) }
    }

    private fun fixture() = UuidMigrationFixture(ApplicationProvider.getApplicationContext<Context>()) {
        File("schemas/org.connectbot.data.ConnectBotDatabase/$it.json").readText()
    }

    @Test
    fun populatedUpgradePreservesData() {
        fixture().use {
            it.create(version)
            it.verifyReopen()
        }
    }

    @Test
    fun emptyUpgradeValidatesAndReopens() {
        fixture().use { fixture ->
            fixture.create(version, populated = false)
            val database = fixture.open()
            fixture.assertHealthy(database.openHelper.readableDatabase)
            val rows = fixture.snapshot(database.openHelper.readableDatabase::query)
            assertTrue(rows.filterKeys { it != "profiles" }.values.all { it.isEmpty() })
            database.close()
            fixture.assertHealthy(fixture.open().openHelper.readableDatabase)
        }
    }
}
