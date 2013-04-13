/**
 *
 */
package com.trilead.ssh2.signature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import java.util.Map;
import java.util.TreeMap;

import com.trilead.ssh2.log.Logger;
import com.trilead.ssh2.packets.TypesReader;
import com.trilead.ssh2.packets.TypesWriter;

/**
 * @author Kenny Root
 *
 */
public class ECDSASHA2Verify {
	private static final Logger log = Logger.getLogger(ECDSASHA2Verify.class);

	public static final String ECDSA_SHA2_PREFIX = "ecdsa-sha2-";

	private static final String NISTP256 = "nistp256";
	private static final String NISTP256_OID = "1.2.840.10045.3.1.7";
	private static final String NISTP384 = "nistp384";
	private static final String NISTP384_OID = "1.3.132.0.34";
	private static final String NISTP521 = "nistp521";
	private static final String NISTP521_OID = "1.3.132.0.35";

	private static final Map<String, ECParameterSpec> CURVES = new TreeMap<String, ECParameterSpec>();
	static {
		CURVES.put(NISTP256, EllipticCurves.nistp256);
		CURVES.put(NISTP384, EllipticCurves.nistp384);
		CURVES.put(NISTP521, EllipticCurves.nistp521);
	}

	private static final Map<Integer, String> CURVE_SIZES = new TreeMap<Integer, String>();
	static {
		CURVE_SIZES.put(256, NISTP256);
		CURVE_SIZES.put(384, NISTP384);
		CURVE_SIZES.put(521, NISTP521);
	}

	private static final Map<String, String> CURVE_OIDS = new TreeMap<String, String>();
	static {
		CURVE_OIDS.put(NISTP256_OID, NISTP256);
		CURVE_OIDS.put(NISTP384_OID, NISTP256);
		CURVE_OIDS.put(NISTP521_OID, NISTP256);
	}

	public static int[] getCurveSizes() {
		int[] keys = new int[CURVE_SIZES.size()];
		int i = 0;
		for (Integer n : CURVE_SIZES.keySet().toArray(new Integer[keys.length])) {
			keys[i++] = n;
		}
		return keys;
	}

	public static ECParameterSpec getCurveForSize(int size) {
		final String name = CURVE_SIZES.get(size);
		if (name == null) {
			return null;
		}
		return CURVES.get(name);
	}

	public static ECPublicKey decodeSSHECDSAPublicKey(byte[] key) throws IOException
	{
		TypesReader tr = new TypesReader(key);

		String key_format = tr.readString();

		if (key_format.startsWith(ECDSA_SHA2_PREFIX) == false)
			throw new IllegalArgumentException("This is not an ECDSA public key");

		String curveName = tr.readString();
		byte[] groupBytes = tr.readByteString();

		if (tr.remain() != 0)
			throw new IOException("Padding in ECDSA public key!");

		if (key_format.equals(ECDSA_SHA2_PREFIX + curveName) == false) {
			throw new IOException("Key format is inconsistent with curve name: " + key_format
					+ " != " + curveName);
		}

		ECParameterSpec params = CURVES.get(curveName);
		if (params == null) {
			throw new IOException("Curve is not supported: " + curveName);
		}

		ECPoint group = ECDSASHA2Verify.decodeECPoint(groupBytes, params.getCurve());
		if (group == null) {
			throw new IOException("Invalid ECDSA group");
		}

		KeySpec keySpec = new ECPublicKeySpec(group, params);

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

		String curveName = getCurveName(key.getParams());

		String keyFormat = ECDSA_SHA2_PREFIX + curveName;

		tw.writeString(keyFormat);

		tw.writeString(curveName);

		byte[] encoded = encodeECPoint(key.getW(), key.getParams().getCurve());
		tw.writeString(encoded, 0, encoded.length);

		return tw.getBytes();
	}

	public static String getCurveName(ECParameterSpec params) throws IOException {
		int fieldSize = getCurveSize(params);
		final String curveName = getCurveName(fieldSize);
		if (curveName == null) {
			throw new IOException("invalid curve size " + fieldSize);
		}
		return curveName;
	}

	public static String getCurveName(int fieldSize) {
		String curveName = CURVE_SIZES.get(fieldSize);
		if (curveName == null) {
			return null;
		}
		return curveName;
	}

	public static int getCurveSize(ECParameterSpec params) {
		return params.getCurve().getField().getFieldSize();
	}

	public static ECParameterSpec getCurveForOID(String oid) {
		String name = CURVE_OIDS.get(oid);
		if (name == null)
			return null;
		return CURVES.get(name);
	}

	public static byte[] decodeSSHECDSASignature(byte[] sig) throws IOException {
		byte[] rsArray = null;

		TypesReader tr = new TypesReader(sig);

		String sig_format = tr.readString();
		if (sig_format.startsWith(ECDSA_SHA2_PREFIX) == false)
			throw new IOException("Peer sent wrong signature format");

		String curveName = sig_format.substring(ECDSA_SHA2_PREFIX.length());
		if (CURVES.containsKey(curveName) == false) {
			throw new IOException("Unsupported curve: " + curveName);
		}

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
		ByteArrayOutputStream os = new ByteArrayOutputStream(6 + first + second);

		/* ASN.1 SEQUENCE tag */
		os.write(0x30);

		/* Size of SEQUENCE */
		writeLength(4 + first + second, os);

		/* ASN.1 INTEGER tag */
		os.write(0x02);

		/* "r" INTEGER length */
		writeLength(first, os);

		/* Copy in the "r" INTEGER */
		if (first != rArray.length) {
			os.write(0x00);
		}
		os.write(rArray);

		/* ASN.1 INTEGER tag */
		os.write(0x02);

		/* "s" INTEGER length */
		writeLength(second, os);

		/* Copy in the "s" INTEGER */
		if (second != sArray.length) {
			os.write(0x00);
		}
		os.write(sArray);

		return os.toByteArray();
	}

