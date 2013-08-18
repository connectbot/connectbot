
package com.trilead.ssh2.crypto;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.DigestException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

import com.trilead.ssh2.crypto.cipher.AES;
import com.trilead.ssh2.crypto.cipher.BlockCipher;
import com.trilead.ssh2.crypto.cipher.CBCMode;
import com.trilead.ssh2.crypto.cipher.DES;
import com.trilead.ssh2.crypto.cipher.DESede;
import com.trilead.ssh2.signature.ECDSASHA2Verify;

/**
 * PEM Support.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PEMDecoder.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */
public class PEMDecoder
{
	public static final int PEM_RSA_PRIVATE_KEY = 1;
	public static final int PEM_DSA_PRIVATE_KEY = 2;
	public static final int PEM_EC_PRIVATE_KEY = 3;

	private static final int hexToInt(char c)
	{
		if ((c >= 'a') && (c <= 'f'))
		{
			return (c - 'a') + 10;
		}

		if ((c >= 'A') && (c <= 'F'))
		{
			return (c - 'A') + 10;
		}

		if ((c >= '0') && (c <= '9'))
		{
			return (c - '0');
		}

		throw new IllegalArgumentException("Need hex char");
	}

	private static byte[] hexToByteArray(String hex)
	{
		if (hex == null)
			throw new IllegalArgumentException("null argument");

		if ((hex.length() % 2) != 0)
			throw new IllegalArgumentException("Uneven string length in hex encoding.");

		byte decoded[] = new byte[hex.length() / 2];

		for (int i = 0; i < decoded.length; i++)
		{
			int hi = hexToInt(hex.charAt(i * 2));
			int lo = hexToInt(hex.charAt((i * 2) + 1));

			decoded[i] = (byte) (hi * 16 + lo);
		}

		return decoded;
	}

	private static byte[] generateKeyFromPasswordSaltWithMD5(byte[] password, byte[] salt, int keyLen)
			throws IOException
	{
		if (salt.length < 8)
			throw new IllegalArgumentException("Salt needs to be at least 8 bytes for key generation.");

		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("VM does not support MD5", e);
		}

		byte[] key = new byte[keyLen];
		byte[] tmp = new byte[md5.getDigestLength()];

