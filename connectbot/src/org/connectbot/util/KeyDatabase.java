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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Contains information about personal private keys used for key-based
 * authentication. Find more information here:
 * 
 * http://www.wlug.org.nz/PublicKeyAuthentication
 * 
 * @author jsharkey
 */
public class KeyDatabase extends SQLiteOpenHelper {
	
	public final static String DB_NAME = "keys";
	public final static int DB_VERSION = 1;
	
	public final static String TABLE_PRIVKEYS = "keys";
	public final static String FIELD_KEY_NAME = "name";
	public final static String FIELD_KEY_PRIVATE = "private";
	
	public KeyDatabase(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE_PRIVKEYS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_KEY_NAME + " TEXT, "
				+ FIELD_KEY_PRIVATE + " TEXT)");
		
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRIVKEYS);
		onCreate(db);
	}
	
	
}
