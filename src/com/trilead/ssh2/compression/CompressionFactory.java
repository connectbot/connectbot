/*
	ConnectBot: simple, powerful, open-source SSH client for Android
	Copyright (C) 2007-2008 Kenny Root, Jeffrey Sharkey
	
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
		compressors.addElement(new CompressorEntry("zlib@openssh.com", "com.trilead.ssh2.compression.Zlib"));
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
