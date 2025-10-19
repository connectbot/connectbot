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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for database integrity, foreign key constraints, and cascade deletes.
 */
@RunWith(AndroidJUnit4.class)
public class HostDatabaseIntegrityTest {
	private Context context;
	private HostDatabase database;

	@Before
	public void setUp() {
		context = ApplicationProvider.getApplicationContext();
		HostDatabase.resetInMemoryInstance(context);
		database = HostDatabase.get(context);
	}

	@After
	public void tearDown() {
		if (database != null) {
			database.resetDatabase();
		}
	}

	@Test
	public void foreignKeys_Enabled_Success() {
		SQLiteDatabase db = database.getReadableDatabase();
		Cursor cursor = db.rawQuery("PRAGMA foreign_keys", null);

		assertTrue("Cursor should have at least one row", cursor.moveToFirst());
		int foreignKeysEnabled = cursor.getInt(0);
		cursor.close();

		assertEquals("Foreign keys should be enabled", 1, foreignKeysEnabled);
	}

	@Test
	public void portForwardCascadeDelete_HostDeleted_ForwardsDeleted() {
		HostBean host = new HostBean();
		host.setNickname("Test Host");
		host.setUsername("testuser");
		host.setHostname("test.example.com");
		host.setPort(22);
		host = database.saveHost(host);
		assertNotNull("Host should be saved", host);
		assertTrue("Host ID should be valid", host.getId() > 0);

		PortForwardBean portForward1 = new PortForwardBean(
			host.getId(), "Forward 1", HostDatabase.PORTFORWARD_LOCAL,
			"8080", "localhost:80");
		PortForwardBean portForward2 = new PortForwardBean(
			host.getId(), "Forward 2", HostDatabase.PORTFORWARD_DYNAMIC5,
			"1080", "");

		assertTrue("Port forward 1 should be saved", database.savePortForward(portForward1));
		assertTrue("Port forward 2 should be saved", database.savePortForward(portForward2));

		List<PortForwardBean> portForwards = database.getPortForwardsForHost(host);
		assertEquals("Should have 2 port forwards", 2, portForwards.size());

		database.deleteHost(host);

		portForwards = database.getPortForwardsForHost(host);
		assertEquals("Port forwards should be cascade deleted", 0, portForwards.size());
	}

	@Test
	public void knownHostsCascadeDelete_HostDeleted_KnownHostsDeleted() {
		HostBean host = new HostBean();
		host.setNickname("SSH Server");
		host.setUsername("admin");
		host.setHostname("ssh.example.com");
		host.setPort(22);
		host = database.saveHost(host);
		assertNotNull("Host should be saved", host);

		byte[] hostkey = new byte[] { 0x01, 0x02, 0x03, 0x04 };
		database.saveKnownHost("ssh.example.com", 22, "ssh-rsa", hostkey);

		SQLiteDatabase db = database.getReadableDatabase();
		Cursor cursor = db.rawQuery(
			"SELECT COUNT(*) FROM knownhosts WHERE hostid = ?",
			new String[] { String.valueOf(host.getId()) });
		cursor.moveToFirst();
		int knownHostCount = cursor.getInt(0);
		cursor.close();
		assertEquals("Should have 1 known host entry", 1, knownHostCount);

		database.deleteHost(host);

		cursor = db.rawQuery(
			"SELECT COUNT(*) FROM knownhosts WHERE hostid = ?",
			new String[] { String.valueOf(host.getId()) });
		cursor.moveToFirst();
		knownHostCount = cursor.getInt(0);
		cursor.close();
		assertEquals("Known host should be cascade deleted", 0, knownHostCount);
	}

