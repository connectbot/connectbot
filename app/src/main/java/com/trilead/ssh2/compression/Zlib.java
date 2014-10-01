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

package com.trilead.ssh2.compression;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;

/**
 * @author Kenny Root
 *
 */
public class Zlib implements ICompressor {
	static private final int DEFAULT_BUF_SIZE = 4096;
	static private final int LEVEL = 5;

	private ZStream deflate;
	private byte[] deflate_tmpbuf;

	private ZStream inflate;
	private byte[] inflate_tmpbuf;
	private byte[] inflated_buf;

	public Zlib() {
		deflate = new ZStream();
		inflate = new ZStream();

		deflate.deflateInit(LEVEL);
		inflate.inflateInit();

		deflate_tmpbuf = new byte[DEFAULT_BUF_SIZE];
		inflate_tmpbuf = new byte[DEFAULT_BUF_SIZE];
		inflated_buf = new byte[DEFAULT_BUF_SIZE];
	}

	public boolean canCompressPreauth() {
		return true;
	}

	public int getBufferSize() {
		return DEFAULT_BUF_SIZE;
	}

	public int compress(byte[] buf, int start, int len, byte[] output) {
		deflate.next_in = buf;
		deflate.next_in_index = start;
		deflate.avail_in = len - start;

		if ((buf.length + 1024) > deflate_tmpbuf.length) {
			deflate_tmpbuf = new byte[buf.length + 1024];
		}

		deflate.next_out = deflate_tmpbuf;
		deflate.next_out_index = 0;
		deflate.avail_out = output.length;

		if (deflate.deflate(JZlib.Z_PARTIAL_FLUSH) != JZlib.Z_OK) {
			System.err.println("compress: compression failure");
		}

		if (deflate.avail_in > 0) {
			System.err.println("compress: deflated data too large");
		}

		int outputlen = output.length - deflate.avail_out;

		System.arraycopy(deflate_tmpbuf, 0, output, 0, outputlen);

		return outputlen;
	}

	public byte[] uncompress(byte[] buffer, int start, int[] length) {
		int inflated_end = 0;

		inflate.next_in = buffer;
		inflate.next_in_index = start;
		inflate.avail_in = length[0];

		while (true) {
			inflate.next_out = inflate_tmpbuf;
			inflate.next_out_index = 0;
			inflate.avail_out = DEFAULT_BUF_SIZE;
			int status = inflate.inflate(JZlib.Z_PARTIAL_FLUSH);
			switch (status) {
			case JZlib.Z_OK:
				if (inflated_buf.length < inflated_end + DEFAULT_BUF_SIZE
						- inflate.avail_out) {
					byte[] foo = new byte[inflated_end + DEFAULT_BUF_SIZE
							- inflate.avail_out];
					System.arraycopy(inflated_buf, 0, foo, 0, inflated_end);
					inflated_buf = foo;
				}
				System.arraycopy(inflate_tmpbuf, 0, inflated_buf, inflated_end,
						DEFAULT_BUF_SIZE - inflate.avail_out);
				inflated_end += (DEFAULT_BUF_SIZE - inflate.avail_out);
				length[0] = inflated_end;
				break;
			case JZlib.Z_BUF_ERROR:
				if (inflated_end > buffer.length - start) {
					byte[] foo = new byte[inflated_end + start];
					System.arraycopy(buffer, 0, foo, 0, start);
					System.arraycopy(inflated_buf, 0, foo, start, inflated_end);
					buffer = foo;
				} else {
					System.arraycopy(inflated_buf, 0, buffer, start,
							inflated_end);
				}
				length[0] = inflated_end;
				return buffer;
			default:
				System.err.println("uncompress: inflate returnd " + status);
				return null;
			}
		}
	}
}
