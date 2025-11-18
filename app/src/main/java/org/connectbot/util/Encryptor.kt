/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root
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

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * This class is from:
 *
 * Encryptor.java
 * Copyright 2008 Zach Scrivena
 * zachscrivena@gmail.com
 * http://zs.freeshell.org/
 */

/**
 * Perform AES-128 encryption.
 */
object Encryptor {
    /** name of the character set to use for converting between characters and bytes  */
    private const val CHARSET_NAME = "UTF-8"

    /** random number generator algorithm  */
    private const val RNG_ALGORITHM = "SHA1PRNG"

    /** message digest algorithm (must be sufficiently long to provide the key and initialization vector)  */
    private const val DIGEST_ALGORITHM = "SHA-256"

    /** key algorithm (must be compatible with CIPHER_ALGORITHM)  */
    private const val KEY_ALGORITHM = "AES"

    /** cipher algorithm (must be compatible with KEY_ALGORITHM)  */
    private const val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"


    /**
     * Encrypt the specified cleartext using the given password.
     * With the correct salt, number of iterations, and password, the decrypt() method reverses
     * the effect of this method.
     * This method generates and uses a random salt, and the user-specified number of iterations
     * and password to create a 16-byte secret key and 16-byte initialization vector.
     * The secret key and initialization vector are then used in the AES-128 cipher to encrypt
     * the given cleartext.
     *
     * @param salt
     * salt that was used in the encryption (to be populated)
     * @param iterations
     * number of iterations to use in salting
     * @param password
     * password to be used for encryption
     * @param cleartext
     * cleartext to be encrypted
     * @return
     * ciphertext
     * @throws Exception
     * on any error encountered in encryption
     */
    @Throws(Exception::class)
    fun encrypt(
        salt: ByteArray,
        iterations: Int,
        password: String,
        cleartext: ByteArray?
    ): ByteArray? {
        /* generate salt randomly */
        SecureRandom.getInstance(RNG_ALGORITHM).nextBytes(salt)

        /* compute key and initialization vector */
        val shaDigest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        var pw = password.toByteArray(charset(CHARSET_NAME))

        for (i in 0..<iterations) {
            /* add salt */
            val salted = ByteArray(pw.size + salt.size)
            System.arraycopy(pw, 0, salted, 0, pw.size)
            System.arraycopy(salt, 0, salted, pw.size, salt.size)
            Arrays.fill(pw, 0x00.toByte())

            /* compute SHA-256 digest */
            shaDigest.reset()
            pw = shaDigest.digest(salted)
            Arrays.fill(salted, 0x00.toByte())
        }

        /* extract the 16-byte key and initialization vector from the SHA-256 digest */
        val key = ByteArray(16)
        val iv = ByteArray(16)
        System.arraycopy(pw, 0, key, 0, 16)
        System.arraycopy(pw, 16, iv, 0, 16)
        Arrays.fill(pw, 0x00.toByte())

        /* perform AES-128 encryption */
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)

        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, KEY_ALGORITHM),
            IvParameterSpec(iv)
        )

        Arrays.fill(key, 0x00.toByte())
        Arrays.fill(iv, 0x00.toByte())

        return cipher.doFinal(cleartext)
    }


    /**
     * Decrypt the specified ciphertext using the given password.
     * With the correct salt, number of iterations, and password, this method reverses the effect
     * of the encrypt() method.
     * This method uses the user-specified salt, number of iterations, and password
     * to recreate the 16-byte secret key and 16-byte initialization vector.
     * The secret key and initialization vector are then used in the AES-128 cipher to decrypt
     * the given ciphertext.
     *
     * @param salt
     * salt to be used in decryption
     * @param iterations
     * number of iterations to use in salting
     * @param password
     * password to be used for decryption
     * @param ciphertext
     * ciphertext to be decrypted
     * @return
     * cleartext
     * @throws Exception
     * on any error encountered in decryption
     */
    @Throws(Exception::class)
    fun decrypt(
        salt: ByteArray,
        iterations: Int,
        password: String,
        ciphertext: ByteArray?
    ): ByteArray? {
        /* compute key and initialization vector */
        val shaDigest = MessageDigest.getInstance(DIGEST_ALGORITHM)
        var pw = password.toByteArray(charset(CHARSET_NAME))

        for (i in 0..<iterations) {
            /* add salt */
            val salted = ByteArray(pw.size + salt.size)
            System.arraycopy(pw, 0, salted, 0, pw.size)
            System.arraycopy(salt, 0, salted, pw.size, salt.size)
            Arrays.fill(pw, 0x00.toByte())

            /* compute SHA-256 digest */
            shaDigest.reset()
            pw = shaDigest.digest(salted)
            Arrays.fill(salted, 0x00.toByte())
        }

        /* extract the 16-byte key and initialization vector from the SHA-256 digest */
        val key = ByteArray(16)
        val iv = ByteArray(16)
        System.arraycopy(pw, 0, key, 0, 16)
        System.arraycopy(pw, 16, iv, 0, 16)
        Arrays.fill(pw, 0x00.toByte())

        /* perform AES-128 decryption */
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)

        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, KEY_ALGORITHM),
            IvParameterSpec(iv)
        )

        Arrays.fill(key, 0x00.toByte())
        Arrays.fill(iv, 0x00.toByte())

        return cipher.doFinal(ciphertext)
    }
}
