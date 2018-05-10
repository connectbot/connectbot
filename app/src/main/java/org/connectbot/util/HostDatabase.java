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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;
import org.connectbot.data.ColorStorage;
import org.connectbot.data.HostStorage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.trilead.ssh2.KnownHosts;

/**
 * Contains information about various SSH hosts, include public hostkey if known
 * from previous sessions.
 *
 * @author jsharkey
 */
public class HostDatabase extends RobustSQLiteOpenHelper implements HostStorage, ColorStorage {

	public final static String TAG = "CB.HostDatabase";

	public final static String DB_NAME = "hosts";
	public final static int DB_VERSION = 25;

	public final static String TABLE_HOSTS = "hosts";
	public final static String FIELD_HOST_NICKNAME = "nickname";
	public final static String FIELD_HOST_PROTOCOL = "protocol";
	public final static String FIELD_HOST_USERNAME = "username";
	public final static String FIELD_HOST_HOSTNAME = "hostname";
	public final static String FIELD_HOST_PORT = "port";
	public final static String FIELD_HOST_LASTCONNECT = "lastconnect";
	public final static String FIELD_HOST_COLOR = "color";
	public final static String FIELD_HOST_USEKEYS = "usekeys";
	public final static String FIELD_HOST_USEAUTHAGENT = "useauthagent";
	public final static String FIELD_HOST_POSTLOGIN = "postlogin";
	public final static String FIELD_HOST_PUBKEYID = "pubkeyid";
	public final static String FIELD_HOST_WANTSESSION = "wantsession";
	public final static String FIELD_HOST_DELKEY = "delkey";
	public final static String FIELD_HOST_FONTSIZE = "fontsize";
	public final static String FIELD_HOST_COMPRESSION = "compression";
	public final static String FIELD_HOST_ENCODING = "encoding";
	public final static String FIELD_HOST_STAYCONNECTED = "stayconnected";
	public final static String FIELD_HOST_QUICKDISCONNECT = "quickdisconnect";

	public final static String TABLE_KNOWNHOSTS = "knownhosts";
	public final static String FIELD_KNOWNHOSTS_HOSTID = "hostid";
	public final static String FIELD_KNOWNHOSTS_HOSTKEYALGO = "hostkeyalgo";
	public final static String FIELD_KNOWNHOSTS_HOSTKEY = "hostkey";

	public final static String TABLE_PORTFORWARDS = "portforwards";
	public final static String FIELD_PORTFORWARD_HOSTID = "hostid";
	public final static String FIELD_PORTFORWARD_NICKNAME = "nickname";
	public final static String FIELD_PORTFORWARD_TYPE = "type";
	public final static String FIELD_PORTFORWARD_SOURCEPORT = "sourceport";
	public final static String FIELD_PORTFORWARD_DESTADDR = "destaddr";
	public final static String FIELD_PORTFORWARD_DESTPORT = "destport";

	public final static String TABLE_COLORS = "colors";
	public final static String FIELD_COLOR_SCHEME = "scheme";
	public final static String FIELD_COLOR_NUMBER = "number";
	public final static String FIELD_COLOR_VALUE = "value";

	public final static String TABLE_COLOR_DEFAULTS = "colorDefaults";
	public final static String FIELD_COLOR_FG = "fg";
	public final static String FIELD_COLOR_BG = "bg";

	public final static int DEFAULT_FG_COLOR = 7;
	public final static int DEFAULT_BG_COLOR = 0;

	public final static String COLOR_RED = "red";
	public final static String COLOR_GREEN = "green";
	public final static String COLOR_BLUE = "blue";
	public final static String COLOR_GRAY = "gray";

	public final static String PORTFORWARD_LOCAL = "local";
	public final static String PORTFORWARD_REMOTE = "remote";
	public final static String PORTFORWARD_DYNAMIC4 = "dynamic4";
	public final static String PORTFORWARD_DYNAMIC5 = "dynamic5";

	public final static String DELKEY_DEL = "del";
	public final static String DELKEY_BACKSPACE = "backspace";

	public final static String AUTHAGENT_NO = "no";
	public final static String AUTHAGENT_CONFIRM = "confirm";
	public final static String AUTHAGENT_YES = "yes";

	public final static String ENCODING_DEFAULT = Charset.defaultCharset().name();

	public final static long PUBKEYID_NEVER = -2;
	public final static long PUBKEYID_ANY = -1;

	public static final int DEFAULT_COLOR_SCHEME = 0;

