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

package org.connectbot.util;

import com.trilead.ssh2.KnownHosts;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Contains information about various SSH hosts, include public hostkey if known
 * from previous sessions.
 * 
 * @author jsharkey
 */
public class HostDatabase extends SQLiteOpenHelper {
	
	public final static String TAG = HostDatabase.class.toString();
	
	public final static String DB_NAME = "hosts";
	public final static int DB_VERSION = 11;
	
	public final static String TABLE_HOSTS = "hosts";
	public final static String FIELD_HOST_NICKNAME = "nickname";
	public final static String FIELD_HOST_USERNAME = "username";
	public final static String FIELD_HOST_HOSTNAME = "hostname";
	public final static String FIELD_HOST_PORT = "port";
	public final static String FIELD_HOST_HOSTKEYALGO = "hostkeyalgo";
	public final static String FIELD_HOST_HOSTKEY = "hostkey";
	public final static String FIELD_HOST_LASTCONNECT = "lastconnect";
	public final static String FIELD_HOST_COLOR = "color";
	public final static String FIELD_HOST_USEKEYS = "usekeys";
	public final static String FIELD_HOST_POSTLOGIN = "postlogin";
	public final static String FIELD_HOST_PUBKEYID = "pubkeyid";

	public final static String COLOR_RED = "red";
	public final static String COLOR_GREEN = "green";
	public final static String COLOR_BLUE = "blue";
	public final static String COLOR_GRAY = "gray";

	public final static long PUBKEYID_NEVER = -2;
	public final static long PUBKEYID_ANY = -1;
	
	public HostDatabase(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE_HOSTS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_HOST_NICKNAME + " TEXT, "
				+ FIELD_HOST_USERNAME + " TEXT, "
				+ FIELD_HOST_HOSTNAME + " TEXT, "
				+ FIELD_HOST_PORT + " INTEGER, "
				+ FIELD_HOST_HOSTKEYALGO + " TEXT, "
				+ FIELD_HOST_HOSTKEY + " BLOB, "
				+ FIELD_HOST_LASTCONNECT + " INTEGER, "
				+ FIELD_HOST_COLOR + " TEXT, "
				+ FIELD_HOST_USEKEYS + " TEXT, "
				+ FIELD_HOST_POSTLOGIN + " TEXT, "
				+ FIELD_HOST_PUBKEYID + " INTEGER DEFAULT " + PUBKEYID_ANY + ")");

