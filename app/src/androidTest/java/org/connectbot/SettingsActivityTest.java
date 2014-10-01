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

import android.test.ActivityInstrumentationTestCase2;

/**
 * This is a simple framework for a test of an Application. See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more
 * information on how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class org.connectbot.HostListActivityTest \
 * org.connectbot.tests/android.test.InstrumentationTestRunner
 */
public class SettingsActivityTest extends
		ActivityInstrumentationTestCase2<SettingsActivity> {

	public SettingsActivityTest() {
		super("org.connectbot", SettingsActivity.class);
	}

	public void testOpenMenu() {
		SettingsActivity a = getActivity();

		a.openOptionsMenu();

		a.closeOptionsMenu();
	}

}