	// Table creation strings
	public static final String TABLE_HOSTS_COLUMNS = "_id INTEGER PRIMARY KEY, "
			+ FIELD_HOST_NICKNAME + " TEXT, "
			+ FIELD_HOST_PROTOCOL + " TEXT DEFAULT 'ssh', "
			+ FIELD_HOST_USERNAME + " TEXT, "
			+ FIELD_HOST_HOSTNAME + " TEXT, "
			+ FIELD_HOST_PORT + " INTEGER, "
			+ FIELD_HOST_LASTCONNECT + " INTEGER, "
			+ FIELD_HOST_COLOR + " TEXT, "
			+ FIELD_HOST_USEKEYS + " TEXT, "
			+ FIELD_HOST_USEAUTHAGENT + " TEXT, "
			+ FIELD_HOST_POSTLOGIN + " TEXT, "
			+ FIELD_HOST_PUBKEYID + " INTEGER DEFAULT " + PUBKEYID_ANY + ", "
			+ FIELD_HOST_DELKEY + " TEXT DEFAULT '" + DELKEY_DEL + "', "
			+ FIELD_HOST_FONTSIZE + " INTEGER, "
			+ FIELD_HOST_WANTSESSION + " TEXT DEFAULT '" + Boolean.toString(true) + "', "
			+ FIELD_HOST_COMPRESSION + " TEXT DEFAULT '" + Boolean.toString(false) + "', "
			+ FIELD_HOST_ENCODING + " TEXT DEFAULT '" + ENCODING_DEFAULT + "', "
			+ FIELD_HOST_STAYCONNECTED + " TEXT DEFAULT '" + Boolean.toString(false) + "', "
			+ FIELD_HOST_QUICKDISCONNECT + " TEXT DEFAULT '" + Boolean.toString(false) + "'";

	public static final String CREATE_TABLE_HOSTS = "CREATE TABLE " + TABLE_HOSTS
			+ " (" + TABLE_HOSTS_COLUMNS + ")";

	public static final String CREATE_TABLE_COLOR_DEFAULTS =
		"CREATE TABLE " + TABLE_COLOR_DEFAULTS
		+ " (" + FIELD_COLOR_SCHEME + " INTEGER NOT NULL, "
		+ FIELD_COLOR_FG + " INTEGER NOT NULL DEFAULT " + DEFAULT_FG_COLOR + ", "
		+ FIELD_COLOR_BG + " INTEGER NOT NULL DEFAULT " + DEFAULT_BG_COLOR + ")";

	public static final String CREATE_TABLE_COLOR_DEFAULTS_INDEX =
		"CREATE INDEX " + TABLE_COLOR_DEFAULTS + FIELD_COLOR_SCHEME + "index ON "
		+ TABLE_COLOR_DEFAULTS + " (" + FIELD_COLOR_SCHEME + ");";

	private static final String WHERE_SCHEME_AND_COLOR = FIELD_COLOR_SCHEME + " = ? AND "
			+ FIELD_COLOR_NUMBER + " = ?";

	static {
		addTableName(TABLE_HOSTS);
		addTableName(TABLE_KNOWNHOSTS);
		addIndexName(TABLE_KNOWNHOSTS + FIELD_KNOWNHOSTS_HOSTID + "index");
		addTableName(TABLE_PORTFORWARDS);
		addIndexName(TABLE_PORTFORWARDS + FIELD_PORTFORWARD_HOSTID + "index");
		addTableName(TABLE_COLORS);
		addIndexName(TABLE_COLORS + FIELD_COLOR_SCHEME + "index");
		addTableName(TABLE_COLOR_DEFAULTS);
		addIndexName(TABLE_COLOR_DEFAULTS + FIELD_COLOR_SCHEME + "index");
	}

	/** Used during upgrades from DB version 23 to 24. */
	private final float displayDensity;

	private static final Object sInstanceLock = new Object();

	private static HostDatabase sInstance;

	private final SQLiteDatabase mDb;

	public static HostDatabase get(Context context) {
		synchronized (sInstanceLock) {
			if (sInstance != null) {
				return sInstance;
			}

			sInstance = new HostDatabase(context.getApplicationContext());
			return sInstance;
		}
	}

	private HostDatabase(Context context) {
		this(context, DB_NAME);
	}

	private HostDatabase(Context context, String dbName) {
		super(context, dbName, null, DB_VERSION);

		this.displayDensity = context.getResources().getDisplayMetrics().density;
		mDb = getWritableDatabase();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		super.onCreate(db);

		createTables(db);
	}

