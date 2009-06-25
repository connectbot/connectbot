package com.trilead.ssh2;

import java.util.Map;

/**
 * AuthAgentCallback.
 *
 * @author Kenny Root
 * @version $Id$
 */
public interface AuthAgentCallback {

	/**
	 * @return array of blobs containing the OpenSSH-format encoded public keys
	 */
	Map<String,byte[]> retrieveIdentities();

	/**
	 * @param key A <code>RSAPrivateKey</code> or <code>DSAPrivateKey</code>
	 *            containing a DSA or RSA private key of
	 *            the user in Trilead object format.
	 * @param comment comment associated with this key
	 * @return success or failure
	 */
	boolean addIdentity(Object key, String comment);

	/**
	 * @param publicKey byte blob containing the OpenSSH-format encoded public key
	 * @return success or failure
	 */
	boolean removeIdentity(byte[] publicKey);

	/**
	 * @return success or failure
	 */
	boolean removeAllIdentities();

	/**
	 * @param publicKey byte blob containing the OpenSSH-format encoded public key
	 * @return A <code>RSAPrivateKey</code> or <code>DSAPrivateKey</code>
	 *         containing a DSA or RSA private key of
	 *         the user in Trilead object format.
	 */
	Object getPrivateKey(byte[] publicKey);
}
