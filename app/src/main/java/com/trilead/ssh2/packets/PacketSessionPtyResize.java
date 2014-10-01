package com.trilead.ssh2.packets;

public class PacketSessionPtyResize {
	byte[] payload;

	public int recipientChannelID;
	public int width;
	public int height;
	public int pixelWidth;
	public int pixelHeight;

	public PacketSessionPtyResize(int recipientChannelID, int width, int height, int pixelWidth, int pixelHeight) {
		this.recipientChannelID = recipientChannelID;
		this.width = width;
		this.height = height;
		this.pixelWidth = pixelWidth;
		this.pixelHeight = pixelHeight;
	}

	public byte[] getPayload()
	{
		if (payload == null)
		{
			TypesWriter tw = new TypesWriter();
			tw.writeByte(Packets.SSH_MSG_CHANNEL_REQUEST);
			tw.writeUINT32(recipientChannelID);
			tw.writeString("window-change");
			tw.writeBoolean(false);
			tw.writeUINT32(width);
			tw.writeUINT32(height);
			tw.writeUINT32(pixelWidth);
			tw.writeUINT32(pixelHeight);

			payload = tw.getBytes();
		}
		return payload;
	}
}


