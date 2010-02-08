/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.trilead.ssh2.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Map.Entry;

import com.trilead.ssh2.AuthAgentCallback;
import com.trilead.ssh2.log.Logger;
import com.trilead.ssh2.packets.TypesReader;
import com.trilead.ssh2.packets.TypesWriter;
import com.trilead.ssh2.signature.DSAPrivateKey;
import com.trilead.ssh2.signature.DSASHA1Verify;
import com.trilead.ssh2.signature.DSASignature;
import com.trilead.ssh2.signature.RSAPrivateKey;
import com.trilead.ssh2.signature.RSASHA1Verify;
import com.trilead.ssh2.signature.RSASignature;

/**
 * AuthAgentForwardThread.
 *
 * @author Kenny Root
 * @version $Id$
 */
public class AuthAgentForwardThread extends Thread implements IChannelWorkerThread
{
	private static final byte[] SSH_AGENT_FAILURE = {0, 0, 0, 1, 5}; // 5
	private static final byte[] SSH_AGENT_SUCCESS = {0, 0, 0, 1, 6}; // 6

	private static final int SSH2_AGENTC_REQUEST_IDENTITIES = 11;
	private static final int SSH2_AGENT_IDENTITIES_ANSWER = 12;

	private static final int SSH2_AGENTC_SIGN_REQUEST = 13;
	private static final int SSH2_AGENT_SIGN_RESPONSE = 14;

	private static final int SSH2_AGENTC_ADD_IDENTITY = 17;
	private static final int SSH2_AGENTC_REMOVE_IDENTITY = 18;
	private static final int SSH2_AGENTC_REMOVE_ALL_IDENTITIES = 19;

//	private static final int SSH_AGENTC_ADD_SMARTCARD_KEY = 20;
//	private static final int SSH_AGENTC_REMOVE_SMARTCARD_KEY = 21;

	private static final int SSH_AGENTC_LOCK = 22;
	private static final int SSH_AGENTC_UNLOCK = 23;

	private static final int SSH2_AGENTC_ADD_ID_CONSTRAINED = 25;
//	private static final int SSH_AGENTC_ADD_SMARTCARD_KEY_CONSTRAINED = 26;

	// Constraints for adding keys
	private static final int SSH_AGENT_CONSTRAIN_LIFETIME = 1;
	private static final int SSH_AGENT_CONSTRAIN_CONFIRM = 2;

	// Flags for signature requests
//	private static final int SSH_AGENT_OLD_SIGNATURE = 1;

	private static final Logger log = Logger.getLogger(RemoteAcceptThread.class);

	AuthAgentCallback authAgent;
	OutputStream os;
	InputStream is;
	Channel c;

	byte[] buffer = new byte[Channel.CHANNEL_BUFFER_SIZE];

	public AuthAgentForwardThread(Channel c, AuthAgentCallback authAgent)
	{
		this.c = c;
		this.authAgent = authAgent;

		if (log.isEnabled())
			log.log(20, "AuthAgentForwardThread started");
	}

