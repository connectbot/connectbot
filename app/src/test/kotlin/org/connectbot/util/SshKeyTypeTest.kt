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
import org.junit.Test

class SshKeyTypeTest {

    @Test
    fun fromStoredType_mapsCanonicalTypes() {
        assertThat(SshKeyType.fromStoredType("RSA")).isEqualTo(SshKeyType.RSA)
        assertThat(SshKeyType.fromStoredType("DSA")).isEqualTo(SshKeyType.DSA)
        assertThat(SshKeyType.fromStoredType("EC")).isEqualTo(SshKeyType.EC)
        assertThat(SshKeyType.fromStoredType("Ed25519")).isEqualTo(SshKeyType.ED25519)
        assertThat(SshKeyType.fromStoredType("IMPORTED")).isEqualTo(SshKeyType.LEGACY_IMPORTED)
    }

    @Test
    fun fromStoredType_mapsLegacyOpenSshTypes() {
        assertThat(SshKeyType.fromStoredType("ssh-rsa")).isEqualTo(SshKeyType.RSA)
        assertThat(SshKeyType.fromStoredType("rsa-sha2-256")).isEqualTo(SshKeyType.RSA)
        assertThat(SshKeyType.fromStoredType("ssh-dss")).isEqualTo(SshKeyType.DSA)
        assertThat(SshKeyType.fromStoredType("ecdsa-sha2-nistp256")).isEqualTo(SshKeyType.EC)
        assertThat(SshKeyType.fromStoredType("ssh-ed25519")).isEqualTo(SshKeyType.ED25519)
    }

    @Test
    fun fromJavaAlgorithm_mapsProviderAlgorithms() {
        assertThat(SshKeyType.fromJavaAlgorithm("RSA")).isEqualTo(SshKeyType.RSA)
        assertThat(SshKeyType.fromJavaAlgorithm("EC")).isEqualTo(SshKeyType.EC)
        assertThat(SshKeyType.fromJavaAlgorithm("ECDSA")).isEqualTo(SshKeyType.EC)
        assertThat(SshKeyType.fromJavaAlgorithm("EdDSA")).isEqualTo(SshKeyType.ED25519)
        assertThat(SshKeyType.fromJavaAlgorithm("Ed25519")).isEqualTo(SshKeyType.ED25519)
    }

    @Test
    fun ed25519_hasNoGenericKeyFactoryAlgorithm() {
        assertThat(SshKeyType.ED25519.keyFactoryAlgorithm).isNull()
    }

    @Test
    fun unknownType_returnsNull() {
        assertThat(SshKeyType.fromStoredType("sk-ecdsa-sha2-nistp256@openssh.com")).isNull()
    }
}
