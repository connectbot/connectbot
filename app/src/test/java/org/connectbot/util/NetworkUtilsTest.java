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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for NetworkUtils functionality, focusing on methods that can be tested
 * without Android Context dependencies.
 */
public class NetworkUtilsTest {

	@Test
	public void testBindAddressConstants() {
		assertEquals("localhost", NetworkUtils.BIND_LOCALHOST);
		assertEquals("0.0.0.0", NetworkUtils.BIND_ALL_INTERFACES);
		assertEquals("access_point", NetworkUtils.BIND_ACCESS_POINT);
	}

	@Test
	public void testGetBindAddressDisplayName() {
		// Test with null AP IP (unavailable)
		assertEquals("all interfaces",
			NetworkUtils.getBindAddressDisplayName("0.0.0.0", (String)null));
		assertEquals("localhost",
			NetworkUtils.getBindAddressDisplayName("localhost", (String)null));
		assertEquals("localhost",
			NetworkUtils.getBindAddressDisplayName("anything_else", (String)null));
		assertEquals("WiFi hotspot (unavailable)",
			NetworkUtils.getBindAddressDisplayName("access_point", (String)null));

		// Test with valid AP IP
		assertEquals("WiFi hotspot (192.168.1.1)",
			NetworkUtils.getBindAddressDisplayName("access_point", "192.168.1.1"));
	}

	@Test
	public void testGetSimpleBindAddressDisplay() {
		// Test with null AP IP (unavailable)
		assertEquals("0.0.0.0",
			NetworkUtils.getSimpleBindAddressDisplay("0.0.0.0", (String)null));
		assertEquals("localhost",
			NetworkUtils.getSimpleBindAddressDisplay("localhost", (String)null));
		assertEquals("localhost",
			NetworkUtils.getSimpleBindAddressDisplay("anything_else", (String)null));
		assertEquals("AP",
			NetworkUtils.getSimpleBindAddressDisplay("access_point", (String)null));

		// Test with valid AP IP
		assertEquals("192.168.1.1",
			NetworkUtils.getSimpleBindAddressDisplay("access_point", "192.168.1.1"));
	}

	@Test
	public void testResolveBindAddress() {
		// Test with null AP IP (unavailable)
		assertEquals("0.0.0.0",
			NetworkUtils.resolveBindAddress("0.0.0.0", (String)null));
		assertEquals("127.0.0.1",
			NetworkUtils.resolveBindAddress("localhost", (String)null));
		assertEquals("127.0.0.1",
			NetworkUtils.resolveBindAddress("anything_else", (String)null));
		// access_point with null AP IP should return null
		assertNull(NetworkUtils.resolveBindAddress("access_point", (String)null));

		// Test with valid AP IP
		assertEquals("192.168.1.1",
			NetworkUtils.resolveBindAddress("access_point", "192.168.1.1"));
	}

	@Test
	public void testBindAddressConstantsEdgeCases() {
		// Test edge cases with constants using pure logic methods
		assertEquals("localhost", NetworkUtils.getBindAddressDisplayName(NetworkUtils.BIND_LOCALHOST, (String)null));
		assertEquals("all interfaces", NetworkUtils.getBindAddressDisplayName(NetworkUtils.BIND_ALL_INTERFACES, (String)null));
		assertEquals("WiFi hotspot (unavailable)", NetworkUtils.getBindAddressDisplayName(NetworkUtils.BIND_ACCESS_POINT, (String)null));

		// Test that constants are non-null and non-empty
		assertNotNull(NetworkUtils.BIND_LOCALHOST);
		assertNotNull(NetworkUtils.BIND_ALL_INTERFACES);
		assertNotNull(NetworkUtils.BIND_ACCESS_POINT);
		assertFalse(NetworkUtils.BIND_LOCALHOST.isEmpty());
		assertFalse(NetworkUtils.BIND_ALL_INTERFACES.isEmpty());
		assertFalse(NetworkUtils.BIND_ACCESS_POINT.isEmpty());
	}

	@Test
	public void testNullInputHandling() {
		// Test methods handle null inputs gracefully using pure logic methods
		assertEquals("localhost", NetworkUtils.getBindAddressDisplayName(null, (String)null));
		assertEquals("localhost", NetworkUtils.getSimpleBindAddressDisplay(null, (String)null));
		assertEquals("127.0.0.1", NetworkUtils.resolveBindAddress(null, (String)null));

		// Test empty strings
		assertEquals("localhost", NetworkUtils.getBindAddressDisplayName("", (String)null));
		assertEquals("localhost", NetworkUtils.getSimpleBindAddressDisplay("", (String)null));
		assertEquals("127.0.0.1", NetworkUtils.resolveBindAddress("", (String)null));
	}

