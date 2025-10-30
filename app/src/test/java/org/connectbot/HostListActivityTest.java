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

package org.connectbot;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostDatabase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;

import android.app.Application;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.shadows.ShadowLooper.idleMainLooper;

@RunWith(AndroidJUnit4.class)
public class HostListActivityTest {

	private HostDatabase mockHostDb;
	private TerminalManager mockTerminalManager;
	private ActivityController<HostListActivity> activityController;
	private HostListActivity activity;

	// Define test host details consistently
	private static final long TEST_HOST_ID = 1L;
	private static final String TEST_HOST_NICKNAME = "TestSSHHost";
	private static final String TEST_HOST_HOSTNAME = "testhost.example.com";
	private static final int TEST_HOST_PORT = 22;
	private static final String TEST_HOST_PROTOCOL = "ssh";
	private static final String TEST_ALGO_1 = "ssh-rsa";
	private static final String TEST_ALGO_2 = "ecdsa-sha2-nistp256";


	@Before
	public void setUp() {
		// --- Mock HostDatabase ---
		mockHostDb = mock(HostDatabase.class);
		// Provide a default HostBean when findHostById is called
		HostBean testHost = createTestHostBean();
		when(mockHostDb.findHostById(TEST_HOST_ID)).thenReturn(testHost);
		// Simulate getHosts returning our test host
		List<HostBean> hosts = new ArrayList<>();
		hosts.add(testHost);
		when(mockHostDb.getHosts(anyBoolean())).thenReturn(hosts);
		// Simulate initial known host keys
		when(mockHostDb.getHostKeyAlgorithmsForHost(TEST_HOST_HOSTNAME, TEST_HOST_PORT))
				.thenReturn(new ArrayList<>(Arrays.asList(TEST_ALGO_1, TEST_ALGO_2))); // Initial state
		// We'll change this later to simulate removal

		// --- Mock TerminalManager ---
		mockTerminalManager = spy(TerminalManager.class); //use spy to verify interactions
		when(mockTerminalManager.getBridges()).thenReturn(new ArrayList<>()); // Start with no bridges
		when(mockTerminalManager.getConnectedBridge(any(HostBean.class))).thenReturn(null); // Default to not connected
		mockBindToService(mockTerminalManager);

		// --- Create Activity ---
		activityController = Robolectric.buildActivity(HostListActivity.class);
		activity = activityController.create().start().get();

		idleMainLooper(); // Ensure UI updates are processed
	}


	// Helper to create a consistent HostBean
	private HostBean createTestHostBean() {
		HostBean host = new HostBean();
		host.setId(TEST_HOST_ID);
		host.setNickname(TEST_HOST_NICKNAME);
		host.setHostname(TEST_HOST_HOSTNAME);
		host.setPort(TEST_HOST_PORT);
		host.setProtocol(TEST_HOST_PROTOCOL); // Important for enabling the menu item
		return host;
	}

	private void mockBindToService(TerminalManager terminalManager) {
		TerminalManager.TerminalBinder stubBinder = mock(TerminalManager.TerminalBinder.class);
		when(stubBinder.getService()).thenReturn(terminalManager);
		shadowOf((Application) ApplicationProvider.getApplicationContext()).setComponentNameAndServiceForBindService(new ComponentName("org.connectbot", TerminalManager.class.getName()), stubBinder);
	}

	@Test
	public void bindsToTerminalManager() {
		TerminalManager terminalManager = spy(TerminalManager.class);
		mockBindToService(terminalManager);

		HostListActivity activity = Robolectric.buildActivity(HostListActivity.class).create().start().get();

		Intent serviceIntent = new Intent(activity, TerminalManager.class);
		Intent actualIntent = shadowOf(activity).getNextStartedService();

		assertTrue(actualIntent.filterEquals(serviceIntent));
	}

