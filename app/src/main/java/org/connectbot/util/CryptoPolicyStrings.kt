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

import org.connectbot.R
import org.connectbot.sshlib.AlgorithmCategory
import org.connectbot.sshlib.AlgorithmPolicyWarningReason

fun AlgorithmCategory.titleRes(): Int = when (this) {
    AlgorithmCategory.KEX -> R.string.crypto_category_kex
    AlgorithmCategory.CIPHER -> R.string.crypto_category_cipher
    AlgorithmCategory.MAC -> R.string.crypto_category_mac
    AlgorithmCategory.HOST_KEY -> R.string.crypto_category_hostkey
    AlgorithmCategory.PUBKEY -> R.string.crypto_category_pubkey
}

fun AlgorithmPolicyWarningReason.titleRes(): Int = when (this) {
    AlgorithmPolicyWarningReason.UNSUPPORTED -> R.string.crypto_reason_unsupported
    AlgorithmPolicyWarningReason.UNAVAILABLE -> R.string.crypto_reason_unavailable
    AlgorithmPolicyWarningReason.DH_TOO_SMALL -> R.string.crypto_reason_dh_small
    AlgorithmPolicyWarningReason.RSA_TOO_SMALL -> R.string.crypto_reason_rsa_small
}

fun cryptoPolicyTitleRes(policy: String): Int? = when (policy) {
    "DEFAULT" -> R.string.crypto_policy_default
    "FUTURE" -> R.string.crypto_policy_future
    "FIPS" -> R.string.crypto_policy_fips
    "LEGACY" -> R.string.crypto_policy_legacy
    "CUSTOM" -> R.string.crypto_policy_custom
    else -> null
}
