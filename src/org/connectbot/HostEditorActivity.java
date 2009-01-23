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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.connectbot.util.HostDatabase;
import org.connectbot.util.PubkeyDatabase;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

public class HostEditorActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	public class CursorPreferenceHack implements SharedPreferences {
		protected final String table;
		protected final long id;

		protected Map<String, String> values = new HashMap<String, String>();
//		protected Map<String, String> pubkeys = new HashMap<String, String>();

		public CursorPreferenceHack(String table, long id) {
			this.table = table;
			this.id = id;

			this.cacheValues();

		}

		protected void cacheValues() {
			// fill a cursor and cache the values locally
			// this makes sure we dont have any floating cursor to dispose later

			SQLiteDatabase db = hostdb.getReadableDatabase();
			Cursor cursor = db.query(table, null, "_id = ?",
					new String[] { String.valueOf(id) }, null, null, null);
			cursor.moveToFirst();

			for(int i = 0; i < cursor.getColumnCount(); i++) {
				String key = cursor.getColumnName(i);
				if(key.equals(HostDatabase.FIELD_HOST_HOSTKEY)) continue;
				String value = cursor.getString(i);
				values.put(key, value);
			}

			cursor.close();
			db.close();

//			db = pubkeydb.getReadableDatabase();
//			cursor = db.query(PubkeyDatabase.TABLE_PUBKEYS,
//					new String[] { "_id", PubkeyDatabase.FIELD_PUBKEY_NICKNAME },
//					null, null, null, null, null);
//
//			if (cursor.moveToFirst()) {
//				do {
//					String pubkeyid = String.valueOf(cursor.getLong(0));
//					String value = cursor.getString(1);
//					pubkeys.put(pubkeyid, value);
//				} while (cursor.moveToNext());
//			}
//
//			cursor.close();
//			db.close();
		}

		public boolean contains(String key) {
			return values.containsKey(key);
		}

		public class Editor implements SharedPreferences.Editor {

			private ContentValues update = new ContentValues();

			public SharedPreferences.Editor clear() {
				Log.d(this.getClass().toString(), "clear()");
				update = new ContentValues();
				return this;
			}

			public boolean commit() {
				//Log.d(this.getClass().toString(), "commit() changes back to database");
				SQLiteDatabase db = hostdb.getWritableDatabase();
				db.update(table, update, "_id = ?", new String[] { String.valueOf(id) });
				db.close();

				// make sure we refresh the parent cached values
				cacheValues();

				// and update any listeners
				for(OnSharedPreferenceChangeListener listener : listeners) {
					listener.onSharedPreferenceChanged(CursorPreferenceHack.this, null);
				}

				return true;
			}

			public android.content.SharedPreferences.Editor putBoolean(String key, boolean value) {
				return this.putString(key, Boolean.toString(value));
			}

			public android.content.SharedPreferences.Editor putFloat(String key, float value) {
				return this.putString(key, Float.toString(value));
			}

			public android.content.SharedPreferences.Editor putInt(String key, int value) {
				return this.putString(key, Integer.toString(value));
			}

			public android.content.SharedPreferences.Editor putLong(String key, long value) {
				return this.putString(key, Long.toString(value));
			}

			public android.content.SharedPreferences.Editor putString(String key, String value) {
				//Log.d(this.getClass().toString(), String.format("Editor.putString(key=%s, value=%s)", key, value));
				update.put(key, value);
				return this;
			}

			public android.content.SharedPreferences.Editor remove(String key) {
				//Log.d(this.getClass().toString(), String.format("Editor.remove(key=%s)", key));
				update.remove(key);
				return this;
			}

		}


		public Editor edit() {
			//Log.d(this.getClass().toString(), "edit()");
			return new Editor();
		}

		public Map<String, ?> getAll() {
			return values;
		}

		public boolean getBoolean(String key, boolean defValue) {
			return Boolean.valueOf(this.getString(key, Boolean.toString(defValue)));
		}

		public float getFloat(String key, float defValue) {
			return Float.valueOf(this.getString(key, Float.toString(defValue)));
		}

		public int getInt(String key, int defValue) {
			return Integer.valueOf(this.getString(key, Integer.toString(defValue)));
		}

