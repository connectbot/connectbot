/**
 *
 */
package com.trilead.ssh2.crypto.dh;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;

/**
 * @author kenny
 *
 */
public class DhExchange extends GenericDhExchange {

	/* Given by the standard */

	private static final BigInteger P1 = new BigInteger(
			  "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
			+ "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
			+ "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
			+ "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
			+ "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381"
			+ "FFFFFFFFFFFFFFFF", 16);

	private static final BigInteger P14 = new BigInteger(
			  "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1"
			+ "29024E088A67CC74020BBEA63B139B22514A08798E3404DD"
			+ "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245"
			+ "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED"
			+ "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D"
			+ "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F"
			+ "83655D23DCA3AD961C62F356208552BB9ED529077096966D"
			+ "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B"
			+ "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9"
			+ "DE2BCBF6955817183995497CEA956AE515D2261898FA0510"
			+ "15728E5A8AACAA68FFFFFFFFFFFFFFFF", 16);

	private static final BigInteger G = BigInteger.valueOf(2);

	/* Client public and private */

	private DHPrivateKey clientPrivate;
	private DHPublicKey clientPublic;

	/* Server public */

	private DHPublicKey serverPublic;

	@Override
	public void init(String name) throws IOException {
		final DHParameterSpec spec;
		if ("diffie-hellman-group1-sha1".equals(name)) {
			spec = new DHParameterSpec(P1, G);
		} else if ("diffie-hellman-group14-sha1".equals(name)) {
			spec = new DHParameterSpec(P14, G);
		} else {
			throw new IllegalArgumentException("Unknown DH group " + name);
		}

		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
			kpg.initialize(spec);
			KeyPair pair = kpg.generateKeyPair();
			clientPrivate = (DHPrivateKey) pair.getPrivate();
			clientPublic = (DHPublicKey) pair.getPublic();
		} catch (NoSuchAlgorithmException e) {
			throw (IOException) new IOException("No DH keypair generator").initCause(e);
		} catch (InvalidAlgorithmParameterException e) {
			throw (IOException) new IOException("Invalid DH parameters").initCause(e);
		}
	}

	@Override
	public byte[] getE() {
		if (clientPublic == null)
			throw new IllegalStateException("DhExchange not initialized!");

		return clientPublic.getY().toByteArray();
	}

	@Override
	protected byte[] getServerE() {
		if (serverPublic == null)
			throw new IllegalStateException("DhExchange not initialized!");

		return serverPublic.getY().toByteArray();
	}

	@Override
	public void setF(byte[] f) throws IOException {
		if (clientPublic == null)
			throw new IllegalStateException("DhExchange not initialized!");

		final KeyAgreement ka;
		try {
			KeyFactory kf = KeyFactory.getInstance("DH");
			DHParameterSpec params = clientPublic.getParams();
			this.serverPublic = (DHPublicKey) kf.generatePublic(new DHPublicKeySpec(
					new BigInteger(f), params.getP(), params.getG()));

			ka = KeyAgreement.getInstance("DH");
			ka.init(clientPrivate);
			ka.doPhase(serverPublic, true);
		} catch (NoSuchAlgorithmException e) {
			throw (IOException) new IOException("No DH key agreement method").initCause(e);
		} catch (InvalidKeyException e) {
			throw (IOException) new IOException("Invalid DH key").initCause(e);
		} catch (InvalidKeySpecException e) {
			throw (IOException) new IOException("Invalid DH key").initCause(e);
		}

		sharedSecret = new BigInteger(ka.generateSecret());
	}

	@Override
	public String getHashAlgo() {
		return "SHA1";
	}
}
