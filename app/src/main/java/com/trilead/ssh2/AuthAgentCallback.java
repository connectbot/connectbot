package com.trilead.ssh2;

import java.security.KeyPair;
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
	 * @param confirmUse whether to prompt before using this key
	 * @param lifetime lifetime in seconds for key to be remembered
	 * @return success or failure
	 */
	boolean addIdentity(KeyPair pair, String comment, boolean confirmUse, int lifetime);

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
	KeyPair getKeyPair(byte[] publicKey);

	/**
	 * @return
	 */
	boolean isAgentLocked();

	/**
	 * @param lockPassphrase
	 */
	boolean setAgentLock(String lockPassphrase);

	/**
	 * @param unlockPassphrase
	 * @return
	 */
	boolean requestAgentUnlock(String unlockPassphrase);
}
