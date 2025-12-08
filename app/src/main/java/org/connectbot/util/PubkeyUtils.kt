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
import com.trilead.ssh2.crypto.SimpleDERReader
import com.google.crypto.tink.subtle.Ed25519Sign
import com.trilead.ssh2.crypto.keys.Ed25519PrivateKey
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import com.trilead.ssh2.signature.DSASHA1Verify
import com.trilead.ssh2.signature.ECDSASHA2Verify
import com.trilead.ssh2.signature.Ed25519Verify
import com.trilead.ssh2.signature.RSASHA1Verify
import com.trilead.ssh2.signature.SSHSignature
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.connectbot.data.entity.Pubkey
import org.keyczar.jce.EcCore
import java.io.IOException
import java.math.BigInteger
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
import java.security.interfaces.DSAPrivateKey
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.DSAPublicKeySpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.InvalidParameterSpecException
import java.security.spec.KeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Arrays
import org.mindrot.jbcrypt.BCrypt
import javax.crypto.Cipher
import javax.crypto.EncryptedPrivateKeyInfo
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec
import javax.crypto.spec.SecretKeySpec

object PubkeyUtils {
    init {
        Ed25519Provider.insertIfNeeded()
    }

    private const val TAG = "CB.PubkeyUtils"

    const val PKCS8_START: String = "-----BEGIN PRIVATE KEY-----"
    const val PKCS8_END: String = "-----END PRIVATE KEY-----"

    // OpenSSH encrypted key constants
    private const val OPENSSH_CIPHER_AES256_CTR = "aes256-ctr"
    private const val OPENSSH_KDF_BCRYPT = "bcrypt"
    private const val OPENSSH_BCRYPT_SALT_SIZE = 16
    private const val OPENSSH_BCRYPT_ROUNDS = 16
    private const val OPENSSH_AES_KEY_SIZE = 32  // 256 bits
    private const val OPENSSH_AES_IV_SIZE = 16   // 128 bits
    private const val OPENSSH_AES_BLOCK_SIZE = 16

    // Size in bytes of salt to use.
    private const val SALT_SIZE = 8

    // Number of iterations for password hashing. PKCS#5 recommends 1000
    private const val ITERATIONS = 1000