	@Test
	public void testGetHotspotInterfaceIP_NullInput() {
		// Test null input
		assertNull(NetworkUtils.getHotspotInterfaceIP(null));

		// Test empty list
		assertNull(NetworkUtils.getHotspotInterfaceIP(Collections.emptyList()));
	}

	@Test
	public void testGetHotspotInterfaceIP_ValidApInterface() throws Exception {
		// Create mock NetworkInterface for AP
		NetworkInterface mockIntf = mock(NetworkInterface.class);
		when(mockIntf.isUp()).thenReturn(true);
		when(mockIntf.isLoopback()).thenReturn(false);
		when(mockIntf.isVirtual()).thenReturn(false);
		when(mockIntf.getName()).thenReturn("ap0");

		// Create mock InetAddress and InterfaceAddress
		InetAddress mockInetAddr = mock(InetAddress.class);
		when(mockInetAddr.isLoopbackAddress()).thenReturn(false);
		when(mockInetAddr.isLinkLocalAddress()).thenReturn(false);
		when(mockInetAddr.isSiteLocalAddress()).thenReturn(true);
		when(mockInetAddr.getHostAddress()).thenReturn("192.168.1.1");

		InterfaceAddress mockIfaceAddr = mock(InterfaceAddress.class);
		when(mockIfaceAddr.getAddress()).thenReturn(mockInetAddr);

		when(mockIntf.getInterfaceAddresses()).thenReturn(Arrays.asList(mockIfaceAddr));

		List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
		String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

		assertEquals("192.168.1.1", result);
	}

	@Test
	public void testGetHotspotInterfaceIP_NoApInterface() throws Exception {
		// Create mock NetworkInterface for non-AP interface
		NetworkInterface mockIntf = mock(NetworkInterface.class);
		when(mockIntf.isUp()).thenReturn(true);
		when(mockIntf.isLoopback()).thenReturn(false);
		when(mockIntf.isVirtual()).thenReturn(false);
		when(mockIntf.getName()).thenReturn("wlan0"); // Not an AP interface

		List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
		String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

		assertNull(result);
	}

	@Test
	public void testGetHotspotInterfaceIP_InterfaceDown() throws Exception {
		// Create mock NetworkInterface that's down
		NetworkInterface mockIntf = mock(NetworkInterface.class);
		when(mockIntf.isUp()).thenReturn(false); // Interface is down
		when(mockIntf.getName()).thenReturn("ap0");

		List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
		String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

		assertNull(result);
	}

	@Test
	public void testGetHotspotInterfaceIP_IPv6Filtered() throws Exception {
		// Create mock NetworkInterface for AP with IPv6 address
		NetworkInterface mockIntf = mock(NetworkInterface.class);
		when(mockIntf.isUp()).thenReturn(true);
		when(mockIntf.isLoopback()).thenReturn(false);
		when(mockIntf.isVirtual()).thenReturn(false);
		when(mockIntf.getName()).thenReturn("ap0");

		// Create mock InetAddress with IPv6 (contains colon)
		InetAddress mockInetAddr = mock(InetAddress.class);
		when(mockInetAddr.isLoopbackAddress()).thenReturn(false);
		when(mockInetAddr.isLinkLocalAddress()).thenReturn(false);
		when(mockInetAddr.isSiteLocalAddress()).thenReturn(true);
		when(mockInetAddr.getHostAddress()).thenReturn("2001:db8::1"); // IPv6

		InterfaceAddress mockIfaceAddr = mock(InterfaceAddress.class);
		when(mockIfaceAddr.getAddress()).thenReturn(mockInetAddr);

		when(mockIntf.getInterfaceAddresses()).thenReturn(Arrays.asList(mockIfaceAddr));

		List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
		String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

		assertNull(result); // IPv6 should be filtered out
	}

	@Test
	public void testGetHotspotInterfaceIP_PublicIPFiltered() throws Exception {
		// Create mock NetworkInterface for AP with public IP
		NetworkInterface mockIntf = mock(NetworkInterface.class);
		when(mockIntf.isUp()).thenReturn(true);
		when(mockIntf.isLoopback()).thenReturn(false);
		when(mockIntf.isVirtual()).thenReturn(false);
		when(mockIntf.getName()).thenReturn("ap0");

		// Create mock InetAddress with public IP
		InetAddress mockInetAddr = mock(InetAddress.class);
		when(mockInetAddr.isLoopbackAddress()).thenReturn(false);
		when(mockInetAddr.isLinkLocalAddress()).thenReturn(false);
		when(mockInetAddr.isSiteLocalAddress()).thenReturn(true);
		when(mockInetAddr.getHostAddress()).thenReturn("8.8.8.8"); // Public IP

		InterfaceAddress mockIfaceAddr = mock(InterfaceAddress.class);
		when(mockIfaceAddr.getAddress()).thenReturn(mockInetAddr);

		when(mockIntf.getInterfaceAddresses()).thenReturn(Arrays.asList(mockIfaceAddr));

		List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
		String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

		assertNull(result); // Public IP should be filtered out by isLikelyApIP
	}

