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

package org.connectbot.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.connectbot.R;
import org.connectbot.TerminalView;
import org.connectbot.bean.PortForwardBean;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.FontMetricsInt;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.LocalPortForwarder;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.crypto.PEMDecoder;

import de.mud.terminal.VDUBuffer;
import de.mud.terminal.VDUDisplay;
import de.mud.terminal.vt320;


/**
 * Provides a bridge between a MUD terminal buffer and a possible TerminalView.
 * This separation allows us to keep the TerminalBridge running in a background
 * service. A TerminalView shares down a bitmap that we can use for rendering
 * when available.
 * 
 * This class also provides SSH hostkey verification prompting, and password
 * prompting.
 */
public class TerminalBridge implements VDUDisplay, OnKeyListener, InteractiveCallback, ConnectionMonitor {
	
	public final static String TAG = TerminalBridge.class.toString();
	
	public final static int TERM_WIDTH_CHARS = 80,
		TERM_HEIGHT_CHARS = 24,
		DEFAULT_FONT_SIZE = 10;
	
	public final static String ENCODING = "ASCII";
	public static final String AUTH_PUBLICKEY = "publickey",
		AUTH_PASSWORD = "password",
		AUTH_KEYBOARDINTERACTIVE = "keyboard-interactive";
	
	private int darken(int color) {
		return Color.argb(0xFF,
			(int)(Color.red(color) * 0.8),
			(int)(Color.green(color) * 0.8),
			(int)(Color.blue(color) * 0.8)
		);
	}
	
	private List<PortForwardBean> portForwards = new LinkedList<PortForwardBean>();
	
	public int color[] = { Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW,
		Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE, };
	
	public int darkerColor[] = new int[color.length];
	
	public final static int COLOR_FG_STD = 7;
	public final static int COLOR_BG_STD = 0;
	
	protected final TerminalManager manager;

	public final String nickname;
	protected final String username;
	public String postlogin = null;
	
	public final Connection connection;
	protected Session session;
	
	protected final Paint defaultPaint;

	protected OutputStream stdin;
	protected InputStream stdout;
	
	protected Thread relay;
	
	protected final String emulation;
	protected final int scrollback;

	public Bitmap bitmap = null;
	public VDUBuffer buffer = null;
	
	protected TerminalView parent = null;
	protected Canvas canvas = new Canvas();

	private boolean ctrlPressed = false;
	private boolean altPressed = false;
	private boolean shiftPressed = false;

	private boolean pubkeysExhausted = false;
	
	private boolean forcedSize = false;
	private int termWidth;
	private int termHeight;
	
	public class HostKeyVerifier implements ServerHostKeyVerifier {
		
		public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception {

			// read in all known hosts from hostdb
			KnownHosts hosts = manager.hostdb.getKnownHosts();
			
			switch(hosts.verifyHostkey(hostname, serverHostKeyAlgorithm, serverHostKey)) {
			case KnownHosts.HOSTKEY_IS_OK:
				return true;

			case KnownHosts.HOSTKEY_IS_NEW:
				// prompt user
				outputLine(String.format("The authenticity of host '%s' can't be established.", hostname));
				outputLine(String.format("RSA key fingerprint is %s", KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey)));
				//outputLine("[For now we'll assume you accept this key, but tap Menu and Disconnect if not.]");
				//outputLine("Are you sure you want to continue connecting (yes/no)? ");
				Boolean result = promptHelper.requestBooleanPrompt("Are you sure you want\nto continue connecting?");
				if(result == null) return false;
				if(result.booleanValue()) {
					// save this key in known database
					manager.hostdb.saveKnownHost(hostname, serverHostKeyAlgorithm, serverHostKey);
				}
				return result.booleanValue();
				
			case KnownHosts.HOSTKEY_HAS_CHANGED:
				outputLine("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
				outputLine("@    WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED!     @");
				outputLine("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
				outputLine("IT IS POSSIBLE THAT SOMEONE IS DOING SOMETHING NASTY!");
				outputLine("Someone could be eavesdropping on you right now (man-in-the-middle attack)!");
				outputLine("It is also possible that the RSA host key has just been changed.");
				outputLine(String.format("RSA key fingerprint is %s", KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey)));
				outputLine("Host key verification failed.");
				return false;
				
			}
			
			return false;
			
		}
		
	}
	
	public PromptHelper promptHelper;
	
