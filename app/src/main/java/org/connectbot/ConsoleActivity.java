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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.connectbot.bean.HostBean;
import org.connectbot.service.BridgeDisconnectedListener;
import org.connectbot.service.PromptHelper;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalKeyListener;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.PreferenceConstants;
import org.connectbot.util.TerminalViewPager;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import com.google.android.material.tabs.TabLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.view.MenuItemCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import de.mud.terminal.vt320;

public class ConsoleActivity extends AppCompatActivity implements BridgeDisconnectedListener {
	public final static String TAG = "CB.ConsoleActivity";

	protected static final int REQUEST_EDIT = 1;

	private static final int KEYBOARD_DISPLAY_TIME = 3000;
	private static final int KEYBOARD_REPEAT_INITIAL = 500;
	private static final int KEYBOARD_REPEAT = 100;
	private static final String STATE_SELECTED_URI = "selectedUri";

	protected TerminalViewPager pager = null;
	protected TabLayout tabs = null;
	protected Toolbar toolbar = null;
	@Nullable
	protected TerminalManager bound = null;
	protected TerminalPagerAdapter adapter = null;
	protected LayoutInflater inflater = null;

	private SharedPreferences prefs = null;

	// determines whether or not menuitem accelerators are bound
	// otherwise they collide with an external keyboard's CTRL-char
	private boolean hardKeyboard = false;

	protected Uri requested;

	protected ClipboardManager clipboard;
	private RelativeLayout stringPromptGroup;
	protected EditText stringPrompt;
	private TextView stringPromptInstructions;

	private RelativeLayout booleanPromptGroup;
	private TextView booleanPrompt;
	private Button booleanYes;

	private LinearLayout keyboardGroup;
	private Runnable keyboardGroupHider;

	private TextView empty;

	private Animation fade_out_delayed;

	private Animation keyboard_fade_in, keyboard_fade_out;

	private MenuItem disconnect;
	private MenuItem paste;
	private MenuItem portForward;
	private MenuItem resize;
	private MenuItem urlscan;

	private boolean forcedOrientation;

	private final Handler handler = new Handler();

	private View contentView;

	private ImageView mKeyboardButton;

	@Nullable private ActionBar actionBar;
	private boolean inActionBarMenu = false;
	private boolean titleBarHide;
	private boolean keyboardAlwaysVisible = false;

	private final ServiceConnection connection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// let manager know about our event handling services
			bound.disconnectListener = ConsoleActivity.this;
			bound.setResizeAllowed(true);

			final String requestedNickname = (requested != null) ? requested.getFragment() : null;
			TerminalBridge requestedBridge = bound.getConnectedBridge(requestedNickname);

			// If we didn't find the requested connection, try opening it
			if (requestedNickname != null && requestedBridge == null) {
				try {
					Log.d(TAG, String.format("We couldn't find an existing bridge with URI=%s (nickname=%s), so creating one now", requested.toString(), requestedNickname));
					requestedBridge = bound.openConnection(requested);
				} catch (Exception e) {
					Log.e(TAG, "Problem while trying to create new requested bridge from URI", e);
				}
			}

			// create views for all bridges on this service
			adapter.notifyDataSetChanged();
			final int requestedIndex = bound.getBridges().indexOf(requestedBridge);

			if (requestedBridge != null)
				requestedBridge.promptHelper.setHandler(promptHandler);