		// insert a few sample hosts, none of which probably connect
		//this.createHost(db, "connectbot@bravo", "connectbot", "192.168.254.230", 22, COLOR_GRAY);
		this.createHost(db, "cron@server.example.com", "cron", "server.example.com", 22, COLOR_GRAY, PUBKEYID_ANY);
		this.createHost(db, "backup@example.net", "backup", "example.net", 22, COLOR_BLUE, PUBKEYID_ANY);
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// Versions of the database before the Android Market release will be
		// shot without warning.
		if (oldVersion <= 9) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS);
			onCreate(db);	
		}
		
		if (oldVersion == 10) {
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_PUBKEYID + " INTEGER DEFAULT " + PUBKEYID_ANY);
		}
	}
	
	/**
	 * Touch a specific host to update its "last connected" field.
	 * @param nickname Nickname field of host to update
	 */
	public void touchHost(String nickname) {
		
		SQLiteDatabase db = this.getWritableDatabase();
		long now = System.currentTimeMillis() / 1000;
		
		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_LASTCONNECT, now);
		
		db.update(TABLE_HOSTS, values, FIELD_HOST_NICKNAME + " = ?", new String[] { nickname });
		db.close();
		
	}
	
	/**
	 * Create a new host using the given parameters, and return its new
	 * <code>_id</code> value.
	 */
	public long createHost(SQLiteDatabase db, String nickname, String username, String hostname, int port, String color, long pubkeyId) {
		// create and insert new host
		
		if(db == null) db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_NICKNAME, nickname);
		values.put(FIELD_HOST_USERNAME, username);
		values.put(FIELD_HOST_HOSTNAME, hostname);
		values.put(FIELD_HOST_PORT, port);
		values.put(FIELD_HOST_LASTCONNECT, 0);
		values.put(FIELD_HOST_USEKEYS, Boolean.toString(true));
		if(color != null)
			values.put(FIELD_HOST_COLOR, color);
		values.put(FIELD_HOST_PUBKEYID, pubkeyId);
		
		return db.insert(TABLE_HOSTS, null, values);
		
	}
	
	/**
	 * Delete a specific host by its <code>_id</code> value.
	 */
	public void deleteHost(long id) {
		
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_HOSTS, "_id = ?", new String[] { Long.toString(id) });
		
	}
	
	/**
	 * Return a cursor that contains information about all known hosts.
	 * @param sortColors If true, sort by color, otherwise sort by nickname.
	 */
	public Cursor allHosts(boolean sortColors) {
		
		String sortField = sortColors ? FIELD_HOST_COLOR : FIELD_HOST_NICKNAME;
		
		SQLiteDatabase db = this.getReadableDatabase();
		return db.query(TABLE_HOSTS, new String[] { "_id", FIELD_HOST_NICKNAME,
				FIELD_HOST_USERNAME, FIELD_HOST_HOSTNAME, FIELD_HOST_PORT,
				FIELD_HOST_LASTCONNECT, FIELD_HOST_COLOR },
				null, null, null, null, sortField + " ASC");
		
	}
	
	/**
	 * Find the post-login command string for the given nickname.
	 */
	public String getPostLogin(String nickname) {
		
		String result = null;
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor c = db.query(TABLE_HOSTS, new String[] { FIELD_HOST_POSTLOGIN },
				FIELD_HOST_NICKNAME + " = ?", new String[] { nickname }, null, null, null);
		if(c == null || !c.moveToFirst()) {
			result = null;
		} else {
			result = c.getString(c.getColumnIndexOrThrow(FIELD_HOST_POSTLOGIN));
		}
		
		return result;
		
	}
	
	/**
	 * Record the given hostkey into database under this nickname.
	 */
	public void saveKnownHost(String hostname, String hostkeyalgo, byte[] hostkey) {

		SQLiteDatabase db = this.getReadableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_HOSTKEYALGO, hostkeyalgo);
		values.put(FIELD_HOST_HOSTKEY, hostkey);
		
		db.update(TABLE_HOSTS, values, FIELD_HOST_HOSTNAME + " = ?", new String[] { hostname });
		Log.d(TAG, String.format("Finished saving hostkey information for '%s'", hostname));
		
	}
	
	/**
	 * Build list of known hosts for Trilead library.
	 */
	public KnownHosts getKnownHosts() {
		KnownHosts known = new KnownHosts();
		
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor c = db.query(TABLE_HOSTS, new String[] { FIELD_HOST_HOSTNAME,
				FIELD_HOST_HOSTKEYALGO, FIELD_HOST_HOSTKEY }, null, null, null,
				null, null);
		if(c == null) return null;
		
		int COL_HOSTNAME = c.getColumnIndexOrThrow(FIELD_HOST_HOSTNAME),
			COL_HOSTKEYALGO = c.getColumnIndexOrThrow(FIELD_HOST_HOSTKEYALGO),
			COL_HOSTKEY = c.getColumnIndexOrThrow(FIELD_HOST_HOSTKEY);
		
		while(c.moveToNext()) {
			String hostname = c.getString(COL_HOSTNAME),
				hostkeyalgo = c.getString(COL_HOSTKEYALGO);
			byte[] hostkey = c.getBlob(COL_HOSTKEY);
			
			if(hostkeyalgo == null || hostkeyalgo.length() == 0) continue;
			if(hostkey == null || hostkey.length == 0) continue;
			
			try {
				known.addHostkey(new String[] { hostname }, hostkeyalgo, hostkey);
			} catch(Exception e) {
				Log.e(TAG, "Problem while adding a known host from database", e);
			}
			
		}
		
		return known;
	}
	
	/**
	 * Find the pubkey to use for a given nickname.
	 */
	public long getPubkeyId(String nickname) {	
		long result = PUBKEYID_ANY;

		SQLiteDatabase db = this.getReadableDatabase();
		Cursor c = db.query(TABLE_HOSTS, new String[] { FIELD_HOST_PUBKEYID },
				FIELD_HOST_NICKNAME + " = ?", new String[] { nickname }, null, null, null);
		
		if (c != null && c.moveToFirst())
			result = c.getLong(0);
		
		return result;
	}
	
	public void setPubkeyId(long hostId, long pubkeyId) {
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_PUBKEYID, pubkeyId);
		
		db.update(TABLE_HOSTS, values, "_id = ?", new String[] { String.valueOf(hostId) });
		db.close();

		Log.d(TAG, String.format("Updated host id %d to use pubkey id %d", hostId, pubkeyId));
	}
	
	/**
	 * Unset any hosts using a pubkey that has been deleted.
	 */
	public void stopUsingPubkey(long pubkeyId) {
		if (pubkeyId < 0) return;
		
		SQLiteDatabase db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_PUBKEYID, PUBKEYID_ANY);
		
		db.update(TABLE_HOSTS, values, FIELD_HOST_PUBKEYID + " = ?", new String[] { String.valueOf(pubkeyId) });
		db.close();
		
		Log.d(TAG, String.format("Set all hosts using pubkey id %d to -1", pubkeyId));
	}
}
