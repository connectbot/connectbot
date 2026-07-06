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

package org.connectbot.transport

import com.trilead.ssh2.Connection
import com.trilead.ssh2.ExtensionInfo
import com.trilead.ssh2.packets.PacketExtInfo
import com.trilead.ssh2.transport.TransportManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey

/**
 * Tests for the rsa-sha2 workaround for Android Keystore-backed RSA keys
 * (upstream issues connectbot#1974 and connectbot#2189).
 */
class RsaSha2CompatTest {

    /**
     * Mimics an Android Keystore private key: RSA by algorithm, but opaque — it exposes
     * neither its encoding nor the [java.security.interfaces.RSAPrivateKey] interface.
     */
    private class OpaqueRsaPrivateKey : PrivateKey {
        override fun getAlgorithm(): String = "RSA"
        override fun getFormat(): String? = null
        override fun getEncoded(): ByteArray? = null
    }

    private val softwareKeyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private val opaqueKeyPair: KeyPair by lazy {
        KeyPair(softwareKeyPair.public, OpaqueRsaPrivateKey())
    }

    private fun connectionWithTransportManager(): Pair<Connection, TransportManager> {
        val connection = Connection("localhost", 22)
        val tm = TransportManager("localhost", 22)
        val tmField = Connection::class.java.getDeclaredField("tm")
        tmField.isAccessible = true
        tmField.set(connection, tm)
        return connection to tm
    }

    private fun setExtensionInfo(tm: TransportManager, serverSigAlgs: String) {
        val field = TransportManager::class.java.getDeclaredField("extensionInfo")
        field.isAccessible = true
        field.set(
            tm,
            ExtensionInfo.fromPacketExtInfo(PacketExtInfo(mapOf("server-sig-algs" to serverSigAlgs))),
        )
    }

    @Test
    fun needsRsaSha2_isTrueForOpaqueRsaKey() {
        assertTrue(RsaSha2Compat.needsRsaSha2(opaqueKeyPair))
    }

    @Test
    fun needsRsaSha2_isFalseForSoftwareRsaKey() {
        assertFalse(RsaSha2Compat.needsRsaSha2(softwareKeyPair))
    }

    @Test
    fun needsRsaSha2_isFalseForNonRsaKey() {
        val ecPair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        assertFalse(RsaSha2Compat.needsRsaSha2(ecPair))
    }

    @Test
    fun opaqueKey_withoutServerSigAlgs_injectsRsaSha2AndRestores() {
        val (connection, tm) = connectionWithTransportManager()
        val originalExtensionInfo = tm.extensionInfo
        assertTrue(originalExtensionInfo.signatureAlgorithmsAccepted.isEmpty())

        var blockRuns = 0
        val result = RsaSha2Compat.withRsaSha2Preference(connection, opaqueKeyPair) {
            blockRuns++
            val accepted = tm.extensionInfo.signatureAlgorithmsAccepted
            assertTrue("rsa-sha2-256 should be injected", "rsa-sha2-256" in accepted)
            assertTrue("rsa-sha2-512 should be injected", "rsa-sha2-512" in accepted)
            "auth-result"
        }

        assertEquals("auth-result", result)
        assertEquals(1, blockRuns)
        assertSame(
            "Original extension info must be restored after the auth attempt",
            originalExtensionInfo,
            tm.extensionInfo,
        )
    }

    @Test
    fun opaqueKey_whenServerAlreadyAdvertisesRsaSha2_leavesExtensionInfoAlone() {
        val (connection, tm) = connectionWithTransportManager()
        setExtensionInfo(tm, "ssh-ed25519,rsa-sha2-256")
        val advertised = tm.extensionInfo

        val result = RsaSha2Compat.withRsaSha2Preference(connection, opaqueKeyPair) {
            assertSame(advertised, tm.extensionInfo)
            true
        }

        assertTrue(result)
        assertSame(advertised, tm.extensionInfo)
    }

    @Test
    fun softwareKey_neverTouchesExtensionInfo() {
        val (connection, tm) = connectionWithTransportManager()
        val original = tm.extensionInfo

        val result = RsaSha2Compat.withRsaSha2Preference(connection, softwareKeyPair) {
            assertSame(original, tm.extensionInfo)
            true
        }

        assertTrue(result)
        assertSame(original, tm.extensionInfo)
    }

    @Test
    fun opaqueKey_onUnconnectedConnection_stillRunsBlock() {
        // A Connection that has not connected yet has no TransportManager
        val connection = Connection("localhost", 22)

        val result = RsaSha2Compat.withRsaSha2Preference(connection, opaqueKeyPair) { 42 }

        assertEquals(42, result)
    }

    @Test
    fun opaqueKey_blockExceptionStillRestoresExtensionInfo() {
        val (connection, tm) = connectionWithTransportManager()
        val original = tm.extensionInfo

        var propagated = false
        try {
            RsaSha2Compat.withRsaSha2Preference(connection, opaqueKeyPair) {
                throw IllegalStateException("auth blew up")
            }
        } catch (expected: IllegalStateException) {
            propagated = true
        }

        assertTrue("The authentication failure must propagate unchanged", propagated)
        assertSame(original, tm.extensionInfo)
    }
}
