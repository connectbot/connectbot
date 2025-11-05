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

package org.connectbot.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.util.Log;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

/**
 * Utility class for network-related operations, specifically for detecting
 * WiFi hotspot IP addresses for port forwarding bind address selection.
 *
 * @author ConnectBot Team
 */
public class NetworkUtils {
	public final static String TAG = "CB.NetworkUtils";

	// Bind address constants
	public static final String BIND_LOCALHOST = "localhost";
	public static final String BIND_ALL_INTERFACES = "0.0.0.0";
	public static final String BIND_ACCESS_POINT = "access_point";

	// Common AP interface name patterns
	private static final String[] AP_INTERFACE_PATTERNS = {
		"ap", "wlan1", "p2p", "hotspot", "softap", "wifi_ap"
	};

	// Cellular interface name patterns for security filtering
	private static final String[] CELLULAR_INTERFACE_PATTERNS = {
		"rmnet", "ccmni", "pdp", "ppp", "cellular", "mobile", "radio", "baseband"
	};

	/**
	 * Interface for listening to network changes
	 */
	public interface NetworkChangeListener {
		void onAccessPointIPChanged(String newIP);
		void onAccessPointDisconnected();
	}

	private static String lastKnownApIP = null;

	/**
	 * Get the current WiFi access point IP address
	 * Read-only method - does not update internal state to avoid masking changes
	 * from the background monitor task
	 * @param context Android context
	 * @return IP address as string, or null if not available
	 */
	public static String getAccessPointIP(Context context) {
		// Only use explicitly identified AP interface IPs for security
		// Do NOT update lastKnownApIP here - that's only for hasAccessPointStateChanged()
		String apInterfaceIP = getHotspotInterfaceIP();
		if (apInterfaceIP != null) {
			Log.d(TAG, "Found AP interface IP: " + apInterfaceIP);
			return apInterfaceIP;
		}

		Log.d(TAG, "No valid AP interface found");
		return null;
	}

	/**
	 * Check if the access point state has changed since last check
	 * @param context Android context
	 * @return true if AP state changed
	 */
	public static boolean hasAccessPointStateChanged(Context context) {
		String currentApIP = getHotspotInterfaceIP(); // Don't update lastKnownApIP yet
		boolean changed = !java.util.Objects.equals(lastKnownApIP, currentApIP);
		if (changed) {
			Log.d(TAG, "AP state changed: " + lastKnownApIP + " -> " + currentApIP);
			lastKnownApIP = currentApIP; // Update only when we detect a change
		}
		return changed;
	}

	/**
	 * Get the hotspot interface IP address from provided interfaces (pure logic, testable)
	 * @param interfaces List of network interfaces to check
	 * @return IP address string, or null if not found
	 */
	static String getHotspotInterfaceIP(List<NetworkInterface> interfaces) {
		if (interfaces == null) return null;

		for (NetworkInterface intf : interfaces) {
			try {
				if (!intf.isUp() || intf.isLoopback() || intf.isVirtual()) {
					continue;
				}

				String name = intf.getName().toLowerCase();

				// SECURITY: Block cellular interfaces using device-agnostic detection
				if (isCellularInterface(intf)) {
					continue;
				}

				// Look for common AP interface names
				boolean isApInterface = false;
				for (String pattern : AP_INTERFACE_PATTERNS) {
					if (name.contains(pattern)) {
						isApInterface = true;
						break;
					}
				}
				if (isApInterface) {

					List<InterfaceAddress> addrs = intf.getInterfaceAddresses();
					for (InterfaceAddress addr : addrs) {
						InetAddress inetAddr = addr.getAddress();
						if (!inetAddr.isLoopbackAddress() &&
							!inetAddr.isLinkLocalAddress() &&
							inetAddr.isSiteLocalAddress()) {

							String ip = inetAddr.getHostAddress();
							if (ip != null && !ip.contains(":")) { // IPv4 only

								// Verify this looks like an AP IP (typically x.x.x.1)
								if (isLikelyApIP(ip)) {
									return ip;
								}
							}
						}
					}
				}
			} catch (Exception e) {
				// Skip this interface if there's an error checking it
				continue;
			}
		}

		// No fallback - only use explicitly named AP interfaces for security
		return null;
	}

	/**
	 * Get the hotspot interface IP address when phone is acting as WiFi AP
	 * @return IP address string, or null if not found
	 */
	private static String getHotspotInterfaceIP() {
		try {
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
			String result = getHotspotInterfaceIP(interfaces);
			if (result != null) {
				Log.d(TAG, "Found AP interface with IP: " + result);
			} else {
				Log.d(TAG, "No hotspot interface IP found");
			}
			return result;
		} catch (SocketException e) {
			Log.e(TAG, "Error getting hotspot interface IP", e);
			return null;
		}
	}

