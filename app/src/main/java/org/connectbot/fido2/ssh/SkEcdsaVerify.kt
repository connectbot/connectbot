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

import org.connectbot.fido2.Fido2SignatureResult
import org.connectbot.sshlib.sk.SkAlgorithm
import org.connectbot.sshlib.sk.SkPublicKey
import org.connectbot.sshlib.sk.SkSignatureBlob
import java.math.BigInteger

object SkEcdsaVerify {
    fun decodePublicKey(blob: ByteArray): SkPublicKey = decodeSecurityKey(blob, SkAlgorithm.ECDSA_P256)

    fun encodeSignature(result: Fido2SignatureResult): ByteArray {
        fun pack(signature: ByteArray) = SkSignatureBlob.pack(SkAlgorithm.ECDSA_P256, signature, result.flags, result.counter.toUInt())
        return try {
            pack(result.signature)
        } catch (e: Exception) {
            if (result.signature.size != 64) throw e
            val r = BigInteger(1, result.signature.copyOfRange(0, 32))
            val s = BigInteger(1, result.signature.copyOfRange(32, 64))
            val rBytes = r.toByteArray()
            val sBytes = s.toByteArray()
            val der = byteArrayOf(0x30, (4 + rBytes.size + sBytes.size).toByte(), 0x02, rBytes.size.toByte()) +
                rBytes + byteArrayOf(0x02, sBytes.size.toByte()) + sBytes
            pack(der)
        }
    }
}
