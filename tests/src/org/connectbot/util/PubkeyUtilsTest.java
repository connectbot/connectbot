/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

package org.connectbot.util;

import java.util.Arrays;

import android.test.AndroidTestCase;

/**
 * @author Kenny Root
 *
 */
public class PubkeyUtilsTest extends AndroidTestCase {
	public void testEncodeHex_Null_Failure() throws Exception {
		try {
			PubkeyUtils.encodeHex(null);
			fail("Should throw null pointer exception when argument is null");
		} catch (NullPointerException e) {
			// success
		}
	}
	public void testEncodeHex_Success() throws Exception {
		byte[] input = {(byte) 0xFF, 0x00, (byte) 0xA5, 0x5A, 0x12, 0x23};
		String expected = "ff00a55a1223";

		assertEquals("Encoded hex should match expected",
				PubkeyUtils.encodeHex(input), expected);
	}

	public void testSha256_Empty_Success() throws Exception {
		byte[] empty_hashed = new byte[] {
				(byte) 0xe3, (byte) 0xb0, (byte) 0xc4, (byte) 0x42,
				(byte) 0x98, (byte) 0xfc, (byte) 0x1c, (byte) 0x14,
				(byte) 0x9a, (byte) 0xfb, (byte) 0xf4, (byte) 0xc8,
				(byte) 0x99, (byte) 0x6f, (byte) 0xb9, (byte) 0x24,
				(byte) 0x27, (byte) 0xae, (byte) 0x41, (byte) 0xe4,
				(byte) 0x64, (byte) 0x9b, (byte) 0x93, (byte) 0x4c,
				(byte) 0xa4, (byte) 0x95, (byte) 0x99, (byte) 0x1b,
				(byte) 0x78, (byte) 0x52, (byte) 0xb8, (byte) 0x55,
		};

		final byte[] empty = new byte[] {};

		assertTrue("Empty string should be equal to known test vector",
				Arrays.equals(empty_hashed, PubkeyUtils.sha256(empty)));
	}
}
