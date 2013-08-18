
package com.trilead.ssh2.signature;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import com.trilead.ssh2.log.Logger;
import com.trilead.ssh2.packets.TypesReader;
import com.trilead.ssh2.packets.TypesWriter;


/**
 * DSASHA1Verify.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: DSASHA1Verify.java,v 1.2 2008/04/01 12:38:09 cplattne Exp $
 */
public class DSASHA1Verify
{
	private static final Logger log = Logger.getLogger(DSASHA1Verify.class);

	public static DSAPublicKey decodeSSHDSAPublicKey(byte[] key) throws IOException
	{
		TypesReader tr = new TypesReader(key);

		String key_format = tr.readString();

		if (key_format.equals("ssh-dss") == false)
			throw new IllegalArgumentException("This is not a ssh-dss public key!");

		BigInteger p = tr.readMPINT();
		BigInteger q = tr.readMPINT();
		BigInteger g = tr.readMPINT();
		BigInteger y = tr.readMPINT();

		if (tr.remain() != 0)
			throw new IOException("Padding in DSA public key!");

		try {
			KeyFactory kf = KeyFactory.getInstance("DSA");

			KeySpec ks = new DSAPublicKeySpec(y, p, q, g);
			return (DSAPublicKey) kf.generatePublic(ks);
		} catch (NoSuchAlgorithmException e) {
			IOException ex = new IOException();
			ex.initCause(e);
			throw ex;
		} catch (InvalidKeySpecException e) {
			IOException ex = new IOException();
			ex.initCause(e);
			throw ex;
		}
	}

	public static byte[] encodeSSHDSAPublicKey(DSAPublicKey pk) throws IOException
	{
		TypesWriter tw = new TypesWriter();

		tw.writeString("ssh-dss");

		DSAParams params = pk.getParams();
		tw.writeMPInt(params.getP());
		tw.writeMPInt(params.getQ());
		tw.writeMPInt(params.getG());
		tw.writeMPInt(pk.getY());

		return tw.getBytes();
	}

	/**
	 * Convert from Java's signature ASN.1 encoding to the SSH spec.
	 * <p>
	 * Java ASN.1 encoding:
	 * <pre>
	 * SEQUENCE ::= {
	 *    r INTEGER,
	 *    s INTEGER
	 * }
	 * </pre>
	 */
	public static byte[] encodeSSHDSASignature(byte[] ds)
	{
		TypesWriter tw = new TypesWriter();

		tw.writeString("ssh-dss");

		int len, index;

		index = 3;
		len = ds[index++] & 0xff;
		byte[] r = new byte[len];
		System.arraycopy(ds, index, r, 0, r.length);

		index = index + len + 1;
		len = ds[index++] & 0xff;
		byte[] s = new byte[len];
		System.arraycopy(ds, index, s, 0, s.length);

		byte[] a40 = new byte[40];

		/* Patch (unsigned) r and s into the target array. */

		int r_copylen = (r.length < 20) ? r.length : 20;
		int s_copylen = (s.length < 20) ? s.length : 20;

		System.arraycopy(r, r.length - r_copylen, a40, 20 - r_copylen, r_copylen);
		System.arraycopy(s, s.length - s_copylen, a40, 40 - s_copylen, s_copylen);

		tw.writeString(a40, 0, 40);

		return tw.getBytes();
	}

	public static byte[] decodeSSHDSASignature(byte[] sig) throws IOException
	{
		byte[] rsArray = null;
		
		if (sig.length == 40)
		{
			/* OK, another broken SSH server. */
			rsArray = sig;
		}
		else
		{
			/* Hopefully a server obeying the standard... */
			TypesReader tr = new TypesReader(sig);

			String sig_format = tr.readString();
			if (sig_format.equals("ssh-dss") == false)
				throw new IOException("Peer sent wrong signature format");

			rsArray = tr.readByteString();

			if (rsArray.length != 40)
				throw new IOException("Peer sent corrupt signature");

			if (tr.remain() != 0)
				throw new IOException("Padding in DSA signature!");
		}

		int i = 0;
		int j = 0;
		byte[] tmp;

		if (rsArray[0] == 0 && rsArray[1] == 0 && rsArray[2] == 0) {
			j = ((rsArray[i++] << 24) & 0xff000000) | ((rsArray[i++] << 16) & 0x00ff0000)
					| ((rsArray[i++] << 8) & 0x0000ff00) | ((rsArray[i++]) & 0x000000ff);
			i += j;
			j = ((rsArray[i++] << 24) & 0xff000000) | ((rsArray[i++] << 16) & 0x00ff0000)
					| ((rsArray[i++] << 8) & 0x0000ff00) | ((rsArray[i++]) & 0x000000ff);
			tmp = new byte[j];
			System.arraycopy(rsArray, i, tmp, 0, j);
			rsArray = tmp;
		}

		/* ASN.1 */
		int frst = ((rsArray[0] & 0x80) != 0 ? 1 : 0);
		int scnd = ((rsArray[20] & 0x80) != 0 ? 1 : 0);

		/* Calculate output length */
		int length = rsArray.length + 6 + frst + scnd;
		tmp = new byte[length];

		/* DER-encoding to match Java */
		tmp[0] = (byte) 0x30;

			if (rsArray.length != 40)
				throw new IOException("Peer sent corrupt signature");
		/* Calculate length */
		tmp[1] = (byte) 0x2c;
		tmp[1] += frst;
		tmp[1] += scnd;

		/* First item */
		tmp[2] = (byte) 0x02;

		/* First item length */
		tmp[3] = (byte) 0x14;
		tmp[3] += frst;

		/* Copy in the data for first item */
		System.arraycopy(rsArray, 0, tmp, 4 + frst, 20);

		/* Second item */
		tmp[4 + tmp[3]] = (byte) 0x02;

		/* Second item length */
		tmp[5 + tmp[3]] = (byte) 0x14;
		tmp[5 + tmp[3]] += scnd;

		/* Copy in the data for the second item */
		System.arraycopy(rsArray, 20, tmp, 6 + tmp[3] + scnd, 20);

		/* Swap buffers */
		rsArray = tmp;

		return rsArray;
	}

	public static boolean verifySignature(byte[] message, byte[] ds, DSAPublicKey dpk) throws IOException
	{
		try {
			Signature s = Signature.getInstance("SHA1withDSA");
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

	public static byte[] generateSignature(byte[] message, DSAPrivateKey pk, SecureRandom rnd) throws IOException
	{
		try {
			Signature s = Signature.getInstance("SHA1withDSA");
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
}
