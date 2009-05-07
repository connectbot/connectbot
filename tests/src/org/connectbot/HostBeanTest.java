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
package org.connectbot;

import org.connectbot.bean.HostBean;

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
