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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;
import org.connectbot.util.UpdateHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.Intent.ShortcutIconResource;
import android.content.SharedPreferences.Editor;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.nullwire.trace.ExceptionHandler;

public class HostListActivity extends ListActivity {
	protected TerminalManager bound = null;

	protected HostDatabase hostdb;
	private List<HostBean> hosts;
	protected LayoutInflater inflater = null;

	protected boolean sortedByColor = false;

	private MenuItem sortcolor;

	private MenuItem sortlast;

	protected Handler updateHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			HostListActivity.this.updateList();
		}
	};

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// update our listview binder to find the service
			HostListActivity.this.updateList();
		}

		public void onServiceDisconnected(ComponentName className) {
			bound = null;
			HostListActivity.this.updateList();
		}
	};

	@Override
	public void onStart() {
		super.onStart();

		// start the terminal manager service
		this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

		if(this.hostdb == null)
			this.hostdb = new HostDatabase(this);

		this.updateList();

		ExceptionHandler.checkForTraces(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		this.unbindService(connection);

		if(this.hostdb != null) {
			this.hostdb.close();
			this.hostdb = null;
		}

	}

	@Override
	public void onResume() {
		super.onResume();

		ExceptionHandler.checkForTraces(this);
	}

	public final static int REQUEST_EDIT = 1;
	public final static int REQUEST_EULA = 2;

	private SharedPreferences prefs = null;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_EULA) {
			if(resultCode == Activity.RESULT_OK) {
				// yay they agreed, so store that info
				Editor edit = prefs.edit();
				edit.putBoolean(PreferenceConstants.EULA, true);
				edit.commit();
			} else {
				// user didnt agree, so close
				this.finish();
			}
		} else if (requestCode == REQUEST_EDIT) {
			this.updateList();
		}
	}

	protected boolean makingShortcut = false;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_hostlist);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_hosts_list)));

		ExceptionHandler.register(this);

		// check for eula agreement
		this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

		boolean agreed = prefs.getBoolean(PreferenceConstants.EULA, false);
		if(!agreed) {
			this.startActivityForResult(new Intent(this, WizardActivity.class), REQUEST_EULA);
		}

		// start thread to check for new version
		new UpdateHelper(this);

		this.makingShortcut = Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction());

		// connect with hosts database and populate list
		this.hostdb = new HostDatabase(this);
		ListView list = this.getListView();

		this.sortedByColor = prefs.getBoolean(PreferenceConstants.SORT_BY_COLOR, false);
		this.updateList();

		//this.list.setSelector(R.drawable.highlight_disabled_pressed);

		list.setOnItemClickListener(new OnItemClickListener() {

			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				// launch off to console details
				HostBean host = (HostBean) parent.getAdapter().getItem(position);

				// create a specific uri that represents this host
				Uri uri = Uri.parse(String.format("ssh://%s@%s:%d/#%s",
						Uri.encode(host.getUsername()),
						Uri.encode(host.getHostname()),
						host.getPort(),
						Uri.encode(host.getNickname())));
				Intent contents = new Intent(Intent.ACTION_VIEW, uri);
				contents.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);


				if (makingShortcut) {
					// create shortcut if requested
					ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(HostListActivity.this, R.drawable.icon);

					Intent intent = new Intent();
					intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, contents);
					intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, host.getNickname());
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

		final Pattern hostmask = Pattern.compile("^([^@]+)@([0-9A-Z.-]+)(:(\\d+))?$", Pattern.CASE_INSENSITIVE);
		final TextView text = (TextView) this.findViewById(R.id.front_quickconnect);
		text.setVisibility(makingShortcut ? View.GONE : View.VISIBLE);
		text.setOnKeyListener(new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {

				if(event.getAction() == KeyEvent.ACTION_UP) return false;
				if(keyCode != KeyEvent.KEYCODE_ENTER) return false;

				// make sure we follow pattern
				if (text.getText().length() < 3)
					return false;

				// show error if poorly formed
				Matcher matcher = hostmask.matcher(text.getText().toString());
				if (!matcher.matches()) {
					text.setError(getString(R.string.list_format_error));
					return false;
				}

				// create new host for entered string and then launch
				String username = matcher.group(1);
				String hostname = matcher.group(2);

				int port = 22;
				try {
					port = Integer.parseInt(matcher.group(4));
				} catch (Exception e) {
					Log.i("HostListActivity", "Invalid format for port: "+ matcher.group(4));
				}

				String nickname;
				if (port == 22) {
					nickname = String.format("%s@%s", username, hostname);
				} else {
					nickname = String.format("%s@%s:%d", username, hostname, port);
				}

				HostBean host = new HostBean(nickname, username, hostname, port);
				host.setColor(HostDatabase.COLOR_GRAY);
				host.setPubkeyId(HostDatabase.PUBKEYID_ANY);
				hostdb.saveHost(host);

				Intent intent = new Intent(HostListActivity.this, ConsoleActivity.class);
				intent.setData(host.getUri());
				HostListActivity.this.startActivity(intent);

				// set list filter based on text
				// String filter = text.getText().toString();
				// list.setTextFilterEnabled((filter.length() > 0));
				// list.setFilterText(filter);

				return true;
			}

		});

		this.inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		// don't offer menus when creating shortcut
		if (makingShortcut) return true;

		sortcolor.setVisible(!sortedByColor);
		sortlast.setVisible(sortedByColor);

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		// don't offer menus when creating shortcut
		if(makingShortcut) return true;

		// add host, ssh keys, about
		sortcolor = menu.add(R.string.list_menu_sortcolor);
		sortcolor.setIcon(android.R.drawable.ic_menu_share);
		sortcolor.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				sortedByColor = true;
				updateList();
				return true;
			}
		});

		sortlast = menu.add(R.string.list_menu_sortname);
		sortlast.setIcon(android.R.drawable.ic_menu_share);
		sortlast.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				sortedByColor = false;
				updateList();
				return true;
			}
		});

		MenuItem keys = menu.add(R.string.list_menu_pubkeys);
		keys.setIcon(android.R.drawable.ic_lock_lock);
		keys.setIntent(new Intent(HostListActivity.this, PubkeyListActivity.class));

		MenuItem settings = menu.add(R.string.list_menu_settings);
		settings.setIcon(android.R.drawable.ic_menu_preferences);
		settings.setIntent(new Intent(HostListActivity.this, SettingsActivity.class));

		MenuItem help = menu.add(R.string.title_help);
		help.setIcon(android.R.drawable.ic_menu_help);
		help.setIntent(new Intent(HostListActivity.this, HelpActivity.class));

		return true;

	}


	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {

		// create menu to handle hosts

		// create menu to handle deleting and sharing lists
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		final HostBean host = (HostBean) this.getListView().getItemAtPosition(info.position);

		menu.setHeaderTitle(host.getNickname());

		// edit, disconnect, delete
		MenuItem connect = menu.add(R.string.list_host_disconnect);
		final TerminalBridge bridge = bound.findBridge(host);
		connect.setEnabled((bridge != null));
		connect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				bridge.dispatchDisconnect(true);
				updateHandler.sendEmptyMessage(-1);
				return true;
			}
		});

		MenuItem edit = menu.add(R.string.list_host_edit);
		edit.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent intent = new Intent(HostListActivity.this, HostEditorActivity.class);
				intent.putExtra(Intent.EXTRA_TITLE, host.getId());
				HostListActivity.this.startActivityForResult(intent, REQUEST_EDIT);
				return true;
			}
		});

		MenuItem portForwards = menu.add(R.string.list_host_portforwards);
		portForwards.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent intent = new Intent(HostListActivity.this, PortForwardListActivity.class);
				intent.putExtra(Intent.EXTRA_TITLE, host.getId());
				HostListActivity.this.startActivityForResult(intent, REQUEST_EDIT);
				return true;
			}
		});

		MenuItem delete = menu.add(R.string.list_host_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(HostListActivity.this)
					.setMessage(getString(R.string.delete_message, host.getNickname()))
					.setPositiveButton(R.string.delete_pos, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
						// make sure we disconnect
							if(bridge != null)
								bridge.dispatchDisconnect(true);

							hostdb.deleteHost(host);
							updateHandler.sendEmptyMessage(-1);
						}
						})
					.setNegativeButton(R.string.delete_neg, null).create().show();

				return true;
			}
		});
	}

	protected void updateList() {
		Editor edit = prefs.edit();
		edit.putBoolean(PreferenceConstants.SORT_BY_COLOR, sortedByColor);
		edit.commit();

		if (hostdb == null)
			hostdb = new HostDatabase(this);

		hosts = hostdb.getHosts(sortedByColor);

		// Don't lose hosts that are connected via shortcuts but not in the database.
		if (bound != null) {
			for (TerminalBridge bridge : bound.bridges) {
				if (!hosts.contains(bridge.host))
					hosts.add(0, bridge.host);
			}
		}

		HostAdapter adapter = new HostAdapter(this, hosts, bound);

		this.setListAdapter(adapter);
	}

	class HostAdapter extends ArrayAdapter<HostBean> {
		private List<HostBean> hosts;
		private final TerminalManager manager;
		private final ColorStateList red, green, blue;

		public final static int STATE_UNKNOWN = 1, STATE_CONNECTED = 2, STATE_DISCONNECTED = 3;

		class ViewHolder {
			public TextView nickname;
			public TextView caption;
			public ImageView icon;
		}

		public HostAdapter(Context context, List<HostBean> hosts, TerminalManager manager) {
			super(context, R.layout.item_host, hosts);

			this.hosts = hosts;
			this.manager = manager;

			red = context.getResources().getColorStateList(R.color.red);
			green = context.getResources().getColorStateList(R.color.green);
			blue = context.getResources().getColorStateList(R.color.blue);
		}

		/**
		 * Check if we're connected to a terminal with the given host.
		 */
		private int getConnectedState(HostBean host) {
			// always disconnected if we dont have backend service
			if (this.manager == null)
				return STATE_UNKNOWN;

			if (manager.findBridge(host) != null)
				return STATE_CONNECTED;

			if (manager.disconnected.contains(host))
				return STATE_DISCONNECTED;

			return STATE_UNKNOWN;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.item_host, null, false);

				holder = new ViewHolder();

				holder.nickname = (TextView)convertView.findViewById(android.R.id.text1);
				holder.caption = (TextView)convertView.findViewById(android.R.id.text2);
				holder.icon = (ImageView)convertView.findViewById(android.R.id.icon);

				convertView.setTag(holder);
			} else
				holder = (ViewHolder) convertView.getTag();

			HostBean host = hosts.get(position);
			if (host == null) {
				// Well, something bad happened. We can't continue.
				Log.e("HostAdapter", "Host bean is null!");

				holder.nickname.setText("Error during lookup");
				holder.caption.setText("see 'adb logcat' for more");
				return convertView;
			}

			holder.nickname.setText(host.getNickname());

			switch (this.getConnectedState(host)) {
			case STATE_UNKNOWN:
				holder.icon.setImageState(new int[] { }, true);
				break;
			case STATE_CONNECTED:
				holder.icon.setImageState(new int[] { android.R.attr.state_checked }, true);
				break;
			case STATE_DISCONNECTED:
				holder.icon.setImageState(new int[] { android.R.attr.state_expanded }, true);
				break;
			}

			ColorStateList chosen = null;
			if (HostDatabase.COLOR_RED.equals(host.getColor()))
				chosen = this.red;
			else if (HostDatabase.COLOR_GREEN.equals(host.getColor()))
				chosen = this.green;
			else if (HostDatabase.COLOR_BLUE.equals(host.getColor()))
				chosen = this.blue;

			Context context = convertView.getContext();

			if (chosen != null) {
				// set color normally if not selected
				holder.nickname.setTextColor(chosen);
				holder.caption.setTextColor(chosen);
			} else {
				// selected, so revert back to default black text
				holder.nickname.setTextAppearance(context, android.R.attr.textAppearanceLarge);
				holder.caption.setTextAppearance(context, android.R.attr.textAppearanceSmall);
			}

			long now = System.currentTimeMillis() / 1000;

			String nice = context.getString(R.string.bind_never);
			if (host.getLastConnect() > 0) {
				int minutes = (int)((now - host.getLastConnect()) / 60);
				if (minutes >= 60) {
					int hours = (minutes / 60);
					if (hours >= 24) {
						int days = (hours / 24);
						nice = context.getString(R.string.bind_days, days);
					} else
						nice = context.getString(R.string.bind_hours, hours);
				} else
					nice = context.getString(R.string.bind_minutes, minutes);
			}

			holder.caption.setText(nice);

			return convertView;
		}
	}
}