	private void createTables(SQLiteDatabase db) {
		db.execSQL(CREATE_TABLE_HOSTS);

		db.execSQL("CREATE TABLE " + TABLE_KNOWNHOSTS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_KNOWNHOSTS_HOSTID + " INTEGER, "
				+ FIELD_KNOWNHOSTS_HOSTKEYALGO + " TEXT, "
				+ FIELD_KNOWNHOSTS_HOSTKEY + " BLOB)");

		db.execSQL("CREATE INDEX " + TABLE_KNOWNHOSTS + FIELD_KNOWNHOSTS_HOSTID + "index ON "
				+ TABLE_KNOWNHOSTS + " (" + FIELD_KNOWNHOSTS_HOSTID + ");");

		db.execSQL("CREATE TABLE " + TABLE_PORTFORWARDS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_PORTFORWARD_HOSTID + " INTEGER, "
				+ FIELD_PORTFORWARD_NICKNAME + " TEXT, "
				+ FIELD_PORTFORWARD_TYPE + " TEXT NOT NULL DEFAULT '" + PORTFORWARD_LOCAL + "', "
				+ FIELD_PORTFORWARD_SOURCEPORT + " INTEGER NOT NULL DEFAULT 8080, "
				+ FIELD_PORTFORWARD_DESTADDR + " TEXT, "
				+ FIELD_PORTFORWARD_DESTPORT + " TEXT)");

		db.execSQL("CREATE INDEX " + TABLE_PORTFORWARDS + FIELD_PORTFORWARD_HOSTID + "index ON "
				+ TABLE_PORTFORWARDS + " (" + FIELD_PORTFORWARD_HOSTID + ");");

		db.execSQL("CREATE TABLE " + TABLE_COLORS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_COLOR_NUMBER + " INTEGER, "
				+ FIELD_COLOR_VALUE + " INTEGER, "
				+ FIELD_COLOR_SCHEME + " INTEGER)");

		db.execSQL("CREATE INDEX " + TABLE_COLORS + FIELD_COLOR_SCHEME + "index ON "
				+ TABLE_COLORS + " (" + FIELD_COLOR_SCHEME + ");");

		db.execSQL(CREATE_TABLE_COLOR_DEFAULTS);
		db.execSQL(CREATE_TABLE_COLOR_DEFAULTS_INDEX);
	}

	@VisibleForTesting
	public void resetDatabase() {
		try {
			mDb.beginTransaction();

			mDb.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS);
			mDb.execSQL("DROP TABLE IF EXISTS " + TABLE_KNOWNHOSTS);
			mDb.execSQL("DROP TABLE IF EXISTS " + TABLE_PORTFORWARDS);
			mDb.execSQL("DROP TABLE IF EXISTS " + TABLE_COLORS);
			mDb.execSQL("DROP TABLE IF EXISTS " + TABLE_COLOR_DEFAULTS);

			createTables(mDb);

			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}

	@VisibleForTesting
	public static void resetInMemoryInstance(Context context) {
		get(context).resetDatabase();
	}

