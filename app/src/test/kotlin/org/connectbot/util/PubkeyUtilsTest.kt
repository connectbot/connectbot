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

package org.connectbot.util

import org.assertj.core.api.Assertions.assertThat
import org.connectbot.sshlib.SshSigning
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator

class PubkeyUtilsTest {

    private val ed25519KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    @Test
    fun decodePrivate_ed25519StoredType_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "Ed25519")

        assertThat(privateKey?.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodePublic_ed25519StoredType_returnsSshlibKey() {
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "Ed25519")

        assertThat(publicKey.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodePrivate_lowercaseEd25519StoredType_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "ed25519")

        assertThat(privateKey?.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodePublic_lowercaseEd25519StoredType_returnsSshlibKey() {
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "ed25519")

        assertThat(publicKey.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodePrivate_lowercaseLegacyEdDsaType_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "eddsa")

        assertThat(privateKey?.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodePublic_lowercaseLegacyEdDsaType_returnsSshlibKey() {
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "eddsa")

        assertThat(publicKey.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodePrivate_uppercaseLegacyEdDsaType_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "EDDSA")

        assertThat(privateKey?.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodePublic_uppercaseLegacyEdDsaType_returnsSshlibKey() {
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "EDDSA")

        assertThat(publicKey.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodePrivate_legacyOpenSshEd25519Type_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "ssh-ed25519")

        assertThat(privateKey?.algorithm).isIn("Ed25519", "EdDSA", "1.3.101.112")
    }

    @Test
    fun decodedEd25519Key_canBeUsedForSshSigning() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "Ed25519")
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "Ed25519")

        val keyPair = KeyPair(publicKey, privateKey)
        val authPublicKey = SshSigning.encodePublicKey(keyPair)
        val signature = SshSigning.signWithKeyPair(authPublicKey.algorithmName, keyPair, "test".toByteArray())

        assertThat(authPublicKey.algorithmName).isEqualTo("ssh-ed25519")
        assertThat(signature).isNotEmpty()
    }
}