		public long getLong(String key, long defValue) {
			return Long.valueOf(this.getString(key, Long.toString(defValue)));
		}

		public String getString(String key, String defValue) {
			//Log.d(this.getClass().toString(), String.format("getString(key=%s, defValue=%s)", key, defValue));

			if(!values.containsKey(key)) return defValue;
			return values.get(key);
		}

		protected List<OnSharedPreferenceChangeListener> listeners = new LinkedList<OnSharedPreferenceChangeListener>();

		public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
			listeners.add(listener);
		}

		public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
			listeners.remove(listener);
		}

	}


	@Override
	public SharedPreferences getSharedPreferences(String name, int mode) {
		//Log.d(this.getClass().toString(), String.format("getSharedPreferences(name=%s)", name));
		return this.pref;
	}

	protected HostDatabase hostdb = null;
	private PubkeyDatabase pubkeydb = null;

	private CursorPreferenceHack pref;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		long id = this.getIntent().getLongExtra(Intent.EXTRA_TITLE, -1);

		// TODO: we could pass through a specific ContentProvider uri here
		//this.getPreferenceManager().setSharedPreferencesName(uri);

		this.hostdb = new HostDatabase(this);
		this.pubkeydb = new PubkeyDatabase(this);

		this.pref = new CursorPreferenceHack(HostDatabase.TABLE_HOSTS, id);
		this.pref.registerOnSharedPreferenceChangeListener(this);

		this.addPreferencesFromResource(R.xml.host_prefs);

		// add all existing pubkeys to our listpreference for user to choose from
		// TODO: may be an issue here when this activity is recycled after adding a new pubkey
		// TODO: should consider moving into onStart, but we dont have a good way of resetting the listpref after filling once
		ListPreference pubkeyPref = (ListPreference)this.findPreference(HostDatabase.FIELD_HOST_PUBKEYID);

		List<CharSequence> pubkeyNicks = new LinkedList<CharSequence>(Arrays.asList(pubkeyPref.getEntries()));
		pubkeyNicks.addAll(pubkeydb.allValues(PubkeyDatabase.FIELD_PUBKEY_NICKNAME));
		pubkeyPref.setEntries((CharSequence[]) pubkeyNicks.toArray(new CharSequence[pubkeyNicks.size()]));

		List<CharSequence> pubkeyIds = new LinkedList<CharSequence>(Arrays.asList(pubkeyPref.getEntryValues()));
		pubkeyIds.addAll(pubkeydb.allValues("_id"));
		pubkeyPref.setEntryValues((CharSequence[]) pubkeyIds.toArray(new CharSequence[pubkeyIds.size()]));

		this.updateSummaries();
	}

	public void onStart() {
		super.onStart();
		if(this.hostdb == null)
			this.hostdb = new HostDatabase(this);

		if(this.pubkeydb == null)
			this.pubkeydb = new PubkeyDatabase(this);

	}

	public void onStop() {
		super.onStop();
		if(this.hostdb != null) {
			this.hostdb.close();
			this.hostdb = null;
		}

		if(this.pubkeydb != null) {
			this.pubkeydb.close();
			this.pubkeydb = null;
		}
	}

	private void updateSummaries() {
		// for all text preferences, set hint as current database value
		for(String key : this.pref.values.keySet()) {
			if(key.equals("postlogin")) continue;
			Preference pref = this.findPreference(key);
			if(pref == null) continue;
			if(pref instanceof CheckBoxPreference) continue;
			String value = this.pref.getString(key, "");

			if(key.equals("pubkeyid")) {
				try {
					int pubkeyId = Integer.parseInt(value);
					if (pubkeyId >= 0)
						pref.setSummary(pubkeydb.getNickname(pubkeyId));
					else if(pubkeyId == HostDatabase.PUBKEYID_ANY)
						pref.setSummary(R.string.list_pubkeyids_any);
					else if(pubkeyId == HostDatabase.PUBKEYID_NEVER)
						pref.setSummary(R.string.list_pubkeyids_none);
					continue;
				} catch (NumberFormatException nfe) {
					// Fall through.
				}
			}

			pref.setSummary(value);
		}

	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// update values on changed preference
		this.updateSummaries();

	}

}