	@Override
	public void onRobustUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) throws SQLiteException {
		// Versions of the database before the Android Market release will be
		// shot without warning.
		if (oldVersion <= 9) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS);
			onCreate(db);
			return;
		}

		switch (oldVersion) {
		case 10:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_PUBKEYID + " INTEGER DEFAULT " + PUBKEYID_ANY);
		case 11:
			db.execSQL("CREATE TABLE " + TABLE_PORTFORWARDS
					+ " (_id INTEGER PRIMARY KEY, "
					+ FIELD_PORTFORWARD_HOSTID + " INTEGER, "
					+ FIELD_PORTFORWARD_NICKNAME + " TEXT, "
					+ FIELD_PORTFORWARD_TYPE + " TEXT NOT NULL DEFAULT '" + PORTFORWARD_LOCAL + "', "
					+ FIELD_PORTFORWARD_SOURCEPORT + " INTEGER NOT NULL DEFAULT 8080, "
					+ FIELD_PORTFORWARD_DESTADDR + " TEXT, "
					+ FIELD_PORTFORWARD_DESTPORT + " INTEGER)");
		case 12:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_WANTSESSION + " TEXT DEFAULT '" + Boolean.toString(true) + "'");
		case 13:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_COMPRESSION + " TEXT DEFAULT '" + Boolean.toString(false) + "'");
		case 14:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_ENCODING + " TEXT DEFAULT '" + ENCODING_DEFAULT + "'");
		case 15:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_PROTOCOL + " TEXT DEFAULT 'ssh'");
		case 16:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_DELKEY + " TEXT DEFAULT '" + DELKEY_DEL + "'");
		case 17:
			db.execSQL("CREATE INDEX " + TABLE_PORTFORWARDS + FIELD_PORTFORWARD_HOSTID + "index ON "
					+ TABLE_PORTFORWARDS + " (" + FIELD_PORTFORWARD_HOSTID + ");");

			// Add colors
			db.execSQL("CREATE TABLE " + TABLE_COLORS
					+ " (_id INTEGER PRIMARY KEY, "
					+ FIELD_COLOR_NUMBER + " INTEGER, "
					+ FIELD_COLOR_VALUE + " INTEGER, "
					+ FIELD_COLOR_SCHEME + " INTEGER)");
			db.execSQL("CREATE INDEX " + TABLE_COLORS + FIELD_COLOR_SCHEME + "index ON "
					+ TABLE_COLORS + " (" + FIELD_COLOR_SCHEME + ");");
		case 18:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_USEAUTHAGENT + " TEXT DEFAULT '" + AUTHAGENT_NO + "'");
		case 19:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_STAYCONNECTED + " TEXT");
		case 20:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_FONTSIZE + " INTEGER");
		case 21:
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_COLOR_DEFAULTS);
			db.execSQL(CREATE_TABLE_COLOR_DEFAULTS);
			db.execSQL(CREATE_TABLE_COLOR_DEFAULTS_INDEX);
		case 22:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_QUICKDISCONNECT + " TEXT DEFAULT '" + Boolean.toString(false) + "'");
		case 23:
			db.execSQL("UPDATE " + TABLE_HOSTS
					+ " SET " + FIELD_HOST_FONTSIZE + " = " + FIELD_HOST_FONTSIZE + " / " + displayDensity);
		case 24:
			// Move all the existing known hostkeys into their own table.
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_KNOWNHOSTS);
			db.execSQL("CREATE TABLE " + TABLE_KNOWNHOSTS
					+ " (_id INTEGER PRIMARY KEY, "
					+ FIELD_KNOWNHOSTS_HOSTID + " INTEGER, "
					+ FIELD_KNOWNHOSTS_HOSTKEYALGO + " TEXT, "
					+ FIELD_KNOWNHOSTS_HOSTKEY + " BLOB)");
			db.execSQL("INSERT INTO " + TABLE_KNOWNHOSTS + " ("
					+ FIELD_KNOWNHOSTS_HOSTID + ", "
					+ FIELD_KNOWNHOSTS_HOSTKEYALGO + ", "
					+ FIELD_KNOWNHOSTS_HOSTKEY + ") "
					+ "SELECT _id, "
					+ FIELD_KNOWNHOSTS_HOSTKEYALGO + ", "
					+ FIELD_KNOWNHOSTS_HOSTKEY
					+ " FROM " + TABLE_HOSTS);
			// Work around SQLite not supporting dropping columns
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS + "_upgrade");
			db.execSQL("CREATE TABLE " + TABLE_HOSTS + "_upgrade (" + TABLE_HOSTS_COLUMNS + ")");
			db.execSQL("INSERT INTO " + TABLE_HOSTS + "_upgrade SELECT _id, "
					+ FIELD_HOST_NICKNAME + ", "
					+ FIELD_HOST_PROTOCOL + ", "
					+ FIELD_HOST_USERNAME + ", "
					+ FIELD_HOST_HOSTNAME + ", "
					+ FIELD_HOST_PORT + ", "
					+ FIELD_HOST_LASTCONNECT + ", "
					+ FIELD_HOST_COLOR + ", "
					+ FIELD_HOST_USEKEYS + ", "
					+ FIELD_HOST_USEAUTHAGENT + ", "
					+ FIELD_HOST_POSTLOGIN + ", "
					+ FIELD_HOST_PUBKEYID + ", "
					+ FIELD_HOST_DELKEY + ", "
					+ FIELD_HOST_FONTSIZE + ", "
					+ FIELD_HOST_WANTSESSION + ", "
					+ FIELD_HOST_COMPRESSION + ", "
					+ FIELD_HOST_ENCODING + ", "
					+ FIELD_HOST_STAYCONNECTED + ", "
					+ FIELD_HOST_QUICKDISCONNECT
					+ " FROM " + TABLE_HOSTS);
			db.execSQL("DROP TABLE " + TABLE_HOSTS);
			db.execSQL("ALTER TABLE " + TABLE_HOSTS + "_upgrade RENAME TO " + TABLE_HOSTS);
		}
	}

	/**
	 * Touch a specific host to update its "last connected" field.
	 * @param host host to update
	 */
	public void touchHost(HostBean host) {
		long now = System.currentTimeMillis() / 1000;

		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_LASTCONNECT, now);

		mDb.beginTransaction();
		try {
			mDb.update(TABLE_HOSTS, values, "_id = ?", new String[] {String.valueOf(host.getId())});
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}

	/**
	 * Create a new or update an existing {@code host}.
	 */
	public HostBean saveHost(HostBean host) {
		long id = host.getId();

		mDb.beginTransaction();
		try {
			if (id == -1) {
				id = mDb.insert(TABLE_HOSTS, null, host.getValues());
			} else {
				mDb.update(TABLE_HOSTS, host.getValues(), "_id = ?", new String[] {String.valueOf(id)});
			}
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}

		host.setId(id);

		return host;
	}

	/**
	 * Delete a specific host by its <code>_id</code> value.
	 */
	public void deleteHost(HostBean host) {
		if (host.getId() < 0) {
			return;
		}

		String[] hostIdArg = new String[] {String.valueOf(host.getId())};
		mDb.beginTransaction();
		try {
			mDb.delete(TABLE_KNOWNHOSTS, FIELD_KNOWNHOSTS_HOSTID + " = ?", hostIdArg);
			mDb.delete(TABLE_HOSTS, "_id = ?", hostIdArg);
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}

	/**
	 * Return a cursor that contains information about all known hosts.
	 * @param sortColors If true, sort by color, otherwise sort by nickname.
	 */
	public List<HostBean> getHosts(boolean sortColors) {
		String sortField = sortColors ? FIELD_HOST_COLOR : FIELD_HOST_NICKNAME;
		List<HostBean> hosts;

		Cursor c = mDb.query(TABLE_HOSTS, null, null, null, null, null, sortField + " ASC");

		hosts = createHostBeans(c);

		c.close();

		return hosts;
	}

	/**
	 * @param c cursor to read from
	 */
	private List<HostBean> createHostBeans(Cursor c) {
		List<HostBean> hosts = new LinkedList<HostBean>();

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_HOST_NICKNAME),
			COL_PROTOCOL = c.getColumnIndexOrThrow(FIELD_HOST_PROTOCOL),
			COL_USERNAME = c.getColumnIndexOrThrow(FIELD_HOST_USERNAME),
			COL_HOSTNAME = c.getColumnIndexOrThrow(FIELD_HOST_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_HOST_PORT),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(FIELD_HOST_LASTCONNECT),
			COL_COLOR = c.getColumnIndexOrThrow(FIELD_HOST_COLOR),
			COL_USEKEYS = c.getColumnIndexOrThrow(FIELD_HOST_USEKEYS),
			COL_USEAUTHAGENT = c.getColumnIndexOrThrow(FIELD_HOST_USEAUTHAGENT),
			COL_POSTLOGIN = c.getColumnIndexOrThrow(FIELD_HOST_POSTLOGIN),
			COL_PUBKEYID = c.getColumnIndexOrThrow(FIELD_HOST_PUBKEYID),
			COL_WANTSESSION = c.getColumnIndexOrThrow(FIELD_HOST_WANTSESSION),
			COL_DELKEY = c.getColumnIndexOrThrow(FIELD_HOST_DELKEY),
			COL_FONTSIZE = c.getColumnIndexOrThrow(FIELD_HOST_FONTSIZE),
			COL_COMPRESSION = c.getColumnIndexOrThrow(FIELD_HOST_COMPRESSION),
			COL_ENCODING = c.getColumnIndexOrThrow(FIELD_HOST_ENCODING),
			COL_STAYCONNECTED = c.getColumnIndexOrThrow(FIELD_HOST_STAYCONNECTED),
			COL_QUICKDISCONNECT = c.getColumnIndexOrThrow(FIELD_HOST_QUICKDISCONNECT);

		while (c.moveToNext()) {
			HostBean host = new HostBean();

			host.setId(c.getLong(COL_ID));
			host.setNickname(c.getString(COL_NICKNAME));
			host.setProtocol(c.getString(COL_PROTOCOL));
			host.setUsername(c.getString(COL_USERNAME));
			host.setHostname(c.getString(COL_HOSTNAME));
			host.setPort(c.getInt(COL_PORT));
			host.setLastConnect(c.getLong(COL_LASTCONNECT));
			host.setColor(c.getString(COL_COLOR));
			host.setUseKeys(Boolean.valueOf(c.getString(COL_USEKEYS)));
			host.setUseAuthAgent(c.getString(COL_USEAUTHAGENT));
			host.setPostLogin(c.getString(COL_POSTLOGIN));
			host.setPubkeyId(c.getLong(COL_PUBKEYID));
			host.setWantSession(Boolean.valueOf(c.getString(COL_WANTSESSION)));
			host.setDelKey(c.getString(COL_DELKEY));
			host.setFontSize(c.getInt(COL_FONTSIZE));
			host.setCompression(Boolean.valueOf(c.getString(COL_COMPRESSION)));
			host.setEncoding(c.getString(COL_ENCODING));
			host.setStayConnected(Boolean.valueOf(c.getString(COL_STAYCONNECTED)));
			host.setQuickDisconnect(Boolean.valueOf(c.getString(COL_QUICKDISCONNECT)));

			hosts.add(host);
		}

		return hosts;
	}

	/**
	 * @param c cursor with zero or more hosts
	 * @return the first host from the cursor or {@code null} if none.
	 */
	private HostBean getFirstHostBean(Cursor c) {
		HostBean host = null;

		List<HostBean> hosts = createHostBeans(c);
		if (hosts.size() > 0)
			host = hosts.get(0);

		c.close();

		return host;
	}

	/**
	 * @param selection parameters describing the desired host
	 * @return host matching selection or {@code null}.
	 */
	public HostBean findHost(Map<String, String> selection) {
		StringBuilder selectionBuilder = new StringBuilder();

		Iterator<Entry<String, String>> i = selection.entrySet().iterator();

		List<String> selectionValuesList = new LinkedList<String>();
		int n = 0;
		while (i.hasNext()) {
			Entry<String, String> entry = i.next();

			if (entry.getValue() == null)
				continue;

			if (n++ > 0)
				selectionBuilder.append(" AND ");

			selectionBuilder.append(entry.getKey())
				.append(" = ?");

			selectionValuesList.add(entry.getValue());
		}

		String selectionValues[] = new String[selectionValuesList.size()];
		selectionValuesList.toArray(selectionValues);

		Cursor c = mDb.query(TABLE_HOSTS, null,
				selectionBuilder.toString(),
				selectionValues,
				null, null, null);

		HostBean host = getFirstHostBean(c);

		return host;
	}

	/**
	 * @param hostId host id for the host
	 * @return host matching the hostId or {@code null} if none match
	 */
	public HostBean findHostById(long hostId) {
		Cursor c = mDb.query(TABLE_HOSTS, null,
				"_id = ?", new String[] {String.valueOf(hostId)},
				null, null, null);

		return getFirstHostBean(c);
	}

	/**
	 * Record the given hostkey into database under this nickname.
	 * @param hostname hostname to match
	 * @param port port to match
	 * @param hostkeyalgo algorithm for host key
	 * @param hostkey the bytes of the host key itself
	 */
	public void saveKnownHost(String hostname, int port, String hostkeyalgo, byte[] hostkey) {
		HashMap<String, String> selection = new HashMap<>();
		selection.put(FIELD_HOST_HOSTNAME, hostname);
		selection.put(FIELD_HOST_PORT, String.valueOf(port));
		HostBean hostBean = findHost(selection);

		if (hostBean == null) {
			Log.e(TAG, "Tried to save known host for " + hostname + ":" + port
					+ " it doesn't exist in the database");
			return;
		}

		ContentValues values = new ContentValues();
		values.put(FIELD_KNOWNHOSTS_HOSTKEYALGO, hostkeyalgo);
		values.put(FIELD_KNOWNHOSTS_HOSTKEY, hostkey);
		values.put(FIELD_KNOWNHOSTS_HOSTID, hostBean.getId());

		mDb.beginTransaction();
		try {
			mDb.delete(TABLE_KNOWNHOSTS, FIELD_KNOWNHOSTS_HOSTID + " = ? AND "
							+ FIELD_KNOWNHOSTS_HOSTKEYALGO + " = ?",
					new String[] {String.valueOf(hostBean.getId()), hostkeyalgo});
			mDb.insert(TABLE_KNOWNHOSTS, null, values);
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
		Log.d(TAG, String.format("Finished saving hostkey information for '%s:%d' algo %s",
				hostname, port, hostkeyalgo));
	}

	@Override
	public void removeKnownHost(String host, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) {
		throw new UnsupportedOperationException("removeKnownHost is not implemented");
	}

	/**
	 * Build list of known hosts for Trilead library.
	 * @return
	 */
	public KnownHosts getKnownHosts() {
		KnownHosts known = new KnownHosts();

		Cursor c = mDb.query(TABLE_HOSTS + " LEFT OUTER JOIN " + TABLE_KNOWNHOSTS
						+ " ON " + TABLE_HOSTS + "._id = "
						+ TABLE_KNOWNHOSTS + "." + FIELD_KNOWNHOSTS_HOSTID,
				new String[] {FIELD_HOST_HOSTNAME, FIELD_HOST_PORT, FIELD_KNOWNHOSTS_HOSTKEYALGO,
						FIELD_KNOWNHOSTS_HOSTKEY},
				null, null, null, null, null);

		if (c != null) {
			int COL_HOSTNAME = c.getColumnIndexOrThrow(FIELD_HOST_HOSTNAME),
					COL_PORT = c.getColumnIndexOrThrow(FIELD_HOST_PORT),
					COL_HOSTKEYALGO = c.getColumnIndexOrThrow(FIELD_KNOWNHOSTS_HOSTKEYALGO),
					COL_HOSTKEY = c.getColumnIndexOrThrow(FIELD_KNOWNHOSTS_HOSTKEY);

			while (c.moveToNext()) {
				String hostname = c.getString(COL_HOSTNAME);
				String hostkeyalgo = c.getString(COL_HOSTKEYALGO);
				int port = c.getInt(COL_PORT);
				byte[] hostkey = c.getBlob(COL_HOSTKEY);

				if (hostkeyalgo == null || hostkeyalgo.length() == 0) continue;
				if (hostkey == null || hostkey.length == 0) continue;

				try {
					known.addHostkey(new String[] {String.format(Locale.US, "%s:%d", hostname, port)},
							hostkeyalgo, hostkey);
				} catch (Exception e) {
					Log.e(TAG, "Problem while adding a known host from database", e);
				}
			}

			c.close();
		}

		return known;
	}

	@Override
	public List<String> getHostKeyAlgorithmsForHost(String hostname, int port) {
		HashMap<String, String> selection = new HashMap<>();
		selection.put(FIELD_HOST_HOSTNAME, hostname);
		selection.put(FIELD_HOST_PORT, String.valueOf(port));
		HostBean hostBean = findHost(selection);

		if (hostBean == null) {
			return null;
		}

		ArrayList<String> knownAlgorithms = new ArrayList<>();

		Cursor c = mDb.query(TABLE_KNOWNHOSTS, new String[] {FIELD_KNOWNHOSTS_HOSTKEYALGO},
				FIELD_KNOWNHOSTS_HOSTID + " = ?",
				new String[] {String.valueOf(hostBean.getId())}, null, null, null);

		if (c != null) {
			int COL_ALGO = c.getColumnIndexOrThrow(FIELD_KNOWNHOSTS_HOSTKEYALGO);

			while (c.moveToNext()) {
				String keyAlgo = c.getString(COL_ALGO);
				if (keyAlgo != null) {
					knownAlgorithms.add(keyAlgo);
				}
			}

			c.close();
		}

		return knownAlgorithms;
	}

	/**
	 * Unset any hosts using a pubkey ID that has been deleted.
	 * @param pubkeyId
	 */
	public void stopUsingPubkey(long pubkeyId) {
		if (pubkeyId < 0) return;

		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_PUBKEYID, PUBKEYID_ANY);

		mDb.beginTransaction();
		try {
			mDb.update(TABLE_HOSTS, values, FIELD_HOST_PUBKEYID + " = ?", new String[] {String.valueOf(pubkeyId)});
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}

		Log.d(TAG, String.format("Set all hosts using pubkey id %d to -1", pubkeyId));
	}

	/*
	 * Methods for dealing with port forwards attached to hosts
	 */

	/**
	 * Returns a list of all the port forwards associated with a particular host ID.
	 * @param host the host for which we want the port forward list
	 * @return port forwards associated with host ID or empty list if no match
	 */
	public List<PortForwardBean> getPortForwardsForHost(HostBean host) {
		List<PortForwardBean> portForwards = new LinkedList<PortForwardBean>();
		if (host == null) {
			return portForwards;
		}

		Cursor c = mDb.query(TABLE_PORTFORWARDS, new String[] {
						"_id", FIELD_PORTFORWARD_NICKNAME, FIELD_PORTFORWARD_TYPE, FIELD_PORTFORWARD_SOURCEPORT,
						FIELD_PORTFORWARD_DESTADDR, FIELD_PORTFORWARD_DESTPORT},
				FIELD_PORTFORWARD_HOSTID + " = ?", new String[] {String.valueOf(host.getId())},
				null, null, null);

		while (c.moveToNext()) {
			PortForwardBean pfb = new PortForwardBean(
					c.getInt(0),
					host.getId(),
					c.getString(1),
					c.getString(2),
					c.getInt(3),
					c.getString(4),
					c.getInt(5));
			portForwards.add(pfb);
		}

		c.close();

		return portForwards;
	}

	/**
	 * Update the parameters of a port forward in the database.
	 * @param pfb {@link PortForwardBean} to save
	 * @return true on success
	 */
	public boolean savePortForward(PortForwardBean pfb) {
		mDb.beginTransaction();
		try {
			if (pfb.getId() < 0) {
				long addedId = mDb.insert(TABLE_PORTFORWARDS, null, pfb.getValues());
				if (addedId == -1) {
					return false;
				}
				pfb.setId(addedId);
			} else {
				if (mDb.update(TABLE_PORTFORWARDS, pfb.getValues(), "_id = ?", new String[] {String.valueOf(pfb.getId())}) <= 0) {
					return false;
				}
			}

			mDb.setTransactionSuccessful();
			return true;
		} finally {
			mDb.endTransaction();
		}
	}

	/**
	 * Deletes a port forward from the database.
	 * @param pfb {@link PortForwardBean} to delete
	 */
	public void deletePortForward(PortForwardBean pfb) {
		if (pfb.getId() < 0) {
			return;
		}

		mDb.beginTransaction();
		try {
			mDb.delete(TABLE_PORTFORWARDS, "_id = ?", new String[] {String.valueOf(pfb.getId())});
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}

	public int[] getColorsForScheme(int scheme) {
		int[] colors = Colors.defaults.clone();

		Cursor c = mDb.query(TABLE_COLORS, new String[] {
						FIELD_COLOR_NUMBER, FIELD_COLOR_VALUE},
				FIELD_COLOR_SCHEME + " = ?",
				new String[] {String.valueOf(scheme)},
				null, null, null);

		while (c.moveToNext()) {
			colors[c.getInt(0)] = c.getInt(1);
		}

		c.close();

		return colors;
	}

	public void setColorForScheme(int scheme, int number, int value) {
		final SQLiteDatabase db;

		final String[] whereArgs = new String[] { String.valueOf(scheme), String.valueOf(number) };

		if (value == Colors.defaults[number]) {
			mDb.beginTransaction();
			try {
				mDb.delete(TABLE_COLORS,
						WHERE_SCHEME_AND_COLOR, whereArgs);
				mDb.setTransactionSuccessful();
			} finally {
				mDb.endTransaction();
			}
		} else {
			final ContentValues values = new ContentValues();
			values.put(FIELD_COLOR_VALUE, value);

			mDb.beginTransaction();
			try {
				final int rowsAffected = mDb.update(TABLE_COLORS, values,
						WHERE_SCHEME_AND_COLOR, whereArgs);

				if (rowsAffected == 0) {
					values.put(FIELD_COLOR_SCHEME, scheme);
					values.put(FIELD_COLOR_NUMBER, number);
					mDb.insert(TABLE_COLORS, null, values);
				}
				mDb.setTransactionSuccessful();
			} finally {
				mDb.endTransaction();
			}
		}
	}

	public void setGlobalColor(int number, int value) {
		setColorForScheme(DEFAULT_COLOR_SCHEME, number, value);
	}

	public int[] getDefaultColorsForScheme(int scheme) {
		int[] colors = new int[] { DEFAULT_FG_COLOR, DEFAULT_BG_COLOR };

		Cursor c = mDb.query(TABLE_COLOR_DEFAULTS,
				new String[] {FIELD_COLOR_FG, FIELD_COLOR_BG},
				FIELD_COLOR_SCHEME + " = ?",
				new String[] {String.valueOf(scheme)},
				null, null, null);

		if (c.moveToFirst()) {
			colors[0] = c.getInt(0);
			colors[1] = c.getInt(1);
		}

		c.close();

		return colors;
	}

	public int[] getGlobalDefaultColors() {
		return getDefaultColorsForScheme(DEFAULT_COLOR_SCHEME);
	}

	public void setDefaultColorsForScheme(int scheme, int fg, int bg) {
		SQLiteDatabase db;

		String schemeWhere = null;
		String[] whereArgs;

		schemeWhere = FIELD_COLOR_SCHEME + " = ?";
		whereArgs = new String[] { String.valueOf(scheme) };

		ContentValues values = new ContentValues();
		values.put(FIELD_COLOR_FG, fg);
		values.put(FIELD_COLOR_BG, bg);

		mDb.beginTransaction();
		try {
			int rowsAffected = mDb.update(TABLE_COLOR_DEFAULTS, values,
					schemeWhere, whereArgs);

			if (rowsAffected == 0) {
				values.put(FIELD_COLOR_SCHEME, scheme);
				mDb.insert(TABLE_COLOR_DEFAULTS, null, values);
			}
			mDb.setTransactionSuccessful();
		} finally {
			mDb.endTransaction();
		}
	}
}
