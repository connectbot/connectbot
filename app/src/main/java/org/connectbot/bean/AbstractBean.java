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
