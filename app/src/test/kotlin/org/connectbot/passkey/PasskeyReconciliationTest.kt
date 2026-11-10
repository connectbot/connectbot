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

package org.connectbot.passkey

import org.connectbot.data.PasskeyIdentityConflict
import org.connectbot.data.PasskeyReconciliation
import org.connectbot.data.entity.KeyStorageType
import org.connectbot.sshlib.sk.SkAlgorithm
import org.connectbot.sshlib.sk.SkAuthHelpers
import org.connectbot.sshlib.sk.SkPublicKeyDecoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class PasskeyReconciliationTest {
    private fun candidate() = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().let {
        val point = it.public as ECPublicKey
        fun bytes(value: BigInteger): ByteArray {
            val raw = value.toByteArray().takeLast(32).toByteArray()
            return ByteArray(32 - raw.size) + raw
        }
        PasskeySupport.fromPublicKey(byteArrayOf(1, 2), byteArrayOf(4) + bytes(point.w.affineX) + bytes(point.w.affineY), "")
    }

    @Test
    fun reconnectsSameKeyWithMissingMetadataAndPreservesEntrySettings() {
        val imported = candidate()
        val existing = imported.copy(
            nickname = "production",
            credentialId = null,
            fido2RpId = null,
            confirmation = true,
            createdDate = 123,
            allowBackup = true,
        )
        assertEquals(listOf(existing), PasskeyReconciliation.matches(imported, listOf(existing)))
        val result = PasskeyReconciliation.reconnect(imported, existing)
        assertEquals(existing.id, result.id)
        assertEquals(existing.nickname, result.nickname)
        assertEquals(existing.createdDate, result.createdDate)
        assertEquals(existing.repositoryId, result.repositoryId)
        assertTrue(result.confirmation)
        assertTrue(result.allowBackup)
        assertArrayEquals(existing.publicKey, result.publicKey)
        assertArrayEquals(imported.credentialId, result.credentialId)
        assertEquals(imported.fido2RpId, result.fido2RpId)
    }

    @Test
    fun returnsDuplicatesForExplicitSelectionAndPrefersCredentialMatch() {
        val imported = candidate()
        val exact = imported.copy(nickname = "exact")
        val stale = imported.copy(id = UUID.randomUUID(), nickname = "stale", credentialId = byteArrayOf(9))
        assertEquals(listOf(exact, stale), PasskeyReconciliation.matches(imported, listOf(stale, exact)))
        assertEquals(stale.id, PasskeyReconciliation.reconnect(imported, stale).id)
    }

    @Test
    fun neverMatchesNicknameOrOverwritesConflictingPublicKey() {
        val imported = candidate()
        val different = candidate().copy(nickname = imported.nickname, credentialId = byteArrayOf(3))
        assertTrue(PasskeyReconciliation.matches(imported, listOf(different)).isEmpty())
        val conflict = different.copy(credentialId = imported.credentialId)
        assertThrows(PasskeyIdentityConflict::class.java) {
            PasskeyReconciliation.matches(imported, listOf(imported, conflict))
        }
        assertThrows(PasskeyIdentityConflict::class.java) {
            PasskeyReconciliation.matches(imported, listOf(conflict.copy(publicKey = byteArrayOf(9))))
        }
    }

    @Test
    fun doesNotMatchDifferentRpOrNonPasskeyEntries() {
        val imported = candidate()
        val point = SkPublicKeyDecoder.decode(imported.publicKey).rawKey
        val otherRp = imported.copy(
            fido2RpId = "different.example",
            publicKey = SkAuthHelpers.buildAuthPublicKey(SkAlgorithm.ECDSA_P256, point, "different.example").publicKeyBlob,
        )
        assertTrue(PasskeyReconciliation.matches(imported, listOf(otherRp)).isEmpty())
        assertTrue(PasskeyReconciliation.matches(imported, listOf(imported.copy(storageType = KeyStorageType.EXPORTABLE))).isEmpty())
        assertFalse(imported.isFido2)
    }
}
