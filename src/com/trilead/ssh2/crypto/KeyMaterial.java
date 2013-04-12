
package com.trilead.ssh2.crypto;


import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.trilead.ssh2.crypto.digest.HashForSSH2Types;

/**
 * Establishes key material for iv/key/mac (both directions).
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: KeyMaterial.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public class KeyMaterial
{
	public byte[] initial_iv_client_to_server;
	public byte[] initial_iv_server_to_client;
	public byte[] enc_key_client_to_server;
	public byte[] enc_key_server_to_client;
	public byte[] integrity_key_client_to_server;
	public byte[] integrity_key_server_to_client;

	private static byte[] calculateKey(HashForSSH2Types sh, BigInteger K, byte[] H, byte type, byte[] SessionID,
			int keyLength)
	{
		byte[] res = new byte[keyLength];

		int dglen = sh.getDigestLength();
		int numRounds = (keyLength + dglen - 1) / dglen;

		byte[][] tmp = new byte[numRounds][];

		sh.reset();
		sh.updateBigInt(K);
		sh.updateBytes(H);
		sh.updateByte(type);
		sh.updateBytes(SessionID);

		tmp[0] = sh.getDigest();

		int off = 0;
		int produced = Math.min(dglen, keyLength);

		System.arraycopy(tmp[0], 0, res, off, produced);

		keyLength -= produced;
		off += produced;

		for (int i = 1; i < numRounds; i++)
		{
			sh.updateBigInt(K);
			sh.updateBytes(H);

			for (int j = 0; j < i; j++)
				sh.updateBytes(tmp[j]);

			tmp[i] = sh.getDigest();

			produced = Math.min(dglen, keyLength);
			System.arraycopy(tmp[i], 0, res, off, produced);
			keyLength -= produced;
			off += produced;
		}

		return res;
	}

	public static KeyMaterial create(String hashAlgo, byte[] H, BigInteger K, byte[] SessionID, int keyLengthCS,
			int blockSizeCS, int macLengthCS, int keyLengthSC, int blockSizeSC, int macLengthSC)
			throws IllegalArgumentException
	{
		KeyMaterial km = new KeyMaterial();

		HashForSSH2Types sh;
		try {
			sh = new HashForSSH2Types(MessageDigest.getInstance(hashAlgo));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(e);
		}

		km.initial_iv_client_to_server = calculateKey(sh, K, H, (byte) 'A', SessionID, blockSizeCS);

		km.initial_iv_server_to_client = calculateKey(sh, K, H, (byte) 'B', SessionID, blockSizeSC);

		km.enc_key_client_to_server = calculateKey(sh, K, H, (byte) 'C', SessionID, keyLengthCS);

		km.enc_key_server_to_client = calculateKey(sh, K, H, (byte) 'D', SessionID, keyLengthSC);

		km.integrity_key_client_to_server = calculateKey(sh, K, H, (byte) 'E', SessionID, macLengthCS);

		km.integrity_key_server_to_client = calculateKey(sh, K, H, (byte) 'F', SessionID, macLengthSC);

		return km;
	}
}
