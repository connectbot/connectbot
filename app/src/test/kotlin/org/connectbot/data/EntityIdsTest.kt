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

import org.connectbot.util.HostConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EntityIdsTest {
    @Test
    fun parsingPreservesUuidAndRejectsInvalidOrMissingInput() {
        val id = UUID.randomUUID()
        assertEquals(id, EntityIds.parse(id.toString()))
        for (value in listOf(null, "", "not-a-uuid", "42")) assertEquals(EntityIds.NONE, EntityIds.parse(value))
    }

    @Test
    fun specialKeyAndSchemeIdentitiesRemainDistinctFromUserIdentities() {
        assertFalse(EntityIds.isKey(HostConstants.PUBKEYID_ANY))
        assertFalse(EntityIds.isKey(HostConstants.PUBKEYID_NEVER))
        assertTrue(EntityIds.isKey(UUID.randomUUID()))
        assertEquals(HostConstants.PUBKEYID_ANY, EntityIds.legacyKey(-1))
        assertEquals(HostConstants.PUBKEYID_NEVER, EntityIds.legacyKey(-2))
        assertEquals(EntityIds.legacy("pubkeys", 42), EntityIds.legacyKey(42))
        assertEquals(EntityIds.DEFAULT_SCHEME, EntityIds.legacyScheme(-1))
        assertEquals(EntityIds.legacy("color_schemes", 42), EntityIds.legacyScheme(42))
        assertFalse(EntityIds.isPreset(UUID.randomUUID()))
    }
}
