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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
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

	/* openssl ecparam -genkey -name prime256v1 -noout | openssl pkcs8 -topk8 -outform d -nocrypt | recode ../x1 | sed 's/0x/(byte) 0x/g' */
	private static final byte[] EC_KEY_PKCS8 = new byte[] { (byte) 0x30, (byte) 0x81, (byte) 0x87,
			(byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x30, (byte) 0x13, (byte) 0x06,
			(byte) 0x07, (byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0xCE, (byte) 0x3D,
			(byte) 0x02, (byte) 0x01, (byte) 0x06, (byte) 0x08, (byte) 0x2A, (byte) 0x86,
			(byte) 0x48, (byte) 0xCE, (byte) 0x3D, (byte) 0x03, (byte) 0x01, (byte) 0x07,
			(byte) 0x04, (byte) 0x6D, (byte) 0x30, (byte) 0x6B, (byte) 0x02, (byte) 0x01,
			(byte) 0x01, (byte) 0x04, (byte) 0x20, (byte) 0xC7, (byte) 0x6B, (byte) 0xA5,
			(byte) 0xB6, (byte) 0xB7, (byte) 0x4E, (byte) 0x0B, (byte) 0x70, (byte) 0x2E,
			(byte) 0xA0, (byte) 0x5D, (byte) 0x8D, (byte) 0x0A, (byte) 0xF5, (byte) 0x43,
			(byte) 0xEF, (byte) 0x54, (byte) 0x2F, (byte) 0x05, (byte) 0x5B, (byte) 0x66,
			(byte) 0x50, (byte) 0xC5, (byte) 0xB4, (byte) 0xA8, (byte) 0x60, (byte) 0x16,
			(byte) 0x8E, (byte) 0x8D, (byte) 0xCD, (byte) 0x11, (byte) 0xFA, (byte) 0xA1,
			(byte) 0x44, (byte) 0x03, (byte) 0x42, (byte) 0x00, (byte) 0x04, (byte) 0x12,
			(byte) 0xE2, (byte) 0x70, (byte) 0x30, (byte) 0x87, (byte) 0x2F, (byte) 0xDE,
			(byte) 0x10, (byte) 0xD9, (byte) 0xC9, (byte) 0x83, (byte) 0xC7, (byte) 0x8D,
			(byte) 0xC9, (byte) 0x9B, (byte) 0x94, (byte) 0x24, (byte) 0x50, (byte) 0x5D,
			(byte) 0xEC, (byte) 0xF1, (byte) 0x4F, (byte) 0x52, (byte) 0xC6, (byte) 0xE7,
			(byte) 0xA3, (byte) 0xD7, (byte) 0xF4, (byte) 0x7C, (byte) 0x09, (byte) 0xA1,
			(byte) 0x10, (byte) 0x11, (byte) 0xE4, (byte) 0x9E, (byte) 0x90, (byte) 0xAF,
			(byte) 0xF9, (byte) 0x4A, (byte) 0x74, (byte) 0x09, (byte) 0x93, (byte) 0xC7,
			(byte) 0x9A, (byte) 0xB3, (byte) 0xE2, (byte) 0xD8, (byte) 0x61, (byte) 0x5F,
			(byte) 0x86, (byte) 0x14, (byte) 0x91, (byte) 0x7A, (byte) 0x23, (byte) 0x81,
			(byte) 0x42, (byte) 0xA9, (byte) 0x02, (byte) 0x1D, (byte) 0x33, (byte) 0x19,
			(byte) 0xC0, (byte) 0x4B, (byte) 0xCE
	};

	private static final BigInteger EC_KEY_priv = new BigInteger("c76ba5b6b74e0b702ea05d8d0af543ef542f055b6650c5b4a860168e8dcd11fa", 16);
	private static final BigInteger EC_KEY_pub_x = new BigInteger("12e27030872fde10d9c983c78dc99b9424505decf14f52c6e7a3d7f47c09a110", 16);
	private static final BigInteger EC_KEY_pub_y = new BigInteger("11e49e90aff94a740993c79ab3e2d8615f8614917a238142a9021d3319c04bce", 16);

	/* openssl genrsa 512 | openssl pkcs8 -topk8 -outform d -nocrypt | recode ../x1 | sed 's/0x/(byte) 0x/g' */
	private static final byte[] RSA_KEY_PKCS8 = new byte[] { (byte) 0x30, (byte) 0x82, (byte) 0x01,
			(byte) 0x55, (byte) 0x02, (byte) 0x01, (byte) 0x00, (byte) 0x30, (byte) 0x0D,
			(byte) 0x06, (byte) 0x09, (byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0x86,
			(byte) 0xF7, (byte) 0x0D, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05,
			(byte) 0x00, (byte) 0x04, (byte) 0x82, (byte) 0x01, (byte) 0x3F, (byte) 0x30,
			(byte) 0x82, (byte) 0x01, (byte) 0x3B, (byte) 0x02, (byte) 0x01, (byte) 0x00,
			(byte) 0x02, (byte) 0x41, (byte) 0x00, (byte) 0xC6, (byte) 0x00, (byte) 0x79,
			(byte) 0x0C, (byte) 0x46, (byte) 0xF9, (byte) 0x03, (byte) 0x15, (byte) 0xBA,
			(byte) 0x35, (byte) 0x63, (byte) 0x6C, (byte) 0x97, (byte) 0x3A, (byte) 0x6C,
			(byte) 0xC8, (byte) 0x15, (byte) 0x32, (byte) 0x2A, (byte) 0x62, (byte) 0x72,
			(byte) 0xBD, (byte) 0x05, (byte) 0x01, (byte) 0xCF, (byte) 0xE6, (byte) 0x49,
			(byte) 0xEC, (byte) 0xC9, (byte) 0x8A, (byte) 0x3A, (byte) 0x4E, (byte) 0xB1,
			(byte) 0xF2, (byte) 0x3E, (byte) 0x86, (byte) 0x3C, (byte) 0x64, (byte) 0x4A,
			(byte) 0x0A, (byte) 0x29, (byte) 0xD6, (byte) 0xFA, (byte) 0xF9, (byte) 0xAC,
			(byte) 0xD8, (byte) 0x7B, (byte) 0x9F, (byte) 0x2A, (byte) 0x6B, (byte) 0x13,
			(byte) 0x06, (byte) 0x06, (byte) 0xEB, (byte) 0x83, (byte) 0x1B, (byte) 0xB8,
			(byte) 0x97, (byte) 0xA3, (byte) 0x91, (byte) 0x95, (byte) 0x60, (byte) 0x15,
			(byte) 0xE5, (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00, (byte) 0x01,
			(byte) 0x02, (byte) 0x40, (byte) 0x0F, (byte) 0xDA, (byte) 0x33, (byte) 0xD6,
			(byte) 0xCE, (byte) 0xCB, (byte) 0xDA, (byte) 0xFA, (byte) 0x5F, (byte) 0x59,
			(byte) 0x2C, (byte) 0xE7, (byte) 0xA1, (byte) 0xC7, (byte) 0xF4, (byte) 0xB3,
			(byte) 0xA4, (byte) 0x36, (byte) 0xCA, (byte) 0xFB, (byte) 0xEC, (byte) 0xD1,
			(byte) 0xC3, (byte) 0x57, (byte) 0xDC, (byte) 0xCC, (byte) 0x44, (byte) 0x38,
			(byte) 0xE7, (byte) 0xFD, (byte) 0xE0, (byte) 0x23, (byte) 0x0E, (byte) 0x97,
			(byte) 0x87, (byte) 0x55, (byte) 0x80, (byte) 0x2B, (byte) 0xF2, (byte) 0xF4,
			(byte) 0x1C, (byte) 0x03, (byte) 0xD2, (byte) 0x3E, (byte) 0x09, (byte) 0x72,
			(byte) 0x49, (byte) 0xD8, (byte) 0x9C, (byte) 0xAC, (byte) 0xDA, (byte) 0x65,
			(byte) 0x68, (byte) 0x4D, (byte) 0x38, (byte) 0x19, (byte) 0xD8, (byte) 0xB1,
			(byte) 0x5B, (byte) 0xB7, (byte) 0x38, (byte) 0xC8, (byte) 0x94, (byte) 0xB5,
			(byte) 0x02, (byte) 0x21, (byte) 0x00, (byte) 0xF7, (byte) 0x8E, (byte) 0x20,
			(byte) 0xDC, (byte) 0x26, (byte) 0x12, (byte) 0x3A, (byte) 0x85, (byte) 0x91,
			(byte) 0x5F, (byte) 0x45, (byte) 0xA6, (byte) 0x95, (byte) 0xE5, (byte) 0x22,
			(byte) 0xD0, (byte) 0xC4, (byte) 0xD7, (byte) 0x6A, (byte) 0xF1, (byte) 0x43,
			(byte) 0x38, (byte) 0x88, (byte) 0x20, (byte) 0x7D, (byte) 0x80, (byte) 0x73,
			(byte) 0x7B, (byte) 0xDC, (byte) 0x73, (byte) 0x51, (byte) 0x3B, (byte) 0x02,
			(byte) 0x21, (byte) 0x00, (byte) 0xCC, (byte) 0xC1, (byte) 0x99, (byte) 0xC8,
			(byte) 0xC0, (byte) 0x54, (byte) 0xBC, (byte) 0xE9, (byte) 0xFB, (byte) 0x77,
			(byte) 0x28, (byte) 0xB8, (byte) 0x26, (byte) 0x02, (byte) 0xC0, (byte) 0x0C,
			(byte) 0xDE, (byte) 0xFD, (byte) 0xEA, (byte) 0xD0, (byte) 0x15, (byte) 0x4B,
			(byte) 0x3B, (byte) 0xD1, (byte) 0xDD, (byte) 0xFD, (byte) 0x5B, (byte) 0xAC,
			(byte) 0xB3, (byte) 0xCF, (byte) 0xC3, (byte) 0x5F, (byte) 0x02, (byte) 0x21,
			(byte) 0x00, (byte) 0xCD, (byte) 0x8C, (byte) 0x25, (byte) 0x9C, (byte) 0xA5,
			(byte) 0xBF, (byte) 0xDC, (byte) 0xF7, (byte) 0xAA, (byte) 0x8D, (byte) 0x00,
			(byte) 0xB8, (byte) 0x21, (byte) 0x1D, (byte) 0xF0, (byte) 0x9A, (byte) 0x87,
			(byte) 0xD6, (byte) 0x95, (byte) 0xE5, (byte) 0x5D, (byte) 0x7B, (byte) 0x43,
			(byte) 0x0C, (byte) 0x37, (byte) 0x28, (byte) 0xC0, (byte) 0xBA, (byte) 0xC7,
			(byte) 0x80, (byte) 0xB8, (byte) 0xA1, (byte) 0x02, (byte) 0x21, (byte) 0x00,
			(byte) 0xCC, (byte) 0x26, (byte) 0x6F, (byte) 0xAD, (byte) 0x60, (byte) 0x4E,
			(byte) 0x5C, (byte) 0xB9, (byte) 0x32, (byte) 0x57, (byte) 0x61, (byte) 0x8B,
			(byte) 0x11, (byte) 0xA3, (byte) 0x06, (byte) 0x57, (byte) 0x0E, (byte) 0xF2,
			(byte) 0xBE, (byte) 0x6F, (byte) 0x4F, (byte) 0xFB, (byte) 0xDE, (byte) 0x1D,
			(byte) 0xE6, (byte) 0xA7, (byte) 0x19, (byte) 0x03, (byte) 0x7D, (byte) 0x98,
			(byte) 0xB6, (byte) 0x23, (byte) 0x02, (byte) 0x20, (byte) 0x24, (byte) 0x80,
			(byte) 0x94, (byte) 0xFF, (byte) 0xDD, (byte) 0x7A, (byte) 0x22, (byte) 0x7D,
			(byte) 0xC4, (byte) 0x5A, (byte) 0xFD, (byte) 0x84, (byte) 0xC1, (byte) 0xAD,
			(byte) 0x8A, (byte) 0x13, (byte) 0x2A, (byte) 0xF9, (byte) 0x5D, (byte) 0xFF,
			(byte) 0x0B, (byte) 0x2E, (byte) 0x0F, (byte) 0x61, (byte) 0x42, (byte) 0x88,
			(byte) 0x57, (byte) 0xCF, (byte) 0xC1, (byte) 0x71, (byte) 0xC9, (byte) 0xB9
	};

	private static final BigInteger RSA_KEY_N = new BigInteger("C600790C46F90315BA35636C973A6CC815322A6272BD0501CFE649ECC98A3A4EB1F23E863C644A0A29D6FAF9ACD87B9F2A6B130606EB831BB897A391956015E5", 16);
	private static final BigInteger RSA_KEY_E = new BigInteger("010001", 16);

	/*
	 openssl dsaparam -genkey -text -out dsakey.pem 1024
	 openssl dsa -in dsakey.pem -text -noout
	 openssl pkcs8 -topk8 -in dsakey.pem -outform d -nocrypt | recode ../x1 | sed 's/0x/(byte) 0x/g'
	 */
	private static final byte[] DSA_KEY_PKCS8 = new byte[] {
			(byte) 0x30, (byte) 0x82, (byte) 0x01, (byte) 0x4A, (byte) 0x02, (byte) 0x01,
			(byte) 0x00, (byte) 0x30, (byte) 0x82, (byte) 0x01, (byte) 0x2B, (byte) 0x06,
			(byte) 0x07, (byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0xCE, (byte) 0x38,
			(byte) 0x04, (byte) 0x01, (byte) 0x30, (byte) 0x82, (byte) 0x01, (byte) 0x1E,
			(byte) 0x02, (byte) 0x81, (byte) 0x81, (byte) 0x00, (byte) 0xD2, (byte) 0x18,
			(byte) 0xDB, (byte) 0x94, (byte) 0x7C, (byte) 0xD6, (byte) 0x2E, (byte) 0xE2,
			(byte) 0x07, (byte) 0x38, (byte) 0x42, (byte) 0xC4, (byte) 0x16, (byte) 0x24,
			(byte) 0x94, (byte) 0x2F, (byte) 0xC1, (byte) 0x0F, (byte) 0x92, (byte) 0x0A,
			(byte) 0x44, (byte) 0x44, (byte) 0x99, (byte) 0xFC, (byte) 0x01, (byte) 0x1B,
			(byte) 0xF8, (byte) 0xF3, (byte) 0x82, (byte) 0x57, (byte) 0x01, (byte) 0x8D,
			(byte) 0xE6, (byte) 0x22, (byte) 0x70, (byte) 0xA0, (byte) 0xD6, (byte) 0x05,
			(byte) 0x0F, (byte) 0xF1, (byte) 0xD0, (byte) 0xF4, (byte) 0x0B, (byte) 0xA2,
			(byte) 0xE4, (byte) 0x1E, (byte) 0xD3, (byte) 0x44, (byte) 0x79, (byte) 0x74,
			(byte) 0x4C, (byte) 0xC1, (byte) 0xA7, (byte) 0xA5, (byte) 0x84, (byte) 0xD8,
			(byte) 0xB9, (byte) 0xDF, (byte) 0xA3, (byte) 0x85, (byte) 0xFA, (byte) 0xF2,
			(byte) 0xFD, (byte) 0x44, (byte) 0x0B, (byte) 0xB1, (byte) 0xA5, (byte) 0x82,
			(byte) 0x8D, (byte) 0x06, (byte) 0x92, (byte) 0xCA, (byte) 0xB4, (byte) 0xFB,
			(byte) 0xDF, (byte) 0xC2, (byte) 0xFD, (byte) 0xA7, (byte) 0xCB, (byte) 0x6F,
			(byte) 0x03, (byte) 0xB9, (byte) 0xEF, (byte) 0xFD, (byte) 0x7F, (byte) 0xBC,
			(byte) 0xB3, (byte) 0x1D, (byte) 0xA4, (byte) 0xE8, (byte) 0x7D, (byte) 0xA2,
			(byte) 0xCF, (byte) 0x62, (byte) 0x35, (byte) 0x06, (byte) 0xC8, (byte) 0xFE,
			(byte) 0xE6, (byte) 0xE7, (byte) 0x6E, (byte) 0xAE, (byte) 0x22, (byte) 0xE7,
			(byte) 0x82, (byte) 0x38, (byte) 0x54, (byte) 0x82, (byte) 0xCD, (byte) 0xEA,
			(byte) 0xD8, (byte) 0x69, (byte) 0xBB, (byte) 0x1C, (byte) 0xD3, (byte) 0x70,
			(byte) 0x32, (byte) 0xB1, (byte) 0xFB, (byte) 0x07, (byte) 0x01, (byte) 0x66,
			(byte) 0xCC, (byte) 0x24, (byte) 0xD6, (byte) 0x50, (byte) 0x46, (byte) 0x9B,
			(byte) 0x02, (byte) 0x15, (byte) 0x00, (byte) 0xD6, (byte) 0xE6, (byte) 0x7E,
			(byte) 0x1A, (byte) 0xE5, (byte) 0xCA, (byte) 0x1D, (byte) 0xB6, (byte) 0xAF,
			(byte) 0x4E, (byte) 0xD9, (byte) 0x18, (byte) 0xE8, (byte) 0x87, (byte) 0xB1,
			(byte) 0xBC, (byte) 0x93, (byte) 0xE1, (byte) 0x80, (byte) 0xF5, (byte) 0x02,
			(byte) 0x81, (byte) 0x80, (byte) 0x19, (byte) 0x20, (byte) 0xCC, (byte) 0x18,
			(byte) 0xF6, (byte) 0x8F, (byte) 0x73, (byte) 0xFA, (byte) 0x9F, (byte) 0x50,
			(byte) 0xC8, (byte) 0x92, (byte) 0xBE, (byte) 0x07, (byte) 0x7C, (byte) 0x34,
			(byte) 0xD8, (byte) 0x6F, (byte) 0x63, (byte) 0xC9, (byte) 0x35, (byte) 0x48,
			(byte) 0x79, (byte) 0x79, (byte) 0x26, (byte) 0xEF, (byte) 0x1E, (byte) 0x99,
			(byte) 0x54, (byte) 0xD7, (byte) 0x30, (byte) 0x2C, (byte) 0x68, (byte) 0xBC,
			(byte) 0xFF, (byte) 0xF2, (byte) 0x4C, (byte) 0x6A, (byte) 0xD3, (byte) 0x2D,
			(byte) 0x1C, (byte) 0x7A, (byte) 0x06, (byte) 0x11, (byte) 0x72, (byte) 0x92,
			(byte) 0x9C, (byte) 0xAA, (byte) 0x95, (byte) 0x0E, (byte) 0x44, (byte) 0x2C,
			(byte) 0x5F, (byte) 0x19, (byte) 0x25, (byte) 0xB4, (byte) 0xBF, (byte) 0x21,
			(byte) 0x8F, (byte) 0xB7, (byte) 0x7E, (byte) 0x4B, (byte) 0x64, (byte) 0x83,
			(byte) 0x59, (byte) 0x20, (byte) 0x20, (byte) 0x36, (byte) 0x84, (byte) 0xA4,
			(byte) 0x1D, (byte) 0xB5, (byte) 0xCA, (byte) 0x7F, (byte) 0x10, (byte) 0x4E,
			(byte) 0x27, (byte) 0x21, (byte) 0x8E, (byte) 0x2C, (byte) 0xA5, (byte) 0xF8,
			(byte) 0xAC, (byte) 0xBD, (byte) 0xF5, (byte) 0xB5, (byte) 0xBA, (byte) 0xEB,
			(byte) 0x86, (byte) 0x6F, (byte) 0x7F, (byte) 0xB1, (byte) 0xE0, (byte) 0x90,
			(byte) 0x35, (byte) 0xCA, (byte) 0xA8, (byte) 0x64, (byte) 0x6E, (byte) 0x06,
			(byte) 0x3D, (byte) 0x02, (byte) 0x3D, (byte) 0x95, (byte) 0x57, (byte) 0xB3,
			(byte) 0x8A, (byte) 0xE2, (byte) 0x0B, (byte) 0xD3, (byte) 0x9E, (byte) 0x1C,
			(byte) 0x13, (byte) 0xDE, (byte) 0x48, (byte) 0xA3, (byte) 0xC2, (byte) 0x11,
			(byte) 0xDA, (byte) 0x75, (byte) 0x09, (byte) 0xF6, (byte) 0x92, (byte) 0x0F,
			(byte) 0x0F, (byte) 0xA6, (byte) 0xF3, (byte) 0x3E, (byte) 0x04, (byte) 0x16,
			(byte) 0x02, (byte) 0x14, (byte) 0x29, (byte) 0x50, (byte) 0xE4, (byte) 0x77,
			(byte) 0x4F, (byte) 0xB2, (byte) 0xFF, (byte) 0xFB, (byte) 0x5D, (byte) 0x33,
			(byte) 0xC9, (byte) 0x37, (byte) 0xF0, (byte) 0xB5, (byte) 0x8F, (byte) 0xFB,
			(byte) 0x0D, (byte) 0x45, (byte) 0xC2, (byte) 0x00
	};

	private static final BigInteger DSA_KEY_P = new BigInteger("00d218db947cd62ee2073842c41624942fc10f920a444499fc011bf8f38257018de62270a0d6050ff1d0f40ba2e41ed34479744cc1a7a584d8b9dfa385faf2fd440bb1a5828d0692cab4fbdfc2fda7cb6f03b9effd7fbcb31da4e87da2cf623506c8fee6e76eae22e782385482cdead869bb1cd37032b1fb070166cc24d650469b", 16);
	private static final BigInteger DSA_KEY_Q = new BigInteger("00d6e67e1ae5ca1db6af4ed918e887b1bc93e180f5", 16);
	private static final BigInteger DSA_KEY_G = new BigInteger("1920cc18f68f73fa9f50c892be077c34d86f63c93548797926ef1e9954d7302c68bcfff24c6ad32d1c7a061172929caa950e442c5f1925b4bf218fb77e4b64835920203684a41db5ca7f104e27218e2ca5f8acbdf5b5baeb866f7fb1e09035caa8646e063d023d9557b38ae20bd39e1c13de48a3c211da7509f6920f0fa6f33e", 16);

	private static final BigInteger DSA_KEY_priv = new BigInteger("2950e4774fb2fffb5d33c937f0b58ffb0d45c200", 16);
	private static final BigInteger DSA_KEY_pub = new BigInteger("0087b82cdf3232db3bec0d00e96c8393bc7f5629551ea1a00888961cf56e80a36f2a7b316bc10b1d367a5ea374235c9361a472a9176f6cf61f708b86a52b4fae814abd1f1bdd16eea94aea9281851032b1bad7567624c615d6899ca1c94ad614f14e767e49d2ba5223cd113a0d02b66183653cd346ae76d85843afe66520904274", 16);

	public void testGetOidFromPkcs8Encoded_Ec_NistP256() throws Exception {
		assertEquals("1.2.840.10045.2.1", PubkeyUtils.getOidFromPkcs8Encoded(EC_KEY_PKCS8));
	}

	public void testGetOidFromPkcs8Encoded_Rsa() throws Exception {
		assertEquals("1.2.840.113549.1.1.1", PubkeyUtils.getOidFromPkcs8Encoded(RSA_KEY_PKCS8));
	}

	public void testGetOidFromPkcs8Encoded_Dsa() throws Exception {
		assertEquals("1.2.840.10040.4.1", PubkeyUtils.getOidFromPkcs8Encoded(DSA_KEY_PKCS8));
	}

	public void testGetOidFromPkcs8Encoded_Null_Failure() throws Exception {
		try {
			PubkeyUtils.getOidFromPkcs8Encoded(null);
			fail("Should throw NoSuchAlgorithmException");
		} catch (NoSuchAlgorithmException expected) {
		}
	}

	public void testGetOidFromPkcs8Encoded_NotCorrectDer_Failure() throws Exception {
		try {
			PubkeyUtils.getOidFromPkcs8Encoded(new byte[] { 0x30, 0x01, 0x00 });
			fail("Should throw NoSuchAlgorithmException");
		} catch (NoSuchAlgorithmException expected) {
		}
	}

	public void testGetAlgorithmForOid_Ecdsa() throws Exception {
		assertEquals("EC", PubkeyUtils.getAlgorithmForOid("1.2.840.10045.2.1"));
	}

	public void testGetAlgorithmForOid_Rsa() throws Exception {
		assertEquals("RSA", PubkeyUtils.getAlgorithmForOid("1.2.840.113549.1.1.1"));
	}

	public void testGetAlgorithmForOid_Dsa() throws Exception {
		assertEquals("DSA", PubkeyUtils.getAlgorithmForOid("1.2.840.10040.4.1"));
	}

	public void testGetAlgorithmForOid_NullInput_Failure() throws Exception {
		try {
			PubkeyUtils.getAlgorithmForOid(null);
			fail("Should throw NoSuchAlgorithmException");
		} catch (NoSuchAlgorithmException expected) {
		}
	}

	public void testGetAlgorithmForOid_UnknownOid_Failure() throws Exception {
		try {
			PubkeyUtils.getAlgorithmForOid("1.3.66666.2000.4000.1");
			fail("Should throw NoSuchAlgorithmException");
		} catch (NoSuchAlgorithmException expected) {
		}
	}

	public void testRecoverKeyPair_Dsa() throws Exception {
		KeyPair kp = PubkeyUtils.recoverKeyPair(DSA_KEY_PKCS8);

		DSAPublicKey pubKey = (DSAPublicKey) kp.getPublic();

		assertEquals(DSA_KEY_pub, pubKey.getY());

		DSAParams params = pubKey.getParams();
		assertEquals(params.getG(), DSA_KEY_G);
		assertEquals(params.getP(), DSA_KEY_P);
		assertEquals(params.getQ(), DSA_KEY_Q);
	}

	public void testRecoverKeyPair_Rsa() throws Exception {
		KeyPair kp = PubkeyUtils.recoverKeyPair(RSA_KEY_PKCS8);

		RSAPublicKey pubKey = (RSAPublicKey) kp.getPublic();

		assertEquals(RSA_KEY_N, pubKey.getModulus());
		assertEquals(RSA_KEY_E, pubKey.getPublicExponent());
	}

	public void testRecoverKeyPair_Ec() throws Exception {
		KeyPair kp = PubkeyUtils.recoverKeyPair(EC_KEY_PKCS8);

		ECPublicKey pubKey = (ECPublicKey) kp.getPublic();

		assertEquals(EC_KEY_pub_x, pubKey.getW().getAffineX());
		assertEquals(EC_KEY_pub_y, pubKey.getW().getAffineY());
	}

	private static class MyPrivateKey implements PrivateKey {
		public String getAlgorithm() {
			throw new UnsupportedOperationException();
		}

		public byte[] getEncoded() {
			throw new UnsupportedOperationException();
		}

		public String getFormat() {
			throw new UnsupportedOperationException();
		}
	}

	public void testRecoverPublicKey_FakeKey_Failure() throws Exception {
		try {
			PubkeyUtils.recoverPublicKey(null, new MyPrivateKey());
			fail("Should not accept unknown key types");
		} catch (NoSuchAlgorithmException expected) {
		}
	}
}
