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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

/**
 * @author Kenny Root
 *
 */
public abstract class RobustSQLiteOpenHelper extends SQLiteOpenHelper {
	private static List<String> mTableNames = new LinkedList<String>();

	public RobustSQLiteOpenHelper(Context context, String name,
			CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	protected static void addTableName(String tableName) {
		mTableNames.add(tableName);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		dropAllTables(db);
	}

	@Override
	public final void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		try {
			onRobustUpgrade(db, oldVersion, newVersion);
		} catch (SQLiteException e) {
			// The database has entered an unknown state. Try to recover.
			try {
				regenerateTables(db);
			} catch (SQLiteException e2) {
				dropAndCreateTables(db);
			}
		}
	}

	public abstract void onRobustUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) throws SQLiteException;

	private void regenerateTables(SQLiteDatabase db) {
		dropAllTablesWithPrefix(db, "OLD_");

		for (String tableName : mTableNames)
			db.execSQL("ALTER TABLE " + tableName + " RENAME TO OLD_"
					+ tableName);

		onCreate(db);

		for (String tableName : mTableNames)
			repopulateTable(db, tableName);

		dropAllTablesWithPrefix(db, "OLD_");
	}

	private void repopulateTable(SQLiteDatabase db, String tableName) {
		String columns = getTableColumnNames(db, tableName);

		StringBuilder sb = new StringBuilder();
		sb.append("INSERT INTO ")
				.append(tableName)
				.append(" (")
				.append(columns)
				.append(") SELECT ")
				.append(columns)
				.append(" FROM OLD_")
				.append(tableName);

		String sql = sb.toString();
		db.execSQL(sql);
	}

	private String getTableColumnNames(SQLiteDatabase db, String tableName) {
		StringBuilder sb = new StringBuilder();

		Cursor fields = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
		while (fields.moveToNext()) {
			if (!fields.isFirst())
				sb.append(", ");
			sb.append(fields.getString(1));
		}
		fields.close();

		return sb.toString();
	}

	private void dropAndCreateTables(SQLiteDatabase db) {
		dropAllTables(db);
		onCreate(db);
	}

	private void dropAllTablesWithPrefix(SQLiteDatabase db, String prefix) {
		for (String tableName : mTableNames)
			db.execSQL("DROP TABLE IF EXISTS " + prefix + tableName);
	}

	private void dropAllTables(SQLiteDatabase db) {
		dropAllTablesWithPrefix(db, "");
	}
}
