package org.connectbot;

import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalBridgeSurface;
import org.connectbot.service.TerminalManager;
import org.theb.ssh.InteractiveHostKeyVerifier;

import org.theb.ssh.R;
import com.trilead.ssh2.Connection;

import de.mud.terminal.vt320;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
			int requestedIndex = 0;
			
			// create views for all bridges on this service
			for(TerminalBridge bridge : bound.bridges) {
				
				// inflate each terminal view 
				RelativeLayout view = (RelativeLayout)inflater.inflate(R.layout.item_terminal, flip, false);

				// set the terminal overlay text
				TextView overlay = (TextView)view.findViewById(R.id.terminal_overlay);
				overlay.setText(bridge.overlay);

				// and add our terminal view control, using index to place behind overlay
				TerminalView terminal = new TerminalView(Console.this, bridge);
				terminal.setId(R.id.console_flip);
				view.addView(terminal, 0);
				
				// finally attach to the flipper
				flip.addView(view);
				
				// check to see if this bridge was requested
				if(bridge.overlay.equals(requestedBridge))
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
	public String requestedBridge = null;
	
	public void updateDefault() {
		// update the current default terminal
		TerminalView terminal = (TerminalView)flip.getCurrentView().findViewById(R.id.console_flip);
		if(bound == null || terminal == null) return;
		bound.defaultBridge = terminal.bridge;
	}
	
	@Override
    public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(R.layout.act_console);
		
		// pull out any requested bridge
		this.requestedBridge = this.getIntent().getExtras().getString(Intent.EXTRA_TEXT);

		this.inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.flip = (ViewFlipper)this.findViewById(R.id.console_flip);
		
		this.fade_out = AnimationUtils.loadAnimation(this, R.anim.fade_out);

		// connect with manager service to find all bridges
		// when connected it will insert all views
        this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);


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
					
					TerminalView terminal = (TerminalView)flip.getCurrentView().findViewById(R.id.console_flip);

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
						flip.getCurrentView().findViewById(R.id.terminal_overlay).startAnimation(fade_stay_hidden);
						
						flip.setInAnimation(slide_right_in);
						flip.setOutAnimation(slide_right_out);
						flip.showPrevious();
						Console.this.updateDefault();
						
						// show overlay on new slide and start fade
						flip.getCurrentView().findViewById(R.id.terminal_overlay).startAnimation(fade_out);
						return true;
					}
					
					if(distx < -goalwidth) {

						// keep current overlay from popping up again
						flip.getCurrentView().findViewById(R.id.terminal_overlay).startAnimation(fade_stay_hidden);
						
						flip.setInAnimation(slide_left_in);
						flip.setOutAnimation(slide_left_out);
						flip.showNext();
						Console.this.updateDefault();

						// show overlay on new slide and start fade
						flip.getCurrentView().findViewById(R.id.terminal_overlay).startAnimation(fade_out);
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

}
