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
	private static List<String> mIndexNames = new LinkedList<String>();

	public RobustSQLiteOpenHelper(Context context, String name,
			CursorFactory factory, int version) {
		super(context, name, factory, version);
	}

	protected static void addTableName(String tableName) {
		mTableNames.add(tableName);
	}

	protected static void addIndexName(String indexName) {
		mIndexNames.add(indexName);
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
		for (String indexName : mIndexNames)
			db.execSQL("DROP INDEX IF EXISTS " + prefix + indexName);
		for (String tableName : mTableNames)
			db.execSQL("DROP TABLE IF EXISTS " + prefix + tableName);
	}

	private void dropAllTables(SQLiteDatabase db) {
		dropAllTablesWithPrefix(db, "");
	}
}
