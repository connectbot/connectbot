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

import java.util.List;

import org.connectbot.bean.HostBean;
import org.connectbot.service.OnHostStatusChangedListener;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.StyleRes;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

public class HostListActivity extends ListActivity implements OnHostStatusChangedListener {
	public final static String TAG = "CB.HostListActivity";
	public static final String DISCONNECT_ACTION = "org.connectbot.action.DISCONNECT";

	public final static int REQUEST_EDIT = 1;

	protected TerminalManager bound = null;

	protected HostDatabase hostdb;
	private List<HostBean> hosts;
	protected LayoutInflater inflater = null;

	protected boolean sortedByColor = false;

	private MenuItem sortcolor;

	private MenuItem sortlast;

	private Spinner transportSpinner;
	private TextView quickconnect;

	private SharedPreferences prefs = null;

	protected boolean makingShortcut = false;

	private boolean waitingForDisconnectAll = false;

	/**
	 * Whether to close the activity when disconnectAll is called. True if this activity was
	 * only brought to the foreground via the notification button to disconnect all hosts.
	 */
	private boolean closeOnDisconnectAll = true;

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// update our listview binder to find the service
			HostListActivity.this.updateList();

			bound.registerOnHostStatusChangedListener(HostListActivity.this);

			if (waitingForDisconnectAll) {
				disconnectAll();
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			bound.unregisterOnHostStatusChangedListener(HostListActivity.this);

			bound = null;
			HostListActivity.this.updateList();
		}
	};

	@Override
	public void onStart() {
		super.onStart();

		// start the terminal manager service
		this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

		hostdb = HostDatabase.get(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		this.unbindService(connection);

		hostdb = null;

		closeOnDisconnectAll = true;
	}

	@Override
	public void onResume() {
		super.onResume();

		// Must disconnectAll before setting closeOnDisconnectAll to know whether to keep the
		// activity open after disconnecting.
		if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0 &&
				DISCONNECT_ACTION.equals(getIntent().getAction())) {
			Log.d(TAG, "Got disconnect all request");
			disconnectAll();
		}

		// Still close on disconnect if waiting for a disconnect.
		closeOnDisconnectAll = waitingForDisconnectAll && closeOnDisconnectAll;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		setIntent(intent);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_EDIT) {
			this.updateList();
		}
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_hostlist);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_hosts_list)));

		this.prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// detect HTC Dream and apply special preferences
		if (Build.MANUFACTURER.equals("HTC") && Build.DEVICE.equals("dream")) {
			SharedPreferences.Editor editor = prefs.edit();
			boolean doCommit = false;
			if (!prefs.contains(PreferenceConstants.SHIFT_FKEYS) &&
					!prefs.contains(PreferenceConstants.CTRL_FKEYS)) {
				editor.putBoolean(PreferenceConstants.SHIFT_FKEYS, true);
				editor.putBoolean(PreferenceConstants.CTRL_FKEYS, true);
				doCommit = true;
			}
			if (!prefs.contains(PreferenceConstants.STICKY_MODIFIERS)) {
				editor.putString(PreferenceConstants.STICKY_MODIFIERS, PreferenceConstants.YES);
				doCommit = true;
			}
			if (!prefs.contains(PreferenceConstants.KEYMODE)) {
				editor.putString(PreferenceConstants.KEYMODE, PreferenceConstants.KEYMODE_RIGHT);
				doCommit = true;
			}
			if (doCommit) {
				editor.commit();
			}
		}

		this.makingShortcut = Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())
								|| Intent.ACTION_PICK.equals(getIntent().getAction());

		// connect with hosts database and populate list
		this.hostdb = HostDatabase.get(this);
		ListView list = this.getListView();

		this.sortedByColor = prefs.getBoolean(PreferenceConstants.SORT_BY_COLOR, false);

		//this.list.setSelector(R.drawable.highlight_disabled_pressed);

		list.setOnItemClickListener(new OnItemClickListener() {

			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				// launch off to console details
				HostBean host = (HostBean) parent.getAdapter().getItem(position);
				Uri uri = host.getUri();

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
					contents.setClass(HostListActivity.this, ConsoleActivity.class);
					HostListActivity.this.startActivity(contents);
				}
			}
		});

		this.registerForContextMenu(list);

		quickconnect = (TextView) this.findViewById(R.id.front_quickconnect);
		quickconnect.setVisibility(makingShortcut ? View.GONE : View.VISIBLE);
		quickconnect.setOnKeyListener(new OnKeyListener() {

			public boolean onKey(View v, int keyCode, KeyEvent event) {

				if (event.getAction() == KeyEvent.ACTION_UP) return false;
				if (keyCode != KeyEvent.KEYCODE_ENTER) return false;

				return startConsoleActivity();
			}
		});

		transportSpinner = (Spinner) findViewById(R.id.transport_selection);
		transportSpinner.setVisibility(makingShortcut ? View.GONE : View.VISIBLE);
		ArrayAdapter<String> transportSelection = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, TransportFactory.getTransportNames());
		transportSelection.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		transportSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> arg0, View view, int position, long id) {
				String formatHint = TransportFactory.getFormatHint(
						(String) transportSpinner.getSelectedItem(),
						HostListActivity.this);

				quickconnect.setHint(formatHint);
				quickconnect.setError(null);
				quickconnect.requestFocus();
			}
			public void onNothingSelected(AdapterView<?> arg0) { }
		});
		transportSpinner.setAdapter(transportSelection);

		this.inflater = LayoutInflater.from(this);
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
		if (makingShortcut) return true;

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

		MenuItem colors = menu.add(R.string.title_colors);
		colors.setIcon(android.R.drawable.ic_menu_slideshow);
		colors.setIntent(new Intent(HostListActivity.this, ColorsActivity.class));

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
		final TerminalBridge bridge = (bound == null) ? null : bound.getConnectedBridge(host);
		connect.setEnabled(bridge != null);
		connect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				bridge.dispatchDisconnect(true);
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
		if (!TransportFactory.canForwardPorts(host.getProtocol()))
			portForwards.setEnabled(false);

		MenuItem delete = menu.add(R.string.list_host_delete);
		delete.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// prompt user to make sure they really want this
				new AlertDialog.Builder(HostListActivity.this)
						.setMessage(getString(R.string.delete_message, host.getNickname()))
						.setPositiveButton(R.string.delete_pos, new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								// make sure we disconnect
								if (bridge != null)
									bridge.dispatchDisconnect(true);

								hostdb.deleteHost(host);
								updateList();
							}
						})
						.setNegativeButton(R.string.delete_neg, null).create().show();

				return true;
			}
		});
	}
	/**
	 * Disconnects all active connections and closes the activity if appropriate.
	 */
	private void disconnectAll() {
		if (bound == null) {
			waitingForDisconnectAll = true;
			return;
		}

		new AlertDialog.Builder(HostListActivity.this)
			.setMessage(getString(R.string.disconnect_all_message))
			.setPositiveButton(R.string.disconnect_all_pos, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					bound.disconnectAll(true, false);
					waitingForDisconnectAll = false;

					// Clear the intent so that the activity can be relaunched without closing.
					// TODO(jlklein): Find a better way to do this.
					setIntent(new Intent());

					if (closeOnDisconnectAll) {
						finish();
					}
				}
			})
			.setNegativeButton(R.string.disconnect_all_neg, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					waitingForDisconnectAll = false;
					// Clear the intent so that the activity can be relaunched without closing.
					// TODO(jlklein): Find a better way to do this.
					setIntent(new Intent());
				}
			}).create().show();
	}


	/**
	 * @return
	 */
	private boolean startConsoleActivity() {
		Uri uri = TransportFactory.getUri((String) transportSpinner
				.getSelectedItem(), quickconnect.getText().toString());

		if (uri == null) {
			quickconnect.setError(getString(R.string.list_format_error,
					TransportFactory.getFormatHint(
							(String) transportSpinner.getSelectedItem(),
							HostListActivity.this)));
			return false;
		}

		HostBean host = TransportFactory.findHost(hostdb, uri);
		if (host == null) {
			host = TransportFactory.getTransport(uri.getScheme()).createHost(uri);
			host.setColor(HostDatabase.COLOR_GRAY);
			host.setPubkeyId(HostDatabase.PUBKEYID_ANY);
			hostdb.saveHost(host);
		}

		Intent intent = new Intent(HostListActivity.this, ConsoleActivity.class);
		intent.setData(uri);
		startActivity(intent);

		// Clear the input box for the next entry.
		quickconnect.setText("");

		return true;
	}

	protected void updateList() {
		if (prefs.getBoolean(PreferenceConstants.SORT_BY_COLOR, false) != sortedByColor) {
			Editor edit = prefs.edit();
			edit.putBoolean(PreferenceConstants.SORT_BY_COLOR, sortedByColor);
			edit.commit();
		}

		if (hostdb == null)
			hostdb = HostDatabase.get(this);

		hosts = hostdb.getHosts(sortedByColor);

		// Don't lose hosts that are connected via shortcuts but not in the database.
		if (bound != null) {
			for (TerminalBridge bridge : bound.getBridges()) {
				if (!hosts.contains(bridge.host))
					hosts.add(0, bridge.host);
			}
		}

		HostAdapter adapter = new HostAdapter(this, hosts, bound);

		this.setListAdapter(adapter);
	}

	@Override
	public void onHostStatusChanged() {
		updateList();
	}

	static class HostAdapter extends BaseAdapter {
		private final LayoutInflater inflater;
		private final List<HostBean> hosts;
		private final TerminalManager manager;
		private final ColorStateList red, green, blue;

		public final static int STATE_UNKNOWN = 1, STATE_CONNECTED = 2, STATE_DISCONNECTED = 3;

		class ViewHolder {
			public TextView nickname;
			public TextView caption;
			public ImageView icon;
		}

		public HostAdapter(Context context, List<HostBean> hosts, TerminalManager manager) {
			this.inflater = LayoutInflater.from(context);
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
			// always disconnected if we don't have backend service
			if (this.manager == null)
				return STATE_UNKNOWN;

			if (manager.getConnectedBridge(host) != null)
				return STATE_CONNECTED;

			if (manager.disconnected.contains(host))
				return STATE_DISCONNECTED;

			return STATE_UNKNOWN;
		}

		@Override
		public int getCount() {
			return hosts.size();
		}

		@Override
		public Object getItem(int position) {
			return hosts.get(position);
		}

		/**
		 * Use the database's IDs for the host.
		 */
		@Override
		public long getItemId(int position) {
			return hosts.get(position).getId();
		}

		/**
		 * Since we're using the database's IDs, they're unchanging.
		 */
		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.item_host, null, false);

				holder = new ViewHolder();

				holder.nickname = (TextView) convertView.findViewById(android.R.id.text1);
				holder.caption = (TextView) convertView.findViewById(android.R.id.text2);
				holder.icon = (ImageView) convertView.findViewById(android.R.id.icon);

				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

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

			@StyleRes final int chosenStyleFirstLine;
			@StyleRes final int chosenStyleSecondLine;
			if (HostDatabase.COLOR_RED.equals(host.getColor())) {
				chosenStyleFirstLine = R.style.ListItemFirstLineText_Red;
				chosenStyleSecondLine = R.style.ListItemSecondLineText_Red;
			} else if (HostDatabase.COLOR_GREEN.equals(host.getColor())) {
				chosenStyleFirstLine = R.style.ListItemFirstLineText_Green;
				chosenStyleSecondLine = R.style.ListItemSecondLineText_Green;
			} else if (HostDatabase.COLOR_BLUE.equals(host.getColor())) {
				chosenStyleFirstLine = R.style.ListItemFirstLineText_Blue;
				chosenStyleSecondLine = R.style.ListItemSecondLineText_Blue;
			} else {
				chosenStyleFirstLine = R.style.ListItemFirstLineText;
				chosenStyleSecondLine = R.style.ListItemSecondLineText;
			}

			holder.nickname.setTextAppearance(chosenStyleFirstLine);
			holder.caption.setTextAppearance(chosenStyleSecondLine);

			Context context = convertView.getContext();
			CharSequence nice = context.getString(R.string.bind_never);
			if (host.getLastConnect() > 0) {
				nice = DateUtils.getRelativeTimeSpanString(host.getLastConnect() * 1000);
			}

			holder.caption.setText(nice);

			return convertView;
		}
	}
}
