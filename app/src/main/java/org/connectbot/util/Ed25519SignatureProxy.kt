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

import com.trilead.ssh2.auth.SignatureProxy
import com.trilead.ssh2.packets.TypesWriter
import com.trilead.ssh2.signature.Ed25519Verify
import java.io.IOException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

/**
 * Signs SSH authentication requests with an Ed25519 key through the JCA
 * [Signature] API instead of sshlib's software implementation.
 *
 * sshlib's own Ed25519 signer needs access to the raw private key seed, which
 * an Android Keystore key never exposes. Routing the signature through a
 * [SignatureProxy] lets the Keystore's hardware-backed "Ed25519" Signature do
 * the signing while sshlib handles the rest of the publickey authentication.
 * https://github.com/connectbot/connectbot/issues/1974
 *
 * The public key handed to sshlib is converted to its own Ed25519 type so key
 * detection and encoding work no matter which provider produced the key.
 */
class Ed25519SignatureProxy(
    publicKey: PublicKey,
    private val privateKey: PrivateKey,
) : SignatureProxy(Ed25519Verify.convertPublicKey(publicKey)) {

    @Throws(IOException::class)
    override fun sign(message: ByteArray, hashAlgorithm: String): ByteArray {
        val rawSignature = try {
            // Provider selection walks the installed providers until one
            // accepts the key, so hardware-backed Keystore keys and software
            // PKCS#8 keys both end up with a working implementation.
            val signature = Signature.getInstance("Ed25519")
            signature.initSign(privateKey)
            signature.update(message)
            signature.sign()
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Ed25519 signing failed", e)
        }

        // Wrap the raw 64-byte RFC 8032 signature in SSH wire format.
        val tw = TypesWriter()
        tw.writeString(Ed25519Verify.ED25519_ID)
        tw.writeString(rawSignature, 0, rawSignature.size)
        return tw.bytes
    }
}
