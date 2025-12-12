/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.connectbot.util

import android.util.Log
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.OpenSSHKeyEncoder
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import com.trilead.ssh2.signature.DSASHA1Verify
import com.trilead.ssh2.signature.ECDSASHA2Verify
import com.trilead.ssh2.signature.Ed25519Verify
import com.trilead.ssh2.signature.RSASHA1Verify
import com.trilead.ssh2.signature.SSHSignature
import java.nio.ByteBuffer
import org.connectbot.data.entity.Pubkey
import java.io.IOException
import java.security.AlgorithmParameters
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.InvalidParameterSpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.EncryptedPrivateKeyInfo
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec

object PubkeyUtils {
    init {
        Ed25519Provider.insertIfNeeded()
    }

    private const val TAG = "CB.PubkeyUtils"

    const val PKCS8_START: String = "-----BEGIN PRIVATE KEY-----"
    const val PKCS8_END: String = "-----END PRIVATE KEY-----"

    // Size in bytes of salt to use.
    private const val SALT_SIZE = 8

    // Number of iterations for password hashing. PKCS#5 recommends 1000
    private const val ITERATIONS = 1000

    fun formatKey(key: Key): String {
        val algo = key.algorithm
        val fmt = key.format
        val encoded = key.encoded
        return "Key[algorithm=" + algo + ", format=" + fmt +
                ", bytes=" + encoded.size + "]"
    }

    @Throws(Exception::class)
    private fun encrypt(cleartext: ByteArray?, secret: String): ByteArray {
        val salt = ByteArray(SALT_SIZE)

        val ciphertext = Encryptor.encrypt(salt, ITERATIONS, secret, cleartext)
            ?: throw IllegalArgumentException("Encryption failed: ciphertext is null")

        val complete = ByteArray(salt.size + ciphertext.size)

        System.arraycopy(salt, 0, complete, 0, salt.size)
        System.arraycopy(ciphertext, 0, complete, salt.size, ciphertext.size)

        Arrays.fill(salt, 0x00.toByte())
        Arrays.fill(ciphertext, 0x00.toByte())

        return complete
    }

    @Throws(Exception::class)
    private fun decrypt(saltAndCiphertext: ByteArray, secret: String): ByteArray? {
        val salt = ByteArray(SALT_SIZE)
        val ciphertext = ByteArray(saltAndCiphertext.size - salt.size)

        System.arraycopy(saltAndCiphertext, 0, salt, 0, salt.size)
        System.arraycopy(saltAndCiphertext, salt.size, ciphertext, 0, ciphertext.size)

        return Encryptor.decrypt(salt, ITERATIONS, secret, ciphertext)
    }

