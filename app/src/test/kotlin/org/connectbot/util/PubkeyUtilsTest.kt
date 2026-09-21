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
import org.connectbot.sshlib.SshKeys
import org.junit.Test

class PubkeyUtilsTest {

    private val ed25519KeyPair = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()

    @Test
    fun decodePrivate_ed25519StoredType_returnsKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "Ed25519")

        assertThat(privateKey).isNotNull()
    }

    @Test
    fun decodePublic_ed25519StoredType_returnsKey() {
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "Ed25519")

        assertThat(publicKey).isNotNull()
    }

    @Test
    fun decodePrivate_lowercaseEd25519StoredType_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "ed25519")

        assertThat(privateKey).isNotNull()
    }

    @Test
    fun decodePublic_lowercaseEd25519StoredType_returnsSshlibKey() {
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "ed25519")

        assertThat(publicKey).isNotNull()
    }

    @Test
    fun decodePrivate_lowercaseLegacyEdDsaType_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "eddsa")

        assertThat(privateKey).isNotNull()
    }

    @Test
    fun decodePublic_lowercaseLegacyEdDsaType_returnsSshlibKey() {
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "eddsa")

        assertThat(publicKey).isNotNull()
    }

    @Test
    fun decodePrivate_uppercaseLegacyEdDsaType_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "EDDSA")

        assertThat(privateKey).isNotNull()
    }

    @Test
    fun decodePublic_uppercaseLegacyEdDsaType_returnsSshlibKey() {
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "EDDSA")

        assertThat(publicKey).isNotNull()
    }

    @Test
    fun decodePrivate_legacyOpenSshEd25519Type_returnsSshlibKey() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "ssh-ed25519")

        assertThat(privateKey).isNotNull()
    }

    @Test
    fun decodedEd25519Key_canBeEncodedAsOpenSshAndPem() {
        val privateKey = PubkeyUtils.decodePrivate(ed25519KeyPair.private.encoded, "Ed25519")
        val publicKey = PubkeyUtils.decodePublic(ed25519KeyPair.public.encoded, "Ed25519")

        val openSsh = SshKeys.encodeOpenSshPrivateKey(java.security.KeyPair(publicKey, privateKey!!))
        val pem = SshKeys.encodePemPrivateKey(java.security.KeyPair(publicKey, privateKey))

        assertThat(openSsh).contains("-----BEGIN OPENSSH PRIVATE KEY-----")
        assertThat(pem).contains("-----BEGIN PRIVATE KEY-----")
    }
}
