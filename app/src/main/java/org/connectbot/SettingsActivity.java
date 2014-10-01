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

import org.connectbot.util.PreferenceConstants;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {
	private static final String TAG = "ConnectBot.Settings";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			addPreferencesFromResource(R.xml.preferences);
		} catch (ClassCastException e) {
			Log.e(TAG, "Shared preferences are corrupt! Resetting to default values.");

			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

			// Blow away all the preferences
			SharedPreferences.Editor editor = preferences.edit();
			editor.clear();
			editor.commit();

			PreferenceManager.setDefaultValues(this, R.xml.preferences, true);

			// Since they were able to get to the Settings activity, they already agreed to the EULA
			editor = preferences.edit();
			editor.putBoolean(PreferenceConstants.EULA, true);
			editor.commit();

			addPreferencesFromResource(R.xml.preferences);
		}

		// TODO: add parse checking here to make sure we have integer value for scrollback

	}

}