	@Test
	public void testResetKnownHostKey_MenuItemClick_ConfirmDialog_RemovesKeys() {
		// Force mockHostDb to return 1 SSH host
		HostBean sshHost = createTestHostBean(); // protocol = "ssh"
		when(mockHostDb.getHosts(anyBoolean())).thenReturn(List.of(sshHost));

		// 0. Inject the mockHostDb into the activity
		Field hostdbField = null;
		try {
			hostdbField = HostListActivity.class.getDeclaredField("hostdb");
			hostdbField.setAccessible(true);
			hostdbField.set(activity, mockHostDb);
		} catch (NoSuchFieldException| IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		// Refresh list to repopulate adapter with mock data
		activity.updateList();
		idleMainLooper();
		// 1. Get ViewHolder
		RecyclerView recyclerView = activity.findViewById(R.id.list);
		RecyclerView.Adapter adapter = recyclerView.getAdapter();
		assertNotNull("Adapter should not be null", adapter);
		assertEquals("Expected exactly 1 host", 1, adapter.getItemCount());

		RecyclerView.ViewHolder rawHolder = adapter.createViewHolder(recyclerView, 0);
		adapter.bindViewHolder(rawHolder, 0);

		HostListActivity.HostViewHolder viewHolder = (HostListActivity.HostViewHolder) rawHolder;
		//HostListActivity.HostViewHolder viewHolder = (HostListActivity.HostViewHolder) recyclerView.findViewHolderForAdapterPosition(0);
		assertNotNull("ViewHolder should not be null", viewHolder);
		View hostItemView = viewHolder.itemView;

		// 2. Mock ContextMenu and the target MenuItem
		ContextMenu mockContextMenu = mock(ContextMenu.class);
		MenuItem mockResetMenuItem = mock(MenuItem.class);
		// Optional: Set item ID if needed elsewhere, although not strictly necessary for listener capture
		when(mockResetMenuItem.getItemId()).thenReturn(R.string.list_host_reset_knownhost);

		// Prepare to capture the listener
		ArgumentCaptor<MenuItem.OnMenuItemClickListener> listenerCaptor =
				ArgumentCaptor.forClass(MenuItem.OnMenuItemClickListener.class);

		// When add() is called for our item ID, return the specific mock
		// and expect setOnMenuItemClickListener to be called on it.
		when(mockContextMenu.add(eq(R.string.list_host_reset_knownhost)))
				.thenReturn(mockResetMenuItem);
		// Ensure setOnMenuItemClickListener is verified *on the specific mock*
		// We capture the listener when it's set.
		when(mockResetMenuItem.setOnMenuItemClickListener(listenerCaptor.capture()))
				.thenReturn(mockResetMenuItem); // Return the mock itself

		when(mockContextMenu.add(eq(R.string.list_host_disconnect)))
				.thenReturn(mock(MenuItem.class)); // Return a generic mock MenuItem

		//  add stubbing for the 'edit' item
		when(mockContextMenu.add(eq(R.string.list_host_edit)))
				.thenReturn(mock(MenuItem.class));
		when(mockContextMenu.add(eq(R.string.list_host_portforwards))) // forwards
				.thenReturn(mock(MenuItem.class));
		when(mockContextMenu.add(eq(R.string.list_host_delete))) //delete
				.thenReturn(mock(MenuItem.class));

		// 3. Invoke the real onCreateContextMenu from the ViewHolder
		viewHolder.onCreateContextMenu(mockContextMenu, hostItemView, null);

		// 4. Verify the listener was set (and thus captured) on our mock item
		// This implicitly checks if the capture happened. If not, capture() below would fail.
		verify(mockResetMenuItem).setOnMenuItemClickListener(any(MenuItem.OnMenuItemClickListener.class));


		// Retrieve the captured listener
		MenuItem.OnMenuItemClickListener actualListener = listenerCaptor.getValue();
		assertNotNull("Reset menu item listener should have been captured", actualListener);

		// 5. Invoke the captured listener directly
		boolean handled = actualListener.onMenuItemClick(mockResetMenuItem); // Pass the mock item
		assertTrue("Listener's onMenuItemClick should return true", handled); // Verify listener returns true
		idleMainLooper(); // Allow UI thread operations (like showing the dialog) to process


		// 6. Verify the AlertDialog is shown
		AlertDialog latestAlertDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
		assertNotNull("Alert dialog should be shown", latestAlertDialog);
		assertTrue("Alert dialog should be showing", latestAlertDialog.isShowing());

		// 7. Simulate clicking the positive button
		verify(mockHostDb, never()).removeKnownHost(anyString(), anyInt(), anyString(), any(byte[].class)); // Verify not called yet
		latestAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
		idleMainLooper(); // Process the click handler

		// 8. Verify the results

		// Verify getHostKeyAlgorithmsForHost was called
		verify(mockHostDb, times(1)).getHostKeyAlgorithmsForHost(eq(TEST_HOST_HOSTNAME), eq(TEST_HOST_PORT));

		// Verify removeKnownHost was called for each algorithm
		verify(mockHostDb, times(1)).removeKnownHost(eq(TEST_HOST_HOSTNAME), eq(TEST_HOST_PORT), eq(TEST_ALGO_1), any(byte[].class));
		verify(mockHostDb, times(1)).removeKnownHost(eq(TEST_HOST_HOSTNAME), eq(TEST_HOST_PORT), eq(TEST_ALGO_2), any(byte[].class));

		assertFalse("Dialog should be dismissed", latestAlertDialog.isShowing());
	}


	// --- Test for disabled state ---
	@Test
	public void testResetKnownHostKey_DisabledForNonSshHost() {
		// Arrange: Modify the mock DB to return a non-SSH host for this test
		HostBean nonSshHost = createTestHostBean();
		nonSshHost.setProtocol("telnet"); // Non-SSH protocol
		// Make getHosts return only this non-SSH host for this specific test run
		when(mockHostDb.getHosts(anyBoolean())).thenReturn(Arrays.asList(nonSshHost));
		// Inject the mockHostDb into the activity
		Field hostdbField = null;
		try {
			hostdbField = HostListActivity.class.getDeclaredField("hostdb");
			hostdbField.setAccessible(true);
			hostdbField.set(activity, mockHostDb);
		} catch (NoSuchFieldException| IllegalAccessException e) {
			throw new RuntimeException(e);
		}

		// Re-setup the activity or adapter with the new host data
		activity.updateList(); // Assuming updateList refreshes the adapter
		idleMainLooper();

		// Get ViewHolder
		RecyclerView recyclerView = activity.findViewById(R.id.list);
		RecyclerView.Adapter adapter = recyclerView.getAdapter();
		assertNotNull("Adapter should not be null", adapter);
		assertEquals("Expected exactly 1 host", 1, adapter.getItemCount());

		RecyclerView.ViewHolder rawHolder = adapter.createViewHolder(recyclerView, 0);
		adapter.bindViewHolder(rawHolder, 0);

		HostListActivity.HostViewHolder viewHolder = (HostListActivity.HostViewHolder) rawHolder;
		assertNotNull("ViewHolder should not be null for non-SSH host", viewHolder);
		assertEquals("ViewHolder should represent non-SSH host", nonSshHost.getNickname(), ((TextView)viewHolder.itemView.findViewById(android.R.id.text1)).getText());
		View hostItemView = viewHolder.itemView;

		// Act:
		// Mock the ContextMenu interaction specifically for this check
		ContextMenu mockContextMenu = mock(ContextMenu.class);
		// Mock the specific MenuItem that should be added and disabled
		MenuItem mockResetMenuItem = mock(MenuItem.class);

		// When add() is called with the specific resource ID, return our mocked MenuItem
		when(mockContextMenu.add(eq(R.string.list_host_reset_knownhost)))
				.thenReturn(mockResetMenuItem);

		// Return generic mocks for other menu items
		when(mockContextMenu.add(eq(R.string.list_host_disconnect)))
				.thenReturn(mock(MenuItem.class));
		//  add stubbing for the 'edit' item
		when(mockContextMenu.add(eq(R.string.list_host_edit)))
				.thenReturn(mock(MenuItem.class));
		when(mockContextMenu.add(eq(R.string.list_host_portforwards))) // forwards
				.thenReturn(mock(MenuItem.class));
		when(mockContextMenu.add(eq(R.string.list_host_delete))) //delete
				.thenReturn(mock(MenuItem.class));

		// Invoke the real onCreateContextMenu method from the ViewHolder instance,
		// passing in our mocked ContextMenu.
		viewHolder.onCreateContextMenu(mockContextMenu, hostItemView, null);

		// Assert:
		// Verify that setEnabled(false) was called on our specific mocked MenuItem.
		verify(mockResetMenuItem).setEnabled(eq(false));
	}
}