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
package org.connectbot.util;

import com.trilead.ssh2.crypto.Base64;

/**
 * @author Kenny Root
 *
 */
public class XmlBuilder {
	private StringBuilder sb;
	
	public XmlBuilder() {
		sb = new StringBuilder();
	}
	
	public XmlBuilder append(String data) {
		sb.append(data);
		
		return this;
	}
	
	public XmlBuilder append(String field, Object data) {
		if (data == null) {
			sb.append(String.format("<%s/>", field));
		} else if (data instanceof String) {
			String input = (String) data;
			boolean binary = false;
			
			for (byte b : input.getBytes()) {
				if (b < 0x20 || b > 0x7e) {
					binary = true;
					break;
				}
			}
			
			sb.append(String.format("<%s>%s</%s>", field,
					binary ? new String(Base64.encode(input.getBytes())) : input, field));
		} else if (data instanceof Integer) {
			sb.append(String.format("<%s>%d</%s>", field, (Integer) data, field));
		} else if (data instanceof Long) {
			sb.append(String.format("<%s>%d</%s>", field, (Long) data, field));
		} else if (data instanceof byte[]) {
			sb.append(String.format("<%s>%s</%s>", field, new String(Base64.encode((byte[]) data)), field));
		} else if (data instanceof Boolean) {
			sb.append(String.format("<%s>%s</%s>", field, (Boolean) data, field));
		}
		
		return this;
	}
	
	public String toString() {
		return sb.toString();
	}
}
