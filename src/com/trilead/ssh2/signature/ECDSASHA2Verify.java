/**
 *
 */
package com.trilead.ssh2.signature;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import com.trilead.ssh2.log.Logger;
import com.trilead.ssh2.packets.TypesReader;
import com.trilead.ssh2.packets.TypesWriter;

/**
 * @author Kenny Root
 *
 */
public class ECDSASHA2Verify {
	private static final Logger log = Logger.getLogger(ECDSASHA2Verify.class);

	public static ECPublicKey decodeSSHECDSAPublicKey(byte[] key) throws IOException
	{
		TypesReader tr = new TypesReader(key);

		String key_format = tr.readString();

		if (key_format.equals("ecdsa-sha2-nistp256") == false)
			throw new IllegalArgumentException("This is not an ecdsa-sha2-nistp256 public key");

		String curveName = tr.readString();
		byte[] groupBytes = tr.readByteString();

		if (tr.remain() != 0)
			throw new IOException("Padding in ECDSA public key!");

		if (!"nistp256".equals(curveName)) {
			throw new IOException("Curve is not nistp256");
		}

		ECParameterSpec nistp256 = ECDSASHA2Verify.EllipticCurves.nistp256;
		ECPoint group = ECDSASHA2Verify.decodeECPoint(groupBytes, nistp256.getCurve());
		if (group == null) {
			throw new IOException("Invalid ECDSA group");
		}

		KeySpec keySpec = new ECPublicKeySpec(group, nistp256);

		try {
			KeyFactory kf = KeyFactory.getInstance("EC");
			return (ECPublicKey) kf.generatePublic(keySpec);
		} catch (NoSuchAlgorithmException nsae) {
			IOException ioe = new IOException("No RSA KeyFactory available");
			ioe.initCause(nsae);
			throw ioe;
		} catch (InvalidKeySpecException ikse) {
			IOException ioe = new IOException("No RSA KeyFactory available");
			ioe.initCause(ikse);
			throw ioe;
		}
	}

	public static byte[] encodeSSHECDSAPublicKey(ECPublicKey key) throws IOException {
		TypesWriter tw = new TypesWriter();

		tw.writeString("ecdsa-sha2-nistp256");

		tw.writeString("nistp256");

		tw.writeBytes(encodeECPoint(key.getW(), key.getParams().getCurve()));

		return tw.getBytes();
	}

	public static byte[] decodeSSHECDSASignature(byte[] sig) throws IOException {
		byte[] rsArray = null;

		/* Hopefully a server obeying the standard... */
		TypesReader tr = new TypesReader(sig);

		String sig_format = tr.readString();
		if (sig_format.equals("ecdsa-sha2-nistp256") == false)
			throw new IOException("Peer sent wrong signature format");

		rsArray = tr.readByteString();

		if (tr.remain() != 0)
			throw new IOException("Padding in ECDSA signature!");

		byte[] rArray;
		byte[] sArray;
		{
			TypesReader rsReader = new TypesReader(rsArray);
			rArray = rsReader.readMPINT().toByteArray();
			sArray = rsReader.readMPINT().toByteArray();
		}

		int first = rArray.length;
		int second = sArray.length;

		/* We can't have the high bit set, so add an extra zero at the beginning if so. */
		if ((rArray[0] & 0x80) != 0) {
			first++;
		}
		if ((sArray[0] & 0x80) != 0) {
			second++;
		}

		/* Calculate total output length */
		int length = 6 + first + second;
		byte[] asn1 = new byte[length];

		/* ASN.1 SEQUENCE tag */
		asn1[0] = (byte) 0x30;

		/* Size of SEQUENCE */
		asn1[1] = (byte) (4 + first + second);

		/* ASN.1 INTEGER tag */
		asn1[2] = (byte) 0x02;

		/* "r" INTEGER length */
		asn1[3] = (byte) first;

		/* Copy in the "r" INTEGER */
		System.arraycopy(rArray, 0, asn1, (4 + first) - rArray.length, rArray.length);

		/* ASN.1 INTEGER tag */
		asn1[rArray.length + 4] = (byte) 0x02;

		/* "s" INTEGER length */
		asn1[rArray.length + 5] = (byte) second;

		/* Copy in the "s" INTEGER */
		System.arraycopy(sArray, 0, asn1, (6 + first + second) - sArray.length, sArray.length);

		return asn1;
	}

