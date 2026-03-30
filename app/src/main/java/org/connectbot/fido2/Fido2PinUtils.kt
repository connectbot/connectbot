/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root, Jeffrey Sharkey
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

package org.connectbot.fido2

import java.text.Normalizer

/**
 * Normalizes a FIDO2 PIN to Unicode NFC form as required by the CTAP2 spec.
 *
 * The FIDO2 specification requires PINs to be normalized to Unicode NFC form
 * before being sent to the authenticator. This ensures that equivalent Unicode
 * representations (e.g., é as U+00E9 vs e + U+0301) are treated as the same PIN.
 */
fun normalizePin(pin: String): CharArray = Normalizer.normalize(pin, Normalizer.Form.NFC).toCharArray()
