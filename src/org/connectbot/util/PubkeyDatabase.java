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

import org.connectbot.bean.PubkeyBean;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Public Key Encryption database. Contains private and public key pairs
 * for public key authentication.
 *
 * @author Kenny Root
 */
public class PubkeyDatabase extends SQLiteOpenHelper {
	public final static String TAG = "ConnectBot.PubkeyDatabase";

	public final static String DB_NAME = "pubkeys";
	public final static int DB_VERSION = 1;

	public final static String TABLE_PUBKEYS = "pubkeys";
	public final static String FIELD_PUBKEY_NICKNAME = "nickname";
	public final static String FIELD_PUBKEY_TYPE = "type";
	public final static String FIELD_PUBKEY_PRIVATE = "private";
	public final static String FIELD_PUBKEY_PUBLIC = "public";
	public final static String FIELD_PUBKEY_ENCRYPTED = "encrypted";
	public final static String FIELD_PUBKEY_STARTUP = "startup";

	public final static String KEY_TYPE_RSA = "RSA",
		KEY_TYPE_DSA = "DSA",
		KEY_TYPE_IMPORTED = "IMPORTED";

	private Context context;

	public PubkeyDatabase(Context context) {
		super(context, DB_NAME, null, DB_VERSION);

		this.context = context;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE " + TABLE_PUBKEYS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_PUBKEY_NICKNAME + " TEXT, "
				+ FIELD_PUBKEY_TYPE + " TEXT, "
				+ FIELD_PUBKEY_PRIVATE + " BLOB, "
				+ FIELD_PUBKEY_PUBLIC + " BLOB, "
				+ FIELD_PUBKEY_ENCRYPTED + " INTEGER, "
				+ FIELD_PUBKEY_STARTUP + " INTEGER)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}

	/**
	 * Delete a specific host by its <code>_id</code> value.
	 */
	public void deletePubkey(PubkeyBean pubkey) {
		HostDatabase hostdb = new HostDatabase(context);
		hostdb.stopUsingPubkey(pubkey.getId());
		hostdb.close();

		SQLiteDatabase db = getWritableDatabase();
		db.delete(TABLE_PUBKEYS, "_id = ?", new String[] { Long.toString(pubkey.getId()) });
		db.close();
	}

	/**
	 * Return a cursor that contains information about all known hosts.
	 */
	/*
	public Cursor allPubkeys() {
		SQLiteDatabase db = this.getReadableDatabase();
		return db.query(TABLE_PUBKEYS, new String[] { "_id",
				FIELD_PUBKEY_NICKNAME, FIELD_PUBKEY_TYPE, FIELD_PUBKEY_PRIVATE,
				FIELD_PUBKEY_PUBLIC, FIELD_PUBKEY_ENCRYPTED, FIELD_PUBKEY_STARTUP },
				null, null, null, null, null);
	}*/

	public List<PubkeyBean> allPubkeys() {
		return getPubkeys(null, null);
	}

	public List<PubkeyBean> getAllStartPubkeys() {
		return getPubkeys(FIELD_PUBKEY_STARTUP + " = 1 AND " + FIELD_PUBKEY_ENCRYPTED + " = 0", null);
	}

	private List<PubkeyBean> getPubkeys(String selection, String[] selectionArgs) {
		SQLiteDatabase db = getReadableDatabase();

		List<PubkeyBean> pubkeys = new LinkedList<PubkeyBean>();

		Cursor c = db.query(TABLE_PUBKEYS, null, selection, selectionArgs, null, null, null);

		if (c != null) {
			final int COL_ID = c.getColumnIndexOrThrow("_id"),
				COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_PUBKEY_NICKNAME),
				COL_TYPE = c.getColumnIndexOrThrow(FIELD_PUBKEY_TYPE),
				COL_PRIVATE = c.getColumnIndexOrThrow(FIELD_PUBKEY_PRIVATE),
				COL_PUBLIC = c.getColumnIndexOrThrow(FIELD_PUBKEY_PUBLIC),
				COL_ENCRYPTED = c.getColumnIndexOrThrow(FIELD_PUBKEY_ENCRYPTED),
				COL_STARTUP = c.getColumnIndexOrThrow(FIELD_PUBKEY_STARTUP);

			while (c.moveToNext()) {
				PubkeyBean pubkey = new PubkeyBean();

				pubkey.setId(c.getLong(COL_ID));
				pubkey.setNickname(c.getString(COL_NICKNAME));
				pubkey.setType(c.getString(COL_TYPE));
				pubkey.setPrivateKey(c.getBlob(COL_PRIVATE));
				pubkey.setPublicKey(c.getBlob(COL_PUBLIC));
				pubkey.setEncrypted(c.getInt(COL_ENCRYPTED) > 0);
				pubkey.setStartup(c.getInt(COL_STARTUP) > 0);

				pubkeys.add(pubkey);
			}

			c.close();
		}

		db.close();

		return pubkeys;
	}

