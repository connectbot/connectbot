
package com.trilead.ssh2.crypto.digest;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

/**
 * MAC.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: MAC.java,v 1.1 2007/10/15 12:49:57 cplattne Exp $
 */
public final class MAC
{
	/**
	 * From http://tools.ietf.org/html/rfc4253
	 */
	private static final String HMAC_MD5 = "hmac-md5";

	/**
	 * From http://tools.ietf.org/html/rfc4253
	 */
	private static final String HMAC_MD5_96 = "hmac-md5-96";

	/**
	 * From http://tools.ietf.org/html/rfc4253
	 */
	private static final String HMAC_SHA1 = "hmac-sha1";

	/**
	 * From http://tools.ietf.org/html/rfc4253
	 */
	private static final String HMAC_SHA1_96 = "hmac-sha1-96";

	/**
	 * From http://tools.ietf.org/html/rfc6668
	 */
	private static final String HMAC_SHA2_256 = "hmac-sha2-256";

	/**
	 * From http://tools.ietf.org/html/rfc6668
	 */
	private static final String HMAC_SHA2_512 = "hmac-sha2-512";

	Mac mac;
	int outSize;
	int macSize;
	byte[] buffer;

	/* Higher Priority First */
	private static final String[] MAC_LIST = {
		HMAC_SHA2_256, HMAC_SHA2_512,
		HMAC_SHA1_96, HMAC_SHA1, HMAC_MD5_96, HMAC_MD5
	};

	public final static String[] getMacList()
	{
		return MAC_LIST;
	}

	public final static void checkMacList(String[] macs)
	{
		for (int i = 0; i < macs.length; i++)
			getKeyLen(macs[i]);
	}

	public final static int getKeyLen(String type)
	{
		if (HMAC_SHA1.equals(type) || HMAC_SHA1_96.equals(type))
			return 20;
		if (HMAC_MD5.equals(type) || HMAC_MD5_96.equals(type))
			return 16;
		if (HMAC_SHA2_256.equals(type))
			return 32;
		if (HMAC_SHA2_512.equals(type))
			return 64;
		throw new IllegalArgumentException("Unkown algorithm " + type);
	}

	public MAC(String type, byte[] key)
	{
		try {
			if (HMAC_SHA1.equals(type) || HMAC_SHA1_96.equals(type))
			{
				mac = Mac.getInstance("HmacSHA1");
			}
			else if (HMAC_MD5.equals(type) || HMAC_MD5_96.equals(type))
			{
				mac = Mac.getInstance("HmacMD5");
			}
			else if (HMAC_SHA2_256.equals(type))
			{
				mac = Mac.getInstance("HmacSHA256");
			}
			else if (HMAC_SHA2_512.equals(type))
			{
				mac = Mac.getInstance("HmacSHA512");
			}
			else
				throw new IllegalArgumentException("Unkown algorithm " + type);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("Unknown algorithm " + type, e);
		}

		macSize = mac.getMacLength();
		if (type.endsWith("-96")) {
			outSize = 12;
			buffer = new byte[macSize];
		} else {
			outSize = macSize;
			buffer = null;
		}

		try {
			mac.init(new SecretKeySpec(key, type));
		} catch (InvalidKeyException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public final void initMac(int seq)
	{
		mac.reset();
		mac.update((byte) (seq >> 24));
		mac.update((byte) (seq >> 16));
		mac.update((byte) (seq >> 8));
		mac.update((byte) (seq));
	}

	public final void update(byte[] packetdata, int off, int len)
	{
		mac.update(packetdata, off, len);
	}

	public final void getMac(byte[] out, int off)
	{
		try {
			if (buffer != null) {
				mac.doFinal(buffer, 0);
				System.arraycopy(buffer, 0, out, off, out.length - off);
			} else {
				mac.doFinal(out, off);
			}
		} catch (ShortBufferException e) {
			throw new IllegalStateException(e);
		}
	}

	public final int size()
	{
		return outSize;
	}
}