	/**
	 * Create new terminal bridge with following parameters. We will immediately
	 * launch thread to start SSH connection and handle any hostkey verification
	 * and password authentication.
	 */
	public TerminalBridge(final TerminalManager manager, final String nickname, final String username, final String hostname, final int port) throws Exception {
		
		this.manager = manager;
		this.nickname = nickname;
		this.username = username;
		
		this.emulation = manager.getEmulation();
		this.scrollback = manager.getScrollback();
		this.postlogin = manager.getPostLogin(nickname);

		// create prompt helper to relay password and hostkey requests up to gui
		this.promptHelper = new PromptHelper(this);
		
		// create our default paint
		this.defaultPaint = new Paint();
		this.defaultPaint.setAntiAlias(true);
		this.defaultPaint.setTypeface(Typeface.MONOSPACE);
		
		this.setFontSize(DEFAULT_FONT_SIZE);

		// prepare our "darker" colors
		for(int i = 0; i < color.length; i++)
			this.darkerColor[i] = darken(color[i]);
		
		// create terminal buffer and handle outgoing data
		// this is probably status reply information
		this.buffer = new vt320() {
			public void write(byte[] b) {
				try {
					TerminalBridge.this.stdin.write(b);
				} catch (IOException e) {
					Log.e(TAG, "Problem handling incoming data in vt320() thread", e);
				} catch (NullPointerException npe) {
					// TODO buffer input?
					Log.d(TAG, "Input before we were connected discarded");
				}
			}

			public void sendTelnetCommand(byte cmd) {
			}

			public void setWindowSize(int c, int r) {
			}
		};

		this.buffer.setScreenSize(TERM_WIDTH_CHARS, TERM_HEIGHT_CHARS, true);
		this.buffer.setBufferSize(scrollback);
		this.buffer.setDisplay(this);
		this.buffer.setCursorPosition(0, 0);

		// TODO Change this when hosts are beans as well
		this.portForwards = manager.hostdb.getPortForwardsForHost(manager.hostdb.findHostByNickname(nickname));
		
		// prepare the ssh connection for opening
		// we perform the actual connection later in startConnection()
		this.outputLine(String.format("Connecting to %s:%d", hostname, port));
		this.connection = new Connection(hostname, port);
		this.connection.addConnectionMonitor(this);
	}
	
	public final static int AUTH_TRIES = 20;
	
	/**
	 * Spawn thread to open connection and start login process.
	 */
	public void startConnection() {
		new Thread(new Runnable() {
			public void run() {
				try {
					connection.connect(new HostKeyVerifier());
					
					// enter a loop to keep trying until authentication
					int tries = 0;
					while(!connection.isAuthenticationComplete() && tries++ < AUTH_TRIES && !disconnectFlag) {
						handleAuthentication();
						
						// sleep to make sure we dont kill system
						Thread.sleep(1000);
					}
				} catch(Exception e) {
					Log.e(TAG, "Problem in SSH connection thread", e);
				}
			} 
		}).start();
	}
	