	@Test
	public void multiplePortForwardsCascadeDelete_HostDeleted_AllForwardsDeleted() {
		HostBean host = new HostBean();
		host.setNickname("Multi Forward Host");
		host.setUsername("user");
		host.setHostname("multi.example.com");
		host.setPort(22);
		host = database.saveHost(host);

		int forwardCount = 5;
		for (int i = 0; i < forwardCount; i++) {
			PortForwardBean forward = new PortForwardBean(
				host.getId(),
				"Forward " + i,
				HostDatabase.PORTFORWARD_LOCAL,
				String.valueOf(8080 + i),
				"localhost:" + (80 + i));
			assertTrue("Port forward " + i + " should be saved", database.savePortForward(forward));
		}

		List<PortForwardBean> forwards = database.getPortForwardsForHost(host);
		assertEquals("Should have " + forwardCount + " port forwards", forwardCount, forwards.size());

		database.deleteHost(host);

		forwards = database.getPortForwardsForHost(host);
		assertEquals("All port forwards should be cascade deleted", 0, forwards.size());
	}

	@Test
	public void deleteHost_OneHost_OtherHostsUnaffected() {
		HostBean host1 = new HostBean();
		host1.setNickname("Host 1");
		host1.setUsername("user1");
		host1.setHostname("host1.example.com");
		host1.setPort(22);
		host1 = database.saveHost(host1);

		HostBean host2 = new HostBean();
		host2.setNickname("Host 2");
		host2.setUsername("user2");
		host2.setHostname("host2.example.com");
		host2.setPort(22);
		host2 = database.saveHost(host2);

		PortForwardBean forward1 = new PortForwardBean(
			host1.getId(), "Forward 1", HostDatabase.PORTFORWARD_LOCAL,
			"8080", "localhost:80");
		PortForwardBean forward2 = new PortForwardBean(
			host2.getId(), "Forward 2", HostDatabase.PORTFORWARD_LOCAL,
			"9090", "localhost:90");

		database.savePortForward(forward1);
		database.savePortForward(forward2);

		database.deleteHost(host1);

		List<PortForwardBean> host1Forwards = database.getPortForwardsForHost(host1);
		assertEquals("Host1's forwards should be deleted", 0, host1Forwards.size());

		List<PortForwardBean> host2Forwards = database.getPortForwardsForHost(host2);
		assertEquals("Host2's forwards should still exist", 1, host2Forwards.size());

		HostBean retrievedHost2 = database.findHostById(host2.getId());
		assertNotNull("Host2 should still exist", retrievedHost2);
		assertEquals("Host2 nickname should match", "Host 2", retrievedHost2.getNickname());
	}

	@Test
	public void portForwardsTable_ForeignKey_ExistsToHosts() {
		SQLiteDatabase db = database.getReadableDatabase();
		Cursor cursor = db.rawQuery("PRAGMA foreign_key_list(portforwards)", null);

		boolean hasForeignKey = false;
		while (cursor.moveToNext()) {
			String table = cursor.getString(cursor.getColumnIndexOrThrow("table"));
			String from = cursor.getString(cursor.getColumnIndexOrThrow("from"));
			String to = cursor.getString(cursor.getColumnIndexOrThrow("to"));

			if ("hosts".equals(table) && "hostid".equals(from) && "_id".equals(to)) {
				hasForeignKey = true;
				break;
			}
		}
		cursor.close();

		assertTrue("portforwards table should have foreign key to hosts", hasForeignKey);
	}

	@Test
	public void knownHostsTable_ForeignKey_ExistsToHosts() {
		SQLiteDatabase db = database.getReadableDatabase();
		Cursor cursor = db.rawQuery("PRAGMA foreign_key_list(knownhosts)", null);

		boolean hasForeignKey = false;
		while (cursor.moveToNext()) {
			String table = cursor.getString(cursor.getColumnIndexOrThrow("table"));
			String from = cursor.getString(cursor.getColumnIndexOrThrow("from"));
			String to = cursor.getString(cursor.getColumnIndexOrThrow("to"));

			if ("hosts".equals(table) && "hostid".equals(from) && "_id".equals(to)) {
				hasForeignKey = true;
				break;
			}
		}
		cursor.close();

		assertTrue("knownhosts table should have foreign key to hosts", hasForeignKey);
	}
}
