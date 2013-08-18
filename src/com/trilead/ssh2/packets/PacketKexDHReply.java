package com.trilead.ssh2.packets;

import java.io.IOException;

/**
 * PacketKexDHReply.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketKexDHReply.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketKexDHReply
{
	byte[] payload;

	byte[] hostKey;
	byte[] publicKey;
	byte[] signature;
	
	public PacketKexDHReply(byte payload[], int off, int len) throws IOException
	{
		this.payload = new byte[len];
		System.arraycopy(payload, off, this.payload, 0, len);

		TypesReader tr = new TypesReader(payload, off, len);

		int packet_type = tr.readByte();

		if (packet_type != Packets.SSH_MSG_KEXDH_REPLY)
			throw new IOException("This is not a SSH_MSG_KEXDH_REPLY! ("
					+ packet_type + ")");

		hostKey = tr.readByteString();
		publicKey = tr.readByteString();
		signature = tr.readByteString();

		if (tr.remain() != 0) throw new IOException("PADDING IN SSH_MSG_KEXDH_REPLY!");
	}

	public byte[] getF()
	{
		return publicKey;
	}
	
	public byte[] getHostKey()
	{
		return hostKey;
	}

	public byte[] getSignature()
	{
		return signature;
	}
}
