/*
 * Copyright (C) 2007 Kenny Root (kenny at the-b.org)
 * 
 * This file is part of Connectbot.
 *
 *  Connectbot is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Connectbot is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Connectbot.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.theb.ssh;

import java.util.HashMap;

import org.theb.provider.HostDb;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class HostDbProvider extends ContentProvider {

	private SQLiteDatabase mDB;
	
	private static final String TAG = "HostDbProvider";
	private static final String DATABASE_NAME = "ssh_hosts.db";
	private static final int DATABASE_VERSION = 2;
	
	private static HashMap<String, String> HOSTS_LIST_PROJECTION_MAP;
	
	private static final int HOSTS = 1;
	private static final int HOST_ID = 2;
	
	private static final UriMatcher URL_MATCHER;
	
	private static class DatabaseHelper extends SQLiteOpenHelper {

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE hosts (_id INTEGER PRIMARY KEY,"
					+ "hostname TEXT," + "username TEXT," + "port INTEGER,"
					+ "hostkey TEXT" + ")");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS hosts");
			onCreate(db);
		}
		
	}
	
	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		int count;
		switch (URL_MATCHER.match(uri)) {
		case HOSTS:
			count = mDB.delete("ssh_hosts", where, whereArgs);
			break;
		
		case HOST_ID:
			String segment = uri.getPathSegments().get(1);
			count = mDB.delete("hosts", "_id="
					+ segment
					+ (!TextUtils.isEmpty(where) ? " AND (" + where
							+ ')' : ""), whereArgs);
			break;
			
		default:
			throw new IllegalArgumentException("Unknown Delete " + uri);
		}
		
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		switch (URL_MATCHER.match(uri)) {
		case HOSTS:
			return "vnd.android.cursor.dir/vnd.theb.host";
		case HOST_ID:
			return "vnd.android.cursor.item/vnd.theb.host";
		default:
			throw new IllegalArgumentException("Unknown getType " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		long rowID;
		
		ContentValues values;
		if (initialValues != null) {
			values = new ContentValues(initialValues);
		} else {
			values = new ContentValues();
		}
		/*
		if (URL_MATCHER.match(uri) != HOSTS) {
			throw new IllegalArgumentException("Unknown Insert " + uri);
		}
		*/
		if (values.containsKey(HostDb.Hosts.HOSTNAME) == false) {
			values.put(HostDb.Hosts.HOSTNAME, "");
		}
		
		if (values.containsKey(HostDb.Hosts.USERNAME) == false) {
			values.put(HostDb.Hosts.USERNAME, "");
		}
		
		if (values.containsKey(HostDb.Hosts.PORT) == false) {
			values.put(HostDb.Hosts.PORT, 22);
		}
		
		if (values.containsKey(HostDb.Hosts.HOSTKEY) == false) {
			values.put(HostDb.Hosts.HOSTKEY, "");
		}
		
		rowID = mDB.insert("hosts", "host", values);
		if (rowID > 0) {
			Uri newUri = ContentUris.withAppendedId(HostDb.Hosts.CONTENT_URI, rowID);
			getContext().getContentResolver().notifyChange(newUri, null);
			return newUri;
		}
		
		throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public boolean onCreate() {
		DatabaseHelper dbHelper = new DatabaseHelper();
		mDB = dbHelper.openDatabase(getContext(), DATABASE_NAME, null, DATABASE_VERSION);
		return (mDB == null) ? false : true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		
		switch (URL_MATCHER.match(uri)) {
		case HOSTS:
			qb.setTables("hosts");
			qb.setProjectionMap(HOSTS_LIST_PROJECTION_MAP);
			break;
			
		case HOST_ID:
			qb.setTables("hosts");
			qb.appendWhere("_id=" + uri.getPathSegments().get(1));
			break;
			
		default:
			throw new IllegalArgumentException("Unknown Query " + uri);
		}
		
		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = HostDb.Hosts.DEFAULT_SORT_ORDER;
		} else {
			orderBy = sortOrder;
		}
		
		Cursor c = qb.query(mDB, projection, selection, selectionArgs, null,
				null, orderBy);
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public int update(Uri uri, ContentValues values, String where,
			String[] whereArgs) {
		int count;
		
		switch (URL_MATCHER.match(uri)) {
		case HOSTS:
			count = mDB.update("hosts", values, where, whereArgs);
			break;
			
        case HOST_ID:
            String segment = uri.getPathSegments().get(1);
            count = mDB
                    .update("hosts", values, "_id="
                            + segment
                            + (!TextUtils.isEmpty(where) ? " AND (" + where
                                    + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown Update " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;

	}

	static {
		URL_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URL_MATCHER.addURI("org.theb.provider.HostDb", "hosts", HOSTS);
		URL_MATCHER.addURI("org.theb.provider.HostDb", "hosts/#", HOST_ID);
		
		HOSTS_LIST_PROJECTION_MAP = new HashMap<String, String>();
		HOSTS_LIST_PROJECTION_MAP.put(HostDb.Hosts._ID, "_id");
		HOSTS_LIST_PROJECTION_MAP.put(HostDb.Hosts.HOSTNAME, "hostname");
		HOSTS_LIST_PROJECTION_MAP.put(HostDb.Hosts.USERNAME, "username");
		HOSTS_LIST_PROJECTION_MAP.put(HostDb.Hosts.PORT, "port");
		HOSTS_LIST_PROJECTION_MAP.put(HostDb.Hosts.HOSTKEY, "hostkey");
	}
}
