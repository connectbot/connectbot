package org.connectbot.util;

import java.util.LinkedList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class HostDatabase extends SQLiteOpenHelper {
	
	public final static String DB_NAME = "hosts";
	public final static int DB_VERSION = 4;
	
	public final static String TABLE_HOSTS = "hosts";
	public final static String FIELD_HOST_NICKNAME = "nickname";
	public final static String FIELD_HOST_USERNAME = "username";
	public final static String FIELD_HOST_HOSTNAME = "hostname";
	public final static String FIELD_HOST_PORT = "port";
	public final static String FIELD_HOST_HOSTKEY = "hostkey";
	public final static String FIELD_HOST_LASTCONNECT = "lastconnect";
	public final static String FIELD_HOST_COLOR = "color";

	public final static String TABLE_PRIVKEYS = "keys";
	public final static String FIELD_KEY_NAME = "name";
	public final static String FIELD_KEY_PRIVATE = "private";
	
	public final static String COLOR_RED = "red";
	public final static String COLOR_GREEN = "green";
	public final static String COLOR_BLUE = "blue";

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
				+ FIELD_HOST_COLOR + " TEXT)");

		db.execSQL("CREATE TABLE " + TABLE_PRIVKEYS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_KEY_NAME + " TEXT, "
				+ FIELD_KEY_PRIVATE + " TEXT)");
		
		this.createHost(db, "connectbot@bravo", "connectbot", "192.168.254.230", 22, COLOR_RED);
		this.createHost(db, "root@google.com", "root", "google.com", 22, COLOR_GREEN);
		this.createHost(db, "cron@server.example.com", "cron", "server.example.com", 22, COLOR_BLUE);
		this.createHost(db, "backup@example.net", "backup", "example.net", 22, COLOR_RED);
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRIVKEYS);
		onCreate(db);
	}
	
	public long createHost(SQLiteDatabase db, String nickname, String username, String hostname, int port, String color) {
		// create and insert new host
		
		if(db == null) db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_NICKNAME, nickname);
		values.put(FIELD_HOST_USERNAME, username);
		values.put(FIELD_HOST_HOSTNAME, hostname);
		values.put(FIELD_HOST_PORT, port);
		values.put(FIELD_HOST_LASTCONNECT, Integer.MAX_VALUE);
		if(color != null)
			values.put(FIELD_HOST_COLOR, color);
		
		return db.insert(TABLE_HOSTS, null, values);
		
	}
	
	public Cursor allHosts(boolean sortColors) {
		
		String sortField = sortColors ? FIELD_HOST_COLOR : FIELD_HOST_LASTCONNECT;
		
		SQLiteDatabase db = this.getReadableDatabase();
		return db.query(TABLE_HOSTS, new String[] { "_id", FIELD_HOST_NICKNAME,
				FIELD_HOST_USERNAME, FIELD_HOST_HOSTNAME, FIELD_HOST_PORT,
				FIELD_HOST_HOSTKEY, FIELD_HOST_LASTCONNECT, FIELD_HOST_COLOR },
				null, null, null, null, sortField + " DESC");
		
	}
	
	
}
