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
import org.connectbot.bean.SelectionArea;
import org.connectbot.service.PromptHelper;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalKeyListener;
import org.connectbot.service.TerminalManager;
import org.connectbot.util.PreferenceConstants;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.InputDevice;
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
import android.view.ViewConfiguration;
import android.view.ViewGroup;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import de.mud.terminal.vt320;

public class ConsoleActivity extends Activity {
	public final static String TAG = "CB.ConsoleActivity";

	protected static final int REQUEST_EDIT = 1;

	private static final int CLICK_TIME = 400;
	private static final float MAX_CLICK_DISTANCE = 25f;
	private static final int KEYBOARD_DISPLAY_TIME = 1500;

	protected ViewPager pager = null;
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
	private Button booleanYes, booleanNo;

	private LinearLayout keyboardGroup;
	private Runnable keyboardGroupHider;

	private TextView empty;

	private Animation fade_out_delayed;

	private Animation keyboard_fade_in, keyboard_fade_out;
	private float lastX, lastY;

	private InputMethodManager inputManager;

	private MenuItem disconnect, copy, paste, portForward, resize, urlscan;

	protected TerminalBridge copySource = null;
	private int lastTouchRow, lastTouchCol;

	private boolean forcedOrientation;

	private Handler handler = new Handler();

	private ImageView mKeyboardButton;

	private ActionBarWrapper actionBar;
	private boolean inActionBarMenu = false;
	private boolean titleBarHide;

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// let manager know about our event handling services
			bound.disconnectHandler = disconnectHandler;
			bound.setResizeAllowed(true);

			final String requestedNickname = (requested != null) ? requested.getFragment() : null;
			int requestedIndex = 0;

			TerminalBridge requestedBridge = bound.getConnectedBridge(requestedNickname);

			// If we didn't find the requested connection, try opening it
			if (requestedNickname != null && requestedBridge == null) {
				try {
					Log.d(TAG, String.format("We couldnt find an existing bridge with URI=%s (nickname=%s), so creating one now", requested.toString(), requestedNickname));
					requestedBridge = bound.openConnection(requested);
				} catch (Exception e) {
					Log.e(TAG, "Problem while trying to create new requested bridge from URI", e);
				}
			}

			// create views for all bridges on this service
			adapter.notifyDataSetChanged();
			requestedIndex = bound.getBridges().indexOf(requestedBridge);

