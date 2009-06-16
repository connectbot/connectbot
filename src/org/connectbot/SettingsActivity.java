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
