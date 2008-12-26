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
package org.connectbot.bean;

import java.util.Map.Entry;

import org.connectbot.util.XmlBuilder;

import android.content.ContentValues;

/**
 * @author Kenny Root
 *
 */
abstract class AbstractBean {
	public abstract ContentValues getValues();
	public abstract String getBeanName();
	
	public String toXML() {
		XmlBuilder xml = new XmlBuilder();
		
		xml.append(String.format("<%s>", getBeanName()));

		ContentValues values = getValues();
		for (Entry<String, Object> entry : values.valueSet()) {
			Object value = entry.getValue();
			if (value != null)
				xml.append(entry.getKey(), value);
		}
		xml.append(String.format("</%s>", getBeanName()));
		
		return xml.toString();
	}
}
