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
import java.util.LinkedList;
import java.util.List;

import org.connectbot.R;
import org.connectbot.TerminalView;
import org.connectbot.bean.HostBean;
import org.connectbot.bean.PortForwardBean;
import org.connectbot.bean.SelectionArea;
import org.connectbot.transport.AbsTransport;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.FontMetrics;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
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
public class TerminalBridge implements VDUDisplay, OnKeyListener {
	public final static String TAG = "ConnectBot.TerminalBridge";

	public final static int DEFAULT_FONT_SIZE = 10;

	public int color[];

	public final static int COLOR_FG_STD = 7;
	public final static int COLOR_BG_STD = 0;

	protected final TerminalManager manager;

	public HostBean host;

	private AbsTransport transport;

	private final Paint defaultPaint;

	private Relay relay;

	private final String emulation;
	private final int scrollback;

	public Bitmap bitmap = null;
	public VDUBuffer buffer = null;

	private TerminalView parent = null;
	private final Canvas canvas = new Canvas();

	private int metaState = 0;

	public final static int META_CTRL_ON = 0x01;
	public final static int META_CTRL_LOCK = 0x02;
	public final static int META_ALT_ON = 0x04;
	public final static int META_ALT_LOCK = 0x08;
	public final static int META_SHIFT_ON = 0x10;
	public final static int META_SHIFT_LOCK = 0x20;
	public final static int META_SLASH = 0x40;
	public final static int META_TAB = 0x80;

	// The bit mask of momentary and lock states for each
	public final static int META_CTRL_MASK = META_CTRL_ON | META_CTRL_LOCK;
	public final static int META_ALT_MASK = META_ALT_ON | META_ALT_LOCK;
	public final static int META_SHIFT_MASK = META_SHIFT_ON | META_SHIFT_LOCK;

	// All the transient key codes
	public final static int META_TRANSIENT = META_CTRL_ON | META_ALT_ON
			| META_SHIFT_ON;

	private boolean disconnected = false;
	private boolean awaitingClose = false;

	private boolean forcedSize = false;
	private int columns;
	private int rows;

	private String keymode = null;

	private boolean selectingForCopy = false;
	private final SelectionArea selectionArea;
	private ClipboardManager clipboard;

