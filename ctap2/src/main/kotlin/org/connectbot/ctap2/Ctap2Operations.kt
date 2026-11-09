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

package org.connectbot.ctap2

import com.yubico.yubikit.core.fido.CtapException
import com.yubico.yubikit.fido.ctap.ClientPin
import com.yubico.yubikit.fido.ctap.CredentialManagement
import com.yubico.yubikit.fido.ctap.Ctap2Session
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocol
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV1
import com.yubico.yubikit.fido.ctap.PinUvAuthProtocolV2

internal class Ctap2Operations(private val rpFilter: (String) -> Boolean = { true }) {
    data class PinRetryInfo(val count: Int, val powerCycleState: Boolean?)
    fun enumerateCredentialsWithPin(
        session: Ctap2Session,
        protocol: PinUvAuthProtocol,
        pin: String,
    ): Fido2Result<List<Fido2Credential>> {
        val clientPin = ClientPin(session, protocol)
        val token = clientPin.getPinToken(
            normalizePin(pin),
            ClientPin.PIN_PERMISSION_CM,
            null,
        )

        val credMgmt = CredentialManagement(session, protocol, token)
        val credentialList = enumerateCredentials(credMgmt)

        return Fido2Result.Success(credentialList)
    }

    fun matchesCtapError(error: Throwable, code: Byte, fallback: String): Boolean {
        val causes = generateSequence(error) { it.cause }.toList()
        val ctap = causes.filterIsInstance<CtapException>().firstOrNull()
        return if (ctap != null) {
            ctap.ctapError == code
        } else {
            causes.any { it.message?.contains(fallback, ignoreCase = true) == true }
        }
    }

    fun shouldRetryPinBlockedWithV1(e: Exception, protocol: PinUvAuthProtocol): Boolean {
        if (protocol !is PinUvAuthProtocolV2) return false
        return matchesCtapError(e, CtapException.ERR_PIN_BLOCKED, "PIN_BLOCKED")
    }

    fun mapPinOrCredentialError(
        e: Exception,
        v1FallbackAttempted: Boolean = false,
        session: Ctap2Session? = null,
        protocol: PinUvAuthProtocol? = null,
    ): Fido2Result<List<Fido2Credential>> = when {
        matchesCtapError(e, CtapException.ERR_PIN_INVALID, "PIN_INVALID") -> {
            Fido2Result.PinInvalid(attemptsRemaining = null)
        }

        matchesCtapError(e, CtapException.ERR_PIN_AUTH_BLOCKED, "PIN_AUTH_BLOCKED") -> {
            if (v1FallbackAttempted) {
                Fido2Result.PinLocked(
                    "PIN is temporarily blocked. Retried with PIN protocol V1; reinsert the security key and try again.",
                )
            } else {
                Fido2Result.PinLocked(ERR_PIN_AUTH_BLOCKED)
            }
        }

        matchesCtapError(e, CtapException.ERR_PIN_BLOCKED, "PIN_BLOCKED") -> {
            pinBlockedError(session, protocol, v1FallbackAttempted)
        }

        else -> Fido2Result.Error(e.message ?: "Authenticator operation failed", e)
    }

    fun pinBlockedError(
        session: Ctap2Session?,
        protocol: PinUvAuthProtocol?,
        v1FallbackAttempted: Boolean,
    ): Fido2Result.PinLocked {
        val retries = getPinRetriesWithFallback(session, protocol)
        return if (retries != null && retries.count > 0) {
            val retryHint = if (v1FallbackAttempted) {
                "Retried with PIN protocol V1."
            } else {
                "Using current PIN protocol."
            }
            val powerCycleHint = if (retries.powerCycleState == true) {
                "Power-cycle or reinsert the key, then retry."
            } else {
                "Reinsert the key and retry."
            }
            Fido2Result.PinLocked(
                "Authenticator reported PIN_BLOCKED, but $retryHint Remaining retries: ${retries.count}. " +
                    "This may be a device firmware quirk. $powerCycleHint",
            )
        } else if (v1FallbackAttempted) {
            Fido2Result.PinLocked("PIN is locked. Retried with PIN protocol V1. Please reset your security key.")
        } else {
            Fido2Result.PinLocked(ERR_PIN_BLOCKED)
        }
    }