	@Test
	public void testGetHotspotInterfaceIP_AllApPatterns() throws Exception {
		// Test all AP interface patterns: "ap", "wlan1", "p2p", "hotspot", "softap", "wifi_ap"
		String[] apPatterns = {"ap0", "wlan1", "p2p0", "hotspot0", "softap0", "wifi_ap0"};

		for (String interfaceName : apPatterns) {
			NetworkInterface mockIntf = mock(NetworkInterface.class);
			when(mockIntf.isUp()).thenReturn(true);
			when(mockIntf.isLoopback()).thenReturn(false);
			when(mockIntf.isVirtual()).thenReturn(false);
			when(mockIntf.getName()).thenReturn(interfaceName);

			InetAddress mockInetAddr = mock(InetAddress.class);
			when(mockInetAddr.isLoopbackAddress()).thenReturn(false);
			when(mockInetAddr.isLinkLocalAddress()).thenReturn(false);
			when(mockInetAddr.isSiteLocalAddress()).thenReturn(true);
			when(mockInetAddr.getHostAddress()).thenReturn("192.168.1.1");

			InterfaceAddress mockIfaceAddr = mock(InterfaceAddress.class);
			when(mockIfaceAddr.getAddress()).thenReturn(mockInetAddr);
			when(mockIntf.getInterfaceAddresses()).thenReturn(Arrays.asList(mockIfaceAddr));

			List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
			String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

			assertEquals("AP pattern " + interfaceName + " should be detected", "192.168.1.1", result);
		}
	}

	@Test
	public void testGetHotspotInterfaceIP_CellularInterfacesFiltered() throws Exception {
		// Test that cellular interfaces are filtered out (security feature)
		String[] cellularNames = {"rmnet0", "ccmni0", "pdp_ip0", "ppp0", "cellular0", "mobile0", "radio0", "baseband0"};

		for (String cellularName : cellularNames) {
			NetworkInterface mockIntf = mock(NetworkInterface.class);
			when(mockIntf.isUp()).thenReturn(true);
			when(mockIntf.isLoopback()).thenReturn(false);
			when(mockIntf.isVirtual()).thenReturn(false);
			when(mockIntf.getName()).thenReturn(cellularName);

			// Even if it has AP-like IP, should be filtered out by cellular detection
			InetAddress mockInetAddr = mock(InetAddress.class);
			when(mockInetAddr.isLoopbackAddress()).thenReturn(false);
			when(mockInetAddr.isLinkLocalAddress()).thenReturn(false);
			when(mockInetAddr.isSiteLocalAddress()).thenReturn(true);
			when(mockInetAddr.getHostAddress()).thenReturn("192.168.1.1");

			InterfaceAddress mockIfaceAddr = mock(InterfaceAddress.class);
			when(mockIfaceAddr.getAddress()).thenReturn(mockInetAddr);
			when(mockIntf.getInterfaceAddresses()).thenReturn(Arrays.asList(mockIfaceAddr));

			List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
			String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

			assertNull("Cellular interface " + cellularName + " should be filtered out", result);
		}
	}

	@Test
	public void testGetHotspotInterfaceIP_AllPrivateIPRanges() throws Exception {
		// Test all supported private IP ranges: 192.168.x, 10.x, 172.16-31.x
		String[] validPrivateIPs = {
			"192.168.1.1",    // 192.168.x.x range
			"192.168.0.1",
			"10.0.0.1",       // 10.x.x.x range
			"10.255.255.1",
			"172.16.0.1",     // 172.16-31.x.x range
			"172.31.255.1"
		};

		for (String ip : validPrivateIPs) {
			NetworkInterface mockIntf = mock(NetworkInterface.class);
			when(mockIntf.isUp()).thenReturn(true);
			when(mockIntf.isLoopback()).thenReturn(false);
			when(mockIntf.isVirtual()).thenReturn(false);
			when(mockIntf.getName()).thenReturn("ap0");

			InetAddress mockInetAddr = mock(InetAddress.class);
			when(mockInetAddr.isLoopbackAddress()).thenReturn(false);
			when(mockInetAddr.isLinkLocalAddress()).thenReturn(false);
			when(mockInetAddr.isSiteLocalAddress()).thenReturn(true);
			when(mockInetAddr.getHostAddress()).thenReturn(ip);

			InterfaceAddress mockIfaceAddr = mock(InterfaceAddress.class);
			when(mockIfaceAddr.getAddress()).thenReturn(mockInetAddr);
			when(mockIntf.getInterfaceAddresses()).thenReturn(Arrays.asList(mockIfaceAddr));

			List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
			String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

			assertEquals("Private IP " + ip + " should be accepted", ip, result);
		}
	}

