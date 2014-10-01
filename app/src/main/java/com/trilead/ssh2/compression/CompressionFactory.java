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

import java.util.Vector;

/**
 * @author Kenny Root
 *
 */
public class CompressionFactory {
	static class CompressorEntry
	{
		String type;
		String compressorClass;

		public CompressorEntry(String type, String compressorClass)
		{
			this.type = type;
			this.compressorClass = compressorClass;
		}
	}

	static Vector<CompressorEntry> compressors = new Vector<CompressorEntry>();

	static
	{
		/* Higher Priority First */

		compressors.addElement(new CompressorEntry("zlib", "com.trilead.ssh2.compression.Zlib"));
		compressors.addElement(new CompressorEntry("zlib@openssh.com", "com.trilead.ssh2.compression.ZlibOpenSSH"));
		compressors.addElement(new CompressorEntry("none", ""));
	}

	public static String[] getDefaultCompressorList()
	{
		String list[] = new String[compressors.size()];
		for (int i = 0; i < compressors.size(); i++)
		{
			CompressorEntry ce = compressors.elementAt(i);
			list[i] = new String(ce.type);
		}
		return list;
	}

	public static void checkCompressorList(String[] compressorCandidates)
	{
		for (int i = 0; i < compressorCandidates.length; i++)
			getEntry(compressorCandidates[i]);
	}

	public static ICompressor createCompressor(String type)
	{
		try
		{
			CompressorEntry ce = getEntry(type);
			if ("".equals(ce.compressorClass))
				return null;

			Class<?> cc = Class.forName(ce.compressorClass);
			ICompressor cmp = (ICompressor) cc.newInstance();

			return cmp;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Cannot instantiate " + type);
		}
	}

	private static CompressorEntry getEntry(String type)
	{
		for (int i = 0; i < compressors.size(); i++)
		{
			CompressorEntry ce = compressors.elementAt(i);
			if (ce.type.equals(type))
				return ce;
		}
		throw new IllegalArgumentException("Unkown algorithm " + type);
	}
}
