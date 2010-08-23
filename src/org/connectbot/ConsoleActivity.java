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
import java.util.List;

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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import android.widget.AdapterView.OnItemClickListener;

import com.nullwire.trace.ExceptionHandler;

import de.mud.terminal.vt320;

public class ConsoleActivity extends Activity {
	public final static String TAG = "ConnectBot.ConsoleActivity";

	protected static final int REQUEST_EDIT = 1;

	private static final int CLICK_TIME = 400;
	private static final float MAX_CLICK_DISTANCE = 25f;
	private static final int KEYBOARD_DISPLAY_TIME = 1500;

	// Direction to shift the ViewFlipper
	private static final int SHIFT_LEFT = 0;
	private static final int SHIFT_RIGHT = 1;

	protected ViewFlipper flip = null;
	protected TerminalManager bound = null;
	protected LayoutInflater inflater = null;

	private SharedPreferences prefs = null;

	private PowerManager.WakeLock wakelock = null;

	protected Uri requested;

	protected ClipboardManager clipboard;
	private RelativeLayout stringPromptGroup;
	protected EditText stringPrompt;
	private TextView stringPromptInstructions;

	private RelativeLayout booleanPromptGroup;
	private TextView booleanPrompt;
	private Button booleanYes, booleanNo;

	private TextView empty;

	private Animation slide_left_in, slide_left_out, slide_right_in, slide_right_out, fade_stay_hidden, fade_out_delayed;

	private Animation keyboard_fade_in, keyboard_fade_out;
	private float lastX, lastY;

	private InputMethodManager inputManager;

	private MenuItem disconnect, copy, paste, portForward, resize, urlscan;

	protected TerminalBridge copySource = null;
	private int lastTouchRow, lastTouchCol;

	private boolean forcedOrientation;

	private Handler handler = new Handler();

	private ImageView mKeyboardButton;

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();

			// let manager know about our event handling services
			bound.disconnectHandler = disconnectHandler;

			Log.d(TAG, String.format("Connected to TerminalManager and found bridges.size=%d", bound.bridges.size()));

			bound.setResizeAllowed(true);

			// clear out any existing bridges and record requested index
			flip.removeAllViews();

			final String requestedNickname = (requested != null) ? requested.getFragment() : null;
			int requestedIndex = 0;

			TerminalBridge requestedBridge = bound.getConnectedBridge(requestedNickname);

			// If we didn't find the requested connection, try opening it
			if (requestedNickname != null && requestedBridge == null) {
				try {
					Log.d(TAG, String.format("We couldnt find an existing bridge with URI=%s (nickname=%s), so creating one now", requested.toString(), requestedNickname));
					requestedBridge = bound.openConnection(requested);
				} catch(Exception e) {
					Log.e(TAG, "Problem while trying to create new requested bridge from URI", e);
				}
			}

			// create views for all bridges on this service
			for (TerminalBridge bridge : bound.bridges) {

				final int currentIndex = addNewTerminalView(bridge);

				// check to see if this bridge was requested
				if (bridge == requestedBridge)
					requestedIndex = currentIndex;
			}

