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

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PubkeyDatabase;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.IBinder;
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

			cacheValues();
		}

		protected final void cacheValues() {
			// fill a cursor and cache the values locally
			// this makes sure we dont have any floating cursor to dispose later

			SQLiteDatabase db = hostdb.getReadableDatabase();
			Cursor cursor = db.query(table, null, "_id = ?",
					new String[] { String.valueOf(id) }, null, null, null);

			if (cursor.moveToFirst()) {
				for(int i = 0; i < cursor.getColumnCount(); i++) {
					String key = cursor.getColumnName(i);
					if(key.equals(HostDatabase.FIELD_HOST_HOSTKEY)) continue;
					String value = cursor.getString(i);
					values.put(key, value);
				}
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

			// Gingerbread compatibility
			public void apply() {
				commit();
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

			public android.content.SharedPreferences.Editor putStringSet(String key, Set<String> value) {
				throw new UnsupportedOperationException("HostEditor Prefs do not support Set<String>");
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

		public Set<String> getStringSet(String key, Set<String> defValue) {
			throw new ClassCastException("HostEditor Prefs do not support Set<String>");
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

	protected static final String TAG = "ConnectBot.HostEditorActivity";

	protected HostDatabase hostdb = null;
	private PubkeyDatabase pubkeydb = null;

	private CursorPreferenceHack pref;
	private ServiceConnection connection;

	private HostBean host;
	protected TerminalBridge hostBridge;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		long hostId = this.getIntent().getLongExtra(Intent.EXTRA_TITLE, -1);

		// TODO: we could pass through a specific ContentProvider uri here
		//this.getPreferenceManager().setSharedPreferencesName(uri);

		this.hostdb = new HostDatabase(this);
		this.pubkeydb = new PubkeyDatabase(this);

		host = hostdb.findHostById(hostId);

		connection = new ServiceConnection() {
			public void onServiceConnected(ComponentName className, IBinder service) {
				TerminalManager bound = ((TerminalManager.TerminalBinder) service).getService();

				hostBridge = bound.getConnectedBridge(host);
			}

			public void onServiceDisconnected(ComponentName name) {
				hostBridge = null;
			}
		};

		this.pref = new CursorPreferenceHack(HostDatabase.TABLE_HOSTS, hostId);
		this.pref.registerOnSharedPreferenceChangeListener(this);

		this.addPreferencesFromResource(R.xml.host_prefs);

		// add all existing pubkeys to our listpreference for user to choose from
		// TODO: may be an issue here when this activity is recycled after adding a new pubkey
		// TODO: should consider moving into onStart, but we dont have a good way of resetting the listpref after filling once
		ListPreference pubkeyPref = (ListPreference)this.findPreference(HostDatabase.FIELD_HOST_PUBKEYID);

		List<CharSequence> pubkeyNicks = new LinkedList<CharSequence>(Arrays.asList(pubkeyPref.getEntries()));
		pubkeyNicks.addAll(pubkeydb.allValues(PubkeyDatabase.FIELD_PUBKEY_NICKNAME));
		pubkeyPref.setEntries(pubkeyNicks.toArray(new CharSequence[pubkeyNicks.size()]));

		List<CharSequence> pubkeyIds = new LinkedList<CharSequence>(Arrays.asList(pubkeyPref.getEntryValues()));
		pubkeyIds.addAll(pubkeydb.allValues("_id"));
		pubkeyPref.setEntryValues(pubkeyIds.toArray(new CharSequence[pubkeyIds.size()]));

		// Populate the character set encoding list with all available
		final ListPreference charsetPref = (ListPreference) findPreference(HostDatabase.FIELD_HOST_ENCODING);

		if (CharsetHolder.isInitialized()) {
			initCharsetPref(charsetPref);
		} else {
			String[] currentCharsetPref = new String[1];
			currentCharsetPref[0] = charsetPref.getValue();
			charsetPref.setEntryValues(currentCharsetPref);
			charsetPref.setEntries(currentCharsetPref);

			new Thread(new Runnable() {
				public void run() {
					initCharsetPref(charsetPref);
				}
			}).start();
		}

		this.updateSummaries();
	}

	@Override
	public void onStart() {
		super.onStart();

		bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

		if(this.hostdb == null)
			this.hostdb = new HostDatabase(this);

		if(this.pubkeydb == null)
			this.pubkeydb = new PubkeyDatabase(this);
	}

	@Override
	public void onStop() {
		super.onStop();

		unbindService(connection);

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
		for (String key : this.pref.values.keySet()) {
			if(key.equals(HostDatabase.FIELD_HOST_POSTLOGIN)) continue;
			Preference pref = this.findPreference(key);
			if(pref == null) continue;
			if(pref instanceof CheckBoxPreference) continue;
			CharSequence value = this.pref.getString(key, "");

			if (key.equals(HostDatabase.FIELD_HOST_PUBKEYID)) {
				try {
					int pubkeyId = Integer.parseInt((String) value);
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
			} else if (pref instanceof ListPreference) {
				ListPreference listPref = (ListPreference) pref;
				int entryIndex = listPref.findIndexOfValue((String) value);
				if (entryIndex >= 0)
					value = listPref.getEntries()[entryIndex];
			}

			pref.setSummary(value);
		}

	}

	private void initCharsetPref(final ListPreference charsetPref) {
		charsetPref.setEntryValues(CharsetHolder.getCharsetIds());
		charsetPref.setEntries(CharsetHolder.getCharsetNames());
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// update values on changed preference
		this.updateSummaries();

		// Our CursorPreferenceHack always send null keys, so try to set charset anyway
		if (hostBridge != null)
			hostBridge.setCharset(sharedPreferences
					.getString(HostDatabase.FIELD_HOST_ENCODING, HostDatabase.ENCODING_DEFAULT));
	}

	public static class CharsetHolder {
		private static boolean initialized = false;

		private static CharSequence[] charsetIds;
		private static CharSequence[] charsetNames;

		public static CharSequence[] getCharsetNames() {
			if (charsetNames == null)
				initialize();

			return charsetNames;
		}

		public static CharSequence[] getCharsetIds() {
			if (charsetIds == null)
				initialize();

			return charsetIds;
		}

		private synchronized static void initialize() {
			if (initialized)
				return;

			List<CharSequence> charsetIdsList = new LinkedList<CharSequence>();
			List<CharSequence> charsetNamesList = new LinkedList<CharSequence>();

			for (Entry<String, Charset> entry : Charset.availableCharsets().entrySet()) {
				Charset c = entry.getValue();
				if (c.canEncode() && c.isRegistered()) {
					String key = entry.getKey();
					if (key.startsWith("cp")) {
						// Custom CP437 charset changes
						charsetIdsList.add("CP437");
						charsetNamesList.add("CP437");
					}
					charsetIdsList.add(entry.getKey());
					charsetNamesList.add(c.displayName());
				}
			}

			charsetIds = charsetIdsList.toArray(new CharSequence[charsetIdsList.size()]);
			charsetNames = charsetNamesList.toArray(new CharSequence[charsetNamesList.size()]);

			initialized = true;
		}

		public static boolean isInitialized() {
			return initialized;
		}
	}
}
