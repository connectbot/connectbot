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

package org.connectbot.transport;

import android.content.Context;
import android.net.Uri;
import android.util.Log;


/**
 * @author Kenny Root
 *
 */
public class TransportFactory {
	private static String[] transportNames = {
		SSH.getProtocolName(),
		Telnet.getProtocolName(),
		Local.getProtocolName(),
	};

	/**
	 * @param protocol
	 * @return
	 */
	public static AbsTransport getTransport(String protocol) {
		if (SSH.getProtocolName().equals(protocol)) {
			return new SSH();
		} else if (Telnet.getProtocolName().equals(protocol)) {
			return new Telnet();
		} else if (Local.getProtocolName().equals(protocol)) {
			return new Local();
		} else {
			return null;
		}
	}

	public static Uri getUri(String scheme, String input) {
		Log.d("TransportFactory", String.format(
				"Attempting to discover URI for scheme=%s on input=%s", scheme,
				input));
		if (SSH.getProtocolName().equals(scheme))
			return SSH.getUri(input);
		else if (Telnet.getProtocolName().equals(scheme))
			return Telnet.getUri(input);
		else if (Local.getProtocolName().equals(scheme)) {
			Log.d("TransportFactory", "Got to the local parsing area");
			return Local.getUri(input);
		} else
			return null;
	}

	public static String[] getTransportNames() {
		return transportNames;
	}

	public static boolean isSameTransportType(AbsTransport a, AbsTransport b) {
		if (a == null || b == null)
			return false;

		return a.getClass().equals(b.getClass());
	}

	public static boolean canForwardPorts(String protocol) {
		// TODO uh, make this have less knowledge about its children
		if (SSH.getProtocolName().equals(protocol)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * @param protocol text name of protocol
	 * @param context
	 * @return expanded format hint
	 */
	public static String getFormatHint(String protocol, Context context) {
		if (SSH.getProtocolName().equals(protocol)) {
			return SSH.getFormatHint(context);
		} else if (Telnet.getProtocolName().equals(protocol)) {
			return Telnet.getFormatHint(context);
		} else if (Local.getProtocolName().equals(protocol)) {
			return Local.getFormatHint(context);
		} else {
			return AbsTransport.getFormatHint(context);
		}
	}
}
