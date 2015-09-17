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

import android.app.Activity;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import java.util.List;

import org.connectbot.bean.HostBean;
import org.connectbot.data.HostStorage;
import org.connectbot.service.OnHostStatusChangedListener;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

public class HostListActivity extends Activity implements OnHostStatusChangedListener {
	public final static String TAG = "CB.HostListActivity";
	public static final String DISCONNECT_ACTION = "org.connectbot.action.DISCONNECT";

	public final static int REQUEST_EDIT = 1;

	protected TerminalManager bound = null;

	private HostStorage hostdb;
	private List<HostBean> hosts;
	protected LayoutInflater inflater = null;

	private RecyclerView mHostListView;
	private HostAdapter mAdapter;
	private View mEmptyView;

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

		mHostListView = (RecyclerView) findViewById(R.id.list);
		mHostListView.setHasFixedSize(true);
		mHostListView.setLayoutManager(new LinearLayoutManager(this));
		mHostListView.addItemDecoration(new HostListItemDecoration(this));

		mEmptyView = findViewById(R.id.empty);

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

		this.sortedByColor = prefs.getBoolean(PreferenceConstants.SORT_BY_COLOR, false);

		this.registerForContextMenu(mHostListView);

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

		mAdapter = new HostAdapter(this, hosts, bound);
		mHostListView.setAdapter(mAdapter);
		adjustViewVisibility();
	}

	@Override
	public void onHostStatusChanged() {
		updateList();
	}

	/**
	 * If the host list is empty, hides the list and shows the empty message; otherwise, shows
	 * the list and hides the empty message.
	 */
	private void adjustViewVisibility() {
		boolean isEmpty = mAdapter.getItemCount() == 0;
		mHostListView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
		mEmptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
	}

	private class HostAdapter extends RecyclerView.Adapter<HostAdapter.ViewHolder> {
		private final LayoutInflater inflater;
		private final List<HostBean> hosts;
		private final TerminalManager manager;
		private final Context context;

		public final static int STATE_UNKNOWN = 1, STATE_CONNECTED = 2, STATE_DISCONNECTED = 3;

		class ViewHolder extends RecyclerView.ViewHolder
				implements View.OnClickListener, View.OnCreateContextMenuListener {
			public final ImageView icon;
			public final TextView nickname;
			public final TextView caption;
			public HostBean host;

			public ViewHolder(View v) {
				super(v);
				v.setOnClickListener(this);
				v.setOnCreateContextMenuListener(this);

				icon = (ImageView) v.findViewById(android.R.id.icon);
				nickname = (TextView) v.findViewById(android.R.id.text1);
				caption = (TextView) v.findViewById(android.R.id.text2);
			}

			@Override
			public void onClick(View v) {
				// launch off to console details
				Uri uri = host.getUri();

				Intent contents = new Intent(Intent.ACTION_VIEW, uri);
				contents.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

				if (makingShortcut) {
					// create shortcut if requested
					ShortcutIconResource icon = Intent.ShortcutIconResource.fromContext(
							HostListActivity.this, R.drawable.icon);

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

			@Override
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
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
		}

		public HostAdapter(Context context, List<HostBean> hosts, TerminalManager manager) {
			this.context = context;
			this.hosts = hosts;
			this.manager = manager;
			this.inflater = LayoutInflater.from(context);
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
		public HostAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View v = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.item_host, parent, false);
			ViewHolder vh = new ViewHolder(v);
			return vh;
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			HostBean host = hosts.get(position);
			if (host == null) {
				// Well, something bad happened. We can't continue.
				Log.e("HostAdapter", "Host bean is null!");

				holder.nickname.setText("Error during lookup");
				holder.caption.setText("see 'adb logcat' for more");
			}
			holder.host = host;

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

			holder.nickname.setTextAppearance(context, chosenStyleFirstLine);
			holder.caption.setTextAppearance(context, chosenStyleSecondLine);

			CharSequence nice = context.getString(R.string.bind_never);
			if (host.getLastConnect() > 0) {
				nice = DateUtils.getRelativeTimeSpanString(host.getLastConnect() * 1000);
			}

			holder.caption.setText(nice);
		}

		public HostBean getItem(int position) {
			return hosts.get(position);
		}

		@Override
		public long getItemId(int position) {
			return hosts.get(position).getId();
		}

		@Override
		public int getItemCount() {
			return hosts.size();
		}
	}

	/**
	 * Item decorations for host list items, which adds a divider between items and leaves a
	 * small offset at the top of the list to adhere to the Material Design spec.
	 */
	private class HostListItemDecoration extends RecyclerView.ItemDecoration {
		private final int[] ATTRS = new int[]{
			android.R.attr.listDivider
		};

		private final int TOP_LIST_OFFSET = 8;

		private Drawable mDivider;

		public HostListItemDecoration(Context c) {
			final TypedArray a = c.obtainStyledAttributes(ATTRS);
			mDivider = a.getDrawable(0);
			a.recycle();
		}

		@Override
		public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
			final int left = parent.getPaddingLeft();
			final int right = parent.getWidth() - parent.getPaddingRight();

			final int childCount = parent.getChildCount();
			for (int i = 0; i < childCount; i++) {
				final View child = parent.getChildAt(i);
				final RecyclerView.LayoutParams params =
						(RecyclerView.LayoutParams) child.getLayoutParams();
				final int top = child.getBottom() + params.bottomMargin;
				final int bottom = top + mDivider.getIntrinsicHeight();
				mDivider.setBounds(left, top, right, bottom);
				mDivider.draw(c);
			}
		}

		@Override
		public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
				RecyclerView.State state) {
			int top = parent.getChildAdapterPosition(view) == 0 ? TOP_LIST_OFFSET : 0;
			outRect.set(0, top, 0, mDivider.getIntrinsicHeight());
		}
	}
}
