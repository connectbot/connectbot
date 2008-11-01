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

package org.connectbot;

import java.security.PublicKey;
import java.util.EventListener;

import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TableRow;
import android.widget.TextView;

public class PubkeyListActivity extends ListActivity implements EventListener {
	public final static String TAG = PubkeyListActivity.class.toString();

	protected PubkeyDatabase pubkeydb;
	protected Cursor pubkeys;
	
	protected int COL_ID, COL_NICKNAME, COL_TYPE, COL_PRIVATE, COL_PUBLIC, COL_ENCRYPTED, COL_STARTUP;

	protected ClipboardManager clipboard;
	
	protected LayoutInflater inflater = null;

	protected Dialog changePasswordDialog;

	@Override
    public void onStart() {
		super.onStart();
		
		if(this.pubkeydb == null)
			this.pubkeydb = new PubkeyDatabase(this);
		
		ListView list = this.getListView();
		this.registerForContextMenu(list);
	}
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_pubkeylist);
		
		// connect with hosts database and populate list
		this.pubkeydb = new PubkeyDatabase(this);
		
		this.updateCursor();
		
		this.COL_ID = pubkeys.getColumnIndexOrThrow("_id");
		this.COL_NICKNAME = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_NICKNAME);
		this.COL_TYPE = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_TYPE);
		this.COL_PRIVATE = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PRIVATE);
		this.COL_PUBLIC = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PUBLIC);
		this.COL_ENCRYPTED = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_ENCRYPTED);
		this.COL_STARTUP = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_STARTUP);
		
		this.clipboard = (ClipboardManager)this.getSystemService(CLIPBOARD_SERVICE);
		
		this.inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem generatekey = menu.add(R.string.pubkey_generate);
		generatekey.setIcon(android.R.drawable.ic_lock_lock);
		generatekey.setIntent(new Intent(PubkeyListActivity.this, GeneratePubkeyActivity.class));

		// TODO: allow importing of keys
		//MenuItem importkey = menu.add("Import");
		//importkey.setIcon(android.R.drawable.ic_lock_lock);
		//importkey.setIntent(new Intent(PubkeyListActivity.this, ImportPubkeyActivity.class));
		
		return true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		// Create menu to handle deleting and editing pubkey
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		Cursor cursor = (Cursor) this.getListView().getItemAtPosition(info.position);

		final String nickname = cursor.getString(COL_NICKNAME);
		menu.setHeaderTitle(nickname);
		
		final int id = cursor.getInt(COL_ID);
		final byte[] pubkeyEncoded = cursor.getBlob(COL_PUBLIC);
		final String keyType = cursor.getString(COL_TYPE);
		final int encrypted = cursor.getInt(COL_ENCRYPTED);

		MenuItem delete = menu.add(R.string.pubkey_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(PubkeyListActivity.this)
					.setMessage(getString(R.string.delete_message, nickname))
					.setPositiveButton(R.string.delete_pos, new DialogInterface.OnClickListener() {
		                public void onClick(DialogInterface dialog, int which) {
		                	pubkeydb.deletePubkey(id);
		    				updateHandler.sendEmptyMessage(-1);
		                }
		            })
		            .setNegativeButton(R.string.delete_neg, null).create().show();

				return true;
			}
		});
		
		MenuItem copyToClipboard = menu.add(R.string.pubkey_copy_clipboard);
		copyToClipboard.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {				
				try {					
					PublicKey pk = PubkeyUtils.decodePublic(pubkeyEncoded, keyType);
					String openSSHPubkey = new String(PubkeyUtils.convertToOpenSSHFormat(pk));
										
					clipboard.setText(openSSHPubkey);
				} catch (Exception e) {
					e.printStackTrace();
				} 
				return true;
			}
		});
		
		MenuItem changePassword = menu.add(R.string.pubkey_change_password);
		changePassword.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				final View changePasswordView = inflater.inflate(R.layout.dia_changepassword, null, false);
				((TableRow)changePasswordView.findViewById(R.id.old_password_prompt))
					.setVisibility(encrypted != 0 ? View.VISIBLE : View.GONE);
				new AlertDialog.Builder(PubkeyListActivity.this)
				.setView(changePasswordView)
				.setPositiveButton(R.string.button_change, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						String oldPassword = ((EditText)changePasswordView.findViewById(R.id.old_password)).getText().toString();
						String password1 = ((EditText)changePasswordView.findViewById(R.id.password1)).getText().toString();
						String password2 = ((EditText)changePasswordView.findViewById(R.id.password2)).getText().toString();

						if (!password1.equals(password2)) {
							new AlertDialog.Builder(PubkeyListActivity.this)
							.setMessage(R.string.alert_passwords_do_not_match_msg)
							.setPositiveButton(android.R.string.ok, null)
							.create().show();
							return;
						}
						
						try {
							if (!pubkeydb.changePassword(id, oldPassword, password1))
								new AlertDialog.Builder(PubkeyListActivity.this)
								.setMessage(R.string.alert_wrong_password_msg)
								.setPositiveButton(android.R.string.ok, null)
								.create().show();
							else
								updateHandler.sendEmptyMessage(-1);
						} catch (Exception e) {
							Log.e(TAG, "Could not change private key password");
							e.printStackTrace();
							new AlertDialog.Builder(PubkeyListActivity.this)
							.setMessage(R.string.alert_key_corrupted_msg)
							.setPositiveButton(android.R.string.ok, null)
							.create().show();
						}
					}
				})
				.setNegativeButton(android.R.string.cancel, null).create().show();
			
			return true;
			}
		});
	}
	
	public Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			PubkeyListActivity.this.updateCursor();
		}
	};
	
	protected void updateCursor() {
		/*
		if (this.pubkeys != null)
			pubkeys.requery();
		*/
		// refresh cursor because of possible sorting change
		if(this.pubkeys != null)
			this.pubkeys.close();
		if(this.pubkeydb == null) return;
		
		this.pubkeys = this.pubkeydb.allPubkeys();
		this.setListAdapter(new PubkeyCursorAdapter(this, this.pubkeys));
		//this.startManagingCursor(pubkeys);
	}
	
	class PubkeyCursorAdapter extends CursorAdapter {
		private final LayoutInflater mInflater;
		private final int mNickname;
		private final int mPubkey;
		private final int mKeyType;
		private final int mEncrypted;
		
		public PubkeyCursorAdapter(Context context, Cursor c) {
			super(context, c);
			
            mInflater = LayoutInflater.from(context);
            mNickname = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_NICKNAME);
            mPubkey = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PUBLIC);
            mEncrypted = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_ENCRYPTED);
            mKeyType = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_TYPE);
		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			TextView text1 = (TextView) view.findViewById(android.R.id.text1);
			TextView text2 = (TextView) view.findViewById(android.R.id.text2);
			ImageView icon1 = (ImageView) view.findViewById(android.R.id.icon1);
			
			text1.setText(cursor.getString(mNickname));

			String keyType = cursor.getString(mKeyType);
			int encrypted = cursor.getInt(mEncrypted);
			PublicKey pk;
			try {
				pk = PubkeyUtils.decodePublic(cursor.getBlob(mPubkey), keyType);
				text2.setText(PubkeyUtils.describeKey(pk, encrypted));
			} catch (Exception e) {
				e.printStackTrace();
				
				text2.setText(R.string.pubkey_unknown_format);
				Log.e(TAG, "Error decoding public key at " + cursor.toString());
			}
			
			if (encrypted == 0)
				icon1.setImageResource(R.drawable.pubkey_unlocked);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final LinearLayout view = (LinearLayout) mInflater.inflate(
                    R.layout.item_pubkey, parent, false);
            return view;
		}
	}

}
