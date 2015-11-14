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

import org.connectbot.transport.Local;
import org.connectbot.transport.SSH;
import org.connectbot.transport.Telnet;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;

import android.content.ContentValues;
import android.net.Uri;

/**
 * @author Kenny Root
 *
 */
public class HostBean extends AbstractBean {

	public static final String BEAN_NAME = "host";
	public static final int DEFAULT_FONT_SIZE = 10;

	/* Database fields */
	private long id = -1;
	private String nickname = null;
	private String username = null;
	private String hostname = null;
	private int port = 22;
	private String protocol = "ssh";
	private long lastConnect = -1;
	private String color;
	private boolean useKeys = true;
	private String useAuthAgent = HostDatabase.AUTHAGENT_NO;
	private String postLogin = null;
	private long pubkeyId = HostDatabase.PUBKEYID_ANY;
	private boolean wantSession = true;
	private String delKey = HostDatabase.DELKEY_DEL;
	private int fontSize = DEFAULT_FONT_SIZE;
	private boolean compression = false;
	private String encoding = HostDatabase.ENCODING_DEFAULT;
	private boolean stayConnected = false;
	private boolean quickDisconnect = false;

	public HostBean() {

	}

	@Override
	public String getBeanName() {
		return BEAN_NAME;
	}

	public HostBean(String nickname, String protocol, String username, String hostname, int port) {
		this.nickname = nickname;
		this.protocol = protocol;
		this.username = username;
		this.hostname = hostname;
		this.port = port;
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
	public void setUsername(String username) {
		this.username = username;
	}
	public String getUsername() {
		return username;
	}
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	public String getHostname() {
		return hostname;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public int getPort() {
		return port;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setLastConnect(long lastConnect) {
		this.lastConnect = lastConnect;
	}
	public long getLastConnect() {
		return lastConnect;
	}
	public void setColor(String color) {
		this.color = color;
	}
	public String getColor() {
		return color;
	}
	public void setUseKeys(boolean useKeys) {
		this.useKeys = useKeys;
	}
	public boolean getUseKeys() {
		return useKeys;
	}
	public void setUseAuthAgent(String useAuthAgent) {
		this.useAuthAgent = useAuthAgent;
	}
	public String getUseAuthAgent() {
		return useAuthAgent;
	}
	public void setPostLogin(String postLogin) {
		this.postLogin = postLogin;
	}
	public String getPostLogin() {
		return postLogin;
	}
	public void setPubkeyId(long pubkeyId) {
		this.pubkeyId = pubkeyId;
	}
	public long getPubkeyId() {
		return pubkeyId;
	}
	public void setWantSession(boolean wantSession) {
		this.wantSession = wantSession;
	}
	public boolean getWantSession() {
		return wantSession;
	}
	public void setDelKey(String delKey) {
		this.delKey = delKey;
	}
	public String getDelKey() {
		return delKey;
	}
	public void setFontSize(int fontSize) {
		this.fontSize = fontSize;
	}
	public int getFontSize() {
		return fontSize;
	}
	public void setCompression(boolean compression) {
		this.compression = compression;
	}
	public boolean getCompression() {
		return compression;
	}

	public void setEncoding(String encoding) {
		this.encoding  = encoding;
	}

	public String getEncoding() {
		return this.encoding;
	}

	public void setStayConnected(boolean stayConnected) {
		this.stayConnected = stayConnected;
	}

	public boolean getStayConnected() {
		return stayConnected;
	}

	public void setQuickDisconnect(boolean quickDisconnect) {
		this.quickDisconnect = quickDisconnect;
	}

	public boolean getQuickDisconnect() {
		return quickDisconnect;
	}

	public String getDescription() {
		String description = String.format("%s@%s", username, hostname);

		if (port != 22)
			description += String.format(":%d", port);

		return description;
	}

	@Override
	public ContentValues getValues() {
		ContentValues values = new ContentValues();

		values.put(HostDatabase.FIELD_HOST_NICKNAME, nickname);
		values.put(HostDatabase.FIELD_HOST_PROTOCOL, protocol);
		values.put(HostDatabase.FIELD_HOST_USERNAME, username);
		values.put(HostDatabase.FIELD_HOST_HOSTNAME, hostname);
		values.put(HostDatabase.FIELD_HOST_PORT, port);
		values.put(HostDatabase.FIELD_HOST_LASTCONNECT, lastConnect);
		values.put(HostDatabase.FIELD_HOST_COLOR, color);
		values.put(HostDatabase.FIELD_HOST_USEKEYS, Boolean.toString(useKeys));
		values.put(HostDatabase.FIELD_HOST_USEAUTHAGENT, useAuthAgent);
		values.put(HostDatabase.FIELD_HOST_POSTLOGIN, postLogin);
		values.put(HostDatabase.FIELD_HOST_PUBKEYID, pubkeyId);
		values.put(HostDatabase.FIELD_HOST_WANTSESSION, Boolean.toString(wantSession));
		values.put(HostDatabase.FIELD_HOST_DELKEY, delKey);
		values.put(HostDatabase.FIELD_HOST_FONTSIZE, fontSize);
		values.put(HostDatabase.FIELD_HOST_COMPRESSION, Boolean.toString(compression));
		values.put(HostDatabase.FIELD_HOST_ENCODING, encoding);
		values.put(HostDatabase.FIELD_HOST_STAYCONNECTED, Boolean.toString(stayConnected));
		values.put(HostDatabase.FIELD_HOST_QUICKDISCONNECT, Boolean.toString(quickDisconnect));

		return values;
	}

	public static HostBean fromContentValues(ContentValues values) {
		HostBean host = new HostBean();
		host.setNickname(values.getAsString(HostDatabase.FIELD_HOST_NICKNAME));
		host.setProtocol(values.getAsString(HostDatabase.FIELD_HOST_PROTOCOL));
		host.setUsername(values.getAsString(HostDatabase.FIELD_HOST_USERNAME));
		host.setHostname(values.getAsString(HostDatabase.FIELD_HOST_HOSTNAME));
		host.setPort(values.getAsInteger(HostDatabase.FIELD_HOST_PORT));
		host.setLastConnect(values.getAsLong(HostDatabase.FIELD_HOST_LASTCONNECT));
		host.setColor(values.getAsString(HostDatabase.FIELD_HOST_COLOR));
		host.setUseKeys(Boolean.valueOf(values.getAsString(HostDatabase.FIELD_HOST_USEKEYS)));
		host.setUseAuthAgent(values.getAsString(HostDatabase.FIELD_HOST_USEAUTHAGENT));
		host.setPostLogin(values.getAsString(HostDatabase.FIELD_HOST_POSTLOGIN));
		host.setPubkeyId(values.getAsLong(HostDatabase.FIELD_HOST_PUBKEYID));
		host.setWantSession(Boolean.valueOf(values.getAsString(HostDatabase.FIELD_HOST_WANTSESSION)));
		host.setDelKey(values.getAsString(HostDatabase.FIELD_HOST_DELKEY));
		host.setFontSize(values.getAsInteger(HostDatabase.FIELD_HOST_FONTSIZE));
		host.setCompression(Boolean.valueOf(values.getAsString(HostDatabase.FIELD_HOST_COMPRESSION)));
		host.setEncoding(values.getAsString(HostDatabase.FIELD_HOST_ENCODING));
		host.setStayConnected(values.getAsBoolean(HostDatabase.FIELD_HOST_STAYCONNECTED));
		host.setQuickDisconnect(values.getAsBoolean(HostDatabase.FIELD_HOST_QUICKDISCONNECT));
		return host;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || !(o instanceof HostBean))
			return false;

		HostBean host = (HostBean) o;

		if (id != -1 && host.getId() != -1)
			return host.getId() == id;

		if (nickname == null) {
			if (host.getNickname() != null)
				return false;
		} else if (!nickname.equals(host.getNickname()))
			return false;

		if (protocol == null) {
			if (host.getProtocol() != null)
				return false;
		} else if (!protocol.equals(host.getProtocol()))
			return false;

		if (username == null) {
			if (host.getUsername() != null)
				return false;
		} else if (!username.equals(host.getUsername()))
			return false;

		if (hostname == null) {
			if (host.getHostname() != null)
				return false;
		} else if (!hostname.equals(host.getHostname()))
			return false;

		if (port != host.getPort())
			return false;

		return true;
	}

	@Override
	public int hashCode() {
		int hash = 7;

		if (id != -1)
			return (int) id;

		hash = 31 * hash + (null == nickname ? 0 : nickname.hashCode());
		hash = 31 * hash + (null == protocol ? 0 : protocol.hashCode());
		hash = 31 * hash + (null == username ? 0 : username.hashCode());
		hash = 31 * hash + (null == hostname ? 0 : hostname.hashCode());
		hash = 31 * hash + port;

		return hash;
	}

	/**
	 * @return URI identifying this HostBean
	 */
	public Uri getUri() {
		StringBuilder sb = new StringBuilder();
		sb.append(protocol)
			.append("://");

		if (username != null)
			sb.append(Uri.encode(username))
				.append('@');

		sb.append(Uri.encode(hostname))
			.append(':')
			.append(port)
			.append("/#")
			.append(nickname);
		return Uri.parse(sb.toString());
	}

	/**
	 * Generates a "pretty" string to be used in the quick-connect host edit view.
	 */
	@Override
	public String toString() {
		if (protocol == null)
			return "";

		int defaultPort = TransportFactory.getTransport(protocol).getDefaultPort();

		if (SSH.getProtocolName().equals(protocol)) {
			if (username == null || hostname == null ||
					username.equals("") || hostname.equals(""))
				return "";

			if (port == defaultPort)
				return username + "@" + hostname;
			else
				return username + "@" + hostname + ":" + port;
		} else if (Telnet.getProtocolName().equals(protocol)) {
			if (hostname == null || hostname.equals(""))
				return "";
			else if (port == defaultPort)
				return hostname;
			else
				return hostname + ":" + port;
		} else if (Local.getProtocolName().equals(protocol)) {
			return nickname;
		}

		// Fail gracefully.
		return "";
	}

}
