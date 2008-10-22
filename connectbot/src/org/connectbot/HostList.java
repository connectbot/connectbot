package org.connectbot;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostAdapter;
import org.connectbot.util.HostBinder;
import org.connectbot.util.HostDatabase;
import org.json.JSONObject;
import org.theb.ssh.InteractiveHostKeyVerifier;

import com.trilead.ssh2.Connection;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.Intent.ShortcutIconResource;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class HostList extends Activity {
	
	public final static String UPDATE_URL = "http://connectbot.org/version";
	public final static double VERSION = 1.0;

	public Handler versionHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			// handle version update message
			if(!(msg.obj instanceof JSONObject)) return;
			JSONObject json = (JSONObject)msg.obj;
			
			double version = json.optDouble("version");
			String features = json.optString("features");
			final String target = "market://" + json.optString("target");
			
			if(version <= VERSION) return;
			
			new AlertDialog.Builder(HostList.this)
				.setTitle("New version")
				.setMessage(features)
				.setPositiveButton("Yes, upgrade", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int which) {
						Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
						HostList.this.startActivity(intent);
	                }
	            })
	            .setNegativeButton("Not right now", new DialogInterface.OnClickListener() {
	                public void onClick(DialogInterface dialog, int which) {
	                }
	            }).create().show();
			
		}

	};

	public TerminalManager bound = null;

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// update our listview binder to find the service
			HostList.this.updateCursor();
		}

		public void onServiceDisconnected(ComponentName className) {
			bound = null;
			HostList.this.updateCursor();
		}
	};

	public HostDatabase hostdb;
	public Cursor hosts;
	public ListView list;
	public HostAdapter adapter;

	public int COL_ID, COL_NICKNAME, COL_USERNAME, COL_HOSTNAME, COL_CONNECTED, COL_PORT;

	@Override
    public void onStart() {
		super.onStart();

		// start the terminal manager service
		this.startService(new Intent(this, TerminalManager.class));
		this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

	}

	@Override
    public void onStop() {
		super.onStop();
		this.unbindService(connection);
		
	}
	
	
	
	public final static String EULA = "eula";
	protected SharedPreferences prefs = null;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if(resultCode == Activity.RESULT_OK) {
			// yay they agreed, so store that info
			Editor edit = prefs.edit();
			edit.putBoolean(EULA, true);
			edit.commit();
		} else {
			// user didnt agree, so close
			this.finish();
		}
		
		this.updateCursor();
		
	}
	
	
	protected boolean shortcut = false;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_hostlist);

		
		// check for eula agreement
		this.prefs = this.getSharedPreferences(EULA, Context.MODE_PRIVATE);
		
		boolean agreed = prefs.getBoolean(EULA, false);
		if(!agreed) {
			this.startActivityForResult(new Intent(this, WizardActivity.class), 1);
		}
		
		// start thread to check for new version
		new Thread(new Runnable() {
			public void run() {
				try {
					JSONObject json = new JSONObject(readUrl(UPDATE_URL));
					Message.obtain(versionHandler, -1, json).sendToTarget();
				} catch(Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
		

		
		
		this.shortcut = Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction());

		// connect with hosts database and populate list
		this.hostdb = new HostDatabase(this);
		this.list = (ListView) this.findViewById(R.id.front_hostlist);
		this.updateCursor();
		
		//this.list.setSelector(R.drawable.highlight_disabled_pressed);

		this.COL_ID = hosts.getColumnIndexOrThrow("_id");
		this.COL_NICKNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_NICKNAME);
		this.COL_USERNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_USERNAME);
		this.COL_HOSTNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_HOSTNAME);
		this.COL_PORT = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_PORT);
		this.COL_CONNECTED = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_LASTCONNECT);

		this.list.setOnItemClickListener(new OnItemClickListener() {

			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				// launch off to console details
				Cursor c = (Cursor)parent.getAdapter().getItem(position);
				String username = c.getString(COL_USERNAME);
				String hostname = c.getString(COL_HOSTNAME);
				int port = c.getInt(COL_PORT);
				String nickname = c.getString(COL_NICKNAME);
				
				Uri uri = Uri.parse(String.format("ssh://%s@%s:%s/#%s", username, hostname, port, nickname));
				Intent contents = new Intent(Intent.ACTION_VIEW, uri);
				contents.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

				
				if (shortcut) {
					// create shortcut if requested
					ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(HostList.this, R.drawable.icon);
					
					Intent intent = new Intent();
					intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, contents);
					intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, nickname);
					intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

					setResult(RESULT_OK, intent);
					finish();
					
				} else {
					HostList.this.startActivity(contents);
					
					
				}

				
			}

		});

		this.registerForContextMenu(this.list);

		final Pattern hostmask = Pattern.compile(".+@.+(:\\d+)?");
		final TextView text = (TextView) this.findViewById(R.id.front_quickconnect);
		text.setOnKeyListener(new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {
				
				if(keyCode == KeyEvent.KEYCODE_ENTER) {
					
					// make sure we follow pattern
					if (text.getText().length() < 3)
						return false;

					// show error if poorly formed
					if (!hostmask.matcher(text.getText().toString()).find()) {
						text.setError("Use the format 'username@hostname:port'");
						return false;
					}
					
					// create new host for entered string and then launch
					Uri uri = Uri.parse(String.format("ssh://%s", text.getText().toString()));
					String username = uri.getUserInfo();
					String hostname = uri.getHost();
					int port = uri.getPort();
					if(port == -1) port = 22;
					
					String nickname = String.format("%s@%s", username, hostname);
					hostdb.createHost(null, nickname, username, hostname, port, hostdb.COLOR_GRAY);
					
					Intent intent = new Intent(HostList.this, Console.class);
					intent.setData(Uri.parse(String.format("ssh://%s@%s:%s/#%s", username, hostname, port, nickname)));
					HostList.this.startActivity(intent);

				}

				// set list filter based on text
				// String filter = text.getText().toString();
				// list.setTextFilterEnabled((filter.length() > 0));
				// list.setFilterText(filter);

				// TODO Auto-generated method stub
				return false;
			}

		});

	}

	public MenuItem sortcolor, sortlast;
	public boolean sortedByColor = false;
	
	public void updateCursor() {

		// refresh cursor because of possible sorting change
		if(this.hosts != null)
			this.hosts.close();
		this.hosts = this.hostdb.allHosts(sortedByColor);
		
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.item_host, this.hosts,
				new String[] { hostdb.FIELD_HOST_NICKNAME, hostdb.FIELD_HOST_LASTCONNECT, hostdb.FIELD_HOST_LASTCONNECT, hostdb.FIELD_HOST_COLOR },
				new int[] { android.R.id.text1, android.R.id.text2, android.R.id.icon, android.R.id.content });
		adapter.setViewBinder(new HostBinder(bound, this.getResources()));
		
		//this.adapter = new HostAdapter(this, this.hosts);
		this.list.setAdapter(adapter);
		
	}

    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		sortcolor.setVisible(!sortedByColor);
		sortlast.setVisible(sortedByColor);
		
		return true;
		
    }

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		// add host, ssh keys, about

