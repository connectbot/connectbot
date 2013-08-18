package com.trilead.ssh2.crypto;

import java.io.IOException;

import java.math.BigInteger;

/**
 * SimpleDERReader.
 * 
 * @author Christian Plattner, plattner@trilead.com
 * @version $Id: SimpleDERReader.java,v 1.1 2007/10/15 12:49:56 cplattne Exp $
 */
public class SimpleDERReader
{
	private static final int CONSTRUCTED = 0x20;

	byte[] buffer;
	int pos;
	int count;

	public SimpleDERReader(byte[] b)
	{
		resetInput(b);
	}
	
	public SimpleDERReader(byte[] b, int off, int len)
	{
		resetInput(b, off, len);
	}

	public void resetInput(byte[] b)
	{
		resetInput(b, 0, b.length);
	}
	
	public void resetInput(byte[] b, int off, int len)
	{
		buffer = b;
		pos = off;
		count = len;
	}

	private byte readByte() throws IOException
	{
		if (count <= 0)
			throw new IOException("DER byte array: out of data");
		count--;
		return buffer[pos++];
	}

	private byte[] readBytes(int len) throws IOException
	{
		if (len > count)
			throw new IOException("DER byte array: out of data");

		byte[] b = new byte[len];

		System.arraycopy(buffer, pos, b, 0, len);

		pos += len;
		count -= len;

		return b;
	}

	public int available()
	{
		return count;
	}

	private int readLength() throws IOException
	{
		int len = readByte() & 0xff;

		if ((len & 0x80) == 0)
			return len;

		int remain = len & 0x7F;

		if (remain == 0)
			return -1;

		len = 0;
		
		while (remain > 0)
		{
			len = len << 8;
			len = len | (readByte() & 0xff);
			remain--;
		}

		return len;
	}

	public int ignoreNextObject() throws IOException
	{
		int type = readByte() & 0xff;

		int len = readLength();

		if ((len < 0) || len > available())
			throw new IOException("Illegal len in DER object (" + len  + ")");

		readBytes(len);
		
		return type;
	}
	
	public BigInteger readInt() throws IOException
	{
		int type = readByte() & 0xff;
		
		if (type != 0x02)
			throw new IOException("Expected DER Integer, but found type " + type);
		
		int len = readLength();

		if ((len < 0) || len > available())
			throw new IOException("Illegal len in DER object (" + len  + ")");

		byte[] b = readBytes(len);
		
		BigInteger bi = new BigInteger(b);
		
		return bi;
	}

	public int readConstructedType() throws IOException {
		int type = readByte() & 0xff;

		if ((type & CONSTRUCTED) != CONSTRUCTED)
			throw new IOException("Expected constructed type, but was " + type);

		return type & 0x1f;
	}

	public SimpleDERReader readConstructed() throws IOException
	{
		int len = readLength();

		if ((len < 0) || len > available())
			throw new IOException("Illegal len in DER object (" + len  + ")");

		SimpleDERReader cr = new SimpleDERReader(buffer, pos, len);

		pos += len;
		count -= len;

		return cr;
	}

	public byte[] readSequenceAsByteArray() throws IOException
	{
		int type = readByte() & 0xff;
		
		if (type != 0x30)
			throw new IOException("Expected DER Sequence, but found type " + type);
		
		int len = readLength();

		if ((len < 0) || len > available())
			throw new IOException("Illegal len in DER object (" + len  + ")");

		byte[] b = readBytes(len);

		return b;
	}

	public String readOid() throws IOException
	{
		int type = readByte() & 0xff;

		if (type != 0x06)
		   throw new IOException("Expected DER OID, but found type " + type);

		int len = readLength();

		if ((len < 1) || len > available())
			throw new IOException("Illegal len in DER object (" + len  + ")");

		byte[] b = readBytes(len);

		long value = 0;

		StringBuilder sb = new StringBuilder(64);
		switch(b[0] / 40) {
		case 0:
			sb.append('0');
			break;
		case 1:
			sb.append('1');
			b[0] -= 40;
			break;
		default:
			sb.append('2');
			b[0] -= 80;
			break;
		}

		for (int i = 0; i < len; i++) {
			value = (value << 7) + (b[i] & 0x7F);
			if ((b[i] & 0x80) == 0) {
				sb.append('.');
				sb.append(value);
				value = 0;
			}
		}

		return sb.toString();
	}

	public byte[] readOctetString() throws IOException
	{
		int type = readByte() & 0xff;

		if (type != 0x04 && type != 0x03)
			throw new IOException("Expected DER Octetstring, but found type " + type);
		
		int len = readLength();

		if ((len < 0) || len > available())
			throw new IOException("Illegal len in DER object (" + len  + ")");

		byte[] b = readBytes(len);

		return b;
	}

}
