
package com.trilead.ssh2.crypto.digest;

import java.math.BigInteger;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * HashForSSH2Types.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: HashForSSH2Types.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public class HashForSSH2Types
{
	MessageDigest md;

	public HashForSSH2Types(MessageDigest md)
	{
		this.md = md;
	}

	public HashForSSH2Types(String type)
	{
		try {
			if ("SHA1".equals(type) || "MD5".equals(type)) {
				md = MessageDigest.getInstance(type);
			} else {
				throw new IllegalArgumentException("Unknown algorithm " + type);
			}
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Unsupported algorithm " + type);
		}
	}

	public void updateByte(byte b)
	{
		/* HACK - to test it with J2ME */
		byte[] tmp = new byte[1];
		tmp[0] = b;
		md.update(tmp);
	}

	public void updateBytes(byte[] b)
	{
		md.update(b);
	}

	public void updateUINT32(int v)
	{
		md.update((byte) (v >> 24));
		md.update((byte) (v >> 16));
		md.update((byte) (v >> 8));
		md.update((byte) (v));
	}

	public void updateByteString(byte[] b)
	{
		updateUINT32(b.length);
		updateBytes(b);
	}

	public void updateBigInt(BigInteger b)
	{
		updateByteString(b.toByteArray());
	}

	public void reset()
	{
		md.reset();
	}

	public int getDigestLength()
	{
		return md.getDigestLength();
	}

	public byte[] getDigest()
	{
		byte[] tmp = new byte[md.getDigestLength()];
		getDigest(tmp);
		return tmp;
	}

	public void getDigest(byte[] out)
	{
		getDigest(out, 0);
	}

	public void getDigest(byte[] out, int off)
	{
		try {
			md.digest(out, off, out.length - off);
		} catch (DigestException e) {
			// TODO is this right?!
			throw new RuntimeException("Unable to digest", e);
		}
	}
}
