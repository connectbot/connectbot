package com.trilead.ssh2.channel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Map.Entry;

import android.util.Log;

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
	public static final int SSH_AGENT_CONSTRAIN_LIFETIME = 1;
	public static final int SSH_AGENT_CONSTRAIN_CONFIRM = 2;

	private static final byte[] SSH_AGENT_FAILURE = {0, 0, 0, 1, 5};
	private static final byte[] SSH_AGENT_SUCCESS = {0, 0, 0, 1, 6};
//	public static final int SSH_AGENT_FAILURE = 5;
//	public static final int SSH_AGENT_SUCCESS = 6;

	public static final int SSH2_AGENTC_REQUEST_IDENTITIES = 11;
	public static final int SSH2_AGENT_IDENTITIES_ANSWER = 12;

	public static final int SSH2_AGENTC_SIGN_REQUEST = 13;
	public static final int SSH2_AGENT_SIGN_RESPONSE = 14;

	public static final int SSH2_AGENTC_ADD_IDENTITY = 17;
	public static final int SSH2_AGENTC_REMOVE_IDENTITY = 18;
	public static final int SSH2_AGENTC_REMOVE_ALL_IDENTITIES = 19;

	public static final int SSH_AGENTC_ADD_SMARTCARD_KEY = 20;
	public static final int SSH_AGENTC_REMOVE_SMARTCARD_KEY = 21;

	public static final int SSH_AGENTC_LOCK = 22;
	public static final int SSH_AGENTC_UNLOCK = 23;

	public static final int SSH2_AGENTC_ADD_ID_CONSTRAINED = 25;
	public static final int SSH_AGENTC_ADD_SMARTCARD_KEY_CONSTRAINED = 26;

	public static final int SSH_AGENT_OLD_SIGNATURE = 1;

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
		} catch (IOException e) {
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
				catch (IOException e) {
					stopWorking();
					return;
				}

				if (len <= 0)
					break;

				readSoFar += len;

				Log.d("AuthAgent", "read " + readSoFar + " bytes");

				if (readSoFar >= 4) {
					TypesReader tr = new TypesReader(buffer, 0, 4);
					totalSize = tr.readUINT32() + 4;
					Log.d("AuthAgent", "message is " + totalSize + " bytes");
				}

				if (totalSize == readSoFar) {
//					debugPacket(buffer, readSoFar);
					TypesReader tr = new TypesReader(buffer, 4, readSoFar - 4);
					int messageType = tr.readByte();

					Log.d("AuthAgent", "Got a message type " + messageType);
					switch (messageType) {
					case SSH2_AGENTC_REQUEST_IDENTITIES:
						sendIdentities();
						break;
					case SSH2_AGENTC_ADD_IDENTITY:
						addIdentity(tr);
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
					default:
						os.write(SSH_AGENT_FAILURE);
						break;
					}

					readSoFar = 0;
				}
				// TODO write actual agent forwarding stuff!
//				log.log(0, "Received an agent request; sending failure");
//				os.write(AGENT_FAILURE);
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

	private void sendIdentities() throws IOException
	{
		Map<String,byte[]> keys = authAgent.retrieveIdentities();

		TypesWriter tw = new TypesWriter();
		tw.writeByte(SSH2_AGENT_IDENTITIES_ANSWER);
		int numKeys = 0;
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

		Log.d("AuthAgent", "Sending " + numKeys + " to server");
		sendPacket(tw.getBytes());
	}

	/**
	 * @param tr
	 */
	private void addIdentity(TypesReader tr) {
		try
		{
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

			if (authAgent.addIdentity(key, comment))
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
			byte[] publicKey = tr.readByteString();
			byte[] challenge = tr.readByteString();

			int flags = tr.readUINT32();

			Object trileadKey = authAgent.getPrivateKey(publicKey);

			if (trileadKey == null) {
				Log.d("AuthAgent", "Key not known to us; failing signature. Public key:");
//				debugPacket(publicKey);
				os.write(SSH_AGENT_FAILURE);
				return;
			}

			byte[] response;

			if (trileadKey instanceof RSAPrivateKey) {
				RSASignature signature = RSASHA1Verify.generateSignature(challenge,
						(RSAPrivateKey) trileadKey);
				response = RSASHA1Verify.encodeSSHRSASignature(signature);
			} else if (trileadKey instanceof DSAPrivateKey) {
				if ((flags & SSH_AGENT_OLD_SIGNATURE) != 0)
					Log.d("AuthAgent", "Want old signature type");
				DSASignature signature = DSASHA1Verify.generateSignature(challenge,
						(DSAPrivateKey) trileadKey, new SecureRandom());
				response = DSASHA1Verify.encodeSSHDSASignature(signature);
			} else {
				Log.d("AuthAgent", "Unknown key type; failing signature request");
				os.write(SSH_AGENT_FAILURE);
				return;
			}

			TypesWriter tw = new TypesWriter();
			tw.writeByte(SSH2_AGENT_SIGN_RESPONSE);
			tw.writeString(response, 0, response.length);

			sendPacket(tw.getBytes());
		}
		catch (IOException e) {
			try
			{
				os.write(SSH_AGENT_FAILURE);
			}
			catch (IOException e1) {
			}
		}
	}

	/**
	 * @param tw
	 * @throws IOException
	 */
	private void sendPacket(byte[] message) throws IOException {
		TypesWriter packet = new TypesWriter();
		packet.writeUINT32(message.length);
		packet.writeBytes(message);
//		debugPacket(packet.getBytes());
		os.write(packet.getBytes());
	}

//	private static final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
//
//	private void debugPacket(byte[] packet) {
//		debugPacket(packet, packet.length);
//	}
//
//	private void debugPacket(byte[] packet, int len) {
//		StringBuilder sb = new StringBuilder();
//		sb.append("Packet dump:");
//
//		for (int i = 0; i < len; i++) {
//			if (packet[i] < 32 || packet[i] > 0x7e) {
//				sb.append(" 0x");
//				sb.append(hexDigits[(packet[i] >> 4) & 0xF]);
//				sb.append(hexDigits[packet[i] & 0xF]);
//			} else {
//				sb.append("    ");
//				sb.append((char)packet[i]);
//			}
//		}
//
//		Log.d("AuthAgent", sb.toString());
//	}
}