		while (true)
		{
			md5.update(password, 0, password.length);
			md5.update(salt, 0, 8); // ARGH we only use the first 8 bytes of the
			// salt in this step.
			// This took me two hours until I got AES-xxx running.

			int copy = (keyLen < tmp.length) ? keyLen : tmp.length;

			try {
				md5.digest(tmp, 0, tmp.length);
			} catch (DigestException e) {
				IOException ex = new IOException("could not digest password");
				ex.initCause(e);
				throw ex;
			}

			System.arraycopy(tmp, 0, key, key.length - keyLen, copy);

			keyLen -= copy;

			if (keyLen == 0)
				return key;

			md5.update(tmp, 0, tmp.length);
		}
	}

	private static byte[] removePadding(byte[] buff, int blockSize) throws IOException
	{
		/* Removes RFC 1423/PKCS #7 padding */

		int rfc_1423_padding = buff[buff.length - 1] & 0xff;

		if ((rfc_1423_padding < 1) || (rfc_1423_padding > blockSize))
			throw new IOException("Decrypted PEM has wrong padding, did you specify the correct password?");

		for (int i = 2; i <= rfc_1423_padding; i++)
		{
			if (buff[buff.length - i] != rfc_1423_padding)
				throw new IOException("Decrypted PEM has wrong padding, did you specify the correct password?");
		}

		byte[] tmp = new byte[buff.length - rfc_1423_padding];
		System.arraycopy(buff, 0, tmp, 0, buff.length - rfc_1423_padding);
		return tmp;
	}

	public static final PEMStructure parsePEM(char[] pem) throws IOException
	{
		PEMStructure ps = new PEMStructure();

		String line = null;

		BufferedReader br = new BufferedReader(new CharArrayReader(pem));

		String endLine = null;

		while (true)
		{
			line = br.readLine();

			if (line == null)
				throw new IOException("Invalid PEM structure, '-----BEGIN...' missing");

			line = line.trim();

			if (line.startsWith("-----BEGIN DSA PRIVATE KEY-----"))
			{
				endLine = "-----END DSA PRIVATE KEY-----";
				ps.pemType = PEM_DSA_PRIVATE_KEY;
				break;
			}

			if (line.startsWith("-----BEGIN RSA PRIVATE KEY-----"))
			{
				endLine = "-----END RSA PRIVATE KEY-----";
				ps.pemType = PEM_RSA_PRIVATE_KEY;
				break;
			}

			if (line.startsWith("-----BEGIN EC PRIVATE KEY-----")) {
				endLine = "-----END EC PRIVATE KEY-----";
				ps.pemType = PEM_EC_PRIVATE_KEY;
				break;
			}
		}

		while (true)
		{
			line = br.readLine();

			if (line == null)
				throw new IOException("Invalid PEM structure, " + endLine + " missing");

			line = line.trim();

			int sem_idx = line.indexOf(':');

			if (sem_idx == -1)
				break;

			String name = line.substring(0, sem_idx + 1);
			String value = line.substring(sem_idx + 1);

			String values[] = value.split(",");

			for (int i = 0; i < values.length; i++)
				values[i] = values[i].trim();

			// Proc-Type: 4,ENCRYPTED
			// DEK-Info: DES-EDE3-CBC,579B6BE3E5C60483

			if ("Proc-Type:".equals(name))
			{
				ps.procType = values;
				continue;
			}

			if ("DEK-Info:".equals(name))
			{
				ps.dekInfo = values;
				continue;
			}
			/* Ignore line */
		}

		StringBuffer keyData = new StringBuffer();

		while (true)
		{
			if (line == null)
				throw new IOException("Invalid PEM structure, " + endLine + " missing");

			line = line.trim();

			if (line.startsWith(endLine))
				break;

			keyData.append(line);

			line = br.readLine();
		}

		char[] pem_chars = new char[keyData.length()];
		keyData.getChars(0, pem_chars.length, pem_chars, 0);

		ps.data = Base64.decode(pem_chars);

		if (ps.data.length == 0)
			throw new IOException("Invalid PEM structure, no data available");

		return ps;
	}

	private static final void decryptPEM(PEMStructure ps, byte[] pw) throws IOException
	{
		if (ps.dekInfo == null)
			throw new IOException("Broken PEM, no mode and salt given, but encryption enabled");

		if (ps.dekInfo.length != 2)
			throw new IOException("Broken PEM, DEK-Info is incomplete!");

		String algo = ps.dekInfo[0];
		byte[] salt = hexToByteArray(ps.dekInfo[1]);

		BlockCipher bc = null;

		if (algo.equals("DES-EDE3-CBC"))
		{
			DESede des3 = new DESede();
			des3.init(false, generateKeyFromPasswordSaltWithMD5(pw, salt, 24));
			bc = new CBCMode(des3, salt, false);
		}
		else if (algo.equals("DES-CBC"))
		{
			DES des = new DES();
			des.init(false, generateKeyFromPasswordSaltWithMD5(pw, salt, 8));
			bc = new CBCMode(des, salt, false);
		}
		else if (algo.equals("AES-128-CBC"))
		{
			AES aes = new AES();
			aes.init(false, generateKeyFromPasswordSaltWithMD5(pw, salt, 16));
			bc = new CBCMode(aes, salt, false);
		}
		else if (algo.equals("AES-192-CBC"))
		{
			AES aes = new AES();
			aes.init(false, generateKeyFromPasswordSaltWithMD5(pw, salt, 24));
			bc = new CBCMode(aes, salt, false);
		}
		else if (algo.equals("AES-256-CBC"))
		{
			AES aes = new AES();
			aes.init(false, generateKeyFromPasswordSaltWithMD5(pw, salt, 32));
			bc = new CBCMode(aes, salt, false);
		}
		else
		{
			throw new IOException("Cannot decrypt PEM structure, unknown cipher " + algo);
		}

		if ((ps.data.length % bc.getBlockSize()) != 0)
			throw new IOException("Invalid PEM structure, size of encrypted block is not a multiple of "
					+ bc.getBlockSize());

		/* Now decrypt the content */

		byte[] dz = new byte[ps.data.length];

		for (int i = 0; i < ps.data.length / bc.getBlockSize(); i++)
		{
			bc.transformBlock(ps.data, i * bc.getBlockSize(), dz, i * bc.getBlockSize());
		}

		/* Now check and remove RFC 1423/PKCS #7 padding */

		dz = removePadding(dz, bc.getBlockSize());

		ps.data = dz;
		ps.dekInfo = null;
		ps.procType = null;
	}

	public static final boolean isPEMEncrypted(PEMStructure ps) throws IOException
	{
		if (ps.procType == null)
			return false;

		if (ps.procType.length != 2)
			throw new IOException("Unknown Proc-Type field.");

		if ("4".equals(ps.procType[0]) == false)
			throw new IOException("Unknown Proc-Type field (" + ps.procType[0] + ")");

		if ("ENCRYPTED".equals(ps.procType[1]))
			return true;

		return false;
	}

	public static KeyPair decode(char[] pem, String password) throws IOException
	{
		PEMStructure ps = parsePEM(pem);
		return decode(ps, password);
	}

	public static KeyPair decode(PEMStructure ps, String password) throws IOException
	{
		if (isPEMEncrypted(ps))
		{
			if (password == null)
				throw new IOException("PEM is encrypted, but no password was specified");

			decryptPEM(ps, password.getBytes("ISO-8859-1"));
		}

		if (ps.pemType == PEM_DSA_PRIVATE_KEY)
		{
			SimpleDERReader dr = new SimpleDERReader(ps.data);

			byte[] seq = dr.readSequenceAsByteArray();

			if (dr.available() != 0)
				throw new IOException("Padding in DSA PRIVATE KEY DER stream.");

			dr.resetInput(seq);

			BigInteger version = dr.readInt();

			if (version.compareTo(BigInteger.ZERO) != 0)
				throw new IOException("Wrong version (" + version + ") in DSA PRIVATE KEY DER stream.");

			BigInteger p = dr.readInt();
			BigInteger q = dr.readInt();
			BigInteger g = dr.readInt();
			BigInteger y = dr.readInt();
			BigInteger x = dr.readInt();

			if (dr.available() != 0)
				throw new IOException("Padding in DSA PRIVATE KEY DER stream.");

			DSAPrivateKeySpec privSpec = new DSAPrivateKeySpec(x, p, q, g);
			DSAPublicKeySpec pubSpec = new DSAPublicKeySpec(y, p, q, g);

			return generateKeyPair("DSA", privSpec, pubSpec);
		}

		if (ps.pemType == PEM_RSA_PRIVATE_KEY)
		{
			SimpleDERReader dr = new SimpleDERReader(ps.data);

			byte[] seq = dr.readSequenceAsByteArray();

			if (dr.available() != 0)
				throw new IOException("Padding in RSA PRIVATE KEY DER stream.");

			dr.resetInput(seq);

			BigInteger version = dr.readInt();

			if ((version.compareTo(BigInteger.ZERO) != 0) && (version.compareTo(BigInteger.ONE) != 0))
				throw new IOException("Wrong version (" + version + ") in RSA PRIVATE KEY DER stream.");

			BigInteger n = dr.readInt();
			BigInteger e = dr.readInt();
			BigInteger d = dr.readInt();
			// TODO: is this right?
			BigInteger primeP = dr.readInt();
			BigInteger primeQ = dr.readInt();
			BigInteger expP = dr.readInt();
			BigInteger expQ = dr.readInt();
			BigInteger coeff = dr.readInt();

			RSAPrivateKeySpec privSpec = new RSAPrivateCrtKeySpec(n, e, d, primeP, primeQ, expP, expQ, coeff);
			RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(n, e);

			return generateKeyPair("RSA", privSpec, pubSpec);
		}

		if (ps.pemType == PEM_EC_PRIVATE_KEY) {
			SimpleDERReader dr = new SimpleDERReader(ps.data);

			byte[] seq = dr.readSequenceAsByteArray();

			if (dr.available() != 0)
				throw new IOException("Padding in EC PRIVATE KEY DER stream.");

			dr.resetInput(seq);

			BigInteger version = dr.readInt();

			if ((version.compareTo(BigInteger.ONE) != 0))
				throw new IOException("Wrong version (" + version + ") in EC PRIVATE KEY DER stream.");

			byte[] privateBytes = dr.readOctetString();

			String curveOid = null;
			byte[] publicBytes = null;
			while (dr.available() > 0) {
				int type = dr.readConstructedType();
				SimpleDERReader cr = dr.readConstructed();
				switch (type) {
				case 0:
					curveOid = cr.readOid();
					break;
				case 1:
					publicBytes = cr.readOctetString();
					break;
				}
			}

			ECParameterSpec params = ECDSASHA2Verify.getCurveForOID(curveOid);
			if (params == null)
				throw new IOException("invalid OID");

			BigInteger s = new BigInteger(privateBytes);
			byte[] publicBytesSlice = new byte[publicBytes.length - 1];
			System.arraycopy(publicBytes, 1, publicBytesSlice, 0, publicBytesSlice.length);
			ECPoint w = ECDSASHA2Verify.decodeECPoint(publicBytesSlice, params.getCurve());

			ECPrivateKeySpec privSpec = new ECPrivateKeySpec(s, params);
			ECPublicKeySpec pubSpec = new ECPublicKeySpec(w, params);

			return generateKeyPair("EC", privSpec, pubSpec);
		}

		throw new IOException("PEM problem: it is of unknown type");
	}

	/**
	 * Generate a {@code KeyPair} given an {@code algorithm} and {@code KeySpec}.
	 */
	private static KeyPair generateKeyPair(String algorithm, KeySpec privSpec, KeySpec pubSpec)
			throws IOException {
		try {
			final KeyFactory kf = KeyFactory.getInstance(algorithm);
			final PublicKey pubKey = kf.generatePublic(pubSpec);
			final PrivateKey privKey = kf.generatePrivate(privSpec);
			return new KeyPair(pubKey, privKey);
		} catch (NoSuchAlgorithmException ex) {
			IOException ioex = new IOException();
			ioex.initCause(ex);
			throw ioex;
		} catch (InvalidKeySpecException ex) {
			IOException ioex = new IOException("invalid keyspec");
			ioex.initCause(ex);
			throw ioex;
		}
	}
}