			setDisplayedTerminal(requestedIndex == -1 ? 0 : requestedIndex);
		}

		public void onServiceDisconnected(ComponentName className) {
			adapter.notifyDataSetChanged();
			updateEmptyVisible();
			bound = null;
		}
	};

	protected Handler promptHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// someone below us requested to display a prompt
			updatePromptVisible();
		}
	};

	protected Handler disconnectHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "Someone sending HANDLE_DISCONNECT to parentHandler");

			// someone below us requested to display a password dialog
			// they are sending nickname and requested
			TerminalBridge bridge = (TerminalBridge) msg.obj;

			adapter.notifyDataSetChanged();
			if (bridge.isAwaitingClose()) {
				closeBridge(bridge);
			}
		}
	};

	/**
	 * @param bridge
	 */
	private void closeBridge(final TerminalBridge bridge) {
		synchronized (pager) {
			updateEmptyVisible();
			updatePromptVisible();

			// If we just closed the last bridge, go back to the previous activity.
			if (pager.getChildCount() == 0) {
				finish();
			}
		}
	}

	protected View findCurrentView(int id) {
		TerminalView view = adapter.getCurrentTerminalView();
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

	private void showEmulatedKeys() {
		keyboardGroup.startAnimation(keyboard_fade_in);
		keyboardGroup.setVisibility(View.VISIBLE);
		actionBar.show();

		if (keyboardGroupHider != null)
			handler.removeCallbacks(keyboardGroupHider);
		keyboardGroupHider = new Runnable() {
			public void run() {
				if (keyboardGroup.getVisibility() == View.GONE || inActionBarMenu)
					return;

				keyboardGroup.startAnimation(keyboard_fade_out);
				keyboardGroup.setVisibility(View.GONE);
				if (titleBarHide) {
					actionBar.hide();
				}
				keyboardGroupHider = null;
			}
		};
		handler.postDelayed(keyboardGroupHider, KEYBOARD_DISPLAY_TIME);
	}

	private void hideEmulatedKeys() {
		if (keyboardGroupHider != null)
			handler.removeCallbacks(keyboardGroupHider);
		keyboardGroup.setVisibility(View.GONE);
		if (titleBarHide) {
			actionBar.hide();
		}
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			StrictModeSetup.run();
		}

		hardKeyboard = getResources().getConfiguration().keyboard ==
				Configuration.KEYBOARD_QWERTY;

		clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		titleBarHide = prefs.getBoolean(PreferenceConstants.TITLEBARHIDE, false);
		if (titleBarHide) {
			getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
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
		requested = getIntent().getData();

		inflater = LayoutInflater.from(this);

		pager = (ViewPager) findViewById(R.id.console_flip);
		registerForContextMenu(pager);
		pager.addOnPageChangeListener(
				new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						onTerminalChanged();
					}
				});

		empty = (TextView) findViewById(android.R.id.empty);

		stringPromptGroup = (RelativeLayout) findViewById(R.id.console_password_group);
		stringPromptInstructions = (TextView) findViewById(R.id.console_password_instructions);
		stringPrompt = (EditText) findViewById(R.id.console_password);
		stringPrompt.setOnKeyListener(new OnKeyListener() {
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

		booleanPromptGroup = (RelativeLayout) findViewById(R.id.console_boolean_group);
		booleanPrompt = (TextView) findViewById(R.id.console_prompt);

		booleanYes = (Button) findViewById(R.id.console_prompt_yes);
		booleanYes.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				PromptHelper helper = getCurrentPromptHelper();
				if (helper == null) return;
				helper.setResponse(Boolean.TRUE);
				updatePromptVisible();
			}
		});

		booleanNo = (Button) findViewById(R.id.console_prompt_no);
		booleanNo.setOnClickListener(new OnClickListener() {
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

		inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		keyboardGroup = (LinearLayout) findViewById(R.id.keyboard_group);

		mKeyboardButton = (ImageView) findViewById(R.id.button_keyboard);
		mKeyboardButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				View terminal = adapter.getCurrentTerminalView();
				if (terminal == null)
					return;

				inputManager.showSoftInput(terminal, InputMethodManager.SHOW_FORCED);
				hideEmulatedKeys();
			}
		});

		final Button ctrlButton = (Button) findViewById(R.id.button_ctrl);
		ctrlButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				TerminalView terminal = adapter.getCurrentTerminalView();
				if (terminal == null) return;

				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.metaPress(TerminalKeyListener.OUR_CTRL_ON, true);
				hideEmulatedKeys();
			}
		});

		final Button escButton = (Button) findViewById(R.id.button_esc);
		escButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				TerminalView terminal = adapter.getCurrentTerminalView();
				if (terminal == null) return;

				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.sendEscape();
				hideEmulatedKeys();
			}
		});

		final Button tabButton = (Button) findViewById(R.id.button_tab);
		tabButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				TerminalView terminal = adapter.getCurrentTerminalView();
				if (terminal == null) return;

				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.sendTab();
				hideEmulatedKeys();
			}
		});
		final Button upButton = (Button) findViewById(R.id.button_up);
		upButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				View flip = findCurrentView(R.id.console_flip);
				if (flip == null) return;
				TerminalView terminal = (TerminalView) flip;


				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.sendPressedKey(vt320.KEY_UP);
			}
		});
		final Button dnButton = (Button) findViewById(R.id.button_down);
		dnButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				View flip = findCurrentView(R.id.console_flip);
				if (flip == null) return;
				TerminalView terminal = (TerminalView) flip;

				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.sendPressedKey(vt320.KEY_DOWN);
			}
		});
		final Button leftButton = (Button) findViewById(R.id.button_left);
		leftButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				View flip = findCurrentView(R.id.console_flip);
				if (flip == null) return;
				TerminalView terminal = (TerminalView) flip;

				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.sendPressedKey(vt320.KEY_LEFT);
			}
		});
		final Button rightButton = (Button) findViewById(R.id.button_right);
		rightButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				View flip = findCurrentView(R.id.console_flip);
				if (flip == null) return;
				TerminalView terminal = (TerminalView) flip;

				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.sendPressedKey(vt320.KEY_RIGHT);
			}
		});

		actionBar = ActionBarWrapper.getActionBar(this);
		actionBar.setDisplayHomeAsUpEnabled(true);
		if (titleBarHide) {
			actionBar.hide();
		}
		actionBar.addOnMenuVisibilityListener(new ActionBarWrapper.OnMenuVisibilityListener() {
			public void onMenuVisibilityChanged(boolean isVisible) {
				inActionBarMenu = isVisible;
				if (isVisible == false) {
					hideEmulatedKeys();
				}
			}
		});

		// detect fling gestures to switch between terminals
		final GestureDetector detect = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			private float totalY = 0;

			@Override
			public void onLongPress(MotionEvent e) {
				super.onLongPress(e);
				openContextMenu(pager);
			}


			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

				// if copying, then ignore
				if (copySource != null && copySource.isSelectingForCopy())
					return false;

				if (e1 == null || e2 == null)
					return false;

				// if releasing then reset total scroll
				if (e2.getAction() == MotionEvent.ACTION_UP) {
					totalY = 0;
				}

				// activate consider if within x tolerance
				int touchSlop = ViewConfiguration.get(ConsoleActivity.this).getScaledTouchSlop();
				if (Math.abs(e1.getX() - e2.getX()) < touchSlop * 4) {

					View view = adapter.getCurrentTerminalView();
					if (view == null) return false;
					TerminalView terminal = (TerminalView) view;

					// estimate how many rows we have scrolled through
					// accumulate distance that doesn't trigger immediate scroll
					totalY += distanceY;
					final int moved = (int) (totalY / terminal.bridge.charHeight);

					// consume as scrollback only if towards right half of screen
					if (e2.getX() > view.getWidth() / 2) {
						if (moved != 0) {
							int base = terminal.bridge.buffer.getWindowBase();
							terminal.bridge.buffer.setWindowBase(base + moved);
							totalY = 0;
							return true;
						}
					} else {
						// otherwise consume as pgup/pgdown for every 5 lines
						if (moved > 5) {
							((vt320) terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ', 0);
							terminal.bridge.tryKeyVibrate();
							totalY = 0;
							return true;
						} else if (moved < -5) {
							((vt320) terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_UP, ' ', 0);
							terminal.bridge.tryKeyVibrate();
							totalY = 0;
							return true;
						}

					}

				}

				return false;
			}


		});

		pager.setLongClickable(true);
		pager.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View v, MotionEvent event) {

				// Handle mouse-specific actions.
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
						MotionEventCompat.getSource(event) == InputDevice.SOURCE_MOUSE &&
						event.getAction() == MotionEvent.ACTION_DOWN) {
					switch (event.getButtonState()) {
					case MotionEvent.BUTTON_PRIMARY:
						// Automatically start copy mode if using a mouse.
						startCopyMode();
						break;
					case MotionEvent.BUTTON_SECONDARY:
						openContextMenu(pager);
						return true;
					case MotionEvent.BUTTON_TERTIARY:
						// Middle click pastes.
						pasteIntoTerminal();
						return true;
					}
				}

				// when copying, highlight the area
				if (copySource != null && copySource.isSelectingForCopy()) {
					int row = (int) Math.floor(event.getY() / copySource.charHeight);
					int col = (int) Math.floor(event.getX() / copySource.charWidth);

					SelectionArea area = copySource.getSelectionArea();

					switch (event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						// recording starting area
						if (area.isSelectingOrigin()) {
							area.setRow(row);
							area.setColumn(col);
							lastTouchRow = row;
							lastTouchCol = col;
							copySource.redraw();
						}
						return true;
					case MotionEvent.ACTION_MOVE:
						/* ignore when user hasn't moved since last time so
						 * we can fine-tune with directional pad
						 */
						if (row == lastTouchRow && col == lastTouchCol)
							return true;

						// if the user moves, start the selection for other corner
						area.finishSelectingOrigin();

						// update selected area
						area.setRow(row);
						area.setColumn(col);
						lastTouchRow = row;
						lastTouchCol = col;
						copySource.redraw();
						return true;
					case MotionEvent.ACTION_UP:
						/* If they didn't move their finger, maybe they meant to
						 * select the rest of the text with the directional pad.
						 */
						if (area.getLeft() == area.getRight() &&
								area.getTop() == area.getBottom()) {
							return true;
						}

						// copy selected area to clipboard
						String copiedText = area.copyFrom(copySource.buffer);

						clipboard.setText(copiedText);
						Toast.makeText(ConsoleActivity.this, getString(R.string.console_copy_done, copiedText.length()), Toast.LENGTH_LONG).show();
						// fall through to clear state

					case MotionEvent.ACTION_CANCEL:
						// make sure we clear any highlighted area
						area.reset();
						copySource.setSelectingForCopy(false);
						copySource.redraw();
						return true;
					}
				}

				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					lastX = event.getX();
					lastY = event.getY();
				} else if (event.getAction() == MotionEvent.ACTION_UP
						&& keyboardGroup.getVisibility() == View.GONE
						&& event.getEventTime() - event.getDownTime() < CLICK_TIME
						&& Math.abs(event.getX() - lastX) < MAX_CLICK_DISTANCE
						&& Math.abs(event.getY() - lastY) < MAX_CLICK_DISTANCE) {
					showEmulatedKeys();
				}

				// pass any touch events back to detector
				return detect.onTouchEvent(event);
			}

		});

		adapter = new TerminalPagerAdapter();
		pager.setAdapter(adapter);
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
			public boolean onMenuItemClick(MenuItem item) {
				// disconnect or close the currently visible session
				TerminalView terminalView = adapter.getCurrentTerminalView();
				TerminalBridge bridge = terminalView.bridge;

				bridge.dispatchDisconnect(true);
				return true;
			}
		});

		copy = menu.add(R.string.console_menu_copy);
		if (hardKeyboard)
			copy.setAlphabeticShortcut('c');
		copy.setIcon(android.R.drawable.ic_menu_set_as);
		copy.setEnabled(activeTerminal);
		copy.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				startCopyMode();
				Toast.makeText(ConsoleActivity.this, getString(R.string.console_copy_start), Toast.LENGTH_LONG).show();
				return true;
			}
		});

		paste = menu.add(R.string.console_menu_paste);
		if (hardKeyboard)
			paste.setAlphabeticShortcut('v');
		paste.setIcon(android.R.drawable.ic_menu_edit);
		paste.setEnabled(clipboard.hasText() && sessionOpen);
		paste.setOnMenuItemClickListener(new OnMenuItemClickListener() {
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
			public boolean onMenuItemClick(MenuItem item) {
				final TerminalView terminalView = adapter.getCurrentTerminalView();

				List<String> urls = terminalView.bridge.scanForURLs();

				Dialog urlDialog = new Dialog(ConsoleActivity.this);
				urlDialog.setTitle(R.string.console_menu_urlscan);

				ListView urlListView = new ListView(ConsoleActivity.this);
				URLItemListener urlListener = new URLItemListener(ConsoleActivity.this);
				urlListView.setOnItemClickListener(urlListener);

				urlListView.setAdapter(new ArrayAdapter<String>(ConsoleActivity.this, android.R.layout.simple_list_item_1, urls));
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
			public boolean onMenuItemClick(MenuItem item) {
				final TerminalView terminalView = adapter.getCurrentTerminalView();

				final View resizeView = inflater.inflate(R.layout.dia_resize, null, false);
				new AlertDialog.Builder(ConsoleActivity.this)
					.setView(resizeView)
					.setPositiveButton(R.string.button_resize, new DialogInterface.OnClickListener() {
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
		copy.setEnabled(activeTerminal);
		paste.setEnabled(clipboard.hasText() && sessionOpen);
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
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		final TerminalView view = adapter.getCurrentTerminalView();
		boolean activeTerminal = view != null;
		boolean sessionOpen = false;

		if (activeTerminal) {
			TerminalBridge bridge = view.bridge;
			sessionOpen = bridge.isSessionOpen();
		}

		MenuItem paste = menu.add(R.string.console_menu_paste);
		if (hardKeyboard)
			paste.setAlphabeticShortcut('v');
		paste.setIcon(android.R.drawable.ic_menu_edit);
		paste.setEnabled(clipboard.hasText() && sessionOpen);
		paste.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				pasteIntoTerminal();
				return true;
			}
		});


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

		if (forcedOrientation && bound != null)
			bound.setResizeAllowed(false);
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

		if (forcedOrientation && bound != null)
			bound.setResizeAllowed(true);
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
					requestedBridge = bound.openConnection(requested);
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

	private void startCopyMode() {
		// mark as copying and reset any previous bounds
		TerminalView terminalView = (TerminalView) adapter.getCurrentTerminalView();
		copySource = terminalView.bridge;

		SelectionArea area = copySource.getSelectionArea();
		area.reset();
		area.setBounds(copySource.buffer.getColumns(), copySource.buffer.getRows());

		copySource.setSelectingForCopy(true);

		// Make sure we show the initial selection
		copySource.redraw();
	}

	/**
	 * Save the currently shown {@link TerminalView} as the default. This is
	 * saved back down into {@link TerminalManager} where we can read it again
	 * later.
	 */
	private void updateDefault() {
		// update the current default terminal
		TerminalView view = adapter.getCurrentTerminalView();
		if (view == null) return;

		if (bound == null) return;
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
			booleanPromptGroup.setVisibility(View.VISIBLE);
			booleanPrompt.setText(prompt.promptHint);
			booleanYes.requestFocus();

		} else {
			hideAllPrompts();
			view.requestFocus();
		}
	}

	private class URLItemListener implements OnItemClickListener {
		private WeakReference<Context> contextRef;

		URLItemListener(Context context) {
			this.contextRef = new WeakReference<Context>(context);
		}

		public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
			Context context = contextRef.get();

			if (context == null)
				return;

			try {
				TextView urlView = (TextView) view;

				String url = urlView.getText().toString();
				if (url.indexOf("://") < 0)
					url = "http://" + url;

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
			if (forcedOrientation &&
					(newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE &&
							getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) ||
					(newConfig.orientation != Configuration.ORIENTATION_PORTRAIT &&
							getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT))
				bound.setResizeAllowed(false);
			else
				bound.setResizeAllowed(true);

			bound.hardKeyboardHidden = (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES);

			mKeyboardButton.setVisibility(bound.hardKeyboardHidden ? View.VISIBLE : View.GONE);
		}
	}

	/**
	 * Called whenever the displayed terminal is changed.
	 */
	private void onTerminalChanged() {
		View overlay = findCurrentView(R.id.terminal_overlay);
		if (overlay != null)
			overlay.startAnimation(fade_out_delayed);
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
		onTerminalChanged();
	}

	private void pasteIntoTerminal() {
		// force insert of clipboard text into current console
		TerminalView terminalView = adapter.getCurrentTerminalView();
		TerminalBridge bridge = terminalView.bridge;

		// pull string from clipboard and generate all events to force down
		String clip = clipboard.getText().toString();
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

			// set the terminal overlay text
			TextView overlay = (TextView) view.findViewById(R.id.terminal_overlay);
			overlay.setText(bridge.host.getNickname());

			// and add our terminal view control, using index to place behind overlay
			final TerminalView terminal = new TerminalView(container.getContext(), bridge);
			terminal.setId(R.id.console_flip);
			view.addView(terminal, 0);

			// Tag the view with its bridge so it can be retrieved later.
			view.setTag(bridge);

			container.addView(view);
			overlay.startAnimation(fade_out_delayed);
			return view;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			final View view = (View) object;

			container.removeView(view);
		}

		@Override
		public int getItemPosition(Object object) {
			final View view = (View) object;
			TerminalView terminal = (TerminalView) view.findViewById(R.id.console_flip);
			final HostBean host = terminal.bridge.host;
			int itemIndex = -1;
			int i = 0;
			for (TerminalBridge bridge : bound.getBridges()) {
				if (bridge.host.equals(host)) {
					itemIndex = i;
					break;
				}
				i++;
			}
			if (itemIndex == -1) {
				return POSITION_NONE;
			} else {
				return itemIndex;
			}
		}

		public TerminalBridge getItemAtPosition(int position) {
			ArrayList<TerminalBridge> bridges = bound.getBridges();
			if (position < 0 || position >= bridges.size()) {
				return null;
			}
			return bridges.get(position);
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			TerminalBridge bridge = getItemAtPosition(position);
			if (bridge == null) {
				return "???";
			}
			return bridge.host.getNickname();
		}

		public TerminalView getCurrentTerminalView() {
			View currentView = pager.findViewWithTag(adapter.getItemAtPosition(pager.getCurrentItem()));
			if (currentView == null) return null;
			return (TerminalView) currentView.findViewById(R.id.console_flip);
		}
	}
}