	protected KeyCharacterMap keymap = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);

	public int charWidth = -1;
	public int charHeight = -1;
	private int charTop = -1;

	private float fontSize = -1;

	private final List<FontSizeChangedListener> fontSizeChangedListeners;

	private final List<String> localOutput;

	/**
	 * Flag indicating if we should perform a full-screen redraw during our next
	 * rendering pass.
	 */
	private boolean fullRedraw = false;

	public PromptHelper promptHelper;

	protected BridgeDisconnectedListener disconnectListener = null;

	/**
	 * Create a new terminal bridge suitable for unit testing.
	 */
	public TerminalBridge() {
		buffer = new vt320() {
			@Override
			public void write(byte[] b) {}
			@Override
			public void write(int b) {}
			@Override
			public void sendTelnetCommand(byte cmd) {}
			@Override
			public void setWindowSize(int c, int r) {}
			@Override
			public void debug(String s) {}
		};

		emulation = null;
		manager = null;

		defaultPaint = new Paint();

		selectionArea = new SelectionArea();
		scrollback = 1;

		localOutput = new LinkedList<String>();

		fontSizeChangedListeners = new LinkedList<FontSizeChangedListener>();

		transport = null;
	}

	/**
	 * Create new terminal bridge with following parameters. We will immediately
	 * launch thread to start SSH connection and handle any hostkey verification
	 * and password authentication.
	 */
	public TerminalBridge(final TerminalManager manager, final HostBean host) throws IOException {

		this.manager = manager;
		this.host = host;

		emulation = manager.getEmulation();
		scrollback = manager.getScrollback();

		// create prompt helper to relay password and hostkey requests up to gui
		promptHelper = new PromptHelper(this);

		// create our default paint
		defaultPaint = new Paint();
		defaultPaint.setAntiAlias(true);
		defaultPaint.setTypeface(Typeface.MONOSPACE);
		defaultPaint.setFakeBoldText(true); // more readable?

		localOutput = new LinkedList<String>();

		fontSizeChangedListeners = new LinkedList<FontSizeChangedListener>();

		setFontSize(DEFAULT_FONT_SIZE);

		// create terminal buffer and handle outgoing data
		// this is probably status reply information
		buffer = new vt320() {
			@Override
			public void debug(String s) {
				Log.d(TAG, s);
			}

			@Override
			public void write(byte[] b) {
				try {
					if (b != null && transport != null)
						transport.write(b);
				} catch (IOException e) {
					Log.e(TAG, "Problem writing outgoing data in vt320() thread", e);
				}
			}

			@Override
			public void write(int b) {
				try {
					if (transport != null)
						transport.write(b);
				} catch (IOException e) {
					Log.e(TAG, "Problem writing outgoing data in vt320() thread", e);
				}
			}

			// We don't use telnet sequences.
			@Override
			public void sendTelnetCommand(byte cmd) {
			}

			// We don't want remote to resize our window.
			@Override
			public void setWindowSize(int c, int r) {
			}

			@Override
			public void beep() {
				if (parent.isShown())
					manager.playBeep();
				else
					manager.sendActivityNotification(host);
			}
		};

		buffer.setBufferSize(scrollback);
		resetColors();
		buffer.setDisplay(this);

		selectionArea = new SelectionArea();
	}

	public PromptHelper getPromptHelper() {
		return promptHelper;
	}

	/**
	 * Spawn thread to open connection and start login process.
	 */
	protected void startConnection() {
		transport = TransportFactory.getTransport(host.getProtocol());
		transport.setBridge(this);
		transport.setManager(manager);
		transport.setHost(host);

		// Should be more abstract?
		transport.setCompression(host.getCompression());
		transport.setEmulation(emulation);

		if (transport.canForwardPorts()) {
			for (PortForwardBean portForward : manager.hostdb.getPortForwardsForHost(host))
				transport.addPortForward(portForward);
		}

		outputLine(String.format("Connecting to %s:%d via %s", host.getHostname(), host.getPort(), host.getProtocol()));

		Thread connectionThread = new Thread(new Runnable() {
			public void run() {
				transport.connect();
			}
		});
		connectionThread.setName("Connection");
		connectionThread.start();
	}

	/**
	 * Handle challenges from keyboard-interactive authentication mode.
	 */
	public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) {
		String[] responses = new String[numPrompts];
		for(int i = 0; i < numPrompts; i++) {
			// request response from user for each prompt
			responses[i] = promptHelper.requestStringPrompt(instruction, prompt[i]);
		}
		return responses;
	}

	/**
	 * Sets the encoding used by the terminal. If the connection is live,
	 * then the character set is changed for the next read.
	 * @param encoding the canonical name of the character encoding
	 */
	public void setCharset(String encoding) {
		if (relay != null)
			relay.setCharset(encoding);
	}

	/**
	 * Convenience method for writing a line into the underlying MUD buffer.
	 * Should never be called once the session is established.
	 */
	public final void outputLine(String line) {
		if (transport != null && transport.isSessionOpen())
			Log.e(TAG, "Session established, cannot use outputLine!", new IOException("outputLine call traceback"));

		synchronized (localOutput) {
			final String s = line + "\r\n";

			localOutput.add(s);

			((vt320) buffer).putString(s);
		}
	}

	/**
	 * Inject a specific string into this terminal. Used for post-login strings
	 * and pasting clipboard.
	 */
	public void injectString(final String string) {
		Thread injectStringThread = new Thread(new Runnable() {
			public void run() {
				if(string == null || string.length() == 0) return;
				KeyEvent[] events = keymap.getEvents(string.toCharArray());
				if(events == null || events.length == 0) return;
				for(KeyEvent event : events) {
					onKey(null, event.getKeyCode(), event);
				}
			}
		});
		injectStringThread.setName("InjectString");
		injectStringThread.start();
	}

	/**
	 * Internal method to request actual PTY terminal once we've finished
	 * authentication. If called before authenticated, it will just fail.
	 */
	public void onConnected() {
		((vt320) buffer).reset();

		// We no longer need our local output.
		localOutput.clear();

		// previously tried vt100 and xterm for emulation modes
		// "screen" works the best for color and escape codes
		((vt320) buffer).setAnswerBack(emulation);

		if (HostDatabase.DELKEY_BACKSPACE.equals(host.getDelKey()))
			((vt320) buffer).setBackspace(vt320.DELETE_IS_BACKSPACE);
		else
			((vt320) buffer).setBackspace(vt320.DELETE_IS_DEL);

		// create thread to relay incoming connection data to buffer
		relay = new Relay(this, transport, (vt320) buffer, host.getEncoding());
		Thread relayThread = new Thread(relay);
		relayThread.setName("Relay");
		relayThread.start();

		// force font-size to make sure we resizePTY as needed
		setFontSize(fontSize);

		// finally send any post-login string, if requested
		injectString(host.getPostLogin());
	}

	/**
	 * @return whether a session is open or not
	 */
	public boolean isSessionOpen() {
		if (transport != null)
			return transport.isSessionOpen();
		return false;
	}

	public void setOnDisconnectedListener(BridgeDisconnectedListener disconnectListener) {
		this.disconnectListener = disconnectListener;
	}

	/**
	 * Force disconnection of this terminal bridge.
	 */
	public void dispatchDisconnect(boolean immediate) {
		// We don't need to do this multiple times.
		if (disconnected && !immediate)
			return;

		// disconnection request hangs if we havent really connected to a host yet
		// temporary fix is to just spawn disconnection into a thread
		Thread disconnectThread = new Thread(new Runnable() {
			public void run() {
				if (transport != null && transport.isConnected())
					transport.close();
			}
		});
		disconnectThread.setName("Disconnect");
		disconnectThread.start();

		disconnected = true;

		if (immediate) {
			awaitingClose = true;
			if (disconnectListener != null)
				disconnectListener.onDisconnected(TerminalBridge.this);
		} else {
			Thread disconnectPromptThread = new Thread(new Runnable() {
				public void run() {
					Boolean result = promptHelper.requestBooleanPrompt(null,
							manager.res.getString(R.string.prompt_host_disconnected), true);
					if (result == null || result.booleanValue()) {
						awaitingClose = true;

						// Tell the TerminalManager that we can be destroyed now.
						if (disconnectListener != null)
							disconnectListener.onDisconnected(TerminalBridge.this);
					}
				}
			});
			disconnectPromptThread.setName("DisconnectPrompt");
			disconnectPromptThread.start();
		}
	}

	public void refreshKeymode() {
		keymode = manager.getKeyMode();
	}

	/**
	 * Handle onKey() events coming down from a {@link TerminalView} above us.
	 * We might collect these for our internal buffer when working with hostkeys
	 * or passwords, but otherwise we pass them directly over to the SSH host.
	 */
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		try {

			// Ignore all key-up events except for the special keys
			if (event.getAction() == KeyEvent.ACTION_UP) {
				// skip keys if we aren't connected yet or have been disconnected
				if (disconnected || transport == null)
					return false;

				if (PreferenceConstants.KEYMODE_RIGHT.equals(keymode)) {
					if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT
							&& (metaState & META_SLASH) != 0) {
						metaState &= ~(META_SLASH | META_TRANSIENT);
						transport.write('/');
						return true;
					} else if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
							&& (metaState & META_TAB) != 0) {
						metaState &= ~(META_TAB | META_TRANSIENT);
						transport.write(0x09);
						return true;
					}
				} else if (PreferenceConstants.KEYMODE_LEFT.equals(keymode)) {
					if (keyCode == KeyEvent.KEYCODE_ALT_LEFT
							&& (metaState & META_SLASH) != 0) {
						metaState &= ~(META_SLASH | META_TRANSIENT);
						transport.write('/');
						return true;
					} else if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
							&& (metaState & META_TAB) != 0) {
						metaState &= ~(META_TAB | META_TRANSIENT);
						transport.write(0x09);
						return true;
					}
				}

				return false;
			}

			// check for terminal resizing keys
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				forcedSize = false;
				setFontSize(fontSize + 2);
				return true;
			} else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				forcedSize = false;
				setFontSize(fontSize - 2);
				return true;
			}

			// skip keys if we aren't connected yet or have been disconnected
			if (disconnected || transport == null)
				return false;

			// if we're in scrollback, scroll to bottom of window on input
			if (buffer.windowBase != buffer.screenBase)
				buffer.setWindowBase(buffer.screenBase);

			boolean printing = (keymap.isPrintingKey(keyCode) || keyCode == KeyEvent.KEYCODE_SPACE);

			// otherwise pass through to existing session
			// print normal keys
			if (printing) {
				int curMetaState = event.getMetaState();

				metaState &= ~(META_SLASH | META_TAB);

				if ((metaState & META_SHIFT_MASK) != 0) {
					curMetaState |= KeyEvent.META_SHIFT_ON;
					metaState &= ~META_SHIFT_ON;
					redraw();
				}

				if ((metaState & META_ALT_MASK) != 0) {
					curMetaState |= KeyEvent.META_ALT_ON;
					metaState &= ~META_ALT_ON;
					redraw();
				}

				int key = keymap.get(keyCode, curMetaState);

				if ((metaState & META_CTRL_MASK) != 0) {
					// Support CTRL-a through CTRL-z
					if (key >= 0x61 && key <= 0x7A)
						key -= 0x60;
					// Support CTRL-A through CTRL-_
					else if (key >= 0x41 && key <= 0x5F)
						key -= 0x40;
					else if (key == 0x20)
						key = 0x00;
					else if (key == 0x3F)
						key = 0x7F;

					metaState &= ~META_CTRL_ON;

					redraw();
				}

				// handle pressing f-keys
				if ((metaState & META_TAB) != 0) {
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

				if (key < 0x80)
					transport.write(key);
				else
					// TODO write encoding routine that doesn't allocate each time
					transport.write(new String(Character.toChars(key))
							.getBytes(host.getEncoding()));

				return true;
			}

			if (keyCode == KeyEvent.KEYCODE_UNKNOWN &&
					event.getAction() == KeyEvent.ACTION_MULTIPLE) {
				byte[] input = event.getCharacters().getBytes(host.getEncoding());
				transport.write(input);
			}

			// try handling keymode shortcuts
			if (event.getRepeatCount() == 0) {
				if ("Use right-side keys".equals(keymode)) {
					switch(keyCode) {
					case KeyEvent.KEYCODE_ALT_RIGHT:
						metaState |= META_SLASH;
						return true;
					case KeyEvent.KEYCODE_SHIFT_RIGHT:
						metaState |= META_TAB;
						return true;
					case KeyEvent.KEYCODE_SHIFT_LEFT:
						metaPress(META_SHIFT_ON);
						return true;
					case KeyEvent.KEYCODE_ALT_LEFT:
						metaPress(META_ALT_ON);
						return true;
					default:
						break;
					}
				} else if ("Use left-side keys".equals(keymode)) {
					switch(keyCode) {
					case KeyEvent.KEYCODE_ALT_LEFT:
						metaState |= META_SLASH;
						return true;
					case KeyEvent.KEYCODE_SHIFT_LEFT:
						metaState |= META_TAB;
						return true;
					case KeyEvent.KEYCODE_SHIFT_RIGHT:
						metaPress(META_SHIFT_ON);
						return true;
					case KeyEvent.KEYCODE_ALT_RIGHT:
						metaPress(META_ALT_ON);
						return true;
					default:
						break;
					}
				}
			}

			// look for special chars
			switch(keyCode) {
			case KeyEvent.KEYCODE_CAMERA:

				// check to see which shortcut the camera button triggers
				String camera = manager.prefs.getString(
						PreferenceConstants.CAMERA,
						PreferenceConstants.CAMERA_CTRLA_SPACE);
				if(PreferenceConstants.CAMERA_CTRLA_SPACE.equals(camera)) {
					transport.write(0x01);
					transport.write(' ');
				} else if(PreferenceConstants.CAMERA_CTRLA.equals(camera)) {
					transport.write(0x01);
				} else if(PreferenceConstants.CAMERA_ESC.equals(camera)) {
					((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
				}

				break;

			case KeyEvent.KEYCODE_DEL:
				((vt320) buffer).keyPressed(vt320.KEY_BACK_SPACE, ' ',
						getStateForBuffer());
				metaState &= ~META_TRANSIENT;
				return true;
			case KeyEvent.KEYCODE_ENTER:
				((vt320)buffer).keyTyped(vt320.KEY_ENTER, ' ', 0);
				metaState &= ~META_TRANSIENT;
				return true;

			case KeyEvent.KEYCODE_DPAD_LEFT:
				if (selectingForCopy) {
					selectionArea.decrementColumn();
					redraw();
				} else {
					((vt320) buffer).keyPressed(vt320.KEY_LEFT, ' ',
							getStateForBuffer());
					metaState &= ~META_TRANSIENT;
					tryKeyVibrate();
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_UP:
				if (selectingForCopy) {
					selectionArea.decrementRow();
					redraw();
				} else {
					((vt320) buffer).keyPressed(vt320.KEY_UP, ' ',
							getStateForBuffer());
					metaState &= ~META_TRANSIENT;
					tryKeyVibrate();
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_DOWN:
				if (selectingForCopy) {
					selectionArea.incrementRow();
					redraw();
				} else {
					((vt320) buffer).keyPressed(vt320.KEY_DOWN, ' ',
							getStateForBuffer());
					metaState &= ~META_TRANSIENT;
					tryKeyVibrate();
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (selectingForCopy) {
					selectionArea.incrementColumn();
					redraw();
				} else {
					((vt320) buffer).keyPressed(vt320.KEY_RIGHT, ' ',
							getStateForBuffer());
					metaState &= ~META_TRANSIENT;
					tryKeyVibrate();
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_CENTER:
				if (selectingForCopy) {
					if (selectionArea.isSelectingOrigin())
						selectionArea.finishSelectingOrigin();
					else {
						if (parent != null && clipboard != null) {
							// copy selected area to clipboard
							String copiedText = selectionArea.copyFrom(buffer);

							clipboard.setText(copiedText);
							parent.notifyUser(parent.getContext().getString(
									R.string.console_copy_done,
									copiedText.length()));

							selectingForCopy = false;
							selectionArea.reset();
						}
					}
				} else {
					if ((metaState & META_CTRL_ON) != 0) {
						((vt320)buffer).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
						metaState &= ~META_CTRL_ON;
					} else
						metaState |= META_CTRL_ON;
				}

				redraw();

				return true;
			}

		} catch (IOException e) {
			Log.e(TAG, "Problem while trying to handle an onKey() event", e);
			try {
				transport.flush();
			} catch (IOException ioe) {
				Log.d(TAG, "Our transport was closed, dispatching disconnect event");
				dispatchDisconnect(false);
			}
		} catch (NullPointerException npe) {
			Log.d(TAG, "Input before connection established ignored.");
			return true;
		}

		return false;
	}

	/**
	 * Handle meta key presses where the key can be locked on.
	 * <p>
	 * 1st press: next key to have meta state<br />
	 * 2nd press: meta state is locked on<br />
	 * 3rd press: disable meta state
	 *
	 * @param code
	 */
	private void metaPress(int code) {
		if ((metaState & (code << 1)) != 0) {
			metaState &= ~(code << 1);
		} else if ((metaState & code) != 0) {
			metaState &= ~code;
			metaState |= code << 1;
		} else
			metaState |= code;
		redraw();
	}

	public int getMetaState() {
		return metaState;
	}

	private int getStateForBuffer() {
		int bufferState = 0;

		if ((metaState & META_CTRL_MASK) != 0)
			bufferState |= vt320.KEY_CONTROL;
		if ((metaState & META_SHIFT_MASK) != 0)
			bufferState |= vt320.KEY_SHIFT;
		if ((metaState & META_ALT_MASK) != 0)
			bufferState |= vt320.KEY_ALT;

		return bufferState;
	}

	public void setSelectingForCopy(boolean selectingForCopy) {
		this.selectingForCopy = selectingForCopy;
	}

	public boolean isSelectingForCopy() {
		return selectingForCopy;
	}

	public SelectionArea getSelectionArea() {
		return selectionArea;
	}

	public synchronized void tryKeyVibrate() {
		manager.tryKeyVibrate();
	}

	/**
	 * Request a different font size. Will make call to parentChanged() to make
	 * sure we resize PTY if needed.
	 */
	private final void setFontSize(float size) {
		if (size <= 0.0)
			return;

		defaultPaint.setTextSize(size);
		fontSize = size;

		// read new metrics to get exact pixel dimensions
		FontMetrics fm = defaultPaint.getFontMetrics();
		charTop = (int)Math.ceil(fm.top);

		float[] widths = new float[1];
		defaultPaint.getTextWidths("X", widths);
		charWidth = (int)Math.ceil(widths[0]);
		charHeight = (int)Math.ceil(fm.descent - fm.top);

		// refresh any bitmap with new font size
		if(parent != null)
			parentChanged(parent);

		for (FontSizeChangedListener ofscl : fontSizeChangedListeners)
			ofscl.onFontSizeChanged(size);
	}

	/**
	 * Add an {@link FontSizeChangedListener} to the list of listeners for this
	 * bridge.
	 *
	 * @param listener
	 *            listener to add
	 */
	public void addFontSizeChangedListener(FontSizeChangedListener listener) {
		fontSizeChangedListeners.add(listener);
	}

	/**
	 * Remove an {@link FontSizeChangedListener} from the list of listeners for
	 * this bridge.
	 *
	 * @param listener
	 */
	public void removeFontSizeChangedListener(FontSizeChangedListener listener) {
		fontSizeChangedListeners.remove(listener);
	}

	/**
	 * Something changed in our parent {@link TerminalView}, maybe it's a new
	 * parent, or maybe it's an updated font size. We should recalculate
	 * terminal size information and request a PTY resize.
	 */
	public final synchronized void parentChanged(TerminalView parent) {
		if (manager != null && !manager.isResizeAllowed()) {
			Log.d(TAG, "Resize is not allowed now");
			return;
		}

		this.parent = parent;
		int width = parent.getWidth();
		int height = parent.getHeight();

		// Something has gone wrong with our layout; we're 0 width or height!
		if (width <= 0 || height <= 0)
			return;

		clipboard = (ClipboardManager) parent.getContext().getSystemService(Context.CLIPBOARD_SERVICE);

		if (!forcedSize) {
			// recalculate buffer size
			int newColumns, newRows;

			newColumns = width / charWidth;
			newRows = height / charHeight;

			// If nothing has changed in the terminal dimensions and not an intial
			// draw then don't blow away scroll regions and such.
			if (newColumns == columns && newRows == rows)
				return;

			columns = newColumns;
			rows = newRows;
		}

		// reallocate new bitmap if needed
		boolean newBitmap = (bitmap == null);
		if(bitmap != null)
			newBitmap = (bitmap.getWidth() != width || bitmap.getHeight() != height);

		if (newBitmap) {
			discardBitmap();
			bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
			canvas.setBitmap(bitmap);
		}

		// clear out any old buffer information
		defaultPaint.setColor(Color.BLACK);
		canvas.drawPaint(defaultPaint);

		// Stroke the border of the terminal if the size is being forced;
		if (forcedSize) {
			int borderX = (columns * charWidth) + 1;
			int borderY = (rows * charHeight) + 1;

			defaultPaint.setColor(Color.GRAY);
			defaultPaint.setStrokeWidth(0.0f);
			if (width >= borderX)
				canvas.drawLine(borderX, 0, borderX, borderY + 1, defaultPaint);
			if (height >= borderY)
				canvas.drawLine(0, borderY, borderX + 1, borderY, defaultPaint);
		}

		try {
			// request a terminal pty resize
			synchronized (buffer) {
				int prevRow = buffer.getCursorRow();
				buffer.setScreenSize(columns, rows, true);

				// Work around weird vt320.java behavior where cursor is an offset from the bottom??
				buffer.setCursorPosition(buffer.getCursorColumn(), prevRow);
			}

			if(transport != null)
				transport.setDimensions(columns, rows, width, height);
		} catch(Exception e) {
			Log.e(TAG, "Problem while trying to resize screen or PTY", e);
		}

		// redraw local output if we don't have a sesson to receive our resize request
		if (transport == null) {
			synchronized (localOutput) {
				((vt320) buffer).reset();

				for (String line : localOutput)
					((vt320) buffer).putString(line);
			}
		}

		// force full redraw with new buffer size
		fullRedraw = true;
		redraw();

		parent.notifyUser(String.format("%d x %d", columns, rows));

		Log.i(TAG, String.format("parentChanged() now width=%d, height=%d", columns, rows));
	}

	/**
	 * Somehow our parent {@link TerminalView} was destroyed. Now we don't need
	 * to redraw anywhere, and we can recycle our internal bitmap.
	 */
	public synchronized void parentDestroyed() {
		parent = null;
		discardBitmap();
	}

	private void discardBitmap() {
		if (bitmap != null)
			bitmap.recycle();
		bitmap = null;
	}

	public void setVDUBuffer(VDUBuffer buffer) {
		this.buffer = buffer;
	}

	public VDUBuffer getVDUBuffer() {
		return buffer;
	}

	public void onDraw() {
		int fg, bg;
		synchronized (buffer) {
			boolean entireDirty = buffer.update[0] || fullRedraw;

			// walk through all lines in the buffer
			for(int l = 0; l < buffer.height; l++) {

				// check if this line is dirty and needs to be repainted
				// also check for entire-buffer dirty flags
				if (!entireDirty && !buffer.update[l + 1]) continue;

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
					if ((currAttr & VDUBuffer.COLOR_FG) != 0) {
						int fgcolor = ((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1;
						if (fgcolor < 8 && (currAttr & VDUBuffer.BOLD) != 0)
							fg = color[fgcolor + 8];
						else
							fg = color[fgcolor];
					}

					// check if background color attribute is set
					if ((currAttr & VDUBuffer.COLOR_BG) != 0)
						bg = color[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1];

					// support character inversion by swapping background and foreground color
					if ((currAttr & VDUBuffer.INVERT) != 0) {
						int swapc = bg;
						bg = fg;
						fg = swapc;
					}

					// set underlined attributes if requested
					defaultPaint.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);

					// determine the amount of continuous characters with the same settings and print them all at once
					while(c + addr < buffer.width && buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr) {
						addr++;
					}

					// Save the current clip region
					canvas.save(Canvas.CLIP_SAVE_FLAG);

					// clear this dirty area with background color
					defaultPaint.setColor(bg);
					canvas.clipRect(c * charWidth, l * charHeight, (c + addr) * charWidth, (l + 1) * charHeight);
					canvas.drawPaint(defaultPaint);

					// write the text string starting at 'c' for 'addr' number of characters
					defaultPaint.setColor(fg);
					if((currAttr & VDUBuffer.INVISIBLE) == 0)
						canvas.drawText(buffer.charArray[buffer.windowBase + l], c,
							addr, c * charWidth, (l * charHeight) - charTop,
							defaultPaint);

					// Restore the previous clip region
					canvas.restore();

					// advance to the next text block with different characteristics
					c += addr - 1;
				}
			}

			// reset entire-buffer flags
			buffer.update[0] = false;
		}
		fullRedraw = false;
	}

	public void redraw() {
		if (parent != null)
			parent.postInvalidate();
	}

	// We don't have a scroll bar.
	public void updateScrollBar() {
	}

	/**
	 * Resize terminal to fit [rows]x[cols] in screen of size [width]x[height]
	 * @param rows
	 * @param cols
	 * @param width
	 * @param height
	 */
	public synchronized void resizeComputed(int cols, int rows, int width, int height) {
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

		forcedSize = true;
		this.columns = cols;
		this.rows = rows;
		setFontSize(size);
	}

	private int fontSizeCompare(float size, int cols, int rows, int width, int height) {
		// read new metrics to get exact pixel dimensions
		defaultPaint.setTextSize(size);
		FontMetrics fm = defaultPaint.getFontMetrics();

		float[] widths = new float[1];
		defaultPaint.getTextWidths("X", widths);
		int termWidth = (int)widths[0] * cols;
		int termHeight = (int)Math.ceil(fm.descent - fm.top) * rows;

		Log.d("fontsize", String.format("font size %f resulted in %d x %d", size, termWidth, termHeight));

		// Check to see if it fits in resolution specified.
		if (termWidth > width || termHeight > height)
			return 1;

		if (termWidth == width || termHeight == height)
			return 0;

		return -1;
	}

	/**
	 * @return whether underlying transport can forward ports
	 */
	public boolean canFowardPorts() {
		return transport.canForwardPorts();
	}

	/**
	 * Adds the {@link PortForwardBean} to the list.
	 * @param portForward the port forward bean to add
	 * @return true on successful addition
	 */
	public boolean addPortForward(PortForwardBean portForward) {
		return transport.addPortForward(portForward);
	}

	/**
	 * Removes the {@link PortForwardBean} from the list.
	 * @param portForward the port forward bean to remove
	 * @return true on successful removal
	 */
	public boolean removePortForward(PortForwardBean portForward) {
		return transport.removePortForward(portForward);
	}

	/**
	 * @return the list of port forwards
	 */
	public List<PortForwardBean> getPortForwards() {
		return transport.getPortForwards();
	}

	/**
	 * Enables a port forward member. After calling this method, the port forward should
	 * be operational.
	 * @param portForward member of our current port forwards list to enable
	 * @return true on successful port forward setup
	 */
	public boolean enablePortForward(PortForwardBean portForward) {
		if (!transport.isConnected()) {
			Log.i(TAG, "Attempt to enable port forward while not connected");
			return false;
		}

		return transport.enablePortForward(portForward);
	}

	/**
	 * Disables a port forward member. After calling this method, the port forward should
	 * be non-functioning.
	 * @param portForward member of our current port forwards list to enable
	 * @return true on successful port forward tear-down
	 */
	public boolean disablePortForward(PortForwardBean portForward) {
		if (!transport.isConnected()) {
			Log.i(TAG, "Attempt to disable port forward while not connected");
			return false;
		}

		return transport.disablePortForward(portForward);
	}

	/**
	 * @return whether the TerminalBridge should close
	 */
	public boolean isAwaitingClose() {
		return awaitingClose;
	}

	/**
	 * @return whether this connection had started and subsequently disconnected
	 */
	public boolean isDisconnected() {
		return disconnected;
	}

	/* (non-Javadoc)
	 * @see de.mud.terminal.VDUDisplay#setColor(byte, byte, byte, byte)
	 */
	public void setColor(int index, int red, int green, int blue) {
		// Don't allow the system colors to be overwritten for now. May violate specs.
		if (index < color.length && index >= 16)
			color[index] = 0xff000000 | red << 16 | green << 8 | blue;
	}

	public final void resetColors() {
		color = new int[] {
				0xff000000, // black
				0xffcc0000, // red
				0xff00cc00, // green
				0xffcccc00, // brown
				0xff0000cc, // blue
				0xffcc00cc, // purple
				0xff00cccc, // cyan
				0xffcccccc, // light grey
				0xff444444, // dark grey
				0xffff4444, // light red
				0xff44ff44, // light green
				0xffffff44, // yellow
				0xff4444ff, // light blue
				0xffff44ff, // light purple
				0xff44ffff, // light cyan
				0xffffffff, // white
				0xff000000, 0xff00005f, 0xff000087, 0xff0000af, 0xff0000d7,
				0xff0000ff, 0xff005f00, 0xff005f5f, 0xff005f87, 0xff005faf,
				0xff005fd7, 0xff005fff, 0xff008700, 0xff00875f, 0xff008787,
				0xff0087af, 0xff0087d7, 0xff0087ff, 0xff00af00, 0xff00af5f,
				0xff00af87, 0xff00afaf, 0xff00afd7, 0xff00afff, 0xff00d700,
				0xff00d75f, 0xff00d787, 0xff00d7af, 0xff00d7d7, 0xff00d7ff,
				0xff00ff00, 0xff00ff5f, 0xff00ff87, 0xff00ffaf, 0xff00ffd7,
				0xff00ffff, 0xff5f0000, 0xff5f005f, 0xff5f0087, 0xff5f00af,
				0xff5f00d7, 0xff5f00ff, 0xff5f5f00, 0xff5f5f5f, 0xff5f5f87,
				0xff5f5faf, 0xff5f5fd7, 0xff5f5fff, 0xff5f8700, 0xff5f875f,
				0xff5f8787, 0xff5f87af, 0xff5f87d7, 0xff5f87ff, 0xff5faf00,
				0xff5faf5f, 0xff5faf87, 0xff5fafaf, 0xff5fafd7, 0xff5fafff,
				0xff5fd700, 0xff5fd75f, 0xff5fd787, 0xff5fd7af, 0xff5fd7d7,
				0xff5fd7ff, 0xff5fff00, 0xff5fff5f, 0xff5fff87, 0xff5fffaf,
				0xff5fffd7, 0xff5fffff, 0xff870000, 0xff87005f, 0xff870087,
				0xff8700af, 0xff8700d7, 0xff8700ff, 0xff875f00, 0xff875f5f,
				0xff875f87, 0xff875faf, 0xff875fd7, 0xff875fff, 0xff878700,
				0xff87875f, 0xff878787, 0xff8787af, 0xff8787d7, 0xff8787ff,
				0xff87af00, 0xff87af5f, 0xff87af87, 0xff87afaf, 0xff87afd7,
				0xff87afff, 0xff87d700, 0xff87d75f, 0xff87d787, 0xff87d7af,
				0xff87d7d7, 0xff87d7ff, 0xff87ff00, 0xff87ff5f, 0xff87ff87,
				0xff87ffaf, 0xff87ffd7, 0xff87ffff, 0xffaf0000, 0xffaf005f,
				0xffaf0087, 0xffaf00af, 0xffaf00d7, 0xffaf00ff, 0xffaf5f00,
				0xffaf5f5f, 0xffaf5f87, 0xffaf5faf, 0xffaf5fd7, 0xffaf5fff,
				0xffaf8700, 0xffaf875f, 0xffaf8787, 0xffaf87af, 0xffaf87d7,
				0xffaf87ff, 0xffafaf00, 0xffafaf5f, 0xffafaf87, 0xffafafaf,
				0xffafafd7, 0xffafafff, 0xffafd700, 0xffafd75f, 0xffafd787,
				0xffafd7af, 0xffafd7d7, 0xffafd7ff, 0xffafff00, 0xffafff5f,
				0xffafff87, 0xffafffaf, 0xffafffd7, 0xffafffff, 0xffd70000,
				0xffd7005f, 0xffd70087, 0xffd700af, 0xffd700d7, 0xffd700ff,
				0xffd75f00, 0xffd75f5f, 0xffd75f87, 0xffd75faf, 0xffd75fd7,
				0xffd75fff, 0xffd78700, 0xffd7875f, 0xffd78787, 0xffd787af,
				0xffd787d7, 0xffd787ff, 0xffd7af00, 0xffd7af5f, 0xffd7af87,
				0xffd7afaf, 0xffd7afd7, 0xffd7afff, 0xffd7d700, 0xffd7d75f,
				0xffd7d787, 0xffd7d7af, 0xffd7d7d7, 0xffd7d7ff, 0xffd7ff00,
				0xffd7ff5f, 0xffd7ff87, 0xffd7ffaf, 0xffd7ffd7, 0xffd7ffff,
				0xffff0000, 0xffff005f, 0xffff0087, 0xffff00af, 0xffff00d7,
				0xffff00ff, 0xffff5f00, 0xffff5f5f, 0xffff5f87, 0xffff5faf,
				0xffff5fd7, 0xffff5fff, 0xffff8700, 0xffff875f, 0xffff8787,
				0xffff87af, 0xffff87d7, 0xffff87ff, 0xffffaf00, 0xffffaf5f,
				0xffffaf87, 0xffffafaf, 0xffffafd7, 0xffffafff, 0xffffd700,
				0xffffd75f, 0xffffd787, 0xffffd7af, 0xffffd7d7, 0xffffd7ff,
				0xffffff00, 0xffffff5f, 0xffffff87, 0xffffffaf, 0xffffffd7,
				0xffffffff, 0xff080808, 0xff121212, 0xff1c1c1c, 0xff262626,
				0xff303030, 0xff3a3a3a, 0xff444444, 0xff4e4e4e, 0xff585858,
				0xff626262, 0xff6c6c6c, 0xff767676, 0xff808080, 0xff8a8a8a,
				0xff949494, 0xff9e9e9e, 0xffa8a8a8, 0xffb2b2b2, 0xffbcbcbc,
				0xffc6c6c6, 0xffd0d0d0, 0xffdadada, 0xffe4e4e4, 0xffeeeeee,
		};
	}
}