	/**
	 * @param hostId
	 * @return
	 */
	public PubkeyBean findPubkeyById(long pubkeyId) {
		SQLiteDatabase db = getReadableDatabase();

		Cursor c = db.query(TABLE_PUBKEYS, null,
				"_id = ?", new String[] { String.valueOf(pubkeyId) },
				null, null, null);

		PubkeyBean pubkey = null;

		if (c != null) {
			if (c.moveToFirst())
				pubkey = createPubkeyBean(c);

			c.close();
		}

		db.close();

		return pubkey;
	}

	private PubkeyBean createPubkeyBean(Cursor c) {
		PubkeyBean pubkey = new PubkeyBean();

		pubkey.setId(c.getLong(c.getColumnIndexOrThrow("_id")));
		pubkey.setNickname(c.getString(c.getColumnIndexOrThrow(FIELD_PUBKEY_NICKNAME)));
		pubkey.setType(c.getString(c.getColumnIndexOrThrow(FIELD_PUBKEY_TYPE)));
		pubkey.setPrivateKey(c.getBlob(c.getColumnIndexOrThrow(FIELD_PUBKEY_PRIVATE)));
		pubkey.setPublicKey(c.getBlob(c.getColumnIndexOrThrow(FIELD_PUBKEY_PUBLIC)));
		pubkey.setEncrypted(c.getInt(c.getColumnIndexOrThrow(FIELD_PUBKEY_ENCRYPTED)) > 0);
		pubkey.setStartup(c.getInt(c.getColumnIndexOrThrow(FIELD_PUBKEY_STARTUP)) > 0);

		return pubkey;
	}

	/**
	 * Pull all values for a given column as a list of Strings, probably for use
	 * in a ListPreference. Sorted by <code>_id</code> ascending.
	 */
	public List<CharSequence> allValues(String column) {
		List<CharSequence> list = new LinkedList<CharSequence>();

		SQLiteDatabase db = this.getReadableDatabase();
		Cursor c = db.query(TABLE_PUBKEYS, new String[] { "_id", column },
				null, null, null, null, "_id ASC");

		if (c != null) {
			int COL = c.getColumnIndexOrThrow(column);

			while (c.moveToNext())
				list.add(c.getString(COL));

			c.close();
		}

		db.close();

		return list;
	}

	public String getNickname(long id) {
		String nickname = null;

		SQLiteDatabase db = this.getReadableDatabase();
		Cursor c = db.query(TABLE_PUBKEYS, new String[] { "_id",
				FIELD_PUBKEY_NICKNAME }, "_id = ?",
				new String[] { Long.toString(id) }, null, null, null);

		if (c != null) {
			if (c.moveToFirst())
				nickname = c.getString(c.getColumnIndexOrThrow(FIELD_PUBKEY_NICKNAME));

			c.close();
		}

		db.close();

		return nickname;
	}

/*
	public void setOnStart(long id, boolean onStart) {

		SQLiteDatabase db = this.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(FIELD_PUBKEY_STARTUP, onStart ? 1 : 0);

		db.update(TABLE_PUBKEYS, values, "_id = ?", new String[] { Long.toString(id) });

	}

	public boolean changePassword(long id, String oldPassword, String newPassword) throws NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException, InvalidKeyException, BadPaddingException {
		SQLiteDatabase db = this.getWritableDatabase();

		Cursor c = db.query(TABLE_PUBKEYS, new String[] { FIELD_PUBKEY_TYPE,
				FIELD_PUBKEY_PRIVATE, FIELD_PUBKEY_ENCRYPTED },
				"_id = ?", new String[] { String.valueOf(id) },
				null, null, null);

		if (!c.moveToFirst())
			return false;

		String keyType = c.getString(0);
		byte[] encPriv = c.getBlob(1);
		c.close();

		PrivateKey priv;
		try {
			priv = PubkeyUtils.decodePrivate(encPriv, keyType, oldPassword);
		} catch (InvalidKeyException e) {
			return false;
		} catch (BadPaddingException e) {
			return false;
		} catch (InvalidKeySpecException e) {
			return false;
		}

		ContentValues values = new ContentValues();
		values.put(FIELD_PUBKEY_PRIVATE, PubkeyUtils.getEncodedPrivate(priv, newPassword));
		values.put(FIELD_PUBKEY_ENCRYPTED, newPassword.length() > 0 ? 1 : 0);
		db.update(TABLE_PUBKEYS, values, "_id = ?", new String[] { String.valueOf(id) });

		return true;
	}
	*/

	/**
	 * @param pubkey
	 */
	public PubkeyBean savePubkey(PubkeyBean pubkey) {
		SQLiteDatabase db = this.getWritableDatabase();
		boolean success = false;

		ContentValues values = pubkey.getValues();

		if (pubkey.getId() > 0) {
			values.remove("_id");
			if (db.update(TABLE_PUBKEYS, values, "_id = ?", new String[] { String.valueOf(pubkey.getId()) }) > 0)
				success = true;
		}

		if (!success) {
			long id = db.insert(TABLE_PUBKEYS, null, pubkey.getValues());
			pubkey.setId(id);
		}

		db.close();

		return pubkey;
	}
}
