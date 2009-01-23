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

import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;

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
	public final static int DB_VERSION = 15;

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
	public final static String FIELD_HOST_WANTSESSION = "wantsession";
	public final static String FIELD_HOST_COMPRESSION = "compression";
	public final static String FIELD_HOST_ENCODING = "encoding";

	public final static String TABLE_PORTFORWARDS = "portforwards";
	public final static String FIELD_PORTFORWARD_HOSTID = "hostid";
	public final static String FIELD_PORTFORWARD_NICKNAME = "nickname";
	public final static String FIELD_PORTFORWARD_TYPE = "type";
	public final static String FIELD_PORTFORWARD_SOURCEPORT = "sourceport";
	public final static String FIELD_PORTFORWARD_DESTADDR = "destaddr";
	public final static String FIELD_PORTFORWARD_DESTPORT = "destport";

	public final static String COLOR_RED = "red";
	public final static String COLOR_GREEN = "green";
	public final static String COLOR_BLUE = "blue";
	public final static String COLOR_GRAY = "gray";

	public final static String PORTFORWARD_LOCAL = "local";
	public final static String PORTFORWARD_REMOTE = "remote";
	public final static String PORTFORWARD_DYNAMIC4 = "dynamic4";
	public final static String PORTFORWARD_DYNAMIC5 = "dynamic5";

	public final static String ENCODING_ASCII = "ASCII";
	public final static String ENCODING_UTF8 = "UTF-8";
	public final static String ENCODING_ISO88591 = "ISO8859_1";

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
				+ FIELD_HOST_PUBKEYID + " INTEGER DEFAULT " + PUBKEYID_ANY + ", "
				+ FIELD_HOST_WANTSESSION + " TEXT DEFAULT '" + Boolean.toString(true) + "', "
				+ FIELD_HOST_COMPRESSION + " TEXT DEFAULT '" + Boolean.toString(false) + "', "
				+ FIELD_HOST_ENCODING + " TEXT DEFAULT '" + ENCODING_ASCII + "')");
		// insert a few sample hosts, none of which probably connect
		//this.createHost(db, "connectbot@bravo", "connectbot", "192.168.254.230", 22, COLOR_GRAY);
		//this.createHost(db, "cron@server.example.com", "cron", "server.example.com", 22, COLOR_GRAY, PUBKEYID_ANY);
		//this.createHost(db, "backup@example.net", "backup", "example.net", 22, COLOR_BLUE, PUBKEYID_ANY);

		db.execSQL("CREATE TABLE " + TABLE_PORTFORWARDS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_PORTFORWARD_HOSTID + " INTEGER, "
				+ FIELD_PORTFORWARD_NICKNAME + " TEXT, "
				+ FIELD_PORTFORWARD_TYPE + " TEXT NOT NULL DEFAULT " + PORTFORWARD_LOCAL + ", "
				+ FIELD_PORTFORWARD_SOURCEPORT + " INTEGER NOT NULL DEFAULT 8080, "
				+ FIELD_PORTFORWARD_DESTADDR + " TEXT, "
				+ FIELD_PORTFORWARD_DESTPORT + " TEXT)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
					+ FIELD_PORTFORWARD_TYPE + " TEXT NOT NULL DEFAULT " + PORTFORWARD_LOCAL + ", "
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
					+ " ADD COLUMN " + FIELD_HOST_ENCODING + " TEXT DEFAULT '" + ENCODING_ASCII + "'");
		}
	}

	/**
	 * Touch a specific host to update its "last connected" field.
	 * @param nickname Nickname field of host to update
	 */
	public void touchHost(HostBean host) {
		SQLiteDatabase db = this.getWritableDatabase();
		long now = System.currentTimeMillis() / 1000;

		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_LASTCONNECT, now);

		db.update(TABLE_HOSTS, values, "_id = ?", new String[] { String.valueOf(host.getId()) });

		db.close();
	}

	/**
	 * Create a new host using the given parameters.
	 */
	public HostBean saveHost(HostBean host) {
		SQLiteDatabase db = this.getWritableDatabase();

		long id = db.insert(TABLE_HOSTS, null, host.getValues());
		db.close();

		host.setId(id);

		return host;
	}

	/**
	 * Delete a specific host by its <code>_id</code> value.
	 */
	public void deleteHost(HostBean host) {
		if (host.getId() < 0)
			return;

		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_HOSTS, "_id = ?", new String[] { String.valueOf(host.getId()) });
		db.close();
	}

	/**
	 * Return a cursor that contains information about all known hosts.
	 * @param sortColors If true, sort by color, otherwise sort by nickname.
	 */
	public List<HostBean> getHosts(boolean sortColors) {
		String sortField = sortColors ? FIELD_HOST_COLOR : FIELD_HOST_NICKNAME;
		SQLiteDatabase db = this.getReadableDatabase();

		List<HostBean> hosts = new LinkedList<HostBean>();

		Cursor c = db.query(TABLE_HOSTS, null, null, null, null, null, sortField + " ASC");

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_HOST_NICKNAME),
			COL_USERNAME = c.getColumnIndexOrThrow(FIELD_HOST_USERNAME),
			COL_HOSTNAME = c.getColumnIndexOrThrow(FIELD_HOST_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_HOST_PORT),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(FIELD_HOST_LASTCONNECT),
			COL_COLOR = c.getColumnIndexOrThrow(FIELD_HOST_COLOR),
			COL_USEKEYS = c.getColumnIndexOrThrow(FIELD_HOST_USEKEYS),
			COL_POSTLOGIN = c.getColumnIndexOrThrow(FIELD_HOST_POSTLOGIN),
			COL_PUBKEYID = c.getColumnIndexOrThrow(FIELD_HOST_PUBKEYID),
			COL_WANTSESSION = c.getColumnIndexOrThrow(FIELD_HOST_WANTSESSION),
			COL_COMPRESSION = c.getColumnIndexOrThrow(FIELD_HOST_COMPRESSION),
			COL_ENCODING = c.getColumnIndexOrThrow(FIELD_HOST_ENCODING);

		while (c.moveToNext()) {
			HostBean host = new HostBean();

			host.setId(c.getLong(COL_ID));
			host.setNickname(c.getString(COL_NICKNAME));
			host.setUsername(c.getString(COL_USERNAME));
			host.setHostname(c.getString(COL_HOSTNAME));
			host.setPort(c.getInt(COL_PORT));
			host.setLastConnect(c.getLong(COL_LASTCONNECT));
			host.setColor(c.getString(COL_COLOR));
			host.setUseKeys(Boolean.valueOf(c.getString(COL_USEKEYS)));
			host.setPostLogin(c.getString(COL_POSTLOGIN));
			host.setPubkeyId(c.getLong(COL_PUBKEYID));
			host.setWantSession(Boolean.valueOf(c.getString(COL_WANTSESSION)));
			host.setCompression(Boolean.valueOf(c.getString(COL_COMPRESSION)));
			host.setEncoding(c.getString(COL_ENCODING));

			hosts.add(host);
		}

		c.close();
		db.close();

		return hosts;
	}

	/**
	 * @param nickname
	 * @param username
	 * @param hostname
	 * @param port
	 * @return
	 */
	public HostBean findHost(String nickname, String username, String hostname,
			int port) {
		SQLiteDatabase db = this.getReadableDatabase();

		Cursor c = db.query(TABLE_HOSTS, null,
				FIELD_HOST_NICKNAME + " = ? AND " +
					FIELD_HOST_USERNAME + " = ? AND " +
					FIELD_HOST_HOSTNAME + " = ? AND " +
					FIELD_HOST_PORT + " = ?",
				new String[] { nickname, username, hostname, String.valueOf(port) },
				null, null, null);

		HostBean host = null;

		if (c != null) {
			if (c.moveToFirst())
				host = createHostBean(c);

			c.close();
		}

		db.close();

		return host;
	}

	/**
	 * @param hostId
	 * @return
	 */
	public HostBean findHostById(long hostId) {
		SQLiteDatabase db = this.getReadableDatabase();

		Cursor c = db.query(TABLE_HOSTS, null,
				"_id = ?", new String[] { String.valueOf(hostId) },
				null, null, null);

		HostBean host = null;

		if (c != null) {
			if (c.moveToFirst())
				host = createHostBean(c);

			c.close();
		}

		db.close();

		return host;
	}

	private HostBean createHostBean(Cursor c) {
		HostBean host = new HostBean();

		host.setId(c.getLong(c.getColumnIndexOrThrow("_id")));
		host.setNickname(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_NICKNAME)));
		host.setUsername(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_USERNAME)));
		host.setHostname(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_HOSTNAME)));
		host.setPort(c.getInt(c.getColumnIndexOrThrow(FIELD_HOST_PORT)));
		host.setLastConnect(c.getLong(c.getColumnIndexOrThrow(FIELD_HOST_LASTCONNECT)));
		host.setColor(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_COLOR)));
		host.setUseKeys(Boolean.valueOf(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_USEKEYS))));
		host.setPostLogin(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_POSTLOGIN)));
		host.setPubkeyId(c.getLong(c.getColumnIndexOrThrow(FIELD_HOST_PUBKEYID)));
		host.setWantSession(Boolean.valueOf(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_WANTSESSION))));
		host.setCompression(Boolean.valueOf(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_COMPRESSION))));
		host.setEncoding(c.getString(c.getColumnIndexOrThrow(FIELD_HOST_ENCODING)));

		return host;
	}

	/**
	 * Record the given hostkey into database under this nickname.
	 * @param hostname
	 * @param port
	 * @param hostkeyalgo
	 * @param hostkey
	 */
	public void saveKnownHost(String hostname, int port, String hostkeyalgo, byte[] hostkey) {
		SQLiteDatabase db = this.getReadableDatabase();

		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_HOSTKEYALGO, hostkeyalgo);
		values.put(FIELD_HOST_HOSTKEY, hostkey);

		db.update(TABLE_HOSTS, values,
				FIELD_HOST_HOSTNAME + " = ? AND " + FIELD_HOST_PORT + " = ?",
				new String[] { hostname, String.valueOf(port) });
		Log.d(TAG, String.format("Finished saving hostkey information for '%s'", hostname));

		db.close();
	}

	/**
	 * Build list of known hosts for Trilead library.
	 * @return
	 */
	public KnownHosts getKnownHosts() {
		KnownHosts known = new KnownHosts();

		SQLiteDatabase db = this.getReadableDatabase();
		Cursor c = db.query(TABLE_HOSTS, new String[] { FIELD_HOST_HOSTNAME,
				FIELD_HOST_PORT, FIELD_HOST_HOSTKEYALGO, FIELD_HOST_HOSTKEY },
				null, null, null, null, null);

		if (c != null) {
			int COL_HOSTNAME = c.getColumnIndexOrThrow(FIELD_HOST_HOSTNAME),
				COL_PORT = c.getColumnIndexOrThrow(FIELD_HOST_PORT),
				COL_HOSTKEYALGO = c.getColumnIndexOrThrow(FIELD_HOST_HOSTKEYALGO),
				COL_HOSTKEY = c.getColumnIndexOrThrow(FIELD_HOST_HOSTKEY);

			while (c.moveToNext()) {
				String hostname = c.getString(COL_HOSTNAME),
					hostkeyalgo = c.getString(COL_HOSTKEYALGO);
				int port = c.getInt(COL_PORT);
				byte[] hostkey = c.getBlob(COL_HOSTKEY);

				if (hostkeyalgo == null || hostkeyalgo.length() == 0) continue;
				if (hostkey == null || hostkey.length == 0) continue;

				try {
					known.addHostkey(new String[] { String.format("%s:%d", hostname, port) }, hostkeyalgo, hostkey);
				} catch(Exception e) {
					Log.e(TAG, "Problem while adding a known host from database", e);
				}
			}

			c.close();
		}

		db.close();

		return known;
	}

	/**
	 * Unset any hosts using a pubkey ID that has been deleted.
	 * @param pubkeyId
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

	/*
	 * Methods for dealing with port forwards attached to hosts
	 */

	/**
	 * Returns a list of all the port forwards associated with a particular host ID.
	 * @param host the host for which we want the port forward list
	 * @return port forwards associated with host ID
	 */
	public List<PortForwardBean> getPortForwardsForHost(HostBean host) {
		SQLiteDatabase db = this.getReadableDatabase();
		List<PortForwardBean> portForwards = new LinkedList<PortForwardBean>();

		Cursor c = db.query(TABLE_PORTFORWARDS, new String[] {
				"_id", FIELD_PORTFORWARD_NICKNAME, FIELD_PORTFORWARD_TYPE, FIELD_PORTFORWARD_SOURCEPORT,
				FIELD_PORTFORWARD_DESTADDR, FIELD_PORTFORWARD_DESTPORT },
				FIELD_PORTFORWARD_HOSTID + " = ?", new String[] { String.valueOf(host.getId()) },
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
		db.close();

		return portForwards;
	}

	/**
	 * Update the parameters of a port forward in the database.
	 * @param pfb {@link PortForwardBean} to save
	 * @return true on success
	 */
	public boolean savePortForward(PortForwardBean pfb) {
		boolean success = false;
		SQLiteDatabase db = this.getWritableDatabase();

		if (pfb.getId() < 0) {
			long id = db.insert(TABLE_PORTFORWARDS, null, pfb.getValues());
			pfb.setId(id);
			success = true;
		} else {
			if (db.update(TABLE_PORTFORWARDS, pfb.getValues(), "_id = ?", new String[] { String.valueOf(pfb.getId()) }) > 0)
				success = true;
		}

		db.close();

		return success;
	}

	/**
	 * Deletes a port forward from the database.
	 * @param pfb {@link PortForwardBean} to delete
	 */
	public void deletePortForward(PortForwardBean pfb) {
		if (pfb.getId() < 0)
			return;

		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_PORTFORWARDS, "_id = ?", new String[] { String.valueOf(pfb.getId()) });
		db.close();
	}
}
