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

import org.connectbot.util.HostDatabase;

import android.content.ContentValues;


/**
 * @author Kenny Root
 *
 */
public class PortForwardBean {
	private long id = -1;
	private long hostId = -1;
	private String nickname = null;
	private String type = null;
	private int sourcePort = -1;
	private String destAddr = null;
	private int destPort = -1;
	
	private boolean enabled = false;
	private Object identifier = null;
	
	/**
	 * @param id database ID of port forward
	 * @param nickname Nickname to use to identify port forward
	 * @param type One of the port forward types from {@link HostDatabase}
	 * @param sourcePort Source port number
	 * @param destAddr Destination hostname or IP address
	 * @param destPort Destination port number
	 */
	public PortForwardBean(long id, long hostId, String nickname, String type, int sourcePort, String destAddr, int destPort) {
		this.id = id;
		this.hostId = hostId;
		this.nickname = nickname;
		this.type = type;
		this.sourcePort = sourcePort;
		this.destAddr = destAddr;
		this.destPort = destPort;
	}
	
	/**
	 * 
	 */
	public PortForwardBean(long hostId) {
		this.hostId = hostId;
	}

	/**
	 * @param type One of the port forward types from {@link HostDatabase}
	 * @param source Source port number
	 * @param dest Destination is "host:port" format
	 */
	public PortForwardBean(long hostId, String nickname, String type, String source, String dest) {
		this.hostId = hostId;
		this.nickname = nickname;
		this.type = type;
		this.sourcePort = Integer.parseInt(source);
		
		this.setDest(dest);
	}

	/**
	 * @param id the id to set
	 */
	public void setId(long id) {
		this.id = id;
	}
	
	/**
	 * @return the id
	 */
	public long getId() {
		return id;
	}
	
	/**
	 * @param nickname the nickname to set
	 */
	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	/**
	 * @return the nickname
	 */
	public String getNickname() {
		return nickname;
	}

	/**
	 * @param type the type to set
	 */
	public void setType(String type) {
		this.type = type;
	}
	
	/**
	 * @return the type
	 */
	public String getType() {
		return type;
	}
	
	/**
	 * @param sourcePort the sourcePort to set
	 */
	public void setSourcePort(int sourcePort) {
		this.sourcePort = sourcePort;
	}
	
	/**
	 * @return the sourcePort
	 */
	public int getSourcePort() {
		return sourcePort;
	}
	
	/**
	 * @param dest The destination in "host:port" format
	 */
	public void setDest(String dest) {
		String[] destSplit = dest.split(":");
		this.destAddr = destSplit[0];
		if (destSplit.length > 1)
			this.destPort = Integer.parseInt(destSplit[1]);
	}
	
	/**
	 * @param destAddr the destAddr to set
	 */
	public void setDestAddr(String destAddr) {
		this.destAddr = destAddr;
	}
	
	/**
	 * @return the destAddr
	 */
	public String getDestAddr() {
		return destAddr;
	}
	
	/**
	 * @param destPort the destPort to set
	 */
	public void setDestPort(int destPort) {
		this.destPort = destPort;
	}
	
	/**
	 * @return the destPort
	 */
	public int getDestPort() {
		return destPort;
	}
	
	/**
	 * @param enabled the enabled to set
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	/**
	 * @return the enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * @param identifier the identifier of this particular type to set
	 */
	public void setIdentifier(Object identifier) {
		this.identifier = identifier;
	}

	/**
	 * @return the identifier used by this particular type
	 */
	public Object getIdentifier() {
		return identifier;
	}

	/**
	 * @return human readable description of the port forward
	 */
	public CharSequence getDescription() {
		String description = "Unknown type";
			
		if (HostDatabase.PORTFORWARD_LOCAL.equals(type)) {
			description = String.format("Local port %d to %s:%d", sourcePort, destAddr, destPort);
		} else if (HostDatabase.PORTFORWARD_REMOTE.equals(type)) {
			description = String.format("Remote port %d to %s:%d", sourcePort, destAddr, destPort); 
/* I don't think we need the SOCKS4 type.
		} else if (HostDatabase.PORTFORWARD_DYNAMIC4.equals(type)) {
			description = String.format("Dynamic port %d (SOCKS4)", sourcePort);
*/
		} else if (HostDatabase.PORTFORWARD_DYNAMIC5.equals(type)) {
			description = String.format("Dynamic port %d (SOCKS)", sourcePort);
		}
		
		return description;
	}

	/**
	 * @return
	 */
	public ContentValues getValues() {
		ContentValues values = new ContentValues();
		
		values.put(HostDatabase.FIELD_PORTFORWARD_HOSTID, hostId);
		values.put(HostDatabase.FIELD_PORTFORWARD_NICKNAME, nickname);
		values.put(HostDatabase.FIELD_PORTFORWARD_TYPE, type);
		values.put(HostDatabase.FIELD_PORTFORWARD_SOURCEPORT, sourcePort);
		values.put(HostDatabase.FIELD_PORTFORWARD_DESTADDR, destAddr);
		values.put(HostDatabase.FIELD_PORTFORWARD_DESTPORT, destPort);
		
		return values;
	}
}
