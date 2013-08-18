/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.keyczar.jce.EcCore;

import android.util.Log;

import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.crypto.SimpleDERReader;
import com.trilead.ssh2.signature.DSASHA1Verify;
import com.trilead.ssh2.signature.ECDSASHA2Verify;
import com.trilead.ssh2.signature.RSASHA1Verify;

public class PubkeyUtils {
	private static final String TAG = "PubkeyUtils";

	public static final String PKCS8_START = "-----BEGIN PRIVATE KEY-----";
	public static final String PKCS8_END = "-----END PRIVATE KEY-----";

	// Size in bytes of salt to use.
	private static final int SALT_SIZE = 8;

	// Number of iterations for password hashing. PKCS#5 recommends 1000
	private static final int ITERATIONS = 1000;

	// Cannot be instantiated
	private PubkeyUtils() {
	}

	public static String formatKey(Key key){
		String algo = key.getAlgorithm();
		String fmt = key.getFormat();
		byte[] encoded = key.getEncoded();
		return "Key[algorithm=" + algo + ", format=" + fmt +
			", bytes=" + encoded.length + "]";
	}

	public static byte[] sha256(byte[] data) throws NoSuchAlgorithmException {
		return MessageDigest.getInstance("SHA-256").digest(data);
	}

	public static byte[] cipher(int mode, byte[] data, byte[] secret) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		SecretKeySpec secretKeySpec = new SecretKeySpec(sha256(secret), "AES");
		Cipher c = Cipher.getInstance("AES");
		c.init(mode, secretKeySpec);
		return c.doFinal(data);
	}

	public static byte[] encrypt(byte[] cleartext, String secret) throws Exception {
		byte[] salt = new byte[SALT_SIZE];

		byte[] ciphertext = Encryptor.encrypt(salt, ITERATIONS, secret, cleartext);

		byte[] complete = new byte[salt.length + ciphertext.length];

		System.arraycopy(salt, 0, complete, 0, salt.length);
		System.arraycopy(ciphertext, 0, complete, salt.length, ciphertext.length);

		Arrays.fill(salt, (byte) 0x00);
		Arrays.fill(ciphertext, (byte) 0x00);

		return complete;
	}

	public static byte[] decrypt(byte[] saltAndCiphertext, String secret) throws Exception {
		try {
			byte[] salt = new byte[SALT_SIZE];
			byte[] ciphertext = new byte[saltAndCiphertext.length - salt.length];

			System.arraycopy(saltAndCiphertext, 0, salt, 0, salt.length);
			System.arraycopy(saltAndCiphertext, salt.length, ciphertext, 0, ciphertext.length);

			return Encryptor.decrypt(salt, ITERATIONS, secret, ciphertext);
		} catch (Exception e) {
			Log.d("decrypt", "Could not decrypt with new method", e);
			// We might be using the old encryption method.
			return cipher(Cipher.DECRYPT_MODE, saltAndCiphertext, secret.getBytes());
		}
	}

	public static byte[] getEncodedPrivate(PrivateKey pk, String secret) throws Exception {
		final byte[] encoded = pk.getEncoded();
		if (secret == null || secret.length() == 0) {
			return encoded;
		}
		return encrypt(pk.getEncoded(), secret);
	}

	public static PrivateKey decodePrivate(byte[] encoded, String keyType) throws NoSuchAlgorithmException, InvalidKeySpecException {
		PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(encoded);
		KeyFactory kf = KeyFactory.getInstance(keyType);
		return kf.generatePrivate(privKeySpec);
	}

	public static PrivateKey decodePrivate(byte[] encoded, String keyType, String secret) throws Exception {
		if (secret != null && secret.length() > 0)
			return decodePrivate(decrypt(encoded, secret), keyType);
		else
			return decodePrivate(encoded, keyType);
	}

	public static PublicKey decodePublic(byte[] encoded, String keyType) throws NoSuchAlgorithmException, InvalidKeySpecException {
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(encoded);
		KeyFactory kf = KeyFactory.getInstance(keyType);
		return kf.generatePublic(pubKeySpec);
	}

	static String getAlgorithmForOid(String oid) throws NoSuchAlgorithmException {
		if ("1.2.840.10045.2.1".equals(oid)) {
			return "EC";
		} else if ("1.2.840.113549.1.1.1".equals(oid)) {
			return "RSA";
		} else if ("1.2.840.10040.4.1".equals(oid)) {
			return "DSA";
		} else {
			throw new NoSuchAlgorithmException("Unknown algorithm OID " + oid);
		}
	}

	static String getOidFromPkcs8Encoded(byte[] encoded) throws NoSuchAlgorithmException {
		if (encoded == null) {
			throw new NoSuchAlgorithmException("encoding is null");
		}

		try {
			SimpleDERReader reader = new SimpleDERReader(encoded);
			reader.resetInput(reader.readSequenceAsByteArray());
			reader.readInt();
			reader.resetInput(reader.readSequenceAsByteArray());
			return reader.readOid();
		} catch (IOException e) {
			Log.w(TAG, "Could not read OID", e);
			throw new NoSuchAlgorithmException("Could not read key", e);
		}
	}

	public static KeyPair recoverKeyPair(byte[] encoded) throws NoSuchAlgorithmException,
			InvalidKeySpecException {
		final String algo = getAlgorithmForOid(getOidFromPkcs8Encoded(encoded));

		final KeySpec privKeySpec = new PKCS8EncodedKeySpec(encoded);

		final KeyFactory kf = KeyFactory.getInstance(algo);
		final PrivateKey priv = kf.generatePrivate(privKeySpec);

		return new KeyPair(recoverPublicKey(kf, priv), priv);
	}

	static PublicKey recoverPublicKey(KeyFactory kf, PrivateKey priv)
			throws NoSuchAlgorithmException, InvalidKeySpecException {
		if (priv instanceof RSAPrivateCrtKey) {
			RSAPrivateCrtKey rsaPriv = (RSAPrivateCrtKey) priv;
			return kf.generatePublic(new RSAPublicKeySpec(rsaPriv.getModulus(), rsaPriv
					.getPublicExponent()));
		} else if (priv instanceof DSAPrivateKey) {
			DSAPrivateKey dsaPriv = (DSAPrivateKey) priv;
			DSAParams params = dsaPriv.getParams();

			// Calculate public key Y
			BigInteger y = params.getG().modPow(dsaPriv.getX(), params.getP());

			return kf.generatePublic(new DSAPublicKeySpec(y, params.getP(), params.getQ(), params
					.getG()));
		} else if (priv instanceof ECPrivateKey) {
			ECPrivateKey ecPriv = (ECPrivateKey) priv;
			ECParameterSpec params = ecPriv.getParams();

			// Calculate public key Y
			ECPoint generator = params.getGenerator();
			BigInteger[] wCoords = EcCore.multiplyPointA(new BigInteger[] { generator.getAffineX(),
					generator.getAffineY() }, ecPriv.getS(), params);
			ECPoint w = new ECPoint(wCoords[0], wCoords[1]);

			return kf.generatePublic(new ECPublicKeySpec(w, params));
		} else {
			throw new NoSuchAlgorithmException("Key type must be RSA, DSA, or EC");
		}
	}

	/*
	 * OpenSSH compatibility methods
	 */

	public static String convertToOpenSSHFormat(PublicKey pk, String origNickname) throws IOException, InvalidKeyException {
		String nickname = origNickname;
		if (nickname == null)
			nickname = "connectbot@android";

		if (pk instanceof RSAPublicKey) {
			String data = "ssh-rsa ";
			data += String.valueOf(Base64.encode(RSASHA1Verify.encodeSSHRSAPublicKey((RSAPublicKey) pk)));
			return data + " " + nickname;
		} else if (pk instanceof DSAPublicKey) {
			String data = "ssh-dss ";
			data += String.valueOf(Base64.encode(DSASHA1Verify.encodeSSHDSAPublicKey((DSAPublicKey) pk)));
			return data + " " + nickname;
		} else if (pk instanceof ECPublicKey) {
			ECPublicKey ecPub = (ECPublicKey) pk;
			String keyType = ECDSASHA2Verify.getCurveName(ecPub.getParams().getCurve().getField().getFieldSize());
			String keyData = String.valueOf(Base64.encode(ECDSASHA2Verify.encodeSSHECDSAPublicKey(ecPub)));
			return ECDSASHA2Verify.ECDSA_SHA2_PREFIX + keyType + " " + keyData + " " + nickname;
		}

		throw new InvalidKeyException("Unknown key type");
	}

	/*
	 * OpenSSH compatibility methods
	 */

	/**
	 * @param trileadKey
	 * @return OpenSSH-encoded pubkey
	 */
	public static byte[] extractOpenSSHPublic(KeyPair pair) {
		try {
			PublicKey pubKey = pair.getPublic();
			if (pubKey instanceof RSAPublicKey) {
				return RSASHA1Verify.encodeSSHRSAPublicKey((RSAPublicKey) pair.getPublic());
			} else if (pubKey instanceof DSAPublicKey) {
				return DSASHA1Verify.encodeSSHDSAPublicKey((DSAPublicKey) pair.getPublic());
			} else if (pubKey instanceof ECPublicKey) {
				return ECDSASHA2Verify.encodeSSHECDSAPublicKey((ECPublicKey) pair.getPublic());
			} else {
				return null;
			}
		} catch (IOException e) {
			return null;
		}
	}

	public static String exportPEM(PrivateKey key, String secret) throws NoSuchAlgorithmException, InvalidParameterSpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, InvalidKeySpecException, IllegalBlockSizeException, IOException {
		StringBuilder sb = new StringBuilder();

		byte[] data = key.getEncoded();

		sb.append(PKCS8_START);
		sb.append('\n');

		if (secret != null) {
			byte[] salt = new byte[8];
			SecureRandom random = new SecureRandom();
			random.nextBytes(salt);

			PBEParameterSpec defParams = new PBEParameterSpec(salt, 1);
			AlgorithmParameters params = AlgorithmParameters.getInstance(key.getAlgorithm());

			params.init(defParams);

			PBEKeySpec pbeSpec = new PBEKeySpec(secret.toCharArray());

			SecretKeyFactory keyFact = SecretKeyFactory.getInstance(key.getAlgorithm());
			Cipher cipher = Cipher.getInstance(key.getAlgorithm());
			cipher.init(Cipher.WRAP_MODE, keyFact.generateSecret(pbeSpec), params);

			byte[] wrappedKey = cipher.wrap(key);

			EncryptedPrivateKeyInfo pinfo = new EncryptedPrivateKeyInfo(params, wrappedKey);

			data = pinfo.getEncoded();

			sb.append("Proc-Type: 4,ENCRYPTED\n");
			sb.append("DEK-Info: DES-EDE3-CBC,");
			sb.append(encodeHex(salt));
			sb.append("\n\n");
		}

		int i = sb.length();
		sb.append(Base64.encode(data));
		for (i += 63; i < sb.length(); i += 64) {
			sb.insert(i, "\n");
		}

		sb.append('\n');
		sb.append(PKCS8_END);
		sb.append('\n');

		return sb.toString();
	}

	private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5', '6',
			'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
	protected static String encodeHex(byte[] bytes) {
		final char[] hex = new char[bytes.length * 2];

		int i = 0;
		for (byte b : bytes) {
			hex[i++] = HEX_DIGITS[(b >> 4) & 0x0f];
			hex[i++] = HEX_DIGITS[b & 0x0f];
		}

		return String.valueOf(hex);
	}
}
