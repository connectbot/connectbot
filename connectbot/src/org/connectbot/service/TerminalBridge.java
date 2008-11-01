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

import org.connectbot.TerminalView;
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
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;

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
	
	protected View parent = null;
	protected Canvas canvas = new Canvas();

	private boolean ctrlPressed = false;
	private boolean altPressed = false;
	private boolean shiftPressed = false;
		
	protected PubkeyDatabase pubkeydb = null;
	protected Cursor pubkeys = null;
	final int COL_NICKNAME, COL_TYPE, COL_PRIVATE, COL_PUBLIC, COL_ENCRYPTED;
	private boolean pubkeysExhausted = false;
	
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

		// prepare the ssh connection for opening
		// we perform the actual connection later in startConnection()
		this.outputLine(String.format("Connecting to %s:%d", hostname, port));
		this.connection = new Connection(hostname, port);
		this.connection.addConnectionMonitor(this);

		this.pubkeydb = new PubkeyDatabase(manager);
		this.pubkeys = this.pubkeydb.allPubkeys();
		
		this.COL_NICKNAME = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_NICKNAME);
		this.COL_TYPE = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_TYPE);
		this.COL_PRIVATE = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PRIVATE);
		this.COL_PUBLIC = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_PUBLIC);
		this.COL_ENCRYPTED = pubkeys.getColumnIndexOrThrow(PubkeyDatabase.FIELD_PUBKEY_ENCRYPTED);
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
		String keyNickname = c.getString(COL_NICKNAME);
		int encrypted = c.getInt(COL_ENCRYPTED);
		
		String password = null;
		if (encrypted != 0)
			password = promptHelper.requestStringPrompt(String.format("Password for key '%s'", keyNickname));
		
		PrivateKey privKey;
		try {
			privKey = PubkeyUtils.decodePrivate(c.getBlob(COL_PRIVATE),
					c.getString(COL_TYPE), password);
		} catch (Exception e) {
			e.printStackTrace();
			outputLine("Bad password for key '" + keyNickname + "'. Authentication failed.");
			return false;
		}
		
		PublicKey pubKey = PubkeyUtils.decodePublic(c.getBlob(COL_PUBLIC),
				c.getString(COL_TYPE));
		
		Log.d("TerminalBridge", "Trying key " + PubkeyUtils.formatKey(pubKey));
		
		if (connection.authenticateWithPublicKey(username,
				PubkeyUtils.convertToTrilead(privKey, pubKey)))
			return true;
		else
			outputLine("Authentication method 'publickey' with key " + keyNickname + " failed");
		
		return false;
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
				Cursor cursor;
				
				if (pubkeyId == HostDatabase.PUBKEYID_ANY) {
					cursor = pubkeydb.allPubkeys();
									
					while (cursor.moveToNext()) {
						if (cursor.getInt(COL_ENCRYPTED) == 0) {
							if (tryPublicKey(cursor))
								finishConnection();
						}
					}
				} else {
					cursor = pubkeydb.getPubkey(pubkeyId);
					if (cursor.moveToFirst())
						if (tryPublicKey(cursor))
							finishConnection();
				}
				
				cursor.close();
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
	public void disconnect() {
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
				this.setFontSize(this.fontSize + 2);
				return true;
			} else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
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
				this.stdin.write(0x01);
				this.stdin.write(' ');
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
	public synchronized void parentChanged(View parent) {
		
		this.parent = parent;
		int width = parent.getWidth();
		int height = parent.getHeight();
		
		// recalculate buffer size
		int termWidth = width / charWidth;
		int termHeight = height / charHeight;
		
		// convert our height/width to integral values
		width = termWidth * charWidth;
		height = termHeight * charHeight;
		
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
		
		if (this.pubkeydb != null)
			this.pubkeydb.close();
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
		this.disconnect();
	}



}
