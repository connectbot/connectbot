package org.theb.ssh;

import com.trilead.ssh2.ServerHostKeyVerifier;

public class InteractiveHostKeyVerifier implements ServerHostKeyVerifier {

	public boolean verifyServerHostKey(String hostname, int port,
			String serverHostKeyAlgorithm, byte[] serverHostKey)
			throws Exception {
		// TODO Auto-generated method stub
		return true;
	}

}