	/**
	 * Attempt connection with database row pointed to by cursor.
	 * @param cursor
	 * @return true for successful authentication
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeySpecException
	 * @throws IOException
	 */
	public boolean tryPublicKey(Cursor c) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		int COL_NICKNAME = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_NICKNAME),
			COL_TYPE = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_TYPE),
			COL_PRIVATE = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PRIVATE),
			COL_PUBLIC = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PUBLIC),
			COL_ENCRYPTED = c.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_ENCRYPTED);

		String keyNickname = c.getString(COL_NICKNAME);
		int encrypted = c.getInt(COL_ENCRYPTED);
		
		Object trileadKey = null;
		if(manager.isKeyLoaded(keyNickname)) {
			// load this key from memory if its already there
			Log.d(TAG, String.format("Found unlocked key '%s' already in-memory", keyNickname));
			trileadKey = manager.getKey(keyNickname);
			
		} else {
			// otherwise load key from database and prompt for password as needed
			String password = null;
			if (encrypted != 0)
				password = promptHelper.requestStringPrompt(String.format("Password for key '%s'", keyNickname));

			String type = c.getString(COL_TYPE);
			if(PubkeyDatabase.KEY_TYPE_IMPORTED.equals(type)) {
				// load specific key using pem format
				byte[] raw = c.getBlob(COL_PRIVATE);
				trileadKey = PEMDecoder.decode(new String(raw).toCharArray(), password);
				
			} else {
				// load using internal generated format
				PrivateKey privKey;
				try {
					privKey = PubkeyUtils.decodePrivate(c.getBlob(COL_PRIVATE),
							c.getString(COL_TYPE), password);
				} catch (Exception e) {
					String message = String.format("Bad password for key '%s'. Authentication failed.", keyNickname);
					Log.e(TAG, message, e);
					outputLine(message);
					return false;
				}
				
				PublicKey pubKey = PubkeyUtils.decodePublic(c.getBlob(COL_PUBLIC),
						c.getString(COL_TYPE));
				
				// convert key to trilead format
				trileadKey = PubkeyUtils.convertToTrilead(privKey, pubKey);
				Log.d(TAG, "Unlocked key " + PubkeyUtils.formatKey(pubKey));
			}

			Log.d(TAG, String.format("Unlocked key '%s'", keyNickname));

			// save this key in-memory if option enabled
			if(manager.isSavingKeys()) {
				manager.addKey(keyNickname, trileadKey);
			}
		}

		return this.tryPublicKey(this.username, nickname, trileadKey);
		
	}
	
	protected boolean tryPublicKey(String username, String keyNickname, Object trileadKey) throws IOException {
		//outputLine(String.format("Attempting 'publickey' with key '%s' [%s]...", keyNickname, trileadKey.toString()));
		boolean success = connection.authenticateWithPublicKey(username, trileadKey);
		if(!success)
			outputLine(String.format("Authentication method 'publickey' with key '%s' failed", keyNickname));
		return success;
	}
	
	public void handleAuthentication() {
		try {
			if (connection.authenticateWithNone(username)) {
				finishConnection();
				return;
			}
		} catch(Exception e) {
			Log.d(TAG, "Host does not support 'none' authentication.");
		}
		
		outputLine("Trying to authenticate");
		
		try {
			long pubkeyId = manager.hostdb.getPubkeyId(nickname);
			
			if (!pubkeysExhausted &&
					pubkeyId != HostDatabase.PUBKEYID_NEVER &&
					connection.isAuthMethodAvailable(username, AUTH_PUBLICKEY)) {
				
				// if explicit pubkey defined for this host, then prompt for password as needed
				// otherwise just try all in-memory keys held in terminalmanager
				
				if (pubkeyId == HostDatabase.PUBKEYID_ANY) {
					// try each of the in-memory keys
					outputLine("Attempting 'publickey' authentication with any in-memory SSH keys");
					for(String nickname : manager.loadedPubkeys.keySet()) {
						Object trileadKey = manager.loadedPubkeys.get(nickname);
						if(this.tryPublicKey(this.username, nickname, trileadKey)) {
							finishConnection();
							break;
						}
					}
					
				} else {
					outputLine("Attempting 'publickey' authentication with a specific SSH key");
					// use a specific key for this host, as requested
					Cursor cursor = manager.pubkeydb.getPubkey(pubkeyId);
					if (cursor.moveToFirst())
						if (tryPublicKey(cursor))
							finishConnection();
					cursor.close();
					
				}
				
				pubkeysExhausted = true;
			} else if (connection.isAuthMethodAvailable(username, AUTH_PASSWORD)) {
				outputLine("Attempting 'password' authentication");
				String password = promptHelper.requestStringPrompt("Password");
				if(connection.authenticateWithPassword(username, password)) {
					finishConnection();
				} else {
					outputLine("Authentication method 'password' failed");
				}
				
			} else if(connection.isAuthMethodAvailable(username, AUTH_KEYBOARDINTERACTIVE)) {
				// this auth method will talk with us using InteractiveCallback interface
				// it blocks until authentication finishes 
				outputLine("Attempting 'keyboard-interactive' authentication");
				if(connection.authenticateWithKeyboardInteractive(username, TerminalBridge.this)) {
					finishConnection();
				} else {
					outputLine("Authentication method 'keyboard-interactive' failed");
				}
				
			} else {
				outputLine("[Your host doesn't support 'password' or 'keyboard-interactive' authentication.]");
				
			}	
		} catch(Exception e) {
			Log.e(TAG, "Problem during handleAuthentication()", e);
		}
		
	}
	
	/**
	 * Handle challenges from keyboard-interactive authentication mode.
	 */
	public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) throws Exception {
		String[] responses = new String[numPrompts];
		for(int i = 0; i < numPrompts; i++) {
			// request response from user for each prompt
			responses[i] = promptHelper.requestStringPrompt(prompt[i]);
		}
		return responses;
	}

	
	/**
	 * Convenience method for writing a line into the underlying MUD buffer.
	 */
	protected void outputLine(String line) {
		this.buffer.putString(0, this.buffer.getCursorRow(), line);
		this.buffer.setCursorPosition(0, this.buffer.getCursorRow() + 1);
		this.redraw();
	}

	/**
	 * Inject a specific string into this terminal. Used for post-login strings
	 * and pasting clipboard.
	 */
	public void injectString(final String string) {
		new Thread(new Runnable() {
			public void run() {
				if(string == null || string.length() == 0) return;
				KeyEvent[] events = keymap.getEvents(string.toCharArray());
				if(events == null || events.length == 0) return;
				for(KeyEvent event : events) {
					onKey(null, event.getKeyCode(), event);
				}
			}
		}).start();
	}
	
	public boolean fullyConnected = false;
	
	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	protected void finishConnection() {
		
		try {
			this.session = connection.openSession();
			buffer.deleteArea(0, 0, TerminalBridge.this.buffer.getColumns(), TerminalBridge.this.buffer.getRows());
			
			// previously tried vt100 and xterm for emulation modes
			// "screen" works the best for color and escape codes
			// TODO: pull this value from the preferences
			this.session.requestPTY(emulation, 0, 0, 0, 0, null);
			this.session.startShell();

			// grab stdin/out from newly formed session
			this.stdin = this.session.getStdin();
			this.stdout = this.session.getStdout();

			// create thread to relay incoming connection data to buffer
			this.relay = new Thread(new Runnable() {
				public void run() {
					byte[] b = new byte[256];
					int n = 0;
					while(n >= 0) {
						try {
							n = TerminalBridge.this.stdout.read(b);
							if(n > 0) {
								// pass along data to buffer, then redraw any results
								((vt320)TerminalBridge.this.buffer).putString(new String(b, 0, n, ENCODING));
								TerminalBridge.this.redraw();
							}
						} catch (IOException e) {
							Log.e(TAG, "Problem while handling incoming data in relay thread", e);
							break;
						}
					}
				}
			});
			this.relay.start();

			// force font-size to make sure we resizePTY as needed
			this.setFontSize(this.fontSize);
			
			this.fullyConnected = true;
			
			// Start up predefined port forwards
			ListIterator<PortForwardBean> itr = portForwards.listIterator();
			while (itr.hasNext()) {
				PortForwardBean pfb = itr.next();
				Log.d(TAG, String.format("Enabling port forward %s", pfb.getDescription()));
				enablePortForward(pfb);
			}
			
			// finally send any post-login string, if requested
			this.injectString(postlogin);

		} catch (IOException e1) {
			Log.e(TAG, "Problem while trying to create PTY in finishConnection()", e1);
		}
		
	}
	
	protected BridgeDisconnectedListener disconnectListener = null;
	
	public void setOnDisconnectedListener(BridgeDisconnectedListener disconnectListener) {
		this.disconnectListener = disconnectListener;
	}
	
	protected boolean disconnectFlag = false;
	
	/**
	 * Force disconnection of this terminal bridge.
	 */
	public void dispatchDisconnect() {
		// disconnection request hangs if we havent really connected to a host yet
		// temporary fix is to just spawn disconnection into a thread
		new Thread(new Runnable() {
			public void run() {
				if(session != null)
					session.close();
				connection.close();
			}
		}).start();
		
		this.disconnectFlag = true;
		this.fullyConnected = false;
		
		// pass notification back up to terminal manager
		// the manager will do any gui notification if applicable
		if(this.disconnectListener != null)
			this.disconnectListener.onDisconnected(this);
		
	}
	
	public String keymode = null;
	
	public void refreshKeymode() {
		this.keymode = this.manager.getKeyMode();
	}
	
	public KeyCharacterMap keymap = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);
	
	/**
	 * Handle onKey() events coming down from a {@link TerminalView} above us.
	 * We might collect these for our internal buffer when working with hostkeys
	 * or passwords, but otherwise we pass them directly over to the SSH host.
	 */
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		// pass through any keystrokes to output stream
		
		// ignore any key-up events
		if(event.getAction() == KeyEvent.ACTION_UP) return false;
		
		try {
			// check for terminal resizing keys
			// TODO: see if there is a way to make sure we dont "blip"
			if(keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				this.forcedSize = false;
				this.setFontSize(this.fontSize + 2);
				return true;
			} else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				this.forcedSize = false;
				this.setFontSize(this.fontSize - 2);
				return true;
			}
			
			boolean printing = (keymap.isPrintingKey(keyCode) || keyCode == KeyEvent.KEYCODE_SPACE);
			
			// skip keys if we arent connected yet
			if(this.session == null) return false;
			
			// otherwise pass through to existing session
			// print normal keys
			if (printing) {
				int metaState = event.getMetaState();
				
				if (shiftPressed) {
					metaState |= KeyEvent.META_SHIFT_ON;
					shiftPressed = false;
				}
				
				if (altPressed) {
					metaState |= KeyEvent.META_ALT_ON;
					altPressed = false;
				}
				
				int key = keymap.get(keyCode, metaState);
				
				//Log.d(TAG, Integer.toString(event.getMetaState()));

				if (ctrlPressed) {
				//if((event.getMetaState() & KeyEvent.META_SYM_ON) != 0) {
		    		// Support CTRL-A through CTRL-Z
		    		if (key >= 0x61 && key <= 0x79)
		    			key -= 0x60;
		    		else if (key >= 0x40 && key <= 0x59)
		    			key -= 0x39;
		    		ctrlPressed = false;
				}
				
				// handle pressing f-keys
				if((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
					//Log.d(TAG, "yay pressing an fkey");
					switch(key) {
					case '!': ((vt320)buffer).keyPressed(vt320.KEY_F1, ' ', 0); return true;
					case '@': ((vt320)buffer).keyPressed(vt320.KEY_F2, ' ', 0); return true;
					case '#': ((vt320)buffer).keyPressed(vt320.KEY_F3, ' ', 0); return true;
					case '$': ((vt320)buffer).keyPressed(vt320.KEY_F4, ' ', 0); return true;
					case '%': ((vt320)buffer).keyPressed(vt320.KEY_F5, ' ', 0); return true;
					case '^': ((vt320)buffer).keyPressed(vt320.KEY_F6, ' ', 0); return true;
					case '&': ((vt320)buffer).keyPressed(vt320.KEY_F7, ' ', 0); return true;
					case '*': ((vt320)buffer).keyPressed(vt320.KEY_F8, ' ', 0); return true;
					case '(': ((vt320)buffer).keyPressed(vt320.KEY_F9, ' ', 0); return true;
					case ')': ((vt320)buffer).keyPressed(vt320.KEY_F10, ' ', 0); return true;
					}
				}
				
				this.stdin.write(key);
				return true;
			}
			
			// try handling keymode shortcuts
			if("Use right-side keys".equals(this.keymode)) {
				switch(keyCode) {
				case KeyEvent.KEYCODE_ALT_RIGHT: this.stdin.write('/'); return true;
				case KeyEvent.KEYCODE_SHIFT_RIGHT: this.stdin.write(0x09); return true;
				case KeyEvent.KEYCODE_SHIFT_LEFT: this.shiftPressed = true; return true;
				case KeyEvent.KEYCODE_ALT_LEFT: this.altPressed = true; return true;
				}
			} else if("Use left-side keys".equals(this.keymode)) {
				switch(keyCode) {
				case KeyEvent.KEYCODE_ALT_LEFT: this.stdin.write('/'); return true;
				case KeyEvent.KEYCODE_SHIFT_LEFT: this.stdin.write(0x09); return true;
				case KeyEvent.KEYCODE_SHIFT_RIGHT: this.shiftPressed = true; return true;
				case KeyEvent.KEYCODE_ALT_RIGHT: this.altPressed = true; return true;
				}
			}

			// look for special chars
			switch(keyCode) {
			case KeyEvent.KEYCODE_CAMERA:
				
				// check to see which shortcut the camera button triggers
				String camera = manager.prefs.getString(manager.res.getString(R.string.pref_camera), manager.res.getString(R.string.list_camera_ctrlaspace));
				if(manager.res.getString(R.string.list_camera_ctrlaspace).equals(camera)) {
					this.stdin.write(0x01);
					this.stdin.write(' ');
					
				} else if(manager.res.getString(R.string.list_camera_ctrla).equals(camera)) {
					this.stdin.write(0x01);
					
				} else if(manager.res.getString(R.string.list_camera_esc).equals(camera)) {
					((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
					
				}

				//((vt320)buffer).keyTyped('a', 'a', vt320.KEY_CONTROL);
				//((vt320)buffer).keyTyped(' ', ' ', 0);
				break;
				
			case KeyEvent.KEYCODE_DEL: stdin.write(0x08); return true;
			case KeyEvent.KEYCODE_ENTER: ((vt320)buffer).keyTyped(vt320.KEY_ENTER, ' ', event.getMetaState()); return true;
			case KeyEvent.KEYCODE_DPAD_LEFT: ((vt320)buffer).keyPressed(vt320.KEY_LEFT, ' ', event.getMetaState()); return true;
			case KeyEvent.KEYCODE_DPAD_UP: ((vt320)buffer).keyPressed(vt320.KEY_UP, ' ', event.getMetaState()); return true;
			case KeyEvent.KEYCODE_DPAD_DOWN: ((vt320)buffer).keyPressed(vt320.KEY_DOWN, ' ', event.getMetaState()); return true;
			case KeyEvent.KEYCODE_DPAD_RIGHT: ((vt320)buffer).keyPressed(vt320.KEY_RIGHT, ' ', event.getMetaState()); return true;
			case KeyEvent.KEYCODE_DPAD_CENTER:
				// TODO: Add some visual indication of Ctrl state
				if (ctrlPressed) {
					((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
					ctrlPressed = false;
				} else
					ctrlPressed = true;
				return true;
			}
			
		} catch (IOException e) {
			Log.e(TAG, "Problem while trying to handle an onKey() event", e);
		}
		return false;
	}
	

	public int charWidth = -1,
		charHeight = -1,
		charDescent = -1;
	
	protected float fontSize = -1;
	
	/**
	 * Request a different font size. Will make call to parentChanged() to make
	 * sure we resize PTY if needed.
	 */
	protected void setFontSize(float size) {
		this.defaultPaint.setTextSize(size);
		this.fontSize = size;
		
		// read new metrics to get exact pixel dimensions
		FontMetricsInt fm = this.defaultPaint.getFontMetricsInt();
		this.charDescent = fm.descent;
		
		float[] widths = new float[1];
		this.defaultPaint.getTextWidths("X", widths);
		this.charWidth = (int)widths[0];
		this.charHeight = Math.abs(fm.top) + Math.abs(fm.descent) + 1;
		
		// refresh any bitmap with new font size
		if(this.parent != null)
			this.parentChanged(this.parent);
	}
	
	/**
	 * Flag indicating if we should perform a full-screen redraw during our next
	 * rendering pass.
	 */
	protected boolean fullRedraw = false;
	
	/**
	 * Something changed in our parent {@link TerminalView}, maybe it's a new
	 * parent, or maybe it's an updated font size. We should recalculate
	 * terminal size information and request a PTY resize.
	 */
	public synchronized void parentChanged(TerminalView parent) {
		
		this.parent = parent;
		int width = parent.getWidth();
		int height = parent.getHeight();
		
		// recalculate buffer size
		int termWidth, termHeight;
		
		if (this.forcedSize) {
			termWidth = this.termWidth;
			termHeight = this.termHeight;
		} else {
			termWidth = width / charWidth;
			termHeight = height / charHeight;
		}
		
		// reallocate new bitmap if needed
		boolean newBitmap = (this.bitmap == null);
		if(this.bitmap != null)
			newBitmap = (this.bitmap.getWidth() != width || this.bitmap.getHeight() != height);
		
		if(newBitmap) {
			this.bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
			this.canvas.setBitmap(this.bitmap);
		}
		
		// clear out any old buffer information
		this.defaultPaint.setColor(Color.BLACK);
		this.canvas.drawRect(0, 0, width, height, this.defaultPaint);

		// Stroke the border of the terminal if the size is being forced;
		if (this.forcedSize) {
			int borderX = (termWidth * charWidth) + 1;
			int borderY = (termHeight * charHeight) + 1;
			
			this.defaultPaint.setColor(Color.GRAY);
			this.defaultPaint.setStrokeWidth(0.0f);
			if (width >= borderX)
				this.canvas.drawLine(borderX, 0, borderX, borderY + 1, defaultPaint);
			if (height >= borderY)
				this.canvas.drawLine(0, borderY, borderX + 1, borderY, defaultPaint);
		}
		
		try {
			// request a terminal pty resize
			buffer.setScreenSize(termWidth, termHeight, true);
			if(session != null)
				session.resizePTY(termWidth, termHeight);
		} catch(Exception e) {
			Log.e(TAG, "Problem while trying to resize screen or PTY", e);
		}
		
		// force full redraw with new buffer size
		this.fullRedraw = true;
		this.redraw();

		this.parent.notifyUser(String.format("%d x %d", termWidth, termHeight));
		
		Log.i(TAG, String.format("parentChanged() now width=%d, height=%d", termWidth, termHeight));
	}
	
	/**
	 * Somehow our parent {@link TerminalView} was destroyed. Now we don't need
	 * to redraw anywhere, and we can recycle our internal bitmap.
	 */
	public synchronized void parentDestroyed() {
		this.parent = null;
		if(this.bitmap != null)
			this.bitmap.recycle();
		this.bitmap = null;
		this.canvas.setBitmap(null);
	}

	public void setVDUBuffer(VDUBuffer buffer) {
		this.buffer = buffer;
	}

	public VDUBuffer getVDUBuffer() {
		return buffer;
	}
	
	public long lastDraw = 0;
	public long drawTolerance = 100;

	public synchronized void redraw() {
		// render our buffer only if we have a surface
		if(this.parent == null) return;
		
		int lines = 0;
		
		int fg, bg;
		boolean entireDirty = buffer.update[0] || this.fullRedraw;
		
		// walk through all lines in the buffer
		for(int l = 0; l < buffer.height; l++) {
			
			// check if this line is dirty and needs to be repainted
			// also check for entire-buffer dirty flags
			if(!entireDirty && !buffer.update[l + 1]) continue;
			lines++;
			
			// reset dirty flag for this line
			buffer.update[l + 1] = false;
			
			// walk through all characters in this line
			for (int c = 0; c < buffer.width; c++) {
				int addr = 0;
				int currAttr = buffer.charAttributes[buffer.windowBase + l][c];

				// reset default colors
				fg = color[COLOR_FG_STD];
				bg = color[COLOR_BG_STD];
				
				// check if foreground color attribute is set
				if((currAttr & VDUBuffer.COLOR_FG) != 0)
					fg = color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1];

				// check if background color attribute is set
				if((currAttr & VDUBuffer.COLOR_BG) != 0)
					bg = darkerColor[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1];
				
				// support character inversion by swapping background and foreground color
				if ((currAttr & VDUBuffer.INVERT) != 0) {
					int swapc = bg;
					bg = fg;
					fg = swapc;
				}
				
				// if black-on-black, try correcting to grey
				if(fg == Color.BLACK && bg == Color.BLACK)
					fg = Color.GRAY;
				
				// correctly set bold and underlined attributes if requested
				this.defaultPaint.setFakeBoldText((currAttr & VDUBuffer.BOLD) != 0);
				this.defaultPaint.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);
				
				// determine the amount of continuous characters with the same settings and print them all at once
				while(c + addr < buffer.width && buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr) {
					addr++;
				}
				
				// clear this dirty area with background color
				this.defaultPaint.setColor(bg);
				canvas.drawRect(c * charWidth, (l * charHeight) - 1, (c + addr) * charWidth, (l + 1) * charHeight, this.defaultPaint);
				
				// write the text string starting at 'c' for 'addr' number of characters
				this.defaultPaint.setColor(fg);
				if((currAttr & VDUBuffer.INVISIBLE) == 0)
					canvas.drawText(buffer.charArray[buffer.windowBase + l], c,
						addr, c * charWidth, ((l + 1) * charHeight) - charDescent - 2,
						this.defaultPaint);
				
				// advance to the next text block with different characteristics
				c += addr - 1;
			}
		}
		
		// reset entire-buffer flags
		buffer.update[0] = false;
		this.fullRedraw = false;
		
		this.parent.postInvalidate();
		
	}

	public void updateScrollBar() {
	}

	public void connectionLost(Throwable reason) {
		// weve lost our ssh connection, so pass along to manager and gui
		Log.e(TAG, "Somehow our underlying SSH socket died", reason);
		this.dispatchDisconnect();
	}

	/**
	 * Resize terminal to fit [rows]x[cols] in screen of size [width]x[height]
	 * @param rows
	 * @param cols
	 * @param width
	 * @param height
	 */
	public void resizeComputed(int cols, int rows, int width, int height) {
		float size = 8.0f;
		float step = 8.0f;
		float limit = 0.125f;
		
		int direction;

		while ((direction = fontSizeCompare(size, cols, rows, width, height)) < 0)
			size += step;
		
		if (direction == 0) {
			Log.d("fontsize", String.format("Found match at %f", size));
			return;
		}
		
		step /= 2.0f;
		size -= step;
		
		while ((direction = fontSizeCompare(size, cols, rows, width, height)) != 0
				&& step >= limit) {
			step /= 2.0f;
			if (direction > 0) {
				size -= step;
			} else {
				size += step;
			}
		}
		
		if (direction > 0)
			size -= step;
		
		this.forcedSize = true;
		this.termWidth = cols;
		this.termHeight = rows;
		setFontSize(size);
	}
	
	private int fontSizeCompare(float size, int cols, int rows, int width, int height) {
		// read new metrics to get exact pixel dimensions
		this.defaultPaint.setTextSize(size);
		FontMetricsInt fm = this.defaultPaint.getFontMetricsInt();
		
		float[] widths = new float[1];
		this.defaultPaint.getTextWidths("X", widths);
		int termWidth = (int)widths[0] * cols;
		int termHeight = (Math.abs(fm.top) + Math.abs(fm.descent) + 1) * rows;
		
		Log.d("fontsize", String.format("font size %f resulted in %d x %d", size, termWidth, termHeight));
		
		// Check to see if it fits in resolution specified.
		if (termWidth > width || termHeight > height)
			return 1;
		
		if (termWidth == width || termHeight == height)
			return 0;
		
		return -1;
	}

	/**
	 * Adds the {@link PortForwardBean} to the list.
	 * @param portForward the port forward bean to add
	 * @return true on successful addition
	 */
	public boolean addPortForward(PortForwardBean portForward) {
		return this.portForwards.add(portForward);
	}

	/**
	 * Removes the {@link PortForwardBean} from the list.
	 * @param portForward the port forward bean to remove
	 * @return true on successful removal
	 */
	public boolean removePortForward(PortForwardBean portForward) {
		// Make sure we don't have a phantom forwarder.
		disablePortForward(portForward);
		
		return this.portForwards.remove(portForward);
	}
	
	/**
	 * @return the list of port forwards
	 */
	public List<PortForwardBean> getPortForwards() {
		return portForwards;
	}

	/**
	 * Enables a port forward member. After calling this method, the port forward should
	 * be operational.
	 * @param portForward member of our current port forwards list to enable
	 * @return true on successful port forward setup
	 */
	public boolean enablePortForward(PortForwardBean portForward) {
		if (!this.portForwards.contains(portForward)) {
			Log.e(TAG, "Attempt to enable port forward not in list");
			return false;
		}
		
		if (HostDatabase.PORTFORWARD_LOCAL.equals(portForward.getType())) {
			LocalPortForwarder lpf = null;
			try {
				lpf = this.connection.createLocalPortForwarder(portForward.getSourcePort(), portForward.getDestAddr(), portForward.getDestPort());
			} catch (IOException e) {
				Log.e(TAG, "Could not create local port forward", e);
				return false;
			}
			
			if (lpf == null) {
				Log.e(TAG, "returned LocalPortForwarder object is null");
				return false;
			}
			
			portForward.setIdentifier(lpf);
			portForward.setEnabled(true);
			return true;
		} else if (HostDatabase.PORTFORWARD_REMOTE.equals(portForward.getType())) {
			try {
				this.connection.requestRemotePortForwarding("", portForward.getSourcePort(), portForward.getDestAddr(), portForward.getDestPort());
			} catch (IOException e) {
				Log.e(TAG, "Could not create remote port forward", e);
				return false;
			}
			
			portForward.setEnabled(false);
			return true;
		} else if (HostDatabase.PORTFORWARD_DYNAMIC5.equals(portForward.getType())) {
			DynamicPortForwarder dpf = null;
			
			try {
				dpf = this.connection.createDynamicPortForwarder(portForward.getSourcePort());
			} catch (IOException e) {
				Log.e(TAG, "Could not create dynamic port forward", e);
				return false;
			}
			
			portForward.setIdentifier(dpf);
			portForward.setEnabled(true);
			return true;
		} else {
			// Unsupported type
			Log.e(TAG, String.format("attempt to forward unknown type %s", portForward.getType()));
			return false;
		}
	}

	/**
	 * Disables a port forward member. After calling this method, the port forward should
	 * be non-functioning.
	 * @param portForward member of our current port forwards list to enable
	 * @return true on successful port forward tear-down
	 */
	public boolean disablePortForward(PortForwardBean portForward) {
		if (portForward.getType() == HostDatabase.PORTFORWARD_LOCAL) {
			LocalPortForwarder lpf = null;
			lpf = (LocalPortForwarder)portForward.getIdentifier();
			
			if (!portForward.isEnabled() || lpf == null)
				return false;
			
			portForward.setEnabled(false);

			try {
				lpf.close();
			} catch (IOException e) {
				Log.e(TAG, "Could not stop local port forwarder, setting enabled to false", e);
				return false;
			}
			
			return true;
		} else if (portForward.getType() == HostDatabase.PORTFORWARD_REMOTE) {
			portForward.setEnabled(false);

			try {
				this.connection.cancelRemotePortForwarding(portForward.getSourcePort());
			} catch (IOException e) {
				Log.e(TAG, "Could not stop remote port forwarding, setting enabled to false", e);
				return false;
			}
			
			return true;
		} else if (portForward.getType() == HostDatabase.PORTFORWARD_DYNAMIC5) {
			DynamicPortForwarder dpf = null;
			dpf = (DynamicPortForwarder)portForward.getIdentifier();
			
			if (!portForward.isEnabled() || dpf == null)
				return false;
			
			portForward.setEnabled(false);
			
			try {
				dpf.close();
			} catch (IOException e) {
				Log.e(TAG, "Could not stop dynamic port forwarder, setting enabled to false", e);
				return false;
			}
			
			return true;
		} else {
			return false;
		}
	}
}
