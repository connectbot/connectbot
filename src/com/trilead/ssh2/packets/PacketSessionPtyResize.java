package com.trilead.ssh2.packets;

public class PacketSessionPtyResize {
	byte[] payload;

	public int recipientChannelID;
	public boolean wantReply;
	public String term;
	public int width;
	public int height;

	public PacketSessionPtyResize(int recipientChannelID, boolean wantReply, int width, int height) {
		this.recipientChannelID = recipientChannelID;
		this.wantReply = wantReply;
		this.term = term;
		this.width = width;
		this.height = height;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_CHANNEL_REQUEST);
			tw.writeUINT32(recipientChannelID);
			tw.writeString("window-change");
			tw.writeBoolean(wantReply);
			tw.writeUINT32(width);
			tw.writeUINT32(height);
			tw.writeUINT32(0);
			tw.writeUINT32(0);

			payload = tw.getBytes();
		}
		return payload;
	}
}


