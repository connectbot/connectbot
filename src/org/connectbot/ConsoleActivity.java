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

import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;
import org.connectbot.service.PromptHelper;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
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
import android.view.Window;
import android.view.WindowManager;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import de.mud.terminal.vt320;

public class ConsoleActivity extends Activity {
	public final static String TAG = ConsoleActivity.class.toString();

	protected static final int REQUEST_EDIT = 1;
	
	protected ViewFlipper flip = null;
	protected TerminalManager bound = null;
	protected LayoutInflater inflater = null;
	
    protected SharedPreferences prefs = null;
	
	protected PowerManager.WakeLock wakelock = null;
	
	protected String PREF_KEEPALIVE = null;
	
	protected Uri requested;
	
	protected ClipboardManager clipboard;
	protected EditText stringPrompt;

	protected TextView booleanPrompt;

	protected Button booleanYes, booleanNo;

	protected TextView empty;
	
	protected Animation slide_left_in, slide_left_out, slide_right_in, slide_right_out, fade_stay_hidden, fade_out;
	
	protected MenuItem disconnect, copy, paste, portForward, resize;
	
	protected boolean requestedDisconnect = false;
	
	protected boolean copying = false;
	protected TerminalView copySource = null;
	
	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();
			
			// let manager know about our event handling services
			bound.disconnectHandler = disconnectHandler;
			
			Log.d(TAG, String.format("Connected to TerminalManager and found bridges.size=%d", bound.bridges.size()));
			
			// clear out any existing bridges and record requested index
			flip.removeAllViews();
			String requestedNickname = (requested != null) ? requested.getFragment() : null;
			int requestedIndex = 0;
			
			// first check if we need to create a new session for requested
			boolean found = false;
			for(TerminalBridge bridge : bound.bridges) {
				if(bridge.host.getNickname().equals(requestedNickname))
					found = true;
			}
	
			// If we didn't find the requested connection, try opening it
			if(!found) {
				try {
					Log.d(TAG, String.format("We couldnt find an existing bridge with URI=%s, so creating one now", requested.toString()));
					bound.openConnection(requested);
				} catch(Exception e) {
					Log.e(TAG, "Problem while trying to create new requested bridge from URI", e);
				}
			}
	
			// create views for all bridges on this service
			for(TerminalBridge bridge : bound.bridges) {
				
				// let them know about our prompt handler services
				bridge.promptHelper.setHandler(promptHandler);
				bridge.refreshKeymode();
				
				// inflate each terminal view 
				RelativeLayout view = (RelativeLayout)inflater.inflate(R.layout.item_terminal, flip, false);
	
				// set the terminal overlay text
				TextView overlay = (TextView)view.findViewById(R.id.terminal_overlay);
				overlay.setText(bridge.host.getNickname());
	
				// and add our terminal view control, using index to place behind overlay
				TerminalView terminal = new TerminalView(ConsoleActivity.this, bridge);
				terminal.setId(R.id.console_flip);
				view.addView(terminal, 0);
				
				// finally attach to the flipper
				flip.addView(view);
				
				// check to see if this bridge was requested
				if(bridge.host.getNickname().equals(requestedNickname))
					requestedIndex = flip.getChildCount() - 1;
				
			}
			
			try {
				// show the requested bridge if found, also fade out overlay
				flip.setDisplayedChild(requestedIndex);
				flip.getCurrentView().findViewById(R.id.terminal_overlay).startAnimation(fade_out);
			} catch (NullPointerException npe) {
				Log.d(TAG, "View went away when we were about to display it", npe);
			}
			
