package org.connectbot;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostAdapter;
import org.connectbot.util.HostDatabase;
import org.theb.ssh.InteractiveHostKeyVerifier;

import com.trilead.ssh2.Connection;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
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
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class HostList extends Activity {

	public TerminalManager bound = null;

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// TODO: update our green bulb icons by checking for existing
			// bridges
			// open up some test sessions
			// try {
			// bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt",
			// "screen", 100);
			// bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt",
			// "screen", 100);
			// bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt",
			// "screen", 100);
			// } catch(Exception e) {
			// e.printStackTrace();
			// }

		}

		public void onServiceDisconnected(ComponentName className) {
			bound = null;
		}
	};

	public HostDatabase hostdb;
	public Cursor hosts;
	public ListView list;
	public HostAdapter adapter;

	public int COL_ID, COL_NICKNAME, COL_USERNAME, COL_HOSTNAME, COL_CONNECTED;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_frontpage);

		// start the terminal manager service
		this.startService(new Intent(this, TerminalManager.class));
		this.bindService(new Intent(this, TerminalManager.class), connection,
				Context.BIND_AUTO_CREATE);

		// connect with hosts database and populate list
		this.hostdb = new HostDatabase(this);
		this.list = (ListView) this.findViewById(R.id.front_hostlist);
		this.updateCursor();
		
		//this.list.setSelector(R.drawable.highlight_disabled_pressed);

		this.COL_ID = hosts.getColumnIndexOrThrow("_id");
		this.COL_NICKNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_NICKNAME);
		this.COL_USERNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_USERNAME);
		this.COL_HOSTNAME = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_HOSTNAME);
		this.COL_CONNECTED = hosts.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_LASTCONNECT);

		this.list.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {

				// launch off to console details
				// TODO: package information about connection selected
				HostList.this.startActivity(new Intent(HostList.this,
						Console.class));

			}

		});

		this.registerForContextMenu(this.list);

		final Pattern hostmask = Pattern.compile(".+@.+(:\\d+)?");
		final TextView text = (TextView) this
				.findViewById(R.id.front_quickconnect);
		text.setOnKeyListener(new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {

				// make sure we follow pattern
				if (text.getText().length() < 1)
					return false;

				// TODO: only show error when trying to hit enter
				if (!hostmask.matcher(text.getText().toString()).find()) {
					text.setError("Use the format 'username@hostname:port'");
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
		this.adapter = new HostAdapter(this, this.hosts);
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

		MenuItem add = menu.add(0, 0, Menu.NONE, "New host");
		add.setIcon(android.R.drawable.ic_menu_add);
		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				return false;
			}
		});

		sortcolor = menu.add(0, 0, Menu.NONE, "Sort by color");
		sortcolor.setIcon(android.R.drawable.ic_menu_share);
		sortcolor.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				sortedByColor = true;
				updateCursor();
				return false;
			}
		});
		
		sortlast = menu.add(0, 0, Menu.NONE, "Sort by last");
		sortlast.setIcon(android.R.drawable.ic_menu_share);
		sortlast.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				sortedByColor = false;
				updateCursor();
				return false;
			}
		});

		MenuItem keys = menu.add(0, 0, Menu.NONE, "Manage keys");
		keys.setIcon(android.R.drawable.ic_lock_lock);
    
		MenuItem settings = menu.add(0, 0, Menu.NONE, "Settings");
		settings.setIcon(android.R.drawable.ic_menu_preferences);

		MenuItem about = menu.add(0, 0, Menu.NONE, "About");
		about.setIcon(android.R.drawable.ic_menu_help);
		
		return true;
		
	}

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
				HostList.this.startActivity(intent);
				return false;
			}
		});

		MenuItem delete = menu.add(0, 0, Menu.NONE, "Delete host");
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				return false;
			}
		});

	}

}