    fun getPinRetriesWithFallback(
        session: Ctap2Session?,
        protocol: PinUvAuthProtocol?,
    ): PinRetryInfo? {
        if (session == null || protocol == null) {
            return null
        }

        getPinRetriesSafely(session, protocol)?.let { return it }

        if (protocol !is PinUvAuthProtocolV1) {
            val fallbackProtocol = PinUvAuthProtocolV1()
            return getPinRetriesSafely(session, fallbackProtocol)
        }

        return null
    }

    fun getPinRetriesSafely(
        session: Ctap2Session,
        protocol: PinUvAuthProtocol,
    ): PinRetryInfo? = try {
        val retries = ClientPin(session, protocol).getPinRetries()
        val result = PinRetryInfo(
            count = retries.count,
            powerCycleState = retries.powerCycleState,
        )
        result
    } catch (e: Exception) {
        null
    }

    fun enumerateCredentials(
        credMgmt: CredentialManagement,
    ): List<Fido2Credential> {
        val rps: List<CredentialManagement.RpData> = credMgmt.enumerateRps()

        val sshRps = rps.filter { (it.rp["id"] as? String)?.let(rpFilter) == true }
        if (sshRps.isEmpty()) {
            return emptyList()
        }

        return sshRps.flatMap { sshRp ->
            val credentials: List<CredentialManagement.CredentialData> = credMgmt.enumerateCredentials(sshRp.rpIdHash)
            credentials.map { Fido2CredentialMapper.fromCredentialData(it, sshRp) }
        }
    }

    fun getAssertion(
        session: Ctap2Session,
        pin: String,
        credentialId: ByteArray,
        challenge: ByteArray,
        rpId: String,
    ): Fido2Result<Fido2SignatureResult> = try {
        require(challenge.size == 32) { "clientDataHash must be 32 bytes" }
        val protocol = selectPinProtocol(session)
        val clientPin = ClientPin(session, protocol)
        val token = clientPin.getPinToken(
            normalizePin(pin),
            ClientPin.PIN_PERMISSION_GA,
            rpId,
        )
        val clientDataHash = challenge
        val pinUvAuth = protocol.authenticate(token, clientDataHash)
        val allowList = listOf(
            mapOf(
                "type" to PUBLIC_KEY_TYPE,
                "id" to credentialId,
            ),
        )
        val assertions: List<Ctap2Session.AssertionData> = session.getAssertions(
            rpId,
            clientDataHash,
            allowList,
            null,
            null,
            pinUvAuth,
            protocol.version,
            null,
        )
        if (assertions.isEmpty()) {
            Fido2Result.Error(ERR_NO_ASSERTION_RETURNED)
        } else {
            Fido2Result.Success(Fido2SignatureMapper.fromAssertion(assertions[0]))
        }
    } catch (e: Exception) {
        mapSigningError(e, "Assertion failed")
    }

    fun <T> mapSigningError(e: Exception, defaultMessage: String): Fido2Result<T> = when {
        matchesCtapError(e, CtapException.ERR_PIN_INVALID, "PIN_INVALID") ->
            Fido2Result.PinInvalid(attemptsRemaining = null)

        matchesCtapError(e, CtapException.ERR_PIN_AUTH_BLOCKED, "PIN_AUTH_BLOCKED") ->
            Fido2Result.PinLocked(ERR_PIN_AUTH_BLOCKED)

        matchesCtapError(e, CtapException.ERR_PIN_BLOCKED, "PIN_BLOCKED") ->
            Fido2Result.PinLocked(ERR_PIN_BLOCKED)

        matchesCtapError(e, CtapException.ERR_NO_CREDENTIALS, "NO_CREDENTIALS") ->
            Fido2Result.Error("Credential not found on security key")

        else -> Fido2Result.Error(e.message ?: defaultMessage, e)
    }

    fun selectPinProtocol(session: Ctap2Session): PinUvAuthProtocol {
        val supportedProtocols: List<Int> = session.cachedInfo.pinUvAuthProtocols
        return if (2 in supportedProtocols) {
            PinUvAuthProtocolV2()
        } else {
            PinUvAuthProtocolV1()
        }
    }

    companion object {
        private const val PUBLIC_KEY_TYPE = "public-key"
        private const val ERR_NO_ASSERTION_RETURNED = "No assertion returned"
        private const val ERR_PIN_AUTH_BLOCKED = "PIN is temporarily blocked. Reinsert the security key and try again."
        private const val ERR_PIN_BLOCKED = "PIN is locked. Please reset your security key."
    }
}
