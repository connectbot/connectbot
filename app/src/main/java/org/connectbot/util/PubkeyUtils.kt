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

import org.connectbot.data.entity.Pubkey
import org.connectbot.sshlib.SshKeys
import timber.log.Timber
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPair
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays

object PubkeyUtils {
    init {
        SshKeys.ensureEd25519Support()
    }

    private const val TAG = "CB.PubkeyUtils"

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
    fun decodePrivate(encoded: ByteArray, keyType: String?, secret: String?): PrivateKey? = if (secret != null && secret.isNotEmpty()) {
        decodePrivate(
            decrypt(encoded, secret),
            keyType
        )
    } else {
        decodePrivate(encoded, keyType)
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
                return SshKeys.decodePemPrivateKey(
                    String(pubkey.privateKey!!, charset("UTF-8")),
                    password
                )
            } catch (e: Exception) {
                Timber.e(e, "Cannot decode imported key")
                throw BadPasswordException()
            }
        } else {
            // load using internal generated format
            try {
                val privKey = decodePrivate(pubkey.privateKey!!, pubkey.type, password)
                val pubKey = decodePublic(pubkey.publicKey, pubkey.type)
                Timber.d("Unlocked key " + formatKey(pubKey))

                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                Timber.e(e, "Cannot decode pubkey from database")
                throw BadPasswordException()
            }
        }
    }

    class BadPasswordException : Exception()
}
