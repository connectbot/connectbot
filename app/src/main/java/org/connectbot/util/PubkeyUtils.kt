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
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import com.trilead.ssh2.signature.DSASHA1Verify
import com.trilead.ssh2.signature.ECDSASHA2Verify
import com.trilead.ssh2.signature.Ed25519Verify
import com.trilead.ssh2.signature.RSASHA1Verify
import com.trilead.ssh2.signature.SSHSignature
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