			updatePromptVisible();
			updateEmptyVisible();
			
		}
	
		public void onServiceDisconnected(ComponentName className) {
			// tell each bridge to forget about our prompt handler
			for(TerminalBridge bridge : bound.bridges)
				bridge.promptHelper.setHandler(null);
			
			flip.removeAllViews();
			updateEmptyVisible();
			bound = null;
			
		}
	};

	public Handler promptHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// someone below us requested to display a prompt
			updatePromptVisible();
		}
	};

	public Handler disconnectHandler = new Handler() {
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
	private void closeBridge(TerminalBridge bridge) {
		for(int i = 0; i < flip.getChildCount(); i++) {
			View child = flip.getChildAt(i).findViewById(R.id.console_flip);
			
			if (!(child instanceof TerminalView)) continue;
			
			TerminalView terminal = (TerminalView) child;
			
			if (terminal.bridge.equals(bridge)) {
				// we've found the terminal to remove
				// shift something into its place if currently visible
				if(flip.getDisplayedChild() == i)
					shiftLeft();
				flip.removeViewAt(i);
				updateEmptyVisible();
				break;
			}
		}
		
		// If we just closed the last bridge, go back to the previous activity.
		if (flip.getChildCount() == 0) {
			finish();
		}
	}
	
	protected void createPortForward(TerminalView target, String nickname, String type, String source, String dest) {
		String summary = getString(R.string.portforward_problem);
		try {
			long hostId = target.bridge.host.getId();

			PortForwardBean pfb = new PortForwardBean(hostId, nickname, type, source, dest);
					
			target.bridge.addPortForward(pfb);
			if (target.bridge.enablePortForward(pfb)) {
				summary = getString(R.string.portforward_done);
			}
		} catch(Exception e) {
			Log.e(TAG, "Problem trying to create portForward", e);
		}
		
		Toast.makeText(ConsoleActivity.this, summary, Toast.LENGTH_LONG).show();		
	}
	protected View findCurrentView(int id) {
		View view = this.flip.getCurrentView();
		if(view == null) return null;
		return view.findViewById(id);
	}
	
	protected HostBean getCurrentHost() {
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return null;
		return ((TerminalView)view).bridge.host;
	}
	
	protected PromptHelper getCurrentPromptHelper() {
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return null;
		return ((TerminalView)view).bridge.promptHelper;
	} 

	protected void hideAllPrompts() {
		this.stringPrompt.setVisibility(View.GONE);
		this.booleanPrompt.setVisibility(View.GONE);
		this.booleanYes.setVisibility(View.GONE);
		this.booleanNo.setVisibility(View.GONE);
	}

	@Override
    public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(R.layout.act_console);
		
		this.clipboard = (ClipboardManager)this.getSystemService(CLIPBOARD_SERVICE);
		this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		// hide status bar if requested by user
		if (this.prefs.getBoolean(getString(R.string.pref_fullscreen), false)) {
			this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		
		// request a forced orientation if requested by user
		String rotate = this.prefs.getString(getString(R.string.pref_rotation), getString(R.string.list_rotation_land));
		if(getString(R.string.list_rotation_land).equals(rotate)) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		} else if (getString(R.string.list_rotation_port).equals(rotate)) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
		
		
        PowerManager manager = (PowerManager)getSystemService(Context.POWER_SERVICE);
		wakelock = manager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
		
		this.PREF_KEEPALIVE = this.getResources().getString(R.string.pref_keepalive);

		// handle requested console from incoming intent
		this.requested = this.getIntent().getData();

		this.inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		this.flip = (ViewFlipper)this.findViewById(R.id.console_flip);
		this.empty = (TextView)this.findViewById(android.R.id.empty);
		
		this.stringPrompt = (EditText)this.findViewById(R.id.console_password);
		this.stringPrompt.setOnKeyListener(new OnKeyListener() {
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
				hideAllPrompts();

				return true;
			}
		});
		
		this.booleanPrompt = (TextView)this.findViewById(R.id.console_prompt);
		
		this.booleanYes = (Button)this.findViewById(R.id.console_prompt_yes);
		this.booleanYes.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				PromptHelper helper = getCurrentPromptHelper();
				if(helper == null) return;
				helper.setResponse(Boolean.TRUE);
				hideAllPrompts();
			}
		});
		
		this.booleanNo = (Button)this.findViewById(R.id.console_prompt_no);
		this.booleanNo.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				PromptHelper helper = getCurrentPromptHelper();
				if(helper == null) return;
				helper.setResponse(Boolean.FALSE);
				hideAllPrompts();
			}
		});
		
		// preload animations for terminal switching
		this.slide_left_in = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
		this.slide_left_out = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
		this.slide_right_in = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
		this.slide_right_out = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);
		
		this.fade_out = AnimationUtils.loadAnimation(this, R.anim.fade_out);
		this.fade_stay_hidden = AnimationUtils.loadAnimation(this, R.anim.fade_stay_hidden);
		
		// detect fling gestures to switch between terminals
		final GestureDetector detect = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
			
			public float totalY = 0;
			
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				
				float distx = e2.getRawX() - e1.getRawX();
				float disty = e2.getRawY() - e1.getRawY();
				int goalwidth = flip.getWidth() / 2;

				// need to slide across half of display to trigger console change
				// make sure user kept a steady hand horizontally
				if(Math.abs(disty) < 100) {
					if(distx > goalwidth) {
						shiftRight();
						return true;
					}
					
					if(distx < -goalwidth) {
						shiftLeft();
						return true;
					}
					
				}
					
				return false;
			}
			 
		
			@Override
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
				
				// if copying, then ignore
				if(copying) return false;

				// if releasing then reset total scroll
				if(e2.getAction() == MotionEvent.ACTION_UP) {
					totalY = 0;
				}
				
				// activate consider if within x tolerance
				if(Math.abs(e1.getX() - e2.getX()) < ViewConfiguration.getTouchSlop() * 4) {
					
					View flip = findCurrentView(R.id.console_flip);
					if(flip == null) return false;
					TerminalView terminal = (TerminalView)flip;

					// estimate how many rows we have scrolled through
					// accumulate distance that doesn't trigger immediate scroll  
					totalY += distanceY;
					int moved = (int)(totalY / terminal.bridge.charHeight);
					
					// consume as scrollback only if towards right half of screen
					if(e2.getX() > flip.getWidth() / 2) {
						if(moved != 0) {
							int base = terminal.bridge.buffer.getWindowBase();
							terminal.bridge.buffer.setWindowBase(base + moved);
							totalY = 0;
							return true;
						}
					} else {
						// otherwise consume as pgup/pgdown for every 5 lines
						if(moved > 5) {
							((vt320)terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ', 0);
							totalY = 0;
							return true;
						} else if(moved < -5) {
							((vt320)terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_UP, ' ', 0);
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
				if(copying) {
					if(copySource == null) return false;
					float row = event.getY() / copySource.bridge.charHeight;
					float col = event.getX() / copySource.bridge.charWidth;
					
					switch(event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						// recording starting area
						copySource.top = (int) Math.floor(row);
						copySource.left = (int) Math.floor(col);
						return false;
					case MotionEvent.ACTION_MOVE:
						// update selected area
						copySource.bottom = (int) Math.ceil(row);
						copySource.right = (int) Math.ceil(col);
						copySource.invalidate();
						return false;
					case MotionEvent.ACTION_UP:
						// copy selected area to clipboard
						int adjust = 0; //copySource.bridge.buffer.windowBase - copySource.bridge.buffer.screenBase;
						int top = Math.min(copySource.top, copySource.bottom) + adjust,
							bottom = Math.max(copySource.top, copySource.bottom) + adjust,
							left = Math.min(copySource.left, copySource.right),
							right = Math.max(copySource.left, copySource.right);
						
						// perform actual buffer copy
						int size = (right - left) * (bottom - top);
						StringBuffer buffer = new StringBuffer(size);
						for(int y = top; y < bottom; y++) {
							int lastNonSpace = buffer.length();
							
							for(int x = left; x < right; x++) {
								// only copy printable chars
								char c = copySource.bridge.buffer.getChar(x, y);
								if(c < 32 || c >= 127) c = ' ';
								if (c != ' ')
									lastNonSpace = buffer.length();
								buffer.append(c);
							}
							
							// Don't leave a bunch of spaces in our copy buffer.
							if (buffer.length() > lastNonSpace)
								buffer.delete(lastNonSpace + 1, buffer.length());
							
							if (y != bottom)
								buffer.append("\n");
						}
						
						clipboard.setText(buffer.toString());
						Toast.makeText(ConsoleActivity.this, getString(R.string.console_copy_done, buffer.length()), Toast.LENGTH_LONG).show();
						
					case MotionEvent.ACTION_CANCEL:
						// make sure we clear any highlighted area
						copySource.resetSelected();
						copySource.invalidate();
						copying = false;
						return true;
					}
					
					
				}
				
				// pass any touch events back to detector
				return detect.onTouchEvent(event);
			}
	
		});
		
	}
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		final View view = findCurrentView(R.id.console_flip);
		final boolean activeTerminal = (view instanceof TerminalView);
		boolean authenticated = false;
		boolean sessionOpen = false;
		boolean disconnected = false;
		
		if (activeTerminal) {
			authenticated = ((TerminalView) view).bridge.isAuthenticated();
			sessionOpen = ((TerminalView) view).bridge.isSessionOpen();
			disconnected = ((TerminalView) view).bridge.isDisconnected();
		}
		
		
		disconnect = menu.add(R.string.console_menu_disconnect);
		if (!sessionOpen && disconnected)
			disconnect.setTitle(R.string.console_menu_close);
		disconnect.setEnabled(activeTerminal);
		disconnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		disconnect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// disconnect or close the currently visible session
				TerminalBridge bridge = ((TerminalView)view).bridge;
				if (bridge.isSessionOpen() || !bridge.isDisconnected()) {
					requestedDisconnect = true;
					bridge.dispatchDisconnect();
				} else {
					// remove this bridge because it's been explicitly closed.
					closeBridge(bridge);
				}
				return true;
			}
		});
		
		copy = menu.add(R.string.console_menu_copy);
		copy.setIcon(android.R.drawable.ic_menu_set_as);
		copy.setEnabled(activeTerminal);
		copy.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// mark as copying and reset any previous bounds
				copying = true;
				copySource = (TerminalView)view;
				copySource.resetSelected();
				
				Toast.makeText(ConsoleActivity.this, getString(R.string.console_copy_start), Toast.LENGTH_LONG).show();
				return true;
			}
		});

		
		paste = menu.add(R.string.console_menu_paste);
		paste.setIcon(android.R.drawable.ic_menu_edit);
		paste.setEnabled(clipboard.hasText() && activeTerminal && authenticated);
		paste.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// force insert of clipboard text into current console
				TerminalView terminal = (TerminalView)view;
				
				// pull string from clipboard and generate all events to force down
				String clip = clipboard.getText().toString();
				terminal.bridge.injectString(clip);

				return true;
			}
		});
		
		
		portForward = menu.add(R.string.console_menu_portforwards);
		portForward.setIcon(android.R.drawable.ic_menu_manage);
		portForward.setEnabled(authenticated);
		portForward.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				Intent intent = new Intent(ConsoleActivity.this, PortForwardListActivity.class);
				intent.putExtra(Intent.EXTRA_TITLE, ((TerminalView) view).bridge.host.getId());
				ConsoleActivity.this.startActivityForResult(intent, REQUEST_EDIT);
				return true;
			}
		});
		
		resize = menu.add(R.string.console_menu_resize);
		resize.setIcon(android.R.drawable.ic_menu_crop);
		resize.setEnabled(activeTerminal && sessionOpen);
		resize.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				final TerminalView terminal = (TerminalView)view;
				
				final View resizeView = inflater.inflate(R.layout.dia_resize, null, false);
				new AlertDialog.Builder(ConsoleActivity.this)
					.setView(resizeView)
					.setPositiveButton(R.string.button_resize, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							int width = Integer.parseInt(((EditText)resizeView.findViewById(R.id.width)).getText().toString());
							int height = Integer.parseInt(((EditText)resizeView.findViewById(R.id.height)).getText().toString());
							
							terminal.forceSize(width, height);
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
		
		final View view = findCurrentView(R.id.console_flip);
		boolean activeTerminal = (view instanceof TerminalView);
		boolean authenticated = false;
		boolean sessionOpen = false;
		boolean disconnected = false;
		if (activeTerminal) {
			authenticated = ((TerminalView)view).bridge.isAuthenticated();
			sessionOpen = ((TerminalView)view).bridge.isSessionOpen();
			disconnected = ((TerminalView)view).bridge.isDisconnected();
		}
		
		disconnect.setEnabled(activeTerminal);
		if (sessionOpen || !disconnected)
			disconnect.setTitle(R.string.console_menu_disconnect);
		else
			disconnect.setTitle(R.string.console_menu_close);
		copy.setEnabled(activeTerminal);
		paste.setEnabled(clipboard.hasText() && activeTerminal && sessionOpen);
		portForward.setEnabled(activeTerminal && authenticated);
		resize.setEnabled(activeTerminal && sessionOpen);

		return true;
	}
	
	@Override
    public void onStart() {
		super.onStart();

		// connect with manager service to find all bridges
		// when connected it will insert all views
        this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

        // make sure we dont let the screen fall asleep
		// this also keeps the wifi chipset from disconnecting us
		if(this.wakelock != null && prefs.getBoolean(PREF_KEEPALIVE, true))
			wakelock.acquire();

	}
	
	@Override
	public void onStop() {
		super.onStop();
		this.unbindService(connection);

		// allow the screen to dim and fall asleep
		if(this.wakelock != null && this.wakelock.isHeld())
			wakelock.release();
		
	}
	
	protected void shiftLeft() {
		// Only show animation if there is something else to go to.
		if (flip.getChildCount() <= 1)
			return;
		
		// keep current overlay from popping up again
		View overlay = findCurrentView(R.id.terminal_overlay);
		if(overlay != null) overlay.startAnimation(fade_stay_hidden);

		flip.setInAnimation(slide_left_in);	
		flip.setOutAnimation(slide_left_out);
		flip.showNext();
		ConsoleActivity.this.updateDefault();

		// show overlay on new slide and start fade
		overlay = findCurrentView(R.id.terminal_overlay);
		if(overlay != null) overlay.startAnimation(fade_out);

		updatePromptVisible();
	}
	
	protected void shiftRight() {
		// Only show animation if there is something else to go to.
		if (flip.getChildCount() <= 1)
			return;
		
		// keep current overlay from popping up again
		View overlay = findCurrentView(R.id.terminal_overlay);
		if(overlay != null) overlay.startAnimation(fade_stay_hidden);
		
		flip.setInAnimation(slide_right_in);
		flip.setOutAnimation(slide_right_out);
		flip.showPrevious();
		ConsoleActivity.this.updateDefault();
		
		// show overlay on new slide and start fade
		overlay = findCurrentView(R.id.terminal_overlay);
		if(overlay != null) overlay.startAnimation(fade_out);

		updatePromptVisible();
	}

	/**
	 * Save the currently shown {@link TerminalView} as the default. This is
	 * saved back down into {@link TerminalManager} where we can read it again
	 * later.
	 */
	protected void updateDefault() {
		// update the current default terminal
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return;

		TerminalView terminal = (TerminalView)view;
		if(bound == null) return;
		bound.defaultBridge = terminal.bridge;
	}
	
	protected void updateEmptyVisible() {
		// update visibility of empty status message
		this.empty.setVisibility((flip.getChildCount() == 0) ? View.VISIBLE : View.GONE);
	}

    /**
	 * Show any prompts requested by the currently visible {@link TerminalView}.
	 */
	protected void updatePromptVisible() {
		// check if our currently-visible terminalbridge is requesting any prompt services
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) {
			// we dont have an active view, so hide any prompts
			this.hideAllPrompts();
			return;
		}
		
		PromptHelper prompt = ((TerminalView)view).bridge.promptHelper;
		if(String.class.equals(prompt.promptRequested)) {
			this.stringPrompt.setVisibility(View.VISIBLE);
			this.stringPrompt.setText("");
			this.stringPrompt.setHint(prompt.promptHint);
			this.stringPrompt.requestFocus();
			
		} else if(Boolean.class.equals(prompt.promptRequested)) {
			this.booleanPrompt.setVisibility(View.VISIBLE);
			this.booleanPrompt.setText(prompt.promptHint);
			this.booleanYes.setVisibility(View.VISIBLE);
			this.booleanYes.requestFocus();
			this.booleanNo.setVisibility(View.VISIBLE);
		
		} else {
			this.hideAllPrompts();
			view.requestFocus();
			
		}
		
	}

}