    @Throws(Exception::class)
    fun getEncodedPrivate(pk: PrivateKey, secret: String?): ByteArray {
        val encoded = pk.encoded
        if (secret == null || secret.isEmpty()) {
            return encoded
        }
        return encrypt(pk.encoded, secret)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun decodePrivate(encoded: ByteArray?, keyType: String?): PrivateKey? {
        val privKeySpec = PKCS8EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance(keyType)
        return kf.generatePrivate(privKeySpec)
    }

    @Throws(Exception::class)
    fun decodePrivate(encoded: ByteArray, keyType: String?, secret: String?): PrivateKey? {
        return if (secret != null && secret.isNotEmpty()) decodePrivate(
            decrypt(encoded, secret),
            keyType
        )
        else decodePrivate(encoded, keyType)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun decodePublic(encoded: ByteArray?, keyType: String?): PublicKey {
        val pubKeySpec = X509EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance(keyType)
        return kf.generatePublic(pubKeySpec)
    }

    @Throws(BadPasswordException::class)
    fun convertToKeyPair(pubkey: Pubkey, password: String?): KeyPair? {
        if ("IMPORTED" == pubkey.type) {
            // load specific key using pem format
            try {
                return PEMDecoder.decode(
                    String(pubkey.privateKey!!, charset("UTF-8")).toCharArray(),
                    password
                )
            } catch (e: Exception) {
                Log.e(TAG, "Cannot decode imported key", e)
                throw BadPasswordException()
            }
        } else {
            // load using internal generated format
            try {
                val privKey = decodePrivate(pubkey.privateKey!!, pubkey.type, password)
                val pubKey = decodePublic(pubkey.publicKey, pubkey.type)
                Log.d(TAG, "Unlocked key " + formatKey(pubKey))

                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot decode pubkey from database", e)
                throw BadPasswordException()
            }
        }
    }

    /*
	 * OpenSSH compatibility methods
	 */
    @Throws(IOException::class, InvalidKeyException::class)
    fun convertToOpenSSHFormat(pk: PublicKey?, origNickname: String?): String {
        var nickname = origNickname
        if (nickname == null) nickname = "connectbot@android"

        when (pk) {
            is RSAPublicKey -> {
                var data = "ssh-rsa "
                data += String(Base64.encode(RSASHA1Verify.get().encodePublicKey(pk)))
                return "$data $nickname"
            }

            is DSAPublicKey -> {
                var data = "ssh-dss "
                data += String(Base64.encode(DSASHA1Verify.get().encodePublicKey(pk)))
                return "$data $nickname"
            }

            is ECPublicKey -> {
                val keyType = ECDSASHA2Verify.getSshKeyType(pk)
                val verifier: SSHSignature = ECDSASHA2Verify.getVerifierForKey(pk)
                val keyData = String(Base64.encode(verifier.encodePublicKey(pk)))
                return "$keyType $keyData $nickname"
            }

            is Ed25519PublicKey -> {
                return Ed25519Verify.ED25519_ID + " " + String(
                    Base64.encode(
                        Ed25519Verify.get().encodePublicKey(pk)
                    )
                ) +
                        " " + nickname
            }

            else -> throw InvalidKeyException("Unknown key type")
        }
    }

    /*
	 * OpenSSH compatibility methods
	 */
    /**
     * @param pair KeyPair to convert to an OpenSSH public key
     * @return OpenSSH-encoded pubkey
     */
    fun extractOpenSSHPublic(pair: KeyPair): ByteArray? {
        try {
            return when (val pubKey = pair.public) {
                is RSAPublicKey ->
                    RSASHA1Verify.get().encodePublicKey(pubKey)
                is DSAPublicKey ->
                    DSASHA1Verify.get().encodePublicKey(pubKey)
                is ECPublicKey ->
                    ECDSASHA2Verify.getVerifierForKey(pubKey).encodePublicKey(pubKey)
                is Ed25519PublicKey ->
                    Ed25519Verify.get().encodePublicKey(pubKey)
                else ->
                    null
            }
        } catch (_: IOException) {
            return null
        }
    }

    @Throws(
        NoSuchAlgorithmException::class,
        InvalidParameterSpecException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeySpecException::class,
        IllegalBlockSizeException::class,
        IOException::class
    )
    fun exportPEM(key: PrivateKey, secret: String?): String {
        val sb = StringBuilder()

        var data = key.encoded

        sb.append(PKCS8_START)
        sb.append('\n')

        if (secret != null) {
            val salt = ByteArray(8)
            val random = SecureRandom()
            random.nextBytes(salt)

            val defParams = PBEParameterSpec(salt, 1)
            val params = AlgorithmParameters.getInstance(key.algorithm)

            params.init(defParams)

            val pbeSpec = PBEKeySpec(secret.toCharArray())

            val keyFact = SecretKeyFactory.getInstance(key.algorithm)
            val cipher = Cipher.getInstance(key.algorithm)
            cipher.init(Cipher.WRAP_MODE, keyFact.generateSecret(pbeSpec), params)

            val wrappedKey = cipher.wrap(key)

            val pinfo = EncryptedPrivateKeyInfo(params, wrappedKey)

            data = pinfo.getEncoded()

            sb.append("Proc-Type: 4,ENCRYPTED\n")
            sb.append("DEK-Info: DES-EDE3-CBC,")
            sb.append(encodeHex(salt))
            sb.append("\n\n")
        }

        var i = sb.length
        sb.append(Base64.encode(data))
        i += 63
        while (i < sb.length) {
            sb.insert(i, "\n")
            i += 64
        }

        sb.append('\n')
        sb.append(PKCS8_END)
        sb.append('\n')

        return sb.toString()
    }

    private const val OPENSSH_PRIVATE_KEY_START = "-----BEGIN OPENSSH PRIVATE KEY-----"
    private const val OPENSSH_PRIVATE_KEY_END = "-----END OPENSSH PRIVATE KEY-----"
    private const val OPENSSH_KEY_V1_MAGIC = "openssh-key-v1\u0000"

    private val HEX_DIGITS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6',
        '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )

    @JvmStatic
    fun encodeHex(bytes: ByteArray): String {
        val hex = CharArray(bytes.size * 2)

        var i = 0
        for (b in bytes) {
            hex[i++] = HEX_DIGITS[(b.toInt() shr 4) and 0x0f]
            hex[i++] = HEX_DIGITS[b.toInt() and 0x0f]
        }

        return String(hex)
    }

    /**
     * Extract key type from OpenSSH format by reading the public key blob.
     * The public key section is not encrypted, so we can read it without the password.
     *
     * @param keyString The full OpenSSH key file content
     * @return The key type (RSA, DSA, EC, Ed25519) or null if not parseable
     */
    fun extractKeyTypeFromOpenSSH(keyString: String): String? {
        try {
            // Check for OpenSSH format
            if (!keyString.contains(OPENSSH_PRIVATE_KEY_START)) {
                return null
            }

            // Extract base64 content
            val startIdx = keyString.indexOf(OPENSSH_PRIVATE_KEY_START) + OPENSSH_PRIVATE_KEY_START.length
            val endIdx = keyString.indexOf(OPENSSH_PRIVATE_KEY_END)
            if (startIdx < 0 || endIdx < 0 || startIdx >= endIdx) return null

            val base64Content = keyString.substring(startIdx, endIdx)
                .replace("\n", "")
                .replace("\r", "")
                .trim()

            val decoded = Base64.decode(base64Content.toCharArray())
            val buffer = ByteBuffer.wrap(decoded)

            // Skip magic header "openssh-key-v1\0" (15 bytes)
            val magic = ByteArray(15)
            buffer.get(magic)
            if (String(magic, Charsets.US_ASCII) != OPENSSH_KEY_V1_MAGIC) {
                return null
            }

            // Skip cipher name (string)
            val cipherLen = buffer.int
            buffer.position(buffer.position() + cipherLen)

            // Skip kdf name (string)
            val kdfLen = buffer.int
            buffer.position(buffer.position() + kdfLen)

            // Skip kdf options (string)
            val kdfOptionsLen = buffer.int
            buffer.position(buffer.position() + kdfOptionsLen)

            // Skip number of keys (uint32)
            buffer.int

            // Read public key blob length
            val pubKeyBlobLen = buffer.int

            // Read key type from public key blob (first string in the blob)
            val keyTypeLen = buffer.int
            val keyTypeBytes = ByteArray(keyTypeLen)
            buffer.get(keyTypeBytes)
            val sshKeyType = String(keyTypeBytes, Charsets.UTF_8)

            // Convert SSH key type to ConnectBot internal type
            return when {
                sshKeyType == "ssh-rsa" -> "RSA"
                sshKeyType == "ssh-dss" -> "DSA"
                sshKeyType == "ssh-ed25519" -> "Ed25519"
                sshKeyType.startsWith("ecdsa-sha2-") -> "EC"
                else -> null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not extract key type from OpenSSH format", e)
            return null
        }
    }

    class BadPasswordException : Exception()
}
