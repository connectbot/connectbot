/*
	ConnectBot: simple, powerful, open-source SSH client for Android
	Copyright (C) 2007-2008 Kenny Root, Jeffrey Sharkey

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.connectbot.util;

/**
 * This class is from:
 *
 * Encryptor.java
 * Copyright 2008 Zach Scrivena
 * zachscrivena@gmail.com
 * http://zs.freeshell.org/
 */

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


/**
 * Perform AES-128 encryption.
 */
public final class Encryptor
{
	/** name of the character set to use for converting between characters and bytes */
	private static final String CHARSET_NAME = "UTF-8";

	/** random number generator algorithm */
	private static final String RNG_ALGORITHM = "SHA1PRNG";

	/** message digest algorithm (must be sufficiently long to provide the key and initialization vector) */
	private static final String DIGEST_ALGORITHM = "SHA-256";

	/** key algorithm (must be compatible with CIPHER_ALGORITHM) */
	private static final String KEY_ALGORITHM = "AES";

	/** cipher algorithm (must be compatible with KEY_ALGORITHM) */
	private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding";


	/**
	* Private constructor that should never be called.
	*/
	private Encryptor()
	{}


	/**
	* Encrypt the specified cleartext using the given password.
	* With the correct salt, number of iterations, and password, the decrypt() method reverses
	* the effect of this method.
	* This method generates and uses a random salt, and the user-specified number of iterations
	* and password to create a 16-byte secret key and 16-byte initialization vector.
	* The secret key and initialization vector are then used in the AES-128 cipher to encrypt
	* the given cleartext.
	*
	* @param salt
	*	  salt that was used in the encryption (to be populated)
	* @param iterations
	*	  number of iterations to use in salting
	* @param password
	*	  password to be used for encryption
	* @param cleartext
	*	  cleartext to be encrypted
	* @return
	*	  ciphertext
	* @throws Exception
	*	  on any error encountered in encryption
	*/
	public static byte[] encrypt(
			final byte[] salt,
			final int iterations,
			final String password,
			final byte[] cleartext)
			throws Exception
	{
		/* generate salt randomly */
		SecureRandom.getInstance(RNG_ALGORITHM).nextBytes(salt);

		/* compute key and initialization vector */
		final MessageDigest shaDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
		byte[] pw = password.getBytes(CHARSET_NAME);

		for (int i = 0; i < iterations; i++)
		{
			/* add salt */
			final byte[] salted = new byte[pw.length + salt.length];
			System.arraycopy(pw, 0, salted, 0, pw.length);
			System.arraycopy(salt, 0, salted, pw.length, salt.length);
			Arrays.fill(pw, (byte) 0x00);

			/* compute SHA-256 digest */
			shaDigest.reset();
			pw = shaDigest.digest(salted);
			Arrays.fill(salted, (byte) 0x00);
		}

		/* extract the 16-byte key and initialization vector from the SHA-256 digest */
		final byte[] key = new byte[16];
		final byte[] iv = new byte[16];
		System.arraycopy(pw, 0, key, 0, 16);
		System.arraycopy(pw, 16, iv, 0, 16);
		Arrays.fill(pw, (byte) 0x00);

		/* perform AES-128 encryption */
		final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

		cipher.init(
				Cipher.ENCRYPT_MODE,
				new SecretKeySpec(key, KEY_ALGORITHM),
				new IvParameterSpec(iv));

		Arrays.fill(key, (byte) 0x00);
		Arrays.fill(iv, (byte) 0x00);

		return cipher.doFinal(cleartext);
	}


	/**
	* Decrypt the specified ciphertext using the given password.
	* With the correct salt, number of iterations, and password, this method reverses the effect
	* of the encrypt() method.
	* This method uses the user-specified salt, number of iterations, and password
	* to recreate the 16-byte secret key and 16-byte initialization vector.
	* The secret key and initialization vector are then used in the AES-128 cipher to decrypt
	* the given ciphertext.
	*
	* @param salt
	*	  salt to be used in decryption
	* @param iterations
	*	  number of iterations to use in salting
	* @param password
	*	  password to be used for decryption
	* @param ciphertext
	*	  ciphertext to be decrypted
	* @return
	*	  cleartext
	* @throws Exception
	*	  on any error encountered in decryption
	*/
	public static byte[] decrypt(
			final byte[] salt,
			final int iterations,
			final String password,
			final byte[] ciphertext)
			throws Exception
	{
		/* compute key and initialization vector */
		final MessageDigest shaDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
		byte[] pw = password.getBytes(CHARSET_NAME);

		for (int i = 0; i < iterations; i++)
		{
			/* add salt */
			final byte[] salted = new byte[pw.length + salt.length];
			System.arraycopy(pw, 0, salted, 0, pw.length);
			System.arraycopy(salt, 0, salted, pw.length, salt.length);
			Arrays.fill(pw, (byte) 0x00);

			/* compute SHA-256 digest */
			shaDigest.reset();
			pw = shaDigest.digest(salted);
			Arrays.fill(salted, (byte) 0x00);
		}

		/* extract the 16-byte key and initialization vector from the SHA-256 digest */
		final byte[] key = new byte[16];
		final byte[] iv = new byte[16];
		System.arraycopy(pw, 0, key, 0, 16);
		System.arraycopy(pw, 16, iv, 0, 16);
		Arrays.fill(pw, (byte) 0x00);

		/* perform AES-128 decryption */
		final Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);

		cipher.init(
				Cipher.DECRYPT_MODE,
				new SecretKeySpec(key, KEY_ALGORITHM),
				new IvParameterSpec(iv));

		Arrays.fill(key, (byte) 0x00);
		Arrays.fill(iv, (byte) 0x00);

		return cipher.doFinal(ciphertext);
	}
}
