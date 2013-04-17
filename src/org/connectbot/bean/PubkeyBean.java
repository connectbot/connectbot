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

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import android.content.ContentValues;

/**
 * @author Kenny Root
 *
 */
public class PubkeyBean extends AbstractBean {
	public static final String BEAN_NAME = "pubkey";

	private static final String KEY_TYPE_RSA = "RSA";

	private static final String KEY_TYPE_DSA = "DSA";

	private static final String KEY_TYPE_EC = "EC";

	/* Database fields */
	private long id;
	private String nickname;
	private String type;
	private byte[] privateKey;
	private byte[] publicKey;
	private boolean encrypted = false;
	private boolean startup = false;
	private boolean confirmUse = false;
	private int lifetime = 0;

	/* Transient values */
	private transient boolean unlocked = false;
	private transient Object unlockedPrivate = null;
	private transient String description;

	@Override
	public String getBeanName() {
		return BEAN_NAME;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getId() {
		return id;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getNickname() {
		return nickname;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	public void setPrivateKey(byte[] privateKey) {
		if (privateKey == null)
			this.privateKey = null;
		else
			this.privateKey = privateKey.clone();
	}

	public byte[] getPrivateKey() {
		if (privateKey == null)
			return null;
		else
			return privateKey.clone();
	}

	public void setPublicKey(byte[] encoded) {
		if (encoded == null)
			publicKey = null;
		else
			publicKey = encoded.clone();
	}

	public byte[] getPublicKey() {
		if (publicKey == null)
			return null;
		else
			return publicKey.clone();
	}

	public void setEncrypted(boolean encrypted) {
		this.encrypted = encrypted;
	}

	public boolean isEncrypted() {
		return encrypted;
	}

	public void setStartup(boolean startup) {
		this.startup = startup;
	}

	public boolean isStartup() {
		return startup;
	}

	public void setConfirmUse(boolean confirmUse) {
		this.confirmUse = confirmUse;
	}

	public boolean isConfirmUse() {
		return confirmUse;
	}

	public void setLifetime(int lifetime) {
		this.lifetime = lifetime;
	}

	public int getLifetime() {
		return lifetime;
	}

	public void setUnlocked(boolean unlocked) {
		this.unlocked = unlocked;
	}

	public boolean isUnlocked() {
		return unlocked;
	}

	public void setUnlockedPrivate(Object unlockedPrivate) {
		this.unlockedPrivate = unlockedPrivate;
	}

	public Object getUnlockedPrivate() {
		return unlockedPrivate;
	}

	public String getDescription() {
		if (description == null) {
			final StringBuilder sb = new StringBuilder();
			try {
				final PublicKey pubKey = PubkeyUtils.decodePublic(privateKey, type);
				if (PubkeyDatabase.KEY_TYPE_RSA.equals(type)) {
					int bits = ((RSAPublicKey) pubKey).getModulus().bitLength();
					sb.append("RSA ");
					sb.append(bits);
					sb.append("-bit");
				} else if (PubkeyDatabase.KEY_TYPE_DSA.equals(type)) {
					sb.append("DSA 1024-bit");
				} else if (PubkeyDatabase.KEY_TYPE_EC.equals(type)) {
					int bits = ((ECPublicKey) pubKey).getParams().getCurve().getField()
							.getFieldSize();
					sb.append("EC ");
					sb.append(bits);
					sb.append("-bit");
				} else {
					sb.append("Unknown Key Type");
				}
			} catch (NoSuchAlgorithmException e) {
				sb.append("Unknown Key Type");
			} catch (InvalidKeySpecException e) {
				sb.append("Unknown Key Type");
			}

			if (encrypted)
				sb.append(" (encrypted)");

			description = sb.toString();
		}
		return description;
	}

	/* (non-Javadoc)
	 * @see org.connectbot.bean.AbstractBean#getValues()
	 */
	@Override
	public ContentValues getValues() {
		ContentValues values = new ContentValues();

		values.put(PubkeyDatabase.FIELD_PUBKEY_NICKNAME, nickname);
		values.put(PubkeyDatabase.FIELD_PUBKEY_TYPE, type);
		values.put(PubkeyDatabase.FIELD_PUBKEY_PRIVATE, privateKey);
		values.put(PubkeyDatabase.FIELD_PUBKEY_PUBLIC, publicKey);
		values.put(PubkeyDatabase.FIELD_PUBKEY_ENCRYPTED, encrypted ? 1 : 0);
		values.put(PubkeyDatabase.FIELD_PUBKEY_STARTUP, startup ? 1 : 0);
		values.put(PubkeyDatabase.FIELD_PUBKEY_CONFIRMUSE, confirmUse ? 1 : 0);
		values.put(PubkeyDatabase.FIELD_PUBKEY_LIFETIME, lifetime);

		return values;
	}

	public boolean changePassword(String oldPassword, String newPassword) throws Exception {
		PrivateKey priv;

		try {
			priv = PubkeyUtils.decodePrivate(getPrivateKey(), getType(), oldPassword);
		} catch (Exception e) {
			return false;
		}

		setPrivateKey(PubkeyUtils.getEncodedPrivate(priv, newPassword));
		setEncrypted(newPassword.length() > 0);

		return true;
	}
}