	private static final void writeLength(int length, OutputStream os) throws IOException {
		if (length <= 0x7F) {
			os.write(length);
			return;
		}

		int numOctets = 0;
		int lenCopy = length;
		while (lenCopy != 0) {
			lenCopy >>>= 8;
			numOctets++;
		}

		os.write(0x80 | numOctets);

		for (int i = (numOctets - 1) * 8; i >= 0; i -= 8) {
			os.write((byte) (length >> i));
		}
	}

	public static byte[] encodeSSHECDSASignature(byte[] sig, ECParameterSpec params) throws IOException
	{
		TypesWriter tw = new TypesWriter();

		String curveName = getCurveName(params);
		tw.writeString(ECDSA_SHA2_PREFIX + curveName);

		if ((sig[0] != 0x30) || (sig[1] != sig.length - 2) || (sig[2] != 0x02)) {
			throw new IOException("Invalid signature format");
		}

		int rLength = sig[3];
		if ((rLength + 6 > sig.length) || (sig[4 + rLength] != 0x02)) {
			throw new IOException("Invalid signature format");
		}

		int sLength = sig[5 + rLength];
		if (6 + rLength + sLength > sig.length) {
			throw new IOException("Invalid signature format");
		}

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
		byte[] encoded = rsWriter.getBytes();
		tw.writeString(encoded, 0, encoded.length);

		return tw.getBytes();
	}

	public static byte[] generateSignature(byte[] message, ECPrivateKey pk) throws IOException
	{
		final String algo = getSignatureAlgorithmForParams(pk.getParams());

		try {
			Signature s = Signature.getInstance(algo);
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
		final String algo = getSignatureAlgorithmForParams(dpk.getParams());

		try {
			Signature s = Signature.getInstance(algo);
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

	private static String getSignatureAlgorithmForParams(ECParameterSpec params) {
		int size = getCurveSize(params);
		if (size <= 256) {
			return "SHA256withECDSA";
		} else if (size <= 384) {
			return "SHA384withECDSA";
		} else {
			return "SHA512withECDSA";
		}
	}

	public static String getDigestAlgorithmForParams(ECParameterSpec params) {
		int size = getCurveSize(params);
		if (size <= 256) {
			return "SHA256";
		} else if (size <= 384) {
			return "SHA384";
		} else {
			return "SHA512";
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
			byte[] affineX = removeLeadingZeroes(group.getAffineX().toByteArray());
			System.arraycopy(affineX, 0, M, 1 + elementSize - affineX.length, affineX.length);
		}

		{
			byte[] affineY = removeLeadingZeroes(group.getAffineY().toByteArray());
			System.arraycopy(affineY, 0, M, 1 + elementSize + elementSize - affineY.length,
							 affineY.length);
		}

		return M;
	}

	private static byte[] removeLeadingZeroes(byte[] input) {
		if (input[0] != 0x00) {
			return input;
		}

		int pos = 1;
		while (pos < input.length - 1 && input[pos] == 0x00) {
			pos++;
		}

		byte[] output = new byte[input.length - pos];
		System.arraycopy(input, pos, output, 0, output.length);
		return output;
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

		public static ECParameterSpec nistp384 = new ECParameterSpec(
				new EllipticCurve(
						new ECFieldFp(new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFF", 16)),
						new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFC", 16),
						new BigInteger("B3312FA7E23EE7E4988E056BE3F82D19181D9C6EFE8141120314088F5013875AC656398D8A2ED19D2A85C8EDD3EC2AEF", 16)),
				new ECPoint(new BigInteger("AA87CA22BE8B05378EB1C71EF320AD746E1D3B628BA79B9859F741E082542A385502F25DBF55296C3A545E3872760AB7", 16),
							new BigInteger("3617DE4A96262C6F5D9E98BF9292DC29F8F41DBD289A147CE9DA3113B5F0B8C00A60B1CE1D7E819D7A431D7C90EA0E5F", 16)),
				new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81F4372DDF581A0DB248B0A77AECEC196ACCC52973", 16),
				1);

		public static ECParameterSpec nistp521 = new ECParameterSpec(
				new EllipticCurve(
						new ECFieldFp(new BigInteger("01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)),
						new BigInteger("01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC", 16),
						new BigInteger("0051953EB9618E1C9A1F929A21A0B68540EEA2DA725B99B315F3B8B489918EF109E156193951EC7E937B1652C0BD3BB1BF073573DF883D2C34F1EF451FD46B503F00", 16)),
				new ECPoint(new BigInteger("00C6858E06B70404E9CD9E3ECB662395B4429C648139053FB521F828AF606B4D3DBAA14B5E77EFE75928FE1DC127A2FFA8DE3348B3C1856A429BF97E7E31C2E5BD66", 16),
							new BigInteger("011839296A789A3BC0045C8A5FB42C7D1BD998F54449579B446817AFBD17273E662C97EE72995EF42640C550B9013FAD0761353C7086A272C24088BE94769FD16650", 16)),
				new BigInteger("01FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFA51868783BF2F966B7FCC0148F709A5D03BB5C9B8899C47AEBB6FB71E91386409", 16),
				1);
	}
}