			if (requestedIndex != -1) {
				pager.post(new Runnable() {
					@Override
					public void run() {
						setDisplayedTerminal(requestedIndex);
					}
				});
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			bound = null;
			adapter.notifyDataSetChanged();
			updateEmptyVisible();
		}
	};

	protected Handler promptHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// someone below us requested to display a prompt
			updatePromptVisible();
		}
	};

	@Override
	public void onDisconnected(TerminalBridge bridge) {
		synchronized (adapter) {
			adapter.notifyDataSetChanged();
			Log.d(TAG, "Someone sending HANDLE_DISCONNECT to parentHandler");

			if (bridge.isAwaitingClose()) {
				closeBridge(bridge);
			}
		}
	}

	protected OnClickListener emulatedKeysListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			onEmulatedKeyClicked(v);
		}
	};

	protected Handler keyRepeatHandler = new Handler();


	/**
	 * Handle repeatable virtual keys and touch events
	 */
	public class KeyRepeater implements Runnable, OnTouchListener, OnClickListener {
		private final View mView;
		private final Handler mHandler;
		private boolean mDown;

		public KeyRepeater(Handler handler, View view) {
			mView = view;
			mHandler = handler;
			mDown = false;
		}

		@Override
		public void run() {
			mDown = true;
			mHandler.removeCallbacks(this);
			mHandler.postDelayed(this, KEYBOARD_REPEAT);
			mView.performClick();
		}

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "KeyRepeater.onTouch(" + v.getId() + ", " +
						event.getAction() + ", " +
						event.getActionIndex() + ", " +
						event.getActionMasked() + ");");
			}
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				mDown = false;
				mHandler.postDelayed(this, KEYBOARD_REPEAT_INITIAL);
				mView.setPressed(true);
				return (true);

			case MotionEvent.ACTION_CANCEL:
				mHandler.removeCallbacks(this);
				mView.setPressed(false);
				return (true);

			case MotionEvent.ACTION_UP:
				mHandler.removeCallbacks(this);
				mView.setPressed(false);

				if (!mDown) {
					mView.performClick();
				}
				return (true);
			}
			return false;
		}

		@Override
		public void onClick(View view) {
			onEmulatedKeyClicked(view);
		}
	}

	private void onEmulatedKeyClicked(View v) {
		TerminalView terminal = adapter.getCurrentTerminalView();
		if (terminal == null) return;

		if (BuildConfig.DEBUG) {
			Log.d(TAG, "onEmulatedKeyClicked(" + v.getId() + ")");
		}
		TerminalKeyListener handler = terminal.bridge.getKeyHandler();
		boolean hideKeys = false;

		switch (v.getId()) {
		case R.id.button_ctrl:
			handler.metaPress(TerminalKeyListener.OUR_CTRL_ON, true);
			hideKeys = true;
			break;
		case R.id.button_esc:
			handler.sendEscape();
			hideKeys = true;
			break;
		case R.id.button_tab:
			handler.sendTab();
			hideKeys = true;
			break;

		case R.id.button_up:
			handler.sendPressedKey(vt320.KEY_UP);
			break;
		case R.id.button_down:
			handler.sendPressedKey(vt320.KEY_DOWN);
			break;
		case R.id.button_left:
			handler.sendPressedKey(vt320.KEY_LEFT);
			break;
		case R.id.button_right:
			handler.sendPressedKey(vt320.KEY_RIGHT);
			break;

		case R.id.button_home:
			handler.sendPressedKey(vt320.KEY_HOME);
			break;
		case R.id.button_end:
			handler.sendPressedKey(vt320.KEY_END);
			break;
		case R.id.button_pgup:
			handler.sendPressedKey(vt320.KEY_PAGE_UP);
			break;
		case R.id.button_pgdn:
			handler.sendPressedKey(vt320.KEY_PAGE_DOWN);
			break;

		case R.id.button_f1:
			handler.sendPressedKey(vt320.KEY_F1);
			break;
		case R.id.button_f2:
			handler.sendPressedKey(vt320.KEY_F2);
			break;
		case R.id.button_f3:
			handler.sendPressedKey(vt320.KEY_F3);
			break;
		case R.id.button_f4:
			handler.sendPressedKey(vt320.KEY_F4);
			break;
		case R.id.button_f5:
			handler.sendPressedKey(vt320.KEY_F5);
			break;
		case R.id.button_f6:
			handler.sendPressedKey(vt320.KEY_F6);
			break;
		case R.id.button_f7:
			handler.sendPressedKey(vt320.KEY_F7);
			break;
		case R.id.button_f8:
			handler.sendPressedKey(vt320.KEY_F8);
			break;
		case R.id.button_f9:
			handler.sendPressedKey(vt320.KEY_F9);
			break;
		case R.id.button_f10:
			handler.sendPressedKey(vt320.KEY_F10);
			break;
		case R.id.button_f11:
			handler.sendPressedKey(vt320.KEY_F11);
			break;
		case R.id.button_f12:
			handler.sendPressedKey(vt320.KEY_F12);
			break;
		default:
			Log.e(TAG, "Unknown emulated key clicked: " + v.getId());
			break;
		}

		if (hideKeys) {
			hideEmulatedKeys();
		} else {
			autoHideEmulatedKeys();
		}

		terminal.bridge.tryKeyVibrate();
		hideActionBarIfRequested();
	}

	private void hideActionBarIfRequested() {
		if (titleBarHide && actionBar != null) {
			actionBar.hide();
		}
	}

	/**
	 * @param bridge
	 */
	private void closeBridge(final TerminalBridge bridge) {
		updateEmptyVisible();
		updatePromptVisible();

		// If we just closed the last bridge, go back to the previous activity.
		if (pager.getChildCount() == 0) {
			finish();
			unregisterMenuListeners();
		}
	}

	private void unregisterMenuListeners() {
		portForward.setOnMenuItemClickListener(null);
		disconnect.setOnMenuItemClickListener(null);
		paste.setOnMenuItemClickListener(null);
		portForward.setOnMenuItemClickListener(null);
		resize.setOnMenuItemClickListener(null);
		urlscan.setOnMenuItemClickListener(null);
	}

	protected View findCurrentView(int id) {
		View view = pager.findViewWithTag(adapter.getBridgeAtPosition(pager.getCurrentItem()));
		if (view == null) {
			return null;
		}
		return view.findViewById(id);
	}

	protected PromptHelper getCurrentPromptHelper() {
		TerminalView view = adapter.getCurrentTerminalView();
		if (view == null) return null;
		return view.bridge.promptHelper;
	}

	protected void hideAllPrompts() {
		stringPromptGroup.setVisibility(View.GONE);
		booleanPromptGroup.setVisibility(View.GONE);
	}

	private void showEmulatedKeys(boolean showActionBar) {
		if (keyboardGroup.getVisibility() == View.GONE) {
			keyboardGroup.startAnimation(keyboard_fade_in);
			keyboardGroup.setVisibility(View.VISIBLE);
		}
		if (showActionBar) {
			actionBar.show();
		}
		autoHideEmulatedKeys();
	}

	private void autoHideEmulatedKeys() {
		if (keyboardGroupHider != null) {
			handler.removeCallbacks(keyboardGroupHider);
		}

		keyboardGroupHider = new Runnable() {
			@Override
			public void run() {
				if (keyboardGroup.getVisibility() == View.GONE || inActionBarMenu) {
					return;
				}

				if (!keyboardAlwaysVisible) {
					keyboardGroup.startAnimation(keyboard_fade_out);
					keyboardGroup.setVisibility(View.GONE);
				}
				hideActionBarIfRequested();
				keyboardGroupHider = null;
			}
		};
		handler.postDelayed(keyboardGroupHider, KEYBOARD_DISPLAY_TIME);
	}

	private void hideEmulatedKeys() {
		if (!keyboardAlwaysVisible) {
			if (keyboardGroupHider != null)
				handler.removeCallbacks(keyboardGroupHider);
			keyboardGroup.setVisibility(View.GONE);
		}
		hideActionBarIfRequested();
	}

	private void requestActionBar() {
		supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		StrictModeSetup.run();

		hardKeyboard = getResources().getConfiguration().keyboard ==
				Configuration.KEYBOARD_QWERTY;

		clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		titleBarHide = prefs.getBoolean(PreferenceConstants.TITLEBARHIDE, false);
		if (titleBarHide) {
			// This is a separate method because Gradle does not uniformly respect the conditional
			// Build check. See: https://code.google.com/p/android/issues/detail?id=137195
			requestActionBar();
		}

		this.setContentView(R.layout.act_console);

		// hide status bar if requested by user
		if (prefs.getBoolean(PreferenceConstants.FULLSCREEN, false)) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		// TODO find proper way to disable volume key beep if it exists.
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// handle requested console from incoming intent
		if (icicle == null) {
			requested = getIntent().getData();
		} else {
			String uri = icicle.getString(STATE_SELECTED_URI);
			if (uri != null) {
				requested = Uri.parse(uri);
			}
		}

		inflater = LayoutInflater.from(this);

		toolbar = findViewById(R.id.toolbar);

		pager = findViewById(R.id.console_flip);
		pager.addOnPageChangeListener(
				new TerminalViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						setTitle(adapter.getPageTitle(position));
						onTerminalChanged();
					}
				});
		adapter = new TerminalPagerAdapter();
		pager.setAdapter(adapter);

		empty = findViewById(android.R.id.empty);

		stringPromptGroup = findViewById(R.id.console_password_group);
		stringPromptInstructions = findViewById(R.id.console_password_instructions);
		stringPrompt = findViewById(R.id.console_password);
		stringPrompt.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_UP) return false;
				if (keyCode != KeyEvent.KEYCODE_ENTER) return false;

				// pass collected password down to current terminal
				String value = stringPrompt.getText().toString();

				PromptHelper helper = getCurrentPromptHelper();
				if (helper == null) return false;
				helper.setResponse(value);

				// finally clear password for next user
				stringPrompt.setText("");
				updatePromptVisible();

				return true;
			}
		});

		booleanPromptGroup = findViewById(R.id.console_boolean_group);
		booleanPrompt = findViewById(R.id.console_prompt);

		booleanYes = findViewById(R.id.console_prompt_yes);
		booleanYes.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				PromptHelper helper = getCurrentPromptHelper();
				if (helper == null) return;
				helper.setResponse(Boolean.TRUE);
				updatePromptVisible();
			}
		});

		Button booleanNo = findViewById(R.id.console_prompt_no);
		booleanNo.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				PromptHelper helper = getCurrentPromptHelper();
				if (helper == null) return;
				helper.setResponse(Boolean.FALSE);
				updatePromptVisible();
			}
		});

		fade_out_delayed = AnimationUtils.loadAnimation(this, R.anim.fade_out_delayed);

		// Preload animation for keyboard button
		keyboard_fade_in = AnimationUtils.loadAnimation(this, R.anim.keyboard_fade_in);
		keyboard_fade_out = AnimationUtils.loadAnimation(this, R.anim.keyboard_fade_out);

		keyboardGroup = findViewById(R.id.keyboard_group);

		keyboardAlwaysVisible = prefs.getBoolean(PreferenceConstants.KEY_ALWAYS_VISIBLE, false);
		if (keyboardAlwaysVisible) {
			// equivalent to android:layout_above=keyboard_group
			RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.MATCH_PARENT);
			layoutParams.addRule(RelativeLayout.ABOVE, R.id.keyboard_group);
			pager.setLayoutParams(layoutParams);

			layoutParams = new RelativeLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
			layoutParams.addRule(RelativeLayout.ABOVE, R.id.keyboard_group);
			findViewById(R.id.console_password_group).setLayoutParams(layoutParams);
			findViewById(R.id.console_boolean_group).setLayoutParams(layoutParams);

			// Show virtual keyboard
			keyboardGroup.setVisibility(View.VISIBLE);
		}

		mKeyboardButton = findViewById(R.id.button_keyboard);
		mKeyboardButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				View terminal = adapter.getCurrentTerminalView();
				if (terminal == null)
					return;
				InputMethodManager inputMethodManager =
					(InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.toggleSoftInputFromWindow(terminal.getApplicationWindowToken(),
					InputMethodManager.SHOW_FORCED, 0);
				terminal.requestFocus();
				hideEmulatedKeys();
			}
		});

		findViewById(R.id.button_ctrl).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_esc).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_tab).setOnClickListener(emulatedKeysListener);

		addKeyRepeater(findViewById(R.id.button_up));
		addKeyRepeater(findViewById(R.id.button_up));
		addKeyRepeater(findViewById(R.id.button_down));
		addKeyRepeater(findViewById(R.id.button_left));
		addKeyRepeater(findViewById(R.id.button_right));

		findViewById(R.id.button_home).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_end).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_pgup).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_pgdn).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f1).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f2).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f3).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f4).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f5).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f6).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f7).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f8).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f9).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f10).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f11).setOnClickListener(emulatedKeysListener);
		findViewById(R.id.button_f12).setOnClickListener(emulatedKeysListener);


		actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			if (titleBarHide) {
				actionBar.hide();
			}
			actionBar.addOnMenuVisibilityListener(new ActionBar.OnMenuVisibilityListener() {
				@Override
				public void onMenuVisibilityChanged(boolean isVisible) {
					inActionBarMenu = isVisible;
					if (!isVisible) {
						hideEmulatedKeys();
					}
				}
			});
		}

		final HorizontalScrollView keyboardScroll = findViewById(R.id.keyboard_hscroll);
		if (!hardKeyboard) {
			// Show virtual keyboard and scroll back and forth
			showEmulatedKeys(false);
			keyboardScroll.postDelayed(new Runnable() {
				@Override
				public void run() {
					final int xscroll = findViewById(R.id.button_f12).getRight();
					if (BuildConfig.DEBUG) {
						Log.d(TAG, "smoothScrollBy(toEnd[" + xscroll + "])");
					}
					keyboardScroll.smoothScrollBy(xscroll, 0);
					keyboardScroll.postDelayed(new Runnable() {
						@Override
						public void run() {
							if (BuildConfig.DEBUG) {
								Log.d(TAG, "smoothScrollBy(toStart[" + -xscroll + "])");
							}
							keyboardScroll.smoothScrollBy(-xscroll, 0);
						}
					}, 500);
				}
			}, 500);
		}

		// Reset keyboard auto-hide timer when scrolling
		keyboardScroll.setOnTouchListener(
				new OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						switch (event.getAction()) {
						case MotionEvent.ACTION_MOVE:
							autoHideEmulatedKeys();
							break;
						case MotionEvent.ACTION_UP:
							v.performClick();
							return (true);
						}
						return (false);
					}
				});

		tabs = findViewById(R.id.tabs);
		if (tabs != null)
			setupTabLayoutWithViewPager();

		pager.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				showEmulatedKeys(true);
			}
		});

		// Change keyboard button image according to soft keyboard visibility
		// How to detect keyboard visibility: http://stackoverflow.com/q/4745988
		contentView = findViewById(android.R.id.content);
		contentView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					Rect r = new Rect();
					contentView.getWindowVisibleDisplayFrame(r);
					int screenHeight = contentView.getRootView().getHeight();
					int keypadHeight = screenHeight - r.bottom;

					if (keypadHeight > screenHeight * 0.15) {
						// keyboard is opened
						mKeyboardButton.setImageResource(R.drawable.ic_keyboard_hide);
						mKeyboardButton.setContentDescription(getResources().getText(R.string.image_description_hide_keyboard));
					} else {
						// keyboard is closed
						mKeyboardButton.setImageResource(R.drawable.ic_keyboard);
						mKeyboardButton.setContentDescription(getResources().getText(R.string.image_description_show_keyboard));
					}
				}
			});
	}

	private void addKeyRepeater(View view) {
		KeyRepeater keyRepeater = new KeyRepeater(keyRepeatHandler, view);
		view.setOnClickListener(keyRepeater);
		view.setOnTouchListener(keyRepeater);
	}

	/**
	 * Ties the {@link TabLayout} to the {@link TerminalViewPager}.
	 *
	 * <p>This method will:
	 * <ul>
	 *     <li>Add a {@link TerminalViewPager.OnPageChangeListener} that will forward events to
	 *     this TabLayout.</li>
	 *     <li>Populate the TabLayout's tabs from the ViewPager's {@link PagerAdapter}.</li>
	 *     <li>Set our {@link TabLayout.OnTabSelectedListener} which will forward
	 *     selected events to the ViewPager</li>
	 * </ul>
	 * </p>
	 */
	public void setupTabLayoutWithViewPager() {
		tabs.setTabsFromPagerAdapter(adapter);
		pager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabs));
		tabs.setOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(pager));

		if (adapter.getCount() > 0) {
			final int curItem = pager.getCurrentItem();
			if (tabs.getSelectedTabPosition() != curItem) {
				tabs.getTabAt(curItem).select();
			}
		}
	}

	/**
	 *
	 */
	private void configureOrientation() {
		String rotateDefault;
		if (getResources().getConfiguration().keyboard == Configuration.KEYBOARD_NOKEYS)
			rotateDefault = PreferenceConstants.ROTATION_PORTRAIT;
		else
			rotateDefault = PreferenceConstants.ROTATION_LANDSCAPE;

		String rotate = prefs.getString(PreferenceConstants.ROTATION, rotateDefault);
		if (PreferenceConstants.ROTATION_DEFAULT.equals(rotate))
			rotate = rotateDefault;

		// request a forced orientation if requested by user
		if (PreferenceConstants.ROTATION_LANDSCAPE.equals(rotate)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			forcedOrientation = true;
		} else if (PreferenceConstants.ROTATION_PORTRAIT.equals(rotate)) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			forcedOrientation = true;
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
			forcedOrientation = false;
		}
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		TerminalView view = adapter.getCurrentTerminalView();
		final boolean activeTerminal = view != null;
		boolean sessionOpen = false;
		boolean disconnected = false;
		boolean canForwardPorts = false;

		if (activeTerminal) {
			TerminalBridge bridge = view.bridge;
			sessionOpen = bridge.isSessionOpen();
			disconnected = bridge.isDisconnected();
			canForwardPorts = bridge.canFowardPorts();
		}

		menu.setQwertyMode(true);

		disconnect = menu.add(R.string.list_host_disconnect);
		if (hardKeyboard)
			disconnect.setAlphabeticShortcut('w');
		if (!sessionOpen && disconnected)
			disconnect.setTitle(R.string.console_menu_close);
		disconnect.setEnabled(activeTerminal);
		disconnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		disconnect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				// disconnect or close the currently visible session
				TerminalView terminalView = adapter.getCurrentTerminalView();
				TerminalBridge bridge = terminalView.bridge;

				bridge.dispatchDisconnect(true);
				return true;
			}
		});

		paste = menu.add(R.string.console_menu_paste);
		if (hardKeyboard)
			paste.setAlphabeticShortcut('v');
		MenuItemCompat.setShowAsAction(paste, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
		paste.setIcon(R.drawable.ic_action_paste);
		paste.setEnabled(activeTerminal);
		paste.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				pasteIntoTerminal();
				return true;
			}
		});

		portForward = menu.add(R.string.console_menu_portforwards);
		if (hardKeyboard)
			portForward.setAlphabeticShortcut('f');
		portForward.setIcon(android.R.drawable.ic_menu_manage);
		portForward.setEnabled(sessionOpen && canForwardPorts);
		portForward.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				TerminalView terminalView = adapter.getCurrentTerminalView();
				TerminalBridge bridge = terminalView.bridge;

				Intent intent = new Intent(ConsoleActivity.this, PortForwardListActivity.class);
				intent.putExtra(Intent.EXTRA_TITLE, bridge.host.getId());
				ConsoleActivity.this.startActivityForResult(intent, REQUEST_EDIT);
				return true;
			}
		});

		urlscan = menu.add(R.string.console_menu_urlscan);
		if (hardKeyboard)
			urlscan.setAlphabeticShortcut('u');
		urlscan.setIcon(android.R.drawable.ic_menu_search);
		urlscan.setEnabled(activeTerminal);
		urlscan.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				final TerminalView terminalView = adapter.getCurrentTerminalView();

				List<String> urls = terminalView.bridge.scanForURLs();

				Dialog urlDialog = new Dialog(ConsoleActivity.this);
				urlDialog.setTitle(R.string.console_menu_urlscan);

				ListView urlListView = new ListView(ConsoleActivity.this);
				URLItemListener urlListener = new URLItemListener(ConsoleActivity.this);
				urlListView.setOnItemClickListener(urlListener);

				urlListView.setAdapter(new ArrayAdapter<>(ConsoleActivity.this, android.R.layout.simple_list_item_1, urls));
				urlDialog.setContentView(urlListView);
				urlDialog.show();

				return true;
			}
		});

		resize = menu.add(R.string.console_menu_resize);
		if (hardKeyboard)
			resize.setAlphabeticShortcut('s');
		resize.setIcon(android.R.drawable.ic_menu_crop);
		resize.setEnabled(sessionOpen);
		resize.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				final TerminalView terminalView = adapter.getCurrentTerminalView();

				@SuppressLint("InflateParams")  // Dialogs do not have a parent view.
				final View resizeView = inflater.inflate(R.layout.dia_resize, null, false);
				new androidx.appcompat.app.AlertDialog.Builder(
								ConsoleActivity.this, R.style.AlertDialogTheme)
						.setView(resizeView)
						.setPositiveButton(R.string.button_resize, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int which) {
								int width, height;
								try {
									width = Integer.parseInt(((EditText) resizeView
											.findViewById(R.id.width))
											.getText().toString());
									height = Integer.parseInt(((EditText) resizeView
											.findViewById(R.id.height))
											.getText().toString());
								} catch (NumberFormatException nfe) {
									// TODO change this to a real dialog where we can
									// make the input boxes turn red to indicate an error.
									return;
								}

								terminalView.forceSize(width, height);
							}
						}).setNegativeButton(android.R.string.cancel, null).create().show();

				return true;
			}
		});

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		setVolumeControlStream(AudioManager.STREAM_NOTIFICATION);

		final TerminalView view = adapter.getCurrentTerminalView();
		boolean activeTerminal = view != null;
		boolean sessionOpen = false;
		boolean disconnected = false;
		boolean canForwardPorts = false;

		if (activeTerminal) {
			TerminalBridge bridge = view.bridge;
			sessionOpen = bridge.isSessionOpen();
			disconnected = bridge.isDisconnected();
			canForwardPorts = bridge.canFowardPorts();
		}

		disconnect.setEnabled(activeTerminal);
		if (sessionOpen || !disconnected)
			disconnect.setTitle(R.string.list_host_disconnect);
		else
			disconnect.setTitle(R.string.console_menu_close);

		paste.setEnabled(activeTerminal);
		portForward.setEnabled(sessionOpen && canForwardPorts);
		urlscan.setEnabled(activeTerminal);
		resize.setEnabled(sessionOpen);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, HostListActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onOptionsMenuClosed(Menu menu) {
		super.onOptionsMenuClosed(menu);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	@Override
	public void onStart() {
		super.onStart();

		// connect with manager service to find all bridges
		// when connected it will insert all views
		bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.d(TAG, "onPause called");

		if (forcedOrientation && bound != null) {
			bound.setResizeAllowed(false);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume called");

		// Make sure we don't let the screen fall asleep.
		// This also keeps the Wi-Fi chipset from disconnecting us.
		if (prefs.getBoolean(PreferenceConstants.KEEP_ALIVE, true)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		} else {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}

		configureOrientation();

		if (forcedOrientation && bound != null) {
			bound.setResizeAllowed(true);
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onNewIntent(android.content.Intent)
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		Log.d(TAG, "onNewIntent called");

		requested = intent.getData();

		if (requested == null) {
			Log.e(TAG, "Got null intent data in onNewIntent()");
			return;
		}

		if (bound == null) {
			Log.e(TAG, "We're not bound in onNewIntent()");
			return;
		}

		TerminalBridge requestedBridge = bound.getConnectedBridge(requested.getFragment());
		int requestedIndex = 0;

		synchronized (pager) {
			if (requestedBridge == null) {
				// If we didn't find the requested connection, try opening it

				try {
					Log.d(TAG, String.format("We couldnt find an existing bridge with URI=%s (nickname=%s)," +
							"so creating one now", requested.toString(), requested.getFragment()));
					bound.openConnection(requested);
				} catch (Exception e) {
					Log.e(TAG, "Problem while trying to create new requested bridge from URI", e);
					// TODO: We should display an error dialog here.
					return;
				}

				adapter.notifyDataSetChanged();
				requestedIndex = adapter.getCount();
			} else {
				final int flipIndex = bound.getBridges().indexOf(requestedBridge);
				if (flipIndex > requestedIndex) {
					requestedIndex = flipIndex;
				}
			}

			setDisplayedTerminal(requestedIndex);
		}
	}

	@Override
	public void onStop() {
		super.onStop();

		unbindService(connection);
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		// Maintain selected host if connected.
		TerminalView currentTerminalView = adapter.getCurrentTerminalView();
		if (currentTerminalView != null
				&& !currentTerminalView.bridge.isDisconnected()) {
			requested = currentTerminalView.bridge.host.getUri();
			savedInstanceState.putString(STATE_SELECTED_URI, requested.toString());
		}

		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Save the currently shown {@link TerminalView} as the default. This is
	 * saved back down into {@link TerminalManager} where we can read it again
	 * later.
	 */
	private void updateDefault() {
		// update the current default terminal
		TerminalView view = adapter.getCurrentTerminalView();
		if (view == null || bound == null) {
			return;
		}
		bound.defaultBridge = view.bridge;
	}

	protected void updateEmptyVisible() {
		// update visibility of empty status message
		empty.setVisibility((pager.getChildCount() == 0) ? View.VISIBLE : View.GONE);
	}

	/**
	 * Show any prompts requested by the currently visible {@link TerminalView}.
	 */
	protected void updatePromptVisible() {
		// check if our currently-visible terminalbridge is requesting any prompt services
		TerminalView view = adapter.getCurrentTerminalView();

		// Hide all the prompts in case a prompt request was canceled
		hideAllPrompts();

		if (view == null) {
			// we dont have an active view, so hide any prompts
			return;
		}

		PromptHelper prompt = view.bridge.promptHelper;
		if (String.class.equals(prompt.promptRequested)) {
			hideEmulatedKeys();
			stringPromptGroup.setVisibility(View.VISIBLE);

			String instructions = prompt.promptInstructions;
			if (instructions != null && instructions.length() > 0) {
				stringPromptInstructions.setVisibility(View.VISIBLE);
				stringPromptInstructions.setText(instructions);
			} else
				stringPromptInstructions.setVisibility(View.GONE);
			stringPrompt.setText("");
			stringPrompt.setHint(prompt.promptHint);
			stringPrompt.requestFocus();

		} else if (Boolean.class.equals(prompt.promptRequested)) {
			hideEmulatedKeys();
			booleanPromptGroup.setVisibility(View.VISIBLE);
			booleanPrompt.setText(prompt.promptHint);
			booleanYes.requestFocus();

		} else {
			hideAllPrompts();
			view.requestFocus();
		}
	}

	private static class URLItemListener implements OnItemClickListener {
		private final WeakReference<Context> contextRef;

		URLItemListener(Context context) {
			this.contextRef = new WeakReference<>(context);
		}

		@Override
		public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
			Context context = contextRef.get();

			if (context == null)
				return;

			try {
				TextView urlView = (TextView) view;

				String url = urlView.getText().toString();
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				context.startActivity(intent);
			} catch (Exception e) {
				Log.e(TAG, "couldn't open URL", e);
				// We should probably tell the user that we couldn't find a handler...
			}
		}

	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		Log.d(TAG, String.format("onConfigurationChanged; requestedOrientation=%d, newConfig.orientation=%d", getRequestedOrientation(), newConfig.orientation));
		if (bound != null) {
			bound.setResizeAllowed(!forcedOrientation ||
					((newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
							getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) &&
							(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT ||
									getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)));

			bound.hardKeyboardHidden = (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES);

			mKeyboardButton.setVisibility(bound.hardKeyboardHidden ? View.VISIBLE : View.GONE);
		}
	}

	/**
	 * Called whenever the displayed terminal is changed.
	 */
	private void onTerminalChanged() {
		View terminalNameOverlay = findCurrentView(R.id.terminal_name_overlay);
		if (terminalNameOverlay != null)
			terminalNameOverlay.startAnimation(fade_out_delayed);
		updateDefault();
		updatePromptVisible();
		ActivityCompat.invalidateOptionsMenu(ConsoleActivity.this);
	}

	/**
	 * Displays the child in the ViewPager at the requestedIndex and updates the prompts.
	 *
	 * @param requestedIndex the index of the terminal view to display
	 */
	private void setDisplayedTerminal(int requestedIndex) {
		pager.setCurrentItem(requestedIndex);
		// set activity title
		setTitle(adapter.getPageTitle(requestedIndex));
		onTerminalChanged();
	}

	private void pasteIntoTerminal() {
		// force insert of clipboard text into current console
		TerminalView terminalView = adapter.getCurrentTerminalView();
		TerminalBridge bridge = terminalView.bridge;

		// pull string from clipboard and generate all events to force down
		String clip = "";
		if (clipboard.hasText()) {
			clip = clipboard.getText().toString();
		}
		bridge.injectString(clip);
	}

	public class TerminalPagerAdapter extends PagerAdapter {
		@Override
		public int getCount() {
			if (bound != null) {
				return bound.getBridges().size();
			} else {
				return 0;
			}
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			if (bound == null || bound.getBridges().size() <= position) {
				Log.w(TAG, "Activity not bound when creating TerminalView.");
			}
			TerminalBridge bridge = bound.getBridges().get(position);
			bridge.promptHelper.setHandler(promptHandler);

			// inflate each terminal view
			RelativeLayout view = (RelativeLayout) inflater.inflate(
					R.layout.item_terminal, container, false);

			// set the terminal name overlay text
			TextView terminalNameOverlay = view.findViewById(R.id.terminal_name_overlay);
			terminalNameOverlay.setText(bridge.host.getNickname());

			// and add our terminal view control, using index to place behind overlay
			final TerminalView terminal = new TerminalView(container.getContext(), bridge, pager);
			terminal.setId(R.id.terminal_view);
			view.addView(terminal, 0);

			// Tag the view with its bridge so it can be retrieved later.
			view.setTag(bridge);

			container.addView(view);
			terminalNameOverlay.startAnimation(fade_out_delayed);
			return view;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			final View view = (View) object;

			container.removeView(view);
		}

		@Override
		public int getItemPosition(Object object) {
			if (bound == null) {
				return POSITION_NONE;
			}

			View view = (View) object;
			TerminalView terminal = view.findViewById(R.id.terminal_view);
			HostBean host = terminal.bridge.host;

			int itemIndex = POSITION_NONE;
			int i = 0;
			for (TerminalBridge bridge : bound.getBridges()) {
				if (bridge.host.equals(host)) {
					itemIndex = i;
					break;
				}
				i++;
			}
			return itemIndex;
		}

		public TerminalBridge getBridgeAtPosition(int position) {
			if (bound == null) {
				return null;
			}

			ArrayList<TerminalBridge> bridges = bound.getBridges();
			if (position < 0 || position >= bridges.size()) {
				return null;
			}
			return bridges.get(position);
		}

		@Override
		public void notifyDataSetChanged() {
			super.notifyDataSetChanged();
			if (tabs != null) {
				toolbar.setVisibility(this.getCount() > 1 ? View.VISIBLE : View.GONE);
				tabs.setTabsFromPagerAdapter(this);
			}
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			TerminalBridge bridge = getBridgeAtPosition(position);
			if (bridge == null) {
				return "???";
			}
			return bridge.host.getNickname();
		}

		public TerminalView getCurrentTerminalView() {
			View currentView = pager.findViewWithTag(getBridgeAtPosition(pager.getCurrentItem()));
			if (currentView == null) {
				return null;
			}
			return (TerminalView) currentView.findViewById(R.id.terminal_view);
		}
	}
}
