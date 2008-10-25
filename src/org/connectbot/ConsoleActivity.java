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

import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;

import de.mud.terminal.vt320;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

public class ConsoleActivity extends Activity {
	
	public final static String TAG = ConsoleActivity.class.toString();
	
	protected ViewFlipper flip = null;
	protected TerminalManager bound = null;
	protected LayoutInflater inflater = null;
	
    private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();
			
			Log.d(TAG, String.format("Connected to TerminalManager and found bridges.size=%d", bound.bridges.size()));
			
			// clear out any existing bridges and record requested index
			flip.removeAllViews();
			String requestedNickname = (requested != null) ? requested.getFragment() : null;
			int requestedIndex = 0;
			
			// first check if we need to create a new session for requested
			boolean found = false;
			for(TerminalBridge bridge : bound.bridges) {
				if(bridge.nickname.equals(requestedNickname))
					found = true;
			}

			// if we didnt find the requested connection, try opening it
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
				
				// let them know about our password services
				bridge.passwordHandler = passwordHandler;
				
				// inflate each terminal view 
				RelativeLayout view = (RelativeLayout)inflater.inflate(R.layout.item_terminal, flip, false);

				// set the terminal overlay text
				TextView overlay = (TextView)view.findViewById(R.id.terminal_overlay);
				overlay.setText(bridge.nickname);

				// and add our terminal view control, using index to place behind overlay
				TerminalView terminal = new TerminalView(ConsoleActivity.this, bridge);
				terminal.setId(R.id.console_flip);
				view.addView(terminal, 0);
				
				// finally attach to the flipper
				flip.addView(view);
				
				// check to see if this bridge was requested
				if(bridge.nickname.equals(requestedNickname))
					requestedIndex = flip.getChildCount() - 1;
				
			}
			
			// show the requested bridge if found, also fade out overlay
			flip.setDisplayedChild(requestedIndex);
			flip.getCurrentView().findViewById(R.id.terminal_overlay).startAnimation(fade_out);
			updatePasswordVisible();
			
		}

		public void onServiceDisconnected(ComponentName className) {
			// remove all bridge views
			bound = null;
			flip.removeAllViews();
		}
	};
	
	protected String getCurrentNickname() {
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return null;
		return ((TerminalView)view).bridge.nickname;
	}
	

	public Handler passwordHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// someone below us requested to display a password dialog
			// they are sending nickname and requested
			String nickname = (String)msg.obj;
			
			// if they are currently active, then obey request
			if(nickname.equals(getCurrentNickname())) {
				updatePasswordVisible();
			}

		}
	};
	
	protected void updatePasswordVisible() {
		// check if our currently-visible terminalbridge is requesting password services
		View view = findCurrentView(R.id.console_flip);
		boolean requested = false;
		if(view instanceof TerminalView)
			requested = ((TerminalView)view).bridge.passwordRequested;
		
		// handle showing/hiding password field and transferring focus
		if(requested) {
			this.password.setVisibility(View.VISIBLE);
			this.password.setText("");
			this.password.setHint(((TerminalView)view).bridge.passwordHint);
			this.password.requestFocus();
		} else {
			this.password.setVisibility(View.GONE);
			view.requestFocus();
		}
		
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
	
	@Override
    public void onStart() {
		super.onStart();

		// connect with manager service to find all bridges
		// when connected it will insert all views
        this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

	}

	@Override
	public void onStop() {
		super.onStop();
		this.unbindService(connection);
		
	}
	
	protected View findCurrentView(int id) {
		View view = this.flip.getCurrentView();
		if(view == null) return null;
		return view.findViewById(id);
	}
	
	protected Uri requested;
	protected ClipboardManager clipboard;
	
	protected EditText password;
	
	protected Animation slide_left_in, slide_left_out, slide_right_in, slide_right_out, fade_stay_hidden, fade_out; 

	@Override
    public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(R.layout.act_console);
		
		this.clipboard = (ClipboardManager)this.getSystemService(CLIPBOARD_SERVICE);
		
		// handle requested console from incoming intent
		this.requested = this.getIntent().getData();

		this.inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		this.flip = (ViewFlipper)this.findViewById(R.id.console_flip);
		
		this.password = (EditText)this.findViewById(R.id.console_password);
		this.password.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if(keyCode != KeyEvent.KEYCODE_ENTER) return false;
				
				// pass collected password down to current terminal
				String value = password.getText().toString();
				
				View view = findCurrentView(R.id.console_flip);
				if(!(view instanceof TerminalView)) return true;
				((TerminalView)view).bridge.incomingPassword(value);

				// finally clear password for next user
				password.setText("");

				return true;
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
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {

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
			 
		
			@Override
			public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
				
				float distx = e2.getRawX() - e1.getRawX();
				float disty = e2.getRawY() - e1.getRawY();
				int goalwidth = flip.getWidth() / 2;
				int goalheight = flip.getHeight() / 2;

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


		});
				
		flip.setLongClickable(true);
		flip.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View v, MotionEvent event) {
				// pass any touch events back to detector
				return detect.onTouchEvent(event);
			}
	
		});
		
	}
	
	protected void shiftLeft() {
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

		updatePasswordVisible();

	}
	
	protected void shiftRight() {

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

		updatePasswordVisible();

	}
	
	protected MenuItem copy, paste;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem add = menu.add("Disconnect");
		add.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// close the currently visible session
				View view = findCurrentView(R.id.console_flip);
				if(view == null) return false;
				TerminalView terminal = (TerminalView)view;
				bound.disconnect(terminal.bridge);
				flip.removeView(flip.getCurrentView());
				shiftLeft();
				return true;
			}
		});
		
		copy = menu.add("Copy");
		copy.setIcon(android.R.drawable.ic_menu_set_as);
		copy.setEnabled(false);
		// TODO: freeze current console, allow selection, and set clipboard to contents

		
		paste = menu.add("Paste");
		paste.setIcon(android.R.drawable.ic_menu_edit);
		paste.setEnabled(clipboard.hasText());
		paste.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// force insert of clipboard text into current console
				View view = findCurrentView(R.id.console_flip);
				if(view == null) return false;
				TerminalView terminal = (TerminalView)view;
				
				// pull string from clipboard and generate all events to force down
				String clip = clipboard.getText().toString();
				KeyEvent[] events = terminal.bridge.keymap.getEvents(clip.toCharArray());
				for(KeyEvent event : events) {
					terminal.bridge.onKey(terminal, event.getKeyCode(), event);
				}

				return true;
			}
		});

		return true;
		
	}

    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		paste.setEnabled(clipboard.hasText());
		
		return true;
	}

}
