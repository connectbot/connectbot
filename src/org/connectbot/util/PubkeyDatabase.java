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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Public Key Encryption database. Contains private and public key pairs
 * for public key authentication.
 * 
 * @author kroot
 */
public class PubkeyDatabase extends SQLiteOpenHelper {
	
	public final static String TAG = PubkeyDatabase.class.toString();
	
	public final static String DB_NAME = "pubkeys";
	public final static int DB_VERSION = 1;
	
	public final static String TABLE_PUBKEYS = "pubkeys";
	public final static String FIELD_PUBKEY_NICKNAME = "nickname";
	public final static String FIELD_PUBKEY_TYPE = "type";
	public final static String FIELD_PUBKEY_PRIVATE = "private";
	public final static String FIELD_PUBKEY_PUBLIC = "public";
	public final static String FIELD_PUBKEY_ENCRYPTED = "encrypted";
	public final static String FIELD_PUBKEY_STARTUP = "startup";

	public PubkeyDatabase(Context context) {
		super(context, DB_NAME, null, DB_VERSION);
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
	 * Create a new pubkey using the given parameters, and return its new
	 * <code>_id</code> value.
	 */
	public long createPubkey(SQLiteDatabase db, String nickname, String type, byte[] privatekey,
			byte[] publickey, boolean encrypted, boolean startup) {
		// create and insert new host
		
		if (db == null)
			db = this.getWritableDatabase();
		
		ContentValues values = new ContentValues();
		values.put(FIELD_PUBKEY_NICKNAME, nickname);
		values.put(FIELD_PUBKEY_TYPE, type);
		values.put(FIELD_PUBKEY_PRIVATE, privatekey);
		values.put(FIELD_PUBKEY_PUBLIC, publickey);
		values.put(FIELD_PUBKEY_ENCRYPTED, encrypted ? 1 : 0);
		values.put(FIELD_PUBKEY_STARTUP, startup ? 1 : 0);
		
		return db.insert(TABLE_PUBKEYS, null, values);		
	}
	
	/**
	 * Delete a specific host by its <code>_id</code> value.
	 */
	public void deletePubkey(long id) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.delete(TABLE_PUBKEYS, "_id = ?", new String[] { Long.toString(id) });
	}
	
	/**
	 * Return a cursor that contains information about all known hosts.
	 */
	public Cursor allPubkeys() {
		SQLiteDatabase db = this.getReadableDatabase();
		return db.query(TABLE_PUBKEYS, new String[] { "_id",
				FIELD_PUBKEY_NICKNAME, FIELD_PUBKEY_TYPE, FIELD_PUBKEY_PRIVATE,
				FIELD_PUBKEY_PUBLIC, FIELD_PUBKEY_ENCRYPTED, FIELD_PUBKEY_STARTUP },
				null, null, null, null, null);
	}
	
	public Cursor getPubkey(long id) {
		SQLiteDatabase db = this.getReadableDatabase();
		return db.query(TABLE_PUBKEYS, new String[] { "_id",
				FIELD_PUBKEY_NICKNAME, FIELD_PUBKEY_TYPE, FIELD_PUBKEY_PRIVATE,
				FIELD_PUBKEY_PUBLIC, FIELD_PUBKEY_ENCRYPTED, FIELD_PUBKEY_STARTUP },
				"_id = ?", new String[] { String.valueOf(id) },
				null, null, null);
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
	
}
