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

package org.connectbot.data.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PubkeyEntityTest {

    @Test
    fun `equals compares ByteArray fields correctly`() {
        val privateKey = byteArrayOf(1, 2, 3, 4, 5)
        val publicKey = byteArrayOf(10, 20, 30, 40, 50)

        val pubkey1 = Pubkey(
            id = 1,
            nickname = "test",
            type = "ssh-rsa",
            privateKey = privateKey,
            publicKey = publicKey,
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 123456789,
            storageType = KeyStorageType.EXPORTABLE,
            allowBackup = true,
            keystoreAlias = null
        )

        val pubkey2 = Pubkey(
            id = 1,
            nickname = "test",
            type = "ssh-rsa",
            privateKey = byteArrayOf(1, 2, 3, 4, 5),
            publicKey = byteArrayOf(10, 20, 30, 40, 50),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 123456789,
            storageType = KeyStorageType.EXPORTABLE,
            allowBackup = true,
            keystoreAlias = null
        )

        // Should be equal because all fields including ByteArrays have same content
        assertThat(pubkey1).isEqualTo(pubkey2)
        assertThat(pubkey1.hashCode()).isEqualTo(pubkey2.hashCode())
    }

    @Test
    fun `equals returns false when ByteArray contents differ`() {
        val pubkey1 = Pubkey(
            nickname = "test",
            type = "ssh-rsa",
            privateKey = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(10, 20, 30),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 123456789
        )

        val pubkey2 = Pubkey(
            nickname = "test",
            type = "ssh-rsa",
            privateKey = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(10, 20, 99), // Different public key
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 123456789
        )

        assertThat(pubkey1).isNotEqualTo(pubkey2)
    }

    @Test
    fun `copy preserves all fields`() {
        val original = Pubkey(
            id = 42,
            nickname = "original",
            type = "ssh-ed25519",
            privateKey = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(4, 5, 6),
            encrypted = true,
            startup = true,
            confirmation = true,
            createdDate = 123456789,
            storageType = KeyStorageType.ANDROID_KEYSTORE,
            allowBackup = false,
            keystoreAlias = "my-alias"
        )

        val copy = original.copy()

        assertThat(copy.id).isEqualTo(original.id)
        assertThat(copy.nickname).isEqualTo(original.nickname)
        assertThat(copy.type).isEqualTo(original.type)
        assertThat(copy.privateKey).isEqualTo(original.privateKey)
        assertThat(copy.publicKey).isEqualTo(original.publicKey)
        assertThat(copy.encrypted).isEqualTo(original.encrypted)
        assertThat(copy.startup).isEqualTo(original.startup)
        assertThat(copy.confirmation).isEqualTo(original.confirmation)
        assertThat(copy.createdDate).isEqualTo(original.createdDate)
        assertThat(copy.storageType).isEqualTo(original.storageType)
        assertThat(copy.allowBackup).isEqualTo(original.allowBackup)
        assertThat(copy.keystoreAlias).isEqualTo(original.keystoreAlias)
    }

    @Test
    fun `copy with changes modifies only specified fields`() {
        val original = Pubkey(
            nickname = "original",
            type = "ssh-rsa",
            privateKey = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(4, 5, 6),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 123456789
        )

        val modified = original.copy(
            nickname = "modified",
            encrypted = true
        )

        assertThat(modified.nickname).isEqualTo("modified")
        assertThat(modified.encrypted).isTrue()
        assertThat(modified.type).isEqualTo(original.type)
        assertThat(modified.privateKey).isEqualTo(original.privateKey)
        assertThat(modified.publicKey).isEqualTo(original.publicKey)
    }

    @Test
    fun `null privateKey is supported`() {
        val pubkey = Pubkey(
            nickname = "public-only",
            type = "ssh-rsa",
            privateKey = null,
            publicKey = byteArrayOf(1, 2, 3),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 123456789
        )

        assertThat(pubkey.privateKey).isNull()
        assertThat(pubkey.publicKey).isNotNull()
    }

    @Test
    fun `default values are set correctly`() {
        val pubkey = Pubkey(
            nickname = "test",
            type = "ssh-rsa",
            privateKey = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(4, 5, 6),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 123456789
        )

        assertThat(pubkey.id).isEqualTo(0) // Default from @PrimaryKey(autoGenerate = true)
        assertThat(pubkey.storageType).isEqualTo(KeyStorageType.EXPORTABLE) // Default
        assertThat(pubkey.allowBackup).isTrue() // Default
        assertThat(pubkey.keystoreAlias).isNull() // Default
    }

    @Test
    fun `keystore type with alias`() {
        val pubkey = Pubkey(
            nickname = "keystore-key",
            type = "ssh-rsa",
            privateKey = null, // Keystore keys don't have exportable private keys
            publicKey = byteArrayOf(1, 2, 3),
            encrypted = false,
            startup = false,
            confirmation = false,
            createdDate = 123456789,
            storageType = KeyStorageType.ANDROID_KEYSTORE,
            allowBackup = false, // Keystore keys can't be backed up
            keystoreAlias = "my-keystore-alias"
        )

        assertThat(pubkey.storageType).isEqualTo(KeyStorageType.ANDROID_KEYSTORE)
        assertThat(pubkey.keystoreAlias).isEqualTo("my-keystore-alias")
        assertThat(pubkey.allowBackup).isFalse()
        assertThat(pubkey.privateKey).isNull()
    }

    @Test
    fun `exportable type without keystore alias`() {
        val pubkey = Pubkey(
            nickname = "exportable-key",
            type = "ssh-rsa",
            privateKey = byteArrayOf(1, 2, 3),
            publicKey = byteArrayOf(4, 5, 6),
            encrypted = true,
            startup = false,
            confirmation = false,
            createdDate = 123456789,
            storageType = KeyStorageType.EXPORTABLE,
            allowBackup = true,
            keystoreAlias = null
        )

        assertThat(pubkey.storageType).isEqualTo(KeyStorageType.EXPORTABLE)
        assertThat(pubkey.keystoreAlias).isNull()
        assertThat(pubkey.allowBackup).isTrue()
        assertThat(pubkey.privateKey).isNotNull()
    }
}
