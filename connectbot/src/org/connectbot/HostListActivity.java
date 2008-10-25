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

import java.util.regex.Pattern;

import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostBinder;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.UpdateHelper;


import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
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

public class HostListActivity extends ListActivity {
	
	public Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			HostListActivity.this.updateCursor();
		}
	};
	
	protected TerminalManager bound = null;

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// update our listview binder to find the service
			HostListActivity.this.updateCursor();
		}

		public void onServiceDisconnected(ComponentName className) {
			bound = null;
			HostListActivity.this.updateCursor();
		}
	};

	protected HostDatabase hostdb;
	protected Cursor hosts;

	protected int COL_ID, COL_NICKNAME, COL_USERNAME, COL_HOSTNAME, COL_CONNECTED, COL_PORT;

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

	public final static int REQUEST_EDIT = 1;
	public final static int REQUEST_EULA = 2;

	protected SharedPreferences prefs = null;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		switch(requestCode) {
		case REQUEST_EULA:
			if(resultCode == Activity.RESULT_OK) {
				// yay they agreed, so store that info
				Editor edit = prefs.edit();
				edit.putBoolean(EULA, true);
				edit.commit();
			} else {
				// user didnt agree, so close
				this.finish();
			}
			break;
			
		case REQUEST_EDIT:
			this.updateCursor();
			break;
			
		}
		
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
			this.startActivityForResult(new Intent(this, WizardActivity.class), REQUEST_EULA);
		}
		
		// start thread to check for new version
		new UpdateHelper(this);
		

		
		
		this.shortcut = Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction());

		// connect with hosts database and populate list
		this.hostdb = new HostDatabase(this);
		ListView list = this.getListView();
		this.updateCursor();
		
		//this.list.setSelector(R.drawable.highlight_disabled_pressed);

		this.COL_ID = hosts.getColumnIndexOrThrow("_id");
		this.COL_NICKNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_NICKNAME);
		this.COL_USERNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_USERNAME);
		this.COL_HOSTNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_HOSTNAME);
		this.COL_PORT = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_PORT);
		this.COL_CONNECTED = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_LASTCONNECT);

		list.setOnItemClickListener(new OnItemClickListener() {

			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				// launch off to console details
				Cursor c = (Cursor)parent.getAdapter().getItem(position);
				String username = c.getString(COL_USERNAME);
				String hostname = c.getString(COL_HOSTNAME);
				int port = c.getInt(COL_PORT);
				String nickname = c.getString(COL_NICKNAME);
				
				// create a specific uri that represents this host
				Uri uri = Uri.parse(String.format("ssh://%s@%s:%s/#%s", username, hostname, port, nickname));
				Intent contents = new Intent(Intent.ACTION_VIEW, uri);
				contents.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

				
				if (shortcut) {
					// create shortcut if requested
					ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(HostListActivity.this, R.drawable.icon);
					
					Intent intent = new Intent();
					intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, contents);
					intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, nickname);
					intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);

					setResult(RESULT_OK, intent);
					finish();
					
				} else {
					// otherwise just launch activity to show this host
					HostListActivity.this.startActivity(contents);
					
					
				}

				
			}

		});

		this.registerForContextMenu(list);

		final Pattern hostmask = Pattern.compile(".+@.+(:\\d+)?");
		final TextView text = (TextView) this.findViewById(R.id.front_quickconnect);
		text.setOnKeyListener(new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {
				
				if(keyCode != KeyEvent.KEYCODE_ENTER) return false;
					
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
				
				Intent intent = new Intent(HostListActivity.this, ConsoleActivity.class);
				intent.setData(Uri.parse(String.format("ssh://%s@%s:%s/#%s", username, hostname, port, nickname)));
				HostListActivity.this.startActivity(intent);

				// set list filter based on text
				// String filter = text.getText().toString();
				// list.setTextFilterEnabled((filter.length() > 0));
				// list.setFilterText(filter);

				return true;
			}

		});

	}

	public MenuItem sortcolor, sortlast;
	public boolean sortedByColor = false;
	
	protected void updateCursor() {

		// refresh cursor because of possible sorting change
		if(this.hosts != null)
			this.hosts.close();
		this.hosts = this.hostdb.allHosts(sortedByColor);
		
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.item_host, this.hosts,
				new String[] { HostDatabase.FIELD_HOST_NICKNAME, HostDatabase.FIELD_HOST_LASTCONNECT, HostDatabase.FIELD_HOST_LASTCONNECT, HostDatabase.FIELD_HOST_COLOR },
				new int[] { android.R.id.text1, android.R.id.text2, android.R.id.icon, android.R.id.content });
		adapter.setViewBinder(new HostBinder(bound, this.getResources()));
		
		//this.adapter = new HostAdapter(this, this.hosts);
		this.setListAdapter(adapter);
		
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

		sortcolor = menu.add("Sort by color");
		sortcolor.setIcon(android.R.drawable.ic_menu_share);
		sortcolor.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				sortedByColor = true;
				updateCursor();
				return true;
			}
		});
		
		sortlast = menu.add("Sort by name");
		sortlast.setIcon(android.R.drawable.ic_menu_share);
		sortlast.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				sortedByColor = false;
				updateCursor();
				return true;
			}
		});

		MenuItem keys = menu.add("Manage keys");
		keys.setIcon(android.R.drawable.ic_lock_lock);
		keys.setEnabled(false);
    
		MenuItem settings = menu.add("Settings");
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(HostListActivity.this, SettingsActivity.class));

		MenuItem about = menu.add("About");
		about.setIcon(android.R.drawable.ic_menu_help);
		about.setIntent(new Intent(HostListActivity.this, WizardActivity.class));
		
		return true;
		
	}
	

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		// create menu to handle hosts

		// create menu to handle deleting and sharing lists
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		Cursor cursor = (Cursor) this.getListView().getItemAtPosition(info.position);

		menu.setHeaderTitle(cursor.getString(COL_NICKNAME));
		final int id = cursor.getInt(COL_ID);

		// edit, disconnect, delete
		// TODO: change disconnect/connect based on status
		MenuItem connect = menu.add("Disconnect");
		connect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				return false;
			}
		});

		MenuItem edit = menu.add("Edit host");
		edit.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent intent = new Intent(HostListActivity.this, HostEditorActivity.class);
				intent.putExtra(Intent.EXTRA_TITLE, id);
				HostListActivity.this.startActivityForResult(intent, REQUEST_EDIT);
				return false;
			}
		});

		MenuItem delete = menu.add("Delete host");
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				hostdb.deleteHost(id);
				updateHandler.sendEmptyMessage(-1);
				return false;
			}
		});

	}

}
