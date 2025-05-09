/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
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
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.connectbot.bean.HostBean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HostDatabaseTest {

		private HostDatabase hostDb;
		private Context context;

		private static final String TEST_HOST = "testhost.com";
		private static final int TEST_PORT = 22;
		private static final String TEST_ALGO_1 = "ssh-rsa";
		private static final byte[] TEST_KEY_1 = new byte[]{1, 2, 3, 4};
		private static final String TEST_ALGO_2 = "ecdsa-sha2-nistp256";
		private static final byte[] TEST_KEY_2 = new byte[]{5, 6, 7, 8};


		@Before
		public void setUp() throws Exception {
			context = ApplicationProvider.getApplicationContext();
			HostDatabase.resetInMemoryInstance(context);
			hostDb = HostDatabase.get(context);
		}


		private HostBean addTestHost(String nickname, String hostname, int port) {
			HostBean host = new HostBean();
			host.setNickname(nickname);
			host.setHostname(hostname);
			host.setPort(port);
			host.setProtocol("ssh");
			host.setUsername("testuser");
			return hostDb.saveHost(host);
		}

		@Test
		public void testRemoveExistingKnownHost() {
			// 1. Add a host
			HostBean host = addTestHost("TestHost1", TEST_HOST, TEST_PORT);
			assertNotNull("Host should be added successfully", host);
			assertTrue("Host ID should be valid", host.getId() >= 0);

			// 2. Add a known host key for it
			hostDb.saveKnownHost(TEST_HOST, TEST_PORT, TEST_ALGO_1, TEST_KEY_1);

			// 3. Verify the key was added
			List<String> algorithms = hostDb.getHostKeyAlgorithmsForHost(TEST_HOST, TEST_PORT);
			assertNotNull("Algorithm list should not be null", algorithms);
			assertEquals("Should have 1 algorithm initially", 1, algorithms.size());
			assertEquals("Algorithm should match", TEST_ALGO_1, algorithms.get(0));

			// 4. Remove the known host key
			hostDb.removeKnownHost(TEST_HOST, TEST_PORT, TEST_ALGO_1, TEST_KEY_1);

			// 5. Verify the key was removed
			algorithms = hostDb.getHostKeyAlgorithmsForHost(TEST_HOST, TEST_PORT);
			assertNotNull("Algorithm list should not be null after removal", algorithms);
			assertTrue("Algorithm list should be empty after removal", algorithms.isEmpty());

			// 6. Verify the host itself still exists
			HostBean foundHost = hostDb.findHostById(host.getId());
			assertNotNull("Host should still exist after removing known key", foundHost);
			assertEquals("Host ID should match", host.getId(), foundHost.getId());
		}

		@Test
		public void testRemoveKnownHostWithWrongAlgorithm() {
			// 1. Add host and known key
			HostBean host = addTestHost("TestHost2", TEST_HOST, TEST_PORT);
			hostDb.saveKnownHost(TEST_HOST, TEST_PORT, TEST_ALGO_1, TEST_KEY_1);

			// 2. Verify key exists
			List<String> algorithms = hostDb.getHostKeyAlgorithmsForHost(TEST_HOST, TEST_PORT);
			assertEquals("Should have 1 algorithm initially", 1, algorithms.size());

			// 3. Attempt to remove with a different algorithm
			hostDb.removeKnownHost(TEST_HOST, TEST_PORT, TEST_ALGO_2, TEST_KEY_2); // Use ALGO_2

			// 4. Verify the original key (ALGO_1) still exists
			algorithms = hostDb.getHostKeyAlgorithmsForHost(TEST_HOST, TEST_PORT);
			assertNotNull("Algorithm list should not be null", algorithms);
			assertEquals("Should still have 1 algorithm", 1, algorithms.size());
			assertEquals("Algorithm should still be the original one", TEST_ALGO_1, algorithms.get(0));
		}

		@Test
		public void testRemoveKnownHostForNonExistentHost() {
			//  Attempt to remove a known host for a host that was never added
			try {
				hostDb.removeKnownHost("nonexistent.host", TEST_PORT, TEST_ALGO_1, TEST_KEY_1);
				// No exception expected
			} catch (Exception e) {
				fail("Should not throw an exception when removing key for non-existent host: " + e.getMessage());
			}

			// Verify no known hosts were somehow created or left over
			// This is a bit indirect, but we can check if any known hosts exist at all
			com.trilead.ssh2.KnownHosts knownHosts = hostDb.getKnownHosts();
			// A more direct check might involve querying the knownhosts table directly if possible,
			// but getKnownHosts() serves as a reasonable proxy.
			// Note: getKnownHosts joins with the hosts table, so if no hosts exist, it might be empty anyway.
			// A better check might be a direct query if test infrastructure allows.
			// For now, primarily rely on the fact that no exception was thrown.
			assertNotNull(knownHosts); // Should return an object, even if empty.
		}

		@Test
		public void testRemoveOneOfMultipleKnownHosts() {
			// 1. Add a host
			HostBean host = addTestHost("TestHost3", TEST_HOST, TEST_PORT);

			// 2. Add two different known host keys for it
			hostDb.saveKnownHost(TEST_HOST, TEST_PORT, TEST_ALGO_1, TEST_KEY_1);
			hostDb.saveKnownHost(TEST_HOST, TEST_PORT, TEST_ALGO_2, TEST_KEY_2);

			// 3. Verify both keys were added
			List<String> algorithms = hostDb.getHostKeyAlgorithmsForHost(TEST_HOST, TEST_PORT);
			assertNotNull("Algorithm list should not be null", algorithms);
			assertEquals("Should have 2 algorithms initially", 2, algorithms.size());
			assertTrue("Should contain ALGO_1", algorithms.contains(TEST_ALGO_1));
			assertTrue("Should contain ALGO_2", algorithms.contains(TEST_ALGO_2));

			// 4. Remove one of the known host keys (ALGO_1)
			hostDb.removeKnownHost(TEST_HOST, TEST_PORT, TEST_ALGO_1, TEST_KEY_1);

			// 5. Verify only the other key (ALGO_2) remains
			algorithms = hostDb.getHostKeyAlgorithmsForHost(TEST_HOST, TEST_PORT);
			assertNotNull("Algorithm list should not be null after removal", algorithms);
			assertEquals("Should have 1 algorithm remaining", 1, algorithms.size());
			assertEquals("Remaining algorithm should be ALGO_2", TEST_ALGO_2, algorithms.get(0));
		}
	}
