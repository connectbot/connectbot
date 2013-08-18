package com.trilead.ssh2.packets;

/**
 * PacketKexDHInit.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: PacketKexDHInit.java,v 1.1 2007/10/15 12:49:55 cplattne Exp $
 */
public class PacketKexDHInit
{
	byte[] payload;

	byte[] publicKey;

	public PacketKexDHInit(byte[] publicKey)
	{
		this.publicKey = publicKey;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_KEXDH_INIT);
			tw.writeString(publicKey, 0, publicKey.length);
			payload = tw.getBytes();
		}
		return payload;
	}
}
