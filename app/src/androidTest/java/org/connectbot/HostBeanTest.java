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
import org.connectbot.mock.BeanTestCase;

import android.test.AndroidTestCase;

/**
 * @author Kenny Root
 * 
 */
public class HostBeanTest extends AndroidTestCase {
	private static final String[] FIELDS = { "nickname", "username",
			"hostname", "port" };

	HostBean host1;
	HostBean host2;

	@Override
	protected void setUp() throws Exception {
		super.setUp();

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

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testIdEquality() {
		host1.setId(1);
		host2.setId(1);
		assertTrue(host1.equals(host2));
		assertTrue(host1.hashCode() == host2.hashCode());
	}

	public void testIdInequality() {
		host1.setId(1);
		host2.setId(2);
		// HostBeans shouldn't be equal when their IDs are not the same
		assertFalse("HostBeans are equal when their ID is different", host1
				.equals(host2));
		assertFalse("HostBean hash codes are equal when their ID is different",
				host1.hashCode() == host2.hashCode());
	}

	public void testIdEquality2() {
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

	public void testBeanMeetsEqualsContract() {
		BeanTestCase.assertMeetsEqualsContract(HostBean.class, FIELDS);
	}

	public void testBeanMeetsHashCodeContract() {
		BeanTestCase.assertMeetsHashCodeContract(HostBean.class, FIELDS);
	}
}
