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

import java.util.LinkedList;
import java.util.List;

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
	
	public final static String DB_NAME = "hosts";
	public final static int DB_VERSION = 8;
	
	public final static String TABLE_HOSTS = "hosts";
	public final static String FIELD_HOST_NICKNAME = "nickname";
	public final static String FIELD_HOST_USERNAME = "username";
	public final static String FIELD_HOST_HOSTNAME = "hostname";
	public final static String FIELD_HOST_PORT = "port";
	public final static String FIELD_HOST_HOSTKEY = "hostkey";
	public final static String FIELD_HOST_LASTCONNECT = "lastconnect";
	public final static String FIELD_HOST_COLOR = "color";
	public final static String FIELD_HOST_USEKEYS = "usekeys";

	public final static String COLOR_RED = "red";
	public final static String COLOR_GREEN = "green";
	public final static String COLOR_BLUE = "blue";
	public final static String COLOR_GRAY = "gray";

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
				+ FIELD_HOST_HOSTKEY + " TEXT, "
				+ FIELD_HOST_LASTCONNECT + " INTEGER, "
				+ FIELD_HOST_COLOR + " TEXT, "
				+ FIELD_HOST_USEKEYS + " TEXT)");

		// insert a few sample hosts, none of which probably connect
		this.createHost(db, "connectbot@bravo", "connectbot", "192.168.254.230", 22, null);
		this.createHost(db, "root@google.com", "root", "google.com", 22, null);
		this.createHost(db, "cron@server.example.com", "cron", "server.example.com", 22, COLOR_BLUE);
		this.createHost(db, "backup@example.net", "backup", "example.net", 22, COLOR_BLUE);
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS);
		onCreate(db);
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
	public long createHost(SQLiteDatabase db, String nickname, String username, String hostname, int port, String color) {
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
				FIELD_HOST_HOSTKEY, FIELD_HOST_LASTCONNECT, FIELD_HOST_COLOR },
				null, null, null, null, sortField + " ASC");
		
	}
	
	
}