			setDisplayedTerminal(requestedIndex);
		}

		public void onServiceDisconnected(ComponentName className) {
			// tell each bridge to forget about our prompt handler
			synchronized (bound.bridges) {
				for(TerminalBridge bridge : bound.bridges)
					bridge.promptHelper.setHandler(null);
			}

			flip.removeAllViews();
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
			TerminalBridge bridge = (TerminalBridge)msg.obj;

			if (bridge.isAwaitingClose())
				closeBridge(bridge);
		}
	};

	/**
	 * @param bridge
	 */
	private void closeBridge(final TerminalBridge bridge) {
		synchronized (flip) {
			final int flipIndex = getFlipIndex(bridge);

			if (flipIndex >= 0) {
				if (flip.getDisplayedChild() == flipIndex) {
					shiftCurrentTerminal(SHIFT_LEFT);
				}
				flip.removeViewAt(flipIndex);

				/* TODO Remove this workaround when ViewFlipper is fixed to listen
				 * to view removals. Android Issue 1784
				 */
				final int numChildren = flip.getChildCount();
				if (flip.getDisplayedChild() >= numChildren &&
						numChildren > 0) {
					flip.setDisplayedChild(numChildren - 1);
				}

				updateEmptyVisible();
			}

			// If we just closed the last bridge, go back to the previous activity.
			if (flip.getChildCount() == 0) {
				finish();
			}
		}
	}

	protected View findCurrentView(int id) {
		View view = flip.getCurrentView();
		if(view == null) return null;
		return view.findViewById(id);
	}

	protected PromptHelper getCurrentPromptHelper() {
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return null;
		return ((TerminalView)view).bridge.promptHelper;
	}

	protected void hideAllPrompts() {
		stringPromptGroup.setVisibility(View.GONE);
		booleanPromptGroup.setVisibility(View.GONE);
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		this.setContentView(R.layout.act_console);

		ExceptionHandler.register(this);

		clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// hide status bar if requested by user
		if (prefs.getBoolean(PreferenceConstants.FULLSCREEN, false)) {
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		// TODO find proper way to disable volume key beep if it exists.
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		PowerManager manager = (PowerManager)getSystemService(Context.POWER_SERVICE);
		wakelock = manager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);

		// handle requested console from incoming intent
		requested = getIntent().getData();

		inflater = LayoutInflater.from(this);

		flip = (ViewFlipper)findViewById(R.id.console_flip);
		empty = (TextView)findViewById(android.R.id.empty);

		stringPromptGroup = (RelativeLayout) findViewById(R.id.console_password_group);
		stringPromptInstructions = (TextView) findViewById(R.id.console_password_instructions);
		stringPrompt = (EditText)findViewById(R.id.console_password);
		stringPrompt.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if(event.getAction() == KeyEvent.ACTION_UP) return false;
				if(keyCode != KeyEvent.KEYCODE_ENTER) return false;

				// pass collected password down to current terminal
				String value = stringPrompt.getText().toString();

				PromptHelper helper = getCurrentPromptHelper();
				if(helper == null) return false;
				helper.setResponse(value);

				// finally clear password for next user
				stringPrompt.setText("");
				updatePromptVisible();

				return true;
			}
		});

		booleanPromptGroup = (RelativeLayout) findViewById(R.id.console_boolean_group);
		booleanPrompt = (TextView)findViewById(R.id.console_prompt);

		booleanYes = (Button)findViewById(R.id.console_prompt_yes);
		booleanYes.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				PromptHelper helper = getCurrentPromptHelper();
				if(helper == null) return;
				helper.setResponse(Boolean.TRUE);
				updatePromptVisible();
			}
		});

		booleanNo = (Button)findViewById(R.id.console_prompt_no);
		booleanNo.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				PromptHelper helper = getCurrentPromptHelper();
				if(helper == null) return;
				helper.setResponse(Boolean.FALSE);
				updatePromptVisible();
			}
		});

		// preload animations for terminal switching
		slide_left_in = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
		slide_left_out = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
		slide_right_in = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
		slide_right_out = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);

		fade_out_delayed = AnimationUtils.loadAnimation(this, R.anim.fade_out_delayed);
		fade_stay_hidden = AnimationUtils.loadAnimation(this, R.anim.fade_stay_hidden);

		// Preload animation for keyboard button
		keyboard_fade_in = AnimationUtils.loadAnimation(this, R.anim.keyboard_fade_in);
		keyboard_fade_out = AnimationUtils.loadAnimation(this, R.anim.keyboard_fade_out);

		inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		final RelativeLayout keyboardGroup = (RelativeLayout) findViewById(R.id.keyboard_group);

		mKeyboardButton = (ImageView) findViewById(R.id.button_keyboard);
		mKeyboardButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				View flip = findCurrentView(R.id.console_flip);
				if (flip == null)
					return;

				inputManager.showSoftInput(flip, InputMethodManager.SHOW_FORCED);
				keyboardGroup.setVisibility(View.GONE);
			}
		});

		final ImageView ctrlButton = (ImageView) findViewById(R.id.button_ctrl);
		ctrlButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				View flip = findCurrentView(R.id.console_flip);
				if (flip == null) return;
				TerminalView terminal = (TerminalView)flip;

				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.metaPress(TerminalKeyListener.META_CTRL_ON);

				keyboardGroup.setVisibility(View.GONE);
			}
		});

		final ImageView escButton = (ImageView) findViewById(R.id.button_esc);
		escButton.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				View flip = findCurrentView(R.id.console_flip);
				if (flip == null) return;
				TerminalView terminal = (TerminalView)flip;

				TerminalKeyListener handler = terminal.bridge.getKeyHandler();
				handler.sendEscape();

				keyboardGroup.setVisibility(View.GONE);
			}
		});

		// detect fling gestures to switch between terminals
		final GestureDetector detect = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
			private float totalY = 0;

			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

				final float distx = e2.getRawX() - e1.getRawX();
				final float disty = e2.getRawY() - e1.getRawY();
				final int goalwidth = flip.getWidth() / 2;

				// need to slide across half of display to trigger console change
				// make sure user kept a steady hand horizontally
				if (Math.abs(disty) < (flip.getHeight() / 4)) {
					if (distx > goalwidth) {
						shiftCurrentTerminal(SHIFT_RIGHT);
						return true;
					}

					if (distx < -goalwidth) {
						shiftCurrentTerminal(SHIFT_LEFT);
						return true;
					}

				}

				return false;
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
				if (Math.abs(e1.getX() - e2.getX()) < ViewConfiguration.getTouchSlop() * 4) {

					View flip = findCurrentView(R.id.console_flip);
					if(flip == null) return false;
					TerminalView terminal = (TerminalView)flip;

					// estimate how many rows we have scrolled through
					// accumulate distance that doesn't trigger immediate scroll
					totalY += distanceY;
					final int moved = (int)(totalY / terminal.bridge.charHeight);

					// consume as scrollback only if towards right half of screen
					if (e2.getX() > flip.getWidth() / 2) {
						if (moved != 0) {
							int base = terminal.bridge.buffer.getWindowBase();
							terminal.bridge.buffer.setWindowBase(base + moved);
							totalY = 0;
							return true;
						}
					} else {
						// otherwise consume as pgup/pgdown for every 5 lines
						if (moved > 5) {
							((vt320)terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ', 0);
							terminal.bridge.tryKeyVibrate();
							totalY = 0;
							return true;
						} else if (moved < -5) {
							((vt320)terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_UP, ' ', 0);
							terminal.bridge.tryKeyVibrate();
							totalY = 0;
							return true;
						}

					}

				}

				return false;
			}


		});

		flip.setLongClickable(true);
		flip.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View v, MotionEvent event) {

				// when copying, highlight the area
				if (copySource != null && copySource.isSelectingForCopy()) {
					int row = (int)Math.floor(event.getY() / copySource.charHeight);
					int col = (int)Math.floor(event.getX() / copySource.charWidth);

					SelectionArea area = copySource.getSelectionArea();

					switch(event.getAction()) {
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

				Configuration config = getResources().getConfiguration();

				if (event.getAction() == MotionEvent.ACTION_DOWN) {
					lastX = event.getX();
					lastY = event.getY();
				} else if (event.getAction() == MotionEvent.ACTION_UP
						&& keyboardGroup.getVisibility() == View.GONE
						&& event.getEventTime() - event.getDownTime() < CLICK_TIME
						&& Math.abs(event.getX() - lastX) < MAX_CLICK_DISTANCE
						&& Math.abs(event.getY() - lastY) < MAX_CLICK_DISTANCE) {
					keyboardGroup.startAnimation(keyboard_fade_in);
					keyboardGroup.setVisibility(View.VISIBLE);

					handler.postDelayed(new Runnable() {
						public void run() {
							if (keyboardGroup.getVisibility() == View.GONE)
								return;

							keyboardGroup.startAnimation(keyboard_fade_out);
							keyboardGroup.setVisibility(View.GONE);
						}
					}, KEYBOARD_DISPLAY_TIME);
				}

				// pass any touch events back to detector
				return detect.onTouchEvent(event);
			}

		});

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

		View view = findCurrentView(R.id.console_flip);
		final boolean activeTerminal = (view instanceof TerminalView);
		boolean sessionOpen = false;
		boolean disconnected = false;
		boolean canForwardPorts = false;

		if (activeTerminal) {
			TerminalBridge bridge = ((TerminalView) view).bridge;
			sessionOpen = bridge.isSessionOpen();
			disconnected = bridge.isDisconnected();
			canForwardPorts = bridge.canFowardPorts();
		}

		menu.setQwertyMode(true);

		disconnect = menu.add(R.string.list_host_disconnect);
		disconnect.setAlphabeticShortcut('w');
		if (!sessionOpen && disconnected)
			disconnect.setTitle(R.string.console_menu_close);
		disconnect.setEnabled(activeTerminal);
		disconnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		disconnect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// disconnect or close the currently visible session
				TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);
				TerminalBridge bridge = terminalView.bridge;

				bridge.dispatchDisconnect(true);
				return true;
			}
		});

		copy = menu.add(R.string.console_menu_copy);
		copy.setAlphabeticShortcut('c');
		copy.setIcon(android.R.drawable.ic_menu_set_as);
		copy.setEnabled(activeTerminal);
		copy.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// mark as copying and reset any previous bounds
				TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);
				copySource = terminalView.bridge;

				SelectionArea area = copySource.getSelectionArea();
				area.reset();
				area.setBounds(copySource.buffer.getColumns(), copySource.buffer.getRows());

				copySource.setSelectingForCopy(true);

				// Make sure we show the initial selection
				copySource.redraw();

				Toast.makeText(ConsoleActivity.this, getString(R.string.console_copy_start), Toast.LENGTH_LONG).show();
				return true;
			}
		});

		paste = menu.add(R.string.console_menu_paste);
		paste.setAlphabeticShortcut('v');
		paste.setIcon(android.R.drawable.ic_menu_edit);
		paste.setEnabled(clipboard.hasText() && sessionOpen);
		paste.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// force insert of clipboard text into current console
				TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);
				TerminalBridge bridge = terminalView.bridge;

				// pull string from clipboard and generate all events to force down
				String clip = clipboard.getText().toString();
				bridge.injectString(clip);

				return true;
			}
		});

		portForward = menu.add(R.string.console_menu_portforwards);
		portForward.setAlphabeticShortcut('f');
		portForward.setIcon(android.R.drawable.ic_menu_manage);
		portForward.setEnabled(sessionOpen && canForwardPorts);
		portForward.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);
				TerminalBridge bridge = terminalView.bridge;

				Intent intent = new Intent(ConsoleActivity.this, PortForwardListActivity.class);
				intent.putExtra(Intent.EXTRA_TITLE, bridge.host.getId());
				ConsoleActivity.this.startActivityForResult(intent, REQUEST_EDIT);
				return true;
			}
		});

		urlscan = menu.add(R.string.console_menu_urlscan);
		urlscan.setAlphabeticShortcut('u');
		urlscan.setIcon(android.R.drawable.ic_menu_search);
		urlscan.setEnabled(activeTerminal);
		urlscan.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				final TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);

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
		resize.setAlphabeticShortcut('s');
		resize.setIcon(android.R.drawable.ic_menu_crop);
		resize.setEnabled(sessionOpen);
		resize.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				final TerminalView terminalView = (TerminalView) findCurrentView(R.id.console_flip);

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

		final View view = findCurrentView(R.id.console_flip);
		boolean activeTerminal = (view instanceof TerminalView);
		boolean sessionOpen = false;
		boolean disconnected = false;
		boolean canForwardPorts = false;

		if (activeTerminal) {
			TerminalBridge bridge = ((TerminalView) view).bridge;
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

		// Allow the screen to dim and fall asleep.
		if (wakelock != null && wakelock.isHeld())
			wakelock.release();

		if (forcedOrientation && bound != null)
			bound.setResizeAllowed(false);
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume called");

		// Make sure we don't let the screen fall asleep.
		// This also keeps the Wi-Fi chipset from disconnecting us.
		if (wakelock != null && prefs.getBoolean(PreferenceConstants.KEEP_ALIVE, true))
			wakelock.acquire();

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

		synchronized (flip) {
			if (requestedBridge == null) {
				// If we didn't find the requested connection, try opening it

				try {
					Log.d(TAG, String.format("We couldnt find an existing bridge with URI=%s (nickname=%s),"+
							"so creating one now", requested.toString(), requested.getFragment()));
					requestedBridge = bound.openConnection(requested);
				} catch(Exception e) {
					Log.e(TAG, "Problem while trying to create new requested bridge from URI", e);
				}

				requestedIndex = addNewTerminalView(requestedBridge);
			} else {
				final int flipIndex = getFlipIndex(requestedBridge);
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

	protected void shiftCurrentTerminal(final int direction) {
		View overlay;
		synchronized (flip) {
			boolean shouldAnimate = flip.getChildCount() > 1;

			// Only show animation if there is something else to go to.
			if (shouldAnimate) {
				// keep current overlay from popping up again
				overlay = findCurrentView(R.id.terminal_overlay);
				if (overlay != null)
					overlay.startAnimation(fade_stay_hidden);

				if (direction == SHIFT_LEFT) {
					flip.setInAnimation(slide_left_in);
					flip.setOutAnimation(slide_left_out);
					flip.showNext();
				} else if (direction == SHIFT_RIGHT) {
					flip.setInAnimation(slide_right_in);
					flip.setOutAnimation(slide_right_out);
					flip.showPrevious();
				}
			}

			ConsoleActivity.this.updateDefault();

			if (shouldAnimate) {
				// show overlay on new slide and start fade
				overlay = findCurrentView(R.id.terminal_overlay);
				if (overlay != null)
					overlay.startAnimation(fade_out_delayed);
			}

			updatePromptVisible();
		}
	}

	/**
	 * Save the currently shown {@link TerminalView} as the default. This is
	 * saved back down into {@link TerminalManager} where we can read it again
	 * later.
	 */
	private void updateDefault() {
		// update the current default terminal
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return;

		TerminalView terminal = (TerminalView)view;
		if(bound == null) return;
		bound.defaultBridge = terminal.bridge;
	}

	protected void updateEmptyVisible() {
		// update visibility of empty status message
		empty.setVisibility((flip.getChildCount() == 0) ? View.VISIBLE : View.GONE);
	}

	/**
	 * Show any prompts requested by the currently visible {@link TerminalView}.
	 */
	protected void updatePromptVisible() {
		// check if our currently-visible terminalbridge is requesting any prompt services
		View view = findCurrentView(R.id.console_flip);

		// Hide all the prompts in case a prompt request was canceled
		hideAllPrompts();

		if(!(view instanceof TerminalView)) {
			// we dont have an active view, so hide any prompts
			return;
		}

		PromptHelper prompt = ((TerminalView)view).bridge.promptHelper;
		if(String.class.equals(prompt.promptRequested)) {
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

		} else if(Boolean.class.equals(prompt.promptRequested)) {
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
	 * Adds a new TerminalBridge to the current set of views in our ViewFlipper.
	 *
	 * @param bridge TerminalBridge to add to our ViewFlipper
	 * @return the child index of the new view in the ViewFlipper
	 */
	private int addNewTerminalView(TerminalBridge bridge) {
		// let them know about our prompt handler services
		bridge.promptHelper.setHandler(promptHandler);

		// inflate each terminal view
		RelativeLayout view = (RelativeLayout)inflater.inflate(R.layout.item_terminal, flip, false);

		// set the terminal overlay text
		TextView overlay = (TextView)view.findViewById(R.id.terminal_overlay);
		overlay.setText(bridge.host.getNickname());

		// and add our terminal view control, using index to place behind overlay
		TerminalView terminal = new TerminalView(ConsoleActivity.this, bridge);
		terminal.setId(R.id.console_flip);
		view.addView(terminal, 0);

		synchronized (flip) {
			// finally attach to the flipper
			flip.addView(view);
			return flip.getChildCount() - 1;
		}
	}

	private int getFlipIndex(TerminalBridge bridge) {
		synchronized (flip) {
			final int children = flip.getChildCount();
			for (int i = 0; i < children; i++) {
				final View view = flip.getChildAt(i).findViewById(R.id.console_flip);

				if (view == null || !(view instanceof TerminalView)) {
					// How did that happen?
					continue;
				}

				final TerminalView tv = (TerminalView) view;

				if (tv.bridge == bridge) {
					return i;
				}
			}
		}

		return -1;
	}

	/**
	 * Displays the child in the ViewFlipper at the requestedIndex and updates the prompts.
	 *
	 * @param requestedIndex the index of the terminal view to display
	 */
	private void setDisplayedTerminal(int requestedIndex) {
		synchronized (flip) {
			try {
				// show the requested bridge if found, also fade out overlay
				flip.setDisplayedChild(requestedIndex);
				flip.getCurrentView().findViewById(R.id.terminal_overlay)
						.startAnimation(fade_out_delayed);
			} catch (NullPointerException npe) {
				Log.d(TAG, "View went away when we were about to display it", npe);
			}

			updatePromptVisible();
			updateEmptyVisible();
		}
	}
}
