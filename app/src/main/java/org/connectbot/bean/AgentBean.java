/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Jonas Dippel, Michael Perk, Marc Totzke
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

import org.connectbot.R;
import org.connectbot.util.AgentDatabase;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public class AgentBean extends AbstractBean {
	public static final String BEAN_NAME = "agent";

	private long id = -1;
	private String keyIdentifier;
	private String keyType;
	private byte[] publicKey;

	private String packageName;

	private String description;

	public AgentBean() {
	}

	public AgentBean(String keyIdentifier, String keyType, String packageName, String description,
			byte[] publicKey) {
		this.keyIdentifier = keyIdentifier;
		this.keyType = keyType;
		this.description = description;
		this.packageName = packageName;
		this.publicKey = publicKey;
	}

	public String getKeyType() {
		return keyType;
	}

	public void setKeyType(String keyType) {
		this.keyType = keyType;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getKeyIdentifier() {
		return keyIdentifier;
	}

	public void setKeyIdentifier(String keyIdentifier) {
		this.keyIdentifier = keyIdentifier;
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

	public byte[] getPublicKey() {
		if (publicKey != null) {
			return publicKey.clone();
		} else {
			return null;
		}
	}

	public void setPublicKey(byte[] encoded) {
		if (encoded == null)
			publicKey = null;
		else
			publicKey = encoded.clone();
	}

	public String getAgentAppName(Context context) {
		PackageManager packageManager = context.getPackageManager();
		ApplicationInfo applicationInfo;
		try {
			applicationInfo = packageManager.getApplicationInfo(getPackageName(), 0);
		} catch (final PackageManager.NameNotFoundException e) {
			return context.getString(R.string.agent_unknown);
		}
		if (applicationInfo != null) {
			return (String) packageManager.getApplicationLabel(applicationInfo);
		} else {
			return context.getString(R.string.agent_unknown);
		}
	}

	@Override
	public ContentValues getValues() {
		ContentValues values = new ContentValues();

		values.put(AgentDatabase.FIELD_AGENT_KEY_IDENTIFIER, keyIdentifier);
		values.put(AgentDatabase.FIELD_AGENT_KEY_TYPE, keyType);
		values.put(AgentDatabase.FIELD_AGENT_DESCRIPTION, description);
		values.put(AgentDatabase.FIELD_AGENT_PACKAGE_NAME, packageName);
		values.put(AgentDatabase.FIELD_AGENT_PUBLIC_KEY, publicKey);

		return values;
	}

	@Override
	public String getBeanName() {
		return BEAN_NAME;
	}
}
