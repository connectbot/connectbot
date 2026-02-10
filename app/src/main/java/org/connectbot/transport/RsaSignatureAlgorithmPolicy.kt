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

package org.connectbot.transport

import org.connectbot.sshlib.SshClient
import timber.log.Timber

/** Prevents Android Keystore RSA keys from falling back to unsupported SHA-1 signatures. */
internal object RsaSignatureAlgorithmPolicy {
    val rsaSha2Algorithms = listOf("rsa-sha2-512", "rsa-sha2-256")

    fun prepareForAuthentication(client: SshClient): Boolean = try {
        val connection = SshClient::class.java.getDeclaredField("connection").run {
            isAccessible = true
            get(client)
        } ?: return false
        val algorithmsField = connection.javaClass.getDeclaredField("serverSigAlgs").apply {
            isAccessible = true
        }
        val acceptedAlgorithms = algorithmsField.get(connection) as? Set<*>
        when {
            acceptedAlgorithms == null -> {
                algorithmsField.set(connection, rsaSha2Algorithms.toSet())
                true
            }

            acceptedAlgorithms.any(rsaSha2Algorithms::contains) -> true

            else -> false
        }
    } catch (e: ReflectiveOperationException) {
        Timber.w(e, "Unable to prepare RSA SHA-2 public-key authentication")
        false
    } catch (e: SecurityException) {
        Timber.w(e, "Unable to prepare RSA SHA-2 public-key authentication")
        false
    }
}
