package org.connectbot;

import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalBridgeSurface;
import org.connectbot.service.TerminalManager;
import org.theb.ssh.InteractiveHostKeyVerifier;

import com.trilead.ssh2.Connection;

import de.mud.terminal.vt320;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;
import android.widget.LinearLayout.LayoutParams;

public class Console extends Activity {
	
	public ViewFlipper flip = null;
	public TerminalManager bound = null;
	public LayoutInflater inflater = null;
	
    private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			bound = ((TerminalManager.TerminalBinder) service).getService();
			
			Log.d(this.getClass().toString(), "ohhai there, i found bridges=" + bound.bridges.size());
			
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
			
			if(!found) {
				try {
					Log.d(this.getClass().toString(), "onServiceConnected() is openConnection() because of unknown requested uri="+requested.toString());
					bound.openConnection(requested);
				} catch(Exception e) {
					e.printStackTrace();
				}
			}

			
			// create views for all bridges on this service
			for(TerminalBridge bridge : bound.bridges) {
				
				// inflate each terminal view 
				RelativeLayout view = (RelativeLayout)inflater.inflate(R.layout.item_terminal, flip, false);

				// set the terminal overlay text
				TextView overlay = (TextView)view.findViewById(R.id.terminal_overlay);
				overlay.setText(bridge.nickname);

				// and add our terminal view control, using index to place behind overlay
				TerminalView terminal = new TerminalView(Console.this, bridge);
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
			
		}

		public void onServiceDisconnected(ComponentName className) {
			// remove all bridge views
			bound = null;
			flip.removeAllViews();
		}
	};
	
	public Animation fade_out = null; 
	//public String requestedBridge = null;
	
	public void updateDefault() {
		// update the current default terminal
		TerminalView terminal = (TerminalView)flip.getCurrentView().findViewById(R.id.console_flip);
		if(bound == null || terminal == null) return;
		bound.defaultBridge = terminal.bridge;
	}
	
	public Uri requested;
	
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
	
	public ClipboardManager clipboard;

	@Override
    public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(R.layout.act_console);
		
		this.clipboard = (ClipboardManager)this.getSystemService(CLIPBOARD_SERVICE);
		
		// pull out any requested bridge
		//this.requestedBridge = this.getIntent().getExtras().getString(Intent.EXTRA_TEXT);
		
		// handle requested console from incoming intent
		this.requested = this.getIntent().getData();

		this.inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.flip = (ViewFlipper)this.findViewById(R.id.console_flip);
		
		this.fade_out = AnimationUtils.loadAnimation(this, R.anim.fade_out);
		

		// preload animations for terminal switching
		final Animation slide_left_in = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
		final Animation slide_left_out = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
		final Animation slide_right_in = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
		final Animation slide_right_out = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);
		final Animation fade_stay_hidden = AnimationUtils.loadAnimation(this, R.anim.fade_stay_hidden);
		
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
				if(Math.abs(e1.getX() - e2.getX()) < 100) {
					
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
							Log.d(this.getClass().toString(), "going pagedown");
							((vt320)terminal.bridge.buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ', 0);
							totalY = 0;
							return true;
						} else if(moved < -5) {
							Log.d(this.getClass().toString(), "going pageup");
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

						// keep current overlay from popping up again
						View overlay = findCurrentView(R.id.terminal_overlay);
						if(overlay != null) overlay.startAnimation(fade_stay_hidden);
						
						flip.setInAnimation(slide_right_in);
						flip.setOutAnimation(slide_right_out);
						flip.showPrevious();
						Console.this.updateDefault();
						
						// show overlay on new slide and start fade
						overlay = findCurrentView(R.id.terminal_overlay);
						if(overlay != null) overlay.startAnimation(fade_out);
						return true;
					}
					
					if(distx < -goalwidth) {

						// keep current overlay from popping up again
						View overlay = findCurrentView(R.id.terminal_overlay);
						if(overlay != null) overlay.startAnimation(fade_stay_hidden);
						
						flip.setInAnimation(slide_left_in);
						flip.setOutAnimation(slide_left_out);
						flip.showNext();
						Console.this.updateDefault();

						// show overlay on new slide and start fade
						overlay = findCurrentView(R.id.terminal_overlay);
						if(overlay != null) overlay.startAnimation(fade_out);
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
	
	public MenuItem copy, paste;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		MenuItem add = menu.add(0, 0, Menu.NONE, "Disconnect");
		add.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		add.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// close the currently visible session
				View view = findCurrentView(R.id.console_flip);
				if(view == null) return false;
				TerminalView terminal = (TerminalView)view;
				bound.disconnect(terminal.bridge);
				flip.removeView(flip.getCurrentView());
				return true;
			}
		});
		
		copy = menu.add(0, 0, Menu.NONE, "Copy");
		copy.setIcon(android.R.drawable.ic_menu_set_as);
		copy.setEnabled(false);

		
		paste = menu.add(0, 0, Menu.NONE, "Paste");
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
