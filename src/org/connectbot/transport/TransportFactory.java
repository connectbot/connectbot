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

package org.connectbot.transport;

import java.util.HashMap;
import java.util.Map;

import org.connectbot.bean.HostBean;
import org.connectbot.util.HostDatabase;

import android.content.Context;
import android.net.Uri;
import android.util.Log;


/**
 * @author Kenny Root
 *
 */
public class TransportFactory {
	private static final String TAG = "ConnectBot.TransportFactory";

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

	/**
	 * @param hostdb Handle to HostDatabase
	 * @param uri URI to target server
	 * @param host HostBean in which to put the results
	 * @return true when host was found
	 */
	public static HostBean findHost(HostDatabase hostdb, Uri uri) {
		AbsTransport transport = getTransport(uri.getScheme());

		Map<String, String> selection = new HashMap<String, String>();

		transport.getSelectionArgs(uri, selection);
		if (selection.size() == 0) {
			Log.e(TAG, String.format("Transport %s failed to do something useful with URI=%s",
					uri.getScheme(), uri.toString()));
			throw new IllegalStateException("Failed to get needed selection arguments");
		}

		return hostdb.findHost(selection);
	}
}