	@Override
	public void run()
	{
		try
		{
			c.cm.registerThread(this);
		}
		catch (IOException e)
		{
			stopWorking();
			return;
		}

		try
		{
			c.cm.sendOpenConfirmation(c);

			is = c.getStdoutStream();
			os = c.getStdinStream();

			int totalSize = 4;
			int readSoFar = 0;

			while (true) {
				int len;

				try
				{
					len = is.read(buffer, readSoFar, buffer.length - readSoFar);
				}
				catch (IOException e)
				{
					stopWorking();
					return;
				}

				if (len <= 0)
					break;

				readSoFar += len;

				if (readSoFar >= 4) {
					TypesReader tr = new TypesReader(buffer, 0, 4);
					totalSize = tr.readUINT32() + 4;
				}

				if (totalSize == readSoFar) {
					TypesReader tr = new TypesReader(buffer, 4, readSoFar - 4);
					int messageType = tr.readByte();

					switch (messageType) {
					case SSH2_AGENTC_REQUEST_IDENTITIES:
						sendIdentities();
						break;
					case SSH2_AGENTC_ADD_IDENTITY:
						addIdentity(tr, false);
						break;
					case SSH2_AGENTC_ADD_ID_CONSTRAINED:
						addIdentity(tr, true);
						break;
					case SSH2_AGENTC_REMOVE_IDENTITY:
						removeIdentity(tr);
						break;
					case SSH2_AGENTC_REMOVE_ALL_IDENTITIES:
						removeAllIdentities(tr);
						break;
					case SSH2_AGENTC_SIGN_REQUEST:
						processSignRequest(tr);
						break;
					case SSH_AGENTC_LOCK:
						processLockRequest(tr);
						break;
					case SSH_AGENTC_UNLOCK:
						processUnlockRequest(tr);
						break;
					default:
						os.write(SSH_AGENT_FAILURE);
						break;
					}

					readSoFar = 0;
				}
			}

			c.cm.closeChannel(c, "EOF on both streams reached.", true);
		}
		catch (IOException e)
		{
			log.log(50, "IOException in agent forwarder: " + e.getMessage());

			try
			{
				is.close();
			}
			catch (IOException e1)
			{
			}

			try
			{
				os.close();
			}
			catch (IOException e2)
			{
			}

			try
			{
				c.cm.closeChannel(c, "IOException in agent forwarder (" + e.getMessage() + ")", true);
			}
			catch (IOException e3)
			{
			}
		}
	}

	public void stopWorking() {
		try
		{
			/* This will lead to an IOException in the is.read() call */
			is.close();
		}
		catch (IOException e)
		{
		}
	}

	/**
	 * @return whether the agent is locked
	 */
	private boolean failWhenLocked() throws IOException
	{
		if (authAgent.isAgentLocked()) {
			os.write(SSH_AGENT_FAILURE);
			return true;
		} else
			return false;
	}

	private void sendIdentities() throws IOException
	{
		Map<String,byte[]> keys = null;

		TypesWriter tw = new TypesWriter();
		tw.writeByte(SSH2_AGENT_IDENTITIES_ANSWER);
		int numKeys = 0;

		if (!authAgent.isAgentLocked())
			keys = authAgent.retrieveIdentities();

		if (keys != null)
			numKeys = keys.size();

		tw.writeUINT32(numKeys);

		if (keys != null) {
			for (Entry<String,byte[]> entry : keys.entrySet()) {
				byte[] keyBytes = entry.getValue();
				tw.writeString(keyBytes, 0, keyBytes.length);
				tw.writeString(entry.getKey());
			}
		}

		sendPacket(tw.getBytes());
	}

	/**
	 * @param tr
	 */
	private void addIdentity(TypesReader tr, boolean checkConstraints) {
		try
		{
			if (failWhenLocked())
				return;

			String type = tr.readString();

			Object key;
			String comment;

			if (type.equals("ssh-rsa")) {
				BigInteger n = tr.readMPINT();
				BigInteger e = tr.readMPINT();
				BigInteger d = tr.readMPINT();
				tr.readMPINT(); // iqmp
				tr.readMPINT(); // p
				tr.readMPINT(); // q
				comment = tr.readString();

				key = new RSAPrivateKey(d, e, n);
			} else if (type.equals("ssh-dss")) {
				BigInteger p = tr.readMPINT();
				BigInteger q = tr.readMPINT();
				BigInteger g = tr.readMPINT();
				BigInteger y = tr.readMPINT();
				BigInteger x = tr.readMPINT();
				comment = tr.readString();

				key = new DSAPrivateKey(p, q, g, y, x);
			} else {
				os.write(SSH_AGENT_FAILURE);
				return;
			}

			boolean confirmUse = false;
			int lifetime = 0;

			if (checkConstraints) {
				while (tr.remain() > 0) {
					int constraint = tr.readByte();
					if (constraint == SSH_AGENT_CONSTRAIN_CONFIRM)
						confirmUse = true;
					else if (constraint == SSH_AGENT_CONSTRAIN_LIFETIME)
						lifetime = tr.readUINT32();
					else {
						// Unknown constraint. Bail.
						os.write(SSH_AGENT_FAILURE);
						return;
					}
				}
			}

			if (authAgent.addIdentity(key, comment, confirmUse, lifetime))
				os.write(SSH_AGENT_SUCCESS);
			else
				os.write(SSH_AGENT_FAILURE);
		}
		catch (IOException e)
		{
			try
			{
				os.write(SSH_AGENT_FAILURE);
			}
			catch (IOException e1)
			{
			}
		}
	}

