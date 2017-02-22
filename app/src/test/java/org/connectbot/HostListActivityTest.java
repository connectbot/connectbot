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

import org.connectbot.service.TerminalManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.ComponentName;
import android.content.Intent;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 22)
public class HostListActivityTest {
	private void mockBindToService(TerminalManager terminalManager) {
		TerminalManager.TerminalBinder stubBinder = mock(TerminalManager.TerminalBinder.class);
		when(stubBinder.getService()).thenReturn(terminalManager);
		shadowOf(RuntimeEnvironment.application).setComponentNameAndServiceForBindService(new ComponentName("org.connectbot", TerminalManager.class.getName()), stubBinder);
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
}