    fun formatKey(key: Key): String {
        val algo = key.getAlgorithm()
        val fmt = key.getFormat()
        val encoded = key.getEncoded()
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

    /**
     * Derives a key and IV from a passphrase using bcrypt_pbkdf for OpenSSH encrypted keys.
     * @param passphrase The passphrase to derive from
     * @param salt The salt (typically 16 bytes)
     * @param rounds Number of bcrypt rounds (typically 16)
     * @return A byte array containing the key (32 bytes) followed by IV (16 bytes)
     */
    private fun deriveOpenSSHKey(passphrase: String, salt: ByteArray, rounds: Int): ByteArray {
        val keyLength = OPENSSH_AES_KEY_SIZE + OPENSSH_AES_IV_SIZE  // 48 bytes
        val output = ByteArray(keyLength)
        BCrypt().pbkdf(passphrase.toByteArray(Charsets.UTF_8), salt, rounds, output)
        return output
    }

    /**
     * Encrypts data using AES-256-CTR for OpenSSH encrypted key format.
     * @param data The plaintext data to encrypt
     * @param key The 32-byte AES key
     * @param iv The 16-byte IV
     * @return The encrypted data
     */
    private fun encryptAesCtr(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    /**
     * Decrypts data using AES-256-CTR for OpenSSH encrypted key format.
     * @param data The encrypted data
     * @param key The 32-byte AES key
     * @param iv The 16-byte IV
     * @return The decrypted data
     */
    private fun decryptAesCtr(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    @Throws(Exception::class)
    fun getEncodedPrivate(pk: PrivateKey, secret: String?): ByteArray {
        val encoded = pk.getEncoded()
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
        if (secret != null && secret.isNotEmpty()) return decodePrivate(
            decrypt(encoded, secret),
            keyType
        )
        else return decodePrivate(encoded, keyType)
    }

    @Throws(InvalidKeySpecException::class, NoSuchAlgorithmException::class)
    fun getBitStrength(encoded: ByteArray?, keyType: String?): Int {
        val pubKey = decodePublic(encoded, keyType)
        if ("RSA" == keyType) {
            return (pubKey as RSAPublicKey).getModulus().bitLength()
        } else if ("DSA" == keyType) {
            return 1024
        } else if ("EC" == keyType) {
            return (pubKey as ECPublicKey).getParams().getCurve().getField()
                .getFieldSize()
        } else if ("Ed25519" == keyType) {
            return 256
        } else {
            return 0
        }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun decodePublic(encoded: ByteArray?, keyType: String?): PublicKey {
        val pubKeySpec = X509EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance(keyType)
        return kf.generatePublic(pubKeySpec)
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class)
    fun getAlgorithmForOid(oid: String?): String {
        if ("1.2.840.10045.2.1" == oid) {
            return "EC"
        } else if ("1.2.840.113549.1.1.1" == oid) {
            return "RSA"
        } else if ("1.2.840.10040.4.1" == oid) {
            return "DSA"
        } else if ("1.3.101.112" == oid) {
            return "Ed25519"
        } else {
            throw NoSuchAlgorithmException("Unknown algorithm OID " + oid)
        }
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class)
    fun getOidFromPkcs8Encoded(encoded: ByteArray): String? {
        try {
            val reader = SimpleDERReader(encoded)
            reader.resetInput(reader.readSequenceAsByteArray())
            reader.readInt()
            reader.resetInput(reader.readSequenceAsByteArray())
            return reader.readOid()
        } catch (e: IOException) {
            Log.w(TAG, "Could not read OID", e)
            throw NoSuchAlgorithmException("Could not read key", e)
        }
    }

    @JvmStatic
    @Throws(InvalidKeySpecException::class)
    fun getRSAPublicExponentFromPkcs8Encoded(encoded: ByteArray): BigInteger? {
        try {
            val reader = SimpleDERReader(encoded)
            reader.resetInput(reader.readSequenceAsByteArray())
            if (reader.readInt() != BigInteger.ZERO) {
                throw InvalidKeySpecException("PKCS#8 is not version 0")
            }

            reader.readSequenceAsByteArray() // OID sequence
            reader.resetInput(reader.readOctetString()) // RSA key bytes
            reader.resetInput(reader.readSequenceAsByteArray()) // RSA key sequence

            if (reader.readInt() != BigInteger.ZERO) {
                throw InvalidKeySpecException("RSA key is not version 0")
            }

            reader.readInt() // modulus
            return reader.readInt() // public exponent
        } catch (e: IOException) {
            Log.w(TAG, "Could not read public exponent", e)
            throw InvalidKeySpecException("Could not read key", e)
        }
    }

    @Throws(BadPasswordException::class)
    fun convertToKeyPair(pubkey: Pubkey, password: String?): KeyPair? {
        if ("IMPORTED" == pubkey.type) {
            // load specific key using pem format
            try {
                return PEMDecoder.decode(
                    kotlin.text.String(pubkey.privateKey!!, charset("UTF-8")).toCharArray(),
                    password
                )
            } catch (e: Exception) {
                Log.e(TAG, "Cannot decode imported key", e)
                throw BadPasswordException()
            }
        } else {
            // load using internal generated format
            try {
                val privKey = PubkeyUtils.decodePrivate(pubkey.privateKey!!, pubkey.type, password)
                val pubKey = decodePublic(pubkey.publicKey, pubkey.type)
                Log.d(TAG, "Unlocked key " + formatKey(pubKey))

                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot decode pubkey from database", e)
                throw BadPasswordException()
            }
        }
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun recoverKeyPair(encoded: ByteArray): KeyPair {
        val algo = getAlgorithmForOid(getOidFromPkcs8Encoded(encoded))

        val privKeySpec: KeySpec = PKCS8EncodedKeySpec(encoded)

        val kf = KeyFactory.getInstance(algo)
        val priv = kf.generatePrivate(privKeySpec)

        // Ed25519 requires special handling to derive the public key
        if (priv is Ed25519PrivateKey) {
            val seed = priv.getSeed()
            val tinkKeyPair = Ed25519Sign.KeyPair.newKeyPairFromSeed(seed)
            val publicKey = Ed25519PublicKey(tinkKeyPair.publicKey)
            return KeyPair(publicKey, priv)
        }

        return KeyPair(recoverPublicKey(kf, priv), priv)
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun recoverPublicKey(kf: KeyFactory, priv: PrivateKey?): PublicKey {
        if (priv is RSAPrivateCrtKey) {
            val rsaPriv = priv
            return kf.generatePublic(
                RSAPublicKeySpec(
                    rsaPriv.getModulus(), rsaPriv
                        .getPublicExponent()
                )
            )
        } else if (priv is RSAPrivateKey) {
            val publicExponent = getRSAPublicExponentFromPkcs8Encoded(priv.getEncoded())
            val rsaPriv = priv
            return kf.generatePublic(RSAPublicKeySpec(rsaPriv.getModulus(), publicExponent))
        } else if (priv is DSAPrivateKey) {
            val dsaPriv = priv
            val params = dsaPriv.getParams()

            // Calculate public key Y
            val y = params.getG().modPow(dsaPriv.getX(), params.getP())

            return kf.generatePublic(
                DSAPublicKeySpec(
                    y, params.getP(), params.getQ(), params
                        .getG()
                )
            )
        } else if (priv is ECPrivateKey) {
            val ecPriv = priv
            val params = ecPriv.getParams()

            // Calculate public key Y
            val generator = params.getGenerator()
            val wCoords = EcCore.multiplyPointA(
                arrayOf<BigInteger?>(
                    generator.getAffineX(),
                    generator.getAffineY()
                ), ecPriv.getS(), params
            )
            val w = ECPoint(wCoords[0], wCoords[1])

            return kf.generatePublic(ECPublicKeySpec(w, params))
        } else {
            throw NoSuchAlgorithmException("Key type must be RSA, DSA, or EC")
        }
    }

    /*
	 * OpenSSH compatibility methods
	 */
    @Throws(IOException::class, InvalidKeyException::class)
    fun convertToOpenSSHFormat(pk: PublicKey?, origNickname: String?): String {
        var nickname = origNickname
        if (nickname == null) nickname = "connectbot@android"

        if (pk is RSAPublicKey) {
            var data = "ssh-rsa "
            data += String(Base64.encode(RSASHA1Verify.get().encodePublicKey(pk)))
            return "$data $nickname"
        } else if (pk is DSAPublicKey) {
            var data = "ssh-dss "
            data += String(Base64.encode(DSASHA1Verify.get().encodePublicKey(pk)))
            return "$data $nickname"
        } else if (pk is ECPublicKey) {
            val ecPub = pk
            val keyType = ECDSASHA2Verify.getSshKeyType(ecPub)
            val verifier: SSHSignature = ECDSASHA2Verify.getVerifierForKey(ecPub)
            val keyData = String(Base64.encode(verifier.encodePublicKey(ecPub)))
            return "$keyType $keyData $nickname"
        } else if (pk is Ed25519PublicKey) {
            val edPub = pk
            return Ed25519Verify.ED25519_ID + " " + String(
                Base64.encode(
                    Ed25519Verify.get().encodePublicKey(edPub)
                )
            ) +
                    " " + nickname
        }

        throw InvalidKeyException("Unknown key type")
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
            val pubKey = pair.getPublic()
            if (pubKey is RSAPublicKey) {
                return RSASHA1Verify.get().encodePublicKey(pubKey)
            } else if (pubKey is DSAPublicKey) {
                return DSASHA1Verify.get().encodePublicKey(pubKey)
            } else if (pubKey is ECPublicKey) {
                return ECDSASHA2Verify.getVerifierForKey(pubKey).encodePublicKey(pubKey)
            } else if (pubKey is Ed25519PublicKey) {
                return Ed25519Verify.get().encodePublicKey(pubKey)
            } else {
                return null
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

        var data = key.getEncoded()

        sb.append(PKCS8_START)
        sb.append('\n')

        if (secret != null) {
            val salt = ByteArray(8)
            val random = SecureRandom()
            random.nextBytes(salt)

            val defParams = PBEParameterSpec(salt, 1)
            val params = AlgorithmParameters.getInstance(key.getAlgorithm())

            params.init(defParams)

            val pbeSpec = PBEKeySpec(secret.toCharArray())

            val keyFact = SecretKeyFactory.getInstance(key.getAlgorithm())
            val cipher = Cipher.getInstance(key.getAlgorithm())
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
    private const val ED25519_KEY_TYPE = "ssh-ed25519"

    /**
     * Exports an Ed25519 key pair in OpenSSH format (unencrypted).
     */
    fun exportOpenSSHEd25519(
        privateKey: Ed25519PrivateKey,
        publicKey: Ed25519PublicKey,
        comment: String?
    ): String {
        return exportOpenSSHEd25519(privateKey, publicKey, comment, null)
    }

    /**
     * Exports an Ed25519 key pair in OpenSSH format.
     * This is the standard format used by ssh-keygen and compatible with other SSH tools.
     *
     * @param privateKey The Ed25519 private key
     * @param publicKey The Ed25519 public key
     * @param comment Optional comment (typically the key nickname)
     * @param passphrase Optional passphrase for encryption. If null or empty, key is unencrypted.
     * @return The key in OpenSSH PEM format
     */
    fun exportOpenSSHEd25519(
        privateKey: Ed25519PrivateKey,
        publicKey: Ed25519PublicKey,
        comment: String?,
        passphrase: String?
    ): String {
        val encrypted = !passphrase.isNullOrEmpty()
        val baos = ByteArrayOutputStream()

        // Magic header: "openssh-key-v1\0"
        baos.write(OPENSSH_KEY_V1_MAGIC.toByteArray(Charsets.US_ASCII))

        // Generate salt for encryption if needed
        val salt = if (encrypted) {
            ByteArray(OPENSSH_BCRYPT_SALT_SIZE).also { SecureRandom().nextBytes(it) }
        } else null

        // Cipher name
        writeString(baos, if (encrypted) OPENSSH_CIPHER_AES256_CTR else "none")

        // KDF name
        writeString(baos, if (encrypted) OPENSSH_KDF_BCRYPT else "none")

        // KDF options
        if (encrypted && salt != null) {
            val kdfOptions = ByteArrayOutputStream()
            writeBytes(kdfOptions, salt)
            writeUint32(kdfOptions, OPENSSH_BCRYPT_ROUNDS)
            writeBytes(baos, kdfOptions.toByteArray())
        } else {
            writeString(baos, "")
        }

        // Number of keys: 1
        writeUint32(baos, 1)

        // Public key blob
        val publicKeyBytes = publicKey.getAbyte()
        val pubKeyBlob = ByteArrayOutputStream()
        writeString(pubKeyBlob, ED25519_KEY_TYPE)
        writeBytes(pubKeyBlob, publicKeyBytes)
        writeBytes(baos, pubKeyBlob.toByteArray())

        // Private key section
        val privateSection = ByteArrayOutputStream()

        // Check integers (random, must match for verification)
        val checkInt = SecureRandom().nextInt()
        writeUint32(privateSection, checkInt)
        writeUint32(privateSection, checkInt)

        // Key type
        writeString(privateSection, ED25519_KEY_TYPE)

        // Public key
        writeBytes(privateSection, publicKeyBytes)

        // Private key: 64 bytes (32-byte seed + 32-byte public key)
        val seed = privateKey.getSeed()
        val privateKeyData = ByteArray(64)
        System.arraycopy(seed, 0, privateKeyData, 0, 32)
        System.arraycopy(publicKeyBytes, 0, privateKeyData, 32, 32)
        writeBytes(privateSection, privateKeyData)

        // Comment
        writeString(privateSection, comment ?: "")

        // Padding to block size (16 for encrypted, 8 for unencrypted)
        val blockSize = if (encrypted) OPENSSH_AES_BLOCK_SIZE else 8
        var paddingNeeded = blockSize - (privateSection.size() % blockSize)
        if (paddingNeeded == blockSize) paddingNeeded = 0
        for (i in 1..paddingNeeded) {
            privateSection.write(i)
        }

        // Encrypt if passphrase provided
        val privateSectionBytes = if (encrypted && salt != null && passphrase != null) {
            val derivedKey = deriveOpenSSHKey(passphrase, salt, OPENSSH_BCRYPT_ROUNDS)
            val key = derivedKey.copyOfRange(0, OPENSSH_AES_KEY_SIZE)
            val iv = derivedKey.copyOfRange(OPENSSH_AES_KEY_SIZE, OPENSSH_AES_KEY_SIZE + OPENSSH_AES_IV_SIZE)
            encryptAesCtr(privateSection.toByteArray(), key, iv)
        } else {
            privateSection.toByteArray()
        }

        // Write the private section
        writeBytes(baos, privateSectionBytes)

        // Base64 encode and format
        val sb = StringBuilder()
        sb.append(OPENSSH_PRIVATE_KEY_START)
        sb.append('\n')

        var i = sb.length
        sb.append(Base64.encode(baos.toByteArray()))
        i += 70
        while (i < sb.length) {
            sb.insert(i, "\n")
            i += 71
        }

        sb.append('\n')
        sb.append(OPENSSH_PRIVATE_KEY_END)
        sb.append('\n')

        return sb.toString()
    }

    private fun writeUint32(baos: ByteArrayOutputStream, value: Int) {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(value)
        baos.write(buffer.array())
    }

    private fun writeString(baos: ByteArrayOutputStream, value: String) {
        writeBytes(baos, value.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytes(baos: ByteArrayOutputStream, data: ByteArray) {
        writeUint32(baos, data.size)
        baos.write(data)
    }

    /**
     * Writes a BigInteger as an SSH mpint (multi-precision integer).
     * SSH mpints are stored as a 4-byte length followed by the value in big-endian format.
     * If the high bit is set, a leading zero byte is prepended.
     */
    private fun writeMpint(baos: ByteArrayOutputStream, value: BigInteger) {
        var bytes = value.toByteArray()
        // BigInteger.toByteArray() already handles sign extension correctly,
        // but may include an extra leading zero if the high bit is set.
        // This is actually what we want for SSH mpints.
        writeBytes(baos, bytes)
    }

    /**
     * Exports an RSA key pair in OpenSSH format (unencrypted).
     */
    fun exportOpenSSHRSA(
        privateKey: RSAPrivateCrtKey,
        publicKey: RSAPublicKey,
        comment: String?
    ): String {
        return exportOpenSSHRSA(privateKey, publicKey, comment, null)
    }

    /**
     * Exports an RSA key pair in OpenSSH format.
     * @param passphrase Optional passphrase for encryption. If null or empty, key is unencrypted.
     */
    fun exportOpenSSHRSA(
        privateKey: RSAPrivateCrtKey,
        publicKey: RSAPublicKey,
        comment: String?,
        passphrase: String?
    ): String {
        val encrypted = !passphrase.isNullOrEmpty()
        val baos = ByteArrayOutputStream()

        // Magic header
        baos.write(OPENSSH_KEY_V1_MAGIC.toByteArray(Charsets.US_ASCII))

        // Generate salt for encryption if needed
        val salt = if (encrypted) {
            ByteArray(OPENSSH_BCRYPT_SALT_SIZE).also { SecureRandom().nextBytes(it) }
        } else null

        // Cipher name
        writeString(baos, if (encrypted) OPENSSH_CIPHER_AES256_CTR else "none")

        // KDF name
        writeString(baos, if (encrypted) OPENSSH_KDF_BCRYPT else "none")

        // KDF options
        if (encrypted && salt != null) {
            val kdfOptions = ByteArrayOutputStream()
            writeBytes(kdfOptions, salt)
            writeUint32(kdfOptions, OPENSSH_BCRYPT_ROUNDS)
            writeBytes(baos, kdfOptions.toByteArray())
        } else {
            writeString(baos, "")
        }

        // Number of keys
        writeUint32(baos, 1)

        // Public key blob: ssh-rsa, e, n
        val pubKeyBlob = ByteArrayOutputStream()
        writeString(pubKeyBlob, "ssh-rsa")
        writeMpint(pubKeyBlob, publicKey.publicExponent)
        writeMpint(pubKeyBlob, publicKey.modulus)
        writeBytes(baos, pubKeyBlob.toByteArray())

        // Private key section
        val privateSection = ByteArrayOutputStream()
        val checkInt = SecureRandom().nextInt()
        writeUint32(privateSection, checkInt)
        writeUint32(privateSection, checkInt)

        writeString(privateSection, "ssh-rsa")
        writeMpint(privateSection, publicKey.modulus)          // n
        writeMpint(privateSection, publicKey.publicExponent)   // e
        writeMpint(privateSection, privateKey.privateExponent) // d
        writeMpint(privateSection, privateKey.crtCoefficient)  // iqmp (q^-1 mod p)
        writeMpint(privateSection, privateKey.primeP)          // p
        writeMpint(privateSection, privateKey.primeQ)          // q

        writeString(privateSection, comment ?: "")

        // Padding to block size (16 for encrypted, 8 for unencrypted)
        val blockSize = if (encrypted) OPENSSH_AES_BLOCK_SIZE else 8
        var paddingNeeded = blockSize - (privateSection.size() % blockSize)
        if (paddingNeeded == blockSize) paddingNeeded = 0
        for (i in 1..paddingNeeded) {
            privateSection.write(i)
        }

        // Encrypt if passphrase provided
        val privateSectionBytes = if (encrypted && salt != null && passphrase != null) {
            val derivedKey = deriveOpenSSHKey(passphrase, salt, OPENSSH_BCRYPT_ROUNDS)
            val key = derivedKey.copyOfRange(0, OPENSSH_AES_KEY_SIZE)
            val iv = derivedKey.copyOfRange(OPENSSH_AES_KEY_SIZE, OPENSSH_AES_KEY_SIZE + OPENSSH_AES_IV_SIZE)
            encryptAesCtr(privateSection.toByteArray(), key, iv)
        } else {
            privateSection.toByteArray()
        }

        writeBytes(baos, privateSectionBytes)

        return formatOpenSSHKey(baos.toByteArray())
    }

    /**
     * Exports a DSA key pair in OpenSSH format (unencrypted).
     */
    fun exportOpenSSHDSA(
        privateKey: DSAPrivateKey,
        publicKey: DSAPublicKey,
        comment: String?
    ): String {
        return exportOpenSSHDSA(privateKey, publicKey, comment, null)
    }

    /**
     * Exports a DSA key pair in OpenSSH format.
     * @param passphrase Optional passphrase for encryption. If null or empty, key is unencrypted.
     */
    fun exportOpenSSHDSA(
        privateKey: DSAPrivateKey,
        publicKey: DSAPublicKey,
        comment: String?,
        passphrase: String?
    ): String {
        val encrypted = !passphrase.isNullOrEmpty()
        val baos = ByteArrayOutputStream()

        // Magic header
        baos.write(OPENSSH_KEY_V1_MAGIC.toByteArray(Charsets.US_ASCII))

        // Generate salt for encryption if needed
        val salt = if (encrypted) {
            ByteArray(OPENSSH_BCRYPT_SALT_SIZE).also { SecureRandom().nextBytes(it) }
        } else null

        // Cipher name
        writeString(baos, if (encrypted) OPENSSH_CIPHER_AES256_CTR else "none")

        // KDF name
        writeString(baos, if (encrypted) OPENSSH_KDF_BCRYPT else "none")

        // KDF options
        if (encrypted && salt != null) {
            val kdfOptions = ByteArrayOutputStream()
            writeBytes(kdfOptions, salt)
            writeUint32(kdfOptions, OPENSSH_BCRYPT_ROUNDS)
            writeBytes(baos, kdfOptions.toByteArray())
        } else {
            writeString(baos, "")
        }

        // Number of keys
        writeUint32(baos, 1)

        val params = publicKey.params

        // Public key blob: ssh-dss, p, q, g, y
        val pubKeyBlob = ByteArrayOutputStream()
        writeString(pubKeyBlob, "ssh-dss")
        writeMpint(pubKeyBlob, params.p)
        writeMpint(pubKeyBlob, params.q)
        writeMpint(pubKeyBlob, params.g)
        writeMpint(pubKeyBlob, publicKey.y)
        writeBytes(baos, pubKeyBlob.toByteArray())

        // Private key section
        val privateSection = ByteArrayOutputStream()
        val checkInt = SecureRandom().nextInt()
        writeUint32(privateSection, checkInt)
        writeUint32(privateSection, checkInt)

        writeString(privateSection, "ssh-dss")
        writeMpint(privateSection, params.p)
        writeMpint(privateSection, params.q)
        writeMpint(privateSection, params.g)
        writeMpint(privateSection, publicKey.y)
        writeMpint(privateSection, privateKey.x)

        writeString(privateSection, comment ?: "")

        // Padding to block size (16 for encrypted, 8 for unencrypted)
        val blockSize = if (encrypted) OPENSSH_AES_BLOCK_SIZE else 8
        var paddingNeeded = blockSize - (privateSection.size() % blockSize)
        if (paddingNeeded == blockSize) paddingNeeded = 0
        for (i in 1..paddingNeeded) {
            privateSection.write(i)
        }

        // Encrypt if passphrase provided
        val privateSectionBytes = if (encrypted && salt != null && passphrase != null) {
            val derivedKey = deriveOpenSSHKey(passphrase, salt, OPENSSH_BCRYPT_ROUNDS)
            val key = derivedKey.copyOfRange(0, OPENSSH_AES_KEY_SIZE)
            val iv = derivedKey.copyOfRange(OPENSSH_AES_KEY_SIZE, OPENSSH_AES_KEY_SIZE + OPENSSH_AES_IV_SIZE)
            encryptAesCtr(privateSection.toByteArray(), key, iv)
        } else {
            privateSection.toByteArray()
        }

        writeBytes(baos, privateSectionBytes)

        return formatOpenSSHKey(baos.toByteArray())
    }

    /**
     * Exports an EC key pair in OpenSSH format (unencrypted).
     */
    fun exportOpenSSHEC(
        privateKey: ECPrivateKey,
        publicKey: ECPublicKey,
        comment: String?
    ): String {
        return exportOpenSSHEC(privateKey, publicKey, comment, null)
    }

    /**
     * Exports an EC key pair in OpenSSH format.
     * @param passphrase Optional passphrase for encryption. If null or empty, key is unencrypted.
     */
    fun exportOpenSSHEC(
        privateKey: ECPrivateKey,
        publicKey: ECPublicKey,
        comment: String?,
        passphrase: String?
    ): String {
        val encrypted = !passphrase.isNullOrEmpty()
        val baos = ByteArrayOutputStream()

        // Determine curve name and key type
        val fieldSize = publicKey.params.curve.field.fieldSize
        val (curveName, keyType) = when (fieldSize) {
            256 -> "nistp256" to "ecdsa-sha2-nistp256"
            384 -> "nistp384" to "ecdsa-sha2-nistp384"
            521 -> "nistp521" to "ecdsa-sha2-nistp521"
            else -> throw InvalidKeyException("Unsupported EC curve size: $fieldSize")
        }

        // Magic header
        baos.write(OPENSSH_KEY_V1_MAGIC.toByteArray(Charsets.US_ASCII))

        // Generate salt for encryption if needed
        val salt = if (encrypted) {
            ByteArray(OPENSSH_BCRYPT_SALT_SIZE).also { SecureRandom().nextBytes(it) }
        } else null

        // Cipher name
        writeString(baos, if (encrypted) OPENSSH_CIPHER_AES256_CTR else "none")

        // KDF name
        writeString(baos, if (encrypted) OPENSSH_KDF_BCRYPT else "none")

        // KDF options
        if (encrypted && salt != null) {
            val kdfOptions = ByteArrayOutputStream()
            writeBytes(kdfOptions, salt)
            writeUint32(kdfOptions, OPENSSH_BCRYPT_ROUNDS)
            writeBytes(baos, kdfOptions.toByteArray())
        } else {
            writeString(baos, "")
        }

        // Number of keys
        writeUint32(baos, 1)

        // Encode public key point in uncompressed format (0x04 || x || y)
        val publicPoint = encodeECPublicKeyPoint(publicKey)

        // Public key blob: key_type, curve_name, Q
        val pubKeyBlob = ByteArrayOutputStream()
        writeString(pubKeyBlob, keyType)
        writeString(pubKeyBlob, curveName)
        writeBytes(pubKeyBlob, publicPoint)
        writeBytes(baos, pubKeyBlob.toByteArray())

        // Private key section
        val privateSection = ByteArrayOutputStream()
        val checkInt = SecureRandom().nextInt()
        writeUint32(privateSection, checkInt)
        writeUint32(privateSection, checkInt)

        writeString(privateSection, keyType)
        writeString(privateSection, curveName)
        writeBytes(privateSection, publicPoint)
        writeMpint(privateSection, privateKey.s)

        writeString(privateSection, comment ?: "")

        // Padding to block size (16 for encrypted, 8 for unencrypted)
        val blockSize = if (encrypted) OPENSSH_AES_BLOCK_SIZE else 8
        var paddingNeeded = blockSize - (privateSection.size() % blockSize)
        if (paddingNeeded == blockSize) paddingNeeded = 0
        for (i in 1..paddingNeeded) {
            privateSection.write(i)
        }

        // Encrypt if passphrase provided
        val privateSectionBytes = if (encrypted && salt != null && passphrase != null) {
            val derivedKey = deriveOpenSSHKey(passphrase, salt, OPENSSH_BCRYPT_ROUNDS)
            val key = derivedKey.copyOfRange(0, OPENSSH_AES_KEY_SIZE)
            val iv = derivedKey.copyOfRange(OPENSSH_AES_KEY_SIZE, OPENSSH_AES_KEY_SIZE + OPENSSH_AES_IV_SIZE)
            encryptAesCtr(privateSection.toByteArray(), key, iv)
        } else {
            privateSection.toByteArray()
        }

        writeBytes(baos, privateSectionBytes)

        return formatOpenSSHKey(baos.toByteArray())
    }

    /**
     * Encodes an EC public key point in uncompressed format (0x04 || x || y).
     */
    private fun encodeECPublicKeyPoint(publicKey: ECPublicKey): ByteArray {
        val params = publicKey.params
        val fieldSize = (params.curve.field.fieldSize + 7) / 8  // bytes needed
        val w = publicKey.w

        val x = w.affineX.toByteArray()
        val y = w.affineY.toByteArray()

        // Pad or trim to exact field size
        val xPadded = padOrTrimToLength(x, fieldSize)
        val yPadded = padOrTrimToLength(y, fieldSize)

        // Uncompressed point format: 0x04 || x || y
        val result = ByteArray(1 + fieldSize * 2)
        result[0] = 0x04
        System.arraycopy(xPadded, 0, result, 1, fieldSize)
        System.arraycopy(yPadded, 0, result, 1 + fieldSize, fieldSize)

        return result
    }

    /**
     * Pads (with leading zeros) or trims (removes leading zeros) a byte array to exact length.
     */
    private fun padOrTrimToLength(bytes: ByteArray, length: Int): ByteArray {
        return when {
            bytes.size == length -> bytes
            bytes.size > length -> {
                // Trim leading zeros (BigInteger may add a sign byte)
                val offset = bytes.size - length
                bytes.copyOfRange(offset, bytes.size)
            }
            else -> {
                // Pad with leading zeros
                val result = ByteArray(length)
                System.arraycopy(bytes, 0, result, length - bytes.size, bytes.size)
                result
            }
        }
    }

    /**
     * Formats raw key data into OpenSSH PEM format with proper line wrapping.
     */
    private fun formatOpenSSHKey(data: ByteArray): String {
        val sb = StringBuilder()
        sb.append(OPENSSH_PRIVATE_KEY_START)
        sb.append('\n')

        var i = sb.length
        sb.append(Base64.encode(data))
        i += 70
        while (i < sb.length) {
            sb.insert(i, "\n")
            i += 71
        }

        sb.append('\n')
        sb.append(OPENSSH_PRIVATE_KEY_END)
        sb.append('\n')

        return sb.toString()
    }

    /**
     * Exports any supported key pair in OpenSSH format (unencrypted).
     * @param privateKey The private key (RSA, DSA, EC, or Ed25519)
     * @param publicKey The public key
     * @param comment Optional comment
     * @return The key in OpenSSH format, or null if the key type is not supported
     */
    fun exportOpenSSH(privateKey: PrivateKey, publicKey: PublicKey, comment: String?): String? {
        return exportOpenSSH(privateKey, publicKey, comment, null)
    }

    /**
     * Exports any supported key pair in OpenSSH format.
     * @param privateKey The private key (RSA, DSA, EC, or Ed25519)
     * @param publicKey The public key
     * @param comment Optional comment
     * @param passphrase Optional passphrase for encryption. If null or empty, key is unencrypted.
     * @return The key in OpenSSH format, or null if the key type is not supported
     */
    fun exportOpenSSH(privateKey: PrivateKey, publicKey: PublicKey, comment: String?, passphrase: String?): String? {
        return when {
            privateKey is RSAPrivateCrtKey && publicKey is RSAPublicKey ->
                exportOpenSSHRSA(privateKey, publicKey, comment, passphrase)
            privateKey is DSAPrivateKey && publicKey is DSAPublicKey ->
                exportOpenSSHDSA(privateKey, publicKey, comment, passphrase)
            privateKey is ECPrivateKey && publicKey is ECPublicKey ->
                exportOpenSSHEC(privateKey, publicKey, comment, passphrase)
            privateKey is Ed25519PrivateKey && publicKey is Ed25519PublicKey ->
                exportOpenSSHEd25519(privateKey, publicKey, comment, passphrase)
            else -> null
        }
    }

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

    class BadPasswordException : Exception()
}