//		MenuItem add = menu.add(0, 0, Menu.NONE, "New host");
//		add.setIcon(android.R.drawable.ic_menu_add);
//		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
//			public boolean onMenuItemClick(MenuItem item) {
//				return true;
//			}
//		});

		sortcolor = menu.add(0, 0, Menu.NONE, "Sort by color");
		sortcolor.setIcon(android.R.drawable.ic_menu_share);
		sortcolor.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				sortedByColor = true;
				updateCursor();
				return true;
			}
		});
		
		sortlast = menu.add(0, 0, Menu.NONE, "Sort by name");
		sortlast.setIcon(android.R.drawable.ic_menu_share);
		sortlast.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				sortedByColor = false;
				updateCursor();
				return true;
			}
		});

		MenuItem keys = menu.add(0, 0, Menu.NONE, "Manage keys");
		keys.setIcon(android.R.drawable.ic_lock_lock);
		keys.setEnabled(false);
    
		MenuItem settings = menu.add(0, 0, Menu.NONE, "Settings");
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(HostList.this, SettingsActivity.class));

		MenuItem about = menu.add(0, 0, Menu.NONE, "About");
		about.setIcon(android.R.drawable.ic_menu_help);
		about.setIntent(new Intent(HostList.this, WizardActivity.class));
		
		return true;
		
	}
	
	
	
	public String readUrl(String fetchurl) throws Exception {
		byte[] buffer = new byte[1024];
		
		URL url = new URL(fetchurl);
		URLConnection connection = url.openConnection();
		connection.setConnectTimeout(6000);
		connection.setReadTimeout(6000);
		connection.setRequestProperty("User-Agent", String.format("%s %f", this.getResources().getString(R.string.app_name), VERSION));
		connection.connect();
		InputStream is = connection.getInputStream();
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		int bytesRead;
		while ((bytesRead = is.read(buffer)) != -1) {
			os.write(buffer, 0, bytesRead);
		}

		os.flush();
		os.close();
		is.close();
		
		return new String(os.toByteArray());
	}
	
	
	public final static int REQUEST_EDIT = 1;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {

		// create menu to handle hosts

		// create menu to handle deleting and sharing lists
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		Cursor cursor = (Cursor) this.list.getItemAtPosition(info.position);

		menu.setHeaderTitle(cursor.getString(COL_NICKNAME));
		final int id = cursor.getInt(COL_ID);

		// edit, disconnect, delete
		// TODO: change disconnect/connect based on status
		MenuItem connect = menu.add(0, 0, Menu.NONE, "Disconnect");
		connect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				return false;
			}
		});

		MenuItem edit = menu.add(0, 0, Menu.NONE, "Edit host");
		edit.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent intent = new Intent(HostList.this, HostEditor.class);
				intent.putExtra(Intent.EXTRA_TITLE, id);
				HostList.this.startActivityForResult(intent, REQUEST_EDIT);
				return false;
			}
		});

		MenuItem delete = menu.add(0, 0, Menu.NONE, "Delete host");
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				hostdb.deleteHost(id);
				return false;
			}
		});

	}

}
