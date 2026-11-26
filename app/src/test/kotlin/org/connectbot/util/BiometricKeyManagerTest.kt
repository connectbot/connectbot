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

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 30])
class BiometricKeyManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = BiometricKeyManagerImpl(context)

    @Test
    fun rsaGenerationUsesBiometricAuthentication() {
        assertAuthenticationParameters(KeyProperties.KEY_ALGORITHM_RSA, 2048)
    }

    @Test
    fun ecGenerationUsesBiometricAuthentication() {
        assertAuthenticationParameters(KeyProperties.KEY_ALGORITHM_EC, 256)
    }

    private fun assertAuthenticationParameters(algorithm: String, keySize: Int) {
        val generator = mock(KeyPairGenerator::class.java)
        val publicKey = mock(PublicKey::class.java)
        `when`(generator.generateKeyPair()).thenReturn(KeyPair(publicKey, mock(PrivateKey::class.java)))

        mockStatic(KeyPairGenerator::class.java).use { generators ->
            generators.`when`<KeyPairGenerator> {
                KeyPairGenerator.getInstance(algorithm, "AndroidKeyStore")
            }.thenReturn(generator)

            assertSame(publicKey, manager.generateKey("test-biometric-key", algorithm, keySize))

            val args = ArgumentCaptor.forClass(KeyGenParameterSpec::class.java)
            verify(generator).initialize(args.capture())
            val spec = args.value
            assertEquals("test-biometric-key", spec.keystoreAlias)
            assertTrue(spec.isUserAuthenticationRequired)
            assertTrue(spec.isInvalidatedByBiometricEnrollment)
            assertEquals(30, spec.userAuthenticationValidityDurationSeconds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG, spec.userAuthenticationType)
            }
        }
    }
}
