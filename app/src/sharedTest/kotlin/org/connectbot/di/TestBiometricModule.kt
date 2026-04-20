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

package org.connectbot.di

import androidx.biometric.BiometricPrompt
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.connectbot.util.BiometricAvailability
import org.connectbot.util.BiometricKeyManager
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [BiometricModule::class],
)
object TestBiometricModule {
    @Provides
    @Singleton
    fun provideBiometricKeyManager(): BiometricKeyManager = object : BiometricKeyManager {
        override fun isBiometricAvailable() = BiometricAvailability.NO_HARDWARE
        override fun generateKeyAlias() = "stub-key-alias"
        override fun generateRsaKey(alias: String, keySize: Int): PublicKey = throw UnsupportedOperationException()
        override fun generateEcKey(alias: String, keySize: Int): PublicKey = throw UnsupportedOperationException()
        override fun generateKey(alias: String, keyType: String, keySize: Int): PublicKey = throw UnsupportedOperationException()
        override fun getCryptoObject(alias: String, algorithm: String): BiometricPrompt.CryptoObject = throw UnsupportedOperationException()
        override fun getSignatureAlgorithm(keyType: String, keySize: Int) = "SHA256withRSA"
        override fun getPrivateKey(alias: String): PrivateKey = throw UnsupportedOperationException()
        override fun getPublicKey(alias: String): PublicKey? = null
        override fun keyExists(alias: String) = false
        override fun deleteKey(alias: String) {}
        override fun sign(signature: Signature, data: ByteArray): ByteArray = throw UnsupportedOperationException()
    }
}
