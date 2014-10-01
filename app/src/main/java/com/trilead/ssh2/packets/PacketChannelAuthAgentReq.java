package com.trilead.ssh2.packets;

/**
 * PacketGlobalAuthAgent.
 *
 * @author Kenny Root, kenny@the-b.org
 * @version $Id$
 */
public class PacketChannelAuthAgentReq
{
	byte[] payload;

	public int recipientChannelID;

	public PacketChannelAuthAgentReq(int recipientChannelID)
	{
		this.recipientChannelID = recipientChannelID;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_CHANNEL_REQUEST);
			tw.writeUINT32(recipientChannelID);
			tw.writeString("auth-agent-req@openssh.com");
			tw.writeBoolean(true); // want reply
			payload = tw.getBytes();
		}
		return payload;
	}
}
