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

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
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
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

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
	public final static String TAG = PubkeyListActivity.class.toString();

	protected PubkeyDatabase pubkeydb;
	private List<PubkeyBean> pubkeys;

	protected ClipboardManager clipboard;

	protected LayoutInflater inflater = null;

	protected TerminalManager bound = null;

	private MenuItem onstartToggle = null;

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
					updateHandler.sendEmptyMessage(-1);
				} else {
					handleAddKey(pubkey);
				}

			}
		});

		clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);

		inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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

				// TODO: replace this with well-known intent over to file browser
				// TODO: if browser not installed (?) then fallback to this simple method?

				// build list of all files in sdcard root
				final File sdcard = Environment.getExternalStorageDirectory();
				Log.d(TAG, sdcard.toString());

				// Don't show a dialog if the SD card is completely absent.
				final String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_REMOVED.equals(state)
						|| Environment.MEDIA_BAD_REMOVAL.equals(state)
						|| Environment.MEDIA_UNMOUNTABLE.equals(state)
						|| Environment.MEDIA_UNMOUNTED.equals(state)) {
					new AlertDialog.Builder(PubkeyListActivity.this)
						.setMessage(R.string.alert_sdcard_absent)
						.setNegativeButton(android.R.string.cancel, null).create().show();
					return true;
				}

				List<String> names = new LinkedList<String>();
				for(File file : sdcard.listFiles()) {
					if(file.isDirectory()) continue;
					names.add(file.getName());
				}
				Collections.sort(names);

				final String[] namesList = names.toArray(new String[] {});
				Log.d(TAG, names.toString());

				// prompt user to select any file from the sdcard root
				new AlertDialog.Builder(PubkeyListActivity.this)
					.setTitle(R.string.pubkey_list_pick)
					.setItems(namesList, new OnClickListener() {
						public void onClick(DialogInterface arg0, int arg1) {
							PubkeyBean pubkey = new PubkeyBean();

							// find the exact file selected
							String name = namesList[arg1];
							pubkey.setNickname(name);
							File actual = new File(sdcard, name);

							// parse the actual key once to check if its encrypted
							// then save original file contents into our database
							try {
								byte[] raw = readRaw(actual);

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
								pubkeydb.savePubkey(pubkey);
								updateHandler.sendEmptyMessage(-1);
							} catch(Exception e) {
								Log.e(TAG, "Problem parsing imported private key", e);
								Toast.makeText(PubkeyListActivity.this, R.string.pubkey_import_parse_problem, Toast.LENGTH_LONG).show();
							}
						}
					})
					.setNegativeButton(android.R.string.cancel, null).create().show();

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

	protected void handleAddKey(PubkeyBean pubkey, String password) {
		Object trileadKey = null;
		if(PubkeyDatabase.KEY_TYPE_IMPORTED.equals(pubkey.getType())) {
			// load specific key using pem format
			try {
				trileadKey = PEMDecoder.decode(new String(pubkey.getPrivateKey()).toCharArray(), password);
			} catch(Exception e) {
				String message = getResources().getString(R.string.pubkey_failed_add, pubkey.getNickname());
				Log.e(TAG, message, e);
				Toast.makeText(PubkeyListActivity.this, message, Toast.LENGTH_LONG);
			}

		} else {
			// load using internal generated format
			PrivateKey privKey = null;
			PublicKey pubKey = null;
			try {
				privKey = PubkeyUtils.decodePrivate(pubkey.getPrivateKey(), pubkey.getType(), password);
				pubKey = PubkeyUtils.decodePublic(pubkey.getPublicKey(), pubkey.getType());
			} catch (Exception e) {
				String message = getResources().getString(R.string.pubkey_failed_add, pubkey.getNickname());
				Log.e(TAG, message, e);
				Toast.makeText(PubkeyListActivity.this, message, Toast.LENGTH_LONG);
				return;
			}

			// convert key to trilead format
			trileadKey = PubkeyUtils.convertToTrilead(privKey, pubKey);
			Log.d(TAG, "Unlocked key " + PubkeyUtils.formatKey(pubKey));
		}

		if(trileadKey == null) return;

		Log.d(TAG, String.format("Unlocked key '%s'", pubkey.getNickname()));

		// save this key in-memory if option enabled
		if(bound.isSavingKeys()) {
			bound.addKey(pubkey.getNickname(), trileadKey);
		}

		updateHandler.sendEmptyMessage(-1);
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
					updateHandler.sendEmptyMessage(-1);
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
				updateHandler.sendEmptyMessage(-1);
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
									updateHandler.sendEmptyMessage(-1);
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
							updateHandler.sendEmptyMessage(-1);
						}
					})
					.setNegativeButton(R.string.delete_neg, null).create().show();

				return true;
			}
		});

	}


	protected Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			updateList();
		}
	};

	protected void updateList() {
		if (pubkeydb == null) return;

		pubkeys = pubkeydb.allPubkeys();
		PubkeyAdapter adapter = new PubkeyAdapter(this, pubkeys);

		this.setListAdapter(adapter);
	}

	class PubkeyAdapter extends ArrayAdapter<PubkeyBean> {
		private List<PubkeyBean> pubkeys;

		public PubkeyAdapter(Context context, List<PubkeyBean> pubkeys) {
			super(context, R.layout.item_pubkey, pubkeys);

			this.pubkeys = pubkeys;
		}

		public View getView(int position, View view, ViewGroup parent) {
			if (view == null)
				view = inflater.inflate(R.layout.item_pubkey, null, false);

			TextView nickname = (TextView)view.findViewById(android.R.id.text1);
			TextView caption = (TextView)view.findViewById(android.R.id.text2);
			ImageView icon = (ImageView)view.findViewById(android.R.id.icon1);

			PubkeyBean pubkey = pubkeys.get(position);
			nickname.setText(pubkey.getNickname());

			boolean imported = PubkeyDatabase.KEY_TYPE_IMPORTED.equals(pubkey.getType());

			if (imported) {
				try {
					PEMStructure struct = PEMDecoder.parsePEM(new String(pubkey.getPrivateKey()).toCharArray());
					String type = (struct.pemType == PEMDecoder.PEM_RSA_PRIVATE_KEY) ? "RSA" : "DSA";
					caption.setText(String.format("%s unknown-bit", type));
				} catch (IOException e) {
					Log.e(TAG, "Error decoding IMPORTED public key at " + pubkey.getId(), e);
				}
			} else {
				try {
					PublicKey pub = PubkeyUtils.decodePublic(pubkey.getPublicKey(), pubkey.getType());
					caption.setText(PubkeyUtils.describeKey(pub, pubkey.isEncrypted()));
				} catch (Exception e) {
					Log.e(TAG, "Error decoding public key at " + pubkey.getId(), e);
					caption.setText(R.string.pubkey_unknown_format);
				}
			}

			if (bound == null) {
				icon.setVisibility(View.GONE);
			} else {
				icon.setVisibility(View.VISIBLE);

				if (bound.isKeyLoaded(pubkey.getNickname()))
					icon.setImageState(new int[] { android.R.attr.state_checked }, true);
				else
					icon.setImageState(new int[] {  }, true);
			}

			return view;
		}
	}
}