	/**
	 * @param tr
	 */
	private void removeIdentity(TypesReader tr) {
		try
		{
			if (failWhenLocked())
				return;

			byte[] publicKey = tr.readByteString();
			if (authAgent.removeIdentity(publicKey))
				os.write(SSH_AGENT_SUCCESS);
			else
				os.write(SSH_AGENT_FAILURE);
		}
		catch (IOException e)
		{
			try
			{
				os.write(SSH_AGENT_FAILURE);
			}
			catch (IOException e1)
			{
			}
		}
	}

	/**
	 * @param tr
	 */
	private void removeAllIdentities(TypesReader tr) {
		try
		{
			if (failWhenLocked())
				return;

			if (authAgent.removeAllIdentities())
				os.write(SSH_AGENT_SUCCESS);
			else
				os.write(SSH_AGENT_FAILURE);
		}
		catch (IOException e)
		{
			try
			{
				os.write(SSH_AGENT_FAILURE);
			}
			catch (IOException e1)
			{
			}
		}
	}

	private void processSignRequest(TypesReader tr)
	{
		try
		{
			if (failWhenLocked())
				return;

			byte[] publicKey = tr.readByteString();
			byte[] challenge = tr.readByteString();

			int flags = tr.readUINT32();

			if (flags != 0) {
				// We don't understand any flags; abort!
				os.write(SSH_AGENT_FAILURE);
				return;
			}

			Object trileadKey = authAgent.getPrivateKey(publicKey);

			if (trileadKey == null) {
				os.write(SSH_AGENT_FAILURE);
				return;
			}

			byte[] response;

			if (trileadKey instanceof RSAPrivateKey) {
				RSASignature signature = RSASHA1Verify.generateSignature(challenge,
						(RSAPrivateKey) trileadKey);
				response = RSASHA1Verify.encodeSSHRSASignature(signature);
			} else if (trileadKey instanceof DSAPrivateKey) {
				DSASignature signature = DSASHA1Verify.generateSignature(challenge,
						(DSAPrivateKey) trileadKey, new SecureRandom());
				response = DSASHA1Verify.encodeSSHDSASignature(signature);
			} else {
				os.write(SSH_AGENT_FAILURE);
				return;
			}

			TypesWriter tw = new TypesWriter();
			tw.writeByte(SSH2_AGENT_SIGN_RESPONSE);
			tw.writeString(response, 0, response.length);

			sendPacket(tw.getBytes());
		}
		catch (IOException e)
		{
			try
			{
				os.write(SSH_AGENT_FAILURE);
			}
			catch (IOException e1)
			{
			}
		}
	}

	/**
	 * @param tr
	 */
	private void processLockRequest(TypesReader tr) {
		try
		{
			if (failWhenLocked())
				return;

			String lockPassphrase = tr.readString();
			if (!authAgent.setAgentLock(lockPassphrase)) {
				os.write(SSH_AGENT_FAILURE);
				return;
			} else
				os.write(SSH_AGENT_SUCCESS);
		}
		catch (IOException e)
		{
			try
			{
				os.write(SSH_AGENT_FAILURE);
			}
			catch (IOException e1)
			{
			}
		}
	}

	/**
	 * @param tr
	 */
	private void processUnlockRequest(TypesReader tr)
	{
		try
		{
			String unlockPassphrase = tr.readString();

			if (authAgent.requestAgentUnlock(unlockPassphrase))
				os.write(SSH_AGENT_SUCCESS);
			else
				os.write(SSH_AGENT_FAILURE);
		}
		catch (IOException e)
		{
			try
			{
				os.write(SSH_AGENT_FAILURE);
			}
			catch (IOException e1)
			{
			}
		}
	}

	/**
	 * @param tw
	 * @throws IOException
	 */
	private void sendPacket(byte[] message) throws IOException
	{
		TypesWriter packet = new TypesWriter();
		packet.writeUINT32(message.length);
		packet.writeBytes(message);
		os.write(packet.getBytes());
	}
}
