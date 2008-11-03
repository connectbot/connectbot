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
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;
import de.mud.terminal.vt320;

public class ConsoleActivity extends Activity {
	
	public final static String TAG = ConsoleActivity.class.toString();
	
	protected ViewFlipper flip = null;
	protected TerminalManager bound = null;
	protected LayoutInflater inflater = null;
	
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
				
				// let them know about our prompt handler services
				bridge.promptHelper.setHandler(promptHandler);
				bridge.refreshKeymode();
				
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
	
	protected String getCurrentNickname() {
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return null;
		return ((TerminalView)view).bridge.nickname;
	}

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
			// someone below us requested to display a password dialog
			// they are sending nickname and requested
			TerminalBridge bridge = (TerminalBridge)msg.obj;
			
			// remove this bridge becase its been disconnected
			Log.d(TAG, "Someone sending HANDLE_DISCONNECT to parentHandler");
			for(int i = 0; i < flip.getChildCount(); i++) {
				View view = flip.getChildAt(i).findViewById(R.id.console_flip);
				if(!(view instanceof TerminalView)) continue;
				TerminalView terminal = (TerminalView)view;
				if(terminal.bridge.equals(bridge)) {
					// weve found the terminal to remove
					// shift something into its place if currently visible
					if(flip.getDisplayedChild() == i) {
						shiftLeft();
					}
					flip.removeViewAt(i);
					updateEmptyVisible();
					break;
				}
			}
			
		}
	};
	
	protected void hideAllPrompts() {
		this.stringPrompt.setVisibility(View.GONE);
		this.booleanPrompt.setVisibility(View.GONE);
		this.booleanYes.setVisibility(View.GONE);
		this.booleanNo.setVisibility(View.GONE);
		
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
	
	protected SharedPreferences prefs = null;
	protected PowerManager.WakeLock wakelock = null;

	protected String PREF_KEEPALIVE = null;

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
	
	protected View findCurrentView(int id) {
		View view = this.flip.getCurrentView();
		if(view == null) return null;
		return view.findViewById(id);
	}
	
	protected PromptHelper getCurrentPromptHelper() {
		View view = findCurrentView(R.id.console_flip);
		if(!(view instanceof TerminalView)) return null;
		return ((TerminalView)view).bridge.promptHelper;
	}
	
	protected Uri requested;
	protected ClipboardManager clipboard;
	
	protected EditText stringPrompt;
	protected TextView booleanPrompt;
	protected Button booleanYes, booleanNo;
	
	protected TextView empty;
	
	protected Animation slide_left_in, slide_left_out, slide_right_in, slide_right_out, fade_stay_hidden, fade_out; 

	@Override
    public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.setContentView(R.layout.act_console);
		
		this.clipboard = (ClipboardManager)this.getSystemService(CLIPBOARD_SERVICE);
		this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
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


		});
				
		flip.setLongClickable(true);
		flip.setOnTouchListener(new OnTouchListener() {

			public boolean onTouch(View v, MotionEvent event) {
				
				// when copying, highlight the area
				if(copying) {
					if(copySource == null) return false;
					int row = (int)(event.getY() / copySource.bridge.charHeight);
					int col = (int)(event.getX() / copySource.bridge.charWidth);
					
					switch(event.getAction()) {
					case MotionEvent.ACTION_DOWN:
						// recording starting area
						copySource.top = row;
						copySource.left = col;
						return false;
					case MotionEvent.ACTION_MOVE:
						// update selected area
						copySource.bottom = row;
						copySource.right = col;
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
							for(int x = left; x < right; x++) {
								// only copy printable chars
								char c = copySource.bridge.buffer.getChar(x, y);
								if(c < 32 || c >= 127) c = ' ';
								buffer.append(c);
							}
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

	protected void updateEmptyVisible() {
		// update visibility of empty status message
		this.empty.setVisibility((flip.getChildCount() == 0) ? View.VISIBLE : View.GONE);
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

		updatePromptVisible();

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

		updatePromptVisible();

	}
	
	protected MenuItem disconnect, copy, paste, tunnel, resize;
	
	protected boolean copying = false;
	protected TerminalView copySource = null;
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		
		final View view = findCurrentView(R.id.console_flip);
		boolean activeTerminal = (view instanceof TerminalView);
		boolean authenticated = false;
		if(activeTerminal)
			authenticated = ((TerminalView)view).bridge.fullyConnected;
		
		disconnect = menu.add(R.string.console_menu_disconnect);
		disconnect.setEnabled(activeTerminal);
		disconnect.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		disconnect.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// close the currently visible session
				TerminalView terminal = (TerminalView)view;
				terminal.bridge.dispatchDisconnect();
				// movement should now be happening over in onDisconnect() handler
				//flip.removeView(flip.getCurrentView());
				//shiftLeft();
				return true;
			}
		});
		
		copy = menu.add(R.string.console_menu_copy);
		copy.setIcon(android.R.drawable.ic_menu_set_as);
		copy.setEnabled(activeTerminal && authenticated);
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
		
		
		tunnel = menu.add(R.string.console_menu_tunnel);
		tunnel.setIcon(android.R.drawable.ic_menu_manage);
		tunnel.setEnabled(activeTerminal && authenticated);
		tunnel.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				// show dialog to create tunnel for this host
				final TerminalView terminal = (TerminalView)view;
				
				// build dialog to prompt user about updating
				final View tunnelView = inflater.inflate(R.layout.dia_tunnel, null, false);
				((RadioButton)tunnelView.findViewById(R.id.tunnel_local)).setChecked(true);
				new AlertDialog.Builder(ConsoleActivity.this)
					.setView(tunnelView)
					.setPositiveButton("Create tunnel", new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							String type = ((RadioButton)tunnelView.findViewById(R.id.tunnel_local)).isChecked() ? TUNNEL_LOCAL : TUNNEL_REMOTE;
							String source = ((TextView)tunnelView.findViewById(R.id.tunnel_source)).getText().toString();
							String dest = ((TextView)tunnelView.findViewById(R.id.tunnel_destination)).getText().toString();
							
							createTunnel(terminal, type, source, dest);
						}
					})
					.setNegativeButton("Cancel", null).create().show();
				
				return true;
			}
		});
		
		resize = menu.add(R.string.console_menu_resize);
		resize.setIcon(android.R.drawable.ic_menu_crop);
		resize.setEnabled(activeTerminal && authenticated);
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
					}).setNegativeButton(R.string.button_cancel, null).create().show();
				
				return true;
			}
		});

		return true;
		
	}
	
	public final static String EXTRA_TYPE = "type", EXTRA_SOURCE = "source", EXTRA_DEST = "dest", EXTRA_SILENT = "silent";
	public final static String TUNNEL_LOCAL = "local", TUNNEL_REMOTE = "remote";
	
	protected void createTunnel(TerminalView target, String type, String source, String dest) {
		String summary = null;
		try {
			boolean local = TUNNEL_LOCAL.equals(type);
			int sourcePort = Integer.parseInt(source);
			String[] destSplit = dest.split(":");
			String destHost = destSplit[0];
			int destPort = Integer.parseInt(destSplit[1]);
			
			if(local) {
				target.bridge.connection.createLocalPortForwarder(sourcePort, destHost, destPort);
				summary = getString(R.string.tunnel_done_local, sourcePort, destHost, destPort);
			} else {
				target.bridge.connection.requestRemotePortForwarding("", sourcePort, destHost, destPort);
				summary = getString(R.string.tunnel_done_remote, sourcePort, destHost, destPort);
			}
		} catch(Exception e) {
			Log.e(TAG, "Problem trying to create tunnel", e);
			summary = getString(R.string.tunnel_problem);
		}
		
		Toast.makeText(ConsoleActivity.this, summary, Toast.LENGTH_LONG).show();
		
	}
	

    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		final View view = findCurrentView(R.id.console_flip);
		boolean activeTerminal = (view instanceof TerminalView);
		boolean authenticated = false;
		if(activeTerminal)
			authenticated = ((TerminalView)view).bridge.fullyConnected;

		disconnect.setEnabled(activeTerminal);
		copy.setEnabled(activeTerminal && authenticated);
		paste.setEnabled(clipboard.hasText() && activeTerminal && authenticated);
		tunnel.setEnabled(activeTerminal && authenticated);
		resize.setEnabled(activeTerminal && authenticated);

		return true;
	}

}
