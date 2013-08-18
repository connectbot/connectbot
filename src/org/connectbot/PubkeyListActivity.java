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

package org.connectbot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.List;

import org.connectbot.bean.PubkeyBean;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;
import org.openintents.intents.FileManagerIntents;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.trilead.ssh2.crypto.Base64;
import com.trilead.ssh2.crypto.PEMDecoder;
import com.trilead.ssh2.crypto.PEMStructure;

/**
 * List public keys in database by nickname and describe their properties. Allow users to import,
 * generate, rename, and delete key pairs.
 *
 * @author Kenny Root
 */
public class PubkeyListActivity extends ListActivity implements EventListener {
	public final static String TAG = "ConnectBot.PubkeyListActivity";

	private static final int MAX_KEYFILE_SIZE = 8192;
	private static final int REQUEST_CODE_PICK_FILE = 1;

	// Constants for AndExplorer's file picking intent
	private static final String ANDEXPLORER_TITLE = "explorer_title";
	private static final String MIME_TYPE_ANDEXPLORER_FILE = "vnd.android.cursor.dir/lysesoft.andexplorer.file";

	protected PubkeyDatabase pubkeydb;
	private List<PubkeyBean> pubkeys;

	protected ClipboardManager clipboard;

	protected LayoutInflater inflater = null;

	protected TerminalManager bound = null;

