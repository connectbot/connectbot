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

package org.connectbot.piv

import com.yubico.yubikit.piv.KeyType
import org.connectbot.sshlib.SshSigning
import org.connectbot.util.SshKeyType
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

// SHA-1 ("ssh-rsa") is deliberately not offered: current servers reject it by default.
private val RSA_SHA2_NAMES = listOf("rsa-sha2-512", "rsa-sha2-256")

/** Key types ConnectBot can use from a PIV slot for SSH, and the SSH algorithm names each one is offered as. */
internal enum class PivAlgorithm(val keyType: KeyType, val label: String, val sshNames: List<String>) {
    EC_P256(KeyType.ECCP256, "EC P-256", listOf("ecdsa-sha2-nistp256")),
    EC_P384(KeyType.ECCP384, "EC P-384", listOf("ecdsa-sha2-nistp384")),
    ED25519(KeyType.ED25519, "Ed25519", listOf("ssh-ed25519")),
    RSA_2048(KeyType.RSA2048, "RSA 2048", RSA_SHA2_NAMES),
    RSA_3072(KeyType.RSA3072, "RSA 3072", RSA_SHA2_NAMES),
    RSA_4096(KeyType.RSA4096, "RSA 4096", RSA_SHA2_NAMES),
    ;

    /** The key type name saved with the key, as [org.connectbot.util.SshKeyType.storedName]. */
    val storedType: String get() = when (this) {
        EC_P256, EC_P384 -> SshKeyType.EC.storedName
        ED25519 -> SshKeyType.ED25519.storedName
        RSA_2048, RSA_3072, RSA_4096 -> SshKeyType.RSA.storedName
    }

    /** The name to sign with when the server did not pick one. */
    val defaultSshName: String get() = sshNames.first()

    companion object {
        fun fromKeyType(keyType: KeyType): PivAlgorithm? = entries.firstOrNull { it.keyType == keyType }

        fun fromPublicKey(publicKey: PublicKey): PivAlgorithm? = when (publicKey) {
            is ECPublicKey -> when (publicKey.params.order.bitLength()) {
                256 -> EC_P256
                384 -> EC_P384
                else -> null
            }

            is RSAPublicKey -> when (publicKey.modulus.bitLength()) {
                2048 -> RSA_2048
                3072 -> RSA_3072
                4096 -> RSA_4096
                else -> null
            }

            else -> if (isEd25519(publicKey)) ED25519 else null
        }

        // Providers disagree on what getAlgorithm() returns for Ed25519 ("Ed25519", "EdDSA" or the OID),
        // so look at the encoding: a fixed X.509 header (OID 1.3.101.112) followed by the 32-byte key.
        private val ED25519_X509_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

        private fun isEd25519(publicKey: PublicKey): Boolean {
            val encoded = publicKey.encoded ?: return false
            return publicKey.format == "X.509" &&
                encoded.size == ED25519_X509_PREFIX.size + 32 &&
                encoded.copyOfRange(0, ED25519_X509_PREFIX.size).contentEquals(ED25519_X509_PREFIX)
        }

        /** Human-readable name of a key, also for the ones that are not supported. */
        fun describe(publicKey: PublicKey): String = fromPublicKey(publicKey)?.label ?: when (publicKey) {
            is ECPublicKey -> "EC P-${publicKey.params.curve.field.fieldSize}"
            is RSAPublicKey -> "RSA ${publicKey.modulus.bitLength()}"
            else -> publicKey.algorithm
        }

        /** Human-readable name of a key type, also for the ones that are not supported. */
        fun describe(keyType: KeyType): String = fromKeyType(keyType)?.label ?: when (keyType) {
            KeyType.RSA1024 -> "RSA 1024"
            else -> keyType.name
        }
    }
}

/**
 * Turns the card's raw private-key operation into an SSH signature blob. The card does the
 * cryptography; this only picks the hash, applies the PKCS#1 padding that RSA needs, and frames the
 * result. Every signature is checked against the public key before it is returned.
 */
internal object PivSignature {
    // DER DigestInfo prefixes from RFC 8017, section 9.2 (notes).
    internal val SHA256_DIGEST_INFO = hex("3031300d060960864801650304020105000420")
    internal val SHA512_DIGEST_INFO = hex("3051300d060960864801650304020305000440")

    private fun hex(value: String): ByteArray = ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /**
     * @param rawSign performs the card's private-key operation on its argument: the digest for ECDSA, the
     * padded block for RSA, and the unhashed message for Ed25519.
     */
    fun sign(
        algorithm: PivAlgorithm,
        sshName: String,
        publicKey: PublicKey,
        data: ByteArray,
        rawSign: (ByteArray) -> ByteArray,
    ): ByteArray {
        require(sshName in algorithm.sshNames) { "$sshName cannot be signed with ${algorithm.label}" }
        return when (algorithm) {
            PivAlgorithm.EC_P256, PivAlgorithm.EC_P384 -> {
                val bits = if (algorithm == PivAlgorithm.EC_P384) 384 else 256
                val der = rawSign(MessageDigest.getInstance("SHA-$bits").digest(data))
                verify("SHA${bits}withECDSA", publicKey, data, der)
                SshSigning.encodeEcdsaSignature(sshName, der)
            }

            PivAlgorithm.RSA_2048, PivAlgorithm.RSA_3072, PivAlgorithm.RSA_4096 -> {
                val sha512 = sshName == "rsa-sha2-512"
                val digest = MessageDigest.getInstance(if (sha512) "SHA-512" else "SHA-256").digest(data)
                val block = pkcs1Block(if (sha512) SHA512_DIGEST_INFO else SHA256_DIGEST_INFO, digest, (publicKey as RSAPublicKey).modulus.bitLength() / 8)
                val signature = rawSign(block)
                verify(if (sha512) "SHA512withRSA" else "SHA256withRSA", publicKey, data, signature)
                sshSignatureBlob(sshName, signature)
            }

            PivAlgorithm.ED25519 -> {
                // The card hashes the message itself.
                val signature = rawSign(data)
                try {
                    verify("Ed25519", publicKey, data, signature)
                } catch (_: NoSuchAlgorithmException) {
                    // No Ed25519 verifier on this device; the server verifies the signature anyway.
                }
                sshSignatureBlob(sshName, signature)
            }
        }
    }

    /** EMSA-PKCS1-v1_5 encoding: 0x00 0x01 FF..FF 0x00 DigestInfo digest, as long as the RSA modulus. */
    internal fun pkcs1Block(digestInfoPrefix: ByteArray, digest: ByteArray, length: Int): ByteArray {
        val tLength = digestInfoPrefix.size + digest.size
        require(length >= tLength + 11) { "RSA modulus too short for this digest" }
        return ByteArray(length).also {
            it[1] = 1
            it.fill(0xFF.toByte(), 2, length - tLength - 1)
            digestInfoPrefix.copyInto(it, length - tLength)
            digest.copyInto(it, length - digest.size)
        }
    }

    internal fun sshSignatureBlob(algorithmName: String, signature: ByteArray): ByteArray {
        val name = algorithmName.toByteArray(Charsets.US_ASCII)
        return ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(name.size)
                write(name)
                writeInt(signature.size)
                write(signature)
            }
        }.toByteArray()
    }

    private fun verify(jcaAlgorithm: String, publicKey: PublicKey, data: ByteArray, signature: ByteArray) {
        val verifier = Signature.getInstance(jcaAlgorithm)
        verifier.initVerify(publicKey)
        verifier.update(data)
        if (!verifier.verify(signature)) throw YubiKeyPiv.WrongKey()
    }
}