	@Test
	public void testGetHotspotInterfaceIP_InvalidPrivateIPRanges() throws Exception {
		// Test IPs that should be rejected by isLikelyApIP
		String[] invalidPrivateIPs = {
			"172.15.255.255", // Just below 172.16-31 range
			"172.32.0.0",     // Just above 172.16-31 range
			"172.0.0.1",      // Way below range
			"127.0.0.1",      // Loopback
			"169.254.1.1",    // Link-local
			"8.8.8.8"         // Public IP
		};

		for (String ip : invalidPrivateIPs) {
			NetworkInterface mockIntf = mock(NetworkInterface.class);
			when(mockIntf.isUp()).thenReturn(true);
			when(mockIntf.isLoopback()).thenReturn(false);
			when(mockIntf.isVirtual()).thenReturn(false);
			when(mockIntf.getName()).thenReturn("ap0");

			InetAddress mockInetAddr = mock(InetAddress.class);
			when(mockInetAddr.isLoopbackAddress()).thenReturn(false);
			when(mockInetAddr.isLinkLocalAddress()).thenReturn(false);
			when(mockInetAddr.isSiteLocalAddress()).thenReturn(true);
			when(mockInetAddr.getHostAddress()).thenReturn(ip);

			InterfaceAddress mockIfaceAddr = mock(InterfaceAddress.class);
			when(mockIfaceAddr.getAddress()).thenReturn(mockInetAddr);
			when(mockIntf.getInterfaceAddresses()).thenReturn(Arrays.asList(mockIfaceAddr));

			List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
			String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

			assertNull("Invalid private IP " + ip + " should be rejected", result);
		}
	}

	@Test
	public void testGetHotspotInterfaceIP_MultipleInterfaces() throws Exception {
		// Test realistic scenario: cellular first, then WiFi client, then AP
		NetworkInterface cellularIntf = mock(NetworkInterface.class);
		when(cellularIntf.isUp()).thenReturn(true);
		when(cellularIntf.isLoopback()).thenReturn(false);
		when(cellularIntf.isVirtual()).thenReturn(false);
		when(cellularIntf.getName()).thenReturn("rmnet0"); // Should be filtered

		NetworkInterface wifiClientIntf = mock(NetworkInterface.class);
		when(wifiClientIntf.isUp()).thenReturn(true);
		when(wifiClientIntf.isLoopback()).thenReturn(false);
		when(wifiClientIntf.isVirtual()).thenReturn(false);
		when(wifiClientIntf.getName()).thenReturn("wlan0"); // Not AP pattern

		NetworkInterface apIntf = mock(NetworkInterface.class);
		when(apIntf.isUp()).thenReturn(true);
		when(apIntf.isLoopback()).thenReturn(false);
		when(apIntf.isVirtual()).thenReturn(false);
		when(apIntf.getName()).thenReturn("ap0"); // AP pattern

		InetAddress mockInetAddr = mock(InetAddress.class);
		when(mockInetAddr.isLoopbackAddress()).thenReturn(false);
		when(mockInetAddr.isLinkLocalAddress()).thenReturn(false);
		when(mockInetAddr.isSiteLocalAddress()).thenReturn(true);
		when(mockInetAddr.getHostAddress()).thenReturn("192.168.43.1");

		InterfaceAddress mockIfaceAddr = mock(InterfaceAddress.class);
		when(mockIfaceAddr.getAddress()).thenReturn(mockInetAddr);
		when(apIntf.getInterfaceAddresses()).thenReturn(Arrays.asList(mockIfaceAddr));

		// Other interfaces return empty address lists
		when(cellularIntf.getInterfaceAddresses()).thenReturn(Collections.emptyList());
		when(wifiClientIntf.getInterfaceAddresses()).thenReturn(Collections.emptyList());

		List<NetworkInterface> interfaces = Arrays.asList(cellularIntf, wifiClientIntf, apIntf);
		String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

		assertEquals("Should find AP interface despite cellular and client interfaces", "192.168.43.1", result);
	}

	@Test
	public void testGetHotspotInterfaceIP_InterfaceWithNoAddresses() throws Exception {
		// Test interface that matches AP pattern but has no addresses
		NetworkInterface mockIntf = mock(NetworkInterface.class);
		when(mockIntf.isUp()).thenReturn(true);
		when(mockIntf.isLoopback()).thenReturn(false);
		when(mockIntf.isVirtual()).thenReturn(false);
		when(mockIntf.getName()).thenReturn("ap0");
		when(mockIntf.getInterfaceAddresses()).thenReturn(Collections.emptyList());

		List<NetworkInterface> interfaces = Arrays.asList(mockIntf);
		String result = NetworkUtils.getHotspotInterfaceIP(interfaces);

		assertNull("Interface with no addresses should return null", result);
	}
}