	private MenuItem onstartToggle = null;
	private MenuItem confirmUse = null;

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// update our listview binder to find the service
			updateList();
		}

		public void onServiceDisconnected(ComponentName className) {
			bound = null;
			updateList();
		}
	};

	@Override
	public void onStart() {
		super.onStart();

		bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

		if(pubkeydb == null)
			pubkeydb = new PubkeyDatabase(this);
	}

	@Override
	public void onStop() {
		super.onStop();

		unbindService(connection);

		if(pubkeydb != null) {
			pubkeydb.close();
			pubkeydb = null;
		}
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_pubkeylist);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_pubkey_list)));

		// connect with hosts database and populate list
		pubkeydb = new PubkeyDatabase(this);

		updateList();

		registerForContextMenu(getListView());

		getListView().setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> adapter, View view, int position, long id) {
				PubkeyBean pubkey = (PubkeyBean) getListView().getItemAtPosition(position);
				boolean loaded = bound.isKeyLoaded(pubkey.getNickname());

				// handle toggling key in-memory on/off
				if(loaded) {
					bound.removeKey(pubkey.getNickname());
					updateList();
				} else {
					handleAddKey(pubkey);
				}

			}
		});

		clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);

		inflater = LayoutInflater.from(this);
	}

	/**
	 * Read given file into memory as <code>byte[]</code>.
	 */
	protected static byte[] readRaw(File file) throws Exception {
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

		MenuItem importkey = menu.add(R.string.pubkey_import);
		importkey.setIcon(android.R.drawable.ic_menu_upload);
		importkey.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Uri sdcard = Uri.fromFile(Environment.getExternalStorageDirectory());
				String pickerTitle = getString(R.string.pubkey_list_pick);

				// Try to use OpenIntent's file browser to pick a file
				Intent intent = new Intent(FileManagerIntents.ACTION_PICK_FILE);
				intent.setData(sdcard);
				intent.putExtra(FileManagerIntents.EXTRA_TITLE, pickerTitle);
				intent.putExtra(FileManagerIntents.EXTRA_BUTTON_TEXT, getString(android.R.string.ok));

				try {
					startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
				} catch (ActivityNotFoundException e) {
					// If OI didn't work, try AndExplorer
					intent = new Intent(Intent.ACTION_PICK);
					intent.setDataAndType(sdcard, MIME_TYPE_ANDEXPLORER_FILE);
					intent.putExtra(ANDEXPLORER_TITLE, pickerTitle);

					try {
						startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
					} catch (ActivityNotFoundException e1) {
						pickFileSimple();
					}
				}

				return true;
			}
		});

		return true;
	}

	protected void handleAddKey(final PubkeyBean pubkey) {
		if (pubkey.isEncrypted()) {
			final View view = inflater.inflate(R.layout.dia_password, null);
			final EditText passwordField = (EditText)view.findViewById(android.R.id.text1);

			new AlertDialog.Builder(PubkeyListActivity.this)
				.setView(view)
				.setPositiveButton(R.string.pubkey_unlock, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						handleAddKey(pubkey, passwordField.getText().toString());
					}
				})
				.setNegativeButton(android.R.string.cancel, null).create().show();
		} else {
			handleAddKey(pubkey, null);
		}
	}

	protected void handleAddKey(PubkeyBean keybean, String password) {
		KeyPair pair = null;
		if(PubkeyDatabase.KEY_TYPE_IMPORTED.equals(keybean.getType())) {
			// load specific key using pem format
			try {
				pair = PEMDecoder.decode(new String(keybean.getPrivateKey()).toCharArray(), password);
			} catch(Exception e) {
				String message = getResources().getString(R.string.pubkey_failed_add, keybean.getNickname());
				Log.e(TAG, message, e);
				Toast.makeText(PubkeyListActivity.this, message, Toast.LENGTH_LONG).show();
			}
		} else {
			// load using internal generated format
			try {
				PrivateKey privKey = PubkeyUtils.decodePrivate(keybean.getPrivateKey(), keybean.getType(), password);
				PublicKey pubKey = PubkeyUtils.decodePublic(keybean.getPublicKey(), keybean.getType());
				Log.d(TAG, "Unlocked key " + PubkeyUtils.formatKey(pubKey));

				pair = new KeyPair(pubKey, privKey);
			} catch (Exception e) {
				String message = getResources().getString(R.string.pubkey_failed_add, keybean.getNickname());
				Log.e(TAG, message, e);
				Toast.makeText(PubkeyListActivity.this, message, Toast.LENGTH_LONG).show();
				return;
			}
		}

		if (pair == null) {
		    return;
		}

		Log.d(TAG, String.format("Unlocked key '%s'", keybean.getNickname()));

		// save this key in memory
		bound.addKey(keybean, pair, true);

		updateList();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		// Create menu to handle deleting and editing pubkey
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final PubkeyBean pubkey = (PubkeyBean) getListView().getItemAtPosition(info.position);

		menu.setHeaderTitle(pubkey.getNickname());

		// TODO: option load/unload key from in-memory list
		// prompt for password as needed for passworded keys

		// cant change password or clipboard imported keys
		final boolean imported = PubkeyDatabase.KEY_TYPE_IMPORTED.equals(pubkey.getType());
		final boolean loaded = bound.isKeyLoaded(pubkey.getNickname());

		MenuItem load = menu.add(loaded ? R.string.pubkey_memory_unload : R.string.pubkey_memory_load);
		load.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				if(loaded) {
					bound.removeKey(pubkey.getNickname());
					updateList();
				} else {
					handleAddKey(pubkey);
					//bound.addKey(nickname, trileadKey);
				}
				return true;
			}
		});

		onstartToggle = menu.add(R.string.pubkey_load_on_start);
		onstartToggle.setEnabled(!pubkey.isEncrypted());
		onstartToggle.setCheckable(true);
		onstartToggle.setChecked(pubkey.isStartup());
		onstartToggle.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// toggle onstart status
				pubkey.setStartup(!pubkey.isStartup());
				pubkeydb.savePubkey(pubkey);
				updateList();
				return true;
			}
		});

		MenuItem copyPublicToClipboard = menu.add(R.string.pubkey_copy_public);
		copyPublicToClipboard.setEnabled(!imported);
		copyPublicToClipboard.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				try {
					PublicKey pk = PubkeyUtils.decodePublic(pubkey.getPublicKey(), pubkey.getType());
					String openSSHPubkey = PubkeyUtils.convertToOpenSSHFormat(pk, pubkey.getNickname());

					clipboard.setText(openSSHPubkey);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return true;
			}
		});

		MenuItem copyPrivateToClipboard = menu.add(R.string.pubkey_copy_private);
		copyPrivateToClipboard.setEnabled(!pubkey.isEncrypted() || imported);
		copyPrivateToClipboard.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				try {
					String data = null;

					if (imported)
						data = new String(pubkey.getPrivateKey());
					else {
						PrivateKey pk = PubkeyUtils.decodePrivate(pubkey.getPrivateKey(), pubkey.getType());
						data = PubkeyUtils.exportPEM(pk, null);
					}

					clipboard.setText(data);
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
					.setVisibility(pubkey.isEncrypted() ? View.VISIBLE : View.GONE);
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
								if (!pubkey.changePassword(oldPassword, password1))
									new AlertDialog.Builder(PubkeyListActivity.this)
										.setMessage(R.string.alert_wrong_password_msg)
										.setPositiveButton(android.R.string.ok, null)
										.create().show();
								else {
									pubkeydb.savePubkey(pubkey);
									updateList();
								}
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

		confirmUse = menu.add(R.string.pubkey_confirm_use);
		confirmUse.setCheckable(true);
		confirmUse.setChecked(pubkey.isConfirmUse());
		confirmUse.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// toggle confirm use
				pubkey.setConfirmUse(!pubkey.isConfirmUse());
				pubkeydb.savePubkey(pubkey);
				updateList();
				return true;
			}
		});

		MenuItem delete = menu.add(R.string.pubkey_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(PubkeyListActivity.this)
					.setMessage(getString(R.string.delete_message, pubkey.getNickname()))
					.setPositiveButton(R.string.delete_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {

							// dont forget to remove from in-memory
							if(loaded)
								bound.removeKey(pubkey.getNickname());

							// delete from backend database and update gui
							pubkeydb.deletePubkey(pubkey);
							updateList();
						}
					})
					.setNegativeButton(R.string.delete_neg, null).create().show();

				return true;
			}
		});

	}

	protected void updateList() {
		if (pubkeydb == null) return;

		pubkeys = pubkeydb.allPubkeys();
		PubkeyAdapter adapter = new PubkeyAdapter(this, pubkeys);

		this.setListAdapter(adapter);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		switch (requestCode) {
		case REQUEST_CODE_PICK_FILE:
			if (resultCode == RESULT_OK && intent != null) {
				Uri uri = intent.getData();
				try {
					if (uri != null) {
						readKeyFromFile(new File(URI.create(uri.toString())));
					} else {
						String filename = intent.getDataString();
						if (filename != null)
							readKeyFromFile(new File(URI.create(filename)));
					}
				} catch (IllegalArgumentException e) {
					Log.e(TAG, "Couldn't read from picked file", e);
				}
			}
			break;
		}
	}

	/**
	 * @param name
	 */
	private void readKeyFromFile(File file) {
		PubkeyBean pubkey = new PubkeyBean();

		// find the exact file selected
		pubkey.setNickname(file.getName());

		if (file.length() > MAX_KEYFILE_SIZE) {
			Toast.makeText(PubkeyListActivity.this,
					R.string.pubkey_import_parse_problem,
					Toast.LENGTH_LONG).show();
			return;
		}

		// parse the actual key once to check if its encrypted
		// then save original file contents into our database
		try {
			byte[] raw = readRaw(file);

			String data = new String(raw);
			if (data.startsWith(PubkeyUtils.PKCS8_START)) {
				int start = data.indexOf(PubkeyUtils.PKCS8_START) + PubkeyUtils.PKCS8_START.length();
				int end = data.indexOf(PubkeyUtils.PKCS8_END);

				if (end > start) {
					char[] encoded = data.substring(start, end - 1).toCharArray();
					Log.d(TAG, "encoded: " + new String(encoded));
					byte[] decoded = Base64.decode(encoded);

					KeyPair kp = PubkeyUtils.recoverKeyPair(decoded);

					pubkey.setType(kp.getPrivate().getAlgorithm());
					pubkey.setPrivateKey(kp.getPrivate().getEncoded());
					pubkey.setPublicKey(kp.getPublic().getEncoded());
				} else {
					Log.e(TAG, "Problem parsing PKCS#8 file; corrupt?");
					Toast.makeText(PubkeyListActivity.this,
							R.string.pubkey_import_parse_problem,
							Toast.LENGTH_LONG).show();
				}
			} else {
				PEMStructure struct = PEMDecoder.parsePEM(new String(raw).toCharArray());
				pubkey.setEncrypted(PEMDecoder.isPEMEncrypted(struct));
				pubkey.setType(PubkeyDatabase.KEY_TYPE_IMPORTED);
				pubkey.setPrivateKey(raw);
			}

			// write new value into database
			if (pubkeydb == null)
				pubkeydb = new PubkeyDatabase(this);
			pubkeydb.savePubkey(pubkey);

			updateList();
		} catch(Exception e) {
			Log.e(TAG, "Problem parsing imported private key", e);
			Toast.makeText(PubkeyListActivity.this, R.string.pubkey_import_parse_problem, Toast.LENGTH_LONG).show();
		}
	}

	/**
	 *
	 */
	private void pickFileSimple() {
		// build list of all files in sdcard root
		final File sdcard = Environment.getExternalStorageDirectory();
		Log.d(TAG, sdcard.toString());

		// Don't show a dialog if the SD card is completely absent.
		final String state = Environment.getExternalStorageState();
		if (!Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)
				&& !Environment.MEDIA_MOUNTED.equals(state)) {
			new AlertDialog.Builder(PubkeyListActivity.this)
				.setMessage(R.string.alert_sdcard_absent)
				.setNegativeButton(android.R.string.cancel, null).create().show();
			return;
		}

		List<String> names = new LinkedList<String>();
		{
			File[] files = sdcard.listFiles();
			if (files != null) {
				for(File file : sdcard.listFiles()) {
					if(file.isDirectory()) continue;
					names.add(file.getName());
				}
			}
		}
		Collections.sort(names);

		final String[] namesList = names.toArray(new String[] {});
		Log.d(TAG, names.toString());

		// prompt user to select any file from the sdcard root
		new AlertDialog.Builder(PubkeyListActivity.this)
			.setTitle(R.string.pubkey_list_pick)
			.setItems(namesList, new OnClickListener() {
				public void onClick(DialogInterface arg0, int arg1) {
					String name = namesList[arg1];

					readKeyFromFile(new File(sdcard, name));
				}
			})
			.setNegativeButton(android.R.string.cancel, null).create().show();
	}

	class PubkeyAdapter extends ArrayAdapter<PubkeyBean> {
		private List<PubkeyBean> pubkeys;

		class ViewHolder {
			public TextView nickname;
			public TextView caption;
			public ImageView icon;
		}

		public PubkeyAdapter(Context context, List<PubkeyBean> pubkeys) {
			super(context, R.layout.item_pubkey, pubkeys);

			this.pubkeys = pubkeys;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.item_pubkey, null, false);

				holder = new ViewHolder();

				holder.nickname = (TextView) convertView.findViewById(android.R.id.text1);
				holder.caption = (TextView) convertView.findViewById(android.R.id.text2);
				holder.icon = (ImageView) convertView.findViewById(android.R.id.icon1);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			PubkeyBean pubkey = pubkeys.get(position);
			holder.nickname.setText(pubkey.getNickname());

			boolean imported = PubkeyDatabase.KEY_TYPE_IMPORTED.equals(pubkey.getType());

			if (imported) {
				try {
					PEMStructure struct = PEMDecoder.parsePEM(new String(pubkey.getPrivateKey()).toCharArray());
					String type = (struct.pemType == PEMDecoder.PEM_RSA_PRIVATE_KEY) ? "RSA" : "DSA";
					holder.caption.setText(String.format("%s unknown-bit", type));
				} catch (IOException e) {
					Log.e(TAG, "Error decoding IMPORTED public key at " + pubkey.getId(), e);
				}
			} else {
				try {
					holder.caption.setText(pubkey.getDescription());
				} catch (Exception e) {
					Log.e(TAG, "Error decoding public key at " + pubkey.getId(), e);
					holder.caption.setText(R.string.pubkey_unknown_format);
				}
			}

			if (bound == null) {
				holder.icon.setVisibility(View.GONE);
			} else {
				holder.icon.setVisibility(View.VISIBLE);

				if (bound.isKeyLoaded(pubkey.getNickname()))
					holder.icon.setImageState(new int[] { android.R.attr.state_checked }, true);
				else
					holder.icon.setImageState(new int[] {  }, true);
			}

			return convertView;
		}
	}
}
