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

package org.connectbot;

import org.connectbot.bean.HostBean;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.connectbot.mock.BeanAssertions.assertMeetsEqualsContract;
import static org.connectbot.mock.BeanAssertions.assertMeetsHashCodeContract;

/**
 * @author Kenny Root
 */
@RunWith(AndroidJUnit4.class)
public class HostBeanTest {
	private static final String[] FIELDS = { "nickname", "username",
			"hostname", "port" };

	private HostBean host1;
	private HostBean host2;

	@Before
	public void setUp() {
		host1 = new HostBean();
		host1.setNickname("Home");
		host1.setUsername("bob");
		host1.setHostname("server.example.com");
		host1.setPort(22);

		host2 = new HostBean();
		host2.setNickname("Home");
		host2.setUsername("bob");
		host2.setHostname("server.example.com");
		host2.setPort(22);
	}

	@Test
	public void id_Equality() {
		host1.setId(1);
		host2.setId(1);
		assertTrue(host1.equals(host2));
		assertTrue(host1.hashCode() == host2.hashCode());
	}

	@Test
	public void id_Inequality() {
		host1.setId(1);
		host2.setId(2);
		// HostBeans shouldn't be equal when their IDs are not the same
		assertFalse("HostBeans are equal when their ID is different", host1
				.equals(host2));
		assertFalse("HostBean hash codes are equal when their ID is different",
				host1.hashCode() == host2.hashCode());
	}

	@Test
	public void id_Equality2() {
		host1.setId(1);
		host2.setId(1);
		host2.setNickname("Work");
		host2.setUsername("alice");
		host2.setHostname("client.example.com");
		assertTrue(
				"HostBeans are not equal when their ID is the same but other fields are different!",
				host1.equals(host2));
		assertTrue(
				"HostBeans hashCodes are not equal when their ID is the same but other fields are different!",
				host1.hashCode() == host2.hashCode());
	}

	@Test
	public void equals_Empty_Success() {
		HostBean bean1 = new HostBean();
		HostBean bean2 = new HostBean();
		assertEquals(bean1, bean2);
	}

	@Test
	public void equals_NicknameDifferent_Failure() {
		host1.setNickname("Work");
		assertNotEquals(host1, host2);
	}

	@Test
	public void equals_NicknameNull_Failure() {
		host1.setNickname(null);
		assertNotEquals(host1, host2);
	}

	@Test
	public void equals_ProtocolNull_Failure() {
		host1.setProtocol(null);
		assertNotEquals(host1, host2);
	}

	@Test
	public void equals_ProtocolDifferent_Failure() {
		host1.setProtocol("fake");
		assertNotEquals(host1, host2);
	}

	@Test
	public void equals_UserDifferent_Failure() {
		host1.setUsername("joe");
		assertNotEquals(host1, host2);
	}

	@Test
	public void equals_UserNull_Failure() {
		host1.setUsername(null);
		assertNotEquals(host1, host2);
	}

	@Test
	public void equals_HostDifferent_Failure() {
		host1.setHostname("work.example.com");
		assertNotEquals(host1, host2);
	}

	@Test
	public void equals_HostNull_Failure() {
		host1.setHostname(null);
		assertNotEquals(host1, host2);
	}

	@Test
	public void testBeanMeetsEqualsContract() {
		assertMeetsEqualsContract(HostBean.class, FIELDS);
	}

	@Test
	public void testBeanMeetsHashCodeContract() {
		assertMeetsHashCodeContract(HostBean.class, FIELDS);
	}
}
