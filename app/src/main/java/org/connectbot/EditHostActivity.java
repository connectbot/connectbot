/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.connectbot.bean.HostBean;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PubkeyDatabase;

public class EditHostActivity extends AppCompatActivity implements HostEditorFragment.Listener {

	private static final String EXTRA_EXISTING_HOST_ID = "org.connectbot.existing_host_id";
	private static final long NO_HOST_ID = -1;
	private static final int ENABLED_ALPHA = 255;
	private static final int DISABLED_ALPHA = 130;

	private HostDatabase mHostDb;
	private PubkeyDatabase mPubkeyDb;
	private ServiceConnection mTerminalConnection;
	private HostBean mHost;
	private TerminalBridge mBridge;
	private boolean mIsCreating;
	private MenuItem mSaveHostButton;

	public static Intent createIntentForExistingHost(Context context, long existingHostId) {
		Intent i = new Intent(context, EditHostActivity.class);
		i.putExtra(EXTRA_EXISTING_HOST_ID, existingHostId);
		return i;
	}

	public static Intent createIntentForNewHost(Context context) {
		return createIntentForExistingHost(context, NO_HOST_ID);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mHostDb = HostDatabase.get(this);
		mPubkeyDb = PubkeyDatabase.get(this);

		mTerminalConnection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName className, IBinder service) {
				TerminalManager bound = ((TerminalManager.TerminalBinder) service).getService();
				mBridge = bound.getConnectedBridge(mHost);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				mBridge = null;
			}
		};

		long hostId = getIntent().getLongExtra(EXTRA_EXISTING_HOST_ID, NO_HOST_ID);
		mIsCreating = hostId == NO_HOST_ID;
		mHost = mIsCreating ? null : mHostDb.findHostById(hostId);

		// Note that the lists must be explicitly declared as ArrayLists because Bundle only accepts
		// ArrayLists of Strings.
		ArrayList<String> pubkeyNames = new ArrayList<>();
		ArrayList<String> pubkeyValues = new ArrayList<>();

		// First, add default pubkey names and values (e.g., "use any" and "don't use any").
		TypedArray defaultPubkeyNames = getResources().obtainTypedArray(R.array.list_pubkeyids);
		for (int i = 0; i < defaultPubkeyNames.length(); i++) {
			pubkeyNames.add(defaultPubkeyNames.getString(i));
		}
		TypedArray defaultPubkeyValues = getResources().obtainTypedArray(R.array.list_pubkeyids_value);
		for (int i = 0; i < defaultPubkeyValues.length(); i++) {
			pubkeyValues.add(defaultPubkeyValues.getString(i));
		}

		// Now, add pubkeys which have been added by the user.
		for (CharSequence cs : mPubkeyDb.allValues(PubkeyDatabase.FIELD_PUBKEY_NICKNAME)) {
			pubkeyNames.add(cs.toString());
		}
		for (CharSequence cs : mPubkeyDb.allValues("_id")) {
			pubkeyValues.add(cs.toString());
		}

		setContentView(R.layout.activity_edit_host);
		FragmentManager fm = getSupportFragmentManager();
		HostEditorFragment fragment =
				(HostEditorFragment) fm.findFragmentById(R.id.fragment_container);

		if (fragment == null) {
			fragment = HostEditorFragment.newInstance(mHost, pubkeyNames, pubkeyValues);
			getSupportFragmentManager().beginTransaction()
					.add(R.id.fragment_container, fragment).commit();
		}

		defaultPubkeyNames.recycle();
		defaultPubkeyValues.recycle();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(
				mIsCreating ? R.menu.edit_host_activity_add_menu : R.menu.edit_host_activity_edit_menu,
				menu);

		mSaveHostButton = menu.getItem(0);

		// If the new host is being created, it can't be added until modifications have been made.
		setAddSaveButtonEnabled(!mIsCreating);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.save:
			case android.R.id.home:
				attemptSaveAndExit();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		bindService(new Intent(
				this, TerminalManager.class), mTerminalConnection, Context.BIND_AUTO_CREATE);

		final HostEditorFragment fragment = (HostEditorFragment) getSupportFragmentManager().
				findFragmentById(R.id.fragment_container);
		if (CharsetHolder.isInitialized()) {
			fragment.setCharsetData(CharsetHolder.getCharsetData());
		} else {
			// If CharsetHolder is uninitialized, initialize it in an AsyncTask. This is necessary
			// because Charset must touch the disk, which cannot be performed on the UI thread.
			AsyncTask<Void, Void, Void> charsetTask = new AsyncTask<Void, Void, Void>() {

				@Override
				protected Void doInBackground(Void... unused) {
					CharsetHolder.initialize();
					return null;
				}

				@Override
				protected void onPostExecute(Void unused) {
					fragment.setCharsetData(CharsetHolder.getCharsetData());
				}
			};
			charsetTask.execute();
		}
	}

	@Override
	public void onStop() {
		super.onStop();

		unbindService(mTerminalConnection);
	}

	@Override
	public void onValidHostConfigured(HostBean host) {
		mHost = host;
		if (mSaveHostButton != null)
			setAddSaveButtonEnabled(true);
	}

	@Override
	public void onHostInvalidated() {
		mHost = null;
		if (mSaveHostButton != null)
			setAddSaveButtonEnabled(false);
	}

	@Override
	public void onBackPressed() {
		attemptSaveAndExit();
	}

	/**
	 * If the host represents a valid URI, save it and exit; otherwise, pop up a dialog asking
	 * the user if he/she wants to discard the changes.
	 */
	private void attemptSaveAndExit() {
		if (mHost == null) {
			showDiscardDialog();
			return;
		}

		mHostDb.saveHost(mHost);

		if (mBridge != null) {
			// If the console is already open, apply the new encoding now. If the console
			// was not yet opened, this will be applied automatically when it is opened.
			mBridge.setCharset(mHost.getEncoding());
		}
		finish();
	}

	private void showDiscardDialog() {
		android.support.v7.app.AlertDialog.Builder builder =
				new android.support.v7.app.AlertDialog.Builder(this, R.style.AlertDialogTheme);
		builder.setMessage(R.string.discard_host_changes_message)
				.setPositiveButton(R.string.discard_host_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Do not save to the database - just exit.
						finish();
					}
				})
				.setNegativeButton(R.string.discard_host_cancel_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Do nothing.
					}
				});
		builder.show();
	}

	private void setAddSaveButtonEnabled(boolean enabled) {
		mSaveHostButton.setEnabled(enabled);
		mSaveHostButton.getIcon().setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
	}

	// Private static class used to generate a list of available Charsets. Note that this class
	// must not be initialized by the UI thread because it blocks on disk access.
	private static class CharsetHolder {
		private static boolean mInitialized = false;

		// Map from Charset display name to Charset value (i.e., unique ID).
		private static Map<String, String> mData;

		public static Map<String, String> getCharsetData() {
			if (mData == null)
				initialize();

			return mData;
		}

		private synchronized static void initialize() {
			if (mInitialized)
				return;

			mData = new HashMap<>();
			for (Map.Entry<String, Charset> entry : Charset.availableCharsets().entrySet()) {
				Charset c = entry.getValue();
				if (c.canEncode() && c.isRegistered()) {
					String key = entry.getKey();
					if (key.startsWith("cp")) {
						// Custom CP437 charset changes.
						mData.put("CP437", "CP437");
					}
					mData.put(c.displayName(), entry.getKey());
				}
			}

			mInitialized = true;
		}

		public static boolean isInitialized() {
			return mInitialized;
		}
	}
}
