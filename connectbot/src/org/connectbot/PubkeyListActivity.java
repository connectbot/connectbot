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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.connectbot.service.TerminalManager;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.crypto.PEMStructure;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter.ViewBinder;

public class PubkeyListActivity extends ListActivity implements EventListener {
	public final static String TAG = PubkeyListActivity.class.toString();

	protected PubkeyDatabase pubkeydb;
	protected Cursor pubkeys;
	
	protected int COL_ID, COL_NICKNAME, COL_TYPE, COL_PRIVATE, COL_PUBLIC, COL_ENCRYPTED, COL_STARTUP;

	protected ClipboardManager clipboard;
	
	protected LayoutInflater inflater = null;
	
	protected TerminalManager bound = null;
	
	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// update our listview binder to find the service
			PubkeyListActivity.this.updateCursor();
		}

		public void onServiceDisconnected(ComponentName className) {
			bound = null;
			PubkeyListActivity.this.updateCursor();
		}
	};

	@Override
    public void onStart() {
		super.onStart();
		
		this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

		if(this.pubkeydb == null)
			this.pubkeydb = new PubkeyDatabase(this);
	}
	
	@Override
    public void onStop() {
		super.onStop();
	
		this.unbindService(connection);

		if(this.pubkeydb != null) {
			this.pubkeydb.close();
			this.pubkeydb = null;
		}
	}
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_pubkeylist);
		
		// connect with hosts database and populate list
		this.pubkeydb = new PubkeyDatabase(this);
		
		this.updateCursor();
		
		this.registerForContextMenu(this.getListView());
		
		this.COL_ID = pubkeys.getColumnIndexOrThrow("_id");
		this.COL_NICKNAME = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_NICKNAME);
		this.COL_TYPE = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_TYPE);
		this.COL_PRIVATE = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PRIVATE);
		this.COL_PUBLIC = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PUBLIC);
		this.COL_ENCRYPTED = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_ENCRYPTED);
		this.COL_STARTUP = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_STARTUP);
		
		this.getListView().setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
				Cursor cursor = (Cursor) getListView().getItemAtPosition(position);
				String nickname = cursor.getString(COL_NICKNAME);
				boolean loaded = bound.isKeyLoaded(nickname);
				
				// handle toggling key in-memory on/off
				if(loaded) {
					bound.removeKey(nickname);
					updateHandler.sendEmptyMessage(-1);
				} else {
					handleAddKey(cursor);
				}

			}
		});
		
		this.clipboard = (ClipboardManager)this.getSystemService(CLIPBOARD_SERVICE);
		
		this.inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	
	/**
	 * Read given file into memory as <code>byte[]</code>.
	 */
	public static byte[] readRaw(File file) throws Exception {
		InputStream is = new FileInputStream(file);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		
		int bytesRead;
		byte[] buffer = new byte[1024];
		while ((bytesRead = is.read(buffer)) != -1) {
			os.write(buffer, 0, bytesRead);
		}

		os.flush();
		os.close();
		is.close();

		return os.toByteArray();
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem generatekey = menu.add(R.string.pubkey_generate);
		generatekey.setIcon(android.R.drawable.ic_menu_manage);
		generatekey.setIntent(new Intent(PubkeyListActivity.this, GeneratePubkeyActivity.class));

		MenuItem importkey = menu.add("Import");
		importkey.setIcon(android.R.drawable.ic_menu_upload);
		importkey.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				
				// TODO: replace this with well-known intent over to file browser
				// TODO: if browser not installed (?) then fallback to this simple method?
				
				// build list of all files in sdcard root
				final File sdcard = Environment.getExternalStorageDirectory();
				Log.d(TAG, sdcard.toString());
				List<String> names = new LinkedList<String>();
				for(File file : sdcard.listFiles()) {
					if(file.isDirectory()) continue;
					names.add(file.getName());
				}
				final String[] namesList = names.toArray(new String[] {});
				Log.d(TAG, names.toString());

				// prompt user to select any file from the sdcard root
				new AlertDialog.Builder(PubkeyListActivity.this)
					.setTitle("Pick from /sdcard")
					.setItems(namesList, new OnClickListener() {
						public void onClick(DialogInterface arg0, int arg1) {
							// find the exact file selected
							String name = namesList[arg1];
							File actual = new File(sdcard, name);
							
							// parse the actual key once to check if its encrypted
							// then save original file contents into our database
							try {
								byte[] raw = readRaw(actual);
								PEMStructure struct = PEMDecoder.parsePEM(new String(raw).toCharArray());
								boolean encrypted = PEMDecoder.isPEMEncrypted(struct);
								
								// write new value into database
								pubkeydb.createPubkey(null, name, PubkeyDatabase.KEY_TYPE_IMPORTED, raw, new byte[] {}, encrypted, false);
								updateHandler.sendEmptyMessage(-1);
								
							} catch(Exception e) {
								Log.e(TAG, "Problem parsing imported private key", e);
								Toast.makeText(PubkeyListActivity.this, "Problem parsing imported private key", Toast.LENGTH_LONG).show();
							}
						}
					})
					.setNegativeButton("Cancel", null).create().show();

				return true;
			}
		});
		
		return true;
	}
	
	protected void handleAddKey(final Cursor c) {
		int encrypted = c.getInt(COL_ENCRYPTED);
		
		if(encrypted != 0) {
			final View view = inflater.inflate(R.layout.dia_password, null);
			final EditText passwordField = (EditText)view.findViewById(android.R.id.text1);
			
			new AlertDialog.Builder(PubkeyListActivity.this)
				.setView(view)
				.setPositiveButton("Unlock key", new DialogInterface.OnClickListener() {
		            public void onClick(DialogInterface dialog, int which) {
		    			handleAddKey(c, passwordField.getText().toString());
		            }
		        })
		        .setNegativeButton("Cancel", null).create().show();
		} else {
			handleAddKey(c, null);

		}
			

	}
	
	protected void handleAddKey(Cursor c, String password) {
		String keyNickname = c.getString(COL_NICKNAME);
		Object trileadKey = null;
		String type = c.getString(COL_TYPE);
		if(PubkeyDatabase.KEY_TYPE_IMPORTED.equals(type)) {
			// load specific key using pem format
			byte[] raw = c.getBlob(COL_PRIVATE);
			try {
				trileadKey = PEMDecoder.decode(new String(raw).toCharArray(), password);
			} catch(Exception e) {
				String message = String.format("Bad password for key '%s'. Authentication failed.", keyNickname);
				Log.e(TAG, message, e);
				Toast.makeText(PubkeyListActivity.this, message, Toast.LENGTH_LONG);
			}
			
		} else {
			// load using internal generated format
			PrivateKey privKey = null;
			PublicKey pubKey = null;
			try {
				privKey = PubkeyUtils.decodePrivate(c.getBlob(COL_PRIVATE), c.getString(COL_TYPE), password);
				pubKey = PubkeyUtils.decodePublic(c.getBlob(COL_PUBLIC), c.getString(COL_TYPE));
			} catch (Exception e) {
				String message = String.format("Bad password for key '%s'. Authentication failed.", keyNickname);
				Log.e(TAG, message, e);
				Toast.makeText(PubkeyListActivity.this, message, Toast.LENGTH_LONG);
			}

			// convert key to trilead format
			trileadKey = PubkeyUtils.convertToTrilead(privKey, pubKey);
			Log.d(TAG, "Unlocked key " + PubkeyUtils.formatKey(pubKey));
		}

		if(trileadKey == null) return;
		
		Log.d(TAG, String.format("Unlocked key '%s'", keyNickname));

		// save this key in-memory if option enabled
		if(bound.isSavingKeys()) {
			bound.addKey(keyNickname, trileadKey);
		}
		
		updateHandler.sendEmptyMessage(-1);

		
	}
	
	protected MenuItem onstartToggle = null;
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		// Create menu to handle deleting and editing pubkey
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final Cursor cursor = (Cursor) this.getListView().getItemAtPosition(info.position);

		final String nickname = cursor.getString(COL_NICKNAME);
		menu.setHeaderTitle(nickname);
		
		// TODO: option load/unload key from in-memory list
		// prompt for password as needed for passworded keys
		
		final int id = cursor.getInt(COL_ID);
		final byte[] pubkeyEncoded = cursor.getBlob(COL_PUBLIC);
		final String keyType = cursor.getString(COL_TYPE);
		final int encrypted = cursor.getInt(COL_ENCRYPTED);
		
		// cant change password or clipboard imported keys
		boolean imported = PubkeyDatabase.KEY_TYPE_IMPORTED.equals(cursor.getString(COL_TYPE));
		final boolean loaded = bound.isKeyLoaded(nickname);
		final boolean onstart = (cursor.getInt(COL_STARTUP) == 1);

		MenuItem load = menu.add(loaded ? "Unload from memory" : "Load into memory");
		load.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				if(loaded) {
					bound.removeKey(nickname);
					updateHandler.sendEmptyMessage(-1);
				} else {
					handleAddKey(cursor);
					//bound.addKey(nickname, trileadKey);
				}
				return true;
			}
		});
		
		onstartToggle = menu.add("Load key on start");
		onstartToggle.setEnabled((encrypted == 0));
		onstartToggle.setCheckable(true);
		onstartToggle.setChecked(onstart);
		onstartToggle.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// toggle onstart status
				pubkeydb.setOnStart(id, !onstart);
				updateHandler.sendEmptyMessage(-1);
				return true;
			}
		});

		MenuItem copyToClipboard = menu.add(R.string.pubkey_copy_clipboard);
		copyToClipboard.setEnabled(!imported);
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
		changePassword.setEnabled(!imported);
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
							Log.e(TAG, "Could not change private key password", e);
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
		
		MenuItem delete = menu.add(R.string.pubkey_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(PubkeyListActivity.this)
					.setMessage(getString(R.string.delete_message, nickname))
					.setPositiveButton(R.string.delete_pos, new DialogInterface.OnClickListener() {
		                public void onClick(DialogInterface dialog, int which) {
		                	
		                	// dont forget to remove from in-memory
		    				if(loaded)
		    					bound.removeKey(nickname);

		                	// delete from backend database and update gui
		                	pubkeydb.deletePubkey(id);
		    				updateHandler.sendEmptyMessage(-1);
		                }
		            })
		            .setNegativeButton(R.string.delete_neg, null).create().show();

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
		if (this.pubkeys != null)
			pubkeys.close();
			//pubkeys.requery();

		if (this.pubkeydb == null) return;
		
		this.pubkeys = this.pubkeydb.allPubkeys();
		
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.item_pubkey, this.pubkeys,
				new String[] { PubkeyDatabase.FIELD_PUBKEY_NICKNAME, PubkeyDatabase.FIELD_PUBKEY_TYPE, PubkeyDatabase.FIELD_PUBKEY_ENCRYPTED },
				new int[] { android.R.id.text1, android.R.id.text2, android.R.id.icon1 });
		adapter.setViewBinder(new PubkeyBinder());
		this.setListAdapter(adapter);

		//this.startManagingCursor(pubkeys);
	}
	
	class PubkeyBinder implements ViewBinder {
		public boolean setViewValue(View view, Cursor cursor, int columnIndex) {			
			switch (view.getId()) {
			case android.R.id.text2:
				int encrypted = cursor.getInt(cursor.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_ENCRYPTED));
				boolean imported = PubkeyDatabase.KEY_TYPE_IMPORTED.equals(cursor.getString(COL_TYPE));
				TextView caption = (TextView)view;
				
				if(imported) {
					// for imported keys, have trilead parse them to get stats
					try {
						byte[] raw = cursor.getBlob(cursor.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PRIVATE));
						PEMStructure struct = PEMDecoder.parsePEM(new String(raw).toCharArray());
						String type = (struct.pemType == PEMDecoder.PEM_RSA_PRIVATE_KEY) ? "RSA" : "DSA";
						caption.setText(String.format("%s unknown-bit", type));
					} catch (IOException e) {
						Log.e(TAG, "Error decoding IMPORTED public key at " + cursor.toString(), e);
					}

					
				} else {
				
					try {
						PublicKey pub = PubkeyUtils.decodePublic(cursor.getBlob(cursor.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PUBLIC)),
								cursor.getString(columnIndex));
						caption.setText(PubkeyUtils.describeKey(pub, encrypted));
					} catch (Exception e) {
						Log.e(TAG, "Error decoding public key at " + cursor.toString(), e);
						caption.setText(R.string.pubkey_unknown_format);
					}
				}
				
				return true;
				
			case android.R.id.icon1:
				
				ImageView icon = (ImageView)view;
				if(bound == null) {
					icon.setVisibility(View.GONE);
					return true;
					
				} else {
					icon.setVisibility(View.VISIBLE);
					
				}
				
				// read key in-memory status from backend terminalmanager
				String nickname = cursor.getString(COL_NICKNAME);
				boolean loaded = bound.isKeyLoaded(nickname);
				
				if(loaded)
					icon.setImageState(new int[] { android.R.attr.state_checked }, true);
				else
					icon.setImageState(new int[] {  }, true);
				return true;
			}
			
			return false;
		}	
	}
}
