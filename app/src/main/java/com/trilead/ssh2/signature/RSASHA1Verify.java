
package com.trilead.ssh2.signature;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;

import com.trilead.ssh2.log.Logger;
import com.trilead.ssh2.packets.TypesReader;
import com.trilead.ssh2.packets.TypesWriter;


/**
 * RSASHA1Verify.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: RSASHA1Verify.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public class RSASHA1Verify
{
	private static final Logger log = Logger.getLogger(RSASHA1Verify.class);

	public static RSAPublicKey decodeSSHRSAPublicKey(byte[] key) throws IOException
	{
		TypesReader tr = new TypesReader(key);

		String key_format = tr.readString();

		if (key_format.equals("ssh-rsa") == false)
			throw new IllegalArgumentException("This is not a ssh-rsa public key");

		BigInteger e = tr.readMPINT();
		BigInteger n = tr.readMPINT();

		if (tr.remain() != 0)
			throw new IOException("Padding in RSA public key!");

		KeySpec keySpec = new RSAPublicKeySpec(n, e);

		try {
			KeyFactory kf = KeyFactory.getInstance("RSA");
			return (RSAPublicKey) kf.generatePublic(keySpec);
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

	public static byte[] encodeSSHRSAPublicKey(RSAPublicKey pk) throws IOException
	{
		TypesWriter tw = new TypesWriter();

		tw.writeString("ssh-rsa");
		tw.writeMPInt(pk.getPublicExponent());
		tw.writeMPInt(pk.getModulus());

		return tw.getBytes();
	}

	public static byte[] decodeSSHRSASignature(byte[] sig) throws IOException
	{
		TypesReader tr = new TypesReader(sig);

		String sig_format = tr.readString();

		if (sig_format.equals("ssh-rsa") == false)
			throw new IOException("Peer sent wrong signature format");

		/* S is NOT an MPINT. "The value for 'rsa_signature_blob' is encoded as a string
		 * containing s (which is an integer, without lengths or padding, unsigned and in
		 * network byte order)." See also below.
		 */

		byte[] s = tr.readByteString();

		if (s.length == 0)
			throw new IOException("Error in RSA signature, S is empty.");

		if (log.isEnabled())
		{
			log.log(80, "Decoding ssh-rsa signature string (length: " + s.length + ")");
		}

		if (tr.remain() != 0)
			throw new IOException("Padding in RSA signature!");

		if (s[0] == 0 && s[1] == 0 && s[2] == 0) {
			int i = 0;
			int j = ((s[i++] << 24) & 0xff000000) | ((s[i++] << 16) & 0x00ff0000)
					| ((s[i++] << 8) & 0x0000ff00) | ((s[i++]) & 0x000000ff);
			i += j;
			j = ((s[i++] << 24) & 0xff000000) | ((s[i++] << 16) & 0x00ff0000)
					| ((s[i++] << 8) & 0x0000ff00) | ((s[i++]) & 0x000000ff);
			byte[] tmp = new byte[j];
			System.arraycopy(s, i, tmp, 0, j);
			sig = tmp;
		}

		return s;
	}

	public static byte[] encodeSSHRSASignature(byte[] s) throws IOException
	{
		TypesWriter tw = new TypesWriter();

		tw.writeString("ssh-rsa");

		/* S is NOT an MPINT. "The value for 'rsa_signature_blob' is encoded as a string
		 * containing s (which is an integer, without lengths or padding, unsigned and in
		 * network byte order)."
		 */

		/* Remove first zero sign byte, if present */

		if ((s.length > 1) && (s[0] == 0x00))
			tw.writeString(s, 1, s.length - 1);
		else
			tw.writeString(s, 0, s.length);

		return tw.getBytes();
	}

	public static byte[] generateSignature(byte[] message, RSAPrivateKey pk) throws IOException
	{
		try {
			Signature s = Signature.getInstance("SHA1withRSA");
			s.initSign(pk);
			s.update(message);
			return s.sign();
		} catch (NoSuchAlgorithmException e) {
			IOException ex = new IOException();
			ex.initCause(e);
			throw ex;
		} catch (InvalidKeyException e) {
			IOException ex =  new IOException();
			ex.initCause(e);
			throw ex;
		} catch (SignatureException e) {
			IOException ex =  new IOException();
			ex.initCause(e);
			throw ex;
		}
	}

	public static boolean verifySignature(byte[] message, byte[] ds, RSAPublicKey dpk) throws IOException
	{
		try {
			Signature s = Signature.getInstance("SHA1withRSA");
			s.initVerify(dpk);
			s.update(message);
			return s.verify(ds);
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
