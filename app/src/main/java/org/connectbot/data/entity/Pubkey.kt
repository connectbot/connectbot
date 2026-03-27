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

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SSH public/private key pair entity with security-conscious backup controls.
 *
 * This entity supports three storage types:
 * - EXPORTABLE: Traditional encrypted keys stored in database (current implementation)
 * - ANDROID_KEYSTORE: Keys stored in Android Keystore (future Phase 2)
 * - FIDO2_RESIDENT_KEY: Keys stored on external FIDO2 security keys (YubiKey, SoloKey, etc.)
 *
 * The allowBackup field gives users granular control over which keys are included
 * in Android backups. Keys marked with allowBackup=false will be excluded from
 * backup operations via the custom BackupAgent.
 */
@Entity(
    tableName = "pubkeys",
    indices = [
        Index(value = ["nickname"], unique = true),
        Index(value = ["storage_type"]),
        Index(value = ["allow_backup"])
    ]
)
data class Pubkey(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nickname: String,

    val type: String,

    @ColumnInfo(name = "private_key", typeAffinity = ColumnInfo.BLOB)
    val privateKey: ByteArray?,

    @ColumnInfo(name = "public_key", typeAffinity = ColumnInfo.BLOB)
    val publicKey: ByteArray,

    val encrypted: Boolean,

    val startup: Boolean,

    val confirmation: Boolean,

    @ColumnInfo(name = "created_date")
    val createdDate: Long,

    @ColumnInfo(name = "storage_type")
    val storageType: KeyStorageType = KeyStorageType.EXPORTABLE,

    @ColumnInfo(name = "allow_backup")
    val allowBackup: Boolean = true,

    @ColumnInfo(name = "keystore_alias")
    val keystoreAlias: String? = null,

    /** FIDO2 credential ID (key handle) for resident keys stored on security keys */
    @ColumnInfo(name = "credential_id", typeAffinity = ColumnInfo.BLOB)
    val credentialId: ByteArray? = null,

    /** FIDO2 relying party ID, typically "ssh:" for SSH keys */
    @ColumnInfo(name = "fido2_rp_id")
    val fido2RpId: String? = null,

    /** Preferred transport for FIDO2 security key (USB or NFC) */
    @ColumnInfo(name = "fido2_transport")
    val fido2Transport: Fido2Transport? = null
) {
    /** Whether this key is stored in Android Keystore with biometric protection */
    val isBiometric: Boolean
        get() = storageType == KeyStorageType.ANDROID_KEYSTORE

    /** Whether this key is stored on an external FIDO2 security key */
    val isFido2: Boolean
        get() = storageType == KeyStorageType.FIDO2_RESIDENT_KEY

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Pubkey

        if (id != other.id) return false
        if (nickname != other.nickname) return false
        if (type != other.type) return false
        if (privateKey != null) {
            if (other.privateKey == null) return false
            if (!privateKey.contentEquals(other.privateKey)) return false
        } else if (other.privateKey != null) {
            return false
        }
        if (!publicKey.contentEquals(other.publicKey)) return false
        if (encrypted != other.encrypted) return false
        if (startup != other.startup) return false
        if (confirmation != other.confirmation) return false
        if (createdDate != other.createdDate) return false
        if (storageType != other.storageType) return false
        if (allowBackup != other.allowBackup) return false
        if (keystoreAlias != other.keystoreAlias) return false
        if (credentialId != null) {
            if (other.credentialId == null) return false
            if (!credentialId.contentEquals(other.credentialId)) return false
        } else if (other.credentialId != null) {
            return false
        }
        if (fido2RpId != other.fido2RpId) return false
        if (fido2Transport != other.fido2Transport) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (privateKey?.contentHashCode() ?: 0)
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + encrypted.hashCode()
        result = 31 * result + startup.hashCode()
        result = 31 * result + confirmation.hashCode()
        result = 31 * result + createdDate.hashCode()
        result = 31 * result + storageType.hashCode()
        result = 31 * result + allowBackup.hashCode()
        result = 31 * result + (keystoreAlias?.hashCode() ?: 0)
        result = 31 * result + (credentialId?.contentHashCode() ?: 0)
        result = 31 * result + (fido2RpId?.hashCode() ?: 0)
        result = 31 * result + (fido2Transport?.hashCode() ?: 0)
        return result
    }
}