	/**
	 * Check if an IP address looks like an access point IP
	 * @param ip the IP address to check
	 * @return true if it looks like an AP IP
	 */
	private static boolean isLikelyApIP(String ip) {
		if (ip == null) return false;

		// Accept private IP ranges that could be AP addresses
		// Since we've already filtered out cellular interfaces by their
		// network characteristics, we can be permissive with IP ranges
		return ip.startsWith("192.168.") ||
			ip.startsWith("10.") ||
			ip.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
	}


	/**
	 * Check if an interface is a cellular interface
	 * Uses only interface name patterns for reliable detection
	 * @param intf the network interface to check
	 * @return true if this appears to be a cellular interface
	 */
	private static boolean isCellularInterface(NetworkInterface intf) {
		if (intf == null) return false;

		String name = intf.getName().toLowerCase();

		// Only use interface name patterns for detection
		// This is the most reliable method across all devices
		for (String pattern : CELLULAR_INTERFACE_PATTERNS) {
			if (name.contains(pattern)) {
				Log.d(TAG, "Interface " + name + " identified as cellular by name pattern: " + pattern);
				return true;
			}
		}

		return false;
	}

	/**
	 * Check if WiFi is currently connected
	 * @param context Android context
	 * @return true if WiFi is connected
	 */
	public static boolean isWifiConnected(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm == null) {
			return false;
		}

		try {
			// API 23+
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
				Network activeNetwork = cm.getActiveNetwork();
				if (activeNetwork == null) {
					return false;
				}
				NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
				return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
			} else {
				// Legacy API
				NetworkInfo networkInfo = cm.getActiveNetworkInfo();
				return networkInfo != null &&
					networkInfo.isConnected() &&
					networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
			}
		} catch (Exception e) {
			Log.e(TAG, "Error checking WiFi connection", e);
			return false;
		}
	}

	/**
	 * Check if access point is available for binding
	 * @param context Android context
	 * @return true if access point IP is available
	 */
	public static boolean isAccessPointAvailable(Context context) {
		return getAccessPointIP(context) != null;
	}

	/**
	 * Get display name for bind address type (pure logic, testable)
	 * @param bindAddress the bind address string
	 * @param apIP the current AP IP (null if unavailable)
	 * @return human readable name
	 */
	public static String getBindAddressDisplayName(String bindAddress, String apIP) {
		if (BIND_ALL_INTERFACES.equals(bindAddress)) {
			return "all interfaces";
		} else if (BIND_ACCESS_POINT.equals(bindAddress)) {
			if (apIP != null) {
				return "WiFi hotspot (" + apIP + ")";
			} else {
				return "WiFi hotspot (unavailable)";
			}
		} else {
			return "localhost";
		}
	}

	/**
	 * Get display name for bind address type
	 * @param bindAddress the bind address string
	 * @param context Android context for hotspot IP resolution
	 * @return human readable name
	 */
	public static String getBindAddressDisplayName(String bindAddress, Context context) {
		String apIP = (context != null) ? getAccessPointIP(context) : null;
		return getBindAddressDisplayName(bindAddress, apIP);
	}

	/**
	 * Get simple bind address display for compact UI (pure logic, testable)
	 * @param bindAddress the bind address string
	 * @param apIP the current AP IP (null if unavailable)
	 * @return simple address display (actual IP or short form)
	 */
	public static String getSimpleBindAddressDisplay(String bindAddress, String apIP) {
		if (BIND_ALL_INTERFACES.equals(bindAddress)) {
			return BIND_ALL_INTERFACES;
		} else if (BIND_ACCESS_POINT.equals(bindAddress)) {
			return apIP != null ? apIP : "AP";
		} else {
			return "localhost";
		}
	}

	/**
	 * Get simple bind address display for compact UI (shows actual IPs)
	 * @param bindAddress the bind address string
	 * @param context Android context for hotspot IP resolution
	 * @return simple address display (actual IP or short form)
	 */
	public static String getSimpleBindAddressDisplay(String bindAddress, Context context) {
		String apIP = (context != null) ? getAccessPointIP(context) : null;
		return getSimpleBindAddressDisplay(bindAddress, apIP);
	}

	/**
	 * Resolve bind address string to actual IP address (pure logic, testable)
	 * @param bindAddress the bind address type
	 * @param apIP the current AP IP (null if unavailable)
	 * @return IP address string, or null if access_point is requested but unavailable
	 */
	public static String resolveBindAddress(String bindAddress, String apIP) {
		if (BIND_ALL_INTERFACES.equals(bindAddress)) {
			return BIND_ALL_INTERFACES;
		} else if (BIND_ACCESS_POINT.equals(bindAddress)) {
			// Return null if AP is not available - do not fall back to localhost for security
			return apIP;
		} else {
			return "127.0.0.1";
		}
	}

	/**
	 * Resolve bind address string to actual IP address
	 * @param bindAddress the bind address type
	 * @param context Android context
	 * @return IP address string, or null if access_point is requested but unavailable
	 */
	public static String resolveBindAddress(String bindAddress, Context context) {
		String apIP = (context != null) ? getAccessPointIP(context) : null;
		return resolveBindAddress(bindAddress, apIP);
	}
}