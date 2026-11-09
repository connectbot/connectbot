/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
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

package org.connectbot.fido2.ssh

import org.connectbot.ctap2.Fido2SignatureResult
import org.connectbot.sshlib.sk.SkAlgorithm
import org.connectbot.sshlib.sk.SkPublicKey
import org.connectbot.sshlib.sk.SkPublicKeyDecoder
import org.connectbot.sshlib.sk.SkSignatureBlob
import java.io.IOException

object SkEd25519Verify {
    fun decodePublicKey(blob: ByteArray): SkPublicKey = decodeSecurityKey(blob, SkAlgorithm.ED25519)

    fun encodeSignature(result: Fido2SignatureResult): ByteArray {
        val signature = if (result.signature.size > 64) result.signature.takeLast(64).toByteArray() else result.signature
        return SkSignatureBlob.pack(SkAlgorithm.ED25519, signature, result.flags, result.counter.toUInt())
    }
}

internal fun decodeSecurityKey(blob: ByteArray, algorithm: SkAlgorithm): SkPublicKey {
    val prefix = algorithm.sshName.toByteArray(Charsets.UTF_8)
    val type = java.nio.ByteBuffer.allocate(4 + prefix.size).putInt(prefix.size).put(prefix).array()
    val complete = if (blob.size >= type.size && blob.copyOfRange(0, type.size).contentEquals(type)) blob else type + blob
    val decoded = try {
        SkPublicKeyDecoder.decode(complete)
    } catch (e: Exception) {
        throw IOException("Invalid ${algorithm.sshName} public key: ${e.message}", e)
    }
    if (decoded.algorithm != algorithm) throw IOException("Unexpected security key algorithm: ${decoded.algorithm}")
    return decoded
}