	public static byte[] encodeSSHECDSASignature(byte[] sig) throws IOException
	{
		TypesWriter tw = new TypesWriter();

		tw.writeString("ecdsa-sha2-nistp256");

		int rLength = sig[3];
		int sLength = sig[5 + rLength];

		byte[] rArray = new byte[rLength];
		byte[] sArray = new byte[sLength];

		System.arraycopy(sig, 4, rArray, 0, rLength);
		System.arraycopy(sig, 6 + rLength, sArray, 0, sLength);

		BigInteger r = new BigInteger(rArray);
		BigInteger s = new BigInteger(sArray);

		// Write the <r,s> to its own types writer.
		TypesWriter rsWriter = new TypesWriter();
		rsWriter.writeMPInt(r);
		rsWriter.writeMPInt(s);
		tw.writeBytes(rsWriter.getBytes());

		return tw.getBytes();
	}

	public static byte[] generateSignature(byte[] message, ECPrivateKey pk) throws IOException
	{
		try {
			Signature s = Signature.getInstance("SHA256withECDSA");
			s.initSign(pk);
			s.update(message);
			return s.sign();
		} catch (NoSuchAlgorithmException e) {
			IOException ex = new IOException();
			ex.initCause(e);
			throw ex;
		} catch (InvalidKeyException e) {
			IOException ex = new IOException();
			ex.initCause(e);
			throw ex;
		} catch (SignatureException e) {
			IOException ex = new IOException();
			ex.initCause(e);
			throw ex;
		}
	}

	public static boolean verifySignature(byte[] message, byte[] ds, ECPublicKey dpk) throws IOException
	{
		try {
			Signature s = Signature.getInstance("SHA256withECDSA");
			s.initVerify(dpk);
			s.update(message);
			return s.verify(ds);
		} catch (NoSuchAlgorithmException e) {
			IOException ex = new IOException("No such algorithm");
			ex.initCause(e);
			throw ex;
		} catch (InvalidKeyException e) {
			IOException ex = new IOException("No such algorithm");
			ex.initCause(e);
			throw ex;
		} catch (SignatureException e) {
			IOException ex = new IOException();
			ex.initCause(e);
			throw ex;
		}
	}

	/**
	 * Decode an OctetString to EllipticCurvePoint according to SECG 2.3.4
	 */
	public static ECPoint decodeECPoint(byte[] M, EllipticCurve curve) {
		if (M.length == 0) {
			return null;
		}

		// M has len 2 ceil(log_2(q)/8) + 1 ?
		int elementSize = (curve.getField().getFieldSize() + 7) / 8;
		if (M.length != 2 * elementSize + 1) {
			return null;
		}

		// step 3.2
		if (M[0] != 0x04) {
			return null;
		}

		// Step 3.3
		byte[] xp = new byte[elementSize];
		System.arraycopy(M, 1, xp, 0, elementSize);

		// Step 3.4
		byte[] yp = new byte[elementSize];
		System.arraycopy(M, 1 + elementSize, yp, 0, elementSize);

		ECPoint P = new ECPoint(new BigInteger(1, xp), new BigInteger(1, yp));

		// TODO check point 3.5

		// Step 3.6
		return P;
	}

	/**
	 * Encode EllipticCurvePoint to an OctetString
	 */
	public static byte[] encodeECPoint(ECPoint group, EllipticCurve curve)
	{
		// M has len 2 ceil(log_2(q)/8) + 1 ?
		int elementSize = (curve.getField().getFieldSize() + 7) / 8;
		byte[] M = new byte[2 * elementSize + 1];

		// Uncompressed format
		M[0] = 0x04;

		{
			byte[] affineX = group.getAffineX().toByteArray();
			System.arraycopy(affineX, 0, M, 1, elementSize - affineX.length);
		}

		{
			byte[] affineY = group.getAffineY().toByteArray();
			System.arraycopy(affineY, 0, M, 1 + elementSize, elementSize - affineY.length);
		}

		return M;
	}

	public static class EllipticCurves {
		public static ECParameterSpec nistp256 = new ECParameterSpec(
				new EllipticCurve(
						new ECFieldFp(new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16)),
						new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC", 16),
						new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)),
				new ECPoint(new BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16),
							new BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16)),
				new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16),
				1);
	}
}
