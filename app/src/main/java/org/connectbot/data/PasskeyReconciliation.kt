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

package org.connectbot.data

import org.connectbot.data.entity.Pubkey
import org.connectbot.sshlib.sk.SkAlgorithm
import org.connectbot.sshlib.sk.SkPublicKeyDecoder

class PasskeyIdentityConflict(val nickname: String) : IllegalStateException()
class PasskeyEntriesChanged : IllegalStateException()
class PasskeyNicknameExists : IllegalArgumentException()

/** Matches cryptographic identities, never nicknames. */
object PasskeyReconciliation {
    fun matches(candidate: Pubkey, entries: List<Pubkey>): List<Pubkey> {
        require(candidate.isPasskey && candidate.credentialId?.isNotEmpty() == true)
        val expected = SkPublicKeyDecoder.decode(candidate.publicKey)
        require(expected.algorithm == SkAlgorithm.ECDSA_P256 && expected.application == candidate.fido2RpId)
        val matches = mutableListOf<Pubkey>()
        for (entry in entries.filter { it.isPasskey }) {
            val publicMatches = runCatching { SkPublicKeyDecoder.decode(entry.publicKey) == expected }.getOrDefault(false)
            val credentialMatches = entry.fido2RpId == candidate.fido2RpId &&
                entry.credentialId?.contentEquals(candidate.credentialId) == true
            if (credentialMatches && !publicMatches) throw PasskeyIdentityConflict(entry.nickname)
            if (publicMatches) matches.add(entry)
        }
        return matches.sortedByDescending { it.credentialId?.contentEquals(candidate.credentialId) == true }
    }

    fun reconnect(candidate: Pubkey, existing: Pubkey): Pubkey {
        require(matches(candidate, listOf(existing)).singleOrNull()?.id == existing.id)
        return existing.copy(
            type = candidate.type,
            credentialId = candidate.credentialId?.copyOf(),
            fido2RpId = candidate.fido2RpId,
        )
    }
}
