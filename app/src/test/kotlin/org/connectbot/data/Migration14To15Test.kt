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

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class Migration14To15Test {
    @Test
    fun postMigration_onlyConvertsUntouchedDefaultProfile() {
        val database = mock(SupportSQLiteDatabase::class.java)

        ConnectBotDatabase.Migration14To15().onPostMigrate(database)

        verify(database).execSQL(
            "UPDATE profiles SET font_size = 0 " +
                "WHERE id = 1 AND name = 'Default' AND font_size = 10",
        )
    }
